package dev.clausevault.clausevault.near;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;

@Slf4j
@Component
public class NearSignatureVerifier {

    private static final String PROVIDER_TEE = "provider_tee";

    public VerificationResult verify(String rawRequest, String rawResponse,
                                     SignatureResponse sig, String attestedAddress,
                                     String expectedModel) {
        String text = sig.text();
        if (text == null) {
            log.debug("Signature text is null");
            return VerificationResult.failed();
        }

        // text = model:reqHash:respHash — split from the right so a ':' in the model name is harmless.
        int lastColon = text.lastIndexOf(':');
        int secondLastColon = lastColon > 0 ? text.lastIndexOf(':', lastColon - 1) : -1;
        if (lastColon < 0 || secondLastColon < 0) {
            log.debug("Malformed signature text, expected model:reqHash:respHash: {}", text);
            return VerificationResult.failed();
        }
        String signedModel = text.substring(0, secondLastColon);
        String expectedRequestHash = text.substring(secondLastColon + 1, lastColon);
        String expectedResponseHash = text.substring(lastColon + 1);

        boolean modelOk = signedModel.equals(expectedModel);
        boolean requestHashOk = sha256Hex(rawRequest).equalsIgnoreCase(expectedRequestHash);
        boolean responseHashOk = sha256Hex(rawResponse).equalsIgnoreCase(expectedResponseHash);

        String recoveredAddress = recoverAddress(text, sig.signature());
        boolean signerOk = recoveredAddress != null && recoveredAddress.equalsIgnoreCase(attestedAddress);
        boolean teeSigned = PROVIDER_TEE.equals(sig.signatureKind());

        return new VerificationResult(modelOk, requestHashOk, responseHashOk, signerOk, teeSigned);
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String recoverAddress(String text, String signatureHex) {
        if (signatureHex == null) {
            log.debug("Signature is null");
            return null;
        }
        try {
            String hex = signatureHex.startsWith("0x") || signatureHex.startsWith("0X")
                ? signatureHex.substring(2)
                : signatureHex;
            byte[] bytes = HexFormat.of().parseHex(hex);
            if (bytes.length != 65) {
                log.debug("Signature has {} bytes, expected 65", bytes.length);
                return null;
            }
            byte[] r = Arrays.copyOfRange(bytes, 0, 32);
            byte[] s = Arrays.copyOfRange(bytes, 32, 64);
            byte v = bytes[64];
            // Some signers emit the recovery id as 0/1; web3j expects 27/28.
            if ((v & 0xFF) < 27) {
                v += 27;
            }
            Sign.SignatureData sigData = new Sign.SignatureData(v, r, s);
            BigInteger publicKey = Sign.signedPrefixedMessageToKey(text.getBytes(UTF_8), sigData);
            return "0x" + Keys.getAddress(publicKey);
        } catch (Exception e) {
            log.debug("Failed to recover signer address from signature", e);
            return null;
        }
    }
}
