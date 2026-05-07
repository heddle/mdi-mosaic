package edu.cnu.mdi.mosaic.patch;

import java.util.List;

import edu.cnu.mdi.mosaic.cell.CellId;
import edu.cnu.mdi.mosaic.cell.CellIntersectionType;

/**
 * A prepatch is the intersection of one Cartesian grid cell with the Mosaic
 * spherical shell, before theta and phi splicing.
 * <p>
 * In the ordinary case, the prepatch boundary is represented by a cyclic list
 * of GENERAL curves, each lying on the sphere and on one Cartesian cell face.
 * </p>
 *
 * @param cellId Cartesian cell id
 * @param sourceType source intersection type
 * @param curves boundary curves
 */
public record Prepatch(
        CellId cellId,
        CellIntersectionType sourceType,
        List<GeneralCurve> curves) {

    /**
     * Creates a prepatch.
     *
     * @param cellId cell id
     * @param sourceType source type
     * @param curves boundary curves
     */
    public Prepatch {
        if (cellId == null) {
            throw new IllegalArgumentException("cellId must not be null.");
        }
        if (sourceType == null) {
            throw new IllegalArgumentException("sourceType must not be null.");
        }
        curves = List.copyOf(curves == null ? List.of() : curves);
    }

    /**
     * Gets the number of boundary curves.
     *
     * @return curve count
     */
    public int curveCount() {
        return curves.size();
    }

    /**
     * Checks whether this prepatch has ordinary boundary curves.
     *
     * @return true if the curve list is nonempty
     */
    public boolean hasCurves() {
        return !curves.isEmpty();
    }
}