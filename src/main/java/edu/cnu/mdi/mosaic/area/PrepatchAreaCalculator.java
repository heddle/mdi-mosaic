package edu.cnu.mdi.mosaic.area;

import java.util.ArrayList;
import java.util.List;

import edu.cnu.mdi.mosaic.geom.Vec3;
import edu.cnu.mdi.mosaic.patch.Prepatch;

/**
 * Computes approximate prepatch areas by replacing each curved prepatch
 * boundary with a sampled spherical polygon.
 */
public final class PrepatchAreaCalculator {

    /**
     * Hidden constructor for utility class.
     */
    private PrepatchAreaCalculator() {
    }

    /**
     * Computes the total area of a set of prepatches.
     *
     * @param prepatches prepatches to measure
     * @param radius spherical shell radius
     * @param samplesPerCurve number of samples per GENERAL curve
     * @return area result
     */
    public static PrepatchAreaResult compute(
            List<Prepatch> prepatches,
            double radius,
            int samplesPerCurve) {

        if (prepatches == null) {
            prepatches = List.of();
        }

        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException("radius must be positive and finite.");
        }

        int attempted = prepatches.size();
        int successful = 0;
        int failed = 0;
        double totalUnitArea = 0.0;

        ArrayList<String> failedCellIds = new ArrayList<>();

        for (Prepatch prepatch : prepatches) {
            List<Vec3> boundary =
                    PrepatchBoundarySampler.sampleBoundary(prepatch, samplesPerCurve);

            if (boundary.size() < 3) {
                failed++;
                failedCellIds.add(prepatch.cellId().toString());
                continue;
            }

            double unitArea =
                    SphericalPolygonArea.unsignedAreaUnitSphere(boundary);

            if (!Double.isFinite(unitArea) || unitArea <= 0.0) {
                failed++;
                failedCellIds.add(prepatch.cellId().toString());
                continue;
            }

            totalUnitArea += unitArea;
            successful++;
        }

        double totalArea = radius * radius * totalUnitArea;
        double normalizedArea = totalUnitArea / (4.0 * Math.PI);

        return new PrepatchAreaResult(
                samplesPerCurve,
                radius,
                attempted,
                successful,
                failed,
                totalArea,
                normalizedArea,
                failedCellIds);
    }

    /**
     * Runs a convergence test for several sample counts.
     *
     * @param prepatches prepatches to measure
     * @param radius spherical shell radius
     * @param sampleCounts sample counts
     * @return area results
     */
    public static List<PrepatchAreaResult> convergenceTest(
            List<Prepatch> prepatches,
            double radius,
            int... sampleCounts) {

        if (sampleCounts == null || sampleCounts.length == 0) {
            sampleCounts = new int[] {4, 8, 16, 32, 64};
        }

        ArrayList<PrepatchAreaResult> results = new ArrayList<>();

        for (int samples : sampleCounts) {
            results.add(compute(prepatches, radius, samples));
        }

        return results;
    }
}