package edu.cnu.mdi.mosaic.diagnostic;

import java.util.Comparator;
import java.util.List;

/**
 * Summary statistics for prepatch diagnostics.
 *
 * @param diagnostics per-prepatch diagnostics
 * @param validCount number of valid diagnostics
 * @param invalidCount number of invalid diagnostics
 * @param totalArea total physical area
 * @param normalizedArea total area divided by {@code 4*pi*R^2}
 * @param minNormalizedArea minimum valid normalized prepatch area
 * @param maxNormalizedArea maximum valid normalized prepatch area
 * @param minThetaRange minimum valid theta range, radians
 * @param maxThetaRange maximum valid theta range, radians
 */
public record PrepatchDiagnosticSummary(
        List<PrepatchDiagnostic> diagnostics,
        int validCount,
        int invalidCount,
        double totalArea,
        double normalizedArea,
        double minNormalizedArea,
        double maxNormalizedArea,
        double minThetaRange,
        double maxThetaRange) {

    /**
     * Creates a summary.
     *
     * @param diagnostics diagnostics
     * @param validCount valid count
     * @param invalidCount invalid count
     * @param totalArea total area
     * @param normalizedArea normalized total area
     * @param minNormalizedArea minimum normalized patch area
     * @param maxNormalizedArea maximum normalized patch area
     * @param minThetaRange minimum theta range
     * @param maxThetaRange maximum theta range
     */
    public PrepatchDiagnosticSummary {
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }

    /**
     * Gets the smallest-area valid diagnostic.
     *
     * @return smallest-area diagnostic, or {@code null}
     */
    public PrepatchDiagnostic smallestAreaPatch() {
        return diagnostics.stream()
                .filter(PrepatchDiagnostic::valid)
                .min(Comparator.comparingDouble(PrepatchDiagnostic::normalizedArea))
                .orElse(null);
    }

    /**
     * Gets the largest-area valid diagnostic.
     *
     * @return largest-area diagnostic, or {@code null}
     */
    public PrepatchDiagnostic largestAreaPatch() {
        return diagnostics.stream()
                .filter(PrepatchDiagnostic::valid)
                .max(Comparator.comparingDouble(PrepatchDiagnostic::normalizedArea))
                .orElse(null);
    }

    /**
     * Gets a compact multi-line summary.
     *
     * @return summary string
     */
    public String summary() {
        StringBuilder sb = new StringBuilder();

        sb.append("Prepatch diagnostics:").append(System.lineSeparator());
        sb.append("  valid: ").append(validCount)
                .append("  invalid: ").append(invalidCount)
                .append(System.lineSeparator());
        sb.append(String.format("  A_norm: %.17g%n", normalizedArea));
        sb.append(String.format("  min patch A_norm: %.17g%n", minNormalizedArea));
        sb.append(String.format("  max patch A_norm: %.17g%n", maxNormalizedArea));
        sb.append(String.format("  theta range deg: min=%.6f max=%.6f%n",
                Math.toDegrees(minThetaRange),
                Math.toDegrees(maxThetaRange)));

        PrepatchDiagnostic smallest = smallestAreaPatch();
        PrepatchDiagnostic largest = largestAreaPatch();

        if (smallest != null) {
            sb.append("  smallest: ").append(smallest.cellId()).append(System.lineSeparator());
        }

        if (largest != null) {
            sb.append("  largest: ").append(largest.cellId()).append(System.lineSeparator());
        }

        return sb.toString();
    }

    /**
     * Creates an empty summary.
     *
     * @return empty summary
     */
    public static PrepatchDiagnosticSummary empty() {
        return new PrepatchDiagnosticSummary(
                List.of(), 0, 0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0);
    }
}