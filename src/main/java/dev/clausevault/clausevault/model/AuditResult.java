package dev.clausevault.clausevault.model;

import java.util.List;

public record AuditResult(String id, String filename, List<Clause> clauses,
                          List<Risk> risks, List<StageReceipt> receipts) {}
