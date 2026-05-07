package edu.cnu.mdi.mosaic.cell;

/**
 * Immutable identifier for a Cartesian grid cell.
 *
 * @param nx x-cell index
 * @param ny y-cell index
 * @param nz z-cell index
 */
public record CellId(int nx, int ny, int nz) {

    @Override
    public String toString() {
        return "(" + nx + ", " + ny + ", " + nz + ")";
    }
}