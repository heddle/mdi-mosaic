package edu.cnu.mdi.mosaic.patch;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.cnu.mdi.mosaic.cell.CellGeometry;
import edu.cnu.mdi.mosaic.cell.CellIntersectionType;
import edu.cnu.mdi.mosaic.cell.IntersectingCell;
import edu.cnu.mdi.mosaic.geom.Vec3;

/**
 * Builds ordinary prepatch boundary curves from intersecting Cartesian cells.
 * <p>
 * This first implementation handles {@link CellIntersectionType#CORNER_STRADDLE}
 * cells. Other intersecting-cell types are recorded as deferred rather than
 * guessed, so the algorithm remains explicit and debuggable.
 * </p>
 */
public final class PrepatchBuilder {

    /** Numerical tolerance. */
    private static final double TOL = 1.0e-12;

    /**
     * Hidden constructor for utility class.
     */
    private PrepatchBuilder() {
    }

    /**
     * Builds ordinary prepatches.
     *
     * @param cells intersecting cells from step 1
     * @param radius spherical shell radius
     * @return prepatch build result
     */
    public static PrepatchBuildResult buildPrepatches(
            List<IntersectingCell> cells, double radius) {

        if (cells == null) {
            return new PrepatchBuildResult(List.of(), List.of(), List.of(),
                    PrepatchBuildStats.empty());
        }

        ArrayList<Prepatch> prepatches = new ArrayList<>();
        ArrayList<IntersectingCell> deferred = new ArrayList<>();
        ArrayList<PrepatchFailure> failures = new ArrayList<>();

        EnumMap<PrepatchFailureReason, Integer> failureCounts =
                new EnumMap<>(PrepatchFailureReason.class);
        for (PrepatchFailureReason reason : PrepatchFailureReason.values()) {
            failureCounts.put(reason, 0);
        }

        int ordinaryVisited = 0;
        int curvesBuilt = 0;
        int deferredFacePenetration = 0;
        int deferredBoundary = 0;
        int deferredTangent = 0;

        for (IntersectingCell cell : cells) {
            if (cell == null || cell.type() == null) {
                continue;
            }

            switch (cell.type()) {
            case CORNER_STRADDLE -> {
                ordinaryVisited++;

                OrdinaryBuildResult result = buildOrdinaryPrepatch(cell, radius);

                if (result.prepatch != null && result.prepatch.hasCurves()) {
                    prepatches.add(result.prepatch);
                    curvesBuilt += result.prepatch.curveCount();
                } else {
                    deferred.add(cell);

                    PrepatchFailure failure = result.failure;
                    if (failure == null) {
                        failure = new PrepatchFailure(
                                cell.id(),
                                PrepatchFailureReason.TOO_FEW_CURVES,
                                0,
                                new int[6],
                                "Ordinary prepatch construction failed without a specific reason.");
                    }

                    failures.add(failure);
                    failureCounts.put(failure.reason(),
                            failureCounts.get(failure.reason()) + 1);
                }
            }

            case FACE_PENETRATION_NO_INSIDE_CORNERS -> {
                deferredFacePenetration++;
                deferred.add(cell);
            }

            case BOUNDARY_DEGENERATE -> {
                deferredBoundary++;
                deferred.add(cell);
            }

            case TANGENT_OR_NEAR_TANGENT -> {
                deferredTangent++;
                deferred.add(cell);
            }
            }
        }

        PrepatchBuildStats stats = new PrepatchBuildStats(
                ordinaryVisited,
                prepatches.size(),
                curvesBuilt,
                deferredFacePenetration,
                deferredBoundary,
                deferredTangent,
                failures.size(),
                failureCounts);

        return new PrepatchBuildResult(prepatches, deferred, failures, stats);
    }

    /**
     * Builds an ordinary prepatch from one corner-straddling cell.
     *
     * @param cell intersecting cell
     * @param radius sphere radius
     * @return ordinary build result
     */
    private static OrdinaryBuildResult buildOrdinaryPrepatch(
            IntersectingCell cell, double radius) {

        CellGeometry geometry = cell.geometry();

        List<SphereEdgeIntersection> intersections =
                findEdgeIntersections(geometry, radius);

        int[] faceHitCounts = faceHitCounts(intersections);

        if (intersections.size() < 3) {
            return OrdinaryBuildResult.failure(new PrepatchFailure(
                    cell.id(),
                    PrepatchFailureReason.TOO_FEW_EDGE_INTERSECTIONS,
                    intersections.size(),
                    faceHitCounts,
                    "Expected at least 3 edge-sphere intersections."));
        }

        ArrayList<GeneralCurve> curves = new ArrayList<>();

        for (int face = 0; face < 6; face++) {
            List<SphereEdgeIntersection> faceHits =
                    intersectionsOnFace(intersections, face);

            if (faceHits.isEmpty()) {
                continue;
            }

            if (faceHits.size() != 2) {
                return OrdinaryBuildResult.failure(new PrepatchFailure(
                        cell.id(),
                        PrepatchFailureReason.FACE_HIT_COUNT_NOT_TWO,
                        intersections.size(),
                        faceHitCounts,
                        "Face " + face + " has " + faceHits.size()
                                + " hits; ordinary face arc requires exactly 2."));
            }

            GeneralCurve curve = GeneralCurve.create(
                    geometry,
                    face,
                    radius,
                    faceHits.get(0).point(),
                    faceHits.get(1).point());

            if (curve == null) {
                return OrdinaryBuildResult.failure(new PrepatchFailure(
                        cell.id(),
                        PrepatchFailureReason.CURVE_CREATION_FAILED,
                        intersections.size(),
                        faceHitCounts,
                        "Could not build valid face arc on face " + face + "."));
            }

            curves.add(curve);
        }

        if (curves.size() < 3) {
            return OrdinaryBuildResult.failure(new PrepatchFailure(
                    cell.id(),
                    PrepatchFailureReason.TOO_FEW_CURVES,
                    intersections.size(),
                    faceHitCounts,
                    "Built only " + curves.size()
                            + " curves; ordinary prepatch needs at least 3."));
        }

        return OrdinaryBuildResult.success(
                new Prepatch(cell.id(), cell.type(), curves));
    }

    /**
     * Finds edge-sphere intersections for one ordinary cell.
     *
     * @param geometry cell geometry
     * @param radius sphere radius
     * @return edge intersections
     */
    private static List<SphereEdgeIntersection> findEdgeIntersections(
            CellGeometry geometry, double radius) {

        ArrayList<SphereEdgeIntersection> intersections = new ArrayList<>();
        double r2 = radius * radius;

        for (int edge = 0; edge < 12; edge++) {
            int[] corners = CellGeometry.getEdgeCornerIndices(edge);

            Vec3 p0 = geometry.corner(corners[0]);
            Vec3 p1 = geometry.corner(corners[1]);

            double f0 = p0.norm2() - r2;
            double f1 = p1.norm2() - r2;

            /*
             * This first ordinary builder expects true sign straddles. Boundary
             * degeneracies are handled later.
             */
            if (f0 * f1 >= 0.0) {
                continue;
            }

            Vec3 p = segmentSphereIntersection(p0, p1, radius);
            if (p != null) {
                intersections.add(new SphereEdgeIntersection(
                        edge,
                        corners[0],
                        corners[1],
                        p,
                        adjacentFacesForEdge(edge)));
            }
        }

        return intersections;
    }

    /**
     * Counts edge intersections on each of the six faces.
     *
     * @param intersections edge intersections
     * @return face hit counts
     */
    private static int[] faceHitCounts(List<SphereEdgeIntersection> intersections) {
        int[] counts = new int[6];

        if (intersections == null) {
            return counts;
        }

        for (SphereEdgeIntersection intersection : intersections) {
            for (int face : intersection.adjacentFaces()) {
                counts[face]++;
            }
        }

        return counts;
    }

    /**
     * Computes the sphere intersection point on a straddling segment.
     *
     * @param p0 segment start
     * @param p1 segment end
     * @param radius sphere radius
     * @return intersection point, or {@code null}
     */
    private static Vec3 segmentSphereIntersection(Vec3 p0, Vec3 p1, double radius) {
        double dx = p1.x() - p0.x();
        double dy = p1.y() - p0.y();
        double dz = p1.z() - p0.z();

        double a = dx * dx + dy * dy + dz * dz;
        double b = 2.0 * (p0.x() * dx + p0.y() * dy + p0.z() * dz);
        double c = p0.norm2() - radius * radius;

        double disc = b * b - 4.0 * a * c;

        if (disc < -TOL) {
            return null;
        }

        disc = Math.max(0.0, disc);
        double sqrt = Math.sqrt(disc);

        double t0 = (-b - sqrt) / (2.0 * a);
        double t1 = (-b + sqrt) / (2.0 * a);

        double t;

        if (t0 >= -TOL && t0 <= 1.0 + TOL) {
            t = clamp01(t0);
        } else if (t1 >= -TOL && t1 <= 1.0 + TOL) {
            t = clamp01(t1);
        } else {
            return null;
        }

        return new Vec3(
                p0.x() + t * dx,
                p0.y() + t * dy,
                p0.z() + t * dz);
    }

    /**
     * Filters edge intersections on one face.
     *
     * @param intersections all edge intersections
     * @param face face index
     * @return intersections on that face
     */
    private static List<SphereEdgeIntersection> intersectionsOnFace(
            List<SphereEdgeIntersection> intersections, int face) {

        ArrayList<SphereEdgeIntersection> hits = new ArrayList<>();

        for (SphereEdgeIntersection intersection : intersections) {
            if (intersection.isOnFace(face)) {
                hits.add(intersection);
            }
        }

        return hits;
    }

    /**
     * Finds the two faces adjacent to an edge.
     *
     * @param edge edge index
     * @return two adjacent face indices
     */
    private static int[] adjacentFacesForEdge(int edge) {
        int[] edgeCorners = CellGeometry.getEdgeCornerIndices(edge);

        ArrayList<Integer> faces = new ArrayList<>(2);

        for (int face = 0; face < 6; face++) {
            int[] faceCorners = CellGeometry.getFaceCornerIndices(face);
            Set<Integer> set = new HashSet<>();
            for (int c : faceCorners) {
                set.add(c);
            }

            if (set.contains(edgeCorners[0]) && set.contains(edgeCorners[1])) {
                faces.add(face);
            }
        }

        if (faces.size() != 2) {
            throw new IllegalStateException(
                    "Canonical edge " + edge + " does not have two adjacent faces.");
        }

        return new int[] {faces.get(0), faces.get(1)};
    }

    /**
     * Clamps to [0, 1].
     *
     * @param t input value
     * @return clamped value
     */
    private static double clamp01(double t) {
        return Math.max(0.0, Math.min(1.0, t));
    }

    /**
     * Internal ordinary-cell build result.
     */
    private record OrdinaryBuildResult(Prepatch prepatch, PrepatchFailure failure) {

        /**
         * Creates a successful result.
         *
         * @param prepatch prepatch
         * @return result
         */
        static OrdinaryBuildResult success(Prepatch prepatch) {
            return new OrdinaryBuildResult(prepatch, null);
        }

        /**
         * Creates a failed result.
         *
         * @param failure failure diagnostic
         * @return result
         */
        static OrdinaryBuildResult failure(PrepatchFailure failure) {
            return new OrdinaryBuildResult(null, failure);
        }
    }
}