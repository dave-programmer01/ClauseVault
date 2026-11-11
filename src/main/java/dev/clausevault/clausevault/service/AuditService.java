package dev.clausevault.clausevault.service;

import dev.clausevault.clausevault.exception.AuditException;
import dev.clausevault.clausevault.exception.StageException;
import dev.clausevault.clausevault.model.AuditResult;
import dev.clausevault.clausevault.model.AuditRun;
import dev.clausevault.clausevault.model.Clause;
import dev.clausevault.clausevault.model.ReviewAction;
import dev.clausevault.clausevault.model.ReviewRequest;
import dev.clausevault.clausevault.model.ReviewStatus;
import dev.clausevault.clausevault.model.Risk;
import dev.clausevault.clausevault.model.Severity;
import dev.clausevault.clausevault.model.StageOutput;
import dev.clausevault.clausevault.model.StageReceipt;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Two-stage audit: clause extraction, then risk flagging over stage 1's exact output.
 * Each stage yields a TEE receipt; stage 2's request hash therefore covers stage 1's result.
 */
@Slf4j
@Service
public class AuditService {

    private static final int PARSE_ATTEMPTS = 2;

    private final StageRunner runner;
    private final ObjectMapper mapper;
    private final Map<String, AuditRun> runs = new ConcurrentHashMap<>();

    public AuditService(StageRunner runner, ObjectMapper mapper) {
        this.runner = runner;
        this.mapper = mapper;
    }

    public AuditResult audit(String filename, String contractText) {
        Parsed<List<Clause>> stage1 = runParsed(
            Prompts.STAGE_1_NAME, Prompts.STAGE_1_SYSTEM, Prompts.stage1User(contractText), this::parseClauses);
        List<Clause> clauses = stage1.value();
        if (clauses.isEmpty()) {
            throw new StageException(Prompts.STAGE_1_NAME + ": model returned no clauses");
        }

        Set<String> clauseIds = new LinkedHashSet<>();
        clauses.forEach(c -> clauseIds.add(c.id()));
        Parsed<List<Risk>> stage2 = runParsed(
            Prompts.STAGE_2_NAME, Prompts.STAGE_2_SYSTEM, Prompts.stage2User(stage1.output().content()),
            node -> parseRisks(node, clauseIds));

        String id = UUID.randomUUID().toString();
        AuditResult result = new AuditResult(id, filename, clauses, stage2.value(),
            List.of(stage1.output().receipt(), stage2.output().receipt()));
        runs.put(id, new AuditRun(result, List.of(stage1.output(), stage2.output())));
        log.info("Audit {} for '{}': {} clauses, {} risks, verified={}", id, filename, clauses.size(),
            result.risks().size(), result.receipts().stream().map(StageReceipt::verified).toList());
        return result;
    }

    public Optional<AuditRun> get(String id) {
        return Optional.ofNullable(runs.get(id));
    }

    /**
     * Applies a human review to one risk. Runs inside {@code compute} so concurrent clicks serialize.
     * Any state may move to any other state. Receipts and raw bytes are untouched: a review never
     * changes what the model said, it only records what the human decided.
     */
    public AuditResult review(String auditId, ReviewRequest req) {
        validate(req);
        AuditRun updated = runs.computeIfPresent(auditId, (id, run) -> {
            List<Risk> risks = new ArrayList<>(run.result().risks());
            int index = -1;
            for (int i = 0; i < risks.size(); i++) {
                if (risks.get(i).id().equals(req.riskId())) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                throw new AuditException(HttpStatus.NOT_FOUND, "No risk " + req.riskId() + " in audit " + auditId);
            }
            Risk current = risks.get(index);
            Risk reviewed = switch (req.action()) {
                case ACCEPT -> current.withStatus(ReviewStatus.ACCEPTED);
                case DISMISS -> current.withStatus(ReviewStatus.DISMISSED);
                case EDIT -> current.withEdit(req.severity(), req.explanation());
            };
            risks.set(index, reviewed);
            AuditResult r = run.result();
            return new AuditRun(new AuditResult(r.id(), r.filename(), r.clauses(), List.copyOf(risks), r.receipts()),
                run.stages());
        });
        if (updated == null) {
            throw new AuditException(HttpStatus.NOT_FOUND, "No audit with id " + auditId);
        }
        log.info("Audit {} risk {} reviewed: {}", auditId, req.riskId(), req.action());
        return updated.result();
    }

    private static void validate(ReviewRequest req) {
        if (req == null || req.riskId() == null || req.riskId().isBlank() || req.action() == null) {
            throw new AuditException(HttpStatus.BAD_REQUEST, "riskId and action are required");
        }
        if (req.action() == ReviewAction.EDIT) {
            if (req.severity() == null && req.explanation() == null) {
                throw new AuditException(HttpStatus.BAD_REQUEST, "EDIT needs a severity and/or an explanation");
            }
            if (req.explanation() != null && req.explanation().isBlank()) {
                throw new AuditException(HttpStatus.BAD_REQUEST, "explanation must not be blank");
            }
        }
    }

    private record Parsed<T>(StageOutput output, T value) {}

    /** Runs a stage and parses its JSON; on a parse failure the stage is retried once, then it throws. */
    private <T> Parsed<T> runParsed(String stage, String system, String user, Function<JsonNode, T> parse) {
        StageException last = null;
        for (int attempt = 1; attempt <= PARSE_ATTEMPTS; attempt++) {
            StageOutput output = runner.run(stage, system, user);
            try {
                return new Parsed<>(output, parse.apply(extractJson(output.content())));
            } catch (RuntimeException e) {
                // Never log or chain the model output: it contains the contract. Class name and size only.
                log.warn("Stage {} attempt {}/{}: unparseable output ({}, {} chars)", stage, attempt, PARSE_ATTEMPTS,
                    e.getClass().getSimpleName(), output.content().length());
                last = new StageException(stage + ": model did not return the expected JSON");
            }
        }
        throw last;
    }

    /** Parses defensively on a copy: strips ``` fences and takes the first '{' to the last '}'. */
    JsonNode extractJson(String content) {
        String s = content.replaceAll("(?m)^\\s*```[a-zA-Z]*\\s*$", "");
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("no JSON object in model output");
        }
        return mapper.readTree(s.substring(start, end + 1));
    }

    private List<Clause> parseClauses(JsonNode root) {
        JsonNode array = root.path("clauses");
        if (!array.isArray()) {
            throw new IllegalArgumentException("missing 'clauses' array");
        }
        List<Clause> clauses = new ArrayList<>();
        for (JsonNode n : array) {
            String id = n.path("id").asString("").strip();
            String text = n.path("text").asString("");
            if (id.isEmpty() || text.isBlank()) {
                log.warn("Dropping clause #{} without id or text", clauses.size() + 1);
                continue;
            }
            String type = n.path("type").asString("").strip().toUpperCase();
            clauses.add(new Clause(id, Prompts.CLAUSE_TYPES.contains(type) ? type : "OTHER",
                n.path("title").asString("").strip(), text));
        }
        return clauses;
    }

    /** Normalizes severity/category, drops risks for unknown clauses, assigns r1.. and PENDING server-side. */
    private List<Risk> parseRisks(JsonNode root, Set<String> clauseIds) {
        JsonNode array = root.path("risks");
        if (!array.isArray()) {
            throw new IllegalArgumentException("missing 'risks' array");
        }
        List<Risk> risks = new ArrayList<>();
        for (JsonNode n : array) {
            String clauseId = n.path("clauseId").asString("").strip();
            if (!clauseIds.contains(clauseId)) {
                log.warn("Dropping risk for unknown clauseId '{}'", clauseId);
                continue;
            }
            risks.add(new Risk(
                "r" + (risks.size() + 1),
                clauseId,
                normalizeCategory(n.path("category").asString("")),
                normalizeSeverity(n.path("severity").asString("")),
                n.path("explanation").asString("").strip(),
                n.path("quote").asString("").strip(),
                ReviewStatus.PENDING,
                false));
        }
        return risks;
    }

    static String normalizeCategory(String raw) {
        String c = raw.strip().toUpperCase().replaceAll("[\\s\\-]+", "_");
        return Prompts.RISK_CATEGORIES.contains(c) ? c : "OTHER";
    }

    static Severity normalizeSeverity(String raw) {
        try {
            return Severity.valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown severity '{}', defaulting to MEDIUM", raw);
            return Severity.MEDIUM;
        }
    }
}
