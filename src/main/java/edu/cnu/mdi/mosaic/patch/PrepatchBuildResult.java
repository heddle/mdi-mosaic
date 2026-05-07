package edu.cnu.mdi.mosaic.patch;

import java.util.List;

import edu.cnu.mdi.mosaic.cell.IntersectingCell;

/**
 * Result of prepatch construction.
 *
 * @param prepatches ordinary prepatches that were built
 * @param deferredCells intersecting cells deliberately deferred to later
 *        degeneracy handling
 * @param failures ordinary-cell failure diagnostics
 * @param stats build statistics
 */
public record PrepatchBuildResult(
        List<Prepatch> prepatches,
        List<IntersectingCell> deferredCells,
        List<PrepatchFailure> failures,
        PrepatchBuildStats stats) {

    /**
     * Creates a build result.
     *
     * @param prepatches prepatches
     * @param deferredCells deferred cells
     * @param failures failure diagnostics
     * @param stats statistics
     */
    public PrepatchBuildResult {
        prepatches = List.copyOf(prepatches == null ? List.of() : prepatches);
        deferredCells = List.copyOf(deferredCells == null ? List.of() : deferredCells);
        failures = List.copyOf(failures == null ? List.of() : failures);
        if (stats == null) {
            stats = PrepatchBuildStats.empty();
        }
    }
}