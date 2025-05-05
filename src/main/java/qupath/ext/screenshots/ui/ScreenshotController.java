package qupath.ext.screenshots.ui;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.dialogs.FileChoosers;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.writers.ImageWriterTools;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import java.io.File;
import java.io.IOException;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Controller for UI pane contained in screenshot-controller.fxml
 */
public class ScreenshotController extends BorderPane {

    private static final Logger logger = LoggerFactory.getLogger(ScreenshotController.class);

    private static final ResourceBundle resources = ResourceBundle.getBundle("qupath.ext.screenshots.ui.strings");

    @FXML
    private TextField tfDirectory;

    @FXML
    private TextField tfName;

    @FXML
    private CheckBox cbUniqueName;

    @FXML
    private ComboBox<String> comboWindow;

    @FXML
    private Spinner<Integer> spinnerDelay;

    @FXML
    private Button btnCapture;

    /**
     * Create a new instance of the interface controller.
     * @return a new instance of the interface controller
     * @throws IOException If reading the extension FXML files fails.
     */
    public static ScreenshotController createInstance() throws IOException {
        return new ScreenshotController();
    }

    /**
     * This method reads an FXML file. These are markup files containing the structure of a UI element.
     * <p>
     * Fields in this class tagged with <code>@FXML</code> correspond to UI elements, and methods tagged with <code>@FXML</code> are methods triggered by actions on the UI (e.g., mouse clicks).
     * <p>
     * We consider the use of FXML to be "best practice" for UI creation, as it separates logic from layout and enables easier use of CSS. However, it is not mandatory, and you could instead define the layout of the UI using code.
     * @throws IOException If the FXML can't be read successfully.
     */
    private ScreenshotController() throws IOException {
        var url = ScreenshotController.class.getResource("screenshot-controller.fxml");
        FXMLLoader loader = new FXMLLoader(url, resources);
        loader.setRoot(this);
        loader.setController(this);
        loader.load();
        init();
    }

    private void init() {
        btnCapture.disableProperty().bind(tfDirectory.textProperty().isEmpty()
                .or(tfName.textProperty().isEmpty()));
    }

    @FXML
    private void promptForDirectory() {
        File dirCurrent = null;
        var text = tfDirectory.textProperty().getValueSafe();
        if (!text.isBlank()) {
            dirCurrent = new File(text);
        }
        var dir = FileChoosers.promptForDirectory(
                getScene().getWindow(),
                "Choose directory",
                dirCurrent
        );
        if (dir != null) {
            tfDirectory.setText(dir.toString());
        }
    }


    @FXML
    private void captureSnapshot() {
        var file = new File(tfDirectory.getText(), tfName.getText());
        Integer delay = spinnerDelay.getValue();
        delay = 2;
        if (delay != null && delay > 0) {
            CompletableFuture.delayedExecutor(delay, TimeUnit.SECONDS, Platform::runLater)
                    .execute(() -> snapshotFocusedWindow(file));
        } else {
            snapshotFocusedWindow(file);
        }
    }

    private void snapshotFocusedWindow(File file) {
        var win = Window.getWindows().filtered(Window::isFocused).stream().findFirst().orElse(null);
        var currentWin = getScene().getWindow();
        if (win != null && win != currentWin) {
            double opacity = currentWin.getOpacity();
            try {
                currentWin.setOpacity(0);
                var image = win.getScene().snapshot(null);
                var img = SwingFXUtils.fromFXImage(image, null);
                ImageWriterTools.writeImage(img, file.getAbsolutePath());
                Dialogs.showInfoNotification("Screenshot", "Written to " + file.getAbsolutePath());
            } catch (IOException e) {
                Dialogs.showErrorMessage("Screenshot error", "Unable to write to " + file.getAbsolutePath());
                logger.error(e.getMessage(), e);
            } finally {
                currentWin.setOpacity(opacity);
            }
        }
    }

}
