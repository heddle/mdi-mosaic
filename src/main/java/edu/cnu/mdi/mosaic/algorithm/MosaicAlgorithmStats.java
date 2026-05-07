package edu.cnu.mdi.mosaic.algorithm;

import java.util.EnumMap;
import java.util.Map;

import edu.cnu.mdi.mosaic.cell.CellIntersectionType;
import edu.cnu.mdi.mosaic.cell.IntersectingCell;

/**
 * Summary statistics for a Mosaic algorithm run.
 */
public final class MosaicAlgorithmStats {

    /** Count of intersecting cells by type. */
    private final EnumMap<CellIntersectionType, Long> cellTypeCounts;

    /**
     * Creates statistics from an existing count map.
     *
     * @param cellTypeCounts count map
     */
    private MosaicAlgorithmStats(Map<CellIntersectionType, Long> cellTypeCounts) {
        this.cellTypeCounts = new EnumMap<>(CellIntersectionType.class);

        for (CellIntersectionType type : CellIntersectionType.values()) {
            this.cellTypeCounts.put(type,
                    cellTypeCounts == null ? 0L : cellTypeCounts.getOrDefault(type, 0L));
        }
    }

    /**
     * Builds statistics from intersecting cells.
     *
     * @param cells intersecting cells
     * @return statistics
     */
    public static MosaicAlgorithmStats fromIntersectingCells(Iterable<IntersectingCell> cells) {
        EnumMap<CellIntersectionType, Long> counts = new EnumMap<>(CellIntersectionType.class);

        for (CellIntersectionType type : CellIntersectionType.values()) {
            counts.put(type, 0L);
        }

        if (cells != null) {
            for (IntersectingCell cell : cells) {
                if (cell != null && cell.type() != null) {
                    counts.put(cell.type(), counts.get(cell.type()) + 1L);
                }
            }
        }

        return new MosaicAlgorithmStats(counts);
    }

    /**
     * Creates empty statistics.
     *
     * @return empty statistics
     */
    public static MosaicAlgorithmStats empty() {
        return new MosaicAlgorithmStats(null);
    }

    /**
     * Gets the count for an intersection type.
     *
     * @param type the intersection type
     * @return count for that type
     */
    public long getCount(CellIntersectionType type) {
        if (type == null) {
            return 0L;
        }

        return cellTypeCounts.getOrDefault(type, 0L);
    }

    /**
     * Gets a copy of the type-count map.
     *
     * @return type-count map
     */
    public Map<CellIntersectionType, Long> getCellTypeCounts() {
        return new EnumMap<>(cellTypeCounts);
    }

    /**
     * Gets the total number of intersecting cells represented by these stats.
     *
     * @return total count
     */
    public long getTotalIntersectingCells() {
        long total = 0L;

        for (long count : cellTypeCounts.values()) {
            total += count;
        }

        return total;
    }

    /**
     * Creates a multi-line summary suitable for logging.
     *
     * @return summary string
     */
    public String summary() {
        StringBuilder sb = new StringBuilder();

        sb.append("Cell intersection type counts:").append(System.lineSeparator());

        for (CellIntersectionType type : CellIntersectionType.values()) {
            sb.append("  ")
                    .append(type)
                    .append(": ")
                    .append(getCount(type))
                    .append(System.lineSeparator());
        }

        return sb.toString();
    }
}