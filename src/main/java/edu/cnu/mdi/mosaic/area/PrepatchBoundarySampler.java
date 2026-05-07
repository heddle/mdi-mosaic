package edu.cnu.mdi.mosaic.area;

import java.util.ArrayList;
import java.util.List;

import edu.cnu.mdi.mosaic.geom.Vec3;
import edu.cnu.mdi.mosaic.patch.GeneralCurve;
import edu.cnu.mdi.mosaic.patch.Prepatch;

/**
 * Samples ordinary prepatch boundaries as ordered point loops on the sphere.
 * <p>
 * A {@link Prepatch} stores boundary curves, but the curve list should not be
 * assumed to be cyclically ordered. This class chains curves by matching end
 * points, reversing curve direction when needed, and then samples the ordered
 * curve loop.
 * </p>
 */
public final class PrepatchBoundarySampler {

    /** Endpoint matching tolerance in physical length units. */
    private static final double ENDPOINT_TOL = 1.0e-8;

    /**
     * Hidden constructor for utility class.
     */
    private PrepatchBoundarySampler() {
    }

    /**
     * Samples a prepatch boundary as one ordered point loop.
     *
     * @param prepatch prepatch to sample
     * @param samplesPerCurve number of samples per GENERAL curve
     * @return sampled ordered boundary, or empty list if ordering fails
     */
    public static List<Vec3> sampleBoundary(Prepatch prepatch, int samplesPerCurve) {
        if (prepatch == null || prepatch.curves().isEmpty()) {
            return List.of();
        }

        List<OrientedCurve> ordered = orderCurves(prepatch.curves());

        if (ordered.isEmpty()) {
            return List.of();
        }

        int n = Math.max(2, samplesPerCurve);
        ArrayList<Vec3> boundary = new ArrayList<>(ordered.size() * n);

        boolean firstCurve = true;

        for (OrientedCurve oriented : ordered) {
            List<Vec3> samples = oriented.curve().sample(n);

            if (oriented.reversed()) {
                samples = reversed(samples);
            }

            for (int i = 0; i < samples.size(); i++) {
                /*
                 * Skip the first sample after the first curve because it should
                 * duplicate the previous curve's final point.
                 */
                if (!firstCurve && i == 0) {
                    continue;
                }

                Vec3 p = samples.get(i);

                if (boundary.isEmpty() || !near(boundary.get(boundary.size() - 1), p)) {
                    boundary.add(p);
                }
            }

            firstCurve = false;
        }

        /*
         * Remove explicit closing duplicate. The polygon area code assumes an
         * implicit close from last vertex to first vertex.
         */
        if (boundary.size() > 1 && near(boundary.get(0), boundary.get(boundary.size() - 1))) {
            boundary.remove(boundary.size() - 1);
        }

        if (boundary.size() < 3) {
            return List.of();
        }

        return boundary;
    }

    /**
     * Orders the curves into a closed chain.
     *
     * @param curves input curves
     * @return ordered oriented curves, or empty list if ordering fails
     */
    private static List<OrientedCurve> orderCurves(List<GeneralCurve> curves) {
        if (curves == null || curves.isEmpty()) {
            return List.of();
        }

        ArrayList<GeneralCurve> remaining = new ArrayList<>(curves);
        ArrayList<OrientedCurve> ordered = new ArrayList<>();

        GeneralCurve first = remaining.remove(0);
        ordered.add(new OrientedCurve(first, false));

        Vec3 currentEnd = first.getEnd();

        while (!remaining.isEmpty()) {
            int matchIndex = -1;
            boolean reverse = false;

            for (int i = 0; i < remaining.size(); i++) {
                GeneralCurve candidate = remaining.get(i);

                if (near(currentEnd, candidate.getStart())) {
                    matchIndex = i;
                    reverse = false;
                    break;
                }

                if (near(currentEnd, candidate.getEnd())) {
                    matchIndex = i;
                    reverse = true;
                    break;
                }
            }

            if (matchIndex < 0) {
                return List.of();
            }

            GeneralCurve match = remaining.remove(matchIndex);
            ordered.add(new OrientedCurve(match, reverse));
            currentEnd = reverse ? match.getStart() : match.getEnd();
        }

        Vec3 loopStart = ordered.get(0).reversed()
                ? ordered.get(0).curve().getEnd()
                : ordered.get(0).curve().getStart();

        if (!near(currentEnd, loopStart)) {
            return List.of();
        }

        return ordered;
    }

    /**
     * Returns a reversed copy of a list.
     *
     * @param input input list
     * @return reversed list
     */
    private static List<Vec3> reversed(List<Vec3> input) {
        ArrayList<Vec3> reversed = new ArrayList<>(input.size());

        for (int i = input.size() - 1; i >= 0; i--) {
            reversed.add(input.get(i));
        }

        return reversed;
    }

    /**
     * Checks whether two points are near each other.
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

        return dx * dx + dy * dy + dz * dz <= ENDPOINT_TOL * ENDPOINT_TOL;
    }

    /**
     * A curve with an orientation flag.
     *
     * @param curve curve
     * @param reversed whether the curve should be traversed in reverse
     */
    private record OrientedCurve(GeneralCurve curve, boolean reversed) {
    }
}