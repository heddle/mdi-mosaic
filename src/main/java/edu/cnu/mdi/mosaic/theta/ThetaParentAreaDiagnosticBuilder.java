package edu.cnu.mdi.mosaic.theta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.cnu.mdi.mosaic.cell.CellId;
import edu.cnu.mdi.mosaic.diagnostic.PrepatchDiagnostic;
import edu.cnu.mdi.mosaic.diagnostic.PrepatchDiagnosticSummary;
import edu.cnu.mdi.mosaic.patch.PoleClassification;

/**
 * Builds per-parent area conservation diagnostics for theta splicing.
 */
public final class ThetaParentAreaDiagnosticBuilder {

    /**
     * Hidden constructor.
     */
    private ThetaParentAreaDiagnosticBuilder() {
    }

    /**
     * Builds per-parent diagnostics by comparing each parent prepatch area to the
     * sum of its theta children.
     *
     * @param prepatchDiagnostics prepatch diagnostics containing parent areas
     * @param thetaPatches theta patches
     * @return theta parent area diagnostics
     */
    public static ThetaParentAreaDiagnostics build(
            PrepatchDiagnosticSummary prepatchDiagnostics,
            List<ThetaPatch> thetaPatches) {

        if (prepatchDiagnostics == null || prepatchDiagnostics.diagnostics().isEmpty()) {
            return ThetaParentAreaDiagnostics.empty();
        }

        if (thetaPatches == null) {
            thetaPatches = List.of();
        }

        Map<CellId, Double> thetaAreaByParent = new HashMap<>();
        Map<CellId, Integer> childCountByParent = new HashMap<>();
        Map<CellId, PoleClassification> poleByParent = new HashMap<>();

        for (ThetaPatch patch : thetaPatches) {
            if (patch == null) {
                continue;
            }

            CellId id = patch.parentCellId();

            thetaAreaByParent.merge(id, patch.normalizedArea(), Double::sum);
            childCountByParent.merge(id, 1, Integer::sum);
            poleByParent.putIfAbsent(id, patch.parentPoleClassification());
        }

        ArrayList<ThetaParentAreaError> errors = new ArrayList<>();

        double totalParent = 0.0;
        double totalTheta = 0.0;
        double maxAbs = 0.0;
        double sumSq = 0.0;
        int validParents = 0;

        for (PrepatchDiagnostic diagnostic : prepatchDiagnostics.diagnostics()) {
            if (diagnostic == null || !diagnostic.valid()) {
                continue;
            }

            CellId id = diagnostic.cellId();

            double parentArea = diagnostic.normalizedArea();
            double thetaArea = thetaAreaByParent.getOrDefault(id, 0.0);
            double delta = thetaArea - parentArea;
            int childCount = childCountByParent.getOrDefault(id, 0);

            PoleClassification pole = poleByParent.get(id);
            if (pole == null) {
                pole = diagnostic.poleClassification();
            }

            ThetaParentAreaError error = new ThetaParentAreaError(
                    id,
                    parentArea,
                    thetaArea,
                    delta,
                    childCount,
                    pole);

            errors.add(error);

            totalParent += parentArea;
            totalTheta += thetaArea;
            maxAbs = Math.max(maxAbs, Math.abs(delta));
            sumSq += delta * delta;
            validParents++;
        }
        double totalDelta = totalTheta - totalParent;
        double rms = validParents == 0 ? 0.0 : Math.sqrt(sumSq / validParents);

        return new ThetaParentAreaDiagnostics(
                errors,
                totalParent,
                totalTheta,
                totalDelta,
                maxAbs,
                rms);
    }
}