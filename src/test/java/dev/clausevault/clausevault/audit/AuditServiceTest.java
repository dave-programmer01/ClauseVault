package dev.clausevault.clausevault.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.clausevault.clausevault.near.VerificationResult;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AuditServiceTest {

    private static final String STAGE1 = "```json\n{\"clauses\":[" +
        "{\"id\":\"c1\",\"type\":\"PARTIES\",\"title\":\"Parties\",\"text\":\"Between A and B.\"}," +
        "{\"id\":\"c2\",\"type\":\"liability\",\"title\":\"Liability\",\"text\":\"Without limit as to amount.\"}," +
        "{\"id\":\"c3\",\"type\":\"WEIRD\",\"title\":\"Misc\",\"text\":\"Something.\"}" +
        "]}\n```";

    private static final String STAGE2 = "Here you go: {\"risks\":[" +
        "{\"clauseId\":\"c2\",\"category\":\"unlimited liability\",\"severity\":\"high\",\"explanation\":\"Uncapped.\",\"quote\":\"without limit\"}," +
        "{\"clauseId\":\"c3\",\"category\":\"SOMETHING_NEW\",\"severity\":\"critical\",\"explanation\":\"Odd.\",\"quote\":\"Something\"}," +
        "{\"clauseId\":\"c9\",\"category\":\"AUTO_RENEWAL\",\"severity\":\"LOW\",\"explanation\":\"Ghost clause.\",\"quote\":\"x\"}" +
        "]}";

    private final StageRunner runner = mock(StageRunner.class);
    private final AuditService service = new AuditService(runner, JsonMapper.builder().build());

    private static StageOutput output(String stage, String content, boolean verified) {
        VerificationResult checks = new VerificationResult(verified, verified, verified, verified, verified);
        StageReceipt receipt = new StageReceipt(stage, "chat-" + stage, "req", "resp", "text", "0xabc", checks, verified);
        return new StageOutput(content, receipt, "{}".getBytes(StandardCharsets.UTF_8),
            content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesNormalizesAndChainsStages() {
        when(runner.run(eq(Prompts.STAGE_1_NAME), any(), any())).thenReturn(output("s1", STAGE1, true));
        when(runner.run(eq(Prompts.STAGE_2_NAME), any(), any())).thenReturn(output("s2", STAGE2, false));

        AuditResult result = service.audit("nda.txt", "Between A and B. ...");

        assertEquals(3, result.clauses().size());
        assertEquals("LIABILITY", result.clauses().get(1).type(), "clause type uppercased");
        assertEquals("OTHER", result.clauses().get(2).type(), "unknown clause type -> OTHER");

        assertEquals(2, result.risks().size(), "risk for unknown clauseId c9 is dropped");
        Risk r1 = result.risks().get(0);
        assertEquals("r1", r1.id());
        assertEquals("UNLIMITED_LIABILITY", r1.category());
        assertEquals(Severity.HIGH, r1.severity());
        assertEquals(ReviewStatus.PENDING, r1.status());
        Risk r2 = result.risks().get(1);
        assertEquals("r2", r2.id());
        assertEquals("OTHER", r2.category(), "unknown category -> OTHER");
        assertEquals(Severity.MEDIUM, r2.severity(), "unknown severity -> MEDIUM");

        assertEquals(List.of(true, false), result.receipts().stream().map(StageReceipt::verified).toList(),
            "verified=false is returned as data, not thrown");

        // Stage 2's input is stage 1's exact output string, fences and all.
        verify(runner).run(Prompts.STAGE_1_NAME, Prompts.STAGE_1_SYSTEM, "<contract>\nBetween A and B. ...\n</contract>");
        verify(runner).run(Prompts.STAGE_2_NAME, Prompts.STAGE_2_SYSTEM, "<clauses>\n" + STAGE1 + "\n</clauses>");

        AuditRun run = service.get(result.id()).orElseThrow();
        assertEquals(2, run.stages().size());
        assertTrue(new String(run.stages().get(0).rawResponse(), StandardCharsets.UTF_8).contains("c2"));
    }

    @Test
    void retriesOnceOnBadJsonThenSucceeds() {
        when(runner.run(eq(Prompts.STAGE_1_NAME), any(), any()))
            .thenReturn(output("s1", "Sorry, I cannot.", true))
            .thenReturn(output("s1", STAGE1, true));
        when(runner.run(eq(Prompts.STAGE_2_NAME), any(), any())).thenReturn(output("s2", "{\"risks\":[]}", true));

        AuditResult result = service.audit("f", "t");

        assertEquals(3, result.clauses().size());
        assertTrue(result.risks().isEmpty());
        verify(runner, times(2)).run(eq(Prompts.STAGE_1_NAME), any(), any());
    }

    @Test
    void twoBadOutputs_throw() {
        when(runner.run(eq(Prompts.STAGE_1_NAME), any(), any())).thenReturn(output("s1", "{\"nope\":1}", true));

        assertThrows(StageException.class, () -> service.audit("f", "t"));
        verify(runner, times(2)).run(eq(Prompts.STAGE_1_NAME), any(), any());
    }
}
