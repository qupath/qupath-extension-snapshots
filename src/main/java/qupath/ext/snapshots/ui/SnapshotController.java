package qupath.ext.snapshots.ui;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.layout.BorderPane;
import javafx.scene.transform.Scale;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.controlsfx.glyphfont.FontAwesome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.dialogs.FileChoosers;
import qupath.fx.utils.FXUtils;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.dialogs.ParameterPanelFX;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.images.writers.ImageWriterTools;
import qupath.lib.plugins.parameters.ParameterList;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import java.awt.AWTException;
import java.awt.Robot;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Controller for UI pane contained in snapshot-controller.fxml
 */
public class SnapshotController extends BorderPane {

    private static final Logger logger = LoggerFactory.getLogger(SnapshotController.class);

    private static final ResourceBundle resources = ResourceBundle.getBundle("qupath.ext.snapshots.ui.strings");

    private static final StringProperty pathProperty = PathPrefs.createPersistentPreference("ext.snapshots.path", "");
    private static final StringProperty nameProperty = PathPrefs.createPersistentPreference("ext.snapshots.name", "");
    private static final BooleanProperty copyProperty = PathPrefs.createPersistentPreference("ext.snapshots.copyToClipboard", true);
    private static final BooleanProperty uniqueNamesProperty = PathPrefs.createPersistentPreference("ext.snapshots.uniqueNames", true);
    private static final ObjectProperty<Format> formatProperty = PathPrefs.createPersistentPreference("ext.snapshots.format", Format.PNG, Format.class);

    private enum Format {
        PNG, JPEG_HIGH, JPEG_MEDIUM, JPEG_LOW;

        public String getFormatName() {
            if (this == PNG)
                return "PNG";
            else
                return "JPEG";
        }

        public String getExtension() {
            if (this == PNG)
                return ".png";
            else
                return ".jpg";
        }

        public float getJpegQuality() {
            return switch(this) {
                case JPEG_HIGH -> 0.9f;
                case JPEG_MEDIUM -> 0.75f;
                case JPEG_LOW -> 0.5f;
                default -> -1f;
            };
        }

        @Override
        public String toString() {
            return switch(this) {
                case PNG -> "PNG";
                case JPEG_HIGH -> "JPEG (high)";
                case JPEG_MEDIUM -> "JPEG (medium)";
                case JPEG_LOW -> "JPEG (low)";
            };
        }

    }

    @FXML
    private TextField tfDirectory;

    @FXML
    private TextField tfName;

    @FXML
    private CheckBox cbUniqueName;

    @FXML
    private CheckBox cbCopyToClipboard;

    @FXML
    private ComboBox<Format> comboFormat;

    @FXML
    private Spinner<Integer> spinnerDelay;

    @FXML
    private Spinner<Double> spinnerScale;

    @FXML
    private Button btnSnapshot;

    @FXML
    private Button btnScreenshot;

    @FXML
    private Button btnDirectory;

    @FXML
    private Button btnSize;

    @FXML
    private Label labelCurrentWindow;

    @FXML
    private ProgressBar progressDelay;

    private BooleanProperty processing = new SimpleBooleanProperty(false);

    private final ObjectProperty<Window> focusedWindow = new SimpleObjectProperty<>();

    private final ObservableValue<String> focusedWindowName = focusedWindow.flatMap(SnapshotController::getWindowName);

    private static ObservableValue<String> getWindowName(Window window) {
        if (window instanceof Stage) {
            return ((Stage) window).titleProperty();
        } else {
            return Bindings.createStringBinding(window::toString);
        }
    }

    /**
     * Create a new instance of the screenshot controller.
     * @return a new instance of the controller
     * @throws IOException If reading the extension FXML files fails.
     */
    public static SnapshotController createInstance() throws IOException {
        return new SnapshotController();
    }

    private SnapshotController() throws IOException {
        var url = SnapshotController.class.getResource("snapshot-controller.fxml");
        FXMLLoader loader = new FXMLLoader(url, resources);
        loader.setRoot(this);
        loader.setController(this);
        loader.load();
        init();
    }

    private void init() {
        btnScreenshot.disableProperty().bind(
                processing.or(
                    cbCopyToClipboard.selectedProperty().not().and(
                            tfDirectory.textProperty().isEmpty()
                                    .or(tfName.textProperty().isEmpty())
                    )
                ));
        btnSnapshot.disableProperty().bind(btnScreenshot.disableProperty());
        comboFormat.disableProperty().bind(btnScreenshot.disableProperty());
        cbUniqueName.disableProperty().bind(btnScreenshot.disableProperty());

        tfDirectory.textProperty().bindBidirectional(pathProperty);
        tfDirectory.disableProperty().bind(cbCopyToClipboard.selectedProperty());
        tfName.textProperty().bindBidirectional(nameProperty);
        tfName.disableProperty().bind(cbCopyToClipboard.selectedProperty());

        cbCopyToClipboard.selectedProperty().bindBidirectional(copyProperty);
        cbUniqueName.selectedProperty().bindBidirectional(uniqueNamesProperty);

        // Listen to changes in the focused window, while ignoring this window
        var listener = new WindowFocusListener(this::isFocusTrackedWindow);
        focusedWindow.bind(listener.focusedWindow());

        spinnerDelay.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100, 0));
        spinnerDelay.getValueFactory().setValue(0);
        FXUtils.resetSpinnerNullToPrevious(spinnerDelay);

        spinnerScale.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.25, 16, 1.0));
        spinnerScale.getValueFactory().setValue(1.0);
        FXUtils.resetSpinnerNullToPrevious(spinnerScale);

        comboFormat.getItems().setAll(Format.values());
        if (!comboFormat.getItems().contains(formatProperty.get()))
            formatProperty.setValue(Format.PNG);
        comboFormat.valueProperty().bindBidirectional(formatProperty);

        labelCurrentWindow.textProperty().bind(focusedWindowName);

        btnDirectory.setGraphic(IconFactory.createNode(FontAwesome.Glyph.FOLDER_OPEN_ALT, 12));
        btnDirectory.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

        btnSize.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            var win = focusedWindow.getValue();
            if (win instanceof Stage stage)
                return !stage.isResizable();
            else
                return true;
        }, focusedWindow));

        btnSnapshot.setGraphic(IconFactory.createNode(FontAwesome.Glyph.PICTURE_ALT));
        btnSnapshot.textProperty().bind(Bindings.createStringBinding(() -> {
            if (cbCopyToClipboard.isSelected())
                return resources.getString("button.snapshot.copy");
            else
                return resources.getString("button.snapshot.save");
        }, cbCopyToClipboard.selectedProperty()));

        btnScreenshot.setGraphic(IconFactory.createNode(FontAwesome.Glyph.CAMERA));
        btnScreenshot.setContentDisplay(ContentDisplay.RIGHT);
        btnScreenshot.textProperty().bind(Bindings.createStringBinding(() -> {
            if (cbCopyToClipboard.isSelected())
                return resources.getString("button.screenshot.copy");
            else
                return resources.getString("button.screenshot.save");
        }, cbCopyToClipboard.selectedProperty()));

        progressDelay.visibleProperty().bind(processing);
        progressDelay.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
    }

    // We only want to track stages, but not our window (or windows we own)
    private boolean isFocusTrackedWindow(Window window) {
        if (window instanceof PopupWindow)
            return false;
        var scene = getScene();
        if (scene != null) {
            var currentWindow = scene.getWindow();
            if (window instanceof Stage stage) {
                return window != currentWindow && stage.getOwner() != currentWindow;
            }
        }
        return window instanceof Stage;
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
    private void promptToSetSize() {
        var win = focusedWindow.getValue();
        if (win instanceof Stage stage) {
            var params = new ParameterList()
                    .addDoubleParameter("width", resources.getString("width"), stage.getWidth(), "px", resources.getString("width.description"))
                    .addDoubleParameter("height", resources.getString("height"), stage.getHeight(), "px", resources.getString("height.description"));
            var pane = new ParameterPanelFX(params).getPane();
            if (Dialogs.builder()
                    .title(resources.getString("size.label"))
                    .owner(getScene().getWindow())
                    .content(pane)
                    .buttons(ButtonType.APPLY, ButtonType.CANCEL)
                    .showAndWait()
                    .orElse(ButtonType.CANCEL) == ButtonType.APPLY) {
                double width = GeneralTools.clipValue(params.getDoubleParameterValue("width"), 1, 20_000);
                double height = GeneralTools.clipValue(params.getDoubleParameterValue("height"), 1, 20_000);
                stage.setWidth(width);
                stage.setHeight(height);
            }
        }
    }

    @FXML
    private void captureSnapshot() {
        doCapture(false);
    }

    @FXML
    private void captureScreenshot() {
        doCapture(true);
    }


    private void doCapture(boolean doScreenshot) {
        var file = cbCopyToClipboard.isSelected() ? null : new File(tfDirectory.getText(), tfName.getText());
        Integer delay = spinnerDelay.getValue();
        if (delay != null && delay > 0) {
            // Ideally we'd show progress... this is the lazy way
            processing.set(true);
            var window = getScene().getWindow();
            window.setOpacity(0.0);
            CompletableFuture.delayedExecutor(delay, TimeUnit.SECONDS, Platform::runLater)
                    .execute(() -> {
                        snapshotFocusedWindow(file, doScreenshot);
                        processing.set(false);
                        window.setOpacity(1.0);
                    });
        } else {
            snapshotFocusedWindow(file, doScreenshot);
        }
    }

    private void snapshotFocusedWindow(File file, boolean doScreenshot) {
        var win = focusedWindow.getValue();
        var currentWin = getScene().getWindow();
        if (win != null && win != currentWin) {
            try {
                // We need JavaFX image for clipboard or BufferedImage for saving
                // For simplicity, just generate both for now
                BufferedImage img = null;
                Image image = null;
                if (doScreenshot) {
                    var rect = new Rectangle2D.Double(
                            win.getX(),
                            win.getY(),
                            win.getWidth(),
                            win.getHeight()
                    );
                    // Need to use AWT Robot for correct colors on Mac
                    if (GeneralTools.isMac()) {
                        try {
                            img = new Robot().createScreenCapture(rect.getBounds());
                            image = SwingFXUtils.toFXImage(img, null);
                        } catch (AWTException e) {
                            logger.warn("Unable to capture screenshot using AWT - falling back to JavaFX (colors may differ)", e);
                        }
                    }
                    // Need to use JavaFX Robot for Windows & Linux
                    if (image == null) {
                        image = new javafx.scene.robot.Robot().getScreenCapture(null,
                                new javafx.geometry.Rectangle2D(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight()));
                        img = SwingFXUtils.fromFXImage(image, null);
                    }
                } else {
                    double scale = spinnerScale.getValue() == null ? 1 : spinnerScale.getValue();
                    if (scale == 1.0 || scale <= 0.0)
                        image = win.getScene().snapshot(null);
                    else {
                        var node = win.getScene().getRoot();
                        var params = new SnapshotParameters();
                        params.setTransform(new Scale(scale, scale));
                        image = node.snapshot(params, null);
                    }
                    img = SwingFXUtils.fromFXImage(image, null);
                }
                if (file == null) {
                    Clipboard.getSystemClipboard().setContent(Map.of(DataFormat.IMAGE, image));
                    return;
                }

                if (GeneralTools.getExtension(file).isPresent()) {
                    file = confirmFile(file);
                    ImageWriterTools.writeImage(img, file.getAbsolutePath());
                } else {
                    var format = comboFormat.getValue();
                    var quality = format.getJpegQuality();
                    file = new File(file.getParentFile(), file.getName() + format.getExtension());
                    file = confirmFile(file);
                    if (quality >= 0)
                        writeJpegWithQuality(img, file, quality);
                    else
                        ImageIO.write(img, format.getFormatName(), file);
                }
                Dialogs.showInfoNotification("Screenshot", "Written to " + file.getAbsolutePath());
            } catch (IOException e) {
                Dialogs.showErrorMessage("Screenshot error", "Unable to write to " + file.getAbsolutePath());
                logger.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Ensure a file is unique, if necessary.
     * @param file the input file
     * @return the input file, or a unique file with a related name in the same directory
     */
    private File confirmFile(File file) {
        if (!cbUniqueName.isSelected() || !file.exists())
            return file;
        var file2 = file;
        int ind = 0;
        var root = GeneralTools.getNameWithoutExtension(file);
        var ext = GeneralTools.getExtension(file).orElse("");
        while (file2.exists()) {
            ind++;
            file2 = new File(file.getParent(), root + "-" + ind + ext);
        }
        return file2;
    }
    

    private void writeJpegWithQuality(BufferedImage img, File file, float quality) throws IOException {
        var jpegWriter = ImageIO.getImageWritersByFormatName("jpeg").next();
        var jpgWriteParam = jpegWriter.getDefaultWriteParam();
        jpgWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        jpgWriteParam.setCompressionQuality(quality);
        // Need to delete existing file
        if (file.exists() && file.isFile())
            file.delete();
        try (var stream = ImageIO.createImageOutputStream(file)) {
            jpegWriter.setOutput(stream);
            img = BufferedImageTools.ensureBufferedImageType(img, BufferedImage.TYPE_INT_RGB); // Can't have alpha
            jpegWriter.write(null, new IIOImage(img, null, null), jpgWriteParam);
        }
    }

}
