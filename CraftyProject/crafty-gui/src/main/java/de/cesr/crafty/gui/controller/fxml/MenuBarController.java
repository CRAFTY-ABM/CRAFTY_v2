package de.cesr.crafty.gui.controller.fxml;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TitledPane;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.main.MainHeadless;
import de.cesr.crafty.core.modelRunner.ModelRunner;
import de.cesr.crafty.gui.utils.analysis.CapitalsAnalyzer;
import de.cesr.crafty.gui.utils.analysis.RecentProjects;
import de.cesr.crafty.gui.utils.graphical.ColorsTools;
import de.cesr.crafty.gui.utils.graphical.NewWindow;
import de.cesr.crafty.gui.utils.graphical.Tools;
import de.cesr.crafty.gui.utils.graphical.WarningWindowes;
import de.cesr.crafty.gui.main.FxMain;
import de.cesr.crafty.gui.main.GuiScaler;
import de.cesr.crafty.core.utils.file.PathTools;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.Alert;

public class MenuBarController {
	@FXML
	private Menu recent;
	@FXML
	private MenuItem dataAnalysis;

	private static MenuBarController instance;

	public MenuBarController() {
		instance = this;
	}

	public static MenuBarController getInstance() {
		return instance;
	}

	public MenuItem getDataAnalysis() {
		return dataAnalysis;
	}

	public void initialize() {
		updateRecentFilesMenu();
		dataAnalysis.setDisable(true);
	}

	// Event Listener on MenuItem.onAction
	@FXML
	public void open(ActionEvent event) {
		openProject();

	}

	@FXML
	public void Exit(ActionEvent event) {
		Platform.exit();
	}

	@FXML
	public void dataAnalysisDirectory(ActionEvent event) {
		// warning windowz that will take time and is alredy exsite and if the user want
		// to update it
		String outputPath = PathTools.makeDirectory(ProjectLoader.getProjectPath() + File.separator
				+ "Input-Data-Analyses" + File.separator + "Capitals-trends-through-Scenarios" + File.separator);
		// Check if the folder is exist
		String message = "Generating the data directory for capital trends across scenarios may take time,\n"
				+ "depending on the map size, the number of scenarios, and the time period.";
		Consumer<String> ok = _ -> {
			CapitalsAnalyzer.generateGrapheDataByScenarios(outputPath);
		};
		Consumer<String> cancel = _ -> {
			System.out.println("Canceled");
		};
		WarningWindowes.showWarningMessage(message, "Continue", ok, "Cancel", cancel);
	}

	@FXML
	public void restart(ActionEvent event) {
		restartApplication();
	}

	@FXML
	public void welcome(ActionEvent event) {
		openWebInBrowser();
	}

	@FXML
	public void colorPallet(ActionEvent event) {
		NewWindow winColor = new NewWindow();
		ColorsTools.windowzpalette(winColor);
	}

	@FXML
	public void increaseTextSize(ActionEvent event) {
		GuiScaler.increaseTextSize(FxMain.scene);
	}

	@FXML
	public void decreaseTextSize(ActionEvent event) {
		GuiScaler.decreaseTextSize(FxMain.scene);
	}

	@FXML
	public void resetTextSize(ActionEvent event) {
		GuiScaler.resetTextSize(FxMain.scene);
	}

	private void restartApplication() {
		try {
			Process process = new ProcessBuilder(buildRestartCommand()).start();
			if (process.waitFor(750, TimeUnit.MILLISECONDS)) {
				throw new IOException("The restarted application exited immediately with code " + process.exitValue());
			}
			System.exit(0);
		} catch (IOException e) {
			showRestartError(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			showRestartError(e);
		}
	}

	private List<String> buildRestartCommand() {
		ProcessHandle.Info processInfo = ProcessHandle.current().info();
		if (processInfo.command().isPresent() && processInfo.arguments().isPresent()) {
			List<String> command = new ArrayList<>();
			command.add(processInfo.command().get());
			Collections.addAll(command, processInfo.arguments().get());
			return command;
		}

		String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
		List<String> command = new ArrayList<>();
		command.add(javaBin);
		command.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
		command.add("-cp");
		command.add(ManagementFactory.getRuntimeMXBean().getClassPath());
		command.add(FxMain.class.getName());
		return command;
	}

	private void showRestartError(Exception error) {
		error.printStackTrace();
		Alert alert = new Alert(Alert.AlertType.ERROR);
		alert.setTitle("Restart failed");
		alert.setHeaderText("CRAFTY could not be restarted");
		alert.setContentText(error.getMessage());
		alert.showAndWait();
	}

	private void openProject() {
		File selectedDirectory;
		String userDocumentsPath = System.getProperty("user.home") + File.separator + "Documents";
		File documentsDir = new File(userDocumentsPath);

		// Check if the Data directory exists within Documents
		File dataDir = new File(documentsDir, "Data");
		if (!dataDir.exists() || !dataDir.isDirectory()) {
			// If the Data directory does not exist, fall back to the Documents directory
			selectedDirectory = Tools.selectFolder(userDocumentsPath);
		} else {
			// If the Data directory exists, use it as the starting path
			selectedDirectory = Tools.selectFolder(dataDir.getAbsolutePath());
		}

		if (selectedDirectory != null) {
			ConfigLoader.init();
			ConfigLoader.config.project_path = selectedDirectory.getAbsolutePath();
			ProjectLoader.pathInitialisation(Paths.get(ConfigLoader.config.project_path));
			ConfigLoader.config.scenario = ProjectLoader.getScenariosList().get(0);
			runnerStartAndPaneInitialze();
			RecentProjects.add(Paths.get(ConfigLoader.config.project_path));
			updateRecentFilesMenu();
		}
	}

	public static boolean onlyOneTime = true;

	private void runnerStartAndPaneInitialze() {
		MainHeadless.runner = new ModelRunner();
		MainHeadless.runner.start();
		if (ProjectLoader.getProjectPath() != null) {
			initialsePanes();
			if (onlyOneTime) {
				Platform.runLater(() -> {
					FxMain.anchor.getChildren().remove(FxMain.logo);
				});
				onlyOneTime = false;
//				GuiScaler.igorInitialScaled = false;
			}

		}
	}

	private void initialsePanes() {
		WarningWindowes.showWaitingDialog(_ -> {
			try {
				Parent workspace = FXMLLoader.load(getClass().getResource("/fxmlControllers/TabPaneFXML.fxml"));
				disableTitledPaneAnimations(workspace);
				FxMain.anchor.setCenter(workspace);
			} catch (IOException en) {
				// TODO Auto-generated catch block
				en.printStackTrace();
			}
		});

	}

	private void disableTitledPaneAnimations(Node node) {
		if (node instanceof TitledPane pane) {
			pane.setAnimated(false);
			if (pane.getContent() != null) {
				disableTitledPaneAnimations(pane.getContent());
			}
		}
		if (node instanceof Parent parent) {
			for (Node child : parent.getChildrenUnmodifiable()) {
				disableTitledPaneAnimations(child);
			}
		}
	}

	private void openWebInBrowser() {
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {

			try {
				Desktop.getDesktop().browse(new URI("https://landchange.imk-ifu.kit.edu/CRAFTY"));
			} catch (IOException | URISyntaxException e) {
				e.printStackTrace();
			}

		}
	}

	private void updateRecentFilesMenu() {
		recent.getItems().clear();

		List<Path> paths = RecentProjects.load();
		Collections.reverse(paths);

		for (Path p : paths) {
			MenuItem item = new MenuItem(p.toString());
			item.setOnAction(_ -> {
				ConfigLoader.init();
				ConfigLoader.config.project_path = p.toString();
				ProjectLoader.pathInitialisation(Paths.get(ConfigLoader.config.project_path));
				ConfigLoader.config.scenario = ProjectLoader.getScenariosList().iterator().next();
				runnerStartAndPaneInitialze();
			});
			recent.getItems().add(item);
		}

		// “Clear history” entry
		MenuItem clear = new MenuItem("Clear history");
		clear.setOnAction(_ -> {
			RecentProjects.clear();
			updateRecentFilesMenu();
		});

		recent.getItems().add(new SeparatorMenuItem());
		recent.getItems().add(clear);
	}

}
