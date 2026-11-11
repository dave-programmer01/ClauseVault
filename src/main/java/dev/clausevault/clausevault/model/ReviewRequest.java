package dev.clausevault.clausevault.model;

/** {@code severity} and {@code explanation} are only read for {@link ReviewAction#EDIT}. */
public record ReviewRequest(String riskId, ReviewAction action, Severity severity, String explanation) {}
