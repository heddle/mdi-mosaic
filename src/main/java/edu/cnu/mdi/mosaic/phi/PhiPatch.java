package edu.cnu.mdi.mosaic.phi;

import java.util.List;

import edu.cnu.mdi.mosaic.cell.CellId;
import edu.cnu.mdi.mosaic.geom.Vec3;
import edu.cnu.mdi.mosaic.patch.PoleClassification;

/**
 * A final phi-spliced patch.
 * <p>
 * A normal phi patch is the portion of one theta patch that lies in a single
 * phi band. It is identified by the parent Cartesian cell id, the theta-cell
 * index, and the phi-cell index.
 * </p>
 *
 * <p>
 * Polar-derived patches may be canonicalized into aggregate final patches.
 * Since phi is singular at the pole, such aggregate patches use {@code nphi=-1}
 * to mean "not a single phi cell."
 * </p>
 *
 * @param parentCellId parent Cartesian cell id
 * @param ntheta theta-cell index
 * @param nphi phi-cell index, or -1 for a polar aggregate patch
 * @param boundary sampled boundary points on the sphere
 * @param area physical area
 * @param normalizedArea area divided by {@code 4*pi*R^2}
 * @param parentPoleClassification pole classification inherited from the parent
 */
public record PhiPatch(
        CellId parentCellId,
        int ntheta,
        int nphi,
        List<Vec3> boundary,
        double area,
        double normalizedArea,
        PoleClassification parentPoleClassification) {

    /**
     * Creates a phi patch.
     *
     * @param parentCellId parent Cartesian cell id
     * @param ntheta theta-cell index
     * @param nphi phi-cell index, or -1 for a polar aggregate patch
     * @param boundary sampled boundary
     * @param area physical area
     * @param normalizedArea normalized area
     * @param parentPoleClassification inherited pole classification
     */
    public PhiPatch {
        if (parentCellId == null) {
            throw new IllegalArgumentException("parentCellId must not be null.");
        }
        if (ntheta < 0) {
            throw new IllegalArgumentException("ntheta must be nonnegative.");
        }
        if (nphi < -1) {
            throw new IllegalArgumentException("nphi must be nonnegative, or -1 for a polar aggregate patch.");
        }

        boundary = List.copyOf(boundary == null ? List.of() : boundary);
    }

    /**
     * Gets the number of boundary points.
     *
     * @return boundary point count
     */
    public int boundaryPointCount() {
        return boundary.size();
    }

    /**
     * Checks whether this patch has usable boundary data.
     *
     * @return true if there are at least three boundary points
     */
    public boolean hasBoundary() {
        return boundary.size() >= 3;
    }

    /**
     * Checks whether this patch is a polar aggregate patch.
     *
     * @return true if {@code nphi == -1}
     */
    public boolean isPolarAggregate() {
        return nphi == -1;
    }
}