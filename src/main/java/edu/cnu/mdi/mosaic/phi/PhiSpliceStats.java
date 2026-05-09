package edu.cnu.mdi.mosaic.phi;

/**
 * Statistics for phi splicing.
 *
 * @param thetaPatchesVisited number of theta patches considered
 * @param phiPatchesBuilt number of phi patches built
 * @param failedThetaPatches number of theta patches that produced no phi patches
 * @param deferredPolarThetaPatches number of polar-derived theta patches
 *        intentionally deferred from phi splicing
 * @param handledPolarThetaPatches number of polar-derived theta patches handled
 *        by special polar phi splicing
 * @param totalArea total phi-patch physical area
 * @param normalizedArea total area divided by {@code 4*pi*R^2}
 * @param referenceNormalizedArea reference theta-patch normalized area
 */
public record PhiSpliceStats(
        int thetaPatchesVisited,
        int phiPatchesBuilt,
        int failedThetaPatches,
        int deferredPolarThetaPatches,
        int handledPolarThetaPatches,
        double totalArea,
        double normalizedArea,
        double referenceNormalizedArea) {

    /**
     * Gets the normalized area difference.
     *
     * @return phi normalized area minus reference normalized area
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
        return "Phi splice statistics:" + System.lineSeparator()
                + "  theta patches visited: " + thetaPatchesVisited + System.lineSeparator()
                + "  phi patches built: " + phiPatchesBuilt + System.lineSeparator()
                + "  failed theta patches: " + failedThetaPatches + System.lineSeparator()
                + "  deferred polar theta patches: " + deferredPolarThetaPatches + System.lineSeparator()
                + "  handled polar theta patches: " + handledPolarThetaPatches + System.lineSeparator()
                + String.format("  phi A_norm: %.17g%n", normalizedArea)
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
    public static PhiSpliceStats empty() {
        return new PhiSpliceStats(0, 0, 0, 0, 0, 0.0, 0.0, 0.0);
    }
}