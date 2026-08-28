package de.cesr.crafty.gui.controller.fxml;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import de.cesr.crafty.core.cli.Config;
import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.CsvKind;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.main.MainHeadless;
import de.cesr.crafty.core.modelRunner.InitialDSEquilibriumManager;
import de.cesr.crafty.core.modelRunner.ModelRunner;
import de.cesr.crafty.gui.canvasFx.CellsCanvas;
import de.cesr.crafty.gui.canvasFx.MapStatisticsPane;
import de.cesr.crafty.gui.utils.graphical.ColorsTools;
import de.cesr.crafty.gui.utils.graphical.LineChartTools;
import de.cesr.crafty.gui.utils.graphical.MousePressed;
import de.cesr.crafty.gui.utils.graphical.NewWindow;
import de.cesr.crafty.gui.utils.graphical.Tools;
import de.cesr.crafty.core.output.Listener;
import de.cesr.crafty.core.updaters.SupplyUpdater;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.gui.institutes.Institutes_Set;
import de.cesr.crafty.gui.institutes.Targets_Set;
import de.cesr.crafty.gui.utils.graphical.SaveAs;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.scene.layout.GridPane;

public class ModelRunnerController {
	@FXML
	private VBox TopBox;

	@FXML
	private Label tickTxt;
	@FXML
	private Button oneStep;
	@FXML
	private Button run;
	@FXML
	private Button stop;
	@FXML
	private GridPane gridPaneLinnChart;
	@FXML
	private ScrollPane scroll;

	public String colorDisplay = "AFT";

//	Timeline timeline;

	ArrayList<LineChart<Number, Number>> lineChart;

	RadioButton[] radioColor;
	NewWindow colorbox = new NewWindow();

	private boolean startRunin = true;
	private static final CustomLogger LOGGER = new CustomLogger(ModelRunnerController.class);

	private static final long TARGET_PERIOD_MS = 40; // 25 FPS ≈ 40 ms

	private ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "simulation-worker");
		t.setDaemon(true);
		return t;
	});

	public void initialize() {
		System.out.println("initialize " + getClass().getSimpleName());
		tickTxt.setText(String.valueOf(Timestep.getStartYear()));
		lineChart = new ArrayList<>();

		Collections.synchronizedList(lineChart);
		ConfigLoader.config.output_folder_name = ProjectLoader.getScenario();
		if (hasLocalCharts()) {
			initilaseChart(lineChart);
		}
		initialzeRadioColorBox();
		if (hasLocalCharts()) {
			initializeGridpane(3);
			Tools.forceResisingHeight(1, scroll);
		}
		Tools.forceResisingWidth(TopBox);
	}

	private boolean hasLocalCharts() {
		return gridPaneLinnChart != null && scroll != null;
	}

	protected void scheduleNextStep(long delayMs) {
		System.out.println("scheduleNextStep: " + delayMs);
		worker.schedule(this::runOneStep, delayMs, TimeUnit.MILLISECONDS);
	}

	/** runs on the worker thread – *never* on FX thread */
	private void runOneStep() {
		/* 1) END-CONDITION ------------------------------------------- */
		if (Timestep.getCurrentYear() > Timestep.getEndtYear()) {
			ModelRunner.exportChartsPlots();
			System.out.println("------------------------------   End Simulation   ------------------------------");
			if (ConfigLoader.config.generate_output_files) {
				Platform.runLater(this::displayRunAsOutput);
			}
			worker.shutdownNow();
			return;
		}

		/* 2) SIMULATION – core --------------------- */
		long start = System.nanoTime();
		System.out.println("step stat... " + Timestep.getCurrentYear());
		if (shouldRunInstitutions()) {
			Institutes_Set.stepPolicies();
		}
		MainHeadless.runner.step();
		Targets_Set.recordTargetsValues();

		long simTime = (System.nanoTime() - start) / 1_000_000; // ms

		/* 3) RENDER – gui ---------------------- */
		CountDownLatch uiDone = new CountDownLatch(1);
		Platform.runLater(() -> {
			renderStep(); // update gui
			uiDone.countDown();
		});

		try {
			uiDone.await();
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return;
		}

		if (shouldPauseAfterStep()) {
			Platform.runLater(this::showRunPauseDialog);
			return;
		}

		/* 4) SCHEDULE next step -------------------------------------- */
		long wait = TARGET_PERIOD_MS - simTime;
		if (wait < 0)
			wait = 0; // step was slow → start immediately
		scheduleNextStep(wait);
	}

	protected boolean shouldPauseAfterStep() {
		return false;
	}

	/** Whether a policy decision is required before the first simulation step. */
	protected boolean shouldPauseBeforeFirstStep() {
		return false;
	}

	/** Whether institution policies participate in the current simulation run. */
	protected boolean shouldRunInstitutions() {
		return true;
	}

	protected void showRunPauseDialog() {
		scheduleNextStep(0);
	}

	void initializeGridpane(int colmunNBR) {
		int j = 0, k = 0;
		for (int m = 0; m < lineChart.size(); m++) {
			gridPaneLinnChart.add(Tools.hBox(lineChart.get(m)), j++, k);
			if (j % colmunNBR == 0) {
				k++;
				j = 0;
			}
		}
	}

	void initialzeRadioColorBox() {
		radioColor = new RadioButton[ServiceSet.getServicesList().size() + 1];
		radioColor[radioColor.length - 1] = new RadioButton("AFT");
		for (int i = 0; i < ServiceSet.getServicesList().size(); i++) {
			radioColor[i] = new RadioButton(ServiceSet.getServicesList().get(i));
		}
		for (int i = 0; i < radioColor.length; i++) {
			int m = i;
			radioColor[i].setOnAction(_ -> {
				colorDisplay = radioColor[m].getText();
				CellsCanvas.colorMap(radioColor[m].getText());
				for (int I = 0; I < radioColor.length; I++) {
					if (I != m) {
						radioColor[I].setSelected(false);
					}
				}
			});
		}
	}

	@FXML
	void selecserivce() {
		if (!colorbox.isShowing()) {
			VBox g = new VBox();
			g.getChildren().addAll(radioColor);
			colorbox.creatwindows("Display services and AFT distribution", g);
		}
	}

	@FXML
	public void oneStep() {
		LOGGER.info("------------------- Start of Tick  |" + Timestep.getCurrentYear() + "| -------------------");
		if (shouldRunInstitutions()) {
			Institutes_Set.stepPolicies();
		}
		MainHeadless.runner.step();
		Targets_Set.recordTargetsValues();
		Platform.runLater(() -> {
			renderStep();
		});
	}

	private void renderStep() {
		map_synchronisation();
		tickTxt.setText(String.valueOf(Timestep.getCurrentYear()));
		if (hasLocalCharts()) {
			updateSupplyDemandLineChart();
		}
		SimulationPlotsController.refreshAll();
		MapStatisticsPane.refresh(CellsCanvas.getColorType());
		InstitutionDashboardController.refreshAll();
		TargetsPlotController.updateAll();
		PolicyPlotsController.refreshAll();
	}

	private void map_synchronisation() {
		if (Config.map_synchronisation
				&& (Timestep.getTick() % Config.map_synchronisation_gap == 0 || Timestep.getTick() == 0)) {
			CellsCanvas.colorMap(colorDisplay);
		}
	}

	private void updateSupplyDemandLineChart() {
		int completedYear = Timestep.getCurrentYear() - 1;
		if (Config.chart_synchronisation && (Timestep.getTick() % Config.chart_synchronisation_gap == 0
				|| completedYear == Timestep.getStartYear()
				|| completedYear == Timestep.getEndtYear())) {
			AtomicInteger m = new AtomicInteger();
			ServiceSet.getServicesList().forEach(service -> {
				lineChart.get(m.get()).getData().get(0).getData().add(new XYChart.Data<>(completedYear,
						ServiceSet.worldService.get(service).getDemands().get(completedYear)));
				lineChart.get(m.get()).getData().get(1).getData()
						.add(new XYChart.Data<>(completedYear, SupplyUpdater.totalSupply.get(service)));
				m.getAndIncrement();
			});
			ObservableList<Series<Number, Number>> observable = lineChart.get(lineChart.size() - 1).getData();
			List<String> listofNames = observable.stream().map(Series::getName).collect(Collectors.toList());
			AFTsLoader.hashAgentNbr.forEach((name, value) -> {
				observable.get(listofNames.indexOf(name)).getData()
						.add(new XYChart.Data<>(completedYear, value));
			});
		}
	}

	private void displayRunAsOutput() {
		OutPuterController.isCurrentResult = true;
		OutPutTabController.getInstance().createNewTab("Current simulation");
		TabPane tabpane = TabPaneController.getInstance().getTabpane();
		tabpane.getSelectionModel().select(tabpane.getTabs().size() - 1);
		tabpane.getTabs().stream().filter(tab -> tab.getText().equals("Model OutPut")) // Match tab by name
				.findFirst() // Get the first matching tab (if any)
				.ifPresent(tab -> tabpane.getSelectionModel().select(tab)); // Select the tab if found
	}

	@FXML
	public void run() {
		run.setDisable(true);
		simulationFolderName();
		
		System.out.println("#######  "+ConfigLoader.config.output_folder_name);
		

		if (startRunin || !ConfigLoader.config.generate_output_files) {
			InitialDSEquilibriumManager.demandEquilibrium();
			worker = Executors.newSingleThreadScheduledExecutor(r -> {
				Thread t = new Thread(r, "simulation-worker");
				t.setDaemon(true);
				return t;
			});
			MapStatisticsPane.setSimulationRunning(true);
			onRunStarted();
			if (shouldPauseBeforeFirstStep()) {
				showRunPauseDialog();
			} else {
				scheduleNextStep(0);
			}
		}
	}
	protected void onRunStarted() {
	}

	@FXML
	public void stop() {
		MapStatisticsPane.setSimulationRunning(false);
		System.out.println("0. worker.shutdown();");
		worker.shutdown();
		try {
			// 2 - block the *calling* thread until the worker finishes, max 5 s
			if (!worker.awaitTermination(3, TimeUnit.SECONDS)) {
				// (optional) give up and interrupt whatever is still running

				worker.shutdownNow();
			}
		} catch (InterruptedException ie) {
			worker.shutdownNow();
			Thread.currentThread().interrupt();
		}

		Platform.runLater(() -> {
			Timestep.setCurrentYear(Timestep.getStartYear());
			Timestep.setTick(0);
			Institutes_Set.resetRuntimeState();
			// reloadBaseline
//			Path baselinePath = PathTools.fileFilter(PathTools.asFolder("worlds"), "Baseline_map").iterator().next();
//			CsvProcessors.processCSV(baselinePath, CsvKind.BASELINE);
			MainHeadless.runner.start();
			run.setDisable(false);
			tickTxt.setText(String.valueOf(Timestep.getStartYear()));
			if (hasLocalCharts()) {
				gridPaneLinnChart.getChildren().clear();
				lineChart.clear();
				initilaseChart(lineChart);
				int j = 0, k = 0;
				for (int m = 0; m < lineChart.size(); m++) {
					gridPaneLinnChart.add(lineChart.get(m), j++, k);
					if (j % 3 == 0) {
						k++;
						j = 0;
					}
				}
			}
			SimulationPlotsController.resetAll();
			InstitutionDashboardController.refreshAll();
			Targets_Set.resetTargetsValues();
			TargetsPlotController.resetAll();
			PolicyPlotsController.resetAll();
			onSimulationReset();
		});
	}

	protected void onSimulationReset() {
	}

	void initilaseChart(ArrayList<LineChart<Number, Number>> lineChart) {
		ServiceSet.getServicesList().forEach(service -> {
			Series<Number, Number> s1 = new XYChart.Series<Number, Number>();
			Series<Number, Number> s2 = new XYChart.Series<Number, Number>();
			s1.setName("Demand " + service);
			s2.setName("Supply " + service);
			LineChart<Number, Number> l = new LineChart<>(
					new NumberAxis(Timestep.getStartYear(), Timestep.getEndtYear(), 5), new NumberAxis());
			l.getData().add(s1);
			l.getData().add(s2);
			LineChartTools.configurexAxis(l, Timestep.getStartYear(), Timestep.getEndtYear());
			lineChart.add(l);
			LineChartTools.addSeriesTooltips(l);

			String ItemName = "Save as CSV";
			Consumer<String> action = _ -> {
				SaveAs.exportLineChartDataToCSV(l);
			};
			HashMap<String, Consumer<String>> othersMenuItems = new HashMap<>();
			othersMenuItems.put(ItemName, action);

			MousePressed.mouseControle(TopBox, l, othersMenuItems);
		});
		LineChart<Number, Number> l = new LineChart<>(
				new NumberAxis(Timestep.getStartYear(), Timestep.getEndtYear(), 5), new NumberAxis());
		lineChart.add(l);

		AFTsLoader.getAftHash().forEach((name, a) -> {
			Series<Number, Number> s = new XYChart.Series<Number, Number>();
			s.setName(name);
			l.getData().add(s);
			s.getNode().lookup(".chart-series-line")
					.setStyle("-fx-stroke: " + ColorsTools.getStringColor(Color.web(a.getColor())) + ";");
		});
		l.setCreateSymbols(false);
		LineChartTools.addSeriesTooltips(l);
		MousePressed.mouseControle(TopBox, l);
		LineChartTools.labelcolor(l);
	}

	Alert simulationFolderName() {
		if (!ConfigLoader.config.generate_output_files) {
			return null;
		}
		ConfigLoader.config.generate_map_output_files = true;
		Listener.initializeListExportingYearsMap();
		Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.setHeaderText("Enter an output folder name");
		String cofiguration = Listener.exportConfigurationFile();
		cofiguration = cofiguration + " \n " + "Additional comments:\n";
		TextField textField = new TextField();
		textField.setPromptText("Output folder name (leave blank to use the default)");
		Text txt = new Text(ProjectLoader.getProjectPath() + PathTools.asFolder("output") + ProjectLoader.getScenario()
				+ File.separator + "...");
		TextArea textArea = new TextArea();
		textArea.setText(cofiguration);
		VBox v = new VBox(txt, textField, textArea);
		DialogPane dialogPane = alert.getDialogPane();
		dialogPane.setContent(v);
		Window window = alert.getDialogPane().getScene().getWindow();
		((Stage) window).setAlwaysOnTop(true);

		alert.showAndWait().ifPresent(response -> {
			if (response == ButtonType.OK) {
				Listener.outputfolderPath(null, textField.getText());
				PathTools.writeFile(ConfigLoader.config.output_folder_name + File.separator + "readme.txt",
						textArea.getText(), false);
				startRunin = true;
			} else if (response == ButtonType.CANCEL) {
				startRunin = false;
				stop();
			}
		});
		return alert;
	}

}
