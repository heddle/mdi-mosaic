package edu.cnu.mdi.mosaic.model;

import edu.cnu.mdi.mosaic.grid.CartesianGrid;
import edu.cnu.mdi.mosaic.grid.SphericalGrid;

/**
 * Immutable specification of a Mosaic grid.
 * <p>
 * A Mosaic grid specification contains the Cartesian grid, the spherical
 * surface grid, and the physical interpretation of the coordinates. It does
 * not contain computed prepatches, theta patches, final patches, or area and
 * perimeter results. Those should be stored in a separate result object after
 * the Mosaic algorithm runs.
 * </p>
 */
public final class MosaicGridSpec {

    /** Coordinate system. */
    private final CoordinateSystem coordinateSystem;

    /** Length unit. */
    private final LengthUnit lengthUnit;

    /** Cartesian grid. */
    private final CartesianGrid cartesianGrid;

    /** Spherical grid. */
    private final SphericalGrid sphericalGrid;

    /** Optional name for display and logging. */
    private final String name;

    /**
     * Creates a Mosaic grid specification.
     *
     * @param name display name
     * @param coordinateSystem coordinate system
     * @param lengthUnit length unit
     * @param cartesianGrid Cartesian grid
     * @param sphericalGrid spherical grid
     */
    public MosaicGridSpec(String name, CoordinateSystem coordinateSystem,
            LengthUnit lengthUnit, CartesianGrid cartesianGrid,
            SphericalGrid sphericalGrid) {

        if (cartesianGrid == null) {
            throw new IllegalArgumentException("Cartesian grid must not be null.");
        }
        if (sphericalGrid == null) {
            throw new IllegalArgumentException("Spherical grid must not be null.");
        }

        this.name = (name == null || name.isBlank()) ? "Untitled Mosaic Grid" : name.trim();
        this.coordinateSystem = coordinateSystem == null ? CoordinateSystem.GSM : coordinateSystem;
        this.lengthUnit = lengthUnit == null ? LengthUnit.EARTH_RADII : lengthUnit;
        this.cartesianGrid = new CartesianGrid(cartesianGrid);
        this.sphericalGrid = new SphericalGrid(sphericalGrid);

        validateSphereEnclosed();
    }

    /**
     * Gets the display name.
     *
     * @return the display name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the coordinate system.
     *
     * @return the coordinate system
     */
    public CoordinateSystem getCoordinateSystem() {
        return coordinateSystem;
    }

    /**
     * Gets the length unit.
     *
     * @return the length unit
     */
    public LengthUnit getLengthUnit() {
        return lengthUnit;
    }

    /**
     * Gets a copy of the Cartesian grid.
     *
     * @return the Cartesian grid
     */
    public CartesianGrid getCartesianGrid() {
        return new CartesianGrid(cartesianGrid);
    }

    /**
     * Gets a copy of the spherical grid.
     *
     * @return the spherical grid
     */
    public SphericalGrid getSphericalGrid() {
        return new SphericalGrid(sphericalGrid);
    }

    /**
     * Creates a human-readable summary suitable for logging.
     *
     * @return a summary string
     */
    public String summary() {
        String sep = System.lineSeparator();
        return "Mosaic Grid: " + name + sep
                + "Coordinate system: " + coordinateSystem + sep
                + "Length unit: " + lengthUnit + sep
                + cartesianGrid.summary() + sep
                + sphericalGrid.summary();
    }

    /**
     * Checks that the spherical surface is enclosed by the Cartesian grid.
     * <p>
     * This is intentionally conservative and assumes the sphere is centered at
     * the global origin.
     * </p>
     */
    private void validateSphereEnclosed() {
        double r = sphericalGrid.getRadius();

        if (cartesianGrid.getXMin() > -r || cartesianGrid.getXMax() < r
                || cartesianGrid.getYMin() > -r || cartesianGrid.getYMax() < r
                || cartesianGrid.getZMin() > -r || cartesianGrid.getZMax() < r) {
            throw new IllegalArgumentException(
                    "The Cartesian grid must enclose the spherical grid of radius " + r + ".");
        }
    }
}