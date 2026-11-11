package dev.clausevault.clausevault.near;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URLEncoder;
import java.util.Base64;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Builds a link to NEAR's Verification Center, {@code https://cloud.near.ai/verify}. In standalone mode the
 * page reads {@code ?model=<id>} and a {@code #receipt=<base64url JSON>} fragment (fragment is never sent to
 * NEAR's server), re-checks the signature in the browser, and verifies the TDX quote and GPU evidence for
 * the signing key. Fields are those the page's parser requires: completionId, model, signingAlgo,
 * signingAddress, signedText, signature, signatureKind ("provider_tee" or "gateway").
 */
@Component
public class NearVerifyLink {

    static final String VERIFY_URL = "https://cloud.near.ai/verify";
    private static final int MAX_RECEIPT_CHARS = 32_768;

    private final ObjectMapper mapper;

    public NearVerifyLink(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String build(String model, String chatId, SignatureResponse sig) {
        ObjectNode receipt = mapper.createObjectNode();
        receipt.put("completionId", chatId);
        receipt.put("model", model);
        receipt.put("signingAlgo", "ecdsa");
        receipt.put("signingAddress", sig.signingAddress());
        receipt.put("signedText", sig.text());
        receipt.put("signature", sig.signature());
        receipt.put("signatureKind", sig.signatureKind() == null ? "provider_tee" : sig.signatureKind());
        String encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mapper.writeValueAsString(receipt).getBytes(UTF_8));
        if (encoded.length() > MAX_RECEIPT_CHARS) {
            throw new IllegalStateException("Receipt too large for the verify page: " + encoded.length());
        }
        return VERIFY_URL + "?model=" + URLEncoder.encode(model, UTF_8) + "#receipt=" + encoded;
    }
}
