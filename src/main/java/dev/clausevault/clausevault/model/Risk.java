package dev.clausevault.clausevault.model;

/**
 * One flagged risk. The model's output (clauseId, category, quote) is attested by the TEE receipt and never
 * changes; a human review only moves {@code status} and, for an EDIT, {@code severity}/{@code explanation}
 * with {@code edited=true} so the UI can show it is no longer verbatim model output.
 */
public record Risk(String id, String clauseId, String category, Severity severity,
                   String explanation, String quote, ReviewStatus status, boolean edited) {

    public Risk withStatus(ReviewStatus newStatus) {
        return new Risk(id, clauseId, category, severity, explanation, quote, newStatus, edited);
    }

    /** Applies a human edit: severity and/or explanation only, marks it edited and accepts it. */
    public Risk withEdit(Severity newSeverity, String newExplanation) {
        return new Risk(id, clauseId, category,
            newSeverity != null ? newSeverity : severity,
            newExplanation != null ? newExplanation.strip() : explanation,
            quote, ReviewStatus.ACCEPTED, true);
    }
}
