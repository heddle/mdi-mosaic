package edu.cnu.mdi.mosaic.theta;

import edu.cnu.mdi.mosaic.cell.CellId;
import edu.cnu.mdi.mosaic.patch.PoleClassification;

/**
 * Per-parent-prepatch theta-splice area conservation diagnostic.
 *
 * @param cellId parent prepatch cell id
 * @param parentNormalizedArea parent prepatch normalized area
 * @param thetaNormalizedArea sum of theta-child normalized areas
 * @param deltaNormalizedArea theta sum minus parent area
 * @param childCount number of theta children
 * @param parentPoleClassification pole classification of the parent prepatch
 */
public record ThetaParentAreaError(
        CellId cellId,
        double parentNormalizedArea,
        double thetaNormalizedArea,
        double deltaNormalizedArea,
        int childCount,
        PoleClassification parentPoleClassification) {

    /**
     * Gets the absolute normalized-area error.
     *
     * @return absolute error
     */
    public double absDeltaNormalizedArea() {
        return Math.abs(deltaNormalizedArea);
    }

    /**
     * Gets the relative error with respect to the parent area.
     *
     * @return relative error, or NaN if parent area is zero
     */
    public double relativeError() {
        return parentNormalizedArea == 0.0
                ? Double.NaN
                : deltaNormalizedArea / parentNormalizedArea;
    }

    /**
     * Creates a compact summary line.
     *
     * @return summary line
     */
    public String summaryLine() {
        return String.format(
                "cell=%s parent=%.6e theta=%.6e delta=%+.6e rel=%+.3e children=%d pole=%s",
                cellId,
                parentNormalizedArea,
                thetaNormalizedArea,
                deltaNormalizedArea,
                relativeError(),
                childCount,
                parentPoleClassification);
    }
}