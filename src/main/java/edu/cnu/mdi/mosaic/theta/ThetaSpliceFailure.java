package edu.cnu.mdi.mosaic.theta;

import edu.cnu.mdi.mosaic.cell.CellId;

/**
 * Diagnostic record for a prepatch that failed theta splicing.
 *
 * @param cellId parent prepatch cell id
 * @param message failure message
 */
public record ThetaSpliceFailure(CellId cellId, String message) {

    @Override
    public String toString() {
        return "cell=" + cellId + ", " + message;
    }
}