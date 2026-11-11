package dev.clausevault.clausevault.near;

import dev.clausevault.clausevault.config.NearAiProperties;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Verifies a chat exchange against its TEE signature and the model's attested replica keys.
 * If the signer is not among the cached replica addresses, the attestation is re-fetched once:
 * replicas can restart with new keys and a stale cache must not fail a good response.
 */
@Slf4j
@Service
public class NearVerificationService {

    private final NearAiClient client;
    private final NearSignatureVerifier verifier;
    private final String model;

    public NearVerificationService(NearAiClient client, NearSignatureVerifier verifier, NearAiProperties props) {
        this.client = client;
        this.verifier = verifier;
        this.model = props.model();
    }

    public VerificationResult verify(ChatExchange exchange, SignatureResponse sig) {
        VerificationResult result = verifyAgainst(exchange, sig, client.attestedAddresses());
        if (!result.signerOk()) {
            log.debug("Signer not in cached attested addresses for {}; refreshing attestation", exchange.id());
            result = verifyAgainst(exchange, sig, client.refreshAttestedAddresses());
        }
        return result;
    }

    private VerificationResult verifyAgainst(ChatExchange exchange, SignatureResponse sig, Set<String> addresses) {
        return verifier.verify(exchange.rawRequest(), exchange.rawResponse(), sig, addresses, model);
    }
}
