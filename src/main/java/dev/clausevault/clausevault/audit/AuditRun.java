package dev.clausevault.clausevault.audit;

import java.util.List;

/** A stored audit: the result returned to the client plus the raw bytes per stage (for re-verification / tamper demo). */
public record AuditRun(AuditResult result, List<StageOutput> stages) {}
