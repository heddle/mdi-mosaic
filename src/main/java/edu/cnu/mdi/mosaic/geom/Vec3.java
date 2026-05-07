package edu.cnu.mdi.mosaic.geom;

/**
 * Immutable three-dimensional vector.
 *
 * @param x x coordinate
 * @param y y coordinate
 * @param z z coordinate
 */
public record Vec3(double x, double y, double z) {

    /**
     * Computes the squared Euclidean norm.
     *
     * @return {@code x*x + y*y + z*z}
     */
    public double norm2() {
        return x * x + y * y + z * z;
    }

    /**
     * Computes the Euclidean norm.
     *
     * @return the vector length
     */
    public double norm() {
        return Math.sqrt(norm2());
    }
}