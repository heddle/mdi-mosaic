package edu.cnu.mdi.mosaic.patch;

/**
 * Describes how a prepatch is related to one of the GSM spherical poles.
 * <p>
 * This classification is diagnostic metadata for now. It is intended to help
 * later theta/phi splicing stages identify prepatches that need polar special
 * handling.
 * </p>
 */
public enum PoleRelation {

    /**
     * The pole is not in this prepatch's Cartesian cell.
     */
    NONE,

    /**
     * The pole lies strictly inside the cell/prepatch, not on a cell face.
     */
    INSIDE,

    /**
     * The pole lies on one of the cell faces and therefore on the prepatch
     * boundary.
     */
    ON_BOUNDARY,

    /**
     * The pole lies at a cell corner, which is also a boundary vertex case.
     */
    AT_VERTEX
}