package edu.cnu.mdi.mosaic.cell;

import java.util.Arrays;

import edu.cnu.mdi.mosaic.geom.Vec3;

/**
 * Geometry of one axis-aligned Cartesian grid cell.
 * <p>
 * The cell is defined by its lower and upper coordinate bounds:
 * </p>
 *
 * <pre>
 * x0 <= x <= x1
 * y0 <= y <= y1
 * z0 <= z <= z1
 * </pre>
 *
 * <h2>Canonical corner numbering</h2>
 *
 * <pre>
 * 0: (x0, y0, z0)
 * 1: (x1, y0, z0)
 * 2: (x0, y1, z0)
 * 3: (x1, y1, z0)
 * 4: (x0, y0, z1)
 * 5: (x1, y0, z1)
 * 6: (x0, y1, z1)
 * 7: (x1, y1, z1)
 * </pre>
 *
 * <h2>Canonical face numbering</h2>
 *
 * <pre>
 * 0: xy face at z = z0
 * 1: xy face at z = z1
 * 2: xz face at y = y0
 * 3: xz face at y = y1
 * 4: yz face at x = x0
 * 5: yz face at x = x1
 * </pre>
 */
public final class CellGeometry {

    /** Canonical edge corner pairs. */
    private static final int[][] EDGE_CORNER_INDICES = {
            {0, 1}, {2, 3}, {4, 5}, {6, 7},
            {0, 2}, {1, 3}, {4, 6}, {5, 7},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    /** Cell identifier. */
    private final CellId id;

    /** Cell bounds. */
    private final double x0, x1, y0, y1, z0, z1;

    /**
     * Creates a cell geometry.
     *
     * @param id cell identifier
     * @param x0 lower x coordinate
     * @param x1 upper x coordinate
     * @param y0 lower y coordinate
     * @param y1 upper y coordinate
     * @param z0 lower z coordinate
     * @param z1 upper z coordinate
     */
    public CellGeometry(CellId id,
            double x0, double x1,
            double y0, double y1,
            double z0, double z1) {

        if (id == null) {
            throw new IllegalArgumentException("cell id must not be null.");
        }

        validateBounds("x", x0, x1);
        validateBounds("y", y0, y1);
        validateBounds("z", z0, z1);

        this.id = id;
        this.x0 = x0;
        this.x1 = x1;
        this.y0 = y0;
        this.y1 = y1;
        this.z0 = z0;
        this.z1 = z1;
    }

    /**
     * Gets the cell identifier.
     *
     * @return the cell id
     */
    public CellId id() {
        return id;
    }

    /**
     * Gets the lower x bound.
     *
     * @return x0
     */
    public double x0() {
        return x0;
    }

    /**
     * Gets the upper x bound.
     *
     * @return x1
     */
    public double x1() {
        return x1;
    }

    /**
     * Gets the lower y bound.
     *
     * @return y0
     */
    public double y0() {
        return y0;
    }

    /**
     * Gets the upper y bound.
     *
     * @return y1
     */
    public double y1() {
        return y1;
    }

    /**
     * Gets the lower z bound.
     *
     * @return z0
     */
    public double z0() {
        return z0;
    }

    /**
     * Gets the upper z bound.
     *
     * @return z1
     */
    public double z1() {
        return z1;
    }

    /**
     * Gets the coordinates of a canonical corner.
     *
     * @param corner corner index in {@code [0, 7]}
     * @return the corner coordinates
     */
    public Vec3 corner(int corner) {
        return switch (corner) {
        case 0 -> new Vec3(x0, y0, z0);
        case 1 -> new Vec3(x1, y0, z0);
        case 2 -> new Vec3(x0, y1, z0);
        case 3 -> new Vec3(x1, y1, z0);
        case 4 -> new Vec3(x0, y0, z1);
        case 5 -> new Vec3(x1, y0, z1);
        case 6 -> new Vec3(x0, y1, z1);
        case 7 -> new Vec3(x1, y1, z1);
        default -> throw new IllegalArgumentException("Invalid corner index: " + corner);
        };
    }

    /**
     * Gets all eight canonical corners.
     *
     * @return the cell corners
     */
    public Vec3[] corners() {
        Vec3[] corners = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            corners[i] = corner(i);
        }
        return corners;
    }

    /**
     * Gets the corner indices of a canonical face.
     * <p>
     * The returned order is chosen so each face is traversed consistently around
     * its perimeter.
     * </p>
     *
     * @param face face index in {@code [0, 5]}
     * @return four corner indices
     */
    public static int[] getFaceCornerIndices(int face) {
        return switch (face) {
        case 0 -> new int[] {0, 1, 3, 2};
        case 1 -> new int[] {4, 5, 7, 6};
        case 2 -> new int[] {0, 1, 5, 4};
        case 3 -> new int[] {2, 3, 7, 6};
        case 4 -> new int[] {0, 2, 6, 4};
        case 5 -> new int[] {1, 3, 7, 5};
        default -> throw new IllegalArgumentException("Invalid face index: " + face);
        };
    }

    /**
     * Gets the two corner indices defining a canonical edge.
     *
     * @param edge edge index in {@code [0, 11]}
     * @return two corner indices
     */
    public static int[] getEdgeCornerIndices(int edge) {
        if (edge < 0 || edge >= EDGE_CORNER_INDICES.length) {
            throw new IllegalArgumentException("Invalid edge index: " + edge);
        }
        return Arrays.copyOf(EDGE_CORNER_INDICES[edge], 2);
    }

    /**
     * Finds the closest point in this cell to the origin.
     *
     * @return closest point to the origin
     */
    public Vec3 closestPointToOrigin() {
        return new Vec3(
                clamp(0.0, x0, x1),
                clamp(0.0, y0, y1),
                clamp(0.0, z0, z1));
    }

    /**
     * Computes the squared distance from the origin to the closest point in the
     * cell.
     *
     * @return minimum squared distance from origin to cell
     */
    public double minDistanceSquaredToOrigin() {
        return closestPointToOrigin().norm2();
    }

    /**
     * Computes the maximum squared distance from the origin to the cell.
     * <p>
     * For an axis-aligned box, the maximum occurs at one of the corners.
     * </p>
     *
     * @return maximum squared distance from origin to cell
     */
    public double maxDistanceSquaredToOrigin() {
        double max = 0.0;
        for (int i = 0; i < 8; i++) {
            max = Math.max(max, corner(i).norm2());
        }
        return max;
    }

    /**
     * Counts corners strictly inside a sphere.
     *
     * @param radius sphere radius
     * @param tolerance squared-distance tolerance
     * @return number of strictly inside corners
     */
    public int countInsideCorners(double radius, double tolerance) {
        double r2 = radius * radius;
        int count = 0;

        for (int i = 0; i < 8; i++) {
            if (corner(i).norm2() < r2 - tolerance) {
                count++;
            }
        }

        return count;
    }

    /**
     * Counts corners strictly outside a sphere.
     *
     * @param radius sphere radius
     * @param tolerance squared-distance tolerance
     * @return number of strictly outside corners
     */
    public int countOutsideCorners(double radius, double tolerance) {
        double r2 = radius * radius;
        int count = 0;

        for (int i = 0; i < 8; i++) {
            if (corner(i).norm2() > r2 + tolerance) {
                count++;
            }
        }

        return count;
    }

    /**
     * Counts corners on the sphere within tolerance.
     *
     * @param radius sphere radius
     * @param tolerance squared-distance tolerance
     * @return number of boundary corners
     */
    public int countBoundaryCorners(double radius, double tolerance) {
        double r2 = radius * radius;
        int count = 0;

        for (int i = 0; i < 8; i++) {
            if (Math.abs(corner(i).norm2() - r2) <= tolerance) {
                count++;
            }
        }

        return count;
    }

    /**
     * Tests whether the spherical surface intersects this cell.
     * <p>
     * This is a robust axis-aligned-box/sphere-surface test. The cell intersects
     * the spherical surface if the closest point in the box is on or inside the
     * sphere and the farthest point in the box is on or outside the sphere:
     * </p>
     *
     * <pre>
     * dmin^2 <= R^2 <= dmax^2
     * </pre>
     *
     * <p>
     * This catches the important case where no corners are inside the sphere,
     * but a nearly tangent face still penetrates the spherical surface.
     * </p>
     *
     * @param radius sphere radius
     * @param tolerance squared-distance tolerance
     * @return {@code true} if the spherical surface intersects this cell
     */
    public boolean intersectsSphereSurface(double radius, double tolerance) {
        double r2 = radius * radius;
        double min2 = minDistanceSquaredToOrigin();
        double max2 = maxDistanceSquaredToOrigin();

        return min2 <= r2 + tolerance && max2 >= r2 - tolerance;
    }

    /**
     * Classifies the intersection with the spherical surface.
     *
     * @param radius sphere radius
     * @param tolerance squared-distance tolerance
     * @return the intersection type, or {@code null} if the cell does not
     *         intersect the spherical surface
     */
    public CellIntersectionType classifySphereSurfaceIntersection(double radius, double tolerance) {
        if (!intersectsSphereSurface(radius, tolerance)) {
            return null;
        }

        int inside = countInsideCorners(radius, tolerance);
        int outside = countOutsideCorners(radius, tolerance);
        int boundary = countBoundaryCorners(radius, tolerance);

        double r2 = radius * radius;
        double min2 = minDistanceSquaredToOrigin();

        if (boundary > 0) {
            return CellIntersectionType.BOUNDARY_DEGENERATE;
        }

        if (inside > 0 && outside > 0) {
            return CellIntersectionType.CORNER_STRADDLE;
        }

        if (Math.abs(min2 - r2) <= tolerance) {
            return CellIntersectionType.TANGENT_OR_NEAR_TANGENT;
        }

        return CellIntersectionType.FACE_PENETRATION_NO_INSIDE_CORNERS;
    }

    /**
     * Validates bounds.
     *
     * @param axis axis name
     * @param lo lower bound
     * @param hi upper bound
     */
    private static void validateBounds(String axis, double lo, double hi) {
        if (!Double.isFinite(lo) || !Double.isFinite(hi) || hi <= lo) {
            throw new IllegalArgumentException(
                    "Invalid " + axis + " bounds: [" + lo + ", " + hi + "]");
        }
    }

    /**
     * Clamps a value to an interval.
     *
     * @param value input value
     * @param lo lower bound
     * @param hi upper bound
     * @return clamped value
     */
    private static double clamp(double value, double lo, double hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}