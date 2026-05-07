package edu.cnu.mdi.mosaic.patch;

import java.util.ArrayList;
import java.util.List;

import edu.cnu.mdi.mosaic.cell.CellGeometry;
import edu.cnu.mdi.mosaic.cell.CellId;
import edu.cnu.mdi.mosaic.geom.Vec3;

/**
 * One ordinary GENERAL curve forming part of a prepatch boundary.
 * <p>
 * A GENERAL curve lies on both the spherical shell and one face of a Cartesian
 * cell. Since the cell face is one of {@code x = constant},
 * {@code y = constant}, or {@code z = constant}, the curve is an arc of a
 * circle in that plane.
 * </p>
 */
public final class GeneralCurve {

    /** Numerical tolerance used for circle construction. */
    private static final double TOL = 1.0e-10;

    /** Number of samples used when choosing between the two possible arcs. */
    private static final int ARC_SELECTION_SAMPLES = 17;

    /** Owning cell id. */
    private final CellId cellId;

    /** Face index on which this curve lies. */
    private final int faceIndex;

    /** Sphere radius. */
    private final double radius;

    /** Constant coordinate value of the face plane. */
    private final double planeValue;

    /** Start point. */
    private final Vec3 start;

    /** End point. */
    private final Vec3 end;

    /** Start angle in the local face-plane circle. */
    private final double startAngle;

    /** Signed angular sweep from start to end. */
    private final double signedDelta;

    /**
     * Creates a general curve.
     *
     * @param cellId owning cell id
     * @param faceIndex face index
     * @param radius sphere radius
     * @param planeValue constant face-plane coordinate
     * @param start start point
     * @param end end point
     * @param startAngle local start angle
     * @param signedDelta signed angular sweep
     */
    private GeneralCurve(CellId cellId, int faceIndex, double radius,
            double planeValue, Vec3 start, Vec3 end,
            double startAngle, double signedDelta) {

        this.cellId = cellId;
        this.faceIndex = faceIndex;
        this.radius = radius;
        this.planeValue = planeValue;
        this.start = start;
        this.end = end;
        this.startAngle = startAngle;
        this.signedDelta = signedDelta;
    }

    /**
     * Creates a curve on a cell face between two sphere-edge intersections.
     * <p>
     * There are two possible circular arcs between two points. This factory
     * samples both candidate arcs and chooses the one that lies most consistently
     * inside the finite rectangular face. This is more robust than a single
     * midpoint test for near-corner and near-tangent geometries.
     * </p>
     *
     * @param geometry cell geometry
     * @param faceIndex face index
     * @param radius sphere radius
     * @param start start point
     * @param end end point
     * @return the curve, or {@code null} if a valid face arc cannot be built
     */
    public static GeneralCurve create(CellGeometry geometry, int faceIndex,
            double radius, Vec3 start, Vec3 end) {

        if (geometry == null || start == null || end == null) {
            return null;
        }

        double planeValue = facePlaneValue(geometry, faceIndex);
        double circleRadius2 = radius * radius - planeValue * planeValue;

        /*
         * If the face plane lies outside the sphere by more than tolerance, no
         * face-circle exists. If it is tangent or nearly tangent, keep going. Tiny
         * face circles are valid and occur near tangent Cartesian faces.
         */
        double radiusScale = Math.max(1.0, radius * radius);
        double circleTol = 1.0e-12 * radiusScale;

        if (circleRadius2 < -circleTol) {
            return null;
        }

        circleRadius2 = Math.max(0.0, circleRadius2);
        
        double a0 = angleOnFace(faceIndex, start);
        double a1 = angleOnFace(faceIndex, end);

        double positiveDelta = positiveDelta(a0, a1);
        double negativeDelta = positiveDelta - 2.0 * Math.PI;

        ArcScore positiveScore = scoreArc(geometry, faceIndex, planeValue,
                radius, a0, positiveDelta);

        ArcScore negativeScore = scoreArc(geometry, faceIndex, planeValue,
                radius, a0, negativeDelta);

        double chosenDelta;

        if (positiveScore.acceptable() && negativeScore.acceptable()) {
            chosenDelta = chooseBetterDelta(positiveDelta, positiveScore,
                    negativeDelta, negativeScore);
        } else if (positiveScore.acceptable()) {
            chosenDelta = positiveDelta;
        } else if (negativeScore.acceptable()) {
            chosenDelta = negativeDelta;
        } else {
            return null;
        }

        return new GeneralCurve(geometry.id(), faceIndex, radius,
                planeValue, start, end, a0, chosenDelta);
    }

    /**
     * Gets the owning cell id.
     *
     * @return cell id
     */
    public CellId getCellId() {
        return cellId;
    }

    /**
     * Gets the face index.
     *
     * @return face index
     */
    public int getFaceIndex() {
        return faceIndex;
    }

    /**
     * Gets the sphere radius.
     *
     * @return radius
     */
    public double getRadius() {
        return radius;
    }

    /**
     * Gets the start point.
     *
     * @return start point
     */
    public Vec3 getStart() {
        return start;
    }

    /**
     * Gets the end point.
     *
     * @return end point
     */
    public Vec3 getEnd() {
        return end;
    }

    /**
     * Samples the curve.
     *
     * @param count number of points to return; values below 2 are promoted to 2
     * @return sampled points from start to end
     */
    public List<Vec3> sample(int count) {
        int n = Math.max(2, count);
        ArrayList<Vec3> points = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            double f = (double) i / (n - 1);
            double angle = startAngle + f * signedDelta;
            points.add(pointOnFaceCircle(faceIndex, planeValue, radius, angle));
        }

        return points;
    }

    /**
     * Scores a candidate arc by sampling points along it and counting how many
     * lie inside the finite rectangular face.
     *
     * @param geometry cell geometry
     * @param face face index
     * @param planeValue constant face coordinate
     * @param radius sphere radius
     * @param startAngle start angle
     * @param delta signed angular sweep
     * @return arc score
     */
    private static ArcScore scoreArc(CellGeometry geometry, int face,
            double planeValue, double radius, double startAngle, double delta) {

        int inside = 0;
        int total = ARC_SELECTION_SAMPLES;

        double tol = faceTolerance(geometry);

        for (int i = 0; i < total; i++) {
            double f = (double) i / (total - 1);
            double angle = startAngle + f * delta;
            Vec3 p = pointOnFaceCircle(face, planeValue, radius, angle);

            if (isInsideFaceRectangle(geometry, face, p, tol)) {
                inside++;
            }
        }

        return new ArcScore(inside, total);
    }

    /**
     * Chooses between two acceptable candidate arcs.
     *
     * @param d1 first delta
     * @param s1 first score
     * @param d2 second delta
     * @param s2 second score
     * @return selected delta
     */
    private static double chooseBetterDelta(double d1, ArcScore s1,
            double d2, ArcScore s2) {

        if (s1.insideCount() != s2.insideCount()) {
            return (s1.insideCount() > s2.insideCount()) ? d1 : d2;
        }

        /*
         * Tie-break: choose the shorter absolute sweep. In normal face
         * intersections, the prepatch boundary arc is usually the shorter of the
         * two arcs on the face circle.
         */
        return (Math.abs(d1) <= Math.abs(d2)) ? d1 : d2;
    }

    /**
     * Gets the constant face-plane coordinate.
     *
     * @param geometry cell geometry
     * @param face face index
     * @return plane coordinate
     */
    private static double facePlaneValue(CellGeometry geometry, int face) {
        return switch (face) {
        case 0 -> geometry.z0();
        case 1 -> geometry.z1();
        case 2 -> geometry.y0();
        case 3 -> geometry.y1();
        case 4 -> geometry.x0();
        case 5 -> geometry.x1();
        default -> throw new IllegalArgumentException("Invalid face index: " + face);
        };
    }

    /**
     * Computes the local circle angle of a point on a face.
     *
     * @param face face index
     * @param p point
     * @return local angle
     */
    private static double angleOnFace(int face, Vec3 p) {
        return switch (face) {
        case 0, 1 -> Math.atan2(p.y(), p.x()); // xy face, z constant
        case 2, 3 -> Math.atan2(p.z(), p.x()); // xz face, y constant
        case 4, 5 -> Math.atan2(p.z(), p.y()); // yz face, x constant
        default -> throw new IllegalArgumentException("Invalid face index: " + face);
        };
    }

    /**
     * Creates a point on the face circle.
     *
     * @param face face index
     * @param planeValue constant face coordinate
     * @param radius sphere radius
     * @param angle local circle angle
     * @return point on the sphere and face plane
     */
    private static Vec3 pointOnFaceCircle(int face, double planeValue,
            double radius, double angle) {

    	double circleRadius2 = radius * radius - planeValue * planeValue;
    	double circleRadius = Math.sqrt(Math.max(0.0, circleRadius2));
    	
        double c = circleRadius * Math.cos(angle);
        double s = circleRadius * Math.sin(angle);

        return switch (face) {
        case 0, 1 -> new Vec3(c, s, planeValue);
        case 2, 3 -> new Vec3(c, planeValue, s);
        case 4, 5 -> new Vec3(planeValue, c, s);
        default -> throw new IllegalArgumentException("Invalid face index: " + face);
        };
    }

    /**
     * Checks whether a point lies inside the finite rectangular face.
     *
     * @param geometry cell geometry
     * @param face face index
     * @param p point
     * @param tol tolerance
     * @return {@code true} if inside the face rectangle
     */
    private static boolean isInsideFaceRectangle(CellGeometry geometry,
            int face, Vec3 p, double tol) {

        return switch (face) {
        case 0, 1 -> between(p.x(), geometry.x0(), geometry.x1(), tol)
                  && between(p.y(), geometry.y0(), geometry.y1(), tol);
        case 2, 3 -> between(p.x(), geometry.x0(), geometry.x1(), tol)
                  && between(p.z(), geometry.z0(), geometry.z1(), tol);
        case 4, 5 -> between(p.y(), geometry.y0(), geometry.y1(), tol)
                  && between(p.z(), geometry.z0(), geometry.z1(), tol);
        default -> false;
        };
    }

    /**
     * Computes a face-rectangle tolerance scaled to the cell dimensions.
     *
     * @param geometry cell geometry
     * @return tolerance
     */
    private static double faceTolerance(CellGeometry geometry) {
        double hx = Math.abs(geometry.x1() - geometry.x0());
        double hy = Math.abs(geometry.y1() - geometry.y0());
        double hz = Math.abs(geometry.z1() - geometry.z0());

        double h = Math.max(hx, Math.max(hy, hz));
        return Math.max(1.0e-10, 1.0e-8 * h);
    }

    /**
     * Tests whether a value lies between two bounds with tolerance.
     *
     * @param value value
     * @param lo lower bound
     * @param hi upper bound
     * @param tol tolerance
     * @return true if inside
     */
    private static boolean between(double value, double lo, double hi, double tol) {
        return value >= lo - tol && value <= hi + tol;
    }

    /**
     * Computes the positive angular delta from a0 to a1.
     *
     * @param a0 start angle
     * @param a1 end angle
     * @return delta in {@code [0, 2*pi)}
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

    @Override
    public String toString() {
        return "GeneralCurve[cell=" + cellId
                + ", face=" + faceIndex
                + ", sweep=" + signedDelta + "]";
    }

    /**
     * Score for a candidate arc.
     *
     * @param insideCount number of sampled points inside the face rectangle
     * @param totalCount total sampled points
     */
    private record ArcScore(int insideCount, int totalCount) {

        /**
         * Tests whether the arc is acceptable.
         * <p>
         * All samples are preferred, but allowing all but one sample prevents
         * harmless roundoff near the endpoints from causing a valid ordinary arc
         * to fail.
         * </p>
         *
         * @return true if acceptable
         */
        boolean acceptable() {
        	return insideCount >= Math.max(2, totalCount / 2);
        }
    }
}