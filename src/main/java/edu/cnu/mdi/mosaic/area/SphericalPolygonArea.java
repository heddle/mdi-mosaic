package edu.cnu.mdi.mosaic.area;

import java.util.List;

import edu.cnu.mdi.mosaic.geom.Vec3;

/**
 * Utility methods for estimating the area of spherical polygons.
 * <p>
 * The polygon vertices are interpreted as points on a sphere. Internally they
 * are normalized to unit vectors, and the returned area is the area on the unit
 * sphere. Multiply by {@code radius * radius} to obtain physical area.
 * </p>
 *
 * <p>
 * This implementation triangulates the spherical polygon fan-wise from the
 * first vertex and uses the robust oriented spherical-triangle formula:
 * </p>
 *
 * <pre>
 * area = 2 atan2( det(a,b,c), 1 + a·b + b·c + c·a )
 * </pre>
 *
 * <p>
 * The absolute value is used by the public unsigned-area method. This is
 * appropriate for the small prepatch regions expected in Mosaic.
 * </p>
 */
public final class SphericalPolygonArea {

    /** Small tolerance used for vector checks. */
    private static final double TOL = 1.0e-14;

    /**
     * Hidden constructor for utility class.
     */
    private SphericalPolygonArea() {
    }

    /**
     * Computes the unsigned area of a spherical polygon on the unit sphere.
     *
     * @param vertices polygon vertices in cyclic order
     * @return unsigned area on the unit sphere
     */
    public static double unsignedAreaUnitSphere(List<Vec3> vertices) {
        if (vertices == null || vertices.size() < 3) {
            return 0.0;
        }

        List<Vec3> unit = vertices.stream()
                .map(SphericalPolygonArea::normalize)
                .filter(v -> v != null)
                .toList();

        if (unit.size() < 3) {
            return 0.0;
        }

        Vec3 a0 = unit.get(0);
        double area = 0.0;

        for (int i = 1; i < unit.size() - 1; i++) {
            Vec3 b = unit.get(i);
            Vec3 c = unit.get(i + 1);

            double triArea = orientedTriangleAreaUnitSphere(a0, b, c);

            if (Double.isFinite(triArea)) {
                area += triArea;
            }
        }

        area = Math.abs(area);

        /*
         * For small prepatches this should never be necessary, but it protects
         * against orientation/complement surprises.
         */
        double fullSphere = 4.0 * Math.PI;
        if (area > fullSphere) {
            area = area % fullSphere;
        }

        if (area > 2.0 * Math.PI) {
            area = fullSphere - area;
        }

        return area;
    }

    /**
     * Computes the oriented area of a spherical triangle on the unit sphere.
     *
     * @param a first unit vector
     * @param b second unit vector
     * @param c third unit vector
     * @return oriented area
     */
    private static double orientedTriangleAreaUnitSphere(Vec3 a, Vec3 b, Vec3 c) {
        double det = dot(a, cross(b, c));
        double denom = 1.0 + dot(a, b) + dot(b, c) + dot(c, a);

        return 2.0 * Math.atan2(det, denom);
    }

    /**
     * Normalizes a vector.
     *
     * @param v input vector
     * @return normalized vector, or {@code null} if invalid
     */
    private static Vec3 normalize(Vec3 v) {
        if (v == null) {
            return null;
        }

        double n = v.norm();

        if (!Double.isFinite(n) || n < TOL) {
            return null;
        }

        return new Vec3(v.x() / n, v.y() / n, v.z() / n);
    }

    /**
     * Dot product.
     *
     * @param a first vector
     * @param b second vector
     * @return dot product
     */
    private static double dot(Vec3 a, Vec3 b) {
        return a.x() * b.x() + a.y() * b.y() + a.z() * b.z();
    }

    /**
     * Cross product.
     *
     * @param a first vector
     * @param b second vector
     * @return cross product
     */
    private static Vec3 cross(Vec3 a, Vec3 b) {
        return new Vec3(
                a.y() * b.z() - a.z() * b.y(),
                a.z() * b.x() - a.x() * b.z(),
                a.x() * b.y() - a.y() * b.x());
    }
}