package edu.cnu.mdi.mosaic.phi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.cnu.mdi.mosaic.patch.PoleClassification;
import edu.cnu.mdi.mosaic.theta.ThetaPatch;

/**
 * Builds per-theta-parent area conservation diagnostics for phi splicing.
 */
public final class PhiParentAreaDiagnosticBuilder {

    /**
     * Hidden constructor.
     */
    private PhiParentAreaDiagnosticBuilder() {
    }

    /**
     * Builds diagnostics comparing each theta-parent area to the sum of its phi
     * children.
     *
     * @param thetaPatches theta parents
     * @param phiPatches phi children
     * @return diagnostics
     */
    public static PhiParentAreaDiagnostics build(
            List<ThetaPatch> thetaPatches,
            List<PhiPatch> phiPatches) {

        if (thetaPatches == null) {
            thetaPatches = List.of();
        }

        if (phiPatches == null) {
            phiPatches = List.of();
        }

        Map<ThetaPatchKey, Double> parentArea = new HashMap<>();
        Map<ThetaPatchKey, PoleClassification> poleByParent = new HashMap<>();

        for (ThetaPatch patch : thetaPatches) {
            if (patch == null) {
                continue;
            }

            if (patch.parentPoleClassification() != null
                    && patch.parentPoleClassification().hasPoleInvolvement()) {
                continue;
            }

            ThetaPatchKey key = new ThetaPatchKey(
                    patch.parentCellId(),
                    patch.ntheta());

            parentArea.merge(key, patch.normalizedArea(), Double::sum);
            poleByParent.putIfAbsent(key, patch.parentPoleClassification());
        }
        
        
        Map<ThetaPatchKey, Double> phiAreaByParent = new HashMap<>();
        Map<ThetaPatchKey, Integer> childCountByParent = new HashMap<>();

        for (PhiPatch patch : phiPatches) {
            if (patch == null) {
                continue;
            }

            ThetaPatchKey key = new ThetaPatchKey(
                    patch.parentCellId(),
                    patch.ntheta());

            phiAreaByParent.merge(key, patch.normalizedArea(), Double::sum);
            childCountByParent.merge(key, 1, Integer::sum);
        }

        ArrayList<PhiParentAreaError> errors = new ArrayList<>();

        double totalParent = 0.0;
        double totalPhi = 0.0;
        double maxAbs = 0.0;
        double sumSq = 0.0;
        int count = 0;

        for (Map.Entry<ThetaPatchKey, Double> entry : parentArea.entrySet()) {
            ThetaPatchKey key = entry.getKey();

            double pArea = entry.getValue();
            double fArea = phiAreaByParent.getOrDefault(key, 0.0);
            double delta = fArea - pArea;
            int childCount = childCountByParent.getOrDefault(key, 0);

            PhiParentAreaError error = new PhiParentAreaError(
                    key,
                    pArea,
                    fArea,
                    delta,
                    childCount,
                    poleByParent.get(key));

            errors.add(error);

            totalParent += pArea;
            totalPhi += fArea;
            maxAbs = Math.max(maxAbs, Math.abs(delta));
            sumSq += delta * delta;
            count++;
        }

        double totalDelta = totalPhi - totalParent;
        double rms = count == 0 ? 0.0 : Math.sqrt(sumSq / count);

        return new PhiParentAreaDiagnostics(
                errors,
                totalParent,
                totalPhi,
                totalDelta,
                maxAbs,
                rms);
    }
}