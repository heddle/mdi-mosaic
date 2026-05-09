package edu.cnu.mdi.mosaic.theta;

import java.util.List;

/**
 * Result of theta splicing.
 *
 * @param thetaPatches theta-spliced patches
 * @param failures splice failures
 * @param stats splice statistics
 * @param parentAreaDiagnostics per-parent area conservation diagnostics
 */
public record ThetaSpliceResult(
        List<ThetaPatch> thetaPatches,
        List<ThetaSpliceFailure> failures,
        ThetaSpliceStats stats,
        ThetaParentAreaDiagnostics parentAreaDiagnostics) {

    /**
     * Creates a theta-splice result.
     *
     * @param thetaPatches theta patches
     * @param failures failures
     * @param stats statistics
     * @param parentAreaDiagnostics per-parent area diagnostics
     */
    public ThetaSpliceResult {
        thetaPatches = List.copyOf(thetaPatches == null ? List.of() : thetaPatches);
        failures = List.copyOf(failures == null ? List.of() : failures);
        stats = (stats == null) ? ThetaSpliceStats.empty() : stats;
        parentAreaDiagnostics = (parentAreaDiagnostics == null)
                ? ThetaParentAreaDiagnostics.empty()
                : parentAreaDiagnostics;
    }

    /**
     * Backward-compatible constructor without parent diagnostics.
     *
     * @param thetaPatches theta patches
     * @param failures failures
     * @param stats statistics
     */
    public ThetaSpliceResult(
            List<ThetaPatch> thetaPatches,
            List<ThetaSpliceFailure> failures,
            ThetaSpliceStats stats) {
        this(thetaPatches, failures, stats, ThetaParentAreaDiagnostics.empty());
    }

    /**
     * Empty result.
     *
     * @return empty theta splice result
     */
    public static ThetaSpliceResult empty() {
        return new ThetaSpliceResult(
                List.of(),
                List.of(),
                ThetaSpliceStats.empty(),
                ThetaParentAreaDiagnostics.empty());
    }
}