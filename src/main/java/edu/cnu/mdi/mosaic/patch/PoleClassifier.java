package edu.cnu.mdi.mosaic.patch;

import edu.cnu.mdi.mosaic.cell.CellGeometry;

/**
 * Classifies whether a Cartesian cell/sphere prepatch involves either GSM pole.
 * <p>
 * The +GSM Z pole is {@code (0, 0, +R)} and the -GSM Z pole is
 * {@code (0, 0, -R)}. A prepatch can only involve a pole if the Cartesian cell
 * contains the pole position.
 * </p>
 */
public final class PoleClassifier {

    /** Numerical tolerance in Cartesian coordinate units. */
    private static final double TOL = 1.0e-10;

    /**
     * Hidden constructor for utility class.
     */
    private PoleClassifier() {
    }

    /**
     * Classifies the pole involvement of a cell.
     *
     * @param geometry cell geometry
     * @param radius spherical shell radius
     * @return pole classification
     */
    public static PoleClassification classify(CellGeometry geometry, double radius) {
        if (geometry == null || !Double.isFinite(radius) || radius <= 0.0) {
            return PoleClassification.NONE;
        }

        PoleRelation north = classifyPole(geometry, radius);
        PoleRelation south = classifyPole(geometry, -radius);

        return new PoleClassification(north, south);
    }

    /**
     * Classifies one pole at {@code (0,0,zPole)}.
     *
     * @param g cell geometry
     * @param zPole pole z coordinate
     * @return pole relation
     */
    private static PoleRelation classifyPole(CellGeometry g, double zPole) {
        if (!between(0.0, g.x0(), g.x1())
                || !between(0.0, g.y0(), g.y1())
                || !between(zPole, g.z0(), g.z1())) {
            return PoleRelation.NONE;
        }

        boolean onXFace = near(0.0, g.x0()) || near(0.0, g.x1());
        boolean onYFace = near(0.0, g.y0()) || near(0.0, g.y1());
        boolean onZFace = near(zPole, g.z0()) || near(zPole, g.z1());

        int faceCount = 0;
        if (onXFace) {
            faceCount++;
        }
        if (onYFace) {
            faceCount++;
        }
        if (onZFace) {
            faceCount++;
        }

        if (faceCount >= 3) {
            return PoleRelation.AT_VERTEX;
        }

        if (faceCount > 0) {
            return PoleRelation.ON_BOUNDARY;
        }

        return PoleRelation.INSIDE;
    }

    /**
     * Checks whether a value is inside an interval.
     *
     * @param value value
     * @param lo lower bound
     * @param hi upper bound
     * @return true if inside with tolerance
     */
    private static boolean between(double value, double lo, double hi) {
        return value >= lo - TOL && value <= hi + TOL;
    }

    /**
     * Checks whether two values are nearly equal.
     *
     * @param a first value
     * @param b second value
     * @return true if near
     */
    private static boolean near(double a, double b) {
        return Math.abs(a - b) <= TOL;
    }
}