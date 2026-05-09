package edu.cnu.mdi.mosaic.phi;

import edu.cnu.mdi.mosaic.cell.CellId;

/**
 * Key identifying a theta-patch parent group.
 *
 * @param cellId parent Cartesian cell id
 * @param ntheta theta-cell index
 */
public record ThetaPatchKey(CellId cellId, int ntheta) {
}