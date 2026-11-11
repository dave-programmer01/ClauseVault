package dev.clausevault.clausevault.near;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NearSignatureVerifierTest {

    private static final String MODEL = "Qwen/Qwen3-VL-30B-A3B-Instruct";

    private static final String REQUEST =
        "{\"model\":\"Qwen/Qwen3-VL-30B-A3B-Instruct\",\"messages\":[{\"role\":\"user\",\"content\":"
            + "\"Flag risks: 'Licensee waives all rights to terminate and agrees to unlimited liability.'\"}]}";

    private static final String TEXT =
        MODEL
            + ":823fa3b076a6690e245e3ec373d9b0e29c466c832c393529786686f1e41edaff"
            + ":a30689dcfb0df548a34959744df393909cd8de7cf386bca0c28a9c9a1449ce11";

    private static final String SIGNATURE =
        "0xd755b38ebdbe574256fb2b98bc6cfb4a7a7db9c5b6e4689dcd74220e1027e3d3"
            + "2cdd88d089941f224b33fb6dd8335eb8743aaa1c21c74e77abe48c2716f897bd1c";

    private static final String ATTESTED_ADDRESS = "0x2665cce942fa427ef30d87f4b0bff67fb7645462";

    private static final SignatureResponse SIG =
        new SignatureResponse(TEXT, SIGNATURE, ATTESTED_ADDRESS, "provider_tee");

    private final NearSignatureVerifier verifier = new NearSignatureVerifier();

    @Test
    void validResponse_verifies() throws IOException {
        VerificationResult result = verifier.verify(REQUEST, loadResponse(), SIG, ATTESTED_ADDRESS, MODEL);

        assertTrue(result.modelOk(), "model");
        assertTrue(result.requestHashOk(), "request hash");
        assertTrue(result.responseHashOk(), "response hash");
        assertTrue(result.signerOk(), "signer");
        assertTrue(result.teeSigned(), "tee signed");
        assertTrue(result.verified());
    }

    @Test
    void tamperedResponse_fails() throws IOException {
        String tampered = loadResponse().replaceFirst("unlimited liability", "unlimitedXliability");

        VerificationResult result = verifier.verify(REQUEST, tampered, SIG, ATTESTED_ADDRESS, MODEL);

        assertFalse(result.responseHashOk());
        assertFalse(result.verified());
    }

    @Test
    void wrongModel_fails() throws IOException {
        VerificationResult result = verifier.verify(REQUEST, loadResponse(), SIG, ATTESTED_ADDRESS, "other/model");

        assertFalse(result.modelOk());
        assertTrue(result.signerOk(), "signature itself is still valid");
        assertFalse(result.verified());
    }

    @Test
    void recoveryIdZeroOrOne_isNormalized() throws IOException {
        // Same signature with v = 1 instead of 0x1c (28); verifier must add 27.
        String sigWithRawV = SIGNATURE.substring(0, SIGNATURE.length() - 2) + "01";
        SignatureResponse sig = new SignatureResponse(TEXT, sigWithRawV, ATTESTED_ADDRESS, "provider_tee");

        VerificationResult result = verifier.verify(REQUEST, loadResponse(), sig, ATTESTED_ADDRESS, MODEL);

        assertTrue(result.signerOk());
        assertTrue(result.verified());
    }

    @Test
    void malformedText_doesNotThrow() throws IOException {
        for (String bad : new String[] {"", "no-colons", "one:colon", ":"}) {
            SignatureResponse sig = new SignatureResponse(bad, SIGNATURE, ATTESTED_ADDRESS, "provider_tee");

            VerificationResult result = verifier.verify(REQUEST, loadResponse(), sig, ATTESTED_ADDRESS, MODEL);

            assertFalse(result.verified(), "text=" + bad);
        }
    }

    private static String loadResponse() throws IOException {
        try (InputStream in = NearSignatureVerifierTest.class.getResourceAsStream("/fixtures/resp.json")) {
            if (in == null) {
                throw new IllegalStateException("fixture /fixtures/resp.json not found on classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
