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
import edu.cnu.mdi.mosaic.phi.PhiAreaClosureDiagnostics;
import edu.cnu.mdi.mosaic.phi.PhiParentAreaDiagnosticBuilder;
import edu.cnu.mdi.mosaic.phi.PhiParentAreaDiagnostics;
import edu.cnu.mdi.mosaic.phi.PhiParentAreaError;
import edu.cnu.mdi.mosaic.phi.PhiPatch;
import edu.cnu.mdi.mosaic.phi.PhiSpliceConvergenceResult;
import edu.cnu.mdi.mosaic.phi.PhiSpliceFailure;
import edu.cnu.mdi.mosaic.phi.PhiSpliceResult;
import edu.cnu.mdi.mosaic.phi.PhiSpliceStats;
import edu.cnu.mdi.mosaic.phi.PhiSplicer;
import edu.cnu.mdi.mosaic.phi.PolarPhiSplicer;
import edu.cnu.mdi.mosaic.theta.ThetaParentAreaDiagnosticBuilder;
import edu.cnu.mdi.mosaic.theta.ThetaParentAreaDiagnostics;
import edu.cnu.mdi.mosaic.theta.ThetaParentAreaError;
import edu.cnu.mdi.mosaic.theta.ThetaSpliceConvergenceResult;
import edu.cnu.mdi.mosaic.theta.ThetaSpliceResult;
import edu.cnu.mdi.mosaic.theta.ThetaSplicer;

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
	 * @param options  algorithm options
	 * @return algorithm result
	 */
	public static MosaicAlgorithmResult run(MosaicGridSpec gridSpec, MosaicAlgorithmOptions options) {

		if (gridSpec == null) {
			throw new IllegalArgumentException("gridSpec must not be null.");
		}

		if (options == null) {
			options = new MosaicAlgorithmOptions();
		}

		Instant start = Instant.now();

		Log.getInstance().info("Starting algorithm...");
		Log.getInstance().info("Step 1: Find intersecting cells...");

		List<IntersectingCell> cells = IntersectingCellFinder.findIntersectingCells(gridSpec);

		MosaicAlgorithmStats stats = MosaicAlgorithmStats.fromIntersectingCells(cells);

		Log.getInstance().info("Found " + cells.size() + " intersecting cells.");
		logStats(stats);

		Log.getInstance().info("Step 2: Build ordinary prepatch boundary curves...");

		double radius = gridSpec.getSphericalGrid().getRadius();
		PrepatchBuildResult prepatchResult = PrepatchBuilder.buildPrepatches(cells, radius);

		Log.getInstance().info("Built " + prepatchResult.prepatches().size() + " ordinary prepatches.");

		Log.getInstance()
				.info("Deferred " + prepatchResult.deferredCells().size() + " cells for later degeneracy handling.");

		for (String line : prepatchResult.stats().summary().split("\\R")) {
			Log.getInstance().info(line);
		}

		logPrepatchFailures(prepatchResult, 10);

		PoleStats poleStats = PoleStats.fromPrepatches(prepatchResult.prepatches());
		for (String line : poleStats.summary().split("\\R")) {
			Log.getInstance().info(line);
		}

		// area test
		Log.getInstance().info("Step 2 area test: approximate ordinary prepatch areas...");

		PrepatchAreaResult productionAreaResult = PrepatchAreaCalculator.compute(prepatchResult.prepatches(), radius,
				options.getAreaSamplesPerCurve());

		Log.getInstance().info("  production " + productionAreaResult.summaryLine());

		List<PrepatchAreaResult> areaResults = new ArrayList<>();
		areaResults.add(productionAreaResult);

		Log.getInstance().info("Step 2 diagnostics: ordinary prepatch diagnostics...");

		PrepatchDiagnosticSummary diagnosticSummary = PrepatchDiagnosticBuilder.build(prepatchResult.prepatches(),
				radius, options.getAreaSamplesPerCurve());

		for (String line : diagnosticSummary.summary().split("\\R")) {
			Log.getInstance().info(line);
		}

		if (options.isRunAreaConvergenceTest()) {
			List<PrepatchAreaResult> convergenceResults = PrepatchAreaCalculator
					.convergenceTest(prepatchResult.prepatches(), radius, options.getConvergenceSampleCounts());

			Log.getInstance().info("  convergence test:");

			for (PrepatchAreaResult areaResult : convergenceResults) {
				Log.getInstance().info("    " + areaResult.summaryLine());
			}

			areaResults.addAll(convergenceResults);
		}

		PrepatchAreaResult finalAreaResult = areaResults.get(areaResults.size() - 1);

		if (!finalAreaResult.failedCellIds().isEmpty()) {
			Log.getInstance().warning("  Area calculation failed for " + finalAreaResult.failedCellIds().size()
					+ " prepatches at highest sample count.");
		}

		Log.getInstance().info("Step 3: Theta splice ordinary prepatches...");

		double referenceAreaNorm = productionAreaResult.normalizedArea();

		Log.getInstance().info(
				String.format("Theta splice reference includes pole prepatches: A_norm=%.17g", referenceAreaNorm));

		ThetaSpliceResult thetaResult = ThetaSplicer.splice(prepatchResult.prepatches(),
				gridSpec.getSphericalGrid().getThetaGrid(), radius, options.getAreaSamplesPerCurve(),
				referenceAreaNorm);

		ThetaParentAreaDiagnostics thetaParentDiagnostics = ThetaParentAreaDiagnosticBuilder.build(diagnosticSummary,
				thetaResult.thetaPatches());

		thetaResult = new ThetaSpliceResult(thetaResult.thetaPatches(), thetaResult.failures(), thetaResult.stats(),
				thetaParentDiagnostics);

		for (String line : thetaResult.stats().summary().split("\\R")) {
			Log.getInstance().info(line);
		}
		for (String line : thetaParentDiagnostics.summary().split("\\R")) {
			Log.getInstance().info(line);
		}

		if (!thetaParentDiagnostics.parentErrors().isEmpty()) {
			Log.getInstance().info("Worst theta parent area errors:");

			int n = Math.min(10, thetaParentDiagnostics.parentErrors().size());
			for (ThetaParentAreaError error : thetaParentDiagnostics.worstErrors(n)) {
				Log.getInstance().info("  " + error.summaryLine());
			}
		}

		List<ThetaSpliceConvergenceResult> thetaConvergenceResults = List.of();

		if (options.isRunAreaConvergenceTest()) {
			Log.getInstance().info("Step 3 convergence test: theta splice...");

			thetaConvergenceResults = ThetaSplicer.convergenceTest(prepatchResult.prepatches(),
					gridSpec.getSphericalGrid().getThetaGrid(), radius, referenceAreaNorm,
					options.getConvergenceSampleCounts());

			for (ThetaSpliceConvergenceResult result : thetaConvergenceResults) {
				Log.getInstance().info("  " + result.summaryLine());
			}
		}

		if (!thetaResult.failures().isEmpty()) {
			Log.getInstance().warning("Deferred theta splice prepatches:");

			int n = Math.min(10, thetaResult.failures().size());
			for (int i = 0; i < n; i++) {
				Log.getInstance().warning("  " + thetaResult.failures().get(i));
			}

			if (thetaResult.failures().size() > n) {
				Log.getInstance().warning("  ... " + (thetaResult.failures().size() - n)
						+ " additional theta splice failures not shown.");
			}
		}

		Log.getInstance().info("Step 4: Phi splice theta patches...");

		double nonPolarPhiReferenceAreaNorm = nonPolarThetaNormalizedArea(thetaResult);
		double polarPhiReferenceAreaNorm = polarThetaNormalizedArea(thetaResult);
		double fullPhiReferenceAreaNorm = nonPolarPhiReferenceAreaNorm + polarPhiReferenceAreaNorm;

		Log.getInstance()
				.info(String.format("Phi splice non-polar reference: A_norm=%.17g", nonPolarPhiReferenceAreaNorm));

		Log.getInstance()
				.info(String.format("Phi splice polar reference:     A_norm=%.17g", polarPhiReferenceAreaNorm));

		Log.getInstance().info(String.format("Phi splice full reference:      A_norm=%.17g", fullPhiReferenceAreaNorm));

		PhiSpliceResult nonPolarPhiResult = PhiSplicer.splice(nonPolarThetaPatches(thetaResult),
				gridSpec.getSphericalGrid().getPhiGrid(), radius, options.getAreaSamplesPerCurve(),
				nonPolarPhiReferenceAreaNorm);

		PhiSpliceResult polarPhiResult = PolarPhiSplicer.splice(polarThetaPatches(thetaResult),
				gridSpec.getSphericalGrid().getPhiGrid(), radius, options.getAreaSamplesPerCurve(),
				polarPhiReferenceAreaNorm);

		PhiSpliceResult phiResult = mergePhiResults(nonPolarPhiResult, polarPhiResult, fullPhiReferenceAreaNorm,
				radius);

		for (String line : phiResult.stats().summary().split("\\R")) {
			Log.getInstance().info(line);
		}

		Log.getInstance().info("  non-polar phi patches: " + nonPolarPhiResult.phiPatches().size());

		Log.getInstance().info("  polar phi patches:     " + polarPhiResult.phiPatches().size());

		PhiParentAreaDiagnostics phiParentDiagnostics = PhiParentAreaDiagnosticBuilder.build(thetaResult.thetaPatches(),
				phiResult.phiPatches());

		for (String line : phiParentDiagnostics.summary().split("\\R")) {
			Log.getInstance().info(line);
		}
		
		PhiAreaClosureDiagnostics phiAreaClosureDiagnostics =
		        PhiAreaClosureDiagnostics.build(
		                thetaResult.thetaPatches(),
		                phiResult.phiPatches());

		for (String line : phiAreaClosureDiagnostics.summary().split("\\R")) {
		    Log.getInstance().info(line);
		}

		if (!phiParentDiagnostics.parentErrors().isEmpty()) {
			Log.getInstance().info("Worst phi parent area errors:");

			int n = Math.min(10, phiParentDiagnostics.parentErrors().size());
			for (PhiParentAreaError error : phiParentDiagnostics.worstErrors(n)) {
				Log.getInstance().info("  " + error.summaryLine());
			}
		}

		if (!phiResult.failures().isEmpty()) {
			Log.getInstance().warning("First phi splice failures/deferrals:");

			int n = Math.min(10, phiResult.failures().size());
			for (int i = 0; i < n; i++) {
				Log.getInstance().warning("  " + phiResult.failures().get(i));
			}

			if (phiResult.failures().size() > n) {
				Log.getInstance().warning(
						"  ... " + (phiResult.failures().size() - n) + " additional phi splice failures not shown.");
			}
		}

		List<PhiSpliceConvergenceResult> phiConvergenceResults = List.of();

		if (options.isRunAreaConvergenceTest()) {
			Log.getInstance().info("Step 4 convergence test: polar phi splice...");

			phiConvergenceResults = PolarPhiSplicer.convergenceTest(polarThetaPatches(thetaResult),
					gridSpec.getSphericalGrid().getPhiGrid(), radius, polarPhiReferenceAreaNorm,
					options.getConvergenceSampleCounts());

			for (PhiSpliceConvergenceResult result : phiConvergenceResults) {
				Log.getInstance().info("  " + result.summaryLine());
			}
		}

		Duration duration = Duration.between(start, Instant.now());

		Log.getInstance().info("Algorithm steps 1-4 completed in " + duration.toMillis() + " ms.");

		return new MosaicAlgorithmResult(cells, prepatchResult.prepatches(), prepatchResult.deferredCells(),
				areaResults, diagnosticSummary, thetaResult, thetaConvergenceResults, phiResult, phiParentDiagnostics,
				phiConvergenceResults, stats, duration);
	}
	
	
	/**
	 * Gets theta patches not derived from polar prepatches.
	 *
	 * @param thetaResult theta result
	 * @return non-polar theta patches
	 */
	private static List<edu.cnu.mdi.mosaic.theta.ThetaPatch> nonPolarThetaPatches(
	        ThetaSpliceResult thetaResult) {

	    if (thetaResult == null || thetaResult.thetaPatches().isEmpty()) {
	        return List.of();
	    }

	    ArrayList<edu.cnu.mdi.mosaic.theta.ThetaPatch> patches = new ArrayList<>();

	    for (var thetaPatch : thetaResult.thetaPatches()) {
	        if (thetaPatch == null) {
	            continue;
	        }

	        if (thetaPatch.parentPoleClassification() != null
	                && thetaPatch.parentPoleClassification().hasPoleInvolvement()) {
	            continue;
	        }

	        patches.add(thetaPatch);
	    }

	    return patches;
	}

	/**
	 * Gets theta patches derived from polar prepatches.
	 *
	 * @param thetaResult theta result
	 * @return polar-derived theta patches
	 */
	private static List<edu.cnu.mdi.mosaic.theta.ThetaPatch> polarThetaPatches(
	        ThetaSpliceResult thetaResult) {

	    if (thetaResult == null || thetaResult.thetaPatches().isEmpty()) {
	        return List.of();
	    }

	    ArrayList<edu.cnu.mdi.mosaic.theta.ThetaPatch> patches = new ArrayList<>();

	    for (var thetaPatch : thetaResult.thetaPatches()) {
	        if (thetaPatch == null) {
	            continue;
	        }

	        if (thetaPatch.parentPoleClassification() != null
	                && thetaPatch.parentPoleClassification().hasPoleInvolvement()) {
	            patches.add(thetaPatch);
	        }
	    }

	    return patches;
	}

	/**
	 * Computes the normalized area of theta patches derived from polar prepatches.
	 *
	 * @param thetaResult theta-splice result
	 * @return normalized polar theta-patch area
	 */
	private static double polarThetaNormalizedArea(ThetaSpliceResult thetaResult) {
	    if (thetaResult == null || thetaResult.thetaPatches().isEmpty()) {
	        return 0.0;
	    }

	    double sum = 0.0;

	    for (var thetaPatch : thetaResult.thetaPatches()) {
	        if (thetaPatch == null) {
	            continue;
	        }

	        if (thetaPatch.parentPoleClassification() != null
	                && thetaPatch.parentPoleClassification().hasPoleInvolvement()) {
	            sum += thetaPatch.normalizedArea();
	        }
	    }

	    return sum;
	}

	/**
	 * Merges non-polar and polar phi-splice results into one final result.
	 *
	 * @param nonPolarResult non-polar result
	 * @param polarResult polar result
	 * @param referenceNormalizedArea full reference normalized area
	 * @param radius shell radius
	 * @return merged result
	 */
	private static PhiSpliceResult mergePhiResults(
	        PhiSpliceResult nonPolarResult,
	        PhiSpliceResult polarResult,
	        double referenceNormalizedArea,
	        double radius) {

	    ArrayList<PhiPatch> patches = new ArrayList<>();
	    ArrayList<PhiSpliceFailure> failures = new ArrayList<>();

	    int thetaPatchesVisited = 0;
	    int failedThetaPatches = 0;
	    int deferredPolarThetaPatches = 0;
	    int handledPolarThetaPatches = 0;

	    if (nonPolarResult != null) {
	        patches.addAll(nonPolarResult.phiPatches());
	        failures.addAll(nonPolarResult.failures());

	        thetaPatchesVisited += nonPolarResult.stats().thetaPatchesVisited();
	        failedThetaPatches += nonPolarResult.stats().failedThetaPatches();
	        deferredPolarThetaPatches += nonPolarResult.stats().deferredPolarThetaPatches();
	        handledPolarThetaPatches += nonPolarResult.stats().handledPolarThetaPatches();
	    }

	    if (polarResult != null) {
	        patches.addAll(polarResult.phiPatches());
	        failures.addAll(polarResult.failures());

	        thetaPatchesVisited += polarResult.stats().thetaPatchesVisited();
	        failedThetaPatches += polarResult.stats().failedThetaPatches();
	        deferredPolarThetaPatches += polarResult.stats().deferredPolarThetaPatches();
	        handledPolarThetaPatches += polarResult.stats().handledPolarThetaPatches();
	    }

	    double totalArea = 0.0;

	    for (PhiPatch patch : patches) {
	        totalArea += patch.area();
	    }

	    double normalizedArea = totalArea / (4.0 * Math.PI * radius * radius);

	    PhiSpliceStats stats = new PhiSpliceStats(
	            thetaPatchesVisited,
	            patches.size(),
	            failedThetaPatches,
	            deferredPolarThetaPatches,
	            handledPolarThetaPatches,
	            totalArea,
	            normalizedArea,
	            referenceNormalizedArea);

	    return new PhiSpliceResult(patches, failures, stats);
	}

	/**
	 * Logs a small sample of ordinary prepatch-construction failures.
	 *
	 * @param result   prepatch build result
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
			Log.getInstance()
					.info("  ... " + (result.failures().size() - n) + " additional ordinary failures not shown.");
		}
	}

	/**
	 * Computes the normalized area of theta patches that are not derived from polar
	 * prepatches.
	 *
	 * @param thetaResult theta-splice result
	 * @return normalized non-polar theta-patch area
	 */
	private static double nonPolarThetaNormalizedArea(ThetaSpliceResult thetaResult) {
		if (thetaResult == null || thetaResult.thetaPatches().isEmpty()) {
			return 0.0;
		}

		double sum = 0.0;

		for (var thetaPatch : thetaResult.thetaPatches()) {
			if (thetaPatch == null) {
				continue;
			}

			if (thetaPatch.parentPoleClassification() != null
					&& thetaPatch.parentPoleClassification().hasPoleInvolvement()) {
				continue;
			}

			sum += thetaPatch.normalizedArea();
		}

		return sum;
	}

	/**
	 * Computes the normalized area of non-pole prepatches from the prepatch
	 * diagnostic summary.
	 *
	 * @param diagnosticSummary prepatch diagnostic summary
	 * @return normalized area of prepatches not involving either pole
	 */
	private static double nonPolePrepatchNormalizedArea(PrepatchDiagnosticSummary diagnosticSummary) {

		if (diagnosticSummary == null || diagnosticSummary.diagnostics().isEmpty()) {
			return 0.0;
		}

		double sum = 0.0;

		for (var diagnostic : diagnosticSummary.diagnostics()) {
			if (diagnostic == null || !diagnostic.valid()) {
				continue;
			}

			if (diagnostic.poleClassification() != null && diagnostic.poleClassification().hasPoleInvolvement()) {
				continue;
			}

			sum += diagnostic.normalizedArea();
		}

		return sum;
	}

	/**
	 * Logs algorithm statistics.
	 *
	 * @param stats statistics to log
	 */
	private static void logStats(MosaicAlgorithmStats stats) {
		Log.getInstance().info("Cell intersection type counts:");

		stats.getCellTypeCounts().forEach((type, count) -> Log.getInstance().info("  " + type + ": " + count));
	}
}