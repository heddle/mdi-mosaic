package edu.cnu.mdi.mosaic.theta;

import java.util.List;

import edu.cnu.mdi.mosaic.cell.CellId;
import edu.cnu.mdi.mosaic.geom.Vec3;
import edu.cnu.mdi.mosaic.patch.PoleClassification;

/**
 * A theta-spliced patch.
 * <p>
 * A theta patch is the portion of one prepatch that lies in a single spherical
 * theta band:
 * </p>
 *
 * <pre>
 * theta[ntheta] <= theta <= theta[ntheta + 1]
 * </pre>
 *
 * <p>
 * This first implementation stores an approximate sampled boundary on the
 * sphere. Later exact splicing can replace the sampled boundary while preserving
 * this class's role in the algorithm pipeline.
 * </p>
 *
 * @param parentCellId Cartesian cell id of the parent prepatch
 * @param ntheta theta-cell index
 * @param boundary sampled boundary points on the sphere
 * @param area physical area
 * @param normalizedArea area divided by {@code 4*pi*R^2}
 * @param parentPoleClassification pole classification inherited from parent
 */
public record ThetaPatch(
        CellId parentCellId,
        int ntheta,
        List<Vec3> boundary,
        double area,
        double normalizedArea,
        PoleClassification parentPoleClassification) {

    /**
     * Creates a theta patch.
     *
     * @param parentCellId parent Cartesian cell id
     * @param ntheta theta-cell index
     * @param boundary sampled boundary
     * @param area physical area
     * @param normalizedArea normalized area
     * @param parentPoleClassification parent pole classification
     */
    public ThetaPatch {
        if (parentCellId == null) {
            throw new IllegalArgumentException("parentCellId must not be null.");
        }
        if (ntheta < 0) {
            throw new IllegalArgumentException("ntheta must be nonnegative.");
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
}