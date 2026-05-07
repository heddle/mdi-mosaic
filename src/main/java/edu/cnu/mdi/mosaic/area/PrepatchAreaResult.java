package edu.cnu.mdi.mosaic.area;

import java.util.List;

/**
 * Result of computing prepatch areas.
 *
 * @param samplesPerCurve samples per GENERAL curve
 * @param radius spherical shell radius
 * @param attemptedPrepatches number of prepatches attempted
 * @param successfulPrepatches number of prepatches with computed areas
 * @param failedPrepatches number of prepatches that could not be sampled or
 *        measured
 * @param totalArea total physical area
 * @param normalizedArea total area divided by {@code 4*pi*R^2}
 * @param failedCellIds string descriptions of failed cell ids
 */
public record PrepatchAreaResult(
        int samplesPerCurve,
        double radius,
        int attemptedPrepatches,
        int successfulPrepatches,
        int failedPrepatches,
        double totalArea,
        double normalizedArea,
        List<String> failedCellIds) {

    /**
     * Creates a formatted one-line summary.
     *
     * @return summary string
     */
    public String summaryLine() {
        return String.format(
                "samples/curve=%3d  prepatches=%5d/%5d  failed=%4d  A_norm=%.17g",
                samplesPerCurve,
                successfulPrepatches,
                attemptedPrepatches,
                failedPrepatches,
                normalizedArea);
    }

    /**
     * Creates a fuller multi-line summary.
     *
     * @return summary string
     */
    public String summary() {
        return summaryLine()
                + System.lineSeparator()
                + String.format("  total area = %.12f", totalArea)
                + System.lineSeparator()
                + String.format("  sphere area = %.12f", 4.0 * Math.PI * radius * radius);
    }
}