package edu.cnu.mdi.mosaic.diagnostic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.cnu.mdi.mosaic.area.PrepatchBoundarySampler;
import edu.cnu.mdi.mosaic.area.SphericalPolygonArea;
import edu.cnu.mdi.mosaic.geom.Vec3;
import edu.cnu.mdi.mosaic.patch.PoleRelation;
import edu.cnu.mdi.mosaic.patch.Prepatch;

/**
 * Builds diagnostic information for ordinary prepatches.
 */
public final class PrepatchDiagnosticBuilder {

    /** Small numerical tolerance. */
    private static final double TOL = 1.0e-14;

    /**
     * Hidden constructor for utility class.
     */
    private PrepatchDiagnosticBuilder() {
    }

    /**
     * Builds diagnostics for a set of prepatches.
     *
     * @param prepatches prepatches
     * @param radius spherical shell radius
     * @param samplesPerCurve samples per GENERAL curve
     * @return diagnostic summary
     */
    public static PrepatchDiagnosticSummary build(
            List<Prepatch> prepatches,
            double radius,
            int samplesPerCurve) {

        if (prepatches == null) {
            prepatches = List.of();
        }

        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException("radius must be positive and finite.");
        }

        ArrayList<PrepatchDiagnostic> diagnostics = new ArrayList<>();

        int valid = 0;
        int invalid = 0;
        double totalArea = 0.0;
        double minAreaNorm = Double.POSITIVE_INFINITY;
        double maxAreaNorm = Double.NEGATIVE_INFINITY;
        double minThetaRange = Double.POSITIVE_INFINITY;
        double maxThetaRange = Double.NEGATIVE_INFINITY;

        for (Prepatch prepatch : prepatches) {
            PrepatchDiagnostic diagnostic =
                    buildOne(prepatch, radius, samplesPerCurve);

            diagnostics.add(diagnostic);

            if (diagnostic.valid()) {
                valid++;
                totalArea += diagnostic.area();

                minAreaNorm = Math.min(minAreaNorm, diagnostic.normalizedArea());
                maxAreaNorm = Math.max(maxAreaNorm, diagnostic.normalizedArea());

                double thetaRange = diagnostic.thetaRange();
                minThetaRange = Math.min(minThetaRange, thetaRange);
                maxThetaRange = Math.max(maxThetaRange, thetaRange);
            } else {
                invalid++;
            }
        }

        if (valid == 0) {
            minAreaNorm = 0.0;
            maxAreaNorm = 0.0;
            minThetaRange = 0.0;
            maxThetaRange = 0.0;
        }

        double normalizedArea = totalArea / (4.0 * Math.PI * radius * radius);

        return new PrepatchDiagnosticSummary(
                diagnostics,
                valid,
                invalid,
                totalArea,
                normalizedArea,
                minAreaNorm,
                maxAreaNorm,
                minThetaRange,
                maxThetaRange);
    }

    /**
     * Builds diagnostics for one prepatch.
     *
     * @param prepatch prepatch
     * @param radius radius
     * @param samplesPerCurve samples per curve
     * @return diagnostic
     */
    private static PrepatchDiagnostic buildOne(
            Prepatch prepatch,
            double radius,
            int samplesPerCurve) {

        if (prepatch == null) {
            return invalid(null, "null prepatch");
        }

        List<Vec3> boundary =
                PrepatchBoundarySampler.sampleBoundary(prepatch, samplesPerCurve);

        if (boundary.size() < 3) {
            return invalid(prepatch, "could not sample ordered boundary");
        }

        double unitArea = SphericalPolygonArea.unsignedAreaUnitSphere(boundary);

        if (!Double.isFinite(unitArea) || unitArea <= 0.0) {
            return invalid(prepatch, "invalid spherical polygon area");
        }

        double area = radius * radius * unitArea;
        double normalizedArea = unitArea / (4.0 * Math.PI);

        ThetaPhiRange range = thetaPhiRange(boundary, prepatch);

        return new PrepatchDiagnostic(
                prepatch.cellId(),
                prepatch.curveCount(),
                boundary.size(),
                area,
                normalizedArea,
                range.thetaMin(),
                range.thetaMax(),
                range.phiSpan(),
                prepatch.poleClassification(),
                true,
                "");
    }

    /**
     * Creates an invalid diagnostic.
     *
     * @param prepatch prepatch, possibly null
     * @param message diagnostic message
     * @return invalid diagnostic
     */
    private static PrepatchDiagnostic invalid(Prepatch prepatch, String message) {
        return new PrepatchDiagnostic(
                prepatch == null ? null : prepatch.cellId(),
                prepatch == null ? 0 : prepatch.curveCount(),
                0,
                0.0,
                0.0,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                prepatch == null ? null : prepatch.poleClassification(),
                false,
                message);
    }

    /**
     * Computes theta and phi span diagnostics from sampled boundary points.
     *
     * @param points sampled boundary points
     * @param prepatch owning prepatch
     * @return theta/phi range
     */
    private static ThetaPhiRange thetaPhiRange(List<Vec3> points, Prepatch prepatch) {
        double thetaMin = Double.POSITIVE_INFINITY;
        double thetaMax = Double.NEGATIVE_INFINITY;

        ArrayList<Double> phis = new ArrayList<>();

        for (Vec3 p : points) {
            double r = p.norm();
            if (!Double.isFinite(r) || r < TOL) {
                continue;
            }

            double zOverR = clamp(p.z() / r, -1.0, 1.0);
            double theta = Math.acos(zOverR);
            double phi = Math.atan2(p.y(), p.x());

            thetaMin = Math.min(thetaMin, theta);
            thetaMax = Math.max(thetaMax, theta);
            phis.add(phi);
        }

        if (prepatch != null && prepatch.poleClassification() != null) {
            if (prepatch.poleClassification().north() != PoleRelation.NONE) {
                thetaMin = 0.0;
            }

            if (prepatch.poleClassification().south() != PoleRelation.NONE) {
                thetaMax = Math.PI;
            }
        }

        if (!Double.isFinite(thetaMin) || !Double.isFinite(thetaMax)) {
            thetaMin = Double.NaN;
            thetaMax = Double.NaN;
        }

        double phiSpan = minimalCircularSpan(phis);

        return new ThetaPhiRange(thetaMin, thetaMax, phiSpan);
    }

    /**
     * Computes the minimal angular span containing a set of angles.
     *
     * @param phis angles in radians
     * @return minimal circular span in radians
     */
    private static double minimalCircularSpan(List<Double> phis) {
        if (phis == null || phis.isEmpty()) {
            return Double.NaN;
        }

        if (phis.size() == 1) {
            return 0.0;
        }

        ArrayList<Double> values = new ArrayList<>();

        for (double phi : phis) {
            if (Double.isFinite(phi)) {
                values.add(toTwoPi(phi));
            }
        }

        if (values.isEmpty()) {
            return Double.NaN;
        }

        Collections.sort(values);

        double largestGap = 0.0;

        for (int i = 0; i < values.size() - 1; i++) {
            largestGap = Math.max(largestGap, values.get(i + 1) - values.get(i));
        }

        double wrapGap = values.get(0) + 2.0 * Math.PI - values.get(values.size() - 1);
        largestGap = Math.max(largestGap, wrapGap);

        return 2.0 * Math.PI - largestGap;
    }

    /**
     * Converts an angle to [0, 2*pi).
     *
     * @param angle angle
     * @return normalized angle
     */
    private static double toTwoPi(double angle) {
        double a = angle % (2.0 * Math.PI);
        return (a < 0.0) ? a + 2.0 * Math.PI : a;
    }

    /**
     * Clamps a value.
     *
     * @param value value
     * @param lo lower bound
     * @param hi upper bound
     * @return clamped value
     */
    private static double clamp(double value, double lo, double hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    /**
     * Theta/phi diagnostic range.
     *
     * @param thetaMin minimum theta
     * @param thetaMax maximum theta
     * @param phiSpan minimal phi span
     */
    private record ThetaPhiRange(double thetaMin, double thetaMax, double phiSpan) {
    }
}