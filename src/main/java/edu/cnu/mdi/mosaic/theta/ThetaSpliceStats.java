package edu.cnu.mdi.mosaic.theta;

/**
 * Statistics for theta splicing.
 *
 * @param prepatchesVisited number of prepatches considered
 * @param thetaPatchesBuilt number of theta patches built
 * @param failedPrepatches number of failed prepatches
 * @param deferredPolePrepatches number of pole-containing prepatches deferred
 * @param handledPolePrepatches number of pole-containing prepatches handled by
 *        special polar theta splicing
 * @param totalArea total theta-patch physical area
 * @param normalizedArea total area divided by {@code 4*pi*R^2}
 * @param referenceNormalizedArea reference prepatch normalized area
 */
public record ThetaSpliceStats(
        int prepatchesVisited,
        int thetaPatchesBuilt,
        int failedPrepatches,
        int deferredPolePrepatches,
        int handledPolePrepatches,
        double totalArea,
        double normalizedArea,
        double referenceNormalizedArea) {

    /**
     * Gets the difference between theta-patch normalized area and reference
     * prepatch normalized area.
     *
     * @return normalized area difference
     */
    public double normalizedAreaDelta() {
        return normalizedArea - referenceNormalizedArea;
    }

    /**
     * Creates a multi-line summary.
     *
     * @return summary string
     */
    public String summary() {
        return "Theta splice statistics:" + System.lineSeparator()
                + "  prepatches visited: " + prepatchesVisited + System.lineSeparator()
                + "  theta patches built: " + thetaPatchesBuilt + System.lineSeparator()
                + "  failed prepatches: " + failedPrepatches + System.lineSeparator()
                + "  deferred pole prepatches: " + deferredPolePrepatches + System.lineSeparator()
                + "  handled pole prepatches: " + handledPolePrepatches + System.lineSeparator()
                + String.format("  theta A_norm: %.17g%n", normalizedArea)
                + String.format("  reference A_norm: %.17g%n", referenceNormalizedArea)
                + String.format("  delta A_norm: %.17g%n", normalizedAreaDelta())
                + String.format("  relative delta: %.3e",
                        referenceNormalizedArea == 0.0
                                ? Double.NaN
                                : normalizedAreaDelta() / referenceNormalizedArea);
    }

    /**
     * Empty statistics.
     *
     * @return empty stats
     */
    public static ThetaSpliceStats empty() {
        return new ThetaSpliceStats(0, 0, 0, 0, 0, 0.0, 0.0, 0.0);
    }
}