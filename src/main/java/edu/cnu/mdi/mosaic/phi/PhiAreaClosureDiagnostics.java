package edu.cnu.mdi.mosaic.phi;

import java.util.List;

import edu.cnu.mdi.mosaic.theta.ThetaPatch;

/**
 * Lightweight aggregate area-closure diagnostics for final phi patches.
 * <p>
 * This diagnostic is intentionally simpler than the parent-by-parent diagnostic
 * builder. It reports aggregate closure for:
 * </p>
 *
 * <ul>
 * <li>all theta patches,</li>
 * <li>non-polar theta patches, and</li>
 * <li>polar-derived theta patches.</li>
 * </ul>
 *
 * <p>
 * It is useful after the ordinary and polar phi-splice results have been
 * merged, because some older parent diagnostics may intentionally or
 * accidentally exclude polar-derived parents.
 * </p>
 *
 * @param thetaParents total theta parents considered
 * @param nonPolarThetaParents non-polar theta parents
 * @param polarThetaParents polar-derived theta parents
 * @param phiPatches total phi patches considered
 * @param nonPolarPhiPatches non-polar phi patches
 * @param polarPhiPatches polar-derived phi patches
 * @param parentNormalizedArea total theta parent normalized area
 * @param phiNormalizedArea total phi normalized area
 * @param nonPolarParentNormalizedArea non-polar theta parent normalized area
 * @param nonPolarPhiNormalizedArea non-polar phi normalized area
 * @param polarParentNormalizedArea polar theta parent normalized area
 * @param polarPhiNormalizedArea polar phi normalized area
 */
public record PhiAreaClosureDiagnostics(
        int thetaParents,
        int nonPolarThetaParents,
        int polarThetaParents,
        int phiPatches,
        int nonPolarPhiPatches,
        int polarPhiPatches,
        double parentNormalizedArea,
        double phiNormalizedArea,
        double nonPolarParentNormalizedArea,
        double nonPolarPhiNormalizedArea,
        double polarParentNormalizedArea,
        double polarPhiNormalizedArea) {

    /**
     * Builds aggregate phi area-closure diagnostics.
     *
     * @param thetaPatches theta patches
     * @param phiPatches final phi patches
     * @return diagnostics
     */
    public static PhiAreaClosureDiagnostics build(
            List<ThetaPatch> thetaPatches,
            List<PhiPatch> phiPatches) {

        int thetaParentCount = 0;
        int nonPolarThetaParentCount = 0;
        int polarThetaParentCount = 0;

        double parentArea = 0.0;
        double nonPolarParentArea = 0.0;
        double polarParentArea = 0.0;

        if (thetaPatches != null) {
            for (ThetaPatch thetaPatch : thetaPatches) {
                if (thetaPatch == null) {
                    continue;
                }

                thetaParentCount++;
                parentArea += thetaPatch.normalizedArea();

                if (isPolar(thetaPatch)) {
                    polarThetaParentCount++;
                    polarParentArea += thetaPatch.normalizedArea();
                } else {
                    nonPolarThetaParentCount++;
                    nonPolarParentArea += thetaPatch.normalizedArea();
                }
            }
        }

        int phiPatchCount = 0;
        int nonPolarPhiPatchCount = 0;
        int polarPhiPatchCount = 0;

        double phiArea = 0.0;
        double nonPolarPhiArea = 0.0;
        double polarPhiArea = 0.0;

        if (phiPatches != null) {
            for (PhiPatch phiPatch : phiPatches) {
                if (phiPatch == null) {
                    continue;
                }

                phiPatchCount++;
                phiArea += phiPatch.normalizedArea();

                if (isPolar(phiPatch)) {
                    polarPhiPatchCount++;
                    polarPhiArea += phiPatch.normalizedArea();
                } else {
                    nonPolarPhiPatchCount++;
                    nonPolarPhiArea += phiPatch.normalizedArea();
                }
            }
        }

        return new PhiAreaClosureDiagnostics(
                thetaParentCount,
                nonPolarThetaParentCount,
                polarThetaParentCount,
                phiPatchCount,
                nonPolarPhiPatchCount,
                polarPhiPatchCount,
                parentArea,
                phiArea,
                nonPolarParentArea,
                nonPolarPhiArea,
                polarParentArea,
                polarPhiArea);
    }

    /**
     * Gets full normalized area delta.
     *
     * @return phi minus parent normalized area
     */
    public double normalizedAreaDelta() {
        return phiNormalizedArea - parentNormalizedArea;
    }

    /**
     * Gets non-polar normalized area delta.
     *
     * @return non-polar phi minus non-polar parent normalized area
     */
    public double nonPolarNormalizedAreaDelta() {
        return nonPolarPhiNormalizedArea - nonPolarParentNormalizedArea;
    }

    /**
     * Gets polar normalized area delta.
     *
     * @return polar phi minus polar parent normalized area
     */
    public double polarNormalizedAreaDelta() {
        return polarPhiNormalizedArea - polarParentNormalizedArea;
    }

    /**
     * Creates a multi-line summary.
     *
     * @return summary
     */
    public String summary() {
        StringBuilder sb = new StringBuilder();

        sb.append("Phi aggregate area closure diagnostics:\n");

        sb.append(String.format(
                "  theta parents: %d  non-polar: %d  polar: %d%n",
                thetaParents,
                nonPolarThetaParents,
                polarThetaParents));

        sb.append(String.format(
                "  phi patches:   %d  non-polar: %d  polar: %d%n",
                phiPatches,
                nonPolarPhiPatches,
                polarPhiPatches));

        sb.append(String.format(
                "  full parent A_norm:     %.17g%n",
                parentNormalizedArea));

        sb.append(String.format(
                "  full phi    A_norm:     %.17g%n",
                phiNormalizedArea));

        sb.append(String.format(
                "  full delta  A_norm:     %.17g%n",
                normalizedAreaDelta()));

        sb.append(String.format(
                "  non-polar parent A_norm: %.17g%n",
                nonPolarParentNormalizedArea));

        sb.append(String.format(
                "  non-polar phi    A_norm: %.17g%n",
                nonPolarPhiNormalizedArea));

        sb.append(String.format(
                "  non-polar delta  A_norm: %.17g%n",
                nonPolarNormalizedAreaDelta()));

        sb.append(String.format(
                "  polar parent A_norm:     %.17g%n",
                polarParentNormalizedArea));

        sb.append(String.format(
                "  polar phi    A_norm:     %.17g%n",
                polarPhiNormalizedArea));

        sb.append(String.format(
                "  polar delta  A_norm:     %.17g",
                polarNormalizedAreaDelta()));

        return sb.toString();
    }

    /**
     * Checks whether a theta patch is polar-derived.
     *
     * @param thetaPatch theta patch
     * @return true if polar-derived
     */
    private static boolean isPolar(ThetaPatch thetaPatch) {
        return thetaPatch.parentPoleClassification() != null
                && thetaPatch.parentPoleClassification().hasPoleInvolvement();
    }

    /**
     * Checks whether a phi patch is polar-derived.
     *
     * @param phiPatch phi patch
     * @return true if polar-derived
     */
    private static boolean isPolar(PhiPatch phiPatch) {
        return phiPatch.parentPoleClassification() != null
                && phiPatch.parentPoleClassification().hasPoleInvolvement();
    }
}