package dev.clausevault.clausevault.model;

import dev.clausevault.clausevault.near.VerificationResult;

/**
 * What the TEE signed for one pipeline stage, plus our local verification of it.
 * {@code verified=false} is data (the UI turns the badge red), not an error.
 * <p>
 * Our five checks prove the response was signed by a key listed in NEAR's attestation report for the model.
 * They do not verify the hardware quote itself; {@code verifyUrl} opens NEAR's Verification Center, which
 * re-checks the signature and verifies the TDX quote and GPU evidence for that key. Badge: "Verified in TEE";
 * proof panel wording: "Signed by an attested TEE key".
 */
public record StageReceipt(String stage, String chatId, String requestHash, String responseHash,
                           String signatureText, String signature, String signingAddress,
                           VerificationResult checks, boolean verified, String verifyUrl) {}
