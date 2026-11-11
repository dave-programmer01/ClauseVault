package dev.clausevault.clausevault.audit;

import static java.nio.charset.StandardCharsets.UTF_8;

import dev.clausevault.clausevault.near.ChatExchange;
import dev.clausevault.clausevault.near.NearAiClient;
import dev.clausevault.clausevault.near.NearVerificationService;
import dev.clausevault.clausevault.near.Sha256;
import dev.clausevault.clausevault.near.SignatureResponse;
import dev.clausevault.clausevault.near.VerificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Runs one pipeline stage: chat, then signature, then local verification. One receipt per stage. */
@Slf4j
@Component
public class StageRunner {

    private final NearAiClient client;
    private final NearVerificationService verification;

    public StageRunner(NearAiClient client, NearVerificationService verification) {
        this.client = client;
        this.verification = verification;
    }

    public StageOutput run(String stage, String systemPrompt, String userContent) {
        ChatExchange exchange;
        SignatureResponse sig;
        VerificationResult checks;
        try {
            exchange = client.chat(systemPrompt, userContent);
            if ("length".equals(exchange.finishReason())) {
                throw new StageException(stage + ": model output was truncated (finish_reason=length)");
            }
            sig = client.signature(exchange.id());
            checks = verification.verify(exchange, sig);
        } catch (StageException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new StageException(stage + ": NEAR AI call failed: " + e.getMessage(), e);
        }

        byte[] rawRequest = exchange.rawRequest().getBytes(UTF_8);
        byte[] rawResponse = exchange.rawResponse().getBytes(UTF_8);
        StageReceipt receipt = new StageReceipt(
            stage, exchange.id(), Sha256.hex(rawRequest), Sha256.hex(rawResponse),
            sig.text(), sig.signingAddress(), checks, checks.verified());
        log.info("Stage {} chat {} verified={} ({} chars)", stage, exchange.id(), checks.verified(),
            exchange.content().length());
        return new StageOutput(exchange.content(), receipt, rawRequest, rawResponse);
    }
}
