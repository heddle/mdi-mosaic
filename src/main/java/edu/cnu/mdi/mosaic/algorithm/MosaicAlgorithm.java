package edu.cnu.mdi.mosaic.algorithm;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import edu.cnu.mdi.log.Log;
import edu.cnu.mdi.mosaic.area.PrepatchAreaCalculator;
import edu.cnu.mdi.mosaic.area.PrepatchAreaResult;
import edu.cnu.mdi.mosaic.cell.IntersectingCell;
import edu.cnu.mdi.mosaic.cell.IntersectingCellFinder;
import edu.cnu.mdi.mosaic.diagnostic.PrepatchDiagnosticBuilder;
import edu.cnu.mdi.mosaic.diagnostic.PrepatchDiagnosticSummary;
import edu.cnu.mdi.mosaic.model.MosaicGridSpec;
import edu.cnu.mdi.mosaic.patch.PoleStats;
import edu.cnu.mdi.mosaic.patch.PrepatchBuildResult;
import edu.cnu.mdi.mosaic.patch.PrepatchBuilder;

/**
 * Static entry point for the exact Mosaic algorithm.
 * <p>
 * This class is deliberately stateless. It computes and returns a
 * {@link MosaicAlgorithmResult}; the shared {@code MosaicModel} owns the result
 * after the run completes.
 * </p>
 */
public final class MosaicAlgorithm {

    /**
     * Hidden constructor for utility class.
     */
    private MosaicAlgorithm() {
    }

    /**
     * Runs the current implemented portion of the Mosaic algorithm.
     * <p>
     * Current implementation:
     * </p>
     *
     * <ol>
     * <li>Find Cartesian cells intersecting the spherical shell.</li>
     * </ol>
     *
     * @param gridSpec grid specification
     * @param options algorithm options
     * @return algorithm result
     */
    public static MosaicAlgorithmResult run(MosaicGridSpec gridSpec,
            MosaicAlgorithmOptions options) {
    	
    	if (gridSpec == null) {
            throw new IllegalArgumentException("gridSpec must not be null.");
        }
    	
    	if (options == null) {
    	    options = new MosaicAlgorithmOptions();
    	}

        Instant start = Instant.now();

        Log.getInstance().info("Starting algorithm...");
        Log.getInstance().info("Step 1: Find intersecting cells...");

        List<IntersectingCell> cells =
                IntersectingCellFinder.findIntersectingCells(gridSpec);

        MosaicAlgorithmStats stats =
                MosaicAlgorithmStats.fromIntersectingCells(cells);

        Log.getInstance().info("Found " + cells.size() + " intersecting cells.");
        logStats(stats);

        Log.getInstance().info("Step 2: Build ordinary prepatch boundary curves...");

        double radius = gridSpec.getSphericalGrid().getRadius();
        PrepatchBuildResult prepatchResult =
                PrepatchBuilder.buildPrepatches(cells, radius);

        Log.getInstance().info("Built "
                + prepatchResult.prepatches().size()
                + " ordinary prepatches.");

        Log.getInstance().info("Deferred "
                + prepatchResult.deferredCells().size()
                + " cells for later degeneracy handling.");

        for (String line : prepatchResult.stats().summary().split("\\R")) {
            Log.getInstance().info(line);
        }
        
        logPrepatchFailures(prepatchResult, 10);
        
        PoleStats poleStats = PoleStats.fromPrepatches(prepatchResult.prepatches());
        for (String line : poleStats.summary().split("\\R")) {
            Log.getInstance().info(line);
        }
        
        //area test
        Log.getInstance().info("Step 2 area test: approximate ordinary prepatch areas...");

        PrepatchAreaResult productionAreaResult =
                PrepatchAreaCalculator.compute(
                        prepatchResult.prepatches(),
                        radius,
                        options.getAreaSamplesPerCurve());

        Log.getInstance().info("  production "
                + productionAreaResult.summaryLine());

        List<PrepatchAreaResult> areaResults = new ArrayList<>();
        areaResults.add(productionAreaResult);
        
        Log.getInstance().info("Step 2 diagnostics: ordinary prepatch diagnostics...");

        PrepatchDiagnosticSummary diagnosticSummary =
                PrepatchDiagnosticBuilder.build(
                        prepatchResult.prepatches(),
                        radius,
                        options.getAreaSamplesPerCurve());

        for (String line : diagnosticSummary.summary().split("\\R")) {
            Log.getInstance().info(line);
        }

        if (options.isRunAreaConvergenceTest()) {
            List<PrepatchAreaResult> convergenceResults =
                    PrepatchAreaCalculator.convergenceTest(
                            prepatchResult.prepatches(),
                            radius,
                            options.getConvergenceSampleCounts());

            Log.getInstance().info("  convergence test:");

            for (PrepatchAreaResult areaResult : convergenceResults) {
                Log.getInstance().info("    " + areaResult.summaryLine());
            }

            areaResults.addAll(convergenceResults);
        }

        PrepatchAreaResult finalAreaResult = areaResults.get(areaResults.size() - 1);

        if (!finalAreaResult.failedCellIds().isEmpty()) {
            Log.getInstance().warning("  Area calculation failed for "
                    + finalAreaResult.failedCellIds().size()
                    + " prepatches at highest sample count.");
        }
        Duration duration = Duration.between(start, Instant.now());

        Log.getInstance().info("Algorithm steps 1-2 completed in "
                + duration.toMillis() + " ms.");

        return new MosaicAlgorithmResult(
                cells,
                prepatchResult.prepatches(),
                prepatchResult.deferredCells(),
                areaResults,
                diagnosticSummary,
                stats,
                duration);    }
    
    /**
     * Logs a small sample of ordinary prepatch-construction failures.
     *
     * @param result prepatch build result
     * @param maxToLog maximum number of failures to log
     */
    private static void logPrepatchFailures(PrepatchBuildResult result, int maxToLog) {
        if (result == null || result.failures().isEmpty()) {
            return;
        }

        Log.getInstance().info("First ordinary prepatch failures:");

        int n = Math.min(maxToLog, result.failures().size());
        for (int i = 0; i < n; i++) {
            Log.getInstance().info("  " + result.failures().get(i));
        }

        if (result.failures().size() > n) {
            Log.getInstance().info("  ... "
                    + (result.failures().size() - n)
                    + " additional ordinary failures not shown.");
        }
    }

    /**
     * Logs algorithm statistics.
     *
     * @param stats statistics to log
     */
    private static void logStats(MosaicAlgorithmStats stats) {
        Log.getInstance().info("Cell intersection type counts:");

        stats.getCellTypeCounts().forEach((type, count) ->
                Log.getInstance().info("  " + type + ": " + count));
    }
}