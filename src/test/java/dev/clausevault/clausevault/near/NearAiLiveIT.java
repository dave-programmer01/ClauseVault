package dev.clausevault.clausevault.near;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.clausevault.clausevault.config.NearAiProperties;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end check against the live NEAR AI Cloud API. Runs only when NEARAI_API_KEY is set:
 *
 * <pre>NEARAI_API_KEY=... ./mvnw test -Dtest=NearAiLiveIT</pre>
 */
@EnabledIfEnvironmentVariable(named = "NEARAI_API_KEY", matches = ".+")
class NearAiLiveIT {

    private static final String MODEL = "Qwen/Qwen3-VL-30B-A3B-Instruct";

    @Test
    void chatThenSignatureThenAttestation_verifies() {
        NearAiProperties props = new NearAiProperties(
            System.getenv("NEARAI_API_KEY"), "https://cloud-api.near.ai/v1", MODEL);
        NearAiClient client = new NearAiClient(RestClient.builder(), props, JsonMapper.builder().build());
        NearVerificationService service = new NearVerificationService(client, new NearSignatureVerifier(), props);

        ChatExchange exchange = client.chat(
            "Flag risks: 'Licensee waives all rights to terminate and agrees to unlimited liability.'");
        SignatureResponse sig = client.signature(exchange.id());
        Set<String> attested = client.attestedAddresses();
        VerificationResult result = service.verify(exchange, sig);

        System.out.println("chat id            = " + exchange.id());
        System.out.println("signature text     = " + sig.text());
        System.out.println("signature_kind     = " + sig.signatureKind());
        System.out.println("signing_address    = " + sig.signingAddress());
        System.out.println("attested addresses = " + attested);
        System.out.println("result             = " + result);
        System.out.println("verified=" + result.verified());

        assertSame(attested, client.attestedAddresses(), "second call must hit the cache");
        assertTrue(result.verified(), "live exchange should verify: " + result);
    }
}
