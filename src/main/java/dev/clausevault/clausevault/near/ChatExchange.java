package dev.clausevault.clausevault.near;

/**
 * One round trip to NEAR AI. {@code rawRequest} and {@code rawResponse} are the exact bytes
 * sent and received (as UTF-8 strings); they are what the TEE signature commits to.
 */
public record ChatExchange(
    String id,
    String rawRequest,
    String rawResponse,
    String content
) {
}
