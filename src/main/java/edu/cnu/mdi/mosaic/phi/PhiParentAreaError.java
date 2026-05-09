package edu.cnu.mdi.mosaic.phi;

import edu.cnu.mdi.mosaic.patch.PoleClassification;

/**
 * Per-theta-parent phi-splice area conservation diagnostic.
 *
 * @param key theta-patch parent key
 * @param parentNormalizedArea parent theta-patch normalized area
 * @param phiNormalizedArea sum of phi-child normalized areas
 * @param deltaNormalizedArea phi sum minus parent area
 * @param childCount number of phi children
 * @param parentPoleClassification inherited pole classification
 */
public record PhiParentAreaError(
        ThetaPatchKey key,
        double parentNormalizedArea,
        double phiNormalizedArea,
        double deltaNormalizedArea,
        int childCount,
        PoleClassification parentPoleClassification) {

    /**
     * Gets absolute normalized-area error.
     *
     * @return absolute error
     */
    public double absDeltaNormalizedArea() {
        return Math.abs(deltaNormalizedArea);
    }

    /**
     * Gets relative error with respect to parent area.
     *
     * @return relative error
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
                "parent=(%s, ntheta=%d) parent=%.6e phi=%.6e delta=%+.6e rel=%+.3e children=%d pole=%s",
                key.cellId(),
                key.ntheta(),
                parentNormalizedArea,
                phiNormalizedArea,
                deltaNormalizedArea,
                relativeError(),
                childCount,
                parentPoleClassification);
    }
}