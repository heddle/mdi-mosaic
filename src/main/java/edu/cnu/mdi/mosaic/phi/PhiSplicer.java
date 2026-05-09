package edu.cnu.mdi.mosaic.phi;

import java.util.ArrayList;
import java.util.List;

import edu.cnu.mdi.mosaic.area.SphericalPolygonArea;
import edu.cnu.mdi.mosaic.geom.Vec3;
import edu.cnu.mdi.mosaic.grid.Grid1D;
import edu.cnu.mdi.mosaic.theta.ThetaPatch;

/**
 * Splits theta patches by spherical phi bands.
 * <p>
 * A phi band is bounded by meridians:
 * </p>
 *
 * <pre>
 * phi_i <= phi <= phi_{i+1}
 * </pre>
 *
 * <p>
 * Each constant-phi boundary is a vertical great-circle plane through the GSM
 * z axis. This first implementation clips sampled theta-patch boundaries
 * against those meridian half-spaces and inserts meridian samples where
 * necessary.
 * </p>
 */
public final class PhiSplicer {

    /** Small tolerance. */
    private static final double TOL = 1.0e-12;

    /** Duplicate-point tolerance squared. */
    private static final double DUP_TOL2 = 1.0e-18;

    /**
     * Hidden constructor.
     */
    private PhiSplicer() {
    }

    /**
     * Splices theta patches by phi.
     *
     * @param thetaPatches theta patches
     * @param phiGrid phi grid
     * @param radius spherical shell radius
     * @param samplesPerMeridianArc samples used when inserting constant-phi arcs
     * @param referenceNormalizedArea theta-patch reference area
     * @return phi-splice result
     */
    public static PhiSpliceResult splice(
            List<ThetaPatch> thetaPatches,
            Grid1D phiGrid,
            double radius,
            int samplesPerMeridianArc,
            double referenceNormalizedArea) {

        if (thetaPatches == null) {
            thetaPatches = List.of();
        }

        if (phiGrid == null) {
            throw new IllegalArgumentException("phiGrid must not be null.");
        }

        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException("radius must be positive and finite.");
        }

        ArrayList<PhiPatch> phiPatches = new ArrayList<>();
        ArrayList<PhiSpliceFailure> failures = new ArrayList<>();

        double totalArea = 0.0;
        int failedThetaPatches = 0;
        int deferredPolarThetaPatches = 0;

        for (ThetaPatch thetaPatch : thetaPatches) {
            if (thetaPatch == null || thetaPatch.boundary().size() < 3) {
                continue;
            }

            if (thetaPatch.parentPoleClassification() != null
                    && thetaPatch.parentPoleClassification().hasPoleInvolvement()) {
                deferredPolarThetaPatches++;

                failures.add(new PhiSpliceFailure(
                        thetaPatch.parentCellId(),
                        thetaPatch.ntheta(),
                        "deferred polar-derived theta patch: "
                                + thetaPatch.parentPoleClassification()));

                continue;
            }


            int builtForTheta = 0;

            /*
             * The robust initial implementation simply tests all phi cells. This
             * avoids seam assumptions and is fast enough at current grid sizes.
             */
            for (int nphi = 0; nphi < phiGrid.numCells(); nphi++) {
                double phi0 = phiGrid.valueAt(nphi);
                double phi1 = phiGrid.valueAt(nphi + 1);

                List<Vec3> clipped = clipToPhiBand(
                        thetaPatch.boundary(),
                        radius,
                        phi0,
                        phi1,
                        samplesPerMeridianArc);

                if (clipped.size() < 3) {
                    continue;
                }

                double unitArea = SphericalPolygonArea.unsignedAreaUnitSphere(clipped);

                if (!Double.isFinite(unitArea) || unitArea <= 0.0) {
                    continue;
                }

                double area = radius * radius * unitArea;
                double normalizedArea = unitArea / (4.0 * Math.PI);

                phiPatches.add(new PhiPatch(
                        thetaPatch.parentCellId(),
                        thetaPatch.ntheta(),
                        nphi,
                        clipped,
                        area,
                        normalizedArea,
                        thetaPatch.parentPoleClassification()));

                totalArea += area;
                builtForTheta++;
            }

            if (builtForTheta == 0) {
                failedThetaPatches++;

                failures.add(new PhiSpliceFailure(
                        thetaPatch.parentCellId(),
                        thetaPatch.ntheta(),
                        "no phi patches built from theta-patch boundary"));
            }
        }

        double normalizedArea = totalArea / (4.0 * Math.PI * radius * radius);

        PhiSpliceStats stats = new PhiSpliceStats(
                thetaPatches.size(),
                phiPatches.size(),
                failedThetaPatches,
                deferredPolarThetaPatches,
                0,
                totalArea,
                normalizedArea,
                referenceNormalizedArea);
        
        return new PhiSpliceResult(phiPatches, failures, stats);
    }

    /**
     * Runs a phi-splice convergence test.
     *
     * @param thetaPatches theta patches
     * @param phiGrid phi grid
     * @param radius radius
     * @param referenceNormalizedArea theta reference area
     * @param sampleCounts meridian-arc sample counts
     * @return convergence rows
     */
    public static List<PhiSpliceConvergenceResult> convergenceTest(
            List<ThetaPatch> thetaPatches,
            Grid1D phiGrid,
            double radius,
            double referenceNormalizedArea,
            int... sampleCounts) {

        if (sampleCounts == null || sampleCounts.length == 0) {
            sampleCounts = new int[] {4, 8, 16, 32, 64};
        }

        ArrayList<PhiSpliceConvergenceResult> results = new ArrayList<>();

        for (int samples : sampleCounts) {
            PhiSpliceResult result = splice(
                    thetaPatches,
                    phiGrid,
                    radius,
                    samples,
                    referenceNormalizedArea);

            PhiSpliceStats stats = result.stats();

            results.add(new PhiSpliceConvergenceResult(
                    samples,
                    stats.phiPatchesBuilt(),
                    stats.failedThetaPatches(),
                    stats.normalizedArea(),
                    stats.referenceNormalizedArea(),
                    stats.normalizedAreaDelta()));
        }

        return results;
    }

 

    /**
     * Projects a point to a specific phi meridian, preserving z when possible.
     *
     * @param p input point
     * @param radius radius
     * @param phi phi meridian
     * @return projected point
     */
    private static Vec3 projectToPhiMeridian(Vec3 p, double radius, double phi) {
        phi = normalizeAngle(phi);

        double z = clamp(p.z(), -radius, radius);
        double rho2 = radius * radius - z * z;

        if (rho2 < 0.0 && rho2 > -1.0e-10) {
            rho2 = 0.0;
        }

        double rho = Math.sqrt(Math.max(0.0, rho2));

        return new Vec3(
                rho * Math.cos(phi),
                rho * Math.sin(phi),
                z);
    }
    /**
     * Inserts samples along adjacent boundary segments lying on one of the two
     * local phi meridians.
     *
     * @param polygon clipped polygon
     * @param radius radius
     * @param phiCenter phi-band center
     * @param uMin lower relative-phi boundary
     * @param uMax upper relative-phi boundary
     * @param samples samples per meridian arc
     * @return boundary with meridian samples inserted
     */
    private static List<Vec3> insertPhiMeridianArcs(
            List<Vec3> polygon,
            double radius,
            double phiCenter,
            double uMin,
            double uMax,
            int samples) {

        if (polygon == null || polygon.size() < 3) {
            return List.of();
        }

        ArrayList<Vec3> output = new ArrayList<>();

        for (int i = 0; i < polygon.size(); i++) {
            Vec3 a = polygon.get(i);
            Vec3 b = polygon.get((i + 1) % polygon.size());

            addIfDistinct(output, a);

            Double sharedU = sharedRelativePhiBoundary(a, b, phiCenter, uMin, uMax);

            if (sharedU != null) {
                double phi = phiCenter + sharedU;

                List<Vec3> arc = meridianArc(a, b, radius, phi, samples);

                for (int j = 1; j < arc.size() - 1; j++) {
                    addIfDistinct(output, arc.get(j));
                }
            }
        }

        return removeConsecutiveDuplicates(output);
    }
 
    /**
     * Clips a sampled spherical polygon to one phi band.
     *
     * @param polygon input boundary
     * @param radius radius
     * @param phi0 lower phi boundary
     * @param phi1 upper phi boundary
     * @param samplesPerMeridianArc samples inserted along meridians
     * @return clipped boundary
     */
    private static List<Vec3> clipToPhiBand(
            List<Vec3> polygon,
            double radius,
            double phi0,
            double phi1,
            int samplesPerMeridianArc) {

        if (polygon == null || polygon.size() < 3) {
            return List.of();
        }

        double width = positiveDelta(phi0, phi1);

        /*
         * Mosaic phi cells should be narrower than pi. If this ever fails, the
         * local-phi clipping assumptions need to be revisited.
         */
        if (!(width > 0.0 && width < Math.PI)) {
            return List.of();
        }

        double phiCenter = normalizeAngle(phi0 + 0.5 * width);
        double uMin = -0.5 * width;
        double uMax = 0.5 * width;

        List<Vec3> clipped = clipByRelativePhi(
                polygon, radius, phiCenter, uMin, true);

        clipped = clipByRelativePhi(
                clipped, radius, phiCenter, uMax, false);

        clipped = removeConsecutiveDuplicates(clipped);

        if (clipped.size() < 3) {
            return List.of();
        }

        int arcSamples = Math.max(2, samplesPerMeridianArc);

        return insertPhiMeridianArcs(
                clipped,
                radius,
                phiCenter,
                uMin,
                uMax,
                arcSamples);
    }

    /**
     * Clips a polygon by a local relative-phi boundary.
     *
     * @param input input polygon
     * @param radius radius
     * @param phiCenter center of the phi band
     * @param uCut relative phi cut
     * @param keepAbove true to keep u >= uCut, false to keep u <= uCut
     * @return clipped polygon
     */
    private static List<Vec3> clipByRelativePhi(
            List<Vec3> input,
            double radius,
            double phiCenter,
            double uCut,
            boolean keepAbove) {

        if (input == null || input.size() < 3) {
            return List.of();
        }

        ArrayList<Vec3> output = new ArrayList<>();

        Vec3 s = input.get(input.size() - 1);
        double uS = relativePhi(s, phiCenter);

        for (Vec3 e : input) {
            double uE = relativePhi(e, phiCenter);

            /*
             * Unwrap the endpoint relative to the start so the edge is treated as a
             * short local phi interval rather than jumping across +/-pi.
             */
            double uEUnwrapped = unwrapNear(uE, uS);

            boolean sInside = insideRelativePhi(uS, uCut, keepAbove);
            boolean eInside = insideRelativePhi(uEUnwrapped, uCut, keepAbove);

            if (eInside) {
                if (!sInside) {
                    addIfDistinct(output, intersectionAtRelativePhi(
                            s, e, radius, phiCenter, uCut, uS, uEUnwrapped));
                }
                addIfDistinct(output, e);
            } else if (sInside) {
                addIfDistinct(output, intersectionAtRelativePhi(
                        s, e, radius, phiCenter, uCut, uS, uEUnwrapped));
            }

            s = e;
            uS = relativePhi(s, phiCenter);
        }

        return removeConsecutiveDuplicates(output);
    }

    /**
     * Tests relative-phi half-space membership.
     *
     * @param u local relative phi
     * @param uCut cut value
     * @param keepAbove true for u >= uCut
     * @return true if inside
     */
    private static boolean insideRelativePhi(double u, double uCut, boolean keepAbove) {
        return keepAbove ? u >= uCut - TOL : u <= uCut + TOL;
    }

    /**
     * Finds an approximate intersection of a spherical edge with a local phi cut.
     * <p>
     * This deliberately projects to the specific meridian ray
     * {@code phiCenter + uCut}, avoiding the antipodal meridian ambiguity of the
     * full meridian plane.
     * </p>
     *
     * @param a start point
     * @param b end point
     * @param radius radius
     * @param phiCenter phi-band center
     * @param uCut relative phi cut
     * @param uA relative phi at start
     * @param uB relative phi at end, unwrapped near uA
     * @return intersection point
     */
    private static Vec3 intersectionAtRelativePhi(
            Vec3 a,
            Vec3 b,
            double radius,
            double phiCenter,
            double uCut,
            double uA,
            double uB) {

        double denom = uB - uA;

        if (Math.abs(denom) < TOL) {
            return projectToPhiMeridian(a, radius, phiCenter + uCut);
        }

        double t = (uCut - uA) / denom;
        t = clamp(t, 0.0, 1.0);

        /*
         * Slerp keeps the intersection on the sphere. Then project to the specific
         * meridian ray to remove any tiny numerical drift.
         */
        Vec3 p = slerp(a, b, radius, t);
        return projectToPhiMeridian(p, radius, phiCenter + uCut);
    }

 

    /**
     * Determines whether two adjacent points lie on the same local phi boundary.
     *
     * @param a first point
     * @param b second point
     * @param phiCenter phi-band center
     * @param uMin lower relative boundary
     * @param uMax upper relative boundary
     * @return shared relative boundary, or null
     */
    private static Double sharedRelativePhiBoundary(
            Vec3 a,
            Vec3 b,
            double phiCenter,
            double uMin,
            double uMax) {

        double tol = 1.0e-8;

        double ua = relativePhi(a, phiCenter);
        double ub = unwrapNear(relativePhi(b, phiCenter), ua);

        boolean aOnMin = Math.abs(ua - uMin) <= tol;
        boolean bOnMin = Math.abs(ub - uMin) <= tol;

        if (aOnMin && bOnMin) {
            return uMin;
        }

        boolean aOnMax = Math.abs(ua - uMax) <= tol;
        boolean bOnMax = Math.abs(ub - uMax) <= tol;

        if (aOnMax && bOnMax) {
            return uMax;
        }

        return null;
    }

    /**
     * Computes local relative phi in [-pi, pi].
     *
     * @param p point
     * @param phiCenter center angle
     * @return relative phi
     */
    private static double relativePhi(Vec3 p, double phiCenter) {
        if (p == null) {
            return 0.0;
        }

        /*
         * At the pole, phi is mathematically undefined. Returning zero is adequate
         * for now because true polar behavior will need its own final-patch special
         * handling anyway.
         */
        double phi = Math.atan2(p.y(), p.x());
        return wrapPi(phi - phiCenter);
    }

    /**
     * Adjusts an angle by multiples of 2*pi so it lies nearest a reference value.
     *
     * @param angle angle to adjust
     * @param reference reference value
     * @return unwrapped angle
     */
    private static double unwrapNear(double angle, double reference) {
        double a = angle;

        while (a - reference > Math.PI) {
            a -= 2.0 * Math.PI;
        }

        while (a - reference <= -Math.PI) {
            a += 2.0 * Math.PI;
        }

        return a;
    }

    /**
     * Positive angular delta from a0 to a1.
     *
     * @param a0 start angle
     * @param a1 end angle
     * @return positive delta in [0, 2*pi)
     */
    private static double positiveDelta(double a0, double a1) {
        double d = a1 - a0;

        while (d < 0.0) {
            d += 2.0 * Math.PI;
        }

        while (d >= 2.0 * Math.PI) {
            d -= 2.0 * Math.PI;
        }

        return d;
    }

    /**
     * Normalizes an angle to [-pi, pi).
     *
     * @param angle angle
     * @return normalized angle
     */
    private static double wrapPi(double angle) {
        double a = angle;

        while (a < -Math.PI) {
            a += 2.0 * Math.PI;
        }

        while (a >= Math.PI) {
            a -= 2.0 * Math.PI;
        }

        return a;
    }

    /**
     * Normalizes an angle to [-pi, pi).
     *
     * @param angle angle
     * @return normalized angle
     */
    private static double normalizeAngle(double angle) {
        return wrapPi(angle);
    }
    /**
     * Samples a meridian arc between two points on the same phi meridian.
     *
     * @param a start
     * @param b end
     * @param radius radius
     * @param phi meridian
     * @param samples sample count
     * @return sampled meridian arc
     */
    private static List<Vec3> meridianArc(
            Vec3 a,
            Vec3 b,
            double radius,
            double phi,
            int samples) {

        int n = Math.max(2, samples);
        ArrayList<Vec3> arc = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            double f = (double) i / (n - 1);
            Vec3 p = slerp(a, b, radius, f);
            arc.add(projectToPhiMeridian(p, radius, phi));
        }

        return arc;
    }

    /**
     * Spherical linear interpolation.
     *
     * @param a start
     * @param b end
     * @param radius radius
     * @param t fraction
     * @return interpolated point
     */
    private static Vec3 slerp(Vec3 a, Vec3 b, double radius, double t) {
        Vec3 ua = normalizeToRadius(a, 1.0);
        Vec3 ub = normalizeToRadius(b, 1.0);

        double dot = clamp(dot(ua, ub), -1.0, 1.0);
        double omega = Math.acos(dot);

        if (omega < 1.0e-12) {
            return normalizeToRadius(lerp(a, b, t), radius);
        }

        double sinOmega = Math.sin(omega);
        double w0 = Math.sin((1.0 - t) * omega) / sinOmega;
        double w1 = Math.sin(t * omega) / sinOmega;

        return new Vec3(
                radius * (w0 * ua.x() + w1 * ub.x()),
                radius * (w0 * ua.y() + w1 * ub.y()),
                radius * (w0 * ua.z() + w1 * ub.z()));
    }

    /**
     * Linear interpolation.
     *
     * @param a start
     * @param b end
     * @param t fraction
     * @return interpolated vector
     */
    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return new Vec3(
                a.x() + t * (b.x() - a.x()),
                a.y() + t * (b.y() - a.y()),
                a.z() + t * (b.z() - a.z()));
    }

    /**
     * Normalizes a vector to a radius.
     *
     * @param v vector
     * @param radius radius
     * @return normalized vector
     */
    private static Vec3 normalizeToRadius(Vec3 v, double radius) {
        double n = v.norm();

        if (!Double.isFinite(n) || n <= 0.0) {
            return new Vec3(radius, 0.0, 0.0);
        }

        return new Vec3(
                radius * v.x() / n,
                radius * v.y() / n,
                radius * v.z() / n);
    }

    /**
     * Adds point if distinct from previous point.
     *
     * @param points output list
     * @param p point
     */
    private static void addIfDistinct(List<Vec3> points, Vec3 p) {
        if (p == null) {
            return;
        }

        if (points.isEmpty() || !near(points.get(points.size() - 1), p)) {
            points.add(p);
        }
    }

    /**
     * Removes consecutive duplicates and explicit closing duplicate.
     *
     * @param input input list
     * @return cleaned list
     */
    private static List<Vec3> removeConsecutiveDuplicates(List<Vec3> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }

        ArrayList<Vec3> cleaned = new ArrayList<>();

        for (Vec3 p : input) {
            addIfDistinct(cleaned, p);
        }

        if (cleaned.size() > 1 && near(cleaned.get(0), cleaned.get(cleaned.size() - 1))) {
            cleaned.remove(cleaned.size() - 1);
        }

        return cleaned;
    }

    /**
     * Checks point proximity.
     *
     * @param a first
     * @param b second
     * @return true if near
     */
    private static boolean near(Vec3 a, Vec3 b) {
        if (a == null || b == null) {
            return false;
        }

        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();

        return dx * dx + dy * dy + dz * dz <= DUP_TOL2;
    }

    /**
     * Dot product.
     *
     * @param a first
     * @param b second
     * @return dot
     */
    private static double dot(Vec3 a, Vec3 b) {
        return a.x() * b.x() + a.y() * b.y() + a.z() * b.z();
    }

    /**
     * Clamps a value.
     *
     * @param value value
     * @param lo low
     * @param hi high
     * @return clamped value
     */
    private static double clamp(double value, double lo, double hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}