package dev.clausevault.clausevault.audit;

import java.util.List;

public record AuditResult(String id, String filename, List<Clause> clauses,
                          List<Risk> risks, List<StageReceipt> receipts) {}
