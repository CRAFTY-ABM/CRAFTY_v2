package de.cesr.crafty.gui.controller.fxml;

import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.main.MainHeadless;
import de.cesr.crafty.core.modelRunner.ModelRunner;
import de.cesr.crafty.core.updaters.CapitalUpdater;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.gui.canvasFx.CellsCanvas;
import de.cesr.crafty.gui.canvasFx.MapPane;
import de.cesr.crafty.gui.canvasFx.MapStatisticsPane;
import de.cesr.crafty.gui.institutes.Institutes_Set;
import de.cesr.crafty.gui.institutes.GuiInstitutionBootstrap;
import de.cesr.crafty.gui.institutes.Targets_Set;
import de.cesr.crafty.gui.utils.analysis.RecentProjects;
import de.cesr.crafty.gui.utils.graphical.ColorsTools;
import de.cesr.crafty.gui.utils.graphical.LineChartTools;
import de.cesr.crafty.gui.utils.graphical.NewWindow;
import de.cesr.crafty.core.utils.general.Utils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Orientation;
import javafx.scene.paint.Color;
import javafx.scene.chart.LineChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Tab;
import javafx.scene.control.ToggleButton;

public class TabPaneController {

	@FXML
	private HBox topLevelBox;
	@FXML
	private ChoiceBox<String> scenarioschoice;
//	@FXML
//	private ChoiceBox<String> yearchoice;
	@FXML
	private TabPane tabpane;
	@FXML
	private VBox mapBox;
	@FXML
	private VBox controlsBox;
	@FXML
	private SplitPane workspaceSplit;
	@FXML
	private ToggleButton mapToggle;
	@FXML
	private Tab dataPane;
//	@FXML
//	CheckBox regionalBox;
//	@FXML
//	private TextArea consoleArea;
// public static CellsLoader cellsLoader = new CellsLoader();

	private boolean isNotInitialsation = false;
	private boolean mapDetached;
	private double mapDividerPosition = 0.52;
	private SplitPane mapDetailsSplit;
	private StackPane mapViewport;

	private static TabPaneController instance;

	public TabPaneController() {
		instance = this;
	}

	public static TabPaneController getInstance() {
		return instance;
	}

	public TabPane getTabpane() {
		return tabpane;
	}

	public void initialize() {
		System.out.println("initialize " + getClass().getSimpleName());
		if (CellsCanvas.subScene != null) {
			mapViewport = new StackPane(CellsCanvas.subScene);
			mapViewport.getStyleClass().add("map-viewport");
			mapViewport.setMinHeight(180);
			mapViewport.setPrefHeight(460);
			MapStatisticsPane statisticsPane = new MapStatisticsPane();
			statisticsPane.setMinHeight(230);
			statisticsPane.setPrefHeight(300);
			mapDetailsSplit = new SplitPane(mapViewport, statisticsPane);
			mapDetailsSplit.setOrientation(Orientation.VERTICAL);
			mapDetailsSplit.getStyleClass().add("map-details-split");
			mapBox.getChildren().add(mapDetailsSplit);
			VBox.setVgrow(mapDetailsSplit, Priority.ALWAYS);
			mapViewport.widthProperty().addListener((_, _, _) -> resizeMap());
			mapViewport.heightProperty().addListener((_, _, _) -> resizeMap());
			Platform.runLater(() -> {
				mapDetailsSplit.setDividerPosition(0, 0.60);
				resizeMap();
				Platform.runLater(MapPane::fitMapInWindow);
			});
		}

		RecentProjects.writePathRecentProject("RecentProject.txt", "\n" + ProjectLoader.getProjectPath());
		scenarioschoice.getItems().addAll(ProjectLoader.getScenariosList());
		scenarioschoice.setValue(ProjectLoader.getScenario());
		ArrayList<String> listYears = new ArrayList<>();
		for (int i = Timestep.getStartYear(); i < Timestep.getEndtYear(); i++) {
			listYears.add(i + "");
		}
//		yearchoice.getItems().addAll(listYears);
//		yearchoice.setValue(listYears.get(0));
		isNotInitialsation = true;
		// tabpane.setPrefWidth(FxMain.topLevelBox.getWidth()/2);
//		regionalBox.setSelected(CellsLoader.regionalisation);
		// regionalBox.setDisable(ServiceSet.isRegionalServicesExisted());
		MenuBarController.getInstance().getDataAnalysis().setDisable(false);
		
		GuiInstitutionBootstrap.initialize();
		Targets_Set.updateTargetsComparisonData();
		InstitutionDashboardController.refreshAll();
		TargetsPlotController.refreshAll();
//		regionalBox.setDisable(true);
	}

	@FXML
	private void toggleMap() {
		if (mapDetached) {
			return;
		}
		if (mapToggle.isSelected()) {
			if (!workspaceSplit.getItems().contains(mapBox)) {
				workspaceSplit.getItems().add(mapBox);
			}
			mapToggle.setText("Hide map");
			Platform.runLater(() -> {
				workspaceSplit.setDividerPosition(0, mapDividerPosition);
				resizeMap();
			});
		} else {
			if (workspaceSplit.getDividers().size() == 1) {
				mapDividerPosition = workspaceSplit.getDividerPositions()[0];
			}
			workspaceSplit.getItems().remove(mapBox);
			mapToggle.setText("Show map");
		}
	}

	private void resizeMap() {
		if (CellsCanvas.subScene == null || mapViewport == null || CellsCanvas.subScene.getParent() != mapViewport) {
			return;
		}
		CellsCanvas.subScene.setWidth(Math.max(1, mapViewport.getWidth()));
		CellsCanvas.subScene.setHeight(Math.max(1, mapViewport.getHeight()));
	}

	public void detachMap() {
		if (mapDetached || CellsCanvas.subScene == null) {
			return;
		}

		int originalIndex = workspaceSplit.getItems().indexOf(mapBox);
		if (originalIndex < 0) {
			return;
		}
		if (!workspaceSplit.getDividers().isEmpty()) {
			mapDividerPosition = workspaceSplit.getDividerPositions()[0];
		}

		mapDetached = true;
		mapToggle.setDisable(true);
		workspaceSplit.getItems().remove(mapBox);

		NewWindow window = new NewWindow();
		try {
			window.creatwindows("CRAFTY map", 0.8, 0.8, mapBox);
			window.setMinWidth(520);
			window.setMinHeight(420);
			URL stylesheet = getClass().getResource("/styles.css");
			if (stylesheet != null) {
				window.getScene().getStylesheets().add(stylesheet.toExternalForm());
			}
			Platform.runLater(() -> {
				resizeMap();
				MapPane.fitMapInWindow();
			});
		} catch (RuntimeException exception) {
			restoreDetachedMap(originalIndex);
			throw exception;
		}

		window.setOnCloseRequest(_ -> restoreDetachedMap(originalIndex));
	}

	private void restoreDetachedMap(int originalIndex) {
		if (!mapDetached) {
			return;
		}

		if (mapBox.getParent() instanceof Pane detachedRoot) {
			detachedRoot.getChildren().remove(mapBox);
		}
		workspaceSplit.getItems().add(Math.min(originalIndex, workspaceSplit.getItems().size()), mapBox);
		mapDetached = false;
		mapToggle.setSelected(true);
		mapToggle.setText("Hide map");
		mapToggle.setDisable(false);

		Platform.runLater(() -> {
			if (!workspaceSplit.getDividers().isEmpty()) {
				workspaceSplit.setDividerPosition(0, mapDividerPosition);
			}
			resizeMap();
			MapPane.fitMapInWindow();
		});
	}

//	@FXML
//	public void regionalisation() {
//		System.out.println("Regionalisation");
//		CellsLoader.regionalisation = regionalBox.isSelected();
//		ConfigLoader.config.regionalisation = regionalBox.isSelected();
//		MainHeadless.runner.start();
//		AtomicInteger nbr = new AtomicInteger();
//		CellsLoader.regions.values().forEach(R -> {
//			Color color = ColorsTools.colorlist(nbr.getAndIncrement());
//			R.getCells().values().forEach(c -> {
//				CellsCanvas.ColorP(c, color);
//			});
//		});
//		// CellsCanvas.gc.drawImage(CellsCanvas.writableImage, 0, 0);
//		// regionalBox.setSelected(CellsLoader.regionsNamesSet.size() > 1);
//	}

	@FXML
	public void scenarioschoice() {
		if (isNotInitialsation) {
			System.out.println("@@@ Changing scenario to: " + scenarioschoice.getValue());
			ConfigLoader.config.scenario = scenarioschoice.getValue();
			ProjectLoader.setScenario(ConfigLoader.config.scenario);
			Targets_Set.updateTargetsComparisonData();
			MainHeadless.runner.start();
			InstitutionDashboardController.refreshAll();
			TargetsPlotController.refreshAll();

//			LineChart<Number, Number> chart = ServicesController.getInstance().getDemandsChart();
//			new LineChartTools().lineChart((Pane) chart.getParent(), chart,
//					ServicesController.serialisationWorldDemand());
//			MasksPaneController.getInstance().clear(new ActionEvent());
//			MasksPaneController.initialiseMask();
//			yearchoice();
		}
	}

	@FXML
	public void yearchoice() {
		if (isNotInitialsation) {
//			if (yearchoice.getValue() != null) {
//				Timestep.setCurrentYear((int) Utils.sToD(yearchoice.getValue()));
				ModelRunner.capitalUpdater.step();
				ModelRunner.aftsUpdater.step();
				if (dataPane.isSelected()) {
					for (int i = 0; i < CapitalUpdater.getCapitalsList().size(); i++) {
						if (CapitalsController.radioColor[i].isSelected()) {
							if (i < CapitalUpdater.getCapitalsList().size()) {
								CellsCanvas.colorMap(CapitalUpdater.getCapitalsList().get(i));
								CapitalsController.getInstance().updateHistogrameCapitals(Timestep.getCurrentYear(),
										CapitalUpdater.getCapitalsList().get(i));
							}
						}
					}
//				CellsCanvas.colorMap("Mock");
				}

//			}
		}
	}
}
