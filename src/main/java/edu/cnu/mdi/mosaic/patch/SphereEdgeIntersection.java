package edu.cnu.mdi.mosaic.patch;

import java.util.Arrays;

import edu.cnu.mdi.mosaic.geom.Vec3;

/**
 * Intersection of a Cartesian cell edge with the Mosaic spherical shell.
 * <p>
 * For ordinary corner-straddling cells, each active cell edge intersects the
 * sphere once. These edge points become the endpoints of the prepatch boundary
 * curves on the cell faces.
 * </p>
 *
 * @param edgeIndex canonical edge index
 * @param corner0 first canonical corner index of the edge
 * @param corner1 second canonical corner index of the edge
 * @param point intersection point on the sphere
 * @param adjacentFaces the two canonical face indices adjacent to this edge
 */
public record SphereEdgeIntersection(
        int edgeIndex,
        int corner0,
        int corner1,
        Vec3 point,
        int[] adjacentFaces) {

    /**
     * Creates an edge-sphere intersection.
     *
     * @param edgeIndex canonical edge index
     * @param corner0 first edge corner
     * @param corner1 second edge corner
     * @param point point on the sphere
     * @param adjacentFaces adjacent face indices
     */
    public SphereEdgeIntersection {
        if (point == null) {
            throw new IllegalArgumentException("point must not be null.");
        }
        if (adjacentFaces == null || adjacentFaces.length != 2) {
            throw new IllegalArgumentException("adjacentFaces must contain exactly two faces.");
        }
        adjacentFaces = Arrays.copyOf(adjacentFaces, adjacentFaces.length);
    }

    /**
     * Checks whether this edge intersection belongs to a face.
     *
     * @param face face index
     * @return {@code true} if the edge lies on the face
     */
    public boolean isOnFace(int face) {
        return adjacentFaces[0] == face || adjacentFaces[1] == face;
    }

    @Override
    public int[] adjacentFaces() {
        return Arrays.copyOf(adjacentFaces, adjacentFaces.length);
    }
}