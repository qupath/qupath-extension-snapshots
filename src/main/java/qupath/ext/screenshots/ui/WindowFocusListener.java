package qupath.ext.screenshots.ui;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.stage.Window;

import java.util.function.Predicate;

public class WindowFocusListener {

    private final Predicate<Window> filter;

    private final SimpleObjectProperty<Window> focused = new SimpleObjectProperty<>();

    public WindowFocusListener(Predicate<Window> filter) {
        this.filter = filter == null ? w -> true : filter;
        Window.getWindows().addListener(this::handleWindowChange);
        for (var window : Window.getWindows()) {
            maybeAddListener(window);
            if (window.isFocused() && this.filter.test(window)) {
                focused.set(window);
            }
        }
    }

    public boolean isFocused(Window window) {
        return filter.test(window);
    }

    public ReadOnlyObjectProperty<Window> focusedWindow() {
        return focused;
    }


    private void handleWindowChange(ListChangeListener.Change<? extends Window> change) {
        while (change.next()) {
            if (change.wasAdded()) {
                for (var window : change.getAddedSubList()) {
                    maybeAddListener(window);
                }
            }
        }
    }

    private void maybeAddListener(Window window) {
        if (!filter.test(window))
            return;
        window.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal)
                focused.set(window);
        });
    }

}
