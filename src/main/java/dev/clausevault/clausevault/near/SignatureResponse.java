package dev.clausevault.clausevault.near;

public record SignatureResponse(
    String text,
    String signature,
    String signingAddress,
    String signatureKind
) {
}
