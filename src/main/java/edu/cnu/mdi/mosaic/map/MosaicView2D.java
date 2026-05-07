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
import edu.cnu.mdi.mosaic.geom.Vec3;
import edu.cnu.mdi.mosaic.grid.Grid1D;
import edu.cnu.mdi.mosaic.grid.SphericalGrid;
import edu.cnu.mdi.mosaic.mc.MonteCarloPoint;
import edu.cnu.mdi.mosaic.model.MosaicModel;
import edu.cnu.mdi.mosaic.patch.GeneralCurve;
import edu.cnu.mdi.mosaic.patch.Prepatch;
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

	// Reusable point for projection calculations to avoid unnecessary object creation.
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

	/**
	 * Creates the Mosaic 2D view.
	 *
	 * @param model the shared Mosaic model
	 */
	public MosaicView2D(MosaicModel model) {
		super(PropertyUtils.TITLE, "Mosaic 2D",
				PropertyUtils.FRACTION, 0.8,
				PropertyUtils.ASPECT, 1.3,
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
			case GRID_SPEC_CHANGED,
		     MODEL_RESET,
		     DISPLAY_OPTIONS_CHANGED,
		     SELECTION_CHANGED,
		     INTERSECTING_CELLS_CHANGED,
		     ALGORITHM_RESULT_CHANGED,
		     PREPATCHES_CHANGED,
		     THETA_PATCHES_CHANGED,
		     PATCHES_CHANGED,
		     MONTE_CARLO_CHANGED,
		     MONTE_CARLO_CLEARED -> gridChange();
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

		// Draw phi lines (longitudes)
		drawPhiLines(g, container);

		// Draw theta lines (latitudes, sort of)
		drawThetaLines(g, container);

		drawPrepatches(g, container);
	}

	/**
	 * Draws ordinary prepatch boundary curves.
	 *
	 * @param g graphics context
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
	 * @param g graphics context
	 * @param container map container
	 * @param projection active projection
	 * @param prepatch prepatch to draw
	 */
	private void drawPrepatch(Graphics2D g, IContainer container,
	        IMapProjection projection, Prepatch prepatch) {

	    if (prepatch == null || prepatch.curves().isEmpty()) {
	        return;
	    }

	    for (GeneralCurve curve : prepatch.curves()) {
	        drawGeneralCurve(g, container, projection, curve);
	    }
	}

	/**
	 * Draws one GENERAL curve by sampling its 3D points on the sphere and projecting
	 * them through the active map projection.
	 *
	 * @param g graphics context
	 * @param container map container
	 * @param projection active projection
	 * @param curve curve to draw
	 */
	private void drawGeneralCurve(Graphics2D g, IContainer container,
	        IMapProjection projection, GeneralCurve curve) {

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

	        if (Double.isFinite(previousLon)
	                && projection.crossesSeam(previousLon, latLon.x)) {
	            started = false;
	        }

	        projection.latLonToXY(latLon, xy);

	        if (!Double.isFinite(xy.x)
	                || !Double.isFinite(xy.y)
	                || !projection.isPointOnMap(xy)) {
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
	 * @param p GSM Cartesian point
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
	 * @param min minimum
	 * @param max maximum
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
	 * @param g graphics context
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
			// This is a placeholder; actual implementation would depend on the projection and map scale
			projection.drawLongitudeLine(g, container, phi);
		}

	}

	// Override to disable standard graticules (latitude/longitude lines) if desired.
	@Override
	protected boolean useStandardGraticules() {
	    return false; // Disable standard graticules to avoid cluttering the map
	}


	/**
	 * Gets the shared Mosaic model.
	 *
	 * @return the Mosaic model
	 */
	public MosaicModel getMosaicModel() {
		return model;
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


			feedbackStrings.add(String.format("r: %.2fRe", r));
	     	feedbackStrings.add(String.format("%s: %.2f%s", UnicodeUtils.SMALL_THETA, gsmTheta, DEG));
			feedbackStrings.add(String.format("%s: %.2f%s", UnicodeUtils.SMALL_PHI, gsmPhi, DEG));
			feedbackStrings.add(String.format("x: %.2fRe", x));
			feedbackStrings.add(String.format("y: %.2fRe", y));
			feedbackStrings.add(String.format("z: %.2fRe", z));

			model.getGridSpec().getPatchIndices(Math.toRadians(gsmTheta), Math.toRadians(gsmPhi), r, indexArray);
			feedbackStrings.add(String.format("nx=%d, ny=%d"
					+ ", nz=%d, %s=%d, %s=%d", indexArray[0], indexArray[1], indexArray[2], NTHETA, indexArray[3], NPHI, indexArray[4]));
			
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