package de.cesr.crafty.gui.controller.fxml;

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
import de.cesr.crafty.gui.utils.analysis.RecentProjects;
import de.cesr.crafty.gui.utils.graphical.ColorsTools;
import de.cesr.crafty.gui.utils.graphical.LineChartTools;
import de.cesr.crafty.core.utils.general.Utils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.chart.LineChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Tab;

public class TabPaneController {

	@FXML
	private HBox topLevelBox;
	@FXML
	private ChoiceBox<String> scenarioschoice;
	@FXML
	private ChoiceBox<String> yearchoice;
	@FXML
	private TabPane tabpane;
	@FXML
	private VBox mapBox;
	@FXML
	private Tab dataPane;
	@FXML
	CheckBox regionalBox;
//	@FXML
//	private TextArea consoleArea;
// public static CellsLoader cellsLoader = new CellsLoader();

	private boolean isNotInitialsation = false;

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
		mapBox.getChildren().add(CellsCanvas.subScene);

		RecentProjects.writePathRecentProject("RecentProject.txt", "\n" + ProjectLoader.getProjectPath());
		scenarioschoice.getItems().addAll(ProjectLoader.getScenariosList());
		scenarioschoice.setValue(ProjectLoader.getScenario());
		ArrayList<String> listYears = new ArrayList<>();
		for (int i = Timestep.getStartYear(); i < Timestep.getEndtYear(); i++) {
			listYears.add(i + "");
		}
		yearchoice.getItems().addAll(listYears);
		yearchoice.setValue(listYears.get(0));
		isNotInitialsation = true;
		// tabpane.setPrefWidth(FxMain.topLevelBox.getWidth()/2);
		regionalBox.setSelected(CellsLoader.regionalization);
		// regionalBox.setDisable(ServiceSet.isRegionalServicesExisted());
		MenuBarController.getInstance().getDataAnalysis().setDisable(false);

//		regionalBox.setDisable(true);
	}

	@FXML
	public void regionalization() {
		System.out.println("Regionalisation");
		CellsLoader.regionalization = regionalBox.isSelected();
		ConfigLoader.config.regionalization = regionalBox.isSelected();
		MainHeadless.runner.start();
		AtomicInteger nbr = new AtomicInteger();
		CellsLoader.regions.values().forEach(R -> {
			Color color = ColorsTools.colorlist(nbr.getAndIncrement());
			R.getCells().values().forEach(c -> {
				CellsCanvas.ColorP(c, color);
			});
		});
		// CellsCanvas.gc.drawImage(CellsCanvas.writableImage, 0, 0);
		// regionalBox.setSelected(CellsLoader.regionsNamesSet.size() > 1);
	}

	@FXML
	public void scenarioschoice() {
		if (isNotInitialsation) {
			ConfigLoader.config.scenario = scenarioschoice.getValue();
			MainHeadless.runner.start();

			LineChart<Number, Number> chart = ServicesController.getInstance().getDemandsChart();
			new LineChartTools().lineChart((Pane) chart.getParent(), chart,
					ServicesController.serialisationWorldDemand());
			MasksPaneController.getInstance().clear(new ActionEvent());
			MasksPaneController.initialiseMask();
			yearchoice();
		}
	}

	@FXML
	public void yearchoice() {
		if (isNotInitialsation) {
			if (yearchoice.getValue() != null) {
				Timestep.setCurrentYear((int) Utils.sToD(yearchoice.getValue()));
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

			}
		}
	}
}
