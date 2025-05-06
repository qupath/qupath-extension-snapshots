package qupath.ext.snapshots.ui;

import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.GitHubProject;
import qupath.lib.gui.extensions.QuPathExtension;

import java.io.IOException;
import java.util.ResourceBundle;


/**
 * QuPath extension to help capture snapshots and screenshots.
 */
public class SnapshotExtension implements QuPathExtension, GitHubProject {

	private static final Logger logger = LoggerFactory.getLogger(SnapshotExtension.class);

	private static final ResourceBundle resources = ResourceBundle.getBundle("qupath.ext.snapshots.ui.strings");

	private static final String EXTENSION_NAME = resources.getString("name");

	private static final String EXTENSION_DESCRIPTION = resources.getString("description");

	private static final Version EXTENSION_QUPATH_VERSION = Version.parse("v0.6.0-SNAPSHOT");

	private static final GitHubRepo EXTENSION_REPOSITORY = GitHubRepo.create(
			EXTENSION_NAME, "qupath", "qupath-extension-screenshots");

	private boolean isInstalled = false;


	/**
	 * Create a stage for the extension to display
	 */
	private Stage stage;

	@Override
	public void installExtension(QuPathGUI qupath) {
		if (isInstalled) {
			logger.debug("{} is already installed", getName());
			return;
		}
		isInstalled = true;
		addMenuItem(qupath);
	}

	/**
	 * Demo showing how a new command can be added to a QuPath menu.
	 * @param qupath The QuPath GUI
	 */
	private void addMenuItem(QuPathGUI qupath) {
		var menu = qupath.getMenu("Extensions", true);
		MenuItem menuItem = new MenuItem("Screenshot window");
		menuItem.setOnAction(e -> createStage());
		menu.getItems().add(menuItem);
	}

	/**
	 * Demo showing how to create a new stage with a JavaFX FXML interface.
	 */
	private void createStage() {
		if (stage == null) {
			try {
				stage = new Stage();
				Scene scene = new Scene(SnapshotController.createInstance());
				stage.initOwner(QuPathGUI.getInstance().getStage());
				stage.setTitle(resources.getString("stage.title"));
				stage.setScene(scene);
				stage.setResizable(true);
			} catch (IOException e) {
				Dialogs.showErrorMessage(resources.getString("error"), resources.getString("error.gui-loading-failed"));
				logger.error("Unable to load extension interface FXML", e);
			}
		}
		stage.show();
	}


	@Override
	public String getName() {
		return EXTENSION_NAME;
	}

	@Override
	public String getDescription() {
		return EXTENSION_DESCRIPTION;
	}
	
	@Override
	public Version getQuPathVersion() {
		return EXTENSION_QUPATH_VERSION;
	}

	@Override
	public GitHubRepo getRepository() {
		return EXTENSION_REPOSITORY;
	}
}
