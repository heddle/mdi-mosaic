package edu.cnu.mdi.mosaic.theta;

/**
 * One row in a theta-splice convergence test.
 *
 * @param samplesPerCurve number of samples per parent GENERAL curve
 * @param thetaPatchCount number of theta patches built
 * @param failedPrepatchCount number of prepatches that failed theta splicing
 * @param thetaNormalizedArea total theta-patch area divided by 4*pi*R^2
 * @param referenceNormalizedArea reference prepatch normalized area
 * @param deltaNormalizedArea theta normalized area minus reference normalized area
 */
public record ThetaSpliceConvergenceResult(
        int samplesPerCurve,
        int thetaPatchCount,
        int failedPrepatchCount,
        double thetaNormalizedArea,
        double referenceNormalizedArea,
        double deltaNormalizedArea) {

    /**
     * Gets the relative normalized-area difference.
     *
     * @return relative area difference, or NaN if the reference is zero
     */
    public double relativeDelta() {
        return referenceNormalizedArea == 0.0
                ? Double.NaN
                : deltaNormalizedArea / referenceNormalizedArea;
    }

    /**
     * Creates a one-line log summary.
     *
     * @return summary string
     */
    public String summaryLine() {
        return String.format(
                "samples/curve=%3d  theta patches=%6d  failed=%4d  theta A_norm=%.17g  delta=%.3e",
                samplesPerCurve,
                thetaPatchCount,
                failedPrepatchCount,
                thetaNormalizedArea,
                deltaNormalizedArea);
    }
}