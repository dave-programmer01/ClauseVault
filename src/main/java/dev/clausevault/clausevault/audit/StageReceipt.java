package dev.clausevault.clausevault.audit;

import dev.clausevault.clausevault.near.VerificationResult;

/**
 * What the TEE signed for one pipeline stage, plus our local verification of it.
 * {@code verified=false} is data (the UI turns the badge red), not an error.
 */
public record StageReceipt(String stage, String chatId, String requestHash, String responseHash,
                           String signatureText, String signingAddress,
                           VerificationResult checks, boolean verified) {}
