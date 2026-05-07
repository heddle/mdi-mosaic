package edu.cnu.mdi.mosaic.patch;

import java.util.Arrays;

import edu.cnu.mdi.mosaic.cell.CellId;

/**
 * Diagnostic information for an ordinary cell that failed prepatch
 * construction.
 *
 * @param cellId the failed cell id
 * @param reason failure reason
 * @param edgeIntersectionCount number of edge-sphere intersections found
 * @param faceHitCounts number of edge intersections found on each of the six
 *        faces
 * @param message optional diagnostic message
 */
public record PrepatchFailure(
        CellId cellId,
        PrepatchFailureReason reason,
        int edgeIntersectionCount,
        int[] faceHitCounts,
        String message) {

    /**
     * Creates a failure diagnostic.
     *
     * @param cellId failed cell id
     * @param reason failure reason
     * @param edgeIntersectionCount edge-intersection count
     * @param faceHitCounts per-face hit counts
     * @param message optional message
     */
    public PrepatchFailure {
        if (cellId == null) {
            throw new IllegalArgumentException("cellId must not be null.");
        }
        if (reason == null) {
            throw new IllegalArgumentException("reason must not be null.");
        }
        if (faceHitCounts == null || faceHitCounts.length != 6) {
            throw new IllegalArgumentException("faceHitCounts must have length 6.");
        }

        faceHitCounts = Arrays.copyOf(faceHitCounts, faceHitCounts.length);
    }

    @Override
    public int[] faceHitCounts() {
        return Arrays.copyOf(faceHitCounts, faceHitCounts.length);
    }

    @Override
    public String toString() {
        return "cell=" + cellId
                + ", reason=" + reason
                + ", edgeHits=" + edgeIntersectionCount
                + ", faceHitCounts=" + Arrays.toString(faceHitCounts)
                + ((message == null || message.isBlank()) ? "" : ", " + message);
    }
}