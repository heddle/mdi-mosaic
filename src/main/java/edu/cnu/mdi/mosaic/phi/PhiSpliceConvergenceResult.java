package edu.cnu.mdi.mosaic.phi;

/**
 * One row in a phi-splice convergence test.
 *
 * @param samplesPerMeridianArc number of samples inserted along constant-phi
 *        meridian arcs
 * @param phiPatchCount number of phi patches built
 * @param failedThetaPatchCount number of theta patches that failed phi splicing
 * @param phiNormalizedArea total phi-patch area divided by 4*pi*R^2
 * @param referenceNormalizedArea reference theta normalized area
 * @param deltaNormalizedArea phi normalized area minus reference normalized area
 */
public record PhiSpliceConvergenceResult(
        int samplesPerMeridianArc,
        int phiPatchCount,
        int failedThetaPatchCount,
        double phiNormalizedArea,
        double referenceNormalizedArea,
        double deltaNormalizedArea) {

    /**
     * Gets relative normalized-area difference.
     *
     * @return relative delta, or NaN if reference is zero
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
                "samples/meridian=%3d  phi patches=%6d  failed=%4d  phi A_norm=%.17g  delta=%.3e",
                samplesPerMeridianArc,
                phiPatchCount,
                failedThetaPatchCount,
                phiNormalizedArea,
                deltaNormalizedArea);
    }
}