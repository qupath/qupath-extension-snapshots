package qupath.ext.screenshots.ui;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.stage.Window;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Helper class to track which window was last in focus, optionally applying a predicate to exclude windows that
 * should be ignored.
 */
class WindowFocusListener {

    private final Predicate<Window> filter;

    private final SimpleObjectProperty<Window> focused = new SimpleObjectProperty<>();

    private final Map<Window, FocusListener> listeners = new WeakHashMap<>();

    /**
     * Create a new window listener.
     * @param filter optional predicate to identify which windows to include
     */
    public WindowFocusListener(Predicate<Window> filter) {
        this.filter = filter == null ? w -> true : filter;
        Window.getWindows().addListener(this::handleWindowListChange);
        for (var window : Window.getWindows()) {
            maybeAddListener(window);
            if (window.isFocused() && this.filter.test(window)) {
                focused.set(window);
            }
        }
    }

    /**
     * Get the last known focused window.
     * Note that this does not <i>necessarily</i> mean that the window is currently in focus.
     * If a window that has been excluded by the predicate is in focus, then this may return the window that was
     * in focus most recently.
     * @return a read-only property giving the last known focused window
     */
    public ReadOnlyObjectProperty<Window> focusedWindow() {
        return focused;
    }


    private void handleWindowListChange(ListChangeListener.Change<? extends Window> change) {
        while (change.next()) {
            if (change.wasAdded()) {
                for (var window : change.getAddedSubList()) {
                    maybeAddListener(window);
                }
            }
            if (change.wasRemoved()) {
                for (var window : change.getRemoved()) {
                    var listener = listeners.remove(window);
                    if (listener != null)
                        window.focusedProperty().removeListener(listener);
                }
            }
        }
    }

    private synchronized void maybeAddListener(Window window) {
        if (!filter.test(window) || listeners.containsKey(window))
            return;
        var listener = new FocusListener(window);
        window.focusedProperty().addListener(listener);
        listeners.put(window, listener);
    }

    class FocusListener implements ChangeListener<Boolean> {

        private final Window window;

        private FocusListener(Window window) {
            this.window = window;
        }

        @Override
        public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
            if (newValue)
                focused.set(window);
        }
    }

}
