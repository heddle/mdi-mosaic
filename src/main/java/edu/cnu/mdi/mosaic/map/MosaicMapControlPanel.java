package edu.cnu.mdi.mosaic.map;

import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import edu.cnu.mdi.component.CommonBorder;
import edu.cnu.mdi.mapping.MapView2D;
import edu.cnu.mdi.mapping.projection.EProjection;
import edu.cnu.mdi.mapping.theme.MapTheme;
import edu.cnu.mdi.ui.fonts.Fonts;

/**
 * Mosaic-specific map control panel.
 *
 * <p>
 * This panel demonstrates how an MDI application can replace the standard
 * {@code MapControlPanel} and include an application-supplied projection
 * without adding that projection to MDI's built-in {@code EProjection} enum.
 * </p>
 */
@SuppressWarnings("serial")
public class MosaicMapControlPanel extends JPanel {

    /** Controlled view. */
    private final MapView2D mapView;

    /** Control font. */
    private final Font font = Fonts.plainFontDelta(-2);

    /** Current theme. */
    private MapTheme currentTheme = MapTheme.light();

    /**
     * Creates the Mosaic map control panel.
     *
     * @param mapView the controlled map view
     */
    public MosaicMapControlPanel(MapView2D mapView) {
        if (mapView == null) {
            throw new IllegalArgumentException("mapView must not be null.");
        }

        this.mapView = mapView;

        setAlignmentX(Component.LEFT_ALIGNMENT);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        createProjectionSelector();
        add(Box.createVerticalStrut(6));
        createThemeSelector();
    }

    /**
     * Gets the current theme.
     *
     * @return the current theme
     */
    public MapTheme getCurrentTheme() {
        return currentTheme;
    }

    /**
     * Creates the projection selector.
     */
    private void createProjectionSelector() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBorder(new CommonBorder("Projection"));

        ButtonGroup group = new ButtonGroup();

        JRadioButton archimedes = radio("Archimedes/Lambert", true);
        JRadioButton mercator = radio("Mercator", false);
        JRadioButton mollweide = radio("Mollweide", false);
        JRadioButton orthographic = radio("Orthographic", false);
        JRadioButton lambertEqualArea = radio("Lambert Equal-Area", false);

        archimedes.addActionListener(e ->
                mapView.setProjection(new ArchimedesLambertCylindricalProjection(currentTheme)));

        mercator.addActionListener(e -> mapView.setProjection(EProjection.MERCATOR));
        mollweide.addActionListener(e -> mapView.setProjection(EProjection.MOLLWEIDE));
        orthographic.addActionListener(e -> mapView.setProjection(EProjection.ORTHOGRAPHIC));
        lambertEqualArea.addActionListener(e -> mapView.setProjection(EProjection.LAMBERT_EQUAL_AREA));

        group.add(archimedes);
        group.add(mercator);
        group.add(mollweide);
        group.add(orthographic);
        group.add(lambertEqualArea);

        panel.add(archimedes);
        panel.add(mercator);
        panel.add(mollweide);
        panel.add(orthographic);
        panel.add(lambertEqualArea);

        add(panel);
    }

    /**
     * Creates the theme selector.
     */
    private void createThemeSelector() {
        JPanel panel = new JPanel();
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBorder(new CommonBorder("Map Theme"));

        ButtonGroup group = new ButtonGroup();

        JRadioButton light = radio("Light", true);
        JRadioButton dark = radio("Dark", false);
        JRadioButton blue = radio("Blue", false);

        ActionListener themeListener = e -> {
            if (light.isSelected()) {
                currentTheme = MapTheme.light();
            } else if (dark.isSelected()) {
                currentTheme = MapTheme.dark();
            } else if (blue.isSelected()) {
                currentTheme = MapTheme.blue();
            }

            if (mapView.getProjection() != null) {
                mapView.getProjection().setTheme(currentTheme);
                mapView.refresh();
            }
        };

        light.addActionListener(themeListener);
        dark.addActionListener(themeListener);
        blue.addActionListener(themeListener);

        group.add(light);
        group.add(dark);
        group.add(blue);

        panel.add(light);
        panel.add(dark);
        panel.add(blue);

        add(panel);
    }

    /**
     * Creates a radio button with standard Mosaic map styling.
     *
     * @param label label text
     * @param selected initial selected state
     * @return the radio button
     */
    private JRadioButton radio(String label, boolean selected) {
        JRadioButton button = new JRadioButton(label, selected);
        button.setFont(font);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        return button;
    }
}