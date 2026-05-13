package edu.cnu.mdi.mosaic.map;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import edu.cnu.mdi.container.IContainer;
import edu.cnu.mdi.graphics.toolbar.ToolBits;
import edu.cnu.mdi.mapping.MapView2D;
import edu.cnu.mdi.mapping.projection.IMapProjection;
import edu.cnu.mdi.mapping.render.IPickable;
import edu.cnu.mdi.mapping.shapefile.ShapeFeatureRenderer;
import edu.cnu.mdi.mosaic.algorithm.MosaicAlgorithmResult;
import edu.cnu.mdi.mosaic.cell.CellId;
import edu.cnu.mdi.mosaic.geom.Vec3;
import edu.cnu.mdi.mosaic.grid.Grid1D;
import edu.cnu.mdi.mosaic.grid.SphericalGrid;
import edu.cnu.mdi.mosaic.mc.MonteCarloPoint;
import edu.cnu.mdi.mosaic.model.MosaicModel;
import edu.cnu.mdi.mosaic.patch.FinalPatchBoundaryCanonicalizer;
import edu.cnu.mdi.mosaic.patch.GeneralCurve;
import edu.cnu.mdi.mosaic.patch.Prepatch;
import edu.cnu.mdi.mosaic.phi.PhiParentAreaError;
import edu.cnu.mdi.mosaic.phi.PhiPatch;
import edu.cnu.mdi.mosaic.theta.ThetaPatch;
import edu.cnu.mdi.ui.colors.ScientificColorMap;
import edu.cnu.mdi.util.PropertyUtils;
import edu.cnu.mdi.util.UnicodeUtils;

/**
 * Two-dimensional Mosaic map view.
 *
 * <p>
 * This view demonstrates application-level extension of MDI mapping:
 * </p>
 *
 * <ul>
 * <li>It uses a Mosaic-supplied Archimedes/Lambert cylindrical projection.</li>
 * <li>It replaces the standard map control panel.</li>
 * <li>It inserts Mosaic display controls above the feedback pane.</li>
 * <li>It does not load or display countries or cities by default.</li>
 * </ul>
 */
@SuppressWarnings("serial")
public class MosaicView2D extends MapView2D {

	// Constant for π/2, used in projection calculations.
	private final double PIOVER2 = Math.PI / 2.0;

	private static final double ANGULAR_SNAP_TOL = 1.0e-5;

	/** for theta index display in feedback */
	public static final String NTHETA = "n" + UnicodeUtils.SMALL_THETA;

	/** for phi index display in feedback */
	public static final String NPHI = "n" + UnicodeUtils.SMALL_PHI;

	/** Shared Mosaic model. */
	private final MosaicModel model;

	// Reusable point for projection calculations to avoid unnecessary object
	// creation.
	private int[] indexArray = new int[5];

	/** Whether Monte Carlo points are drawn. */
	private boolean showMonteCarloPoints = true;

	/** Active color map for Monte Carlo points. */
	private ScientificColorMap monteCarloColorMap = ScientificColorMap.VIRIDIS;

	/** Dot size for Monte Carlo points, in pixels. */
	private int monteCarloDotSize = 2;

	/** Whether ordinary prepatch boundary curves are drawn. */
	private boolean showPrepatches = false;

	/** Number of sample points used per prepatch curve. */
	private int prepatchCurveSamples = 24;

	/** Prepatch boundary color. */
	private Color prepatchColor = new Color(20, 20, 20, 210);

	/** Prepatch boundary stroke width. */
	private float prepatchStrokeWidth = 1.0f;

	/** Whether theta patch boundaries are drawn. */
	private boolean showThetaPatches = false;

	/** Theta patch boundary color. */
	private Color thetaPatchColor = new Color(180, 0, 0, 180);

	/** Theta patch stroke width. */
	private float thetaPatchStrokeWidth = 0.8f;

	/** Whether spherical grid guide lines are drawn. */
	private boolean showSphericalGridLines = true;

	/** Whether non-polar final phi-patch boundaries are drawn. */
	private boolean showFinalPatches = false;

	/** Whether non-polar final phi patches are filled translucently. */
	private boolean fillFinalPatches = false;

	/** Whether polar-derived final phi-patch boundaries are drawn. */
	private boolean showPolarFinalPatches = false;

	/** Whether polar-derived final phi patches are filled translucently. */
	private boolean fillPolarFinalPatches = false;

	/**
	 * Whether projected pole markers are drawn when polar final patches are
	 * visible.
	 */
	private boolean showProjectedPoleMarkers = true;

	private Color finalPatchColor = Color.red; 
	private Color finalPatchFillColor = new Color(255, 0, 255, 20); // translucent magenta
	private float finalPatchStrokeWidth = 1.5f;

	private Color polarFinalPatchColor = new Color(255, 120, 0, 255); // orange
	private Color polarFinalPatchFillColor = new Color(255, 120, 0, 35); // translucent orange
	private Color projectedPoleMarkerColor = new Color(255, 120, 0, 255);

	/** Polar-derived final phi-patch stroke width. */
	private float polarFinalPatchStrokeWidth = 3.0f;

	/** Draw only every Nth polar boundary edge; 1 draws all. */
	private int polarFinalPatchBoundaryStride = 1;

	/** Whether worst phi-parent area errors are highlighted. */
	private boolean showWorstPhiParentErrors = false;

	/** Number of worst phi-parent errors to highlight. */
	private int worstPhiParentErrorCount = 10;

	/** Worst phi-parent error highlight color. */
	private Color worstPhiParentErrorColor = new Color(255, 80, 0, 230);

	/** Worst phi-parent error highlight stroke width. */
	private float worstPhiParentErrorStrokeWidth = 2.25f;

	/** Whether phi children of worst phi-parent errors are highlighted. */
	private boolean showWorstPhiChildren = false;

	/** Number of worst phi-parent errors whose children are highlighted. */
	private int worstPhiChildrenParentCount = 2;

	/** Worst phi-child highlight color. */
	private Color worstPhiChildColor = new Color(0, 80, 255, 230);

	/** Worst phi-child highlight stroke width. */
	private float worstPhiChildStrokeWidth = 2.0f;

	/**
	 * Creates the Mosaic 2D view.
	 *
	 * @param model the shared Mosaic model
	 */
	public MosaicView2D(MosaicModel model) {
		super(PropertyUtils.TITLE, "Mosaic 2D", PropertyUtils.FRACTION, 0.8, PropertyUtils.ASPECT, 1.4,
				PropertyUtils.TOOLBARBITS, (ToolBits.MAPTOOLS | ToolBits.ZOOMTOOLS) & ~ToolBits.STATUS);

		if (model == null) {
			throw new IllegalArgumentException("model must not be null.");
		}

		this.model = model;

		/*
		 * These calls are intentionally made after super(...) returns, avoiding the
		 * superclass-constructor override/lifecycle problem.
		 */
		setMapControlPanel(new MosaicMapControlPanel(this));
		addCustomSidePanelComponent(new MosaicMapModePanel(model, this));

		setProjection(new ArchimedesLambertCylindricalProjection(getCurrentMapTheme()));

		model.addModelChangedListener(event -> {
			System.out.println("Model changed: " + event.getType());

			switch (event.getType()) {
			case GRID_SPEC_CHANGED, MODEL_RESET, DISPLAY_OPTIONS_CHANGED, SELECTION_CHANGED, INTERSECTING_CELLS_CHANGED,
					ALGORITHM_RESULT_CHANGED, ALGORITHM_OPTIONS_CHANGED, PREPATCHES_CHANGED, THETA_PATCHES_CHANGED,
					PATCHES_CHANGED, MONTE_CARLO_CHANGED, MONTE_CARLO_CLEARED ->
				gridChange();
			default -> {
				// No redraw needed.
			}
			}
		});
	}

	// Redraw when the grid changes or options change
	private void gridChange() {
		System.out.println("Grid spec changed ");
		refresh();
	}

	// Override to draw custom Mosaic content on the map.
	@Override
	protected void drawCustomMapContent(Graphics2D g, IContainer container) {

		drawMonteCarloPoints(g, container);

		if (showSphericalGridLines) {
			// Draw phi lines (longitudes)
			drawPhiLines(g, container);

			// Draw theta lines (latitudes, sort of)
			drawThetaLines(g, container);
		}
		// Draw prepatch boundaries
		drawPrepatches(g, container);

		// Draw theta-patch boundaries
		drawThetaPatches(g, container);

		// Draw final phi-patch boundaries and optional translucent fills
		drawFinalPatches(g, container);

		// Mark projected pole locations when polar final patches are being inspected
		drawProjectedPoleMarkers(g, container);

		// Draw worst phi-parent area errors last so they sit on top
		drawWorstPhiParentErrors(g, container);

		// Draw phi children of the worst phi-parent area errors last
		drawWorstPhiChildren(g, container);
	}

	/**
	 * Sets whether the spherical grid guide lines are visible.
	 *
	 * @param visible true to show spherical grid guide lines
	 */
	public void setSphericalGridLinesVisible(boolean visible) {
		showSphericalGridLines = visible;
		refresh();
	}

	/**
	 * Checks whether the spherical grid guide lines are visible.
	 *
	 * @return true if visible
	 */
	public boolean isSphericalGridLinesVisible() {
		return showSphericalGridLines;
	}

	/**
	 * Draws highlighted final phi-patch children for the theta parents with the
	 * worst phi-splice area closure errors.
	 *
	 * @param g         graphics context
	 * @param container map container
	 */
	private void drawWorstPhiChildren(Graphics2D g, IContainer container) {
		if (!showWorstPhiChildren) {
			return;
		}

		MosaicAlgorithmResult result = model.getAlgorithmResult();

		if (result == null || result.getPhiParentAreaDiagnostics() == null
				|| result.getPhiParentAreaDiagnostics().parentErrors().isEmpty() || model.getPhiPatchCount() == 0) {
			return;
		}

		IMapProjection projection = getProjection();

		Color oldColor = g.getColor();
		Stroke oldStroke = g.getStroke();

		g.setColor(worstPhiChildColor);
		g.setStroke(new BasicStroke(worstPhiChildStrokeWidth));

		int count = Math.max(1, worstPhiChildrenParentCount);

		for (PhiParentAreaError error : result.getPhiParentAreaDiagnostics().worstErrors(count)) {

			drawPhiChildrenForParentError(g, container, projection, error);
		}

		g.setColor(oldColor);
		g.setStroke(oldStroke);
	}

	/**
	 * Draws all final phi-patch children corresponding to one phi-parent area
	 * diagnostic error.
	 *
	 * @param g          graphics context
	 * @param container  map container
	 * @param projection active projection
	 * @param error      parent area error
	 */
	private void drawPhiChildrenForParentError(Graphics2D g, IContainer container, IMapProjection projection,
			PhiParentAreaError error) {

		if (error == null || error.key() == null) {
			return;
		}

		for (PhiPatch patch : model.getPhiPatches()) {
			if (isChildOfPhiParentError(patch, error)) {
				drawFinalPatch(g, container, projection, patch, 1, false);
			}
		}
	}

	/**
	 * Draws visible projected pole markers when polar final patches are being
	 * inspected.
	 * <p>
	 * This is a visualization aid only. It helps explain the apparent "hole" or
	 * crescent that can appear in orthographic mode when many patch boundaries
	 * collapse toward the projected pole.
	 * </p>
	 *
	 * @param g         graphics context
	 * @param container map container
	 */
	private void drawProjectedPoleMarkers(Graphics2D g, IContainer container) {
		if (!showProjectedPoleMarkers || !showPolarFinalPatches) {
			return;
		}

		IMapProjection projection = getProjection();
		
		// Only draw pole markers for orthographic projections, where the poles are projected to points. 
		// For other projection types, pole markers would be less meaningful 
		String projectionName = projection.getClass().getSimpleName().toLowerCase();

		if (!projectionName.contains("orthographic")) {
		    return;
		}

		Color oldColor = g.getColor();
		Stroke oldStroke = g.getStroke();

		g.setColor(projectedPoleMarkerColor);
		g.setStroke(new BasicStroke(2.0f));

		drawProjectedPoleMarker(g, container, projection, Math.PI / 2.0, "N");
		drawProjectedPoleMarker(g, container, projection, -Math.PI / 2.0, "S");

		g.setColor(oldColor);
		g.setStroke(oldStroke);
	}

	/**
	 * Draws one projected pole marker.
	 *
	 * @param g          graphics context
	 * @param container  map container
	 * @param projection active projection
	 * @param latitude   pole latitude
	 * @param label      marker label
	 */
	private void drawProjectedPoleMarker(Graphics2D g, IContainer container, IMapProjection projection, double latitude,
			String label) {

		Point2D.Double latLon = new Point2D.Double(0.0, latitude);
		Point2D.Double xy = new Point2D.Double();
		Point screen = new Point();

		projection.latLonToXY(latLon, xy);

		if (!Double.isFinite(xy.x) || !Double.isFinite(xy.y) || !projection.isPointOnMap(xy)) {
			return;
		}

		container.worldToLocal(screen, xy);

		int r = 7;

		g.drawOval(screen.x - r, screen.y - r, 2 * r, 2 * r);
		g.drawLine(screen.x - r - 3, screen.y, screen.x + r + 3, screen.y);
		g.drawLine(screen.x, screen.y - r - 3, screen.x, screen.y + r + 3);

		g.drawString(label, screen.x + r + 5, screen.y - r - 3);
	}

	/**
	 * Draws highlighted boundaries for the theta parents with the worst phi-splice
	 * area closure errors.
	 *
	 * @param g         graphics context
	 * @param container map container
	 */
	private void drawWorstPhiParentErrors(Graphics2D g, IContainer container) {
		if (!showWorstPhiParentErrors) {
			return;
		}

		MosaicAlgorithmResult result = model.getAlgorithmResult();

		if (result == null || result.getPhiParentAreaDiagnostics() == null
				|| result.getPhiParentAreaDiagnostics().parentErrors().isEmpty() || model.getThetaPatchCount() == 0) {
			return;
		}

		IMapProjection projection = getProjection();

		Color oldColor = g.getColor();
		Stroke oldStroke = g.getStroke();

		g.setColor(worstPhiParentErrorColor);
		g.setStroke(new BasicStroke(worstPhiParentErrorStrokeWidth));

		int count = Math.max(1, worstPhiParentErrorCount);

		for (PhiParentAreaError error : result.getPhiParentAreaDiagnostics().worstErrors(count)) {

			ThetaPatch thetaPatch = findThetaPatchForPhiParentError(error);

			if (thetaPatch != null) {
				drawThetaPatch(g, container, projection, thetaPatch);
			}
		}

		g.setColor(oldColor);
		g.setStroke(oldStroke);
	}

	/**
	 * Checks whether a final phi patch is a child of the theta parent identified by
	 * a phi-parent area error.
	 *
	 * @param patch final phi patch
	 * @param error phi-parent area error
	 * @return true if the patch is one of the children of the error parent
	 */
	private static boolean isChildOfPhiParentError(PhiPatch patch, PhiParentAreaError error) {

		if (patch == null || error == null || error.key() == null) {
			return false;
		}

		if (patch.ntheta() != error.key().ntheta()) {
			return false;
		}

		return patch.parentCellId().equals(error.key().cellId());
	}

	/**
	 * Finds the theta patch corresponding to a phi-parent area error.
	 *
	 * @param error phi-parent area error
	 * @return matching theta patch, or {@code null}
	 */
	private ThetaPatch findThetaPatchForPhiParentError(PhiParentAreaError error) {
		if (error == null || error.key() == null) {
			return null;
		}

		for (ThetaPatch thetaPatch : model.getThetaPatches()) {
			if (thetaPatch == null) {
				continue;
			}

			if (thetaPatch.ntheta() != error.key().ntheta()) {
				continue;
			}

			if (thetaPatch.parentCellId().equals(error.key().cellId())) {
				return thetaPatch;
			}
		}

		return null;
	}

	/**
	 * Draws final phi-patch boundaries and optional translucent fills.
	 *
	 * @param g         graphics context
	 * @param container map container
	 */
	private void drawFinalPatches(Graphics2D g, IContainer container) {
		if ((!showFinalPatches && !showPolarFinalPatches) || model.getPhiPatchCount() == 0) {
			return;
		}

		IMapProjection projection = getProjection();

		Color oldColor = g.getColor();
		Stroke oldStroke = g.getStroke();

		/*
		 * Draw fills first, then outlines. This makes filled patches useful as coverage
		 * diagnostics without hiding the boundary structure.
		 */

		if (showFinalPatches && fillFinalPatches) {
			g.setColor(finalPatchFillColor);

			for (PhiPatch patch : model.getPhiPatches()) {
				if (!isPolarFinalPatch(patch)) {
					drawFinalPatch(g, container, projection, patch, 1, true);
				}
			}
		}

		if (showPolarFinalPatches && fillPolarFinalPatches) {
			g.setColor(polarFinalPatchFillColor);

			int stride = Math.max(1, polarFinalPatchBoundaryStride);

			for (PhiPatch patch : model.getPhiPatches()) {
				if (isPolarFinalPatch(patch)) {
					drawFinalPatch(g, container, projection, patch, stride, true);
				}
			}
		}

		/*
		 * Draw non-polar outlines first, then polar outlines. Polar outlines sit on top
		 * because they are usually the object of inspection.
		 */
		if (showFinalPatches) {
			g.setColor(finalPatchColor);
			g.setStroke(new BasicStroke(finalPatchStrokeWidth));

			for (PhiPatch patch : model.getPhiPatches()) {
				if (!isPolarFinalPatch(patch)) {
					drawFinalPatch(g, container, projection, patch, 1, false);
				}
			}
		}

		if (showPolarFinalPatches) {
			g.setColor(polarFinalPatchColor);
			g.setStroke(new BasicStroke(polarFinalPatchStrokeWidth));

			int stride = Math.max(1, polarFinalPatchBoundaryStride);

			for (PhiPatch patch : model.getPhiPatches()) {
				if (isPolarFinalPatch(patch)) {
					drawFinalPatch(g, container, projection, patch, stride, false);
				}
			}
		}

		g.setColor(oldColor);
		g.setStroke(oldStroke);
	}

	/**
	 * Draws one final phi patch boundary or filled diagnostic shape.
	 *
	 * @param g              graphics context
	 * @param container      map container
	 * @param projection     active projection
	 * @param patch          final phi patch
	 * @param boundaryStride draw every Nth boundary point; 1 draws all
	 * @param fill           true to fill the visible path, false to draw the
	 *                       outline
	 */
	private void drawFinalPatch(Graphics2D g, IContainer container, IMapProjection projection, PhiPatch patch,
			int boundaryStride, boolean fill) {

		if (patch == null || patch.boundary().size() < 2) {
			return;
		}

		List<Vec3> boundary = FinalPatchBoundaryCanonicalizer.canonicalize(patch.boundary());

		if (boundary.size() < 2) {
			return;
		}

		int stride = Math.max(1, boundaryStride);

		Path2D.Double path = new Path2D.Double();

		Point2D.Double latLon = new Point2D.Double();
		Point2D.Double xy = new Point2D.Double();
		Point screen = new Point();

		boolean started = false;
		int segmentPointCount = 0;
		double previousLon = Double.NaN;

		int index = 0;

		for (Vec3 p : boundary) {
			/*
			 * Always draw the first point. After that, allow thinning. This is only a
			 * visualization optimization; it does not affect stored patch geometry.
			 */
			if (index > 0 && stride > 1 && (index % stride) != 0) {
				index++;
				continue;
			}

			index++;

			if (!gsmToLatLon(p, latLon)) {
				if (fill && started && segmentPointCount >= 3) {
					path.closePath();
				}

				started = false;
				segmentPointCount = 0;
				previousLon = Double.NaN;
				continue;
			}

			if (Double.isFinite(previousLon) && projection.crossesSeam(previousLon, latLon.x)) {

				if (fill && started && segmentPointCount >= 3) {
					path.closePath();
				}

				started = false;
				segmentPointCount = 0;
			}

			projection.latLonToXY(latLon, xy);

			if (!Double.isFinite(xy.x) || !Double.isFinite(xy.y) || !projection.isPointOnMap(xy)) {

				if (fill && started && segmentPointCount >= 3) {
					path.closePath();
				}

				started = false;
				segmentPointCount = 0;
				previousLon = latLon.x;
				continue;
			}

			container.worldToLocal(screen, xy);

			if (!started) {
				path.moveTo(screen.x, screen.y);
				started = true;
				segmentPointCount = 1;
			} else {
				path.lineTo(screen.x, screen.y);
				segmentPointCount++;
			}

			previousLon = latLon.x;
		}

		if (fill && started && segmentPointCount >= 3) {
			path.closePath();
		}

		if (fill) {
			g.fill(path);
		} else {
			g.draw(path);
		}
	}

	/**
	 * Sets whether polar-derived final phi-patch boundaries are visible.
	 *
	 * @param visible true to show polar final patches
	 */
	public void setPolarFinalPatchesVisible(boolean visible) {
		showPolarFinalPatches = visible;
		refresh();
	}

	/**
	 * Checks whether polar-derived final phi-patch boundaries are visible.
	 *
	 * @return true if polar final patches are visible
	 */
	public boolean isPolarFinalPatchesVisible() {
		return showPolarFinalPatches;
	}

	/**
	 * Sets the polar final phi-patch boundary color.
	 *
	 * @param color boundary color; ignored if null
	 */
	public void setPolarFinalPatchColor(Color color) {
		if (color != null) {
			polarFinalPatchColor = color;
			refresh();
		}
	}

	/**
	 * Sets the polar final phi-patch stroke width.
	 *
	 * @param width stroke width in pixels
	 */
	public void setPolarFinalPatchStrokeWidth(float width) {
		polarFinalPatchStrokeWidth = Math.max(0.25f, width);
		refresh();
	}

	/**
	 * Sets the drawing stride for polar final patch boundaries.
	 * <p>
	 * This is visualization-only. It does not alter stored patch geometry.
	 * </p>
	 *
	 * @param stride draw every Nth boundary point; values below 1 are treated as 1
	 */
	public void setPolarFinalPatchBoundaryStride(int stride) {
		polarFinalPatchBoundaryStride = Math.max(1, stride);
		refresh();
	}

	/**
	 * Gets the polar final patch boundary drawing stride.
	 *
	 * @return drawing stride
	 */
	public int getPolarFinalPatchBoundaryStride() {
		return polarFinalPatchBoundaryStride;
	}

	/**
	 * Checks whether a final phi patch came from a polar-derived theta patch.
	 *
	 * @param patch final phi patch
	 * @return true if the patch is polar-derived
	 */
	private static boolean isPolarFinalPatch(PhiPatch patch) {
		return patch != null && patch.parentPoleClassification() != null
				&& patch.parentPoleClassification().hasPoleInvolvement();
	}

	/**
	 * Draws theta-patch boundaries.
	 *
	 * @param g         graphics context
	 * @param container map container
	 */
	private void drawThetaPatches(Graphics2D g, IContainer container) {
		if (!showThetaPatches || model.getThetaPatchCount() == 0) {
			return;
		}

		IMapProjection projection = getProjection();

		Color oldColor = g.getColor();
		Stroke oldStroke = g.getStroke();

		g.setColor(thetaPatchColor);
		g.setStroke(new BasicStroke(thetaPatchStrokeWidth));

		for (ThetaPatch patch : model.getThetaPatches()) {
			if (!isPolarThetaPatch(patch)) {
				drawThetaPatch(g, container, projection, patch);
			}
		}

		g.setColor(oldColor);
		g.setStroke(oldStroke);
	}

	private static boolean isPolarThetaPatch(ThetaPatch patch) {
		return patch != null && patch.parentPoleClassification() != null
				&& patch.parentPoleClassification().hasPoleInvolvement();
	}

	/**
	 * Draws one theta patch boundary.
	 *
	 * @param g          graphics context
	 * @param container  map container
	 * @param projection active projection
	 * @param patch      theta patch
	 */
	private void drawThetaPatch(Graphics2D g, IContainer container, IMapProjection projection, ThetaPatch patch) {

		if (patch == null || patch.boundary().size() < 2) {
			return;
		}

		Path2D.Double path = new Path2D.Double();

		Point2D.Double latLon = new Point2D.Double();
		Point2D.Double xy = new Point2D.Double();
		Point screen = new Point();

		boolean started = false;
		double previousLon = Double.NaN;

		for (Vec3 p : patch.boundary()) {
			if (!gsmToLatLon(p, latLon)) {
				started = false;
				previousLon = Double.NaN;
				continue;
			}

			if (Double.isFinite(previousLon) && projection.crossesSeam(previousLon, latLon.x)) {
				started = false;
			}

			projection.latLonToXY(latLon, xy);

			if (!Double.isFinite(xy.x) || !Double.isFinite(xy.y) || !projection.isPointOnMap(xy)) {
				started = false;
				previousLon = latLon.x;
				continue;
			}

			container.worldToLocal(screen, xy);

			if (!started) {
				path.moveTo(screen.x, screen.y);
				started = true;
			} else {
				path.lineTo(screen.x, screen.y);
			}

			previousLon = latLon.x;
		}

		/*
		 * Close the visible path only if the last and first projected points are not
		 * separated by a projection seam. For cylindrical projections this avoids long
		 * false lines across the map.
		 */
		g.draw(path);
	}

	/**
	 * Draws ordinary prepatch boundary curves.
	 *
	 * @param g         graphics context
	 * @param container map container
	 */
	private void drawPrepatches(Graphics2D g, IContainer container) {
		if (!showPrepatches || model.getPrepatchCount() == 0) {
			return;
		}

		IMapProjection projection = getProjection();

		Color oldColor = g.getColor();
		Stroke oldStroke = g.getStroke();

		g.setColor(prepatchColor);
		g.setStroke(new BasicStroke(prepatchStrokeWidth));

		for (Prepatch prepatch : model.getPrepatches()) {
			drawPrepatch(g, container, projection, prepatch);
		}

		g.setColor(oldColor);
		g.setStroke(oldStroke);
	}

	/**
	 * Draws one prepatch.
	 *
	 * @param g          graphics context
	 * @param container  map container
	 * @param projection active projection
	 * @param prepatch   prepatch to draw
	 */
	private void drawPrepatch(Graphics2D g, IContainer container, IMapProjection projection, Prepatch prepatch) {

		if (prepatch == null || prepatch.curves().isEmpty()) {
			return;
		}

		for (GeneralCurve curve : prepatch.curves()) {
			drawGeneralCurve(g, container, projection, curve);
		}
	}

	/**
	 * Draws one GENERAL curve by sampling its 3D points on the sphere and
	 * projecting them through the active map projection.
	 *
	 * @param g          graphics context
	 * @param container  map container
	 * @param projection active projection
	 * @param curve      curve to draw
	 */
	private void drawGeneralCurve(Graphics2D g, IContainer container, IMapProjection projection, GeneralCurve curve) {

		List<Vec3> samples = curve.sample(prepatchCurveSamples);

		if (samples.size() < 2) {
			return;
		}

		Path2D.Double path = new Path2D.Double();

		Point2D.Double latLon = new Point2D.Double();
		Point2D.Double xy = new Point2D.Double();
		Point screen = new Point();

		boolean started = false;
		double previousLon = Double.NaN;

		for (Vec3 p : samples) {
			if (!gsmToLatLon(p, latLon)) {
				started = false;
				previousLon = Double.NaN;
				continue;
			}

			if (Double.isFinite(previousLon) && projection.crossesSeam(previousLon, latLon.x)) {
				started = false;
			}

			projection.latLonToXY(latLon, xy);

			if (!Double.isFinite(xy.x) || !Double.isFinite(xy.y) || !projection.isPointOnMap(xy)) {
				started = false;
				previousLon = latLon.x;
				continue;
			}

			container.worldToLocal(screen, xy);

			if (!started) {
				path.moveTo(screen.x, screen.y);
				started = true;
			} else {
				path.lineTo(screen.x, screen.y);
			}

			previousLon = latLon.x;
		}

		g.draw(path);
	}

	/**
	 * Converts a GSM Cartesian point on the spherical shell to the latitude-like
	 * and longitude-like coordinates expected by the map projection.
	 * <p>
	 * The map-projection convention is:
	 * </p>
	 *
	 * <pre>
	 * latLon.x = GSM phi
	 * latLon.y = GSM latitude = pi/2 - theta
	 * </pre>
	 *
	 * Equivalently:
	 *
	 * <pre>
	 * phi      = atan2(y, x)
	 * latitude = asin(z / r)
	 * </pre>
	 *
	 * @param p      GSM Cartesian point
	 * @param latLon output point, x=phi and y=GSM latitude, in radians
	 * @return true if conversion succeeded
	 */
	private static boolean gsmToLatLon(Vec3 p, Point2D.Double latLon) {
		if (p == null || latLon == null) {
			return false;
		}

		double r = Math.sqrt(p.x() * p.x() + p.y() * p.y() + p.z() * p.z());

		if (r <= 0.0 || !Double.isFinite(r)) {
			return false;
		}

		latLon.x = Math.atan2(p.y(), p.x());
		latLon.y = Math.asin(clamp(p.z() / r, -1.0, 1.0));

		return Double.isFinite(latLon.x) && Double.isFinite(latLon.y);
	}

	/**
	 * Clamps a value.
	 *
	 * @param value value
	 * @param min   minimum
	 * @param max   maximum
	 * @return clamped value
	 */
	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	// method to draw phi lines (longitudes) on the map.
	private void drawThetaLines(Graphics2D g, IContainer container) {
		SphericalGrid grid = model.getGridSpec().getSphericalGrid();
		Grid1D thetaGrid = grid.getThetaGrid();
		IMapProjection projection = getProjection();

		for (double theta : thetaGrid.getPoints()) {
			double latitude = PIOVER2 - theta; // Convert theta to latitude

			// Snap near-equator and near-pole values. The historical test grids use
			// rounded values such as 1.5708 instead of Math.PI / 2, which can create
			// near-antipode artifacts in azimuthal projections.
			if (Math.abs(latitude) < ANGULAR_SNAP_TOL) {
				latitude = 0.0;
			} else if (Math.abs(latitude - PIOVER2) < ANGULAR_SNAP_TOL) {
				latitude = PIOVER2;
			} else if (Math.abs(latitude + PIOVER2) < ANGULAR_SNAP_TOL) {
				latitude = -PIOVER2;
			}

			projection.drawLatitudeLine(g, container, latitude);
		}
	}

	/**
	 * Draws Monte Carlo points using their stored GSM angular coordinates and
	 * stored color-map values.
	 *
	 * @param g         graphics context
	 * @param container map container
	 */
	private void drawMonteCarloPoints(Graphics2D g, IContainer container) {
		if (!showMonteCarloPoints || model.getMonteCarloPointCount() == 0) {
			return;
		}

		IMapProjection projection = getProjection();

		Point2D.Double latLon = new Point2D.Double();
		Point2D.Double xy = new Point2D.Double();
		Point screen = new Point();

		int s = monteCarloDotSize;
		int half = s / 2;

		for (MonteCarloPoint point : model.getMonteCarloPoints()) {
			latLon.x = point.phi();
			latLon.y = Math.PI / 2.0 - point.theta();

			projection.latLonToXY(latLon, xy);

			if (!Double.isFinite(xy.x) || !Double.isFinite(xy.y) || !projection.isPointOnMap(xy)) {
				continue;
			}

			container.worldToLocal(screen, xy);

			Color color = monteCarloColorMap.colorAt(point.colorValue());
			g.setColor(color);
			g.fillRect(screen.x - half, screen.y - half, s, s);
		}
	}

	// method to draw phi lines (longitudes) on the map.
	private void drawPhiLines(Graphics2D g, IContainer container) {
		SphericalGrid grid = model.getGridSpec().getSphericalGrid();
		Grid1D phiGrid = grid.getPhiGrid();
		IMapProjection projection = getProjection();

		for (double phi : phiGrid.getPoints()) {
			// Convert phi to map coordinates and draw the line
			// This is a placeholder; actual implementation would depend on the projection
			// and map scale
			projection.drawLongitudeLine(g, container, phi);
		}

	}

	// Override to disable standard graticules (latitude/longitude lines) if
	// desired.
	@Override
	protected boolean useStandardGraticules() {
		return false; // Disable standard graticules to avoid cluttering the map
	}

	/**
	 * Override to set a custom side panel width suitable for Mosaic controls.
	 *
	 * @return the side panel width in pixels
	 */
	protected int getSidePanelWidth() {
		return 380;
	}

	/**
	 * Gets the shared Mosaic model.
	 *
	 * @return the Mosaic model
	 */
	public MosaicModel getMosaicModel() {
		return model;
	}

	/**
	 * Sets whether non-polar final phi patches are filled translucently.
	 *
	 * @param fill true to fill non-polar final patches
	 */
	public void setFinalPatchesFilled(boolean fill) {
		fillFinalPatches = fill;
		refresh();
	}

	/**
	 * Checks whether non-polar final phi patches are filled.
	 *
	 * @return true if filled
	 */
	public boolean isFinalPatchesFilled() {
		return fillFinalPatches;
	}

	/**
	 * Sets whether polar final phi patches are filled translucently.
	 *
	 * @param fill true to fill polar final patches
	 */
	public void setPolarFinalPatchesFilled(boolean fill) {
		fillPolarFinalPatches = fill;
		refresh();
	}

	/**
	 * Checks whether polar final phi patches are filled.
	 *
	 * @return true if filled
	 */
	public boolean isPolarFinalPatchesFilled() {
		return fillPolarFinalPatches;
	}

	/**
	 * Sets whether projected pole markers are drawn.
	 *
	 * @param visible true to show projected pole markers
	 */
	public void setProjectedPoleMarkersVisible(boolean visible) {
		showProjectedPoleMarkers = visible;
		refresh();
	}

	/**
	 * Checks whether projected pole markers are drawn.
	 *
	 * @return true if projected pole markers are visible
	 */
	public boolean isProjectedPoleMarkersVisible() {
		return showProjectedPoleMarkers;
	}

	/**
	 * Sets the translucent fill color for non-polar final patches.
	 *
	 * @param color fill color; ignored if null
	 */
	public void setFinalPatchFillColor(Color color) {
		if (color != null) {
			finalPatchFillColor = color;
			refresh();
		}
	}

	/**
	 * Sets the translucent fill color for polar final patches.
	 *
	 * @param color fill color; ignored if null
	 */
	public void setPolarFinalPatchFillColor(Color color) {
		if (color != null) {
			polarFinalPatchFillColor = color;
			refresh();
		}
	}

	/**
	 * Sets the projected pole marker color.
	 *
	 * @param color marker color; ignored if null
	 */
	public void setProjectedPoleMarkerColor(Color color) {
		if (color != null) {
			projectedPoleMarkerColor = color;
			refresh();
		}
	}

	@Override
	protected boolean includeShapeFileMenu() {
		return false;
	}

	/**
	 * Sets whether Monte Carlo points are visible.
	 *
	 * @param visible true to show Monte Carlo points
	 */
	public void setMonteCarloPointsVisible(boolean visible) {
		showMonteCarloPoints = visible;
		refresh();
	}

	/**
	 * Checks whether Monte Carlo points are visible.
	 *
	 * @return true if visible
	 */
	public boolean isMonteCarloPointsVisible() {
		return showMonteCarloPoints;
	}

	/**
	 * Sets the Monte Carlo point color map.
	 *
	 * @param colorMap color map; {@code null} means Viridis
	 */
	public void setMonteCarloColorMap(ScientificColorMap colorMap) {
		monteCarloColorMap = (colorMap == null) ? ScientificColorMap.VIRIDIS : colorMap;
		refresh();
	}

	/**
	 * Sets the Monte Carlo dot size.
	 *
	 * @param dotSize dot size in pixels
	 */
	public void setMonteCarloDotSize(int dotSize) {
		monteCarloDotSize = Math.max(1, dotSize);
		refresh();
	}

	/**
	 * Sets whether prepatch boundaries are visible.
	 *
	 * @param visible true to show prepatches
	 */
	public void setPrepatchesVisible(boolean visible) {
		showPrepatches = visible;
		refresh();
	}

	/**
	 * Checks whether prepatch boundaries are visible.
	 *
	 * @return true if prepatches are visible
	 */
	public boolean isPrepatchesVisible() {
		return showPrepatches;
	}

	/**
	 * Sets the number of sample points used per prepatch curve.
	 *
	 * @param samples sample count
	 */
	public void setPrepatchCurveSamples(int samples) {
		prepatchCurveSamples = Math.max(2, samples);
		refresh();
	}

	/**
	 * Sets whether the worst phi-parent area errors are highlighted.
	 *
	 * @param visible true to show the diagnostic overlay
	 */
	public void setWorstPhiParentErrorsVisible(boolean visible) {
		showWorstPhiParentErrors = visible;
		refresh();
	}

	/**
	 * Checks whether the worst phi-parent area errors are highlighted.
	 *
	 * @return true if visible
	 */
	public boolean isWorstPhiParentErrorsVisible() {
		return showWorstPhiParentErrors;
	}

	/**
	 * Sets the number of worst phi-parent errors to highlight.
	 *
	 * @param count number of errors to highlight
	 */
	public void setWorstPhiParentErrorCount(int count) {
		worstPhiParentErrorCount = Math.max(1, count);
		refresh();
	}

	/**
	 * Sets whether the phi children of the worst phi-parent area errors are
	 * highlighted.
	 *
	 * @param visible true to show the diagnostic overlay
	 */
	public void setWorstPhiChildrenVisible(boolean visible) {
		showWorstPhiChildren = visible;
		refresh();
	}

	/**
	 * Checks whether phi children of the worst phi-parent area errors are
	 * highlighted.
	 *
	 * @return true if visible
	 */
	public boolean isWorstPhiChildrenVisible() {
		return showWorstPhiChildren;
	}

	/**
	 * Sets the number of worst phi-parent errors whose children are highlighted.
	 *
	 * @param count number of worst parents
	 */
	public void setWorstPhiChildrenParentCount(int count) {
		worstPhiChildrenParentCount = Math.max(1, count);
		refresh();
	}

	/**
	 * Gets the number of worst phi-parent errors whose children are highlighted.
	 *
	 * @return parent count
	 */
	public int getWorstPhiChildrenParentCount() {
		return worstPhiChildrenParentCount;
	}

	/**
	 * Sets the worst-phi-child highlight color.
	 *
	 * @param color highlight color; ignored if null
	 */
	public void setWorstPhiChildColor(Color color) {
		if (color != null) {
			worstPhiChildColor = color;
			refresh();
		}
	}

	/**
	 * Sets the worst-phi-child highlight stroke width.
	 *
	 * @param width stroke width in pixels
	 */
	public void setWorstPhiChildStrokeWidth(float width) {
		worstPhiChildStrokeWidth = Math.max(0.25f, width);
		refresh();
	}

	/**
	 * Gets the number of worst phi-parent errors highlighted.
	 *
	 * @return error count
	 */
	public int getWorstPhiParentErrorCount() {
		return worstPhiParentErrorCount;
	}

	/**
	 * Sets the worst-phi-error highlight color.
	 *
	 * @param color highlight color; ignored if null
	 */
	public void setWorstPhiParentErrorColor(Color color) {
		if (color != null) {
			worstPhiParentErrorColor = color;
			refresh();
		}
	}

	/**
	 * Sets the worst-phi-error highlight stroke width.
	 *
	 * @param width stroke width in pixels
	 */
	public void setWorstPhiParentErrorStrokeWidth(float width) {
		worstPhiParentErrorStrokeWidth = Math.max(0.25f, width);
		refresh();
	}

	/**
	 * Sets the prepatch boundary color.
	 *
	 * @param color boundary color; ignored if null
	 */
	public void setPrepatchColor(Color color) {
		if (color != null) {
			prepatchColor = color;
			refresh();
		}
	}

	/**
	 * Sets the prepatch boundary stroke width.
	 *
	 * @param width stroke width in pixels
	 */
	public void setPrepatchStrokeWidth(float width) {
		prepatchStrokeWidth = Math.max(0.25f, width);
		refresh();
	}

	public void setThetaPatchesVisible(boolean visible) {
		showThetaPatches = visible;
		refresh();
	}

	public boolean isThetaPatchesVisible() {
		return showThetaPatches;
	}

	/**
	 * Sets whether non-polar final phi-patch boundaries are visible.
	 *
	 * @param visible true to show non-polar final patches
	 */
	public void setFinalPatchesVisible(boolean visible) {
		showFinalPatches = visible;
		refresh();
	}

	/**
	 * Checks whether non-polar final phi-patch boundaries are visible.
	 *
	 * @return true if non-polar final patches are visible
	 */
	public boolean isFinalPatchesVisible() {
		return showFinalPatches;
	}

	/**
	 * Sets the final phi-patch boundary color.
	 *
	 * @param color boundary color; ignored if null
	 */
	public void setFinalPatchColor(Color color) {
		if (color != null) {
			finalPatchColor = color;
			refresh();
		}
	}

	/**
	 * Sets the final phi-patch stroke width.
	 *
	 * @param width stroke width in pixels
	 */
	public void setFinalPatchStrokeWidth(float width) {
		finalPatchStrokeWidth = Math.max(0.25f, width);
		refresh();
	}

	/**
	 * Finds final phi patches matching the current grid indices.
	 *
	 * @param nx x-cell index
	 * @param ny y-cell index
	 * @param nz z-cell index
	 * @param ntheta theta-cell index
	 * @param nphi phi-cell index
	 * @return matching final patches
	 */
	private List<PhiPatch> findFinalPatchesAtIndices(
	        int nx, int ny, int nz, int ntheta, int nphi) {

	    if (model.getPhiPatchCount() == 0) {
	        return List.of();
	    }

	    CellId cellId = new CellId(nx, ny, nz);
	    ArrayList<PhiPatch> matches = new ArrayList<>();

	    for (PhiPatch patch : model.getPhiPatches()) {
	        if (!patch.parentCellId().equals(cellId)) {
	            continue;
	        }

	        if (patch.ntheta() != ntheta) {
	            continue;
	        }

	        /*
	         * Ordinary final patches match the current phi bin. Polar aggregate
	         * patches have nphi=-1, so allow them as a special match near the pole.
	         */
	        if (patch.nphi() == nphi || patch.isPolarAggregate()) {
	            matches.add(patch);
	        }
	    }

	    return matches;
	}
	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Appends the following strings (in order) to {@code feedbackStrings}:
	 * <ol>
	 * <li>Number of countries loaded.</li>
	 * <li>Number of cities loaded.</li>
	 * <li>Active projection name.</li>
	 * <li>Screen coordinates of the cursor.</li>
	 * <li>World (projection-space) coordinates.</li>
	 * <li>Latitude and longitude in degrees (only when cursor is on map).</li>
	 * <li>Picked country name and ISO code (only when cursor is on a country
	 * polygon).</li>
	 * <li>Tooltip text from any extra layers ({@link ShapeFeatureRenderer}) whose
	 * {@link IPickable#pick} returns a non-null result. Each layer contributes at
	 * most one string; all hit layers are reported.</li>
	 * <li>Picked city name and population (only when cursor is near a city
	 * dot).</li>
	 * </ol>
	 */
	@Override
	public void getFeedbackStrings(IContainer container, Point pp, Point2D.Double wp, List<String> feedbackStrings) {

		IMapProjection projection = getProjection();

		if (projection.isPointOnMap(wp)) {
			projection.latLonFromXY(latLon, wp);
			double gsmTheta = 90 - Math.toDegrees(latLon.y);
			double gsmPhi = Math.toDegrees(latLon.x);

			double r = model.getGridSpec().getSphericalGrid().getRadius();
			double sinTheta = Math.sin(Math.toRadians(gsmTheta));
			double x = r * sinTheta * Math.cos(Math.toRadians(gsmPhi));
			double y = r * sinTheta * Math.sin(Math.toRadians(gsmPhi));
			double z = r * Math.cos(Math.toRadians(gsmTheta));

			String polarStr = String.format("(r, %s, %s) = (%.2fRe, %.2f%s, %.2f%s)", UnicodeUtils.SMALL_THETA,
					UnicodeUtils.SMALL_PHI, r, gsmTheta, DEG, gsmPhi, DEG);

			String carStr = String.format("(x, y, z) = (%.2fRe, %.2fRe, %.2fRe)", x, y, z);

			feedbackStrings.add(polarStr);
			feedbackStrings.add(carStr);

			model.getGridSpec().getPatchIndices(Math.toRadians(gsmTheta), Math.toRadians(gsmPhi), r, indexArray);
			feedbackStrings.add(String.format("nx=%d, ny=%d" + ", nz=%d, %s=%d, %s=%d", indexArray[0], indexArray[1],
					indexArray[2], NTHETA, indexArray[3], NPHI, indexArray[4]));
			
			List<PhiPatch> finalMatches = findFinalPatchesAtIndices(
			        indexArray[0],
			        indexArray[1],
			        indexArray[2],
			        indexArray[3],
			        indexArray[4]);

			if (!finalMatches.isEmpty()) {
			    if (finalMatches.size() == 1) {
			        PhiPatch patch = finalMatches.get(0);

			        String nphiText = patch.isPolarAggregate()
			                ? "aggregate"
			                : Integer.toString(patch.nphi());

			        feedbackStrings.add(String.format(
			                "Patch: cell=%s, %s=%d, %s=%s, A_norm=%.3e, pts=%d%s",
			                patch.parentCellId(),
			                NTHETA,
			                patch.ntheta(),
			                NPHI,
			                nphiText,
			                patch.normalizedArea(),
			                patch.boundaryPointCount(),
			                patch.isPolarAggregate() ? ", polar aggregate" : ""));			    } else {
			        feedbackStrings.add(String.format(
			                "Final patches here: %d", finalMatches.size()));

			        int limit = Math.min(3, finalMatches.size());

			        for (int i = 0; i < limit; i++) {
			            PhiPatch patch = finalMatches.get(i);

			            feedbackStrings.add(String.format(
			                    "  cell=%s, %s=%d, %s=%d, A_norm=%.3e%s",
			                    patch.parentCellId(),
			                    NTHETA,
			                    patch.ntheta(),
			                    NPHI,
			                    patch.nphi(),
			                    patch.normalizedArea(),
			                    patch.isPolarAggregate() ? ", polar" : ""));
			        }

			        if (finalMatches.size() > limit) {
			            feedbackStrings.add(String.format(
			                    "  ... %,d more", finalMatches.size() - limit));
			        }
			    }
			}

			MosaicAlgorithmResult result = model.getAlgorithmResult();
			if (result != null) {
				ArrayList<String> resStr = result.getResultStrings();
				for (String s : resStr) {
					feedbackStrings.add(s);
				}
			}

		}
	}

}