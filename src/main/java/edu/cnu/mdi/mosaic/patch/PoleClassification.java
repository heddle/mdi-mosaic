package edu.cnu.mdi.mosaic.patch;

/**
 * Pole involvement classification for a prepatch.
 *
 * @param north relation of the prepatch to the +GSM Z pole
 * @param south relation of the prepatch to the -GSM Z pole
 */
public record PoleClassification(PoleRelation north, PoleRelation south) {

    /**
     * Creates a pole classification.
     *
     * @param north north-pole relation
     * @param south south-pole relation
     */
    public PoleClassification {
        north = (north == null) ? PoleRelation.NONE : north;
        south = (south == null) ? PoleRelation.NONE : south;
    }

    /**
     * Classification with no pole involvement.
     */
    public static final PoleClassification NONE =
            new PoleClassification(PoleRelation.NONE, PoleRelation.NONE);

    /**
     * Checks whether either pole is involved.
     *
     * @return {@code true} if either pole is inside, on the boundary, or at a
     *         vertex
     */
    public boolean hasPoleInvolvement() {
        return north != PoleRelation.NONE || south != PoleRelation.NONE;
    }

    /**
     * Checks whether the north pole is involved.
     *
     * @return {@code true} if the north pole is involved
     */
    public boolean hasNorthPoleInvolvement() {
        return north != PoleRelation.NONE;
    }

    /**
     * Checks whether the south pole is involved.
     *
     * @return {@code true} if the south pole is involved
     */
    public boolean hasSouthPoleInvolvement() {
        return south != PoleRelation.NONE;
    }

    @Override
    public String toString() {
        if (!hasPoleInvolvement()) {
            return "NONE";
        }

        return "north=" + north + ", south=" + south;
    }
}