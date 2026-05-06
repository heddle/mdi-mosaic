package edu.cnu.mdi.mosaic.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.SwingUtilities;

/**
 * Shared application model for Mosaic.
 * <p>
 * The model stores the active grid specification and broadcasts
 * Mosaic-specific change events to any interested views or controllers. The
 * model should be the shared center of the Mosaic application: dialogs update
 * it, algorithm runners update it, and views listen to it.
 * </p>
 */
public final class MosaicModel {

    /** Active grid specification. */
    private MosaicGridSpec gridSpec;

    /** Current selected object, such as a cell, prepatch, patch, or patch id. */
    private Object selectedObject;

    /** Current status message. */
    private String statusMessage = "";

    /** Registered model listeners. */
    private final List<ModelChangedListener> listeners = new ArrayList<>();

    /**
     * Creates a model using the default paper test grid.
     */
    public MosaicModel() {
        gridSpec = MosaicGridPresets.paperTestGrid();
        statusMessage = "Loaded default Mosaic grid: " + gridSpec.getName();
    }

    /**
     * Gets the active grid specification.
     *
     * @return the active grid specification
     */
    public MosaicGridSpec getGridSpec() {
        return gridSpec;
    }

    /**
     * Sets the active grid specification.
     * <p>
     * This should be called by the grid setup dialog after the user accepts a
     * new grid. Changing the grid specification invalidates any computed Mosaic
     * algorithm results, so future result fields should be cleared here.
     * </p>
     *
     * @param newGridSpec the new grid specification
     */
    public void setGridSpec(MosaicGridSpec newGridSpec) {
        Objects.requireNonNull(newGridSpec, "newGridSpec must not be null");

        MosaicGridSpec oldSpec = gridSpec;
        if (oldSpec == newGridSpec) {
            return;
        }

        gridSpec = newGridSpec;
        selectedObject = null;
        statusMessage = "Grid changed: " + newGridSpec.getName();

        fireModelChanged(ModelChangedEvent.Type.GRID_SPEC_CHANGED,
                oldSpec, newGridSpec, statusMessage);

        fireModelChanged(ModelChangedEvent.Type.SELECTION_CHANGED,
                null, null, "Selection cleared because the grid changed.");
    }

    /**
     * Resets the model to the default paper test grid.
     */
    public void resetToDefaultGrid() {
        MosaicGridSpec oldSpec = gridSpec;
        Object oldSelection = selectedObject;

        gridSpec = MosaicGridPresets.paperTestGrid();
        selectedObject = null;
        statusMessage = "Model reset to default paper test grid.";

        fireModelChanged(ModelChangedEvent.Type.MODEL_RESET,
                oldSpec, gridSpec, statusMessage);

        if (oldSelection != null) {
            fireModelChanged(ModelChangedEvent.Type.SELECTION_CHANGED,
                    oldSelection, null, "Selection cleared.");
        }
    }

    /**
     * Gets the currently selected object.
     *
     * @return the selected object, or {@code null}
     */
    public Object getSelectedObject() {
        return selectedObject;
    }

    /**
     * Sets the currently selected object.
     * <p>
     * The selected object can initially be informal. Later this can become a
     * stronger type, such as a selected cell, selected prepatch, selected patch,
     * or selected patch id.
     * </p>
     *
     * @param newSelection the new selected object, or {@code null}
     */
    public void setSelectedObject(Object newSelection) {
        Object oldSelection = selectedObject;

        if (oldSelection == newSelection) {
            return;
        }

        selectedObject = newSelection;

        fireModelChanged(ModelChangedEvent.Type.SELECTION_CHANGED,
                oldSelection, newSelection, "Selection changed.");
    }

    /**
     * Clears the current selection.
     */
    public void clearSelection() {
        setSelectedObject(null);
    }

    /**
     * Gets the current status message.
     *
     * @return the status message
     */
    public String getStatusMessage() {
        return statusMessage;
    }

    /**
     * Sets the current status message.
     *
     * @param message the new status message
     */
    public void setStatusMessage(String message) {
        String oldMessage = statusMessage;
        statusMessage = (message == null) ? "" : message;

        if (!Objects.equals(oldMessage, statusMessage)) {
            fireModelChanged(ModelChangedEvent.Type.STATUS_CHANGED,
                    oldMessage, statusMessage, statusMessage);
        }
    }

    /**
     * Signals that a Mosaic algorithm run has started.
     */
    public void algorithmStarted() {
        setStatusMessage("Mosaic algorithm started.");
        fireModelChanged(ModelChangedEvent.Type.ALGORITHM_STARTED,
                null, null, statusMessage);
    }

    /**
     * Signals that a Mosaic algorithm run completed.
     *
     * @param message completion message
     */
    public void algorithmCompleted(String message) {
        setStatusMessage(message == null ? "Mosaic algorithm completed." : message);
        fireModelChanged(ModelChangedEvent.Type.ALGORITHM_COMPLETED,
                null, null, statusMessage);
    }

    /**
     * Signals that a Mosaic algorithm run failed.
     *
     * @param throwable the exception or error that caused the failure
     */
    public void algorithmFailed(Throwable throwable) {
        String message = (throwable == null)
                ? "Mosaic algorithm failed."
                : "Mosaic algorithm failed: " + throwable.getMessage();

        setStatusMessage(message);
        fireModelChanged(ModelChangedEvent.Type.ALGORITHM_FAILED,
                null, throwable, message);
    }

    /**
     * Signals that intersecting cells changed.
     * <p>
     * This is a placeholder hook for the first algorithm step. Once a
     * {@code MosaicGridResult} class exists, this method can accept the new
     * list or result object.
     * </p>
     *
     * @param newValue the new intersecting-cell data, or {@code null}
     */
    public void intersectingCellsChanged(Object newValue) {
        fireModelChanged(ModelChangedEvent.Type.INTERSECTING_CELLS_CHANGED,
                null, newValue, "Intersecting cells changed.");
    }

    /**
     * Signals that prepatches changed.
     *
     * @param newValue the new prepatch data, or {@code null}
     */
    public void prepatchesChanged(Object newValue) {
        fireModelChanged(ModelChangedEvent.Type.PREPATCHES_CHANGED,
                null, newValue, "Prepatches changed.");
    }

    /**
     * Signals that theta patches changed.
     *
     * @param newValue the new theta-patch data, or {@code null}
     */
    public void thetaPatchesChanged(Object newValue) {
        fireModelChanged(ModelChangedEvent.Type.THETA_PATCHES_CHANGED,
                null, newValue, "Theta patches changed.");
    }

    /**
     * Signals that final Mosaic patches changed.
     *
     * @param newValue the new patch data, or {@code null}
     */
    public void patchesChanged(Object newValue) {
        fireModelChanged(ModelChangedEvent.Type.PATCHES_CHANGED,
                null, newValue, "Final Mosaic patches changed.");
    }

    /**
     * Signals that display options changed.
     * <p>
     * Use this when a view option changes but the grid and computed geometry do
     * not.
     * </p>
     *
     * @param message optional message
     */
    public void displayOptionsChanged(String message) {
        fireModelChanged(ModelChangedEvent.Type.DISPLAY_OPTIONS_CHANGED,
                null, null, message);
    }

    /**
     * Adds a model changed listener.
     *
     * @param listener the listener to add
     */
    public void addModelChangedListener(ModelChangedListener listener) {
        if (listener == null) {
            return;
        }

        synchronized (listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }
    }

    /**
     * Removes a model changed listener.
     *
     * @param listener the listener to remove
     */
    public void removeModelChangedListener(ModelChangedListener listener) {
        if (listener == null) {
            return;
        }

        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * Fires a model changed event.
     * <p>
     * Events are delivered on the Swing event dispatch thread. If this method is
     * called from a worker thread, delivery is posted back to the EDT. This keeps
     * MDI/Swing views safe when the Mosaic algorithm eventually runs in the
     * background.
     * </p>
     *
     * @param type the event type
     * @param oldValue optional old value
     * @param newValue optional new value
     * @param message optional message
     */
    public void fireModelChanged(ModelChangedEvent.Type type,
            Object oldValue, Object newValue, String message) {

        ModelChangedEvent event = new ModelChangedEvent(this, type,
                oldValue, newValue, message);

        if (SwingUtilities.isEventDispatchThread()) {
            notifyListeners(event);
        } else {
            SwingUtilities.invokeLater(() -> notifyListeners(event));
        }
    }

    /**
     * Notifies a stable snapshot of the listener list.
     *
     * @param event the event to deliver
     */
    private void notifyListeners(ModelChangedEvent event) {
        List<ModelChangedListener> snapshot;

        synchronized (listeners) {
            snapshot = new ArrayList<>(listeners);
        }

        for (ModelChangedListener listener : snapshot) {
            try {
                listener.modelChanged(event);
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
    }
}