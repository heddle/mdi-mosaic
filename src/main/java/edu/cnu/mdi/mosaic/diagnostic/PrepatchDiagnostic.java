package edu.cnu.mdi.mosaic.diagnostic;

import edu.cnu.mdi.mosaic.cell.CellId;
import edu.cnu.mdi.mosaic.patch.PoleClassification;

/**
 * Diagnostic information for one ordinary prepatch.
 *
 * @param cellId Cartesian cell id
 * @param curveCount number of GENERAL boundary curves
 * @param boundaryPointCount number of sampled boundary points
 * @param area physical prepatch area
 * @param normalizedArea area divided by {@code 4*pi*R^2}
 * @param thetaMin minimum sampled GSM theta, radians
 * @param thetaMax maximum sampled GSM theta, radians
 * @param phiSpan minimal sampled GSM phi span, radians
 * @param poleClassification pole-involvement classification
 * @param valid true if the diagnostic was successfully computed
 * @param message optional diagnostic message
 */
public record PrepatchDiagnostic(
        CellId cellId,
        int curveCount,
        int boundaryPointCount,
        double area,
        double normalizedArea,
        double thetaMin,
        double thetaMax,
        double phiSpan,
        PoleClassification poleClassification,
        boolean valid,
        String message) {

    /**
     * Gets the theta range in radians.
     *
     * @return {@code thetaMax - thetaMin}
     */
    public double thetaRange() {
        return thetaMax - thetaMin;
    }

    /**
     * Gets the minimum theta in degrees.
     *
     * @return minimum theta in degrees
     */
    public double thetaMinDeg() {
        return Math.toDegrees(thetaMin);
    }

    /**
     * Gets the maximum theta in degrees.
     *
     * @return maximum theta in degrees
     */
    public double thetaMaxDeg() {
        return Math.toDegrees(thetaMax);
    }

    /**
     * Gets the theta range in degrees.
     *
     * @return theta range in degrees
     */
    public double thetaRangeDeg() {
        return Math.toDegrees(thetaRange());
    }

    /**
     * Gets the minimal phi span in degrees.
     *
     * @return phi span in degrees
     */
    public double phiSpanDeg() {
        return Math.toDegrees(phiSpan);
    }

    /**
     * Gets a compact one-line summary.
     *
     * @return summary string
     */
    public String summaryLine() {
        if (!valid) {
            return String.format("cell=%s INVALID %s",
                    cellId, message == null ? "" : message);
        }

        return String.format(
                "cell=%s curves=%d A_norm=%.17g theta=[%.3f, %.3f] phiSpan=%.3f pole=%s",
                cellId,
                curveCount,
                normalizedArea,
                thetaMinDeg(),
                thetaMaxDeg(),
                phiSpanDeg(),
                poleClassification);
    }
}