package edu.cnu.mdi.mosaic.export;

import java.nio.file.Path;
import java.util.List;

import edu.cnu.mdi.mosaic.phi.PhiPatch;

/**
 * Scratch helper for manually testing final-patch export.
 */
public final class MosaicExportScratch {

    /**
     * Hidden constructor.
     */
    private MosaicExportScratch() {
    }

    /**
     * Writes final patches to a JSON file.
     *
     * @param patches final patches
     * @param outputPath output path
     * @throws Exception if export fails
     */
    public static void writeFinalPatchesForTesting(
            List<PhiPatch> patches,
            Path outputPath) throws Exception {

        MosaicFinalPatchExportSummary summary =
                MosaicFinalPatchJsonExporter.export(
                        outputPath,
                        patches,
                        MosaicFinalPatchExportOptions.defaults());

        System.out.println("Exported " + summary.patchCount()
                + " final patches to " + summary.outputPath());
    }
}