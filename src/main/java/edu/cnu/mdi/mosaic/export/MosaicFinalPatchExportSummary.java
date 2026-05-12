package edu.cnu.mdi.mosaic.export;

import java.nio.file.Path;

/**
 * Summary returned after exporting final patches.
 *
 * @param outputPath output path
 * @param patchCount number of patches exported
 * @param totalNormalizedArea total normalized area
 * @param totalArea total physical area stored in the patches
 * @param totalPerimeter total perimeter over exported patches
 * @param sphereRadius sphere radius used by the exporter
 */
public record MosaicFinalPatchExportSummary(
        Path outputPath,
        int patchCount,
        double totalNormalizedArea,
        double totalArea,
        double totalPerimeter,
        double sphereRadius) {
}