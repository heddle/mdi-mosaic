package edu.cnu.mdi.mosaic.cell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.cnu.mdi.mosaic.geom.Vec3;
import edu.cnu.mdi.mosaic.grid.CartesianGrid;
import edu.cnu.mdi.mosaic.grid.Grid1D;
import edu.cnu.mdi.mosaic.model.MosaicGridSpec;

/**
 * Finds Cartesian cells whose volumes intersect the Mosaic spherical surface.
 * <p>
 * This is the first step in the exact patch algorithm. It identifies candidate
 * prepatch cells indexed by {@code (nx, ny, nz)}.
 * </p>
 */
public final class IntersectingCellFinder {

    /**
     * Hidden constructor for utility class.
     */
    private IntersectingCellFinder() {
    }

    /**
     * Finds all Cartesian cells that intersect the spherical shell in the given
     * grid specification.
     *
     * @param gridSpec Mosaic grid specification
     * @return intersecting cells
     */
    public static List<IntersectingCell> findIntersectingCells(MosaicGridSpec gridSpec) {
        if (gridSpec == null) {
            throw new IllegalArgumentException("gridSpec must not be null.");
        }

        CartesianGrid cart = gridSpec.getCartesianGrid();
        double radius = gridSpec.getSphericalGrid().getRadius();

        return findIntersectingCells(cart, radius);
    }

    /**
     * Finds all Cartesian cells that intersect a sphere centered at the origin.
     *
     * @param cart Cartesian grid
     * @param radius sphere radius
     * @return intersecting cells
     */
    public static List<IntersectingCell> findIntersectingCells(CartesianGrid cart, double radius) {
        if (cart == null) {
            throw new IllegalArgumentException("cart must not be null.");
        }
        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException("radius must be positive and finite.");
        }

        Grid1D xGrid = cart.getXGrid();
        Grid1D yGrid = cart.getYGrid();
        Grid1D zGrid = cart.getZGrid();

        int[] xLimits = candidateLimits(xGrid, cart.getXOffset(), radius);
        int[] yLimits = candidateLimits(yGrid, cart.getYOffset(), radius);
        int[] zLimits = candidateLimits(zGrid, cart.getZOffset(), radius);

        if (xLimits == null || yLimits == null || zLimits == null) {
            return Collections.emptyList();
        }

        double tolerance = defaultSquaredDistanceTolerance(radius, xGrid, yGrid, zGrid);

        List<IntersectingCell> cells = new ArrayList<>();

        for (int nx = xLimits[0]; nx <= xLimits[1]; nx++) {
            double x0 = xGrid.valueAt(nx) + cart.getXOffset();
            double x1 = xGrid.valueAt(nx + 1) + cart.getXOffset();

            for (int ny = yLimits[0]; ny <= yLimits[1]; ny++) {
                double y0 = yGrid.valueAt(ny) + cart.getYOffset();
                double y1 = yGrid.valueAt(ny + 1) + cart.getYOffset();

                for (int nz = zLimits[0]; nz <= zLimits[1]; nz++) {
                    double z0 = zGrid.valueAt(nz) + cart.getZOffset();
                    double z1 = zGrid.valueAt(nz + 1) + cart.getZOffset();

                    CellGeometry geometry = new CellGeometry(
                            new CellId(nx, ny, nz),
                            x0, x1, y0, y1, z0, z1);

                    IntersectingCell cell = classify(geometry, radius, tolerance);
                    if (cell != null) {
                        cells.add(cell);
                    }
                }
            }
        }

        return cells;
    }

    /**
     * Classifies a cell as intersecting or non-intersecting.
     *
     * @param geometry cell geometry
     * @param radius sphere radius
     * @param tolerance squared-distance tolerance
     * @return intersecting cell record, or {@code null}
     */
    private static IntersectingCell classify(CellGeometry geometry,
            double radius, double tolerance) {

        CellIntersectionType type =
                geometry.classifySphereSurfaceIntersection(radius, tolerance);

        if (type == null) {
            return null;
        }

        Vec3 closest = geometry.closestPointToOrigin();

        return new IntersectingCell(
                geometry,
                type,
                closest,
                geometry.minDistanceSquaredToOrigin(),
                geometry.maxDistanceSquaredToOrigin(),
                geometry.countInsideCorners(radius, tolerance),
                geometry.countOutsideCorners(radius, tolerance),
                geometry.countBoundaryCorners(radius, tolerance));
    }

    /**
     * Finds conservative candidate limits for one grid axis.
     * <p>
     * A cell is included if its global coordinate interval overlaps
     * {@code [-radius, radius]}. This is only a broad filter; the full
     * box-sphere-surface test is still applied later.
     * </p>
     *
     * @param grid one-dimensional grid
     * @param offset coordinate offset
     * @param radius sphere radius
     * @return two-element inclusive cell-index limits, or {@code null} if no
     *         cell interval overlaps the sphere's coordinate extent
     */
    private static int[] candidateLimits(Grid1D grid, double offset, double radius) {
        int lower = Integer.MAX_VALUE;
        int upper = Integer.MIN_VALUE;

        for (int i = 0; i < grid.numCells(); i++) {
            double lo = grid.valueAt(i) + offset;
            double hi = grid.valueAt(i + 1) + offset;

            if (hi >= -radius && lo <= radius) {
                lower = Math.min(lower, i);
                upper = Math.max(upper, i);
            }
        }

        if (lower == Integer.MAX_VALUE) {
            return null;
        }

        return new int[] {lower, upper};
    }

    /**
     * Creates a conservative squared-distance tolerance.
     * <p>
     * The tolerance is intentionally small, but scaled to the radius and grid
     * spacing so that rounded values such as {@code 1.5708} and decimal grid
     * endpoints do not cause inconsistent boundary classification.
     * </p>
     *
     * @param radius sphere radius
     * @param xGrid x grid
     * @param yGrid y grid
     * @param zGrid z grid
     * @return squared-distance tolerance
     */
    private static double defaultSquaredDistanceTolerance(double radius,
            Grid1D xGrid, Grid1D yGrid, Grid1D zGrid) {

        double h = Math.max(xGrid.getMaxSpacing(),
                Math.max(yGrid.getMaxSpacing(), zGrid.getMaxSpacing()));

        double lengthTol = 1.0e-10 * Math.max(radius, h);
        lengthTol = Math.max(lengthTol, 1.0e-12);

        /*
         * Convert a length tolerance to a squared-distance tolerance using the
         * approximate relation d(r^2) = 2 r dr.
         */
        return Math.max(1.0e-14, 2.0 * radius * lengthTol);
    }
}