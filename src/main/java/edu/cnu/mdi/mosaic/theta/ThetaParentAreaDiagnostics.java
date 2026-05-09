package edu.cnu.mdi.mosaic.theta;

import java.util.Comparator;
import java.util.List;

/**
 * Collection of per-parent theta-splice area diagnostics.
 *
 * @param parentErrors per-parent area errors
 * @param totalParentNormalizedArea total parent normalized area
 * @param totalThetaNormalizedArea total theta-child normalized area
 * @param totalDeltaNormalizedArea total theta minus parent normalized area
 * @param maxAbsDeltaNormalizedArea maximum absolute parent error
 * @param rmsDeltaNormalizedArea RMS parent error
 */
public record ThetaParentAreaDiagnostics(
        List<ThetaParentAreaError> parentErrors,
        double totalParentNormalizedArea,
        double totalThetaNormalizedArea,
        double totalDeltaNormalizedArea,
        double maxAbsDeltaNormalizedArea,
        double rmsDeltaNormalizedArea) {

    /**
     * Creates diagnostics.
     *
     * @param parentErrors parent errors
     * @param totalParentNormalizedArea total parent area
     * @param totalThetaNormalizedArea total theta area
     * @param totalDeltaNormalizedArea total delta
     * @param maxAbsDeltaNormalizedArea maximum absolute delta
     * @param rmsDeltaNormalizedArea RMS delta
     */
    public ThetaParentAreaDiagnostics {
        parentErrors = List.copyOf(parentErrors == null ? List.of() : parentErrors);
    }

    /**
     * Creates empty diagnostics.
     *
     * @return empty diagnostics
     */
    public static ThetaParentAreaDiagnostics empty() {
        return new ThetaParentAreaDiagnostics(
                List.of(), 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Gets the worst parent errors by absolute normalized-area delta.
     *
     * @param count maximum number to return
     * @return worst parent errors
     */
    public List<ThetaParentAreaError> worstErrors(int count) {
        int n = Math.max(0, count);

        return parentErrors.stream()
                .sorted(Comparator.comparingDouble(
                        ThetaParentAreaError::absDeltaNormalizedArea).reversed())
                .limit(n)
                .toList();
    }

    /**
     * Creates a compact multi-line summary.
     *
     * @return summary
     */
    public String summary() {
        return "Theta parent area diagnostics:" + System.lineSeparator()
                + "  parents: " + parentErrors.size() + System.lineSeparator()
                + String.format("  parent A_norm: %.17g%n", totalParentNormalizedArea)
                + String.format("  theta  A_norm: %.17g%n", totalThetaNormalizedArea)
                + String.format("  delta  A_norm: %.17g%n", totalDeltaNormalizedArea)
                + String.format("  max |parent delta|: %.6e%n", maxAbsDeltaNormalizedArea)
                + String.format("  rms parent delta: %.6e", rmsDeltaNormalizedArea);
    }
}