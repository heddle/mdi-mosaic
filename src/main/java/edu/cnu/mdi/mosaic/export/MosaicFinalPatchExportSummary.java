package edu.cnu.mdi.mosaic.export;

import java.nio.file.Path;

/**
 * Summary returned after exporting final patches.
 *
 * @param outputPath output path
 * @param patchCount number of patches exported
 * @param totalNormalizedArea total normalized area
 * @param totalPerimeterOverRadius total perimeter divided by sphere radius
 * @param sphereRadius sphere radius used by the exporter
 */
public record MosaicFinalPatchExportSummary(
        Path outputPath,
        int patchCount,
        double totalNormalizedArea,
        double totalPerimeterOverRadius,
        double sphereRadius) {
}