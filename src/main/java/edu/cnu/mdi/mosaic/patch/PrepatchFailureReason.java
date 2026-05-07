package edu.cnu.mdi.mosaic.patch;

/**
 * Reason an ordinary corner-straddling cell failed prepatch construction.
 * <p>
 * Ordinary cells are expected to produce a closed boundary made from face arcs.
 * When that does not happen, this enum records where the construction failed.
 * </p>
 */
public enum PrepatchFailureReason {

    /**
     * The cell had fewer than three edge-sphere intersections.
     */
    TOO_FEW_EDGE_INTERSECTIONS,

    /**
     * One or more faces had a nonzero number of edge intersections other than
     * two. Ordinary face arcs require exactly two endpoints on a face.
     */
    FACE_HIT_COUNT_NOT_TWO,

    /**
     * A face had two endpoints, but a valid circular face arc could not be
     * constructed between them.
     */
    CURVE_CREATION_FAILED,

    /**
     * Curves were built, but fewer than three boundary curves were obtained.
     */
    TOO_FEW_CURVES
}