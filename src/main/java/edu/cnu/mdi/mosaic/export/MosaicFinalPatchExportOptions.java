package edu.cnu.mdi.mosaic.export;

/**
 * Options controlling final-patch JSON export.
 *
 * @param sphereRadius radius used for perimeter and physical-area metadata; if
 *        nonpositive, the exporter estimates the radius from patch boundary
 *        points
 * @param includeCartesianBoundary true to include Cartesian boundary points
 * @param includeSphericalBoundary true to include spherical boundary points
 * @param includeBoundarySegments true to include parameterized great-circle
 *        segment records
 * @param pretty true for human-readable formatted JSON
 */
public record MosaicFinalPatchExportOptions(
        double sphereRadius,
        boolean includeCartesianBoundary,
        boolean includeSphericalBoundary,
        boolean includeBoundarySegments,
        boolean pretty) {

    /**
     * Creates default export options.
     *
     * @return default options
     */
    public static MosaicFinalPatchExportOptions defaults() {
        return new MosaicFinalPatchExportOptions(
                Double.NaN,
                true,
                true,
                true,
                true);
    }

    /**
     * Creates default options using a specified sphere radius.
     *
     * @param sphereRadius sphere radius
     * @return options
     */
    public static MosaicFinalPatchExportOptions withRadius(double sphereRadius) {
        return new MosaicFinalPatchExportOptions(
                sphereRadius,
                true,
                true,
                true,
                true);
    }
}