package edu.cnu.mdi.mosaic.phi;

import edu.cnu.mdi.mosaic.cell.CellId;

/**
 * Diagnostic record for a theta patch that failed phi splicing.
 *
 * @param cellId parent Cartesian cell id
 * @param ntheta parent theta-cell index
 * @param message failure message
 */
public record PhiSpliceFailure(CellId cellId, int ntheta, String message) {

    @Override
    public String toString() {
        return "cell=" + cellId + ", ntheta=" + ntheta + ", " + message;
    }
}