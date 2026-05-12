package edu.cnu.mdi.mosaic.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import edu.cnu.mdi.mosaic.cell.CellId;
import edu.cnu.mdi.mosaic.geom.Vec3;
import edu.cnu.mdi.mosaic.phi.PhiPatch;

/**
 * Exports final phi-spliced mosaic patches to JSON.
 * <p>
 * This class has no UI dependencies. It is intended to be called later from a
 * menu item, toolbar action, test harness, or command-line helper.
 * </p>
 */
public final class MosaicFinalPatchJsonExporter {

    /** JSON schema name for this export format. */
    private static final String SCHEMA = "mdi-mosaic.final-patches.v1";

    /** Degrees per radian. */
    private static final double DEG = 180.0 / Math.PI;

    /**
     * Hidden constructor.
     */
    private MosaicFinalPatchJsonExporter() {
    }

    /**
     * Exports final patches using default options.
     *
     * @param outputPath output JSON path
     * @param patches final phi patches
     * @return export summary
     * @throws IOException if writing fails
     */
    public static MosaicFinalPatchExportSummary export(
            Path outputPath,
            List<PhiPatch> patches) throws IOException {

        return export(outputPath, patches, MosaicFinalPatchExportOptions.defaults());
    }

    /**
     * Exports final patches to JSON.
     *
     * @param outputPath output JSON path
     * @param patches final phi patches
     * @param options export options
     * @return export summary
     * @throws IOException if writing fails
     */
    public static MosaicFinalPatchExportSummary export(
            Path outputPath,
            List<PhiPatch> patches,
            MosaicFinalPatchExportOptions options) throws IOException {

        if (outputPath == null) {
            throw new IllegalArgumentException("outputPath must not be null.");
        }

        if (patches == null) {
            patches = List.of();
        }

        if (options == null) {
            options = MosaicFinalPatchExportOptions.defaults();
        }

        double radius = options.sphereRadius();

        if (!Double.isFinite(radius) || radius <= 0.0) {
            radius = estimateRadius(patches);
        }

        if (!Double.isFinite(radius) || radius <= 0.0) {
            radius = 1.0;
        }

        Path parent = outputPath.toAbsolutePath().getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        double totalNormalizedArea = 0.0;
        double totalArea = 0.0;
        double totalPerimeter = 0.0;

        for (PhiPatch patch : patches) {
            if (patch == null) {
                continue;
            }

            totalNormalizedArea += patch.normalizedArea();
            totalArea += patch.area();
            totalPerimeter += perimeter(patch.boundary(), radius);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(
                outputPath,
                StandardCharsets.UTF_8)) {

            JsonOut out = new JsonOut(writer, options.pretty());

            out.beginObject();

            out.name("schema").value(SCHEMA).comma();
            out.name("exportedAt").value(Instant.now().toString()).comma();
            out.name("patchCount").value(patches.size()).comma();
            out.name("sphereRadius").value(radius).comma();
            out.name("areaUnits").value("radius^2").comma();
            out.name("perimeterUnits").value("radius").comma();

            out.name("totals").beginObject();
            out.name("normalizedArea").value(totalNormalizedArea).comma();
            out.name("area").value(totalArea).comma();
            out.name("perimeter").value(totalPerimeter);
            out.endObject().comma();

            out.name("patches").beginArray();

            for (int i = 0; i < patches.size(); i++) {
                PhiPatch patch = patches.get(i);

                if (i > 0) {
                    out.comma();
                }

                writePatch(out, patch, i, radius, options);
            }

            out.endArray();

            out.endObject();
            out.newline();
        }

        return new MosaicFinalPatchExportSummary(
                outputPath,
                patches.size(),
                totalNormalizedArea,
                totalArea,
                totalPerimeter,
                radius);
    }

    /**
     * Writes one final patch.
     *
     * @param out JSON output helper
     * @param patch patch
     * @param index patch index
     * @param radius sphere radius
     * @param options export options
     * @throws IOException if writing fails
     */
    private static void writePatch(
            JsonOut out,
            PhiPatch patch,
            int index,
            double radius,
            MosaicFinalPatchExportOptions options) throws IOException {

        out.beginObject();

        if (patch == null) {
            out.name("index").value(index).comma();
            out.name("nullPatch").value(true);
            out.endObject();
            return;
        }

        CellId cell = patch.parentCellId();
        String patchId = patchId(patch, index);

        out.name("id").value(patchId).comma();
        out.name("index").value(index).comma();

        out.name("parentCell").beginObject();
        out.name("nx").value(cell.nx()).comma();
        out.name("ny").value(cell.ny()).comma();
        out.name("nz").value(cell.nz());
        out.endObject().comma();

        out.name("ntheta").value(patch.ntheta()).comma();

        out.name("nphi");
        if (patch.isPolarAggregate()) {
            out.raw("null");
        } else {
            out.value(patch.nphi());
        }
        out.comma();

        out.name("nphiLabel").value(
                patch.isPolarAggregate() ? "aggregate" : Integer.toString(patch.nphi())).comma();

        out.name("polarAggregate").value(patch.isPolarAggregate()).comma();
        out.name("boundaryPointCount").value(patch.boundaryPointCount()).comma();

        out.name("area").value(patch.area()).comma();
        out.name("normalizedArea").value(patch.normalizedArea()).comma();

        double patchPerimeter = perimeter(patch.boundary(), radius);
        out.name("perimeter").value(patchPerimeter).comma();
        out.name("perimeterOverRadius").value(patchPerimeter / radius).comma();

        out.name("poleClassification").value(
                patch.parentPoleClassification() == null
                        ? "null"
                        : patch.parentPoleClassification().toString()).comma();

        out.name("boundary").beginArray();

        for (int i = 0; i < patch.boundary().size(); i++) {
            if (i > 0) {
                out.comma();
            }

            writeBoundaryPoint(out, patch.boundary().get(i), i, options);
        }

        out.endArray();

        if (options.includeBoundarySegments()) {
            out.comma();
            out.name("boundarySegments").beginArray();

            int n = patch.boundary().size();

            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    out.comma();
                }

                int j = (i + 1) % n;
                writeBoundarySegment(out, patch.boundary().get(i), patch.boundary().get(j), i, j, radius);
            }

            out.endArray();
        }

        out.endObject();
    }

    /**
     * Writes one boundary point.
     *
     * @param out JSON output helper
     * @param p boundary point
     * @param index point index
     * @param options export options
     * @throws IOException if writing fails
     */
    private static void writeBoundaryPoint(
            JsonOut out,
            Vec3 p,
            int index,
            MosaicFinalPatchExportOptions options) throws IOException {

        out.beginObject();

        out.name("i").value(index);

        if (options.includeCartesianBoundary()) {
            out.comma();
            out.name("cartesian").beginObject();
            out.name("x").value(p.x()).comma();
            out.name("y").value(p.y()).comma();
            out.name("z").value(p.z());
            out.endObject();
        }

        if (options.includeSphericalBoundary()) {
            SphericalPoint sp = spherical(p);

            out.comma();
            out.name("spherical").beginObject();
            out.name("r").value(sp.r()).comma();
            out.name("thetaRad").value(sp.theta()).comma();
            out.name("thetaDeg").value(sp.theta() * DEG).comma();
            out.name("phiRad").value(sp.phi()).comma();
            out.name("phiDeg").value(sp.phi() * DEG);
            out.endObject();
        }

        out.endObject();
    }

    /**
     * Writes one parameterized boundary segment.
     * <p>
     * Each segment is represented as a great-circle segment between two boundary
     * vertices. The natural parameter is {@code t} in {@code [0,1]}, with
     * spherical linear interpolation between the endpoints.
     * </p>
     *
     * @param out JSON output helper
     * @param a start point
     * @param b end point
     * @param i start index
     * @param j end index
     * @param radius sphere radius
     * @throws IOException if writing fails
     */
    private static void writeBoundarySegment(
            JsonOut out,
            Vec3 a,
            Vec3 b,
            int i,
            int j,
            double radius) throws IOException {

        double angle = angularSeparation(a, b);
        double length = radius * angle;

        out.beginObject();
        out.name("type").value("greatCircleSegment").comma();
        out.name("from").value(i).comma();
        out.name("to").value(j).comma();
        out.name("parameter").value("t in [0,1], spherical linear interpolation").comma();
        out.name("centralAngleRad").value(angle).comma();
        out.name("centralAngleDeg").value(angle * DEG).comma();
        out.name("length").value(length);
        out.endObject();
    }

    /**
     * Creates a stable-ish patch id for export.
     *
     * @param patch patch
     * @param index patch index
     * @return patch id
     */
    private static String patchId(PhiPatch patch, int index) {
        CellId c = patch.parentCellId();

        String phi = patch.isPolarAggregate()
                ? "agg"
                : Integer.toString(patch.nphi());

        return String.format(Locale.US,
                "patch-%05d-cell-%d-%d-%d-ntheta-%d-nphi-%s",
                index,
                c.nx(),
                c.ny(),
                c.nz(),
                patch.ntheta(),
                phi);
    }

    /**
     * Estimates the sphere radius from all boundary points.
     *
     * @param patches patches
     * @return estimated radius
     */
    private static double estimateRadius(List<PhiPatch> patches) {
        double sum = 0.0;
        int count = 0;

        for (PhiPatch patch : patches) {
            if (patch == null) {
                continue;
            }

            for (Vec3 p : patch.boundary()) {
                double r = p.norm();

                if (Double.isFinite(r) && r > 0.0) {
                    sum += r;
                    count++;
                }
            }
        }

        return count == 0 ? Double.NaN : sum / count;
    }

    /**
     * Computes the perimeter along great-circle segments between boundary
     * points.
     *
     * @param boundary boundary points
     * @param radius sphere radius
     * @return perimeter
     */
    private static double perimeter(List<Vec3> boundary, double radius) {
        if (boundary == null || boundary.size() < 2) {
            return 0.0;
        }

        double perimeter = 0.0;
        int n = boundary.size();

        for (int i = 0; i < n; i++) {
            Vec3 a = boundary.get(i);
            Vec3 b = boundary.get((i + 1) % n);

            perimeter += radius * angularSeparation(a, b);
        }

        return perimeter;
    }

    /**
     * Computes angular separation between two vectors.
     *
     * @param a first vector
     * @param b second vector
     * @return angular separation in radians
     */
    private static double angularSeparation(Vec3 a, Vec3 b) {
        double ra = a.norm();
        double rb = b.norm();

        if (ra <= 0.0 || rb <= 0.0) {
            return 0.0;
        }

        double dot = (a.x() * b.x() + a.y() * b.y() + a.z() * b.z()) / (ra * rb);
        dot = clamp(dot, -1.0, 1.0);

        return Math.acos(dot);
    }

    /**
     * Converts Cartesian point to spherical coordinates.
     *
     * @param p point
     * @return spherical point
     */
    private static SphericalPoint spherical(Vec3 p) {
        double r = p.norm();

        if (r <= 0.0) {
            return new SphericalPoint(0.0, 0.0, 0.0);
        }

        double theta = Math.acos(clamp(p.z() / r, -1.0, 1.0));
        double phi = Math.atan2(p.y(), p.x());

        return new SphericalPoint(r, theta, phi);
    }

    /**
     * Clamps a value.
     *
     * @param value value
     * @param lo lower bound
     * @param hi upper bound
     * @return clamped value
     */
    private static double clamp(double value, double lo, double hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    /**
     * Spherical coordinate tuple.
     *
     * @param r radius
     * @param theta polar angle
     * @param phi azimuth
     */
    private record SphericalPoint(double r, double theta, double phi) {
    }

    /**
     * Tiny JSON writer for this exporter.
     * <p>
     * This avoids adding a dependency just for this first export step.
     * </p>
     */
    private static final class JsonOut {

        /** Writer. */
        private final BufferedWriter writer;

        /** Whether to pretty-print. */
        private final boolean pretty;

        /** Current indentation level. */
        private int indent;

        /** Whether the most recent write was a structural open. */
        private boolean afterOpen;

        /**
         * Constructor.
         *
         * @param writer writer
         * @param pretty pretty-print flag
         */
        JsonOut(BufferedWriter writer, boolean pretty) {
            this.writer = writer;
            this.pretty = pretty;
        }

        /**
         * Begins an object.
         *
         * @return this writer
         * @throws IOException if writing fails
         */
        JsonOut beginObject() throws IOException {
            writer.write("{");
            indent++;
            afterOpen = true;
            return this;
        }

        /**
         * Ends an object.
         *
         * @return this writer
         * @throws IOException if writing fails
         */
        JsonOut endObject() throws IOException {
            indent--;
            newline();
            writeIndent();
            writer.write("}");
            afterOpen = false;
            return this;
        }

        /**
         * Begins an array.
         *
         * @return this writer
         * @throws IOException if writing fails
         */
        JsonOut beginArray() throws IOException {
            writer.write("[");
            indent++;
            afterOpen = true;
            return this;
        }

        /**
         * Ends an array.
         *
         * @return this writer
         * @throws IOException if writing fails
         */
        JsonOut endArray() throws IOException {
            indent--;
            newline();
            writeIndent();
            writer.write("]");
            afterOpen = false;
            return this;
        }

        /**
         * Writes a field name.
         *
         * @param name field name
         * @return this writer
         * @throws IOException if writing fails
         */
        JsonOut name(String name) throws IOException {
            newline();
            writeIndent();
            valueString(name);
            writer.write(pretty ? ": " : ":");
            afterOpen = false;
            return this;
        }

        /**
         * Writes a string value.
         *
         * @param value value
         * @return this writer
         * @throws IOException if writing fails
         */
        JsonOut value(String value) throws IOException {
            valueString(value);
            afterOpen = false;
            return this;
        }

        /**
         * Writes an integer value.
         *
         * @param value value
         * @return this writer
         * @throws IOException if writing fails
         */
        JsonOut value(int value) throws IOException {
            writer.write(Integer.toString(value));
            afterOpen = false;
            return this;
        }

        /**
         * Writes a double value.
         *
         * @param value value
         * @return this writer
         * @throws IOException if writing fails
         */
        JsonOut value(double value) throws IOException {
            if (Double.isFinite(value)) {
                writer.write(String.format(Locale.US, "%.17g", value));
            } else {
                writer.write("null");
            }

            afterOpen = false;
            return this;
        }

        /**
         * Writes a boolean value.
         *
         * @param value value
         * @return this writer
         * @throws IOException if writing fails
         */
        JsonOut value(boolean value) throws IOException {
            writer.write(Boolean.toString(value));
            afterOpen = false;
            return this;
        }

        /**
         * Writes raw JSON.
         *
         * @param raw raw text
         * @return this writer
         * @throws IOException if writing fails
         */
        JsonOut raw(String raw) throws IOException {
            writer.write(raw);
            afterOpen = false;
            return this;
        }

        /**
         * Writes a comma.
         *
         * @return this writer
         * @throws IOException if writing fails
         */
        JsonOut comma() throws IOException {
            writer.write(",");
            afterOpen = false;
            return this;
        }

        /**
         * Writes a newline if pretty-printing.
         *
         * @return this writer
         * @throws IOException if writing fails
         */
        JsonOut newline() throws IOException {
            if (pretty) {
                writer.newLine();
            }
            return this;
        }

        /**
         * Writes indentation if pretty-printing.
         *
         * @throws IOException if writing fails
         */
        private void writeIndent() throws IOException {
            if (!pretty) {
                return;
            }

            for (int i = 0; i < indent; i++) {
                writer.write("  ");
            }
        }

        /**
         * Writes a JSON string with escaping.
         *
         * @param value value
         * @throws IOException if writing fails
         */
        private void valueString(String value) throws IOException {
            if (value == null) {
                writer.write("null");
                return;
            }

            writer.write("\"");

            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);

                switch (ch) {
                case '"' -> writer.write("\\\"");
                case '\\' -> writer.write("\\\\");
                case '\b' -> writer.write("\\b");
                case '\f' -> writer.write("\\f");
                case '\n' -> writer.write("\\n");
                case '\r' -> writer.write("\\r");
                case '\t' -> writer.write("\\t");
                default -> {
                    if (ch < 0x20) {
                        writer.write(String.format(Locale.US, "\\u%04x", (int) ch));
                    } else {
                        writer.write(ch);
                    }
                }
                }
            }

            writer.write("\"");
        }
    }
}