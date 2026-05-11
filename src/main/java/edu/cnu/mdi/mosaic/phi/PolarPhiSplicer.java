package edu.cnu.mdi.mosaic.phi;

import java.util.ArrayList;
import java.util.List;

import edu.cnu.mdi.mosaic.area.SphericalPolygonArea;
import edu.cnu.mdi.mosaic.geom.Vec3;
import edu.cnu.mdi.mosaic.grid.Grid1D;
import edu.cnu.mdi.mosaic.theta.ThetaPatch;

/**
 * Phi splicer for theta patches derived from polar prepatches.
 * <p>
 * Ordinary {@link PhiSplicer} intentionally avoids polar-derived theta patches
 * because phi is undefined at the pole and full meridian-plane clipping is too
 * ambiguous there. This class handles those patches separately by projecting the
 * polar neighborhood to a local pole-centered disk, clipping by phi wedges in
 * that disk, and lifting the clipped boundaries back to the sphere.
 * </p>
 * <p>
 * The clipped spherical boundaries are sampled approximations. To preserve the
 * already-trusted theta-splice area, the phi child areas for each polar theta
 * patch are renormalized so their sum equals the parent theta-patch area.
 * </p>
 */
public final class PolarPhiSplicer {

    /** Small tolerance. */
    private static final double TOL = 1.0e-12;

    /** Duplicate-point tolerance squared in 3D. */
    private static final double DUP_TOL2_3D = 1.0e-18;

    /** Duplicate-point tolerance squared in local 2D pole disk. */
    private static final double DUP_TOL2_2D = 1.0e-18;

    /**
     * Hidden constructor.
     */
    private PolarPhiSplicer() {
    }
    /**
     * Splices only polar-derived theta patches by phi.
     * <p>
     * For final/export geometry, polar-derived patches are not represented as
     * ordinary phi cells. Phi is singular at the pole, and drawing/exporting
     * per-phi polar children creates artificial fan spokes.
     * </p>
     *
     * <p>
     * Therefore this method now returns polar aggregate patches. It wraps the
     * polar-derived theta fragments as raw polar fragments and lets
     * {@link FinalPhiPatchCanonicalizer} dissolve their internal fan edges. The
     * resulting final patches have {@code nphi = -1}.
     * </p>
     *
     * @param thetaPatches theta patches
     * @param phiGrid phi grid; retained for signature compatibility
     * @param radius spherical shell radius
     * @param samplesPerMeridianArc retained for signature compatibility
     * @param referenceNormalizedArea reference area for statistics
     * @return phi-splice result for polar-derived theta patches only
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

        ArrayList<PhiPatch> rawPolarThetaFragments = new ArrayList<>();
        ArrayList<PhiSpliceFailure> failures = new ArrayList<>();

        int polarThetaInput = 0;
        int failedThetaPatches = 0;
        double rawTotalArea = 0.0;

        for (ThetaPatch thetaPatch : thetaPatches) {
            if (!isPolarDerived(thetaPatch)) {
                continue;
            }

            polarThetaInput++;

            if (thetaPatch == null || thetaPatch.boundary().size() < 3) {
                failedThetaPatches++;
                continue;
            }

            /*
             * This is deliberately NOT a real phi index. It is only a temporary value
             * so the raw theta fragment can be passed through the final canonicalizer.
             *
             * FinalPhiPatchCanonicalizer detects polar-derived fragments and groups
             * them by parent cell + theta index, ignoring nphi, then rebuilds the
             * output patch with nphi = -1.
             */
            PhiPatch rawFragment = new PhiPatch(
                    thetaPatch.parentCellId(),
                    thetaPatch.ntheta(),
                    0,
                    thetaPatch.boundary(),
                    thetaPatch.area(),
                    thetaPatch.normalizedArea(),
                    thetaPatch.parentPoleClassification());

            rawPolarThetaFragments.add(rawFragment);
            rawTotalArea += thetaPatch.area();
        }

        /*
         * Dissolve the polar fan construction before anything is exposed as final
         * geometry. This should remove the internal spoke edges introduced by the
         * theta-stage polar fan decomposition.
         */
        List<PhiPatch> canonicalPolarPatches =
                FinalPhiPatchCanonicalizer.canonicalize(rawPolarThetaFragments, radius);
        
        for (PhiPatch patch : canonicalPolarPatches) {
            System.out.printf(
                    "POLAR FINAL parent=%s ntheta=%d nphi=%d aggregate=%s boundary=%d area=%.17g%n",
                    patch.parentCellId(),
                    patch.ntheta(),
                    patch.nphi(),
                    patch.isPolarAggregate(),
                    patch.boundaryPointCount(),
                    patch.normalizedArea());
        }

        double totalArea = 0.0;
        int polarAggregates = 0;

        for (PhiPatch patch : canonicalPolarPatches) {
            totalArea += patch.area();

            if (patch.isPolarAggregate()) {
                polarAggregates++;
            }
        }

        double normalizedArea = totalArea / (4.0 * Math.PI * radius * radius);
        int handledPolarThetaPatches = polarThetaInput - failedThetaPatches;

        System.out.printf(
                "Polar theta aggregation: rawTheta=%d final=%d aggregates=%d rawA=%.17g finalA=%.17g deltaA=%.6g%n",
                rawPolarThetaFragments.size(),
                canonicalPolarPatches.size(),
                polarAggregates,
                rawTotalArea / (4.0 * Math.PI * radius * radius),
                normalizedArea,
                normalizedArea - rawTotalArea / (4.0 * Math.PI * radius * radius));

        PhiSpliceStats stats = new PhiSpliceStats(
                polarThetaInput,
                canonicalPolarPatches.size(),
                failedThetaPatches,
                0,
                handledPolarThetaPatches,
                totalArea,
                normalizedArea,
                referenceNormalizedArea);

        return new PhiSpliceResult(canonicalPolarPatches, failures, stats);
    }

    /**
     * Runs a polar phi-splice convergence test.
     *
     * @param thetaPatches theta patches
     * @param phiGrid phi grid
     * @param radius radius
     * @param referenceNormalizedArea reference area
     * @param sampleCounts sample counts
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
     * Splices one polar-derived theta patch.
     *
     * @param thetaPatch theta patch
     * @param phiGrid phi grid
     * @param radius radius
     * @param samplesPerMeridianArc samples along inserted meridian arcs
     * @param failures failure list to append to
     * @return phi patches built from this theta patch
     */
    private static List<PhiPatch> spliceOnePolarThetaPatch(
            ThetaPatch thetaPatch,
            Grid1D phiGrid,
            double radius,
            int samplesPerMeridianArc,
            List<PhiSpliceFailure> failures) {

        boolean north = isNorthPolarPatch(thetaPatch);

        List<P2> localPolygon = toPoleDisk(thetaPatch.boundary(), radius, north);
        localPolygon = removeConsecutiveDuplicates2D(localPolygon);

        if (localPolygon.size() < 3) {
            return List.of();
        }

        ArrayList<RawPolarPhiPatch> rawPatches = new ArrayList<>();

        for (int nphi = 0; nphi < phiGrid.numCells(); nphi++) {
            double phi0 = phiGrid.valueAt(nphi);
            double phi1 = phiGrid.valueAt(nphi + 1);

            List<P2> clipped2D = clipToPhiWedge(localPolygon, phi0, phi1);

            clipped2D = removeConsecutiveDuplicates2D(clipped2D);

            if (clipped2D.size() < 3) {
                continue;
            }

            List<Vec3> boundary3D = liftPoleDiskPolygon(
                    clipped2D,
                    radius,
                    north,
                    samplesPerMeridianArc);

            boundary3D = removeConsecutiveDuplicates3D(boundary3D);

            if (boundary3D.size() < 3) {
                continue;
            }

            double unitArea = SphericalPolygonArea.unsignedAreaUnitSphere(boundary3D);

            if (!Double.isFinite(unitArea) || unitArea <= 0.0) {
                failures.add(new PhiSpliceFailure(
                        thetaPatch.parentCellId(),
                        thetaPatch.ntheta(),
                        "polar phi patch had invalid raw spherical area at nphi="
                                + nphi));
                continue;
            }

            rawPatches.add(new RawPolarPhiPatch(nphi, boundary3D, unitArea));
        }

        if (rawPatches.isEmpty()) {
            return List.of();
        }

        double rawUnitAreaSum = 0.0;
        for (RawPolarPhiPatch raw : rawPatches) {
            rawUnitAreaSum += raw.unitArea();
        }

        if (!Double.isFinite(rawUnitAreaSum) || rawUnitAreaSum <= 0.0) {
            return List.of();
        }

        /*
         * Preserve the theta-splice parent area. This is intentional. The theta
         * stage already solved the polar area accurately; the polar phi stage
         * distributes that area among phi bins.
         */
        double parentUnitArea = thetaPatch.normalizedArea() * 4.0 * Math.PI;
        double scale = parentUnitArea / rawUnitAreaSum;

        ArrayList<PhiPatch> patches = new ArrayList<>();

        for (RawPolarPhiPatch raw : rawPatches) {
            double unitArea = raw.unitArea() * scale;
            double area = radius * radius * unitArea;
            double normalizedArea = unitArea / (4.0 * Math.PI);

            patches.add(new PhiPatch(
                    thetaPatch.parentCellId(),
                    thetaPatch.ntheta(),
                    raw.nphi(),
                    raw.boundary(),
                    area,
                    normalizedArea,
                    thetaPatch.parentPoleClassification()));
        }

        return patches;
    }

    /**
     * Checks whether a theta patch came from a polar parent.
     *
     * @param thetaPatch theta patch
     * @return true if polar-derived
     */
    private static boolean isPolarDerived(ThetaPatch thetaPatch) {
        return thetaPatch != null
                && thetaPatch.parentPoleClassification() != null
                && thetaPatch.parentPoleClassification().hasPoleInvolvement();
    }

    /**
     * Determines whether the patch belongs to the north or south polar region.
     *
     * @param thetaPatch theta patch
     * @return true for north, false for south
     */
    private static boolean isNorthPolarPatch(ThetaPatch thetaPatch) {
        double sumZ = 0.0;
        int count = 0;

        for (Vec3 p : thetaPatch.boundary()) {
            if (p == null) {
                continue;
            }

            sumZ += p.z();
            count++;
        }

        return count == 0 || sumZ >= 0.0;
    }

    /**
     * Projects a spherical boundary to a pole-centered local disk.
     *
     * @param boundary spherical boundary
     * @param radius radius
     * @param north true for north pole, false for south pole
     * @return local 2D polygon
     */
    private static List<P2> toPoleDisk(
            List<Vec3> boundary,
            double radius,
            boolean north) {

        ArrayList<P2> output = new ArrayList<>();

        for (Vec3 p : boundary) {
            if (p == null) {
                continue;
            }

            double r = p.norm();

            if (!Double.isFinite(r) || r <= 0.0) {
                continue;
            }

            double zUnit = clamp(p.z() / r, -1.0, 1.0);
            double theta = Math.acos(zUnit);

            double rho = north ? theta * radius : (Math.PI - theta) * radius;

            /*
             * At the pole, phi is undefined. In the local disk this is simply the
             * origin, so the chosen angle does not matter.
             */
            double phi = Math.atan2(p.y(), p.x());

            addIfDistinct2D(output, new P2(
                    rho * Math.cos(phi),
                    rho * Math.sin(phi)));
        }

        return removeConsecutiveDuplicates2D(output);
    }

    /**
     * Lifts a local pole-disk polygon back to the sphere.
     *
     * @param polygon local polygon
     * @param radius radius
     * @param north true for north, false for south
     * @param samplesPerMeridianArc samples on inserted radial edges
     * @return spherical boundary
     */
    private static List<Vec3> liftPoleDiskPolygon(
            List<P2> polygon,
            double radius,
            boolean north,
            int samplesPerMeridianArc) {

        if (polygon == null || polygon.size() < 3) {
            return List.of();
        }

        ArrayList<Vec3> output = new ArrayList<>();

        int arcSamples = Math.max(2, samplesPerMeridianArc);

        for (int i = 0; i < polygon.size(); i++) {
            P2 a = polygon.get(i);
            P2 b = polygon.get((i + 1) % polygon.size());

            addIfDistinct3D(output, lift(a, radius, north));

            if (sameRayFromOrigin(a, b)) {
                List<Vec3> arc = liftRadialArc(a, b, radius, north, arcSamples);

                for (int j = 1; j < arc.size() - 1; j++) {
                    addIfDistinct3D(output, arc.get(j));
                }
            }
        }

        return removeConsecutiveDuplicates3D(output);
    }

    /**
     * Lifts one local disk point to the sphere.
     *
     * @param p local point
     * @param radius radius
     * @param north true for north, false for south
     * @return spherical point
     */
    private static Vec3 lift(P2 p, double radius, boolean north) {
        double rho = Math.hypot(p.x(), p.y());
        double alpha = clamp(rho / radius, 0.0, Math.PI);

        double theta = north ? alpha : Math.PI - alpha;
        double phi = Math.atan2(p.y(), p.x());

        double sinTheta = Math.sin(theta);

        return new Vec3(
                radius * sinTheta * Math.cos(phi),
                radius * sinTheta * Math.sin(phi),
                radius * Math.cos(theta));
    }

    /**
     * Samples a radial local-disk segment and lifts it to a meridian arc.
     *
     * @param a first local point
     * @param b second local point
     * @param radius radius
     * @param north true for north, false for south
     * @param samples sample count
     * @return lifted arc
     */
    private static List<Vec3> liftRadialArc(
            P2 a,
            P2 b,
            double radius,
            boolean north,
            int samples) {

        int n = Math.max(2, samples);
        ArrayList<Vec3> arc = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            double f = (double) i / (n - 1);

            P2 p = new P2(
                    a.x() + f * (b.x() - a.x()),
                    a.y() + f * (b.y() - a.y()));

            arc.add(lift(p, radius, north));
        }

        return arc;
    }

    /**
     * Clips a local pole-disk polygon to one phi wedge.
     *
     * @param polygon input polygon
     * @param phi0 lower phi edge
     * @param phi1 upper phi edge
     * @return clipped polygon
     */
    private static List<P2> clipToPhiWedge(
            List<P2> polygon,
            double phi0,
            double phi1) {

        if (polygon == null || polygon.size() < 3) {
            return List.of();
        }

        double width = positiveDelta(phi0, phi1);

        if (!(width > 0.0 && width < Math.PI)) {
            return List.of();
        }

        double phiCenter = normalizeAngle(phi0 + 0.5 * width);
        double uMin = -0.5 * width;
        double uMax = 0.5 * width;

        P2 rayMin = unitVector(phiCenter + uMin);
        P2 rayMax = unitVector(phiCenter + uMax);

        List<P2> clipped = clipByRayHalfPlane(polygon, rayMin, true);
        clipped = clipByRayHalfPlane(clipped, rayMax, false);

        return removeConsecutiveDuplicates2D(clipped);
    }

    /**
     * Clips by a half-plane whose boundary is a ray line through the origin.
     *
     * @param input input polygon
     * @param ray unit vector along boundary ray
     * @param keepLeft true to keep cross(ray,p) &gt;= 0, false to keep &lt;= 0
     * @return clipped polygon
     */
    private static List<P2> clipByRayHalfPlane(
            List<P2> input,
            P2 ray,
            boolean keepLeft) {

        if (input == null || input.size() < 3) {
            return List.of();
        }

        ArrayList<P2> output = new ArrayList<>();

        P2 s = input.get(input.size() - 1);
        double ds = signedDistanceFromRayLine(ray, s);
        boolean sInside = inside(ds, keepLeft);

        for (P2 e : input) {
            double de = signedDistanceFromRayLine(ray, e);
            boolean eInside = inside(de, keepLeft);

            if (eInside) {
                if (!sInside) {
                    addIfDistinct2D(output, intersectionWithRayLine(s, e, ray));
                }

                addIfDistinct2D(output, e);
            } else if (sInside) {
                addIfDistinct2D(output, intersectionWithRayLine(s, e, ray));
            }

            s = e;
            ds = de;
            sInside = eInside;
        }

        return removeConsecutiveDuplicates2D(output);
    }

    /**
     * Tests half-plane membership.
     *
     * @param signedDistance signed distance proxy
     * @param keepLeft true for left side, false for right side
     * @return true if inside
     */
    private static boolean inside(double signedDistance, boolean keepLeft) {
        return keepLeft ? signedDistance >= -TOL : signedDistance <= TOL;
    }

    /**
     * Signed line test based on cross(ray,p).
     *
     * @param ray ray direction
     * @param p point
     * @return signed value
     */
    private static double signedDistanceFromRayLine(P2 ray, P2 p) {
        return cross(ray, p);
    }

    /**
     * Finds the segment intersection with a line through the origin.
     *
     * @param a first segment endpoint
     * @param b second segment endpoint
     * @param ray line direction
     * @return intersection point
     */
    private static P2 intersectionWithRayLine(P2 a, P2 b, P2 ray) {
        P2 d = new P2(b.x() - a.x(), b.y() - a.y());

        double denom = cross(ray, d);

        if (Math.abs(denom) < TOL) {
            return projectToRayLine(a, ray);
        }

        double t = -cross(ray, a) / denom;
        t = clamp(t, 0.0, 1.0);

        return new P2(
                a.x() + t * d.x(),
                a.y() + t * d.y());
    }

    /**
     * Projects a point to a line through the origin.
     *
     * @param p point
     * @param ray line direction
     * @return projected point
     */
    private static P2 projectToRayLine(P2 p, P2 ray) {
        double s = p.x() * ray.x() + p.y() * ray.y();

        return new P2(s * ray.x(), s * ray.y());
    }

    /**
     * Tests whether two points lie on the same ray from the origin.
     *
     * @param a first point
     * @param b second point
     * @return true if same ray
     */
    private static boolean sameRayFromOrigin(P2 a, P2 b) {
        double ra = Math.hypot(a.x(), a.y());
        double rb = Math.hypot(b.x(), b.y());

        if (ra < 1.0e-12 || rb < 1.0e-12) {
            return true;
        }

        double c = cross(a, b);
        double dot = a.x() * b.x() + a.y() * b.y();

        return Math.abs(c) <= 1.0e-10 * ra * rb && dot >= 0.0;
    }

    /**
     * Unit vector at angle phi.
     *
     * @param phi angle
     * @return unit vector
     */
    private static P2 unitVector(double phi) {
        return new P2(Math.cos(phi), Math.sin(phi));
    }

    /**
     * Positive angular delta from a0 to a1.
     *
     * @param a0 first angle
     * @param a1 second angle
     * @return positive delta
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
    private static double normalizeAngle(double angle) {
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
     * Removes consecutive duplicate 2D points and any closing duplicate.
     *
     * @param input input points
     * @return cleaned points
     */
    private static List<P2> removeConsecutiveDuplicates2D(List<P2> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }

        ArrayList<P2> cleaned = new ArrayList<>();

        for (P2 p : input) {
            addIfDistinct2D(cleaned, p);
        }

        if (cleaned.size() > 1 && near2D(cleaned.get(0), cleaned.get(cleaned.size() - 1))) {
            cleaned.remove(cleaned.size() - 1);
        }

        return cleaned;
    }

    /**
     * Removes consecutive duplicate 3D points and any closing duplicate.
     *
     * @param input input points
     * @return cleaned points
     */
    private static List<Vec3> removeConsecutiveDuplicates3D(List<Vec3> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }

        ArrayList<Vec3> cleaned = new ArrayList<>();

        for (Vec3 p : input) {
            addIfDistinct3D(cleaned, p);
        }

        if (cleaned.size() > 1 && near3D(cleaned.get(0), cleaned.get(cleaned.size() - 1))) {
            cleaned.remove(cleaned.size() - 1);
        }

        return cleaned;
    }

    /**
     * Adds a 2D point if distinct from the previous point.
     *
     * @param points points
     * @param p point
     */
    private static void addIfDistinct2D(List<P2> points, P2 p) {
        if (p == null) {
            return;
        }

        if (points.isEmpty() || !near2D(points.get(points.size() - 1), p)) {
            points.add(p);
        }
    }

    /**
     * Adds a 3D point if distinct from the previous point.
     *
     * @param points points
     * @param p point
     */
    private static void addIfDistinct3D(List<Vec3> points, Vec3 p) {
        if (p == null) {
            return;
        }

        if (points.isEmpty() || !near3D(points.get(points.size() - 1), p)) {
            points.add(p);
        }
    }

    /**
     * Tests 2D proximity.
     *
     * @param a first
     * @param b second
     * @return true if near
     */
    private static boolean near2D(P2 a, P2 b) {
        if (a == null || b == null) {
            return false;
        }

        double dx = a.x() - b.x();
        double dy = a.y() - b.y();

        return dx * dx + dy * dy <= DUP_TOL2_2D;
    }

    /**
     * Tests 3D proximity.
     *
     * @param a first
     * @param b second
     * @return true if near
     */
    private static boolean near3D(Vec3 a, Vec3 b) {
        if (a == null || b == null) {
            return false;
        }

        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();

        return dx * dx + dy * dy + dz * dz <= DUP_TOL2_3D;
    }

    /**
     * 2D cross product z component.
     *
     * @param a first
     * @param b second
     * @return cross product
     */
    private static double cross(P2 a, P2 b) {
        return a.x() * b.y() - a.y() * b.x();
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

    /**
     * Local 2D point.
     *
     * @param x x coordinate
     * @param y y coordinate
     */
    private record P2(double x, double y) {
    }

    /**
     * Raw polar phi patch before area renormalization.
     *
     * @param nphi phi-cell index
     * @param boundary lifted spherical boundary
     * @param unitArea raw unit-sphere area
     */
    private record RawPolarPhiPatch(
            int nphi,
            List<Vec3> boundary,
            double unitArea) {

        /**
         * Creates a raw patch.
         *
         * @param nphi phi-cell index
         * @param boundary boundary
         * @param unitArea raw area on unit sphere
         */
        RawPolarPhiPatch {
            boundary = List.copyOf(boundary == null ? List.of() : boundary);
        }
    }
}