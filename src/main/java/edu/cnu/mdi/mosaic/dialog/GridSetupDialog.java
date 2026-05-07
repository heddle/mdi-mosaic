package edu.cnu.mdi.mosaic.dialog;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import edu.cnu.mdi.dialog.SimpleDialog;
import edu.cnu.mdi.mosaic.grid.CartesianGrid;
import edu.cnu.mdi.mosaic.grid.SphericalGrid;
import edu.cnu.mdi.mosaic.grid.ThetaSpacing;
import edu.cnu.mdi.mosaic.model.CoordinateSystem;
import edu.cnu.mdi.mosaic.model.LengthUnit;
import edu.cnu.mdi.mosaic.model.MosaicGridPresets;
import edu.cnu.mdi.mosaic.model.MosaicGridSpec;

/**
 * Dialog for creating or replacing the active Mosaic grid specification.
 * <p>
 * The first version intentionally supports generated grids and a few presets
 * rather than direct editing of arbitrary coordinate arrays. The underlying
 * model still supports fully nonuniform grids.
 * </p>
 */
@SuppressWarnings("serial")
public class GridSetupDialog extends SimpleDialog {
	
	/** Grid specification supplied when the dialog was opened. */
	private MosaicGridSpec initialGridSpec;
	
	/** True while controls are being populated programmatically. */
	private boolean populatingControls;

    /** OK command. */
    private static final String OK = " OK ";

    /** Cancel command. */
    private static final String CANCEL = " Cancel ";

    /** Preset choices. */
    private enum Preset {
        CURRENT_GRID("Current grid"),
        GENERATED("Generated grid"),
        PAPER_TEST("Paper Test Grid"),
        SMALL_DEBUG("Small Debug Grid"),
        COARSE_GSM("Coarse GSM Grid");

        private final String label;

        Preset(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Preset selector. */
    private JComboBox<Preset> presetCombo;

    /** Theta spacing selector. */
    private JComboBox<ThetaSpacing> thetaSpacingCombo;

    /** X min spinner. */
    private JSpinner xminSpinner;

    /** X max spinner. */
    private JSpinner xmaxSpinner;

    /** X cell count spinner. */
    private JSpinner nxSpinner;

    /** Y min spinner. */
    private JSpinner yminSpinner;

    /** Y max spinner. */
    private JSpinner ymaxSpinner;

    /** Y cell count spinner. */
    private JSpinner nySpinner;

    /** Z min spinner. */
    private JSpinner zminSpinner;

    /** Z max spinner. */
    private JSpinner zmaxSpinner;

    /** Z cell count spinner. */
    private JSpinner nzSpinner;

    /** Radius spinner. */
    private JSpinner radiusSpinner;

    /** Theta cell count spinner. */
    private JSpinner nthetaSpinner;

    /** Phi cell count spinner. */
    private JSpinner nphiSpinner;

    /** Summary area. */
    private JTextArea summaryArea;

    /** The selected grid specification, if the dialog closed with OK. */
    private MosaicGridSpec selectedGridSpec;

    /** Whether the dialog was cancelled. */
    private boolean cancelled = true;

    /**
     * Creates the grid setup dialog.
     *
     * @param initialSpec the initial grid specification
     */
    public GridSetupDialog(MosaicGridSpec initialSpec) {
        super("Mosaic Grid Setup", true, OK, CANCEL);

        initialGridSpec = (initialSpec != null)
                ? initialSpec
                : MosaicGridPresets.paperTestGrid();

        selectedGridSpec = initialGridSpec;

        populateFromGridSpec(initialGridSpec);
        updateSummary();
    }
    
    /**
     * Shows a modal grid setup dialog.
     *
     * @param parent parent component
     * @param initialSpec initial grid specification
     * @return the selected grid specification, or {@code null} if cancelled
     */
    public static MosaicGridSpec showDialog(Component parent, MosaicGridSpec initialSpec) {
        GridSetupDialog dialog = new GridSetupDialog(initialSpec);

        if (parent != null) {
            Window window = SwingUtilities.getWindowAncestor(parent);
            if (window != null) {
                dialog.setLocationRelativeTo(window);
            }
        }

        dialog.setVisible(true);
        return dialog.isCancelled() ? null : dialog.getSelectedGridSpec();
    }
    
    /**
     * Populates the editable controls from a grid specification.
     * <p>
     * If the grid is nonuniform, the dialog shows the grid's min/max and cell
     * counts. Pressing OK while the "Current grid" preset is selected preserves the
     * exact nonuniform grid; pressing OK while "Generated grid" is selected creates
     * a new uniform grid from the displayed min/max/count values.
     * </p>
     *
     * @param spec the grid specification to display
     */
    /**
     * Populates the editable controls from a grid specification.
     * <p>
     * If the grid is nonuniform, the dialog shows the grid's min/max and cell
     * counts. Pressing OK while the "Current grid" preset is selected preserves the
     * exact nonuniform grid; pressing OK after editing a field switches the dialog
     * to "Generated grid" and creates a new generated grid from the displayed
     * values.
     * </p>
     *
     * @param spec the grid specification to display
     */
    private void populateFromGridSpec(MosaicGridSpec spec) {
        if (spec == null) {
            return;
        }

        populatingControls = true;

        try {
            CartesianGrid cart = spec.getCartesianGrid();
            SphericalGrid sphere = spec.getSphericalGrid();

            setSpinnerValue(xminSpinner, cart.getXMin());
            setSpinnerValue(xmaxSpinner, cart.getXMax());
            setSpinnerValue(nxSpinner, cart.getNumXCells());

            setSpinnerValue(yminSpinner, cart.getYMin());
            setSpinnerValue(ymaxSpinner, cart.getYMax());
            setSpinnerValue(nySpinner, cart.getNumYCells());

            setSpinnerValue(zminSpinner, cart.getZMin());
            setSpinnerValue(zmaxSpinner, cart.getZMax());
            setSpinnerValue(nzSpinner, cart.getNumZCells());

            setSpinnerValue(radiusSpinner, sphere.getRadius());
            setSpinnerValue(nthetaSpinner, sphere.getNumThetaCells());
            setSpinnerValue(nphiSpinner, sphere.getNumPhiCells());

            ThetaSpacing inferred = inferThetaSpacing(sphere);
            thetaSpacingCombo.setSelectedItem(inferred);
        } finally {
            populatingControls = false;
        }
    }    
    
    /**
     * Handles a user edit to one of the generated-grid controls.
     * <p>
     * Any manual edit means the dialog is no longer returning the exact current
     * grid or one of the named presets. It is now building a generated grid from
     * the visible control values.
     * </p>
     */
    private void controlChanged() {
        if (populatingControls) {
            return;
        }

        if (presetCombo != null && presetCombo.getSelectedItem() != Preset.GENERATED) {
            presetCombo.setSelectedItem(Preset.GENERATED);
        } else {
            updateSummary();
        }
    }
    
    
    /**
     * Infers the closest theta-spacing mode for display.
     *
     * @param sphere the spherical grid
     * @return the inferred theta spacing
     */
    private static ThetaSpacing inferThetaSpacing(SphericalGrid sphere) {
        double[] theta = sphere.getThetaGrid().getPoints();

        if (theta.length < 3) {
            return ThetaSpacing.UNIFORM_THETA;
        }

        double thetaErr = maxSpacingError(theta);

        double[] mu = new double[theta.length];
        for (int i = 0; i < theta.length; i++) {
            mu[i] = Math.cos(theta[i]);
        }

        double muErr = maxSpacingError(mu);

        return (muErr < thetaErr) ? ThetaSpacing.UNIFORM_COS_THETA : ThetaSpacing.UNIFORM_THETA;
    }

    /**
     * Measures the maximum deviation from uniform spacing.
     *
     * @param values values to inspect
     * @return maximum spacing deviation
     */
    private static double maxSpacingError(double[] values) {
        if (values.length < 3) {
            return 0.0;
        }

        double expected = (values[values.length - 1] - values[0]) / (values.length - 1);
        double maxErr = 0.0;

        for (int i = 1; i < values.length; i++) {
            double actual = values[i] - values[i - 1];
            maxErr = Math.max(maxErr, Math.abs(actual - expected));
        }

        return maxErr;
    }
    
    /**
     * Sets a spinner value.
     *
     * @param spinner the spinner
     * @param value the new value
     */
    private static void setSpinnerValue(JSpinner spinner, double value) {
        spinner.setValue(value);
    }

    /**
     * Sets a spinner value.
     *
     * @param spinner the spinner
     * @param value the new value
     */
    private static void setSpinnerValue(JSpinner spinner, int value) {
        spinner.setValue(value);
    }

    @Override
    protected Component createCenterComponent() {
        JPanel main = new JPanel(new BorderLayout(8, 8));
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        main.add(createCoordinatePanel(), BorderLayout.NORTH);
        main.add(createGridPanel(), BorderLayout.CENTER);
        main.add(createSummaryPanel(), BorderLayout.SOUTH);

        return main;
    }

    /**
     * Creates the coordinate-system panel.
     *
     * @return the coordinate panel
     */
    private Component createCoordinatePanel() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setOpaque(false);
        area.setText(
                "Coordinate system: " + CoordinateSystem.GSM + "\n"
                        + "Origin: Earth center\n"
                        + "Length unit: " + LengthUnit.EARTH_RADII + "\n"
                        + "Axes: +X toward Sun, +Z tilted toward north magnetic pole, +Y completes GSM system");

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Physical Coordinates"));
        panel.add(area, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates the editable grid panel.
     *
     * @return the grid panel
     */
    private Component createGridPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JPanel presetPanel = new JPanel(new GridLayout(1, 2, 8, 8));
        presetPanel.setBorder(BorderFactory.createTitledBorder("Preset"));
        
        presetCombo = new JComboBox<>(Preset.values());
        presetCombo.setSelectedItem(Preset.CURRENT_GRID);
        
        presetCombo.addActionListener(e -> {
            if (populatingControls) {
                return;
            }

            Preset preset = (Preset) presetCombo.getSelectedItem();

            if (preset == Preset.PAPER_TEST) {
                populateFromGridSpec(MosaicGridPresets.paperTestGrid());
            } else if (preset == Preset.SMALL_DEBUG) {
                populateFromGridSpec(MosaicGridPresets.smallDebugGrid());
            } else if (preset == Preset.COARSE_GSM) {
                populateFromGridSpec(MosaicGridPresets.coarseGsmGrid());
            } else if (preset == Preset.CURRENT_GRID) {
                populateFromGridSpec(initialGridSpec);
            } else if (preset == Preset.GENERATED) {
                // Leave the current visible control values alone.
                // They now define the generated grid.
            }

            updateSummary();
        });
        
        presetPanel.add(new JLabel("Grid source"));
        presetPanel.add(presetCombo);

        JPanel cartPanel = createCartesianPanel();
        JPanel spherePanel = createSphericalPanel();

        JPanel editPanel = new JPanel(new GridLayout(1, 2, 8, 8));
        editPanel.add(cartPanel);
        editPanel.add(spherePanel);

        panel.add(presetPanel, BorderLayout.NORTH);
        panel.add(editPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Creates the Cartesian grid editing panel.
     *
     * @return the Cartesian grid panel
     */
    private JPanel createCartesianPanel() {
        JPanel panel = new JPanel(new GridLayout(9, 2, 6, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Cartesian GSM Grid"));

        xminSpinner = doubleSpinner(-6.0, -1.0e6, 1.0e6, 0.5);
        xmaxSpinner = doubleSpinner(6.0, -1.0e6, 1.0e6, 0.5);
        nxSpinner = intSpinner(36, 1, 10000);

        yminSpinner = doubleSpinner(-6.0, -1.0e6, 1.0e6, 0.5);
        ymaxSpinner = doubleSpinner(6.0, -1.0e6, 1.0e6, 0.5);
        nySpinner = intSpinner(36, 1, 10000);

        zminSpinner = doubleSpinner(-6.0, -1.0e6, 1.0e6, 0.5);
        zmaxSpinner = doubleSpinner(6.0, -1.0e6, 1.0e6, 0.5);
        nzSpinner = intSpinner(36, 1, 10000);

        addLabeled(panel, "X min", xminSpinner);
        addLabeled(panel, "X max", xmaxSpinner);
        addLabeled(panel, "X cells", nxSpinner);
        addLabeled(panel, "Y min", yminSpinner);
        addLabeled(panel, "Y max", ymaxSpinner);
        addLabeled(panel, "Y cells", nySpinner);
        addLabeled(panel, "Z min", zminSpinner);
        addLabeled(panel, "Z max", zmaxSpinner);
        addLabeled(panel, "Z cells", nzSpinner);

        return panel;
    }

    /**
     * Creates the spherical grid editing panel.
     *
     * @return the spherical grid panel
     */
    private JPanel createSphericalPanel() {
        JPanel panel = new JPanel(new GridLayout(4, 2, 6, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Spherical Grid"));

        radiusSpinner = doubleSpinner(4.60993, 1.0e-12, 1.0e6, 0.1);
        nthetaSpinner = intSpinner(48, 1, 10000);
        nphiSpinner = intSpinner(32, 1, 10000);
        thetaSpacingCombo = new JComboBox<>(ThetaSpacing.values());
        
        thetaSpacingCombo.addActionListener(e -> {
            Component c = thetaSpacingCombo.getTopLevelAncestor();
            if (c instanceof GridSetupDialog dialog) {
                dialog.controlChanged();
            }
        });

        addLabeled(panel, "Radius, R\u2091", radiusSpinner);
        addLabeled(panel, "Theta cells", nthetaSpinner);
        addLabeled(panel, "Phi cells", nphiSpinner);
        addLabeled(panel, "Theta spacing", thetaSpacingCombo);

        return panel;
    }

    /**
     * Creates the summary panel.
     *
     * @return the summary panel
     */
    private Component createSummaryPanel() {
        summaryArea = new JTextArea(7, 70);
        summaryArea.setEditable(false);
        summaryArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Grid Summary"));
        panel.add(summaryArea, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Gets the selected grid specification.
     *
     * @return the selected grid specification
     */
    public MosaicGridSpec getSelectedGridSpec() {
        return selectedGridSpec;
    }

    /**
     * Checks whether the dialog was cancelled.
     *
     * @return {@code true} if cancelled
     */
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    protected void handleCommand(String command) {
        if (command != null && command.trim().equals("OK")) {
            try {
                selectedGridSpec = buildGridSpec();
                cancelled = false;
                setVisible(false);
            } catch (RuntimeException e) {
                summaryArea.setText("Grid setup error:\n" + e.getMessage());
            }
        } else {
            cancelled = true;
            setVisible(false);
        }
    }

    /**
     * Builds the grid specification from the current dialog state.
     *
     * @return the selected grid specification
     */
    private MosaicGridSpec buildGridSpec() {
    	Preset preset = (Preset) presetCombo.getSelectedItem();

    	if (preset == Preset.CURRENT_GRID) {
    	    return initialGridSpec;
    	}

    	if (preset == Preset.PAPER_TEST) {
    	    return MosaicGridPresets.paperTestGrid();
    	}

    	if (preset == Preset.SMALL_DEBUG) {
    	    return MosaicGridPresets.smallDebugGrid();
    	}

    	if (preset == Preset.COARSE_GSM) {
    	    return MosaicGridPresets.coarseGsmGrid();
    	}
        double xmin = doubleValue(xminSpinner);
        double xmax = doubleValue(xmaxSpinner);
        int nx = intValue(nxSpinner);

        double ymin = doubleValue(yminSpinner);
        double ymax = doubleValue(ymaxSpinner);
        int ny = intValue(nySpinner);

        double zmin = doubleValue(zminSpinner);
        double zmax = doubleValue(zmaxSpinner);
        int nz = intValue(nzSpinner);

        double radius = doubleValue(radiusSpinner);
        int ntheta = intValue(nthetaSpinner);
        int nphi = intValue(nphiSpinner);
        ThetaSpacing spacing = (ThetaSpacing) thetaSpacingCombo.getSelectedItem();

        CartesianGrid cartesianGrid = CartesianGrid.uniform(
                xmin, xmax, nx,
                ymin, ymax, ny,
                zmin, zmax, nz);

        SphericalGrid sphericalGrid = SphericalGrid.generated(radius, ntheta, nphi, spacing);

        return new MosaicGridSpec("Generated GSM Grid", CoordinateSystem.GSM,
                LengthUnit.EARTH_RADII, cartesianGrid, sphericalGrid);
    }

    /**
     * Updates the summary display.
     */
    private void updateSummary() {
        if (summaryArea == null) {
            return;
        }

        try {
            summaryArea.setText(buildGridSpec().summary());
        } catch (RuntimeException e) {
            summaryArea.setText("Grid setup error:\n" + e.getMessage());
        }
    }

    /**
     * Creates a floating-point spinner.
     *
     * @param value initial value
     * @param min minimum value
     * @param max maximum value
     * @param step step size
     * @return the spinner
     */
    private static JSpinner doubleSpinner(double value, double min, double max, double step) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        spinner.addChangeListener(e -> {
            Component c = spinner.getTopLevelAncestor();
            if (c instanceof GridSetupDialog dialog) {
                dialog.controlChanged();
            }
        });
        return spinner;
    }
    
    /**
     * Creates an integer spinner.
     *
     * @param value initial value
     * @param min minimum value
     * @param max maximum value
     * @return the spinner
     */
    private static JSpinner intSpinner(int value, int min, int max) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        spinner.addChangeListener(e -> {
            Component c = spinner.getTopLevelAncestor();
            if (c instanceof GridSetupDialog dialog) {
                dialog.controlChanged();
            }
        });
        return spinner;
    }
    
    /**
     * Adds a label/component pair to a grid panel.
     *
     * @param panel destination panel
     * @param label label text
     * @param component editor component
     */
    private static void addLabeled(JPanel panel, String label, Component component) {
        panel.add(new JLabel(label));
        panel.add(component);
    }

    /**
     * Gets a double value from a spinner.
     *
     * @param spinner the spinner
     * @return the double value
     */
    private static double doubleValue(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue();
    }

    /**
     * Gets an integer value from a spinner.
     *
     * @param spinner the spinner
     * @return the integer value
     */
    private static int intValue(JSpinner spinner) {
        return ((Number) spinner.getValue()).intValue();
    }
}