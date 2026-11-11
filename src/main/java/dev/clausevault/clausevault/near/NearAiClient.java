package dev.clausevault.clausevault.near;

import static java.nio.charset.StandardCharsets.UTF_8;

import dev.clausevault.clausevault.config.NearAiProperties;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Slf4j
@Component
public class NearAiClient {

    private static final String SIGNING_ALGO = "ecdsa";
    private static final int SIGNATURE_ATTEMPTS = 3;
    private static final long SIGNATURE_RETRY_DELAY_MS = 1_000;
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(90);

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final String model;
    private final SecureRandom random = new SecureRandom();
    private final AtomicReference<Set<String>> cachedAttestedAddresses = new AtomicReference<>();

    public NearAiClient(RestClient.Builder builder, NearAiProperties props, ObjectMapper mapper) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(READ_TIMEOUT);
        this.restClient = builder
            .requestFactory(factory)
            .baseUrl(props.baseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            // Response bytes must be hashable as received: no transparent compression.
            .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "identity")
            .build();
        this.mapper = mapper;
        this.model = props.model();
    }

    /** Sends one user prompt to /chat/completions and returns the exact request and response bytes. */
    public ChatExchange chat(String prompt) {
        // Serialize exactly once; this string is both what we send and what we hash.
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "user").put("content", prompt);
        String rawRequest = mapper.writeValueAsString(body);

        byte[] responseBytes = restClient.post()
            .uri("/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .body(rawRequest.getBytes(UTF_8))
            .retrieve()
            .body(byte[].class);
        if (responseBytes == null) {
            throw new IllegalStateException("Empty response from /chat/completions");
        }
        String rawResponse = new String(responseBytes, UTF_8);

        JsonNode root = mapper.readTree(rawResponse);
        String id = root.path("id").asString();
        String content = root.path("choices").path(0).path("message").path("content").asString();
        if (id.isEmpty() || content.isEmpty()) {
            throw new IllegalStateException("Unexpected /chat/completions payload: " + abbreviate(rawResponse));
        }
        return new ChatExchange(id, rawRequest, rawResponse, content);
    }

    /**
     * Fetches the TEE signature for a completion. The gateway can briefly answer 404 (or a 2xx
     * carrying {@code error_code}) right after the completion, so this retries a few times.
     */
    public SignatureResponse signature(String chatId) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= SIGNATURE_ATTEMPTS; attempt++) {
            try {
                String body = restClient.get()
                    .uri(b -> b.path("/signature/{id}")
                        .queryParam("model", model)
                        .queryParam("signing_algo", SIGNING_ALGO)
                        .build(chatId))
                    .retrieve()
                    .body(String.class);
                JsonNode node = mapper.readTree(body);
                if (node.has("error_code")) {
                    last = new IllegalStateException("Signature not available yet: " + body);
                } else {
                    return new SignatureResponse(
                        node.path("text").asString(),
                        node.path("signature").asString(),
                        node.path("signing_address").asString(),
                        node.path("signature_kind").asString(null));
                }
            } catch (HttpClientErrorException.NotFound e) {
                last = e;
            }
            log.debug("Signature for {} not ready (attempt {}/{})", chatId, attempt, SIGNATURE_ATTEMPTS);
            if (attempt < SIGNATURE_ATTEMPTS) {
                sleep(SIGNATURE_RETRY_DELAY_MS);
            }
        }
        throw new IllegalStateException(
            "No signature for chat " + chatId + " after " + SIGNATURE_ATTEMPTS + " attempts", last);
    }

    /**
     * Lowercased signing addresses of every TEE replica currently serving the model, from
     * {@code model_attestations[].signing_address}. Fetched once and cached; see
     * {@link #refreshAttestedAddresses()} when a replica may have restarted with a new key.
     */
    public Set<String> attestedAddresses() {
        Set<String> cached = cachedAttestedAddresses.get();
        if (cached != null) {
            return cached;
        }
        return refreshAttestedAddresses();
    }

    /** Drops the cache and fetches a fresh attestation report. */
    public Set<String> refreshAttestedAddresses() {
        String nonce = HexFormat.of().formatHex(randomBytes(32));
        String body = restClient.get()
            .uri(b -> b.path("/attestation/report")
                .queryParam("model", model)
                .queryParam("signing_algo", SIGNING_ALGO)
                .queryParam("nonce", nonce)
                .build())
            .retrieve()
            .body(String.class);
        JsonNode node = mapper.readTree(body);
        Set<String> addresses = new LinkedHashSet<>();
        for (JsonNode attestation : node.path("model_attestations")) {
            String address = attestation.path("signing_address").asString();
            if (!address.isEmpty()) {
                addresses.add(address.toLowerCase());
            }
        }
        if (addresses.isEmpty()) {
            throw new IllegalStateException("No model_attestations[].signing_address in: " + abbreviate(body));
        }
        Set<String> result = Set.copyOf(addresses);
        cachedAttestedAddresses.set(result);
        log.debug("Attested signing addresses for {}: {}", model, result);
        return result;
    }

    private byte[] randomBytes(int n) {
        byte[] bytes = new byte[n];
        random.nextBytes(bytes);
        return bytes;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for signature", e);
        }
    }

    private static String abbreviate(String s) {
        return s.length() <= 300 ? s : s.substring(0, 300) + "...";
    }
}
