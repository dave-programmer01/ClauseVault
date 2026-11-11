package dev.clausevault.clausevault.audit;

/** One stage's model output with its receipt and the exact bytes the signature covers. */
public record StageOutput(String content, StageReceipt receipt, byte[] rawRequest, byte[] rawResponse) {}
