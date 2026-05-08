package edu.cnu.mdi.mosaic.patch;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Summary counts of prepatch pole involvement.
 */
public final class PoleStats {

    /** North-pole relation counts. */
    private final EnumMap<PoleRelation, Integer> northCounts =
            new EnumMap<>(PoleRelation.class);

    /** South-pole relation counts. */
    private final EnumMap<PoleRelation, Integer> southCounts =
            new EnumMap<>(PoleRelation.class);

    /** Number of prepatches involving either pole. */
    private int anyPoleCount;

    /**
     * Creates empty pole statistics.
     */
    private PoleStats() {
        for (PoleRelation relation : PoleRelation.values()) {
            northCounts.put(relation, 0);
            southCounts.put(relation, 0);
        }
    }

    /**
     * Builds pole statistics from prepatches.
     *
     * @param prepatches prepatches
     * @return pole statistics
     */
    public static PoleStats fromPrepatches(List<Prepatch> prepatches) {
        PoleStats stats = new PoleStats();

        if (prepatches == null) {
            return stats;
        }

        for (Prepatch prepatch : prepatches) {
            if (prepatch == null) {
                continue;
            }

            PoleClassification pc = prepatch.poleClassification();

            stats.northCounts.put(pc.north(),
                    stats.northCounts.get(pc.north()) + 1);

            stats.southCounts.put(pc.south(),
                    stats.southCounts.get(pc.south()) + 1);

            if (pc.hasPoleInvolvement()) {
                stats.anyPoleCount++;
            }
        }

        return stats;
    }

    /**
     * Gets the number of prepatches involving either pole.
     *
     * @return count
     */
    public int getAnyPoleCount() {
        return anyPoleCount;
    }

    /**
     * Gets north-pole counts.
     *
     * @return copy of north-pole counts
     */
    public Map<PoleRelation, Integer> getNorthCounts() {
        return new EnumMap<>(northCounts);
    }

    /**
     * Gets south-pole counts.
     *
     * @return copy of south-pole counts
     */
    public Map<PoleRelation, Integer> getSouthCounts() {
        return new EnumMap<>(southCounts);
    }

    /**
     * Gets a north-pole count.
     *
     * @param relation relation
     * @return count
     */
    public int getNorthCount(PoleRelation relation) {
        return northCounts.getOrDefault(relation, 0);
    }

    /**
     * Gets a south-pole count.
     *
     * @param relation relation
     * @return count
     */
    public int getSouthCount(PoleRelation relation) {
        return southCounts.getOrDefault(relation, 0);
    }

    /**
     * Creates a concise multi-line summary.
     *
     * @return summary string
     */
    public String summary() {
        StringBuilder sb = new StringBuilder();

        sb.append("Pole involvement:").append(System.lineSeparator());
        sb.append("  any pole: ").append(anyPoleCount).append(System.lineSeparator());

        appendPoleSummary(sb, "north", northCounts);
        appendPoleSummary(sb, "south", southCounts);

        return sb.toString();
    }

    /**
     * Appends summary for one pole.
     *
     * @param sb string builder
     * @param label pole label
     * @param counts count map
     */
    private static void appendPoleSummary(StringBuilder sb, String label,
            EnumMap<PoleRelation, Integer> counts) {

        sb.append("  ").append(label).append(": ");

        boolean first = true;

        for (PoleRelation relation : PoleRelation.values()) {
            if (relation == PoleRelation.NONE) {
                continue;
            }

            int count = counts.getOrDefault(relation, 0);
            if (count <= 0) {
                continue;
            }

            if (!first) {
                sb.append(", ");
            }

            sb.append(relation).append("=").append(count);
            first = false;
        }

        if (first) {
            sb.append("none");
        }

        sb.append(System.lineSeparator());
    }
}