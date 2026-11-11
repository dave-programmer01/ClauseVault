package dev.clausevault.clausevault.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.clausevault.clausevault.config.NearAiProperties;
import dev.clausevault.clausevault.model.AuditResult;
import dev.clausevault.clausevault.model.Risk;
import dev.clausevault.clausevault.model.StageReceipt;
import dev.clausevault.clausevault.near.NearAiClient;
import dev.clausevault.clausevault.near.NearSignatureVerifier;
import dev.clausevault.clausevault.near.NearVerificationService;
import dev.clausevault.clausevault.near.NearVerifyLink;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Full two-stage audit against live NEAR AI Cloud. Runs only when NEARAI_API_KEY is set:
 *
 * <pre>NEARAI_API_KEY=... ./mvnw test -Dtest=AuditPipelineLiveIT</pre>
 */
@EnabledIfEnvironmentVariable(named = "NEARAI_API_KEY", matches = ".+")
class AuditPipelineLiveIT {

    @Test
    void vendorNda_flagsPlantedRisks_bothStagesVerified() throws IOException {
        NearAiProperties props = new NearAiProperties(
            System.getenv("NEARAI_API_KEY"), "https://cloud-api.near.ai/v1", "Qwen/Qwen3-VL-30B-A3B-Instruct");
        JsonMapper mapper = JsonMapper.builder().build();
        NearAiClient client = new NearAiClient(RestClient.builder(), props, mapper);
        NearVerificationService verification = new NearVerificationService(client, new NearSignatureVerifier(), props);
        AuditService service = new AuditService(
            new StageRunner(client, verification, new NearVerifyLink(mapper), props), mapper);

        String text = Files.readString(Path.of("samples/nda-vendor.txt"));
        long started = System.currentTimeMillis();
        AuditResult result = service.audit("nda-vendor.txt", text);
        System.out.println("elapsed ms = " + (System.currentTimeMillis() - started));
        System.out.println("clauses    = " + result.clauses().stream().map(c -> c.id() + ":" + c.type()).toList());
        result.risks().forEach(r -> System.out.println("risk " + r.id() + " " + r.clauseId() + " " + r.category()
            + " " + r.severity() + " | " + r.quote()));
        result.receipts().forEach(r -> System.out.println("receipt " + r.stage() + " chat=" + r.chatId()
            + " verified=" + r.verified() + " checks=" + r.checks()));
        System.out.println("verify link (stage 2) = " + result.receipts().get(1).verifyUrl());

        assertFalse(result.clauses().isEmpty());
        List<String> categories = result.risks().stream().map(Risk::category).toList();
        assertTrue(categories.contains("IP_ASSIGNMENT"), categories.toString());
        assertTrue(categories.contains("ONE_SIDED_TERMINATION"), categories.toString());
        assertTrue(categories.contains("UNLIMITED_LIABILITY"), categories.toString());
        assertTrue(result.receipts().stream().allMatch(StageReceipt::verified), result.receipts().toString());
    }
}
