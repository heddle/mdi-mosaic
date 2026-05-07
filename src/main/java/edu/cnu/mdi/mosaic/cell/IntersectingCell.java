package edu.cnu.mdi.mosaic.cell;

import edu.cnu.mdi.mosaic.geom.Vec3;

/**
 * A Cartesian cell whose volume intersects the Mosaic spherical surface.
 *
 * @param geometry cell geometry
 * @param type intersection classification
 * @param closestPoint closest point in the cell to the origin
 * @param minDistanceSquared minimum squared distance from origin to cell
 * @param maxDistanceSquared maximum squared distance from origin to cell
 * @param insideCornerCount number of corners strictly inside the sphere
 * @param outsideCornerCount number of corners strictly outside the sphere
 * @param boundaryCornerCount number of corners on the sphere within tolerance
 */
public record IntersectingCell(
        CellGeometry geometry,
        CellIntersectionType type,
        Vec3 closestPoint,
        double minDistanceSquared,
        double maxDistanceSquared,
        int insideCornerCount,
        int outsideCornerCount,
        int boundaryCornerCount) {

    /**
     * Gets the cell id.
     *
     * @return the cell id
     */
    public CellId id() {
        return geometry.id();
    }

    @Override
    public String toString() {
        return "IntersectingCell[id=" + id()
                + ", type=" + type
                + ", inside=" + insideCornerCount
                + ", outside=" + outsideCornerCount
                + ", boundary=" + boundaryCornerCount
                + "]";
    }
}