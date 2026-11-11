package dev.clausevault.clausevault.near;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class NearVerifyLinkTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final NearVerifyLink link = new NearVerifyLink(mapper);

    @Test
    void buildsStandaloneVerifyUrlWithReceiptInFragment() {
        SignatureResponse sig = new SignatureResponse("Qwen/Qwen3-VL-30B-A3B-Instruct:aa:bb", "0xsig", "0xaddr", "provider_tee");

        String url = link.build("Qwen/Qwen3-VL-30B-A3B-Instruct", "chatcmpl-1", sig);

        URI uri = URI.create(url);
        assertEquals("https", uri.getScheme());
        assertEquals("cloud.near.ai", uri.getHost());
        assertEquals("/verify", uri.getPath());
        assertEquals("model=Qwen%2FQwen3-VL-30B-A3B-Instruct", uri.getRawQuery());

        String fragment = uri.getRawFragment();
        assertTrue(fragment.startsWith("receipt="), fragment);
        String encoded = fragment.substring("receipt=".length());
        // The page's parser accepts only base64url characters and at most two '=' of padding.
        assertTrue(encoded.matches("^[\\w-]+={0,2}$"), encoded);

        JsonNode receipt = mapper.readTree(new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8));
        assertEquals("chatcmpl-1", receipt.path("completionId").asString());
        assertEquals("Qwen/Qwen3-VL-30B-A3B-Instruct", receipt.path("model").asString());
        assertEquals("ecdsa", receipt.path("signingAlgo").asString());
        assertEquals("0xaddr", receipt.path("signingAddress").asString());
        assertEquals("Qwen/Qwen3-VL-30B-A3B-Instruct:aa:bb", receipt.path("signedText").asString());
        assertEquals("0xsig", receipt.path("signature").asString());
        assertEquals("provider_tee", receipt.path("signatureKind").asString());
    }
}
