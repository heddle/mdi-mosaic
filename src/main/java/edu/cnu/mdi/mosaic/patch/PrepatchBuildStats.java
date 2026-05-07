package edu.cnu.mdi.mosaic.patch;

import java.util.EnumMap;
import java.util.Map;

/**
 * Statistics from prepatch construction.
 *
 * @param ordinaryCellsVisited ordinary corner-straddle cells visited
 * @param ordinaryPrepatchesBuilt ordinary prepatches built
 * @param curvesBuilt total GENERAL curves built
 * @param deferredFacePenetrationCells face-penetration cells deferred
 * @param deferredBoundaryDegenerateCells boundary-degenerate cells deferred
 * @param deferredTangentCells tangent or near-tangent cells deferred
 * @param failedOrdinaryCells ordinary cells for which curves could not be built
 * @param failureReasonCounts counts of ordinary failures by reason
 */
public record PrepatchBuildStats(
        int ordinaryCellsVisited,
        int ordinaryPrepatchesBuilt,
        int curvesBuilt,
        int deferredFacePenetrationCells,
        int deferredBoundaryDegenerateCells,
        int deferredTangentCells,
        int failedOrdinaryCells,
        Map<PrepatchFailureReason, Integer> failureReasonCounts) {

    /**
     * Creates prepatch build statistics.
     *
     * @param ordinaryCellsVisited ordinary cells visited
     * @param ordinaryPrepatchesBuilt ordinary prepatches built
     * @param curvesBuilt curves built
     * @param deferredFacePenetrationCells deferred face-penetration cells
     * @param deferredBoundaryDegenerateCells deferred boundary-degenerate cells
     * @param deferredTangentCells deferred tangent cells
     * @param failedOrdinaryCells failed ordinary cells
     * @param failureReasonCounts failure counts by reason
     */
    public PrepatchBuildStats {
        EnumMap<PrepatchFailureReason, Integer> counts =
                new EnumMap<>(PrepatchFailureReason.class);

        for (PrepatchFailureReason reason : PrepatchFailureReason.values()) {
            counts.put(reason, 0);
        }

        if (failureReasonCounts != null) {
            failureReasonCounts.forEach((reason, count) -> {
                if (reason != null && count != null) {
                    counts.put(reason, count);
                }
            });
        }

        failureReasonCounts = counts;
    }

    /**
     * Creates empty build statistics.
     *
     * @return empty statistics
     */
    public static PrepatchBuildStats empty() {
        return new PrepatchBuildStats(0, 0, 0, 0, 0, 0, 0, null);
    }

    /**
     * Gets the count for a failure reason.
     *
     * @param reason failure reason
     * @return count
     */
    public int getFailureCount(PrepatchFailureReason reason) {
        if (reason == null) {
            return 0;
        }

        return failureReasonCounts.getOrDefault(reason, 0);
    }

    /**
     * Creates a multi-line summary.
     *
     * @return summary string
     */
    public String summary() {
        StringBuilder sb = new StringBuilder();

        sb.append("Prepatch build statistics:").append(System.lineSeparator());
        sb.append("  ordinary cells visited: ").append(ordinaryCellsVisited).append(System.lineSeparator());
        sb.append("  ordinary prepatches built: ").append(ordinaryPrepatchesBuilt).append(System.lineSeparator());
        sb.append("  GENERAL curves built: ").append(curvesBuilt).append(System.lineSeparator());
        sb.append("  deferred face-penetration cells: ").append(deferredFacePenetrationCells).append(System.lineSeparator());
        sb.append("  deferred boundary-degenerate cells: ").append(deferredBoundaryDegenerateCells).append(System.lineSeparator());
        sb.append("  deferred tangent cells: ").append(deferredTangentCells).append(System.lineSeparator());
        sb.append("  failed ordinary cells: ").append(failedOrdinaryCells).append(System.lineSeparator());

        if (failedOrdinaryCells > 0) {
            sb.append("  ordinary failure reasons:").append(System.lineSeparator());
            for (PrepatchFailureReason reason : PrepatchFailureReason.values()) {
                sb.append("    ")
                        .append(reason)
                        .append(": ")
                        .append(getFailureCount(reason))
                        .append(System.lineSeparator());
            }
        }

        return sb.toString();
    }
}