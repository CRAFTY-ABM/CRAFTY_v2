package de.cesr.crafty.gui.controller.fxml;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.CapitalUpdater;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;
import de.cesr.crafty.gui.canvasFx.CellsCanvas;
import de.cesr.crafty.gui.utils.analysis.CapitalsAnalyzer;
import de.cesr.crafty.gui.utils.graphical.Histogram;
import de.cesr.crafty.gui.utils.graphical.MousePressed;
import de.cesr.crafty.gui.utils.graphical.SaveAs;
import de.cesr.crafty.gui.utils.graphical.Tools;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class CapitalsController {
	private static final CustomLogger LOGGER = new CustomLogger(CapitalsController.class);
	private static final String CAPITAL_PLACEHOLDER = "Select a capital";

	@FXML
	private VBox TopBox;
	@FXML
	private VBox vboxForSliderColors;
	@FXML
	private VBox vboxAnaliser;
	@FXML
	private HBox hbox;

	@FXML
	private BarChart<String, Number> histogramCapitals;
	@FXML
	private BarChart<String, Number> hServiceSensitivity;
	@FXML
	private BarChart<String, Number> hAftSensitivity;

	@FXML
	private ChoiceBox<String> capitalChoice;
	@FXML
	private ChoiceBox<Integer> capitalYearChoice;
	@FXML
	private Label capitalMapStatus;

	private Task<Map<Cell, Double>> capitalMapLoadTask;

	public static RadioButton[] radioColor;
	private static CapitalsController instance;

	public CapitalsController() {
		instance = this;
	}

	public static CapitalsController getInstance() {
		return instance;
	}

	public BarChart<String, Number> getHistogramCapitals() {
		return histogramCapitals;
	}

	public BarChart<String, Number> getHServiceSensitivity() {
		return hServiceSensitivity;
	}

	public BarChart<String, Number> getHAftSensitivity() {
		return hAftSensitivity;
	}

	public void initialize() {
		System.out.println("initialize " + getClass().getSimpleName());
		initialiseCapitalMapChoices();
		
//		mapColorAndCapitalHistogrameInitialisation();
//		((CategoryAxis) histogramCapitals.getXAxis()).setCategories(FXCollections.observableArrayList(
//				IntStream.rangeClosed(1, 100).mapToObj(String::valueOf).collect(Collectors.toList())));
//		radioColor[0].fire();
		addCapitalsTrends();

		Tools.forceResisingWidth(TopBox/* ,hbox, vboxAnaliser */);
		Tools.forceResisingHeight(vboxAnaliser);
//		Tools.forceResisingWidth(0.1, vboxForSliderColors);

	}
	
	private void initialiseCapitalMapChoices() {
		ObservableList<String> capitals = FXCollections.observableArrayList(CAPITAL_PLACEHOLDER);
		capitals.addAll(CapitalUpdater.getCapitalsList());
		capitalChoice.setItems(capitals);
		capitalChoice.setValue(CAPITAL_PLACEHOLDER);

		ObservableList<Integer> years = FXCollections.observableArrayList();
		for (int year = Timestep.getStartYear(); year <= Timestep.getEndtYear(); year++) {
			if (CapitalUpdater.getCapitalPath(year) != null) {
				years.add(year);
			}
		}
		capitalYearChoice.setItems(years);
		if (!years.isEmpty()) {
			int currentYear = Timestep.getCurrentYear();
			capitalYearChoice.setValue(years.contains(currentYear) ? currentYear : years.getFirst());
		} else {
			capitalMapStatus.setText("No capital map files are available.");
			capitalChoice.setDisable(true);
			capitalYearChoice.setDisable(true);
			return;
		}

		capitalChoice.setOnAction(_ -> displaySelectedCapitalMap());
		capitalYearChoice.setOnAction(_ -> displaySelectedCapitalMap());
	}

	private void displaySelectedCapitalMap() {
		String capital = capitalChoice.getValue();
		Integer year = capitalYearChoice.getValue();
		if (capital == null || CAPITAL_PLACEHOLDER.equals(capital) || year == null) {
			return;
		}

		Path capitalFile = CapitalUpdater.getCapitalPath(year);
		if (capitalFile == null) {
			capitalMapStatus.setText("No capital map file is configured for " + year + ".");
			return;
		}

		setCapitalMapControlsBusy(true);
		capitalMapStatus.setText("Loading " + capital + " for " + year + "...");

		Task<Map<Cell, Double>> task = new Task<>() {
			@Override
			protected Map<Cell, Double> call() {
				return readCapitalValues(capitalFile, capital);
			}
		};
		capitalMapLoadTask = task;

		task.setOnSucceeded(_ -> {
			if (capitalMapLoadTask != task) {
				return;
			}
			CellsCanvas.colorCapitalMap(capital, year, task.getValue());
			capitalMapStatus.setText("Displaying " + capital + " for " + year + " (display only).");
			setCapitalMapControlsBusy(false);
		});
		task.setOnFailed(_ -> {
			if (capitalMapLoadTask != task) {
				return;
			}
			Throwable error = task.getException();
			LOGGER.error("Could not display " + capital + " for " + year + ".", error);
			capitalMapStatus.setText("Could not load " + capital + " for " + year + ".");
			setCapitalMapControlsBusy(false);
		});
		task.setOnCancelled(_ -> {
			if (capitalMapLoadTask == task) {
				setCapitalMapControlsBusy(false);
			}
		});

		Thread loader = new Thread(task, "capital-map-display-loader");
		loader.setDaemon(true);
		loader.start();
	}

	private Map<Cell, Double> readCapitalValues(Path capitalFile, String capital) {
		Map<String, List<String>> data = CsvProcessors.ReadAsaHash(capitalFile);
		if (data == null) {
			throw new IllegalArgumentException("The capital file could not be read: " + capitalFile);
		}

		List<String> xValues = findColumn(data, "X");
		List<String> yValues = findColumn(data, "Y");
		List<String> capitalValues = findColumn(data, capital);
		if (xValues == null || yValues == null || capitalValues == null) {
			throw new IllegalArgumentException("Required columns X, Y, or " + capital + " are missing.");
		}

		int rowCount = Math.min(xValues.size(), Math.min(yValues.size(), capitalValues.size()));
		Map<Cell, Double> valuesByCell = new HashMap<>();
		for (int row = 0; row < rowCount; row++) {
			Cell cell = CellsLoader.getCell(Utils.sToI(xValues.get(row)), Utils.sToI(yValues.get(row)));
			if (cell != null) {
				valuesByCell.put(cell, Utils.sToD(capitalValues.get(row)));
			}
		}
		return valuesByCell;
	}

	private List<String> findColumn(Map<String, List<String>> data, String name) {
		return data.entrySet().stream()
				.filter(entry -> entry.getKey().trim().equalsIgnoreCase(name))
				.map(Map.Entry::getValue)
				.findFirst()
				.orElse(null);
	}

	private void setCapitalMapControlsBusy(boolean busy) {
		capitalChoice.setDisable(busy);
		capitalYearChoice.setDisable(busy);
	}

	

	private void addCapitalsTrends() {
		ArrayList<Path> listPaths = PathTools.fileFilter(PathTools.asFolder("Input-Data-Analyses"),
				PathTools.asFolder("Capitals-trends-through-Scenarios"));
		if (listPaths != null && listPaths.size() > 0) {
			GridPane grid = new GridPane();
			grid.setHgap(8);
			grid.setVgap(8);
			grid.setMaxWidth(Double.MAX_VALUE);
			for (int column = 0; column < 3; column++) {
				ColumnConstraints constraints = new ColumnConstraints();
				constraints.setPercentWidth(100.0 / 3.0);
				constraints.setHgrow(Priority.ALWAYS);
				constraints.setFillWidth(true);
				grid.getColumnConstraints().add(constraints);
			}

			int chartIndex = 0;
			for (Path path : listPaths) {
				Map<String, List<Double>> data = CsvProcessors.ReadAsaHashDouble(path);
				LineChart<Number, Number> chart = CapitalsAnalyzer.generateCapitalChart(path.getFileName().toString(),
						data);
				if (chart != null) {
					chart.getStyleClass().addAll("analysis-chart", "capital-trend-chart");
					chart.setMinWidth(0);
					chart.setPrefWidth(280);
					chart.setMaxWidth(Double.MAX_VALUE);
					chart.setMinHeight(190);
					chart.setPrefHeight(230);
					chart.setMaxHeight(230);
					chart.setCreateSymbols(false);
					GridPane.setHgrow(chart, Priority.ALWAYS);

					int column = chartIndex % 3;
					int row = chartIndex / 3;
					grid.add(chart, column, row);
					HashMap<String, Consumer<String>> chartActions = new HashMap<>();
					chartActions.put("Save as CSV", _ -> SaveAs.exportLineChartDataToCSV(chart));
					MousePressed.mouseControle(grid, chart, chartActions, path.getFileName().toString());
					chartIndex++;
				}
			}
			// add the grid to the Vbox
			vboxAnaliser.getChildren().add(grid);
		} else {
			vboxAnaliser.getChildren()
					.add(new Text("The data directory for capital trends across scenarios does not exist. "
							+ "To create it, select Edit > Generate input data analysis directory."));
		}
	}

	private void mapColorAndCapitalHistogrameInitialisation() {
		ToggleGroup radiosgroup = new ToggleGroup();

		radioColor = new RadioButton[CapitalUpdater.getCapitalsList().size()];
		for (int i = 0; i < radioColor.length; i++) {
			int k = i;
			if (k < CapitalUpdater.getCapitalsList().size()) {
				radioColor[k] = new RadioButton(CapitalUpdater.getCapitalsList().get(i));
			}
			radioColor[k].setOnAction(_ -> {
				updatehistograms(k);
				CellsCanvas.colorMap(radioColor[k].getText());
			});
			radioColor[k].setToggleGroup(radiosgroup);
			vboxForSliderColors.getChildren().add(radioColor[k]);
		}
	}

	private void updatehistograms(int k) {
		histogramCapitals.getData().clear();
		if (k < CapitalUpdater.getCapitalsList().size()) {
			if (!ProjectLoader.getScenario().equalsIgnoreCase("Baseline")) {
				updateHistogrameCapitals(Timestep.getCurrentYear(), CapitalUpdater.getCapitalsList().get(k));
				updateHistoService(Timestep.getCurrentYear(), CapitalUpdater.getCapitalsList().get(k));
				updateHistoAft(Timestep.getCurrentYear(), CapitalUpdater.getCapitalsList().get(k));
			}
		}
	}

	void updateHistoService(int year, String capitalName) {
		// initialise container
		Map<String, Double> hash = new HashMap<>();
		// loop for Services
		ServiceSet.getServicesList().forEach(serviceName -> {
			// loop for AFTs
			AFTsLoader.getActivateAFTsHash().values().forEach(a -> {
				// aggreagte by service
				if (a.getSensByService().get(serviceName) != null
						&& a.getSensByService().get(serviceName).get(capitalName) != 0) {
					hash.merge(serviceName, a.getSensByService().get(serviceName).get(capitalName), Double::sum);
				}

			});
		});

		Histogram.histo("Services", hServiceSensitivity, hash);
		Histogram.mouseHistogrameController(hServiceSensitivity);
	}

	void updateHistoAft(int year, String capitalName) {
		// initialise container
		Map<String, Double> hash = new HashMap<>();

		// loop for AFTs
		AtomicInteger count = new AtomicInteger();
		AFTsLoader.getActivateAFTsHash().forEach((aftName, a) -> {

			Map<String, Double> sumServices = new HashMap<>();
			// loop for Services
			ServiceSet.getServicesList().forEach(serviceName -> {
				// aggreagte by service

				if (a.getSensByService().get(serviceName) != null
						&& a.getSensByService().get(serviceName).get(capitalName) != 0) {
					sumServices.merge(aftName, a.getSensByService().get(serviceName).get(capitalName), Double::sum);
				}
			});
			if (sumServices.size() > 0) {
				count.getAndIncrement();
				sumServices.forEach((key, value) -> {
					hash.merge(key, value, Double::sum);
				});
			}
		});

		hash.forEach((key, value) -> {
			hash.put(key, value / count.get());
		});
		Histogram.histo("AFTs", hAftSensitivity, hash);
		Histogram.mouseHistogrameController(hAftSensitivity);
	}

	void updateHistogrameCapitals(int year, String capitalName) {
		Set<Double> dset = CellsLoader.hashCell.values().stream().map(c -> c.getCapitals().get(capitalName))
				.collect(Collectors.toSet());
		XYChart.Series<String, Number> dataSeries = new XYChart.Series<>();
		List<Integer> numbersInInterval = countNumbersInIntervals(dset, 100);
		dataSeries.setName(capitalName + "_" + year + "_" + ProjectLoader.getScenario());
		for (int i = 0; i < numbersInInterval.size(); i++) {
			Integer v = numbersInInterval.get(i);
			dataSeries.getData().add(new XYChart.Data<>((i) + "", v));
		}
		histogramCapitals.getData().add(dataSeries);
		Histogram.mouseHistogrameController(histogramCapitals);
	}

	public static List<Integer> countNumbersInIntervals(Set<Double> numbers, int intervalNBR) {
		int[] counts = new int[intervalNBR + 1];
		for (Double number : numbers) {
			if (number != null && number >= 0.0 && number <= 1.0) {
				int index = (int) (number * intervalNBR);
				counts[index]++;
			}
		}
		OptionalInt max = Arrays.stream(counts).max();
		List<Integer> result = new ArrayList<>();
		for (int count : counts) {
			result.add((count * 100) / (max.getAsInt() != 0 ? max.getAsInt() : 1));
		}
		return result;
	}

	// Generate capital comparaison use the exsiting ones (add it in the menue)
	//

}
