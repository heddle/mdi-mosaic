package edu.cnu.mdi.mosaic.algorithm;

import java.util.Arrays;

/**
 * User-adjustable options for the Mosaic exact algorithm.
 * <p>
 * These options control numerical and diagnostic behavior of the algorithm
 * without changing the grid specification itself.
 * </p>
 */
public final class MosaicAlgorithmOptions {

    /** Default production area samples per GENERAL curve. */
    public static final int DEFAULT_AREA_SAMPLES_PER_CURVE = 16;

    /** Default convergence-test sample counts. */
    private static final int[] DEFAULT_CONVERGENCE_SAMPLE_COUNTS = {
            4, 8, 16, 32, 64
    };

    /** Number of samples per GENERAL curve for the production area estimate. */
    private int areaSamplesPerCurve = DEFAULT_AREA_SAMPLES_PER_CURVE;

    /** Whether to run the area convergence diagnostic. */
    private boolean runAreaConvergenceTest = false;

    /** Sample counts used by the area convergence diagnostic. */
    private int[] convergenceSampleCounts =
            Arrays.copyOf(DEFAULT_CONVERGENCE_SAMPLE_COUNTS,
                    DEFAULT_CONVERGENCE_SAMPLE_COUNTS.length);

    /**
     * Creates default algorithm options.
     */
    public MosaicAlgorithmOptions() {
    }

    /**
     * Copy constructor.
     *
     * @param other options to copy
     */
    public MosaicAlgorithmOptions(MosaicAlgorithmOptions other) {
        if (other != null) {
            areaSamplesPerCurve = other.areaSamplesPerCurve;
            runAreaConvergenceTest = other.runAreaConvergenceTest;
            convergenceSampleCounts = Arrays.copyOf(
                    other.convergenceSampleCounts,
                    other.convergenceSampleCounts.length);
        }
    }

    /**
     * Gets the production area samples per curve.
     *
     * @return samples per curve
     */
    public int getAreaSamplesPerCurve() {
        return areaSamplesPerCurve;
    }

    /**
     * Sets the production area samples per curve.
     *
     * @param areaSamplesPerCurve samples per curve
     */
    public void setAreaSamplesPerCurve(int areaSamplesPerCurve) {
        if (areaSamplesPerCurve < 2) {
            throw new IllegalArgumentException(
                    "Area samples per curve must be at least 2.");
        }

        this.areaSamplesPerCurve = areaSamplesPerCurve;
    }

    /**
     * Checks whether the convergence test should run.
     *
     * @return true to run the convergence test
     */
    public boolean isRunAreaConvergenceTest() {
        return runAreaConvergenceTest;
    }

    /**
     * Sets whether the convergence test should run.
     *
     * @param runAreaConvergenceTest true to run the convergence test
     */
    public void setRunAreaConvergenceTest(boolean runAreaConvergenceTest) {
        this.runAreaConvergenceTest = runAreaConvergenceTest;
    }

    /**
     * Gets a defensive copy of the convergence sample counts.
     *
     * @return convergence sample counts
     */
    public int[] getConvergenceSampleCounts() {
        return Arrays.copyOf(convergenceSampleCounts,
                convergenceSampleCounts.length);
    }

    /**
     * Sets the convergence sample counts.
     *
     * @param counts sample counts
     */
    public void setConvergenceSampleCounts(int... counts) {
        if (counts == null || counts.length == 0) {
            throw new IllegalArgumentException(
                    "At least one convergence sample count is required.");
        }

        int[] copy = Arrays.copyOf(counts, counts.length);

        for (int count : copy) {
            if (count < 2) {
                throw new IllegalArgumentException(
                        "Convergence sample counts must be at least 2.");
            }
        }

        Arrays.sort(copy);
        convergenceSampleCounts = copy;
    }

    /**
     * Restores default options.
     */
    public void resetToDefaults() {
        areaSamplesPerCurve = DEFAULT_AREA_SAMPLES_PER_CURVE;
        runAreaConvergenceTest = true;
        convergenceSampleCounts = Arrays.copyOf(
                DEFAULT_CONVERGENCE_SAMPLE_COUNTS,
                DEFAULT_CONVERGENCE_SAMPLE_COUNTS.length);
    }

    /**
     * Creates a summary string.
     *
     * @return summary
     */
    public String summary() {
        return "Algorithm options:"
                + System.lineSeparator()
                + "  area samples per curve: " + areaSamplesPerCurve
                + System.lineSeparator()
                + "  area convergence test: " + runAreaConvergenceTest
                + System.lineSeparator()
                + "  convergence sample counts: "
                + Arrays.toString(convergenceSampleCounts);
    }
}