package dev.clausevault.clausevault.near;

public record VerificationResult(
    boolean modelOk,
    boolean requestHashOk,
    boolean responseHashOk,
    boolean signerOk,
    boolean teeSigned
) {
    static VerificationResult failed() {
        return new VerificationResult(false, false, false, false, false);
    }

    public boolean verified() {
        return modelOk && requestHashOk && responseHashOk && signerOk && teeSigned;
    }
}
