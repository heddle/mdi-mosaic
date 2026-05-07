package edu.cnu.mdi.mosaic.cell;

/**
 * Classification of how a Cartesian cell intersects the Mosaic spherical shell.
 */
public enum CellIntersectionType {

    /**
     * The cell has at least one corner inside the sphere and at least one corner
     * outside the sphere. This is the ordinary intersection case.
     */
    CORNER_STRADDLE,

    /**
     * The cell intersects the spherical surface but has no strictly inside
     * corners. This includes the important face-penetration case where a face
     * cuts the sphere even though all corners are outside.
     */
    FACE_PENETRATION_NO_INSIDE_CORNERS,

    /**
     * The cell is tangent, or nearly tangent within tolerance, to the spherical
     * surface.
     */
    TANGENT_OR_NEAR_TANGENT,

    /**
     * The spherical surface passes through one or more cell corners or edges.
     * This is a boundary-degenerate case that is still a valid intersecting
     * cell.
     */
    BOUNDARY_DEGENERATE
}