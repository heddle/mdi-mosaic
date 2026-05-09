package edu.cnu.mdi.mosaic.algorithm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import edu.cnu.mdi.mosaic.area.PrepatchAreaResult;
import edu.cnu.mdi.mosaic.cell.IntersectingCell;
import edu.cnu.mdi.mosaic.diagnostic.PrepatchDiagnosticSummary;
import edu.cnu.mdi.mosaic.patch.PoleRelation;
import edu.cnu.mdi.mosaic.patch.PoleStats;
import edu.cnu.mdi.mosaic.patch.Prepatch;
import edu.cnu.mdi.mosaic.phi.PhiParentAreaDiagnostics;
import edu.cnu.mdi.mosaic.phi.PhiPatch;
import edu.cnu.mdi.mosaic.phi.PhiSpliceConvergenceResult;
import edu.cnu.mdi.mosaic.phi.PhiSpliceResult;
import edu.cnu.mdi.mosaic.theta.ThetaParentAreaDiagnostics;
import edu.cnu.mdi.mosaic.theta.ThetaPatch;
import edu.cnu.mdi.mosaic.theta.ThetaSpliceConvergenceResult;
import edu.cnu.mdi.mosaic.theta.ThetaSpliceResult;
import edu.cnu.mdi.util.UnicodeUtils;

/**
 * Immutable result of a Mosaic algorithm run.
 * <p>
 * This object is intentionally designed to grow as the exact Mosaic algorithm
 * gains stages. At first it stores only the intersecting Cartesian cells. Later
 * it can also store prepatches, theta-spliced patches, final patches, area and
 * perimeter measurements, and validation statistics.
 * </p>
 */
public final class MosaicAlgorithmResult {

    /** Intersecting Cartesian cells found in step 1. */
    private final List<IntersectingCell> intersectingCells;

    /** Summary statistics for the run. */
    private final MosaicAlgorithmStats stats;
    
    /** Ordinary prepatches built in step 2. */
    private final List<Prepatch> prepatches;

    /** Cells deferred during prepatch construction. */
    private final List<IntersectingCell> deferredPrepatchCells;
    
    /** Prepatch area convergence results. */
    private final List<PrepatchAreaResult> prepatchAreaResults;
    
    /** Pole-involvement statistics for ordinary prepatches. */
    private final PoleStats poleStats;
    
    /** Diagnostic summary for ordinary prepatches. */
    private final PrepatchDiagnosticSummary prepatchDiagnosticSummary;
    
    /** Theta-spliced patches built after prepatch construction. */
    private final List<ThetaPatch> thetaPatches;

    /** Theta-splice result and statistics. */
    private final ThetaSpliceResult thetaSpliceResult;
    
    /** Theta-splice convergence diagnostic rows. */
    private final List<ThetaSpliceConvergenceResult> thetaSpliceConvergenceResults;
    
    /** Phi-spliced final patches. */
    private final List<PhiPatch> phiPatches;

    /** Phi-splice result and statistics. */
    private final PhiSpliceResult phiSpliceResult;

    /** Phi parent-area diagnostics. */
    private final PhiParentAreaDiagnostics phiParentAreaDiagnostics;

    /** Phi-splice convergence diagnostic rows. */
    private final List<PhiSpliceConvergenceResult> phiSpliceConvergenceResults;

    /** Total run time. */
    private final Duration duration;

    /**
     * Creates an algorithm result.
     *
     * @param intersectingCells intersecting Cartesian cells
     * @param prepatches ordinary prepatches
     * @param deferredPrepatchCells cells deferred during prepatch construction
     * @param stats run statistics
     * @param duration run duration
     */
    public MosaicAlgorithmResult(List<IntersectingCell> intersectingCells,
            List<Prepatch> prepatches,
            List<IntersectingCell> deferredPrepatchCells,
            List<PrepatchAreaResult> prepatchAreaResults,
            PrepatchDiagnosticSummary prepatchDiagnosticSummary,
            ThetaSpliceResult thetaSpliceResult,
            List<ThetaSpliceConvergenceResult> thetaSpliceConvergenceResults,
            PhiSpliceResult phiSpliceResult,
            PhiParentAreaDiagnostics phiParentAreaDiagnostics,
            List<PhiSpliceConvergenceResult> phiSpliceConvergenceResults,
            MosaicAlgorithmStats stats,
            Duration duration) {   	
        this.intersectingCells = List.copyOf(
                Objects.requireNonNull(intersectingCells, "intersectingCells"));

        this.prepatches = List.copyOf(prepatches == null ? List.of() : prepatches);
        this.poleStats = PoleStats.fromPrepatches(this.prepatches);
        this.deferredPrepatchCells = List.copyOf(
                deferredPrepatchCells == null ? List.of() : deferredPrepatchCells);
        this.prepatchAreaResults = List.copyOf(
                prepatchAreaResults == null ? List.of() : prepatchAreaResults);
        this.prepatchDiagnosticSummary = (prepatchDiagnosticSummary == null)
                ? PrepatchDiagnosticSummary.empty()
                : prepatchDiagnosticSummary;
        
        this.thetaSpliceResult = (thetaSpliceResult == null)
                ? ThetaSpliceResult.empty()
                : thetaSpliceResult;
        
        this.thetaSpliceConvergenceResults = List.copyOf(
                thetaSpliceConvergenceResults == null
                        ? List.of()
                        : thetaSpliceConvergenceResults);

        this.thetaPatches = this.thetaSpliceResult.thetaPatches();
        
        this.phiSpliceResult = (phiSpliceResult == null)
                ? PhiSpliceResult.empty()
                : phiSpliceResult;

        this.phiPatches = this.phiSpliceResult.phiPatches();

        this.phiParentAreaDiagnostics = (phiParentAreaDiagnostics == null)
                ? PhiParentAreaDiagnostics.empty()
                : phiParentAreaDiagnostics;

        this.phiSpliceConvergenceResults = List.copyOf(
                phiSpliceConvergenceResults == null
                        ? List.of()
                        : phiSpliceConvergenceResults);

        this.stats = Objects.requireNonNull(stats, "stats");
        this.duration = Objects.requireNonNull(duration, "duration");
    }  
    
    /**
     * Gets theta-splice convergence results.
     *
     * @return immutable theta-splice convergence results
     */
    public List<ThetaSpliceConvergenceResult> getThetaSpliceConvergenceResults() {
        return thetaSpliceConvergenceResults;
    }
    
    /**
     * Gets theta-spliced patches.
     *
     * @return immutable theta-patch list
     */
    public List<ThetaPatch> getThetaPatches() {
        return thetaPatches;
    }

    /**
     * Gets the theta-splice result.
     *
     * @return theta-splice result
     */
    public ThetaSpliceResult getThetaSpliceResult() {
        return thetaSpliceResult;
    }

    /**
     * Gets the number of theta patches.
     *
     * @return theta-patch count
     */
    public int getThetaPatchCount() {
        return thetaPatches.size();
    }
    
    /**
     * Gets pole-involvement statistics for ordinary prepatches.
     *
     * @return pole statistics
     */
    public PoleStats getPoleStats() {
        return poleStats;
    }
    /**
     * Gets prepatch area convergence results.
     *
     * @return area results
     */
    public List<PrepatchAreaResult> getPrepatchAreaResults() {
        return prepatchAreaResults;
    }
    
    /**
     * Gets the intersecting Cartesian cells.
     *
     * @return immutable list of intersecting cells
     */
    public List<IntersectingCell> getIntersectingCells() {
        return intersectingCells;
    }

    /**
     * Gets the algorithm statistics.
     *
     * @return algorithm statistics
     */
    public MosaicAlgorithmStats getStats() {
        return stats;
    }
    
    /**
     * Gets ordinary prepatches built in step 2.
     *
     * @return immutable prepatch list
     */
    public List<Prepatch> getPrepatches() {
        return prepatches;
    }

    /**
     * Gets cells deferred during prepatch construction.
     *
     * @return immutable deferred-cell list
     */
    public List<IntersectingCell> getDeferredPrepatchCells() {
        return deferredPrepatchCells;
    }

    /**
     * Gets the number of ordinary prepatches.
     *
     * @return prepatch count
     */
    public int getPrepatchCount() {
        return prepatches.size();
    }

    /**
     * Gets the number of deferred prepatch cells.
     *
     * @return deferred-cell count
     */
    public int getDeferredPrepatchCellCount() {
        return deferredPrepatchCells.size();
    }

    /**
     * Gets the total algorithm run duration.
     *
     * @return run duration
     */
    public Duration getDuration() {
        return duration;
    }

    /**
     * Gets the number of intersecting cells.
     *
     * @return intersecting-cell count
     */
    public int getIntersectingCellCount() {
        return intersectingCells.size();
    }

    /**
     * Creates an empty result.
     *
     * @return an empty result
     */
    public static MosaicAlgorithmResult empty() {
        return new MosaicAlgorithmResult(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                PrepatchDiagnosticSummary.empty(),
                ThetaSpliceResult.empty(),
                List.of(),
                PhiSpliceResult.empty(),
                PhiParentAreaDiagnostics.empty(),
                List.of(),
                MosaicAlgorithmStats.empty(),
                Duration.ZERO);
    }    
    /**
     * Gets the prepatch diagnostic summary.
     *
     * @return diagnostic summary
     */
    public PrepatchDiagnosticSummary getPrepatchDiagnosticSummary() {
        return prepatchDiagnosticSummary;
    }
    
    /**
     * Gets the production prepatch area result.
     *
     * <p>
     * By convention, entry zero of {@code prepatchAreaResults} is the production
     * area result. Additional entries, when present, are convergence-test results.
     * </p>
     *
     * @return production area result, or {@code null} if no area was computed
     */
    public PrepatchAreaResult getProductionPrepatchAreaResult() {
        return prepatchAreaResults.isEmpty() ? null : prepatchAreaResults.get(0);
    }
    
    /**
     * Gets phi-spliced final patches.
     *
     * @return immutable phi-patch list
     */
    public List<PhiPatch> getPhiPatches() {
        return phiPatches;
    }

    /**
     * Gets the phi-splice result.
     *
     * @return phi-splice result
     */
    public PhiSpliceResult getPhiSpliceResult() {
        return phiSpliceResult;
    }

    /**
     * Gets the number of final phi patches.
     *
     * @return phi-patch count
     */
    public int getPhiPatchCount() {
        return phiPatches.size();
    }

    /**
     * Gets phi parent-area diagnostics.
     *
     * @return diagnostics
     */
    public PhiParentAreaDiagnostics getPhiParentAreaDiagnostics() {
        return phiParentAreaDiagnostics;
    }

    /**
     * Gets phi-splice convergence results.
     *
     * @return convergence rows
     */
    public List<PhiSpliceConvergenceResult> getPhiSpliceConvergenceResults() {
        return phiSpliceConvergenceResults;
    }
    
    /**
     * Gets human-readable strings summarizing the most recent algorithm result.
     *
     * <p>
     * The strings are suitable for the Mosaic feedback pane. They summarize the
     * current exact-algorithm state, including intersecting cells, ordinary
     * prepatches, deferred cells, area estimates, and run time.
     * </p>
     *
     * @return list of result strings
     */
    public ArrayList<String> getResultStrings() {
        final String titleColor = "$orange$";
        final String valueColor = "$cyan$";
        final String warnColor = "$yellow$";

        ArrayList<String> resultStrings = new ArrayList<>();

        if (intersectingCells.isEmpty()
                && prepatches.isEmpty()
                && prepatchAreaResults.isEmpty()) {
            resultStrings.add(titleColor + "Algorithm: no results");
            return resultStrings;
        }

        resultStrings.add(titleColor + "Algorithm results");
        resultStrings.add(String.format("%sIntersecting cells: %,d",
                valueColor, getIntersectingCellCount()));

        if (stats != null) {
            for (var entry : stats.getCellTypeCounts().entrySet()) {
                long count = entry.getValue();
                if (count > 0) {
                    resultStrings.add(String.format("%s  %s: %,d",
                            valueColor, entry.getKey(), count));
                }
            }
        }

        resultStrings.add(String.format("%sPrepatches: %,d",
                valueColor, getPrepatchCount()));

        if (getDeferredPrepatchCellCount() > 0) {
            resultStrings.add(String.format("%sDeferred cells: %,d",
                    warnColor, getDeferredPrepatchCellCount()));
        } else {
            resultStrings.add(valueColor + "Deferred cells: 0");
        }
        
        if (poleStats != null && poleStats.getAnyPoleCount() > 0) {
            resultStrings.add(String.format("%sPole prepatches: %,d",
                    warnColor, poleStats.getAnyPoleCount()));

            if (poleStats.getNorthCount(PoleRelation.INSIDE) > 0
                    || poleStats.getNorthCount(PoleRelation.ON_BOUNDARY) > 0
                    || poleStats.getNorthCount(PoleRelation.AT_VERTEX) > 0) {
                resultStrings.add(String.format(
                        "%s  north: in=%d, bnd=%d, vtx=%d",
                        warnColor,
                        poleStats.getNorthCount(PoleRelation.INSIDE),
                        poleStats.getNorthCount(PoleRelation.ON_BOUNDARY),
                        poleStats.getNorthCount(PoleRelation.AT_VERTEX)));
            }

            if (poleStats.getSouthCount(PoleRelation.INSIDE) > 0
                    || poleStats.getSouthCount(PoleRelation.ON_BOUNDARY) > 0
                    || poleStats.getSouthCount(PoleRelation.AT_VERTEX) > 0) {
                resultStrings.add(String.format(
                        "%s  south: in=%d, bnd=%d, vtx=%d",
                        warnColor,
                        poleStats.getSouthCount(PoleRelation.INSIDE),
                        poleStats.getSouthCount(PoleRelation.ON_BOUNDARY),
                        poleStats.getSouthCount(PoleRelation.AT_VERTEX)));
            }
        }       

        if (prepatchAreaResults.isEmpty()) {
            resultStrings.add(warnColor + "Prepatch area: not computed");
        } else {
            PrepatchAreaResult production = prepatchAreaResults.get(0);

            resultStrings.add(String.format(
                    "%sPrepatch A_norm: %.17g",
                    titleColor,
                    production.normalizedArea()));

            resultStrings.add(String.format(
                    "%sArea samples/curve: %d",
                    valueColor,
                    production.samplesPerCurve()));

            resultStrings.add(String.format(
                    "%sArea prepatches: %,d/%,d",
                    valueColor,
                    production.successfulPrepatches(),
                    production.attemptedPrepatches()));

            if (production.failedPrepatches() > 0) {
                resultStrings.add(String.format(
                        "%sArea failures: %,d",
                        warnColor,
                        production.failedPrepatches()));
            }

            /*
             * If there is more than one entry, entry 0 is the production area and
             * entries 1..N are convergence-test results.
             */
            if (prepatchAreaResults.size() > 1) {
                resultStrings.add(titleColor + "Area convergence");

                for (int i = 1; i < prepatchAreaResults.size(); i++) {
                    PrepatchAreaResult result = prepatchAreaResults.get(i);

                    resultStrings.add(String.format(
                            "%s  s=%d  A_norm=%.17g",
                            valueColor,
                            result.samplesPerCurve(),
                            result.normalizedArea()));
                }
            }
        }
        
        if (thetaSpliceResult != null && thetaSpliceResult.stats().thetaPatchesBuilt() > 0) {
            resultStrings.add(String.format("%sTheta patches: %,d",
                    valueColor, thetaSpliceResult.stats().thetaPatchesBuilt()));

            resultStrings.add(String.format("%sTheta A_norm: %.17g",
                    titleColor, thetaSpliceResult.stats().normalizedArea()));

            resultStrings.add(String.format("%sTheta area \u0394: %.3e",
                    valueColor, thetaSpliceResult.stats().normalizedAreaDelta()));

            if (thetaSpliceResult.stats().failedPrepatches() > 0) {
                resultStrings.add(String.format("%sTheta failures: %,d",
                        warnColor, thetaSpliceResult.stats().failedPrepatches()));
            }
            
            if (thetaSpliceResult.stats().handledPolePrepatches() > 0) {
                resultStrings.add(String.format("%sTheta handled poles: %,d",
                        valueColor, thetaSpliceResult.stats().handledPolePrepatches()));
            }

            if (thetaSpliceResult.stats().deferredPolePrepatches() > 0) {
                resultStrings.add(String.format("%sTheta deferred poles: %,d",
                        warnColor, thetaSpliceResult.stats().deferredPolePrepatches()));
            }
            
            if (thetaSpliceResult.stats().deferredPolePrepatches() > 0) {
                resultStrings.add(String.format("%sTheta deferred poles: %,d",
                        warnColor, thetaSpliceResult.stats().deferredPolePrepatches()));
            }
        }
        
        if (thetaSpliceConvergenceResults != null
                && !thetaSpliceConvergenceResults.isEmpty()) {

            resultStrings.add(titleColor + "Theta convergence");

            for (ThetaSpliceConvergenceResult result : thetaSpliceConvergenceResults) {
                resultStrings.add(String.format(
                        "%s  s=%d \u0394=%.3e",
                        valueColor,
                        result.samplesPerCurve(),
                        result.deltaNormalizedArea()));
            }
        }
        
        if (thetaSpliceResult != null
                && thetaSpliceResult.parentAreaDiagnostics() != null
                && !thetaSpliceResult.parentAreaDiagnostics().parentErrors().isEmpty()) {

            ThetaParentAreaDiagnostics parentDiag =
                    thetaSpliceResult.parentAreaDiagnostics();

            resultStrings.add(String.format("%sTheta max parent \u0394: %.3e",
                    valueColor,
                    parentDiag.maxAbsDeltaNormalizedArea()));

            resultStrings.add(String.format("%sTheta RMS parent \u0394: %.3e",
                    valueColor,
                    parentDiag.rmsDeltaNormalizedArea()));
        }
        
        if (phiSpliceResult != null && phiSpliceResult.stats().phiPatchesBuilt() > 0) {
            resultStrings.add(String.format("%sFinal patches: %,d",
                    valueColor, phiSpliceResult.stats().phiPatchesBuilt()));

            resultStrings.add(String.format("%sPhi A_norm: %.17g",
                    titleColor, phiSpliceResult.stats().normalizedArea()));

            resultStrings.add(String.format("%sPhi area \u0394: %.3e",
                    valueColor, phiSpliceResult.stats().normalizedAreaDelta()));

            if (phiSpliceResult.stats().failedThetaPatches() > 0) {
                resultStrings.add(String.format("%sPhi failures: %,d",
                        warnColor, phiSpliceResult.stats().failedThetaPatches()));
            }
            
            if (phiSpliceResult.stats().deferredPolarThetaPatches() > 0) {
                resultStrings.add(String.format("%sPhi deferred polar: %,d",
                        warnColor, phiSpliceResult.stats().deferredPolarThetaPatches()));
            }
            
            if (phiSpliceResult.stats().handledPolarThetaPatches() > 0) {
                resultStrings.add(String.format("%sPhi handled polar: %,d",
                        valueColor, phiSpliceResult.stats().handledPolarThetaPatches()));
            }

            if (phiSpliceResult.stats().deferredPolarThetaPatches() > 0) {
                resultStrings.add(String.format("%sPhi deferred polar: %,d",
                        warnColor, phiSpliceResult.stats().deferredPolarThetaPatches()));
            }
        }

        if (phiParentAreaDiagnostics != null
                && !phiParentAreaDiagnostics.parentErrors().isEmpty()) {

            resultStrings.add(String.format("%sPhi max parent \u0394: %.3e",
                    valueColor,
                    phiParentAreaDiagnostics.maxAbsDeltaNormalizedArea()));

            resultStrings.add(String.format("%sPhi RMS parent \u0394: %.3e",
                    valueColor,
                    phiParentAreaDiagnostics.rmsDeltaNormalizedArea()));
        }

        if (phiSpliceConvergenceResults != null
                && !phiSpliceConvergenceResults.isEmpty()) {

            resultStrings.add(titleColor + "Phi convergence");

            for (PhiSpliceConvergenceResult result : phiSpliceConvergenceResults) {
                resultStrings.add(String.format(
                        "%s  s=%d \u0394=%.3e",
                        valueColor,
                        result.samplesPerMeridianArc(),
                        result.deltaNormalizedArea()));
            }
        }
        
        if (prepatchDiagnosticSummary != null
                && prepatchDiagnosticSummary.validCount() > 0) {

            resultStrings.add(String.format(
                    "%sPatch A_norm min/max: %.3e / %.3e",
                    valueColor,
                    prepatchDiagnosticSummary.minNormalizedArea(),
                    prepatchDiagnosticSummary.maxNormalizedArea()));

            resultStrings.add(String.format(
                    "%sTheta range min/max: %.3f%s / %.3f%s",
                    valueColor,
                    Math.toDegrees(prepatchDiagnosticSummary.minThetaRange()),
                    UnicodeUtils.DEGREE,
                    Math.toDegrees(prepatchDiagnosticSummary.maxThetaRange()),
                    UnicodeUtils.DEGREE));

            if (prepatchDiagnosticSummary.invalidCount() > 0) {
                resultStrings.add(String.format(
                        "%sDiagnostic failures: %,d",
                        warnColor,
                        prepatchDiagnosticSummary.invalidCount()));
            }
        }

        resultStrings.add(String.format("%sRun time: %d ms",
                valueColor, duration.toMillis()));

        return resultStrings;
    }
    
}