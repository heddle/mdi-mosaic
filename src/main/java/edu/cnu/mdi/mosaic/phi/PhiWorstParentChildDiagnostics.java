package edu.cnu.mdi.mosaic.phi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import edu.cnu.mdi.log.Log;
import edu.cnu.mdi.mosaic.geom.Vec3;
import edu.cnu.mdi.mosaic.grid.Grid1D;
import edu.cnu.mdi.mosaic.theta.ThetaPatch;

/**
 * Diagnostic dump for the phi children belonging to the worst phi-parent area
 * errors.
 * <p>
 * This is intentionally a logging-only diagnostic. It does not affect the
 * algorithm result. Its purpose is to show whether phi area loss is
 * concentrated in a particular child, a seam/end phi bin, or spread across many
 * children.
 * </p>
 */
public final class PhiWorstParentChildDiagnostics {

    /** Degrees per radian. */
    private static final double DEG = 180.0 / Math.PI;

    /**
     * Hidden constructor.
     */
    private PhiWorstParentChildDiagnostics() {
    }

    /**
     * Logs child details for the worst phi-parent area errors.
     *
     * @param diagnostics phi parent diagnostics
     * @param thetaPatches theta parent patches
     * @param phiPatches final phi child patches
     * @param phiGrid phi grid
     * @param maxParents maximum number of worst parents to dump
     */
    public static void log(
            PhiParentAreaDiagnostics diagnostics,
            List<ThetaPatch> thetaPatches,
            List<PhiPatch> phiPatches,
            Grid1D phiGrid,
            int maxParents) {

        if (diagnostics == null || diagnostics.parentErrors().isEmpty()) {
            return;
        }

        if (thetaPatches == null) {
            thetaPatches = List.of();
        }

        if (phiPatches == null) {
            phiPatches = List.of();
        }

        Map<ThetaPatchKey, ThetaPatch> thetaByKey = new HashMap<>();

        for (ThetaPatch thetaPatch : thetaPatches) {
            if (thetaPatch == null) {
                continue;
            }

            ThetaPatchKey key = new ThetaPatchKey(
                    thetaPatch.parentCellId(),
                    thetaPatch.ntheta());

            thetaByKey.putIfAbsent(key, thetaPatch);
        }

        Map<ThetaPatchKey, List<PhiPatch>> childrenByKey = new HashMap<>();

        for (PhiPatch phiPatch : phiPatches) {
            if (phiPatch == null) {
                continue;
            }

            ThetaPatchKey key = new ThetaPatchKey(
                    phiPatch.parentCellId(),
                    phiPatch.ntheta());

            childrenByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(phiPatch);
        }

        int nparent = Math.min(Math.max(0, maxParents), diagnostics.parentErrors().size());

        if (nparent <= 0) {
            return;
        }

        Log.getInstance().info("Worst phi parent child diagnostics:");

        for (PhiParentAreaError error : diagnostics.worstErrors(nparent)) {
            ThetaPatchKey key = error.key();
            ThetaPatch parent = thetaByKey.get(key);
            List<PhiPatch> children = new ArrayList<>(
                    childrenByKey.getOrDefault(key, List.of()));

            children.sort((a, b) -> Integer.compare(a.nphi(), b.nphi()));

            Log.getInstance().info("  Parent " + error.summaryLine());

            if (parent == null) {
                Log.getInstance().warning("    parent theta patch not found for key " + key);
            } else {
                GeometrySummary parentSummary = summarize(parent.boundary(), Double.NaN);

                Log.getInstance().info(String.format(
                        "    parent boundary: pts=%d theta=[%.6f, %.6f] deg phi=[%.6f, %.6f] deg z/r=[%.9f, %.9f]",
                        parent.boundaryPointCount(),
                        parentSummary.minThetaDeg,
                        parentSummary.maxThetaDeg,
                        parentSummary.minPhiDeg,
                        parentSummary.maxPhiDeg,
                        parentSummary.minZOverR,
                        parentSummary.maxZOverR));
            }

            double childArea = 0.0;
            int childPointCount = 0;

            TreeSet<Integer> presentBins = new TreeSet<>();

            for (PhiPatch child : children) {
                childArea += child.normalizedArea();
                childPointCount += child.boundaryPointCount();

                if (child.nphi() >= 0) {
                    presentBins.add(child.nphi());
                }
            }

            Log.getInstance().info(String.format(
                    "    children: count=%d area=%.12e delta=%+.12e totalBoundaryPts=%d bins=%s missingBetween=%s",
                    children.size(),
                    childArea,
                    childArea - error.parentNormalizedArea(),
                    childPointCount,
                    presentBins,
                    missingBinsBetweenExtremes(presentBins)));

            double cumulative = 0.0;

            for (PhiPatch child : children) {
                cumulative += child.normalizedArea();

                double phiCenter = childPhiCenter(child, phiGrid);
                GeometrySummary childSummary = summarize(child.boundary(), phiCenter);

                String bandText = childBandText(child, phiGrid);
                String touchText = childTouchText(child, phiGrid);

                double fracOfParent = error.parentNormalizedArea() == 0.0
                        ? Double.NaN
                        : child.normalizedArea() / error.parentNormalizedArea();

                double cumulativeFracOfParent = error.parentNormalizedArea() == 0.0
                        ? Double.NaN
                        : cumulative / error.parentNormalizedArea();

                Log.getInstance().info(String.format(
                        "      child nphi=%3d A=%.12e fracParent=%.6f cumFrac=%.6f pts=%4d %s theta=[%.6f, %.6f] deg phi=[%.6f, %.6f] deg z/r=[%.9f, %.9f] %s",
                        child.nphi(),
                        child.normalizedArea(),
                        fracOfParent,
                        cumulativeFracOfParent,
                        child.boundaryPointCount(),
                        bandText,
                        childSummary.minThetaDeg,
                        childSummary.maxThetaDeg,
                        childSummary.minPhiDeg,
                        childSummary.maxPhiDeg,
                        childSummary.minZOverR,
                        childSummary.maxZOverR,
                        touchText));
            }
        }
    }

    /**
     * Gets text describing missing integer bins between the smallest and largest
     * present bins.
     *
     * @param presentBins present bins
     * @return missing-bin text
     */
    private static String missingBinsBetweenExtremes(TreeSet<Integer> presentBins) {
        if (presentBins == null || presentBins.size() < 2) {
            return "[]";
        }

        ArrayList<Integer> missing = new ArrayList<>();

        int min = presentBins.first();
        int max = presentBins.last();

        for (int i = min; i <= max; i++) {
            if (!presentBins.contains(i)) {
                missing.add(i);
            }
        }

        return missing.toString();
    }

    /**
     * Gets the nominal center of a child's phi band.
     *
     * @param child child patch
     * @param phiGrid phi grid
     * @return center angle, or NaN if unavailable
     */
    private static double childPhiCenter(PhiPatch child, Grid1D phiGrid) {
        if (child == null || phiGrid == null || child.nphi() < 0
                || child.nphi() + 1 >= phiGrid.numPoints()) {
            return Double.NaN;
        }

        double phi0 = phiGrid.valueAt(child.nphi());
        double phi1 = phiGrid.valueAt(child.nphi() + 1);

        return normalizeAngle(phi0 + 0.5 * positiveDelta(phi0, phi1));
    }

    /**
     * Gets readable band text for a child.
     *
     * @param child child patch
     * @param phiGrid phi grid
     * @return band text
     */
    private static String childBandText(PhiPatch child, Grid1D phiGrid) {
        if (child == null || phiGrid == null || child.nphi() < 0
                || child.nphi() + 1 >= phiGrid.numPoints()) {
            return "band=[aggregate]";
        }

        double phi0 = phiGrid.valueAt(child.nphi());
        double phi1 = phiGrid.valueAt(child.nphi() + 1);

        return String.format("band=[%.6f, %.6f] deg", phi0 * DEG, phi1 * DEG);
    }

    /**
     * Counts vertices lying close to the left and right phi-band meridians.
     *
     * @param child child patch
     * @param phiGrid phi grid
     * @return touch summary
     */
    private static String childTouchText(PhiPatch child, Grid1D phiGrid) {
        if (child == null || phiGrid == null || child.nphi() < 0
                || child.nphi() + 1 >= phiGrid.numPoints()) {
            return "touch=[aggregate]";
        }

        double phi0 = phiGrid.valueAt(child.nphi());
        double phi1 = phiGrid.valueAt(child.nphi() + 1);

        int left = 0;
        int right = 0;

        /*
         * This is intentionally loose. It is a diagnostic count, not geometry.
         */
        double tol = 1.0e-7;

        for (Vec3 p : child.boundary()) {
            double phi = Math.atan2(p.y(), p.x());

            if (Math.abs(wrapPi(phi - phi0)) <= tol) {
                left++;
            }

            if (Math.abs(wrapPi(phi - phi1)) <= tol) {
                right++;
            }
        }

        return String.format("touch=[left=%d right=%d]", left, right);
    }

    /**
     * Summarizes basic spherical geometry of a boundary.
     *
     * @param boundary boundary points
     * @param preferredPhiCenter preferred center for unwrapping phi; if NaN, a
     *        center is estimated from the boundary
     * @return summary
     */
    private static GeometrySummary summarize(List<Vec3> boundary, double preferredPhiCenter) {
        if (boundary == null || boundary.isEmpty()) {
            return GeometrySummary.empty();
        }

        double phiCenter = preferredPhiCenter;

        if (!Double.isFinite(phiCenter)) {
            phiCenter = estimatePhiCenter(boundary);
        }

        double minTheta = Double.POSITIVE_INFINITY;
        double maxTheta = Double.NEGATIVE_INFINITY;
        double minPhi = Double.POSITIVE_INFINITY;
        double maxPhi = Double.NEGATIVE_INFINITY;
        double minZOverR = Double.POSITIVE_INFINITY;
        double maxZOverR = Double.NEGATIVE_INFINITY;

        for (Vec3 p : boundary) {
            if (p == null) {
                continue;
            }

            double r = p.norm();

            if (!Double.isFinite(r) || r <= 0.0) {
                continue;
            }

            double zOverR = clamp(p.z() / r, -1.0, 1.0);
            double theta = Math.acos(zOverR);

            double phi = Math.atan2(p.y(), p.x());
            double phiUnwrapped = phiCenter + wrapPi(phi - phiCenter);

            minTheta = Math.min(minTheta, theta);
            maxTheta = Math.max(maxTheta, theta);
            minPhi = Math.min(minPhi, phiUnwrapped);
            maxPhi = Math.max(maxPhi, phiUnwrapped);
            minZOverR = Math.min(minZOverR, zOverR);
            maxZOverR = Math.max(maxZOverR, zOverR);
        }

        if (!Double.isFinite(minTheta)) {
            return GeometrySummary.empty();
        }

        return new GeometrySummary(
                minTheta * DEG,
                maxTheta * DEG,
                minPhi * DEG,
                maxPhi * DEG,
                minZOverR,
                maxZOverR);
    }

    /**
     * Estimates a reasonable phi center from the average xy direction.
     *
     * @param boundary boundary points
     * @return center phi
     */
    private static double estimatePhiCenter(List<Vec3> boundary) {
        double sx = 0.0;
        double sy = 0.0;

        for (Vec3 p : boundary) {
            if (p == null) {
                continue;
            }

            sx += p.x();
            sy += p.y();
        }

        if (sx == 0.0 && sy == 0.0) {
            return 0.0;
        }

        return Math.atan2(sy, sx);
    }

    /**
     * Positive angular delta from a0 to a1.
     *
     * @param a0 start angle
     * @param a1 end angle
     * @return positive delta
     */
    private static double positiveDelta(double a0, double a1) {
        double d = a1 - a0;

        while (d < 0.0) {
            d += 2.0 * Math.PI;
        }

        while (d >= 2.0 * Math.PI) {
            d -= 2.0 * Math.PI;
        }

        return d;
    }

    /**
     * Normalizes angle to [-pi, pi).
     *
     * @param angle angle
     * @return normalized angle
     */
    private static double normalizeAngle(double angle) {
        return wrapPi(angle);
    }

    /**
     * Wraps angle to [-pi, pi).
     *
     * @param angle angle
     * @return wrapped angle
     */
    private static double wrapPi(double angle) {
        double a = angle;

        while (a < -Math.PI) {
            a += 2.0 * Math.PI;
        }

        while (a >= Math.PI) {
            a -= 2.0 * Math.PI;
        }

        return a;
    }

    /**
     * Clamps a value.
     *
     * @param value value
     * @param lo low value
     * @param hi high value
     * @return clamped value
     */
    private static double clamp(double value, double lo, double hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    /**
     * Geometry summary.
     *
     * @param minThetaDeg minimum theta in degrees
     * @param maxThetaDeg maximum theta in degrees
     * @param minPhiDeg minimum unwrapped phi in degrees
     * @param maxPhiDeg maximum unwrapped phi in degrees
     * @param minZOverR minimum z/r
     * @param maxZOverR maximum z/r
     */
    private record GeometrySummary(
            double minThetaDeg,
            double maxThetaDeg,
            double minPhiDeg,
            double maxPhiDeg,
            double minZOverR,
            double maxZOverR) {

        /**
         * Empty summary.
         *
         * @return empty summary
         */
        static GeometrySummary empty() {
            return new GeometrySummary(
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN);
        }
    }
}