package edu.cnu.mdi.mosaic.app;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import edu.cnu.mdi.app.BaseMDIApplication;
import edu.cnu.mdi.log.Log;
import edu.cnu.mdi.mosaic.algorithm.MosaicAlgorithm;
import edu.cnu.mdi.mosaic.algorithm.MosaicAlgorithmController;
import edu.cnu.mdi.mosaic.dialog.AlgorithmOptionsDialog;
import edu.cnu.mdi.mosaic.dialog.GridSetupDialog;
import edu.cnu.mdi.mosaic.dialog.MonteCarloDialog;
import edu.cnu.mdi.mosaic.map.MosaicView2D;
import edu.cnu.mdi.mosaic.model.ModelChangedEvent;
import edu.cnu.mdi.mosaic.model.ModelChangedListener;
import edu.cnu.mdi.mosaic.model.MosaicGridSpec;
import edu.cnu.mdi.mosaic.model.MosaicModel;
import edu.cnu.mdi.ui.colors.X11Colors;
import edu.cnu.mdi.ui.menu.MenuManager;
import edu.cnu.mdi.util.PropertyUtils;
import edu.cnu.mdi.view.LogView;
import edu.cnu.mdi.view.VirtualView;

/**
 * Main application class for Mosaic.
 * <p>
 * Mosaic computes and visualizes the exact intersection of a rectangular
 * Cartesian grid with an enclosed spherical grid. The application uses MDI for
 * the desktop/view framework and MDI-3D for true spherical visualization.
 * </p>
 */
@SuppressWarnings("serial")
public class MosaicApp extends BaseMDIApplication {

	/** Singleton instance of the Mosaic application. */
	private static MosaicApp INSTANCE;
	
	/** Main Mosaic 2D map view. */
	private MosaicView2D mosaicView2D;

	/** the log view */
	private LogView logView;
	
	/** Controller for running the exact Mosaic algorithm. */
	private MosaicAlgorithmController algorithmController;

	/** the main data model for Mosaic with the grid information */
	private final static MosaicModel mosaicModel;
	static {
	    mosaicModel = new MosaicModel();
	}

	/**
	 * Creates the Mosaic application.
	 */
	private MosaicApp(Object... keyVals) {
		super(keyVals);
	    algorithmController = new MosaicAlgorithmController(mosaicModel);
		modifyMenus();
	}

	/**
	 * Returns the singleton instance of the Mosaic application.
	 *
	 * @return the Mosaic application instance
	 */
	public static MosaicApp getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new MosaicApp(PropertyUtils.TITLE, "Mosaic", PropertyUtils.CONSOLELOG, true,
					PropertyUtils.BACKGROUND, X11Colors.getX11Color("dark green"), PropertyUtils.FRACTION, 0.8);
		}
		return INSTANCE;
	}

	@Override
	protected int getVirtualDesktopColumns() {
		return 3;
	} // opts in; 0 = disabled

	@Override
	protected void addInitialViews() {
	    mosaicView2D = new MosaicView2D(mosaicModel);

		// Log view is useful but not always visible.
		logView = new LogView();
		logView.setVisible(false);

	    mosaicModel.addModelChangedListener(event -> {
	        String message = event.getMessage();
	        if (message != null && !message.isBlank()) {
	            System.out.println("[Mosaic] " + message);
	        }

	        if (event.getType() == ModelChangedEvent.Type.GRID_SPEC_CHANGED) {
	            Log.getInstance().config(mosaicModel.getGridSpec().summary());
	        }
	    });

	}

	// Add menu items for Mosaic-specific commands, such as grid setup. This is called from the constructor after the base
	private void modifyMenus() {
		JMenuItem gridItem = new JMenuItem("Grid Spec...");
		gridItem.addActionListener(e -> gridDialog());

		JMenu fileMenu = MenuManager.getInstance().getFileMenu();
		int quitIndex = fileMenu.getItemCount() - 1;

		// Insert the new item just above the separator before Quit.
		fileMenu.insert(gridItem, quitIndex);

		// Insert another separator above Quit, so the new command is grouped
		// separately.
		fileMenu.insertSeparator(quitIndex + 1);
		
		
		addMonteCarloMenu();
		addAlgorithmMenu();
	}
	
	private void addMonteCarloMenu() {
		JMenu mcMenu = new JMenu("Monte Carlo");
		getJMenuBar().add(mcMenu);

		JMenuItem generateMonteCarloItem = new JMenuItem("Generate Monte Carlo...");
		generateMonteCarloItem.addActionListener(e -> MonteCarloDialog.showDialog(this, mosaicModel));
		mcMenu.add(generateMonteCarloItem);

		JMenuItem clearMonteCarloItem = new JMenuItem("Clear Monte Carlo");
		clearMonteCarloItem.addActionListener(e -> mosaicModel.clearMonteCarloPoints());
		mcMenu.add(clearMonteCarloItem);
	}
	
	private void addAlgorithmMenu() {
		JMenu algorithmMenu = new JMenu("Algorithm");
		getJMenuBar().add(algorithmMenu);
		
		JMenuItem optionsItem = new JMenuItem("Algorithm Options...");
		optionsItem.addActionListener(e ->
		        AlgorithmOptionsDialog.showDialog(this, mosaicModel));
		algorithmMenu.add(optionsItem);
		
		JMenuItem runAlgorithmItem = new JMenuItem("Run Algorithm...");
		runAlgorithmItem.addActionListener(e -> algorithmController.runAlgorithm());
		algorithmMenu.add(runAlgorithmItem);

		JMenuItem clearAlgorithmItem = new JMenuItem("Clear Algorithm Result");
		clearAlgorithmItem.addActionListener(e -> algorithmController.clearAlgorithmResult());
		algorithmMenu.add(clearAlgorithmItem);	;
	}

	/**
	 * Place the views in the virtual desktop in a reasonable default layout.
	 *
	 * <p>
	 * Note: this placement will be ignored if the user has a persisted
	 * layout/config.
	 * </p>
	 */
	@Override
	protected void defaultViewLayout() {
		virtualViewMove(mosaicView2D, 0, VirtualView.CENTER);
		virtualViewMove(logView, 2, VirtualView.UPPERLEFT);
	}

	// Show the grid setup dialog and update the model if the user accepts a new
	private void gridDialog() {
		MosaicGridSpec spec = GridSetupDialog.showDialog(null, mosaicModel.getGridSpec());
		if (spec != null) {
			mosaicModel.setGridSpec(spec);
		}
	}

	/**
	 * Gets the shared Mosaic application model.
	 *
	 * @return the Mosaic model
	 */
	public MosaicModel getMosaicModel() {
	    return mosaicModel;
	}

	/**
	 * Adds a Mosaic model changed listener.
	 *
	 * @param listener the listener to add
	 */
	public void addModelChangedListener(ModelChangedListener listener) {
	    mosaicModel.addModelChangedListener(listener);
	}

	/**
	 * Removes a Mosaic model changed listener.
	 *
	 * @param listener the listener to remove
	 */
	public void removeModelChangedListener(ModelChangedListener listener) {
	    mosaicModel.removeModelChangedListener(listener);
	}

	/**
	 * Application entry point.
	 *
	 * @param args command-line arguments
	 */
	public static void main(String[] args) {
		BaseMDIApplication.launch(MosaicApp::getInstance);
	}
}