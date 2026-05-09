package edu.cnu.mdi.mosaic.map;

import java.awt.Component;
import java.awt.Font;
import java.awt.GridLayout;

import javax.swing.JToggleButton;
import javax.swing.JPanel;

import edu.cnu.mdi.component.CommonBorder;
import edu.cnu.mdi.mosaic.model.MosaicModel;
import edu.cnu.mdi.ui.fonts.Fonts;

/**
 * Mosaic-specific display-mode controls inserted above the map feedback pane.
 */
@SuppressWarnings("serial")
public class MosaicMapModePanel extends JPanel {

    /** Shared Mosaic model. */
    private final MosaicModel model;

    /** Controlled Mosaic view. */
    private final MosaicView2D view;
    
    // font for toggle buttons
    private static final Font font = Fonts.plainFontDelta(-3);

    /**
     * Creates the Mosaic map mode panel.
     *
     * @param model the shared Mosaic model
     * @param view the controlled Mosaic 2D view
     */
    public MosaicMapModePanel(MosaicModel model, MosaicView2D view) {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null.");
        }
        if (view == null) {
            throw new IllegalArgumentException("view must not be null.");
        }

        this.model = model;
        this.view = view;

        setAlignmentX(Component.LEFT_ALIGNMENT);
        setLayout(new GridLayout(3, 1, 2, 2));
        setBorder(new CommonBorder("Mosaic Display"));

        addMonteCarloToggle();
        addPrepatchToggle();
        addThetaPatchToggle();
        addFinalPatchToggle();
        addPlaceholderToggle("Show Patch IDs");
    }

    /**
     * Adds the Monte Carlo visibility toggle.
     */
    private void addMonteCarloToggle() {
        JToggleButton button = new JToggleButton("Show Monte Carlo");
        button.setFont(font);
        button.setSelected(view.isMonteCarloPointsVisible());
        button.addActionListener(e -> {
            view.setMonteCarloPointsVisible(button.isSelected());
            model.setStatusMessage(button.isSelected()
                    ? "Monte Carlo points visible."
                    : "Monte Carlo points hidden.");
        });
        add(button);
    }
    
    private void addThetaPatchToggle() {
        JToggleButton button = new JToggleButton("Show Theta Patches");
        button.setFont(font);
        button.setSelected(view.isThetaPatchesVisible());
        button.addActionListener(e -> {
            view.setThetaPatchesVisible(button.isSelected());
            model.setStatusMessage(button.isSelected()
                    ? "Theta patches visible."
                    : "Theta patches hidden.");
        });
        add(button);
    }
    
    /**
     * Adds the final-patch visibility toggle.
     */
    private void addFinalPatchToggle() {
        JToggleButton button = new JToggleButton("Show Final Patches");
        button.setFont(font);
        button.setSelected(view.isFinalPatchesVisible());

        button.addActionListener(e -> {
            view.setFinalPatchesVisible(button.isSelected());
            model.setStatusMessage(button.isSelected()
                    ? "Final phi patches visible."
                    : "Final phi patches hidden.");
        });

        add(button);
    }

    /**
     * Adds the prepatch visibility toggle.
     */
    private void addPrepatchToggle() {
        JToggleButton button = new JToggleButton("Show Prepatches");
        button.setFont(font);
        button.setSelected(view.isPrepatchesVisible());
        button.addActionListener(e -> {
            view.setPrepatchesVisible(button.isSelected());
            model.setStatusMessage(button.isSelected()
                    ? "Prepatch boundaries visible."
                    : "Prepatch boundaries hidden.");
        });
        add(button);
    }

    /**
     * Adds a placeholder toggle for later algorithm stages.
     *
     * @param label button label
     */
    private void addPlaceholderToggle(String label) {
        JToggleButton button = new JToggleButton(label);
        button.setFont(font);
        button.addActionListener(e -> model.setStatusMessage(label));
        add(button);
    }
}