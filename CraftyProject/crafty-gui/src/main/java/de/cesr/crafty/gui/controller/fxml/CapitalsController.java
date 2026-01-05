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
import java.util.stream.Collectors;

import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.CapitalUpdater;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.gui.canvasFx.CellsCanvas;
import de.cesr.crafty.gui.utils.analysis.CapitalsAnalyzer;
import de.cesr.crafty.gui.utils.graphical.Histogram;
import de.cesr.crafty.gui.utils.graphical.MousePressed;
import de.cesr.crafty.gui.utils.graphical.Tools;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class CapitalsController {
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
		mapColorAndCapitalHistogrameInitialisation();
//		((CategoryAxis) histogramCapitals.getXAxis()).setCategories(FXCollections.observableArrayList(
//				IntStream.rangeClosed(1, 100).mapToObj(String::valueOf).collect(Collectors.toList())));
		radioColor[0].fire();
		addCapitalsTrends();

		Tools.forceResisingWidth(TopBox/* ,hbox, vboxAnaliser */);
		Tools.forceResisingHeight(vboxAnaliser);
		Tools.forceResisingWidth(0.1, vboxForSliderColors);

	}

	private void addCapitalsTrends() {
		ArrayList<Path> listPaths = PathTools.fileFilter(PathTools.asFolder("Input-Data-Analyses"),
				PathTools.asFolder("Capitals-trends-through-Scenarios"));
		if (listPaths != null && listPaths.size() > 0) {
			// initialse the grid
			GridPane grid = new GridPane();
			AtomicInteger i = new AtomicInteger(), j = new AtomicInteger();
			listPaths.forEach(path -> {
				Map<String, List<Double>> data = CsvProcessors.ReadAsaHashDouble(path);
				// plot
				LineChart<Number, Number> chart = CapitalsAnalyzer.generateCapitalChart(path.getFileName().toString(),
						data);
				if (chart != null) {
					if (i.get() % 5 == 0) {
						i.set(0);
						j.getAndIncrement();
					}
					i.getAndIncrement();
					grid.add(chart, i.get(), j.get());
					MousePressed.mouseControle((Pane) chart.getParent(), chart);
				}
			});
			// add the grid to the Vbox
			vboxAnaliser.getChildren().add(grid);
		} else {
			vboxAnaliser.getChildren()
					.add(new Text("Repository of data for capitals' trends across scenarios is not exite."
							+ "To create it go to Menu \"Edit\" -> \" Generate input data analysis directory\" "));
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
