package edu.cnu.mdi.mosaic.theta;

import java.util.ArrayList;
import java.util.List;

import edu.cnu.mdi.mosaic.area.PrepatchBoundarySampler;
import edu.cnu.mdi.mosaic.area.SphericalPolygonArea;
import edu.cnu.mdi.mosaic.geom.Vec3;
import edu.cnu.mdi.mosaic.grid.Grid1D;
import edu.cnu.mdi.mosaic.patch.PoleClassification;
import edu.cnu.mdi.mosaic.patch.PoleRelation;
import edu.cnu.mdi.mosaic.patch.Prepatch;

/**
 * Splits ordinary prepatches by spherical theta bands.
 * <p>
 * Non-pole prepatches are clipped directly by theta bands. Prepatches
 * containing a pole are handled by a special polar fan decomposition: the
 * prepatch boundary is sampled, the pole is inserted as an explicit synthetic
 * vertex, and the resulting polar fan triangles are theta-clipped.
 * </p>
 */
public final class ThetaSplicer {

    /** Small tolerance. */
    private static final double TOL = 1.0e-12;

    /** Duplicate-point tolerance squared. */
    private static final double DUP_TOL2 = 1.0e-18;

    /** Number of bisection iterations for great-circle/plane intersections. */
    private static final int BISECTION_COUNT = 60;

    /**
     * Hidden constructor.
     */
    private ThetaSplicer() {
    }

    /**
     * Splices prepatches by theta.
     *
     * @param prepatches ordinary prepatches
     * @param thetaGrid spherical theta grid
     * @param radius shell radius
     * @param samplesPerCurve samples per GENERAL curve used to sample parent
     *        prepatch boundaries
     * @param referenceNormalizedArea reference prepatch normalized area
     * @return theta splice result
     */
    public static ThetaSpliceResult splice(
            List<Prepatch> prepatches,
            Grid1D thetaGrid,
            double radius,
            int samplesPerCurve,
            double referenceNormalizedArea) {

        if (prepatches == null) {
            prepatches = List.of();
        }

        if (thetaGrid == null) {
            throw new IllegalArgumentException("thetaGrid must not be null.");
        }

        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException("radius must be positive and finite.");
        }

        ArrayList<ThetaPatch> thetaPatches = new ArrayList<>();
        ArrayList<ThetaSpliceFailure> failures = new ArrayList<>();

        double totalArea = 0.0;

        int deferredPolePrepatches = 0;
        int handledPolePrepatches = 0;
        int failedPrepatches = 0;

        for (Prepatch prepatch : prepatches) {
            if (prepatch == null) {
                continue;
            }

            PoleCase poleCase = poleCase(prepatch.poleClassification());

            if (poleCase == PoleCase.NORTH_INSIDE || poleCase == PoleCase.SOUTH_INSIDE) {
                PolarBuild polarBuild = splicePolarPrepatch(
                        prepatch,
                        poleCase,
                        thetaGrid,
                        radius,
                        samplesPerCurve);

                if (polarBuild.thetaPatches().isEmpty()) {
                    failedPrepatches++;
                    failures.add(new ThetaSpliceFailure(
                            prepatch.cellId(),
                            "special polar theta splice produced no patches: "
                                    + prepatch.poleClassification()));
                } else {
                    handledPolePrepatches++;
                    thetaPatches.addAll(polarBuild.thetaPatches());
                    totalArea += polarBuild.totalArea();
                }

                continue;
            }

            if (prepatch.hasPoleInvolvement()) {
                deferredPolePrepatches++;
                failures.add(new ThetaSpliceFailure(
                        prepatch.cellId(),
                        "deferred non-INSIDE pole case: "
                                + prepatch.poleClassification()));
                continue;
            }

            NormalBuild normalBuild = spliceNormalPrepatch(
                    prepatch,
                    thetaGrid,
                    radius,
                    samplesPerCurve);

            if (normalBuild.thetaPatches().isEmpty()) {
                failedPrepatches++;
                failures.add(new ThetaSpliceFailure(
                        prepatch.cellId(),
                        "no theta patches built from sampled boundary"));
            } else {
                thetaPatches.addAll(normalBuild.thetaPatches());
                totalArea += normalBuild.totalArea();
            }
        }

        double normalizedArea = totalArea / (4.0 * Math.PI * radius * radius);

        ThetaSpliceStats stats = new ThetaSpliceStats(
                prepatches.size(),
                thetaPatches.size(),
                failedPrepatches,
                deferredPolePrepatches,
                handledPolePrepatches,
                totalArea,
                normalizedArea,
                referenceNormalizedArea);

        return new ThetaSpliceResult(thetaPatches, failures, stats);
    }

    /**
     * Runs a theta-splice convergence test for several sampling densities.
     *
     * @param prepatches ordinary prepatches
     * @param thetaGrid spherical theta grid
     * @param radius shell radius
     * @param referenceNormalizedArea reference prepatch normalized area
     * @param sampleCounts samples-per-curve values to test
     * @return convergence-test rows
     */
    public static List<ThetaSpliceConvergenceResult> convergenceTest(
            List<Prepatch> prepatches,
            Grid1D thetaGrid,
            double radius,
            double referenceNormalizedArea,
            int... sampleCounts) {

        if (sampleCounts == null || sampleCounts.length == 0) {
            sampleCounts = new int[] {4, 8, 16, 32, 64};
        }

        ArrayList<ThetaSpliceConvergenceResult> results = new ArrayList<>();

        for (int samplesPerCurve : sampleCounts) {
            ThetaSpliceResult result = splice(
                    prepatches,
                    thetaGrid,
                    radius,
                    samplesPerCurve,
                    referenceNormalizedArea);

            ThetaSpliceStats stats = result.stats();

            results.add(new ThetaSpliceConvergenceResult(
                    samplesPerCurve,
                    stats.thetaPatchesBuilt(),
                    stats.failedPrepatches(),
                    stats.normalizedArea(),
                    stats.referenceNormalizedArea(),
                    stats.normalizedAreaDelta()));
        }

        return results;
    }

    /**
     * Splices a non-pole prepatch.
     *
     * @param prepatch prepatch
     * @param thetaGrid theta grid
     * @param radius radius
     * @param samplesPerCurve samples per curve
     * @return normal build result
     */
    private static NormalBuild spliceNormalPrepatch(
            Prepatch prepatch,
            Grid1D thetaGrid,
            double radius,
            int samplesPerCurve) {

        List<Vec3> parentBoundary =
                PrepatchBoundarySampler.sampleBoundary(prepatch, samplesPerCurve);

        if (parentBoundary.size() < 3) {
            return new NormalBuild(List.of(), 0.0);
        }

        ArrayList<ThetaPatch> patches = new ArrayList<>();
        double totalArea = 0.0;

        int[] thetaLimits = thetaCellLimits(parentBoundary, prepatch, thetaGrid);

        for (int ntheta = thetaLimits[0]; ntheta <= thetaLimits[1]; ntheta++) {
            double theta0 = thetaGrid.valueAt(ntheta);
            double theta1 = thetaGrid.valueAt(ntheta + 1);

            List<Vec3> clipped = clipToThetaBand(
                    parentBoundary,
                    radius,
                    theta0,
                    theta1,
                    samplesPerCurve);

            AddPatchResult result = addThetaPatchIfValid(
                    patches,
                    prepatch,
                    ntheta,
                    clipped,
                    radius);

            totalArea += result.area();
        }

        return new NormalBuild(patches, totalArea);
    }

    /**
     * Splices a pole-containing prepatch using a polar fan decomposition.
     *
     * @param prepatch prepatch
     * @param poleCase pole case
     * @param thetaGrid theta grid
     * @param radius radius
     * @param samplesPerCurve samples per curve
     * @return polar build result
     */
    private static PolarBuild splicePolarPrepatch(
            Prepatch prepatch,
            PoleCase poleCase,
            Grid1D thetaGrid,
            double radius,
            int samplesPerCurve) {

        List<Vec3> boundary =
                PrepatchBoundarySampler.sampleBoundary(prepatch, samplesPerCurve);

        if (boundary.size() < 3) {
            return new PolarBuild(List.of(), 0.0);
        }

        Vec3 pole = (poleCase == PoleCase.NORTH_INSIDE)
                ? new Vec3(0.0, 0.0, radius)
                : new Vec3(0.0, 0.0, -radius);

        int[] thetaLimits = thetaCellLimits(boundary, prepatch, thetaGrid);

        ArrayList<ThetaPatch> patches = new ArrayList<>();
        double totalArea = 0.0;

        /*
         * Fan triangulation from the explicit pole point to consecutive sampled
         * boundary points. The artificial radial edges cancel in the area sum and
         * let the theta clipper see the polar cap topology explicitly.
         */
        for (int i = 0; i < boundary.size(); i++) {
            Vec3 a = boundary.get(i);
            Vec3 b = boundary.get((i + 1) % boundary.size());

            if (near(a, b)) {
                continue;
            }

            List<Vec3> triangle = List.of(pole, a, b);

            for (int ntheta = thetaLimits[0]; ntheta <= thetaLimits[1]; ntheta++) {
                double theta0 = thetaGrid.valueAt(ntheta);
                double theta1 = thetaGrid.valueAt(ntheta + 1);

                List<Vec3> clipped = clipToThetaBand(
                        triangle,
                        radius,
                        theta0,
                        theta1,
                        samplesPerCurve);

                AddPatchResult result = addThetaPatchIfValid(
                        patches,
                        prepatch,
                        ntheta,
                        clipped,
                        radius);

                totalArea += result.area();
            }
        }

        return new PolarBuild(patches, totalArea);
    }

    /**
     * Adds a theta patch if the clipped boundary is valid.
     *
     * @param patches output patch list
     * @param prepatch parent prepatch
     * @param ntheta theta index
     * @param boundary boundary
     * @param radius radius
     * @return added area result
     */
    private static AddPatchResult addThetaPatchIfValid(
            List<ThetaPatch> patches,
            Prepatch prepatch,
            int ntheta,
            List<Vec3> boundary,
            double radius) {

        if (boundary == null || boundary.size() < 3) {
            return new AddPatchResult(0.0);
        }

        double unitArea = SphericalPolygonArea.unsignedAreaUnitSphere(boundary);

        if (!Double.isFinite(unitArea) || unitArea <= 0.0) {
            return new AddPatchResult(0.0);
        }

        double area = radius * radius * unitArea;
        double normalizedArea = unitArea / (4.0 * Math.PI);

        patches.add(new ThetaPatch(
                prepatch.cellId(),
                ntheta,
                boundary,
                area,
                normalizedArea,
                prepatch.poleClassification()));

        return new AddPatchResult(area);
    }

    /**
     * Clips a polygon to one theta band and inserts constant-theta arcs.
     *
     * @param polygon input polygon
     * @param radius shell radius
     * @param theta0 first theta boundary
     * @param theta1 second theta boundary
     * @param samplesPerCurve sampling density
     * @return clipped boundary
     */
    private static List<Vec3> clipToThetaBand(
            List<Vec3> polygon,
            double radius,
            double theta0,
            double theta1,
            int samplesPerCurve) {

        double z0 = radius * Math.cos(theta0);
        double z1 = radius * Math.cos(theta1);

        double zMin = Math.min(z0, z1);
        double zMax = Math.max(z0, z1);

        List<Vec3> clipped = clipByZLower(polygon, radius, zMin);
        clipped = clipByZUpper(clipped, radius, zMax);
        clipped = removeConsecutiveDuplicates(clipped);

        if (clipped.size() < 3) {
            return List.of();
        }

        int thetaArcSamples = Math.max(4, samplesPerCurve);
        return insertThetaArcs(clipped, radius, zMin, zMax, thetaArcSamples);
    }

    /**
     * Clips to {@code z >= zMin}.
     *
     * @param input input polygon
     * @param radius radius
     * @param zMin lower z bound
     * @return clipped polygon
     */
    private static List<Vec3> clipByZLower(List<Vec3> input, double radius, double zMin) {
        return clipByZ(input, radius, zMin, true);
    }

    /**
     * Clips to {@code z <= zMax}.
     *
     * @param input input polygon
     * @param radius radius
     * @param zMax upper z bound
     * @return clipped polygon
     */
    private static List<Vec3> clipByZUpper(List<Vec3> input, double radius, double zMax) {
        return clipByZ(input, radius, zMax, false);
    }

    /**
     * Clips by one horizontal plane.
     *
     * @param input input polygon
     * @param radius radius
     * @param zCut z cut
     * @param keepAbove true for {@code z >= zCut}, false for {@code z <= zCut}
     * @return clipped polygon
     */
    private static List<Vec3> clipByZ(
            List<Vec3> input,
            double radius,
            double zCut,
            boolean keepAbove) {

        if (input == null || input.size() < 3) {
            return List.of();
        }

        ArrayList<Vec3> output = new ArrayList<>();

        Vec3 s = input.get(input.size() - 1);
        boolean sInside = insideZ(s, zCut, keepAbove);

        for (Vec3 e : input) {
            boolean eInside = insideZ(e, zCut, keepAbove);

            if (eInside) {
                if (!sInside) {
                    addIfDistinct(output, intersectionAtZ(s, e, radius, zCut));
                }
                addIfDistinct(output, e);
            } else if (sInside) {
                addIfDistinct(output, intersectionAtZ(s, e, radius, zCut));
            }

            s = e;
            sInside = eInside;
        }

        return removeConsecutiveDuplicates(output);
    }

    /**
     * Inserts constant-theta arc samples along clipped edges lying on theta cuts.
     *
     * @param polygon clipped polygon
     * @param radius radius
     * @param zMin lower z cut
     * @param zMax upper z cut
     * @param samples samples per arc
     * @return sampled boundary
     */
    private static List<Vec3> insertThetaArcs(
            List<Vec3> polygon,
            double radius,
            double zMin,
            double zMax,
            int samples) {

        if (polygon == null || polygon.size() < 3) {
            return List.of();
        }

        ArrayList<Vec3> output = new ArrayList<>();

        for (int i = 0; i < polygon.size(); i++) {
            Vec3 a = polygon.get(i);
            Vec3 b = polygon.get((i + 1) % polygon.size());

            addIfDistinct(output, a);

            double zArc = thetaCutSharedBy(a, b, zMin, zMax);

            if (Double.isFinite(zArc)) {
                List<Vec3> arc = thetaArc(a, b, radius, zArc, samples);

                for (int j = 1; j < arc.size() - 1; j++) {
                    addIfDistinct(output, arc.get(j));
                }
            }
        }

        return removeConsecutiveDuplicates(output);
    }

    /**
     * Determines whether two adjacent points lie on the same theta cut.
     *
     * @param a first
     * @param b second
     * @param zMin lower z cut
     * @param zMax upper z cut
     * @return shared z value, or NaN
     */
    private static double thetaCutSharedBy(Vec3 a, Vec3 b, double zMin, double zMax) {
        if (a == null || b == null) {
            return Double.NaN;
        }

        double tol = 1.0e-9;

        boolean aOnMin = Math.abs(a.z() - zMin) <= tol;
        boolean bOnMin = Math.abs(b.z() - zMin) <= tol;

        if (aOnMin && bOnMin) {
            return zMin;
        }

        boolean aOnMax = Math.abs(a.z() - zMax) <= tol;
        boolean bOnMax = Math.abs(b.z() - zMax) <= tol;

        if (aOnMax && bOnMax) {
            return zMax;
        }

        return Double.NaN;
    }

    /**
     * Samples the shorter constant-z theta arc from {@code a} to {@code b}.
     *
     * @param a start
     * @param b end
     * @param radius radius
     * @param z constant z
     * @param samples samples
     * @return sampled theta arc
     */
    private static List<Vec3> thetaArc(
            Vec3 a,
            Vec3 b,
            double radius,
            double z,
            int samples) {

        int n = Math.max(2, samples);

        double rho2 = radius * radius - z * z;
        if (rho2 < 0.0 && rho2 > -1.0e-10) {
            rho2 = 0.0;
        }

        if (rho2 < 0.0) {
            return List.of(a, b);
        }

        double rho = Math.sqrt(rho2);

        if (rho <= 1.0e-12) {
            return List.of(new Vec3(0.0, 0.0, z));
        }

        double phi0 = Math.atan2(a.y(), a.x());
        double phi1 = Math.atan2(b.y(), b.x());
        double dPhi = shortestSignedDelta(phi0, phi1);

        ArrayList<Vec3> arc = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            double f = (double) i / (n - 1);
            double phi = phi0 + f * dPhi;

            arc.add(new Vec3(
                    rho * Math.cos(phi),
                    rho * Math.sin(phi),
                    z));
        }

        return arc;
    }

    /**
     * Returns the signed angular change from {@code a0} to {@code a1} in
     * {@code [-pi, pi]}.
     *
     * @param a0 start angle
     * @param a1 end angle
     * @return signed shortest delta
     */
    private static double shortestSignedDelta(double a0, double a1) {
        double d = a1 - a0;

        while (d <= -Math.PI) {
            d += 2.0 * Math.PI;
        }

        while (d > Math.PI) {
            d -= 2.0 * Math.PI;
        }

        return d;
    }

    /**
     * Tests whether a point is inside one z half-space.
     *
     * @param p point
     * @param zCut z cut
     * @param keepAbove true for {@code z >= zCut}
     * @return true if inside
     */
    private static boolean insideZ(Vec3 p, double zCut, boolean keepAbove) {
        if (p == null) {
            return false;
        }

        return keepAbove ? p.z() >= zCut - TOL : p.z() <= zCut + TOL;
    }

    /**
     * Finds the intersection of a short great-circle edge with {@code z=zCut}.
     *
     * @param a start
     * @param b end
     * @param radius radius
     * @param zCut z cut
     * @return intersection point
     */
    private static Vec3 intersectionAtZ(Vec3 a, Vec3 b, double radius, double zCut) {
        double fa = a.z() - zCut;
        double fb = b.z() - zCut;

        if (Math.abs(fa) <= TOL) {
            return projectToZCircle(a, radius, zCut);
        }

        if (Math.abs(fb) <= TOL) {
            return projectToZCircle(b, radius, zCut);
        }

        if (fa * fb > 0.0) {
            double t = Math.abs(fa) / (Math.abs(fa) + Math.abs(fb));
            return projectToZCircle(lerp(a, b, t), radius, zCut);
        }

        double lo = 0.0;
        double hi = 1.0;
        double fLo = fa;

        for (int i = 0; i < BISECTION_COUNT; i++) {
            double mid = 0.5 * (lo + hi);
            Vec3 p = slerp(a, b, radius, mid);
            double fMid = p.z() - zCut;

            if (Math.abs(fMid) <= TOL) {
                return projectToZCircle(p, radius, zCut);
            }

            if (fLo * fMid <= 0.0) {
                hi = mid;
            } else {
                lo = mid;
                fLo = fMid;
            }
        }

        return projectToZCircle(slerp(a, b, radius, 0.5 * (lo + hi)), radius, zCut);
    }

    /**
     * Projects a point to the small circle {@code z=zCut}.
     *
     * @param p input
     * @param radius radius
     * @param zCut z
     * @return projected point
     */
    private static Vec3 projectToZCircle(Vec3 p, double radius, double zCut) {
        double rho2 = radius * radius - zCut * zCut;

        if (rho2 < 0.0 && rho2 > -1.0e-10) {
            rho2 = 0.0;
        }

        if (rho2 < 0.0) {
            return normalizeToRadius(p, radius);
        }

        double rho = Math.sqrt(rho2);

        if (rho <= 1.0e-12) {
            return new Vec3(0.0, 0.0, zCut);
        }

        double phi = Math.atan2(p.y(), p.x());

        return new Vec3(
                rho * Math.cos(phi),
                rho * Math.sin(phi),
                zCut);
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
     * Finds theta-cell limits touched by a sampled prepatch boundary.
     *
     * @param boundary sampled boundary
     * @param prepatch parent prepatch
     * @param thetaGrid theta grid
     * @return inclusive theta-cell limits
     */
    private static int[] thetaCellLimits(
            List<Vec3> boundary,
            Prepatch prepatch,
            Grid1D thetaGrid) {

        double thetaMin = Double.POSITIVE_INFINITY;
        double thetaMax = Double.NEGATIVE_INFINITY;

        for (Vec3 p : boundary) {
            double r = p.norm();

            if (!Double.isFinite(r) || r <= 0.0) {
                continue;
            }

            double theta = Math.acos(clamp(p.z() / r, -1.0, 1.0));
            thetaMin = Math.min(thetaMin, theta);
            thetaMax = Math.max(thetaMax, theta);
        }

        if (prepatch.poleClassification() != null) {
            if (prepatch.poleClassification().north() != PoleRelation.NONE) {
                thetaMin = 0.0;
            }

            if (prepatch.poleClassification().south() != PoleRelation.NONE) {
                thetaMax = Math.PI;
            }
        }

        if (!Double.isFinite(thetaMin) || !Double.isFinite(thetaMax)) {
            return new int[] {0, thetaGrid.numCells() - 1};
        }

        int lo = thetaGrid.cellIndex(thetaMin);
        int hi = thetaGrid.cellIndex(thetaMax);

        if (lo < 0) {
            lo = 0;
        }

        if (hi < 0) {
            hi = thetaGrid.numCells() - 1;
        }

        lo = Math.max(0, Math.min(lo, thetaGrid.numCells() - 1));
        hi = Math.max(0, Math.min(hi, thetaGrid.numCells() - 1));

        if (hi < lo) {
            int tmp = lo;
            lo = hi;
            hi = tmp;
        }

        return new int[] {lo, hi};
    }

    /**
     * Classifies the polar case for a prepatch.
     *
     * @param classification pole classification
     * @return pole case
     */
    private static PoleCase poleCase(PoleClassification classification) {
        if (classification == null) {
            return PoleCase.NONE;
        }

        if (classification.north() == PoleRelation.INSIDE) {
            return PoleCase.NORTH_INSIDE;
        }

        if (classification.south() == PoleRelation.INSIDE) {
            return PoleCase.SOUTH_INSIDE;
        }

        if (classification.hasPoleInvolvement()) {
            return PoleCase.OTHER_POLE_CASE;
        }

        return PoleCase.NONE;
    }

    /**
     * Adds a point if distinct from the previous point.
     *
     * @param points list
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
     * @param input input points
     * @return cleaned points
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
     * Tests point proximity.
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
     * @return dot product
     */
    private static double dot(Vec3 a, Vec3 b) {
        return a.x() * b.x() + a.y() * b.y() + a.z() * b.z();
    }

    /**
     * Clamps a value.
     *
     * @param value value
     * @param lo lower
     * @param hi upper
     * @return clamped value
     */
    private static double clamp(double value, double lo, double hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    /**
     * Internal pole case.
     */
    private enum PoleCase {
        NONE,
        NORTH_INSIDE,
        SOUTH_INSIDE,
        OTHER_POLE_CASE
    }

    /**
     * Result of normal prepatch theta splicing.
     *
     * @param thetaPatches theta patches
     * @param totalArea total area
     */
    private record NormalBuild(List<ThetaPatch> thetaPatches, double totalArea) {
    }

    /**
     * Result of polar prepatch theta splicing.
     *
     * @param thetaPatches theta patches
     * @param totalArea total area
     */
    private record PolarBuild(List<ThetaPatch> thetaPatches, double totalArea) {
    }

    /**
     * Result from trying to add one theta patch.
     *
     * @param area area added, or zero
     */
    private record AddPatchResult(double area) {
    }
}