package edu.cnu.mdi.mosaic.phi;

import java.util.Comparator;
import java.util.List;

/**
 * Collection of per-theta-parent phi-splice area diagnostics.
 *
 * @param parentErrors per-parent errors
 * @param totalParentNormalizedArea total theta-parent area
 * @param totalPhiNormalizedArea total phi-child area
 * @param totalDeltaNormalizedArea total phi minus parent area
 * @param maxAbsDeltaNormalizedArea maximum absolute parent error
 * @param rmsDeltaNormalizedArea RMS parent error
 */
public record PhiParentAreaDiagnostics(
        List<PhiParentAreaError> parentErrors,
        double totalParentNormalizedArea,
        double totalPhiNormalizedArea,
        double totalDeltaNormalizedArea,
        double maxAbsDeltaNormalizedArea,
        double rmsDeltaNormalizedArea) {

    /**
     * Creates diagnostics.
     *
     * @param parentErrors parent errors
     * @param totalParentNormalizedArea total parent area
     * @param totalPhiNormalizedArea total phi area
     * @param totalDeltaNormalizedArea total delta
     * @param maxAbsDeltaNormalizedArea max absolute delta
     * @param rmsDeltaNormalizedArea RMS delta
     */
    public PhiParentAreaDiagnostics {
        parentErrors = List.copyOf(parentErrors == null ? List.of() : parentErrors);
    }

    /**
     * Creates empty diagnostics.
     *
     * @return empty diagnostics
     */
    public static PhiParentAreaDiagnostics empty() {
        return new PhiParentAreaDiagnostics(
                List.of(), 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Gets the worst parent errors by absolute normalized-area delta.
     *
     * @param count maximum number
     * @return worst errors
     */
    public List<PhiParentAreaError> worstErrors(int count) {
        int n = Math.max(0, count);

        return parentErrors.stream()
                .sorted(Comparator.comparingDouble(
                        PhiParentAreaError::absDeltaNormalizedArea).reversed())
                .limit(n)
                .toList();
    }

    /**
     * Creates a compact multi-line summary.
     *
     * @return summary string
     */
    public String summary() {
        return "Phi parent area diagnostics:" + System.lineSeparator()
                + "  parents: " + parentErrors.size() + System.lineSeparator()
                + String.format("  parent A_norm: %.17g%n", totalParentNormalizedArea)
                + String.format("  phi    A_norm: %.17g%n", totalPhiNormalizedArea)
                + String.format("  delta  A_norm: %.17g%n", totalDeltaNormalizedArea)
                + String.format("  max |parent delta|: %.6e%n", maxAbsDeltaNormalizedArea)
                + String.format("  rms parent delta: %.6e", rmsDeltaNormalizedArea);
    }
}