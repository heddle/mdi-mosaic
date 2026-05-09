package edu.cnu.mdi.mosaic.phi;

import java.util.List;

/**
 * Result of phi splicing.
 *
 * @param phiPatches phi-spliced final patches
 * @param failures splice failures
 * @param stats splice statistics
 */
public record PhiSpliceResult(
        List<PhiPatch> phiPatches,
        List<PhiSpliceFailure> failures,
        PhiSpliceStats stats) {

    /**
     * Creates a phi-splice result.
     *
     * @param phiPatches phi patches
     * @param failures failures
     * @param stats statistics
     */
    public PhiSpliceResult {
        phiPatches = List.copyOf(phiPatches == null ? List.of() : phiPatches);
        failures = List.copyOf(failures == null ? List.of() : failures);
        stats = (stats == null) ? PhiSpliceStats.empty() : stats;
    }

    /**
     * Empty result.
     *
     * @return empty result
     */
    public static PhiSpliceResult empty() {
        return new PhiSpliceResult(List.of(), List.of(), PhiSpliceStats.empty());
    }
}