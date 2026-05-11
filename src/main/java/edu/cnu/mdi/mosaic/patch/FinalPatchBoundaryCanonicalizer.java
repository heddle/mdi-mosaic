package edu.cnu.mdi.mosaic.patch;

import java.util.ArrayList;
import java.util.List;

import edu.cnu.mdi.mosaic.geom.Vec3;

/**
 * Utility methods for cleaning final patch boundaries before display or export.
 * <p>
 * This is intentionally conservative. It does not change patch topology, merge
 * patches, or invent new boundary geometry. It only removes local artifacts that
 * are clearly non-geometric:
 * </p>
 *
 * <ul>
 * <li>consecutive duplicate points,</li>
 * <li>an explicit duplicate closing point,</li>
 * <li>zero-area needle spikes of the form A-B-A.</li>
 * </ul>
 *
 * <p>
 * This is appropriate as a final display/export cleanup pass, especially for
 * polar-derived patches whose intermediate theta construction may contain
 * artificial fan edges.
 * </p>
 */
public final class FinalPatchBoundaryCanonicalizer {

    /** Squared distance tolerance for treating two points as identical. */
    private static final double DUP_TOL2 = 1.0e-18;

    /** Maximum cleanup passes for spike removal. */
    private static final int MAX_PASSES = 20;

    /**
     * Hidden constructor.
     */
    private FinalPatchBoundaryCanonicalizer() {
    }

    /**
     * Canonicalizes a sampled final patch boundary.
     *
     * @param boundary input sampled boundary
     * @return cleaned boundary; never null
     */
    public static List<Vec3> canonicalize(List<Vec3> boundary) {
        if (boundary == null || boundary.isEmpty()) {
            return List.of();
        }

        List<Vec3> cleaned = removeConsecutiveDuplicates(boundary);
        cleaned = removeClosingDuplicate(cleaned);
        cleaned = removeNeedleSpikes(cleaned);

        if (cleaned.size() < 3) {
            return List.of();
        }

        return cleaned;
    }

    /**
     * Computes an approximate spherical perimeter for a sampled closed boundary.
     * <p>
     * The perimeter is computed as the sum of great-circle arc lengths between
     * consecutive sampled boundary points, including the closing segment from the
     * final point back to the first point.
     * </p>
     *
     * @param boundary sampled closed boundary, without requiring a duplicate final
     *        point
     * @param radius sphere radius
     * @return approximate spherical perimeter in the same length units as radius
     */
    public static double sphericalPerimeter(List<Vec3> boundary, double radius) {
        List<Vec3> b = canonicalize(boundary);

        if (b.size() < 3 || !Double.isFinite(radius) || radius <= 0.0) {
            return 0.0;
        }

        double sum = 0.0;

        for (int i = 0; i < b.size(); i++) {
            Vec3 a = b.get(i);
            Vec3 c = b.get((i + 1) % b.size());

            double na = a.norm();
            double nc = c.norm();

            if (na <= 0.0 || nc <= 0.0
                    || !Double.isFinite(na)
                    || !Double.isFinite(nc)) {
                continue;
            }

            double dot = (a.x() * c.x() + a.y() * c.y() + a.z() * c.z())
                    / (na * nc);

            dot = clamp(dot, -1.0, 1.0);

            sum += radius * Math.acos(dot);
        }

        return sum;
    }

    /**
     * Removes consecutive duplicate points.
     *
     * @param input input boundary
     * @return boundary with consecutive duplicates removed
     */
    private static List<Vec3> removeConsecutiveDuplicates(List<Vec3> input) {
        ArrayList<Vec3> output = new ArrayList<>();

        for (Vec3 p : input) {
            if (p == null) {
                continue;
            }

            if (output.isEmpty() || !near(output.get(output.size() - 1), p)) {
                output.add(p);
            }
        }

        return output;
    }

    /**
     * Removes an explicit final point duplicating the first point.
     *
     * @param input input boundary
     * @return boundary without duplicate closing point
     */
    private static List<Vec3> removeClosingDuplicate(List<Vec3> input) {
        if (input == null || input.size() < 2) {
            return List.of();
        }

        ArrayList<Vec3> output = new ArrayList<>(input);

        while (output.size() > 1 && near(output.get(0), output.get(output.size() - 1))) {
            output.remove(output.size() - 1);
        }

        return output;
    }

    /**
     * Removes repeated A-B-A needle spikes.
     * <p>
     * These are not valid exterior boundary features. They represent a walk down
     * an edge and immediately back along the same edge.
     * </p>
     *
     * @param input input boundary
     * @return cleaned boundary
     */
    private static List<Vec3> removeNeedleSpikes(List<Vec3> input) {
        if (input == null || input.size() < 3) {
            return List.of();
        }

        ArrayList<Vec3> work = new ArrayList<>(input);

        boolean changed = true;
        int pass = 0;

        while (changed && pass < MAX_PASSES && work.size() >= 3) {
            changed = false;
            pass++;

            ArrayList<Vec3> next = new ArrayList<>();

            int n = work.size();

            for (int i = 0; i < n; i++) {
                Vec3 prev = work.get((i - 1 + n) % n);
                Vec3 curr = work.get(i);
                Vec3 nextPoint = work.get((i + 1) % n);

                /*
                 * If prev and next are the same point, curr is the tip of a
                 * zero-area spike: prev -> curr -> prev. Drop curr.
                 */
                if (near(prev, nextPoint)) {
                    changed = true;
                    continue;
                }

                next.add(curr);
            }

            work = next;
            work = new ArrayList<>(removeClosingDuplicate(removeConsecutiveDuplicates(work)));
        }

        if (work.size() < 3) {
            return List.of();
        }

        return work;
    }

    /**
     * Tests whether two points are approximately identical.
     *
     * @param a first point
     * @param b second point
     * @return true if near
     */
    private static boolean near(Vec3 a, Vec3 b) {
        if (a == null || b == null) {
            return false;
        }

        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();

        return dx * dx + dy * dy + dz * dz <= DUP_TOL2;
    }

    /**
     * Clamps a value.
     *
     * @param value value
     * @param lo low
     * @param hi high
     * @return clamped value
     */
    private static double clamp(double value, double lo, double hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}