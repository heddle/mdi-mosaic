package edu.cnu.mdi.mosaic.model;

/**
 * Listener notified when the shared Mosaic model changes.
 * <p>
 * This is intentionally small and domain-specific. MDI remains the application
 * framework; this listener mechanism only lets Mosaic views and controllers
 * react to changes in the Mosaic data model.
 * </p>
 */
@FunctionalInterface
public interface ModelChangedListener {

    /**
     * Called when the Mosaic model changes.
     *
     * @param event the model change event
     */
    void modelChanged(ModelChangedEvent event);
}