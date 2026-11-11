package dev.clausevault.clausevault.service;

import static java.nio.charset.StandardCharsets.UTF_8;

import dev.clausevault.clausevault.config.NearAiProperties;
import dev.clausevault.clausevault.exception.StageException;
import dev.clausevault.clausevault.model.StageOutput;
import dev.clausevault.clausevault.model.StageReceipt;
import dev.clausevault.clausevault.near.ChatExchange;
import dev.clausevault.clausevault.near.NearAiClient;
import dev.clausevault.clausevault.near.NearVerificationService;
import dev.clausevault.clausevault.near.NearVerifyLink;
import dev.clausevault.clausevault.near.Sha256;
import dev.clausevault.clausevault.near.SignatureResponse;
import dev.clausevault.clausevault.near.VerificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

/** Runs one pipeline stage: chat, then signature, then local verification. One receipt per stage. */
@Slf4j
@Component
public class StageRunner {

    private final NearAiClient client;
    private final NearVerificationService verification;
    private final NearVerifyLink verifyLink;
    private final String model;

    public StageRunner(NearAiClient client, NearVerificationService verification, NearVerifyLink verifyLink,
                       NearAiProperties props) {
        this.client = client;
        this.verification = verification;
        this.verifyLink = verifyLink;
        this.model = props.model();
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
            // Exception messages from the HTTP client can embed response bodies; keep class and status only.
            String detail = e instanceof RestClientResponseException r
                ? "HTTP " + r.getStatusCode().value() : e.getClass().getSimpleName();
            throw new StageException(stage + ": NEAR AI call failed (" + detail + ")");
        }

        byte[] rawRequest = exchange.rawRequest().getBytes(UTF_8);
        byte[] rawResponse = exchange.rawResponse().getBytes(UTF_8);
        StageReceipt receipt = new StageReceipt(
            stage, exchange.id(), Sha256.hex(rawRequest), Sha256.hex(rawResponse),
            sig.text(), sig.signature(), sig.signingAddress(), checks, checks.verified(),
            verifyLink.build(model, exchange.id(), sig));
        log.info("Stage {} chat {} verified={} ({} chars)", stage, exchange.id(), checks.verified(),
            exchange.content().length());
        return new StageOutput(exchange.content(), receipt, rawRequest, rawResponse);
    }
}
