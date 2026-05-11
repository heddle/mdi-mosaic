package edu.cnu.mdi.mosaic.phi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.cnu.mdi.mosaic.area.SphericalPolygonArea;
import edu.cnu.mdi.mosaic.cell.CellId;
import edu.cnu.mdi.mosaic.geom.Vec3;
import edu.cnu.mdi.mosaic.patch.PoleClassification;

/**
 * Canonicalizes final phi patches.
 * <p>
 * Non-polar patches remain true phi-cell patches and are grouped by
 * parent cell, theta index, and phi index.
 * </p>
 *
 * <p>
 * Polar-derived patches are different. Because phi is singular at the pole,
 * the individual polar phi children are an area-accounting and clipping device,
 * not the preferred final/export representation. Therefore polar-derived
 * patches are grouped by parent cell and theta index only. Their internal
 * meridian edges are cancelled, producing polar aggregate patches with
 * {@code nphi = -1}.
 * </p>
 */
public final class FinalPhiPatchCanonicalizer {

    /** Duplicate-point tolerance squared. */
    private static final double DUP_TOL2 = 1.0e-18;

    /** Quantization scale used to match nearly identical endpoints. */
    private static final double KEY_TOL = 1.0e-9;

    /** Maximum local cleanup passes. */
    private static final int MAX_CLEANUP_PASSES = 30;

    /**
     * Hidden constructor.
     */
    private FinalPhiPatchCanonicalizer() {
    }

    /**
     * Canonicalizes a list of raw phi fragments.
     *
     * @param patches raw phi fragments
     * @param radius spherical shell radius
     * @return canonicalized final patches
     */
    public static List<PhiPatch> canonicalize(List<PhiPatch> patches, double radius) {
        if (patches == null || patches.isEmpty()) {
            return List.of();
        }

        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException("radius must be positive and finite.");
        }

        Map<PatchKey, List<PhiPatch>> groups = new LinkedHashMap<>();

        for (PhiPatch patch : patches) {
            if (patch == null || patch.boundary().size() < 3) {
                continue;
            }

            PatchKey key = keyFor(patch);
            groups.computeIfAbsent(key, unused -> new ArrayList<>()).add(patch);
        }

        ArrayList<PhiPatch> output = new ArrayList<>();

        for (Map.Entry<PatchKey, List<PhiPatch>> entry : groups.entrySet()) {
            PatchKey key = entry.getKey();
            List<PhiPatch> group = entry.getValue();

            if (group.size() == 1) {
                PhiPatch patch = group.get(0);
                List<Vec3> boundary = cleanBoundary(patch.boundary());

                if (boundary.size() >= 3) {
                    output.add(rebuildPatch(key, boundary, radius));
                }

                continue;
            }

            output.addAll(mergePatchGroup(key, group, radius));
        }

        return List.copyOf(output);
    }

    /**
     * Computes the sampled spherical perimeter of a final patch boundary.
     *
     * @param boundary sampled boundary
     * @param radius spherical shell radius
     * @return perimeter in the same length units as radius
     */
    public static double sphericalPerimeter(List<Vec3> boundary, double radius) {
        List<Vec3> b = cleanBoundary(boundary);

        if (b.size() < 3 || !Double.isFinite(radius) || radius <= 0.0) {
            return 0.0;
        }

        double sum = 0.0;

        for (int i = 0; i < b.size(); i++) {
            Vec3 a = b.get(i);
            Vec3 c = b.get((i + 1) % b.size());

            double na = a.norm();
            double nc = c.norm();

            if (!Double.isFinite(na) || !Double.isFinite(nc) || na <= 0.0 || nc <= 0.0) {
                continue;
            }

            double dot = (a.x() * c.x() + a.y() * c.y() + a.z() * c.z()) / (na * nc);
            dot = clamp(dot, -1.0, 1.0);

            sum += radius * Math.acos(dot);
        }

        return sum;
    }

    /**
     * Creates the canonical grouping key for one raw phi fragment.
     *
     * @param patch raw phi fragment
     * @return grouping key
     */
    private static PatchKey keyFor(PhiPatch patch) {
        boolean polar = isPolarDerived(patch);

        return new PatchKey(
                patch.parentCellId(),
                patch.ntheta(),
                polar ? -1 : patch.nphi(),
                patch.parentPoleClassification(),
                polar);
    }

    /**
     * Checks whether a raw phi fragment is polar-derived.
     *
     * @param patch patch
     * @return true if polar-derived
     */
    private static boolean isPolarDerived(PhiPatch patch) {
        return patch != null
                && patch.parentPoleClassification() != null
                && patch.parentPoleClassification().hasPoleInvolvement();
    }

    /**
     * Merges all fragments with the same final-patch key.
     *
     * @param key patch key
     * @param group fragments
     * @param radius radius
     * @return one or more canonical final patches
     */
    private static List<PhiPatch> mergePatchGroup(
            PatchKey key,
            List<PhiPatch> group,
            double radius) {

        /*
         * Polar aggregate patches are special. The input boundaries may still contain
         * theta-stage fan construction walks. For final/export geometry, build a
         * clean pole-disk exterior envelope directly and preserve the trusted summed
         * area from the fragments.
         */
        if (key != null && key.polarAggregate()) {
            PhiPatch patch = buildPolarAggregatePatch(key, group, radius);
            return patch == null ? List.of() : List.of(patch);
        }

        ArrayList<Edge> remainingEdges = exteriorEdges(key, group, radius);

        if (remainingEdges.isEmpty()) {
            return List.of();
        }

        ArrayList<List<Vec3>> loops = buildLoops(remainingEdges);
        ArrayList<PhiPatch> output = new ArrayList<>();

        for (List<Vec3> loop : loops) {
            List<Vec3> boundary = cleanBoundary(loop);

            if (boundary.size() >= 3) {
                output.add(rebuildPatch(key, boundary, radius));
            }
        }

        return output;
    } 
    
    /**
     * Builds one clean polar aggregate patch.
     * <p>
     * The boundary is an exterior envelope in a pole-centered disk. The area is not
     * recomputed from that envelope; instead, the trusted summed area of the input
     * fragments is preserved.
     * </p>
     *
     * @param key polar aggregate key
     * @param group input fragments
     * @param radius spherical radius
     * @return aggregate patch, or null
     */
    private static PhiPatch buildPolarAggregatePatch(
            PatchKey key,
            List<PhiPatch> group,
            double radius) {

        if (group == null || group.isEmpty()) {
            return null;
        }

        double area = 0.0;
        double normalizedArea = 0.0;

        ArrayList<Vec3> points3D = new ArrayList<>();

        for (PhiPatch patch : group) {
            if (patch == null) {
                continue;
            }

            area += patch.area();
            normalizedArea += patch.normalizedArea();

            for (Vec3 p : patch.boundary()) {
                if (p != null && !isPolePoint(p, radius)) {
                    points3D.add(p);
                }
            }
        }

        if (points3D.size() < 3) {
            return null;
        }

        boolean north = averageZ(points3D) >= 0.0;

        ArrayList<DiskPoint> diskPoints = new ArrayList<>();

        for (Vec3 p : points3D) {
            DiskPoint dp = toPoleDiskPoint(p, radius, north);

            if (dp != null) {
                diskPoints.add(dp);
            }
        }

        if (diskPoints.size() < 3) {
            return null;
        }

        List<DiskPoint> hull = convexHull(diskPoints);

        if (hull.size() < 3) {
            return null;
        }

        ArrayList<Vec3> boundary = new ArrayList<>();

        for (DiskPoint dp : hull) {
            addIfDistinct(boundary, liftPoleDiskPoint(dp, radius, north));
        }

        boundary = new ArrayList<>(removeClosingDuplicate(removeConsecutiveDuplicates(boundary)));

        if (boundary.size() < 3) {
            return null;
        }

        return new PhiPatch(
                key.parentCellId(),
                key.ntheta(),
                -1,
                boundary,
                area,
                normalizedArea,
                key.parentPoleClassification());
    }

    /**
     * Projects one spherical point to a local disk centered on the relevant pole.
     *
     * @param p spherical point
     * @param radius spherical radius
     * @param north true for north pole, false for south pole
     * @return disk point
     */
    private static DiskPoint toPoleDiskPoint(Vec3 p, double radius, boolean north) {
        if (p == null || !Double.isFinite(radius) || radius <= 0.0) {
            return null;
        }

        double r = p.norm();

        if (!Double.isFinite(r) || r <= 0.0) {
            return null;
        }

        double theta = Math.acos(clamp(p.z() / r, -1.0, 1.0));
        double alpha = north ? theta : Math.PI - theta;
        double phi = Math.atan2(p.y(), p.x());
        double rho = radius * alpha;

        return new DiskPoint(
                rho * Math.cos(phi),
                rho * Math.sin(phi));
    }

    /**
     * Lifts one local pole-disk point back to the sphere.
     *
     * @param p local disk point
     * @param radius spherical radius
     * @param north true for north pole, false for south pole
     * @return spherical point
     */
    private static Vec3 liftPoleDiskPoint(DiskPoint p, double radius, boolean north) {
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
     * Computes the convex hull of local disk points using the monotone chain
     * algorithm.
     *
     * @param points input points
     * @return hull points in boundary order
     */
    private static List<DiskPoint> convexHull(List<DiskPoint> points) {
        ArrayList<DiskPoint> pts = new ArrayList<>(points);

        pts.sort((a, b) -> {
            int c = Double.compare(a.x(), b.x());
            if (c != 0) {
                return c;
            }
            return Double.compare(a.y(), b.y());
        });

        ArrayList<DiskPoint> unique = new ArrayList<>();

        for (DiskPoint p : pts) {
            if (unique.isEmpty() || !nearDisk(unique.get(unique.size() - 1), p)) {
                unique.add(p);
            }
        }

        if (unique.size() <= 3) {
            return unique;
        }

        ArrayList<DiskPoint> lower = new ArrayList<>();

        for (DiskPoint p : unique) {
            while (lower.size() >= 2
                    && crossDisk(
                            lower.get(lower.size() - 2),
                            lower.get(lower.size() - 1),
                            p) <= 0.0) {
                lower.remove(lower.size() - 1);
            }

            lower.add(p);
        }

        ArrayList<DiskPoint> upper = new ArrayList<>();

        for (int i = unique.size() - 1; i >= 0; i--) {
            DiskPoint p = unique.get(i);

            while (upper.size() >= 2
                    && crossDisk(
                            upper.get(upper.size() - 2),
                            upper.get(upper.size() - 1),
                            p) <= 0.0) {
                upper.remove(upper.size() - 1);
            }

            upper.add(p);
        }

        lower.remove(lower.size() - 1);
        upper.remove(upper.size() - 1);

        lower.addAll(upper);

        return lower;
    }

    /**
     * Cross product for three disk points.
     *
     * @param a first point
     * @param b second point
     * @param c third point
     * @return signed area proxy
     */
    private static double crossDisk(DiskPoint a, DiskPoint b, DiskPoint c) {
        double ux = b.x() - a.x();
        double uy = b.y() - a.y();
        double vx = c.x() - a.x();
        double vy = c.y() - a.y();

        return ux * vy - uy * vx;
    }

    /**
     * Tests local disk proximity.
     *
     * @param a first point
     * @param b second point
     * @return true if near
     */
    private static boolean nearDisk(DiskPoint a, DiskPoint b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();

        return dx * dx + dy * dy <= 1.0e-18;
    }


    /**
     * Local pole-disk point.
     *
     * @param x x coordinate
     * @param y y coordinate
     */
    private record DiskPoint(double x, double y) {
    }
    
    /**
     * Builds the set of exterior edges by cancelling shared edges.
     *
     * @param key patch key
     * @param group patch fragments
     * @param radius spherical radius
     * @return unmatched exterior edges
     */
    private static ArrayList<Edge> exteriorEdges(
            PatchKey key,
            List<PhiPatch> group,
            double radius) {

        Map<UndirectedEdgeKey, ArrayList<Edge>> buckets = new HashMap<>();

        boolean polarAggregate = key != null && key.polarAggregate();

        for (PhiPatch patch : group) {
            List<Vec3> boundary = cleanBoundary(patch.boundary());

            if (boundary.size() < 3) {
                continue;
            }

            for (int i = 0; i < boundary.size(); i++) {
                Vec3 a = boundary.get(i);
                Vec3 b = boundary.get((i + 1) % boundary.size());

                if (near(a, b)) {
                    continue;
                }

                /*
                 * Critical polar cleanup:
                 *
                 * For a pole-containing parent, the pole is inside the aggregate
                 * patch, not on its exterior boundary. Any edge incident on the pole
                 * is a synthetic fan edge from the theta-stage construction and must
                 * not survive into final/export geometry.
                 */
                if (polarAggregate && isSyntheticPolarFanEdge(a, b, radius)) {
                    continue;
                }
                
                Edge edge = new Edge(a, b);
                UndirectedEdgeKey edgeKey = new UndirectedEdgeKey(pointKey(a), pointKey(b));

                buckets.computeIfAbsent(edgeKey, unused -> new ArrayList<>()).add(edge);
            }
        }

        ArrayList<Edge> exterior = new ArrayList<>();

        for (ArrayList<Edge> bucket : buckets.values()) {
            /*
             * One occurrence means exterior edge.
             * Two occurrences usually means shared internal edge.
             * Odd leftovers are retained defensively.
             */
            if (bucket.size() == 1) {
                exterior.add(bucket.get(0));
            } else if ((bucket.size() % 2) == 1) {
                exterior.add(bucket.get(0));
            }
        }

        return exterior;
    }
    
    /**
     * Checks whether an edge is a synthetic polar fan edge.
     * <p>
     * In polar aggregate patches, radial/meridian edges are construction artifacts
     * from the theta-stage polar fan. They should not be part of final/export
     * boundaries. This includes both edges incident on the pole and constant-phi
     * radial edges between theta cuts.
     * </p>
     *
     * @param a first endpoint
     * @param b second endpoint
     * @param radius spherical radius
     * @return true if the edge should be discarded for polar aggregate geometry
     */
    private static boolean isSyntheticPolarFanEdge(Vec3 a, Vec3 b, double radius) {
        if (a == null || b == null || !Double.isFinite(radius) || radius <= 0.0) {
            return false;
        }

        if (isPolePoint(a, radius) || isPolePoint(b, radius)) {
            return true;
        }

        /*
         * A theta-cut boundary is a small-circle arc, so its z is nearly constant.
         * Do not remove those.
         */
        if (Math.abs(a.z() - b.z()) <= 1.0e-9 * radius) {
            return false;
        }

        /*
         * Synthetic fan/radial edges have essentially constant phi: their xy
         * projections lie on the same ray from the z axis.
         */
        return sameMeridianRay(a, b);
    }

    /**
     * Checks whether two spherical points lie on essentially the same meridian ray.
     *
     * @param a first point
     * @param b second point
     * @return true if same meridian ray
     */
    private static boolean sameMeridianRay(Vec3 a, Vec3 b) {
        double ra = Math.hypot(a.x(), a.y());
        double rb = Math.hypot(b.x(), b.y());

        if (ra <= 1.0e-12 || rb <= 1.0e-12) {
            return true;
        }

        double cross = a.x() * b.y() - a.y() * b.x();
        double dot = a.x() * b.x() + a.y() * b.y();

        return Math.abs(cross) <= 1.0e-10 * ra * rb && dot >= 0.0;
    }
    
    /**
     * Builds closed loops from unmatched exterior edges.
     *
     * @param edges exterior edges
     * @return loops
     */
    private static ArrayList<List<Vec3>> buildLoops(ArrayList<Edge> edges) {
        ArrayList<Edge> unused = new ArrayList<>(edges);
        ArrayList<List<Vec3>> loops = new ArrayList<>();

        while (!unused.isEmpty()) {
            Edge first = unused.remove(unused.size() - 1);

            ArrayList<Vec3> loop = new ArrayList<>();
            loop.add(first.a());
            loop.add(first.b());

            boolean extended = true;

            while (extended && loop.size() >= 2) {
                extended = false;

                Vec3 current = loop.get(loop.size() - 1);

                if (near(current, loop.get(0))) {
                    break;
                }

                for (int i = 0; i < unused.size(); i++) {
                    Edge edge = unused.get(i);

                    if (near(edge.a(), current)) {
                        loop.add(edge.b());
                        unused.remove(i);
                        extended = true;
                        break;
                    }

                    if (near(edge.b(), current)) {
                        loop.add(edge.a());
                        unused.remove(i);
                        extended = true;
                        break;
                    }
                }
            }

            loop = new ArrayList<>(removeClosingDuplicate(loop));
            loop = new ArrayList<>(cleanBoundary(loop));

            if (loop.size() >= 3) {
                loops.add(loop);
            }
        }

        return loops;
    }

    /**
     * Rebuilds a PhiPatch using a cleaned boundary and recomputed area.
     *
     * @param key patch key
     * @param boundary cleaned boundary
     * @param radius radius
     * @return rebuilt patch
     */
    private static PhiPatch rebuildPatch(PatchKey key, List<Vec3> boundary, double radius) {
    	if (key.polarAggregate()) {
    	    boundary = polarExteriorBoundary(boundary, radius);
    	}

    	double unitArea = SphericalPolygonArea.unsignedAreaUnitSphere(boundary);        double area = radius * radius * unitArea;
        double normalizedArea = unitArea / (4.0 * Math.PI);

        return new PhiPatch(
                key.parentCellId(),
                key.ntheta(),
                key.nphi(),
                boundary,
                area,
                normalizedArea,
                key.parentPoleClassification());
    }
    
    /**
     * Extracts an exterior boundary for a polar aggregate patch.
     * <p>
     * The merged polar aggregate may still contain a fan-like traversal inherited
     * from the theta-stage polar construction. For final/export geometry, the pole
     * is interior, so the desired boundary is the outer envelope of the aggregate
     * in a pole-centered disk.
     * </p>
     *
     * @param boundary merged boundary candidate
     * @param radius spherical radius
     * @return exterior polar boundary
     */
    private static List<Vec3> polarExteriorBoundary(List<Vec3> boundary, double radius) {
        if (boundary == null || boundary.size() < 3) {
            return List.of();
        }

        boolean north = averageZ(boundary) >= 0.0;

        ArrayList<PolarPoint> points = new ArrayList<>();

        for (Vec3 p : boundary) {
            if (p == null) {
                continue;
            }

            if (isPolePoint(p, radius)) {
                continue;
            }

            double r = p.norm();
            if (!Double.isFinite(r) || r <= 0.0) {
                continue;
            }

            double theta = Math.acos(clamp(p.z() / r, -1.0, 1.0));
            double alpha = north ? theta : Math.PI - theta;
            double phi = Math.atan2(p.y(), p.x());

            points.add(new PolarPoint(phi, alpha, p));
        }

        if (points.size() < 3) {
            return List.of();
        }

        /*
         * Sort by phi around the pole and keep the farthest point in each small
         * angular bin. That removes interior radial fan points while retaining the
         * exterior envelope.
         */
        points.sort((a, b) -> Double.compare(a.phi(), b.phi()));

        int bins = Math.max(180, points.size() / 2);
        PolarPoint[] best = new PolarPoint[bins];

        for (PolarPoint pp : points) {
            double phi01 = (pp.phi() + Math.PI) / (2.0 * Math.PI);
            int bin = (int) Math.floor(phi01 * bins);

            if (bin < 0) {
                bin = 0;
            } else if (bin >= bins) {
                bin = bins - 1;
            }

            if (best[bin] == null || pp.alpha() > best[bin].alpha()) {
                best[bin] = pp;
            }
        }

        ArrayList<Vec3> output = new ArrayList<>();

        for (PolarPoint pp : best) {
            if (pp != null) {
                addIfDistinct(output, pp.point());
            }
        }

        output = new ArrayList<>(removeClosingDuplicate(removeConsecutiveDuplicates(output)));

        if (output.size() < 3) {
            return List.of();
        }

        return output;
    }

    private static void addIfDistinct(List<Vec3> points, Vec3 p) {
        if (p == null) {
            return;
        }

        if (points.isEmpty() || !near(points.get(points.size() - 1), p)) {
            points.add(p);
        }
    }
    
    /**
     * Average z coordinate.
     *
     * @param points points
     * @return average z
     */
    private static double averageZ(List<Vec3> points) {
        double sum = 0.0;
        int count = 0;

        for (Vec3 p : points) {
            if (p != null) {
                sum += p.z();
                count++;
            }
        }

        return count == 0 ? 0.0 : sum / count;
    }

    /**
     * Polar-boundary helper.
     *
     * @param phi azimuth angle
     * @param alpha angular distance from relevant pole
     * @param point original spherical point
     */
    private record PolarPoint(double phi, double alpha, Vec3 point) {
    }

    /**
     * Cleans a sampled boundary.
     *
     * @param boundary input boundary
     * @return cleaned boundary
     */
    private static List<Vec3> cleanBoundary(List<Vec3> boundary) {
        if (boundary == null || boundary.isEmpty()) {
            return List.of();
        }

        List<Vec3> cleaned = removeConsecutiveDuplicates(boundary);
        cleaned = removeClosingDuplicate(cleaned);
        cleaned = removeNeedleSpikes(cleaned);

        if (cleaned.size() < 3) {
            return List.of();
        }

        return cleaned;
    }

    /**
     * Removes consecutive duplicate points.
     *
     * @param input input points
     * @return cleaned points
     */
    private static List<Vec3> removeConsecutiveDuplicates(List<Vec3> input) {
        ArrayList<Vec3> output = new ArrayList<>();

        for (Vec3 p : input) {
            if (p == null) {
                continue;
            }

            if (output.isEmpty() || !near(output.get(output.size() - 1), p)) {
                output.add(p);
            }
        }

        return output;
    }

    /**
     * Removes an explicit duplicate closing point.
     *
     * @param input input points
     * @return cleaned points
     */
    private static List<Vec3> removeClosingDuplicate(List<Vec3> input) {
        if (input == null || input.size() < 2) {
            return List.of();
        }

        ArrayList<Vec3> output = new ArrayList<>(input);

        while (output.size() > 1 && near(output.get(0), output.get(output.size() - 1))) {
            output.remove(output.size() - 1);
        }

        return output;
    }
    
    /**
     * Checks whether a point is essentially one of the spherical poles.
     * <p>
     * For polar aggregate patches, the pole is an interior construction point.
     * Edges incident on it are synthetic fan edges and should not be part of the
     * final exterior boundary.
     * </p>
     *
     * @param p point
     * @param radius spherical radius
     * @return true if the point is near the north or south pole
     */
    private static boolean isPolePoint(Vec3 p, double radius) {
        if (p == null || !Double.isFinite(radius) || radius <= 0.0) {
            return false;
        }

        double rho2 = p.x() * p.x() + p.y() * p.y();

        /*
         * Use a tolerance much looser than duplicate-point tolerance because points
         * that were projected/lifted through several stages may not be exactly
         * (0,0,+/-R), even though they are topologically the pole.
         */
        double rhoTol = 1.0e-9 * radius;

        if (rho2 > rhoTol * rhoTol) {
            return false;
        }

        return Math.abs(Math.abs(p.z()) - radius) <= 1.0e-8 * radius;
    }

    /**
     * Removes local A-B-A needle spikes.
     *
     * @param input input points
     * @return cleaned points
     */
    private static List<Vec3> removeNeedleSpikes(List<Vec3> input) {
        if (input == null || input.size() < 3) {
            return List.of();
        }

        ArrayList<Vec3> work = new ArrayList<>(input);

        boolean changed = true;
        int pass = 0;

        while (changed && pass < MAX_CLEANUP_PASSES && work.size() >= 3) {
            changed = false;
            pass++;

            ArrayList<Vec3> next = new ArrayList<>();
            int n = work.size();

            for (int i = 0; i < n; i++) {
                Vec3 prev = work.get((i - 1 + n) % n);
                Vec3 curr = work.get(i);
                Vec3 nextPoint = work.get((i + 1) % n);

                if (near(prev, nextPoint)) {
                    changed = true;
                    continue;
                }

                next.add(curr);
            }

            work = new ArrayList<>(removeClosingDuplicate(removeConsecutiveDuplicates(next)));
        }

        if (work.size() < 3) {
            return List.of();
        }

        return work;
    }

    /**
     * Creates a quantized point key.
     *
     * @param p point
     * @return point key
     */
    private static PointKey pointKey(Vec3 p) {
        return new PointKey(
                Math.round(p.x() / KEY_TOL),
                Math.round(p.y() / KEY_TOL),
                Math.round(p.z() / KEY_TOL));
    }

    /**
     * Tests whether two points are approximately identical.
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
     * Grouping key for canonical final patches.
     *
     * @param parentCellId parent Cartesian cell id
     * @param ntheta theta index
     * @param nphi phi index, or -1 for polar aggregate
     * @param parentPoleClassification inherited pole classification
     * @param polarAggregate true for polar aggregate groups
     */
    private record PatchKey(
            CellId parentCellId,
            int ntheta,
            int nphi,
            PoleClassification parentPoleClassification,
            boolean polarAggregate) {
    }

    /**
     * Quantized point key.
     */
    private record PointKey(long x, long y, long z) implements Comparable<PointKey> {

        @Override
        public int compareTo(PointKey other) {
            int c = Long.compare(x, other.x);
            if (c != 0) {
                return c;
            }

            c = Long.compare(y, other.y);
            if (c != 0) {
                return c;
            }

            return Long.compare(z, other.z);
        }
    }

    /**
     * Undirected edge key.
     */
    private record UndirectedEdgeKey(PointKey a, PointKey b) {

        private UndirectedEdgeKey {
            if (b.compareTo(a) < 0) {
                PointKey tmp = a;
                a = b;
                b = tmp;
            }
        }
    }

    /**
     * Directed edge.
     */
    private record Edge(Vec3 a, Vec3 b) {
    }
}