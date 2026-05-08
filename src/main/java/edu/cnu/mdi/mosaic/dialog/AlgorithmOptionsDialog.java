package edu.cnu.mdi.mosaic.dialog;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import edu.cnu.mdi.mosaic.algorithm.MosaicAlgorithmOptions;
import edu.cnu.mdi.mosaic.model.MosaicModel;

/**
 * Dialog for editing Mosaic algorithm options.
 */
@SuppressWarnings("serial")
public class AlgorithmOptionsDialog extends JDialog {

    /** Shared Mosaic model. */
    private final MosaicModel model;

    /** Working copy of options. */
    private final MosaicAlgorithmOptions options;

    /** Area samples spinner. */
    private JSpinner areaSamplesSpinner;

    /** Convergence test checkbox. */
    private JCheckBox convergenceCheckBox;

    /** Convergence sample-count text field. */
    private JTextField convergenceCountsField;

    /**
     * Creates the dialog.
     *
     * @param owner owner window
     * @param model shared Mosaic model
     */
    public AlgorithmOptionsDialog(Window owner, MosaicModel model) {
        super(owner, "Mosaic Algorithm Options", ModalityType.APPLICATION_MODAL);

        if (model == null) {
            throw new IllegalArgumentException("model must not be null.");
        }

        this.model = model;
        this.options = model.getAlgorithmOptions();

        setLayout(new BorderLayout(8, 8));
        add(createCenterPanel(), BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);

        pack();
        setMinimumSize(getPreferredSize());
        setLocationRelativeTo(owner);
    }

    /**
     * Shows the dialog.
     *
     * @param parent parent component
     * @param model shared Mosaic model
     */
    public static void showDialog(Component parent, MosaicModel model) {
        Window owner = (parent == null)
                ? null
                : SwingUtilities.getWindowAncestor(parent);

        AlgorithmOptionsDialog dialog =
                new AlgorithmOptionsDialog(owner, model);
        dialog.setVisible(true);
    }

    /**
     * Creates the main editing panel.
     *
     * @return center panel
     */
    private Component createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));

        JPanel grid = new JPanel(new GridLayout(0, 2, 8, 6));
        grid.setBorder(BorderFactory.createTitledBorder("Area Calculation"));

        areaSamplesSpinner = new JSpinner(new SpinnerNumberModel(
                options.getAreaSamplesPerCurve(), 2, 512, 1));

        convergenceCheckBox = new JCheckBox(
                "Run convergence test",
                options.isRunAreaConvergenceTest());

        convergenceCountsField = new JTextField(
                countsToText(options.getConvergenceSampleCounts()));

        grid.add(new JLabel("Area samples per curve"));
        grid.add(areaSamplesSpinner);

        grid.add(new JLabel("Diagnostics"));
        grid.add(convergenceCheckBox);

        grid.add(new JLabel("Convergence sample counts"));
        grid.add(convergenceCountsField);

        panel.add(grid, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Creates the button panel.
     *
     * @return button panel
     */
    private Component createButtonPanel() {
        JPanel panel = new JPanel();

        javax.swing.JButton ok = new javax.swing.JButton("OK");
        javax.swing.JButton defaults = new javax.swing.JButton("Defaults");
        javax.swing.JButton cancel = new javax.swing.JButton("Cancel");

        ok.addActionListener(e -> applyAndClose());
        defaults.addActionListener(e -> resetDefaults());
        cancel.addActionListener(e -> dispose());

        panel.add(ok);
        panel.add(defaults);
        panel.add(cancel);

        return panel;
    }

    /**
     * Applies the edited options and closes the dialog.
     */
    private void applyAndClose() {
        try {
            options.setAreaSamplesPerCurve(
                    ((Number) areaSamplesSpinner.getValue()).intValue());

            options.setRunAreaConvergenceTest(convergenceCheckBox.isSelected());

            options.setConvergenceSampleCounts(
                    parseCounts(convergenceCountsField.getText()));

            model.setAlgorithmOptions(options);
            dispose();

        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this,
                    "Invalid algorithm options:\n" + ex.getMessage(),
                    "Algorithm Options",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Resets the dialog controls to default values.
     */
    private void resetDefaults() {
        options.resetToDefaults();

        areaSamplesSpinner.setValue(options.getAreaSamplesPerCurve());
        convergenceCheckBox.setSelected(options.isRunAreaConvergenceTest());
        convergenceCountsField.setText(
                countsToText(options.getConvergenceSampleCounts()));
    }

    /**
     * Converts sample counts to display text.
     *
     * @param counts counts
     * @return display text
     */
    private static String countsToText(int[] counts) {
        if (counts == null || counts.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < counts.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(counts[i]);
        }

        return sb.toString();
    }

    /**
     * Parses comma- or whitespace-separated integer counts.
     *
     * @param text input text
     * @return parsed counts
     */
    private static int[] parseCounts(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                    "Convergence sample counts cannot be blank.");
        }

        String[] tokens = text.trim().split("[,\\s]+");
        List<Integer> values = new ArrayList<>();

        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }

            int value = Integer.parseInt(token.trim());

            if (value < 2) {
                throw new IllegalArgumentException(
                        "Sample counts must be at least 2.");
            }

            values.add(value);
        }

        if (values.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one convergence sample count is required.");
        }

        int[] counts = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            counts[i] = values.get(i);
        }

        return counts;
    }
}