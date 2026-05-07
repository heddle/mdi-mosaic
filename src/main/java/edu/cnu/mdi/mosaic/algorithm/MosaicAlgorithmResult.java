package edu.cnu.mdi.mosaic.algorithm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import edu.cnu.mdi.mosaic.area.PrepatchAreaResult;
import edu.cnu.mdi.mosaic.cell.IntersectingCell;
import edu.cnu.mdi.mosaic.patch.Prepatch;

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
            MosaicAlgorithmStats stats,
            Duration duration) {

        this.intersectingCells = List.copyOf(
                Objects.requireNonNull(intersectingCells, "intersectingCells"));

        this.prepatches = List.copyOf(prepatches == null ? List.of() : prepatches);
        this.deferredPrepatchCells = List.copyOf(
                deferredPrepatchCells == null ? List.of() : deferredPrepatchCells);
        this.prepatchAreaResults = List.copyOf(
                prepatchAreaResults == null ? List.of() : prepatchAreaResults);

        this.stats = Objects.requireNonNull(stats, "stats");
        this.duration = Objects.requireNonNull(duration, "duration");
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
                MosaicAlgorithmStats.empty(),
                Duration.ZERO);
    }
    
    /**
	 * Gets human-readable strings summarizing prepatch area convergence results.
	 *
	 * @return list of result strings
	 */
    public ArrayList<String> getResultStrings() {
    	String cstr = "$orange$";
    			
		ArrayList<String> resultStrings = new ArrayList<>();
		for (PrepatchAreaResult result : prepatchAreaResults) {
			resultStrings.add(String.format("%sAnorm: %.17g", cstr, result.normalizedArea()));

		}
		return resultStrings;
	}
    
}