package dev.clausevault.clausevault.audit;

public record Risk(String id, String clauseId, String category, Severity severity,
                   String explanation, String quote, ReviewStatus status) {}
