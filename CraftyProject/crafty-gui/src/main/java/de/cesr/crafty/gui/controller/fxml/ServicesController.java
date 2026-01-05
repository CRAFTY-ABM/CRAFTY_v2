package de.cesr.crafty.gui.controller.fxml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.CapitalUpdater;
import de.cesr.crafty.gui.utils.analysis.AftAnalyzer;
import de.cesr.crafty.gui.utils.graphical.Histogram;
import de.cesr.crafty.gui.utils.graphical.LineChartTools;
import de.cesr.crafty.gui.utils.graphical.MousePressed;
import de.cesr.crafty.gui.utils.graphical.SaveAs;
import de.cesr.crafty.gui.utils.graphical.Tools;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

public class ServicesController {
	@FXML
	private VBox TopBox;
	@FXML
	private HBox hbox;
	@FXML
	private HBox hboxDemandWeight;
	@FXML
	private VBox vboxForSliders;
	@FXML
	private BarChart<String, Number> histoCapitalS;
	@FXML
	private BarChart<String, Number> histoAftProductivity;
	@FXML
	private BarChart<String, Number> histoAftSensitivity;

	@FXML
	private LineChart<Number, Number> demandsChart;
//	@FXML
//	private LineChart<Number, Number> weightsChart;

	public static List<RadioButton> radioService = new ArrayList<>();

	private static ServicesController instance;

	public ServicesController() {
		instance = this;
	}

	public static ServicesController getInstance() {
		return instance;
	}

	public LineChart<Number, Number> getDemandsChart() {
		return demandsChart;
	}

	public void initialize() {
		System.out.println("initialize " + getClass().getSimpleName());

		new LineChartTools().lineChart((Pane) demandsChart.getParent(), demandsChart, serialisationWorldDemand());
		String ItemName = "Save as CSV";
		Consumer<String> action = _ -> {
			SaveAs.exportLineChartDataToCSV(demandsChart);
		};
		HashMap<String, Consumer<String>> othersMenuItems = new HashMap<>();
		othersMenuItems.put(ItemName, action);
		MousePressed.mouseControle((Pane) demandsChart.getParent(), demandsChart, othersMenuItems);
//		MousePressed.mouseControle((Pane) weightsChart.getParent(), weightsChart, othersMenuItems);
//		// =====
//		new LineChartTools().lineChart((Pane) weightsChart.getParent(), weightsChart,
//				ServiceWeightLoader.serialisationWorldWeight());

		Tools.forceResisingWidth(TopBox, hboxDemandWeight);

		demandsChart.setMinWidth(TopBox.getMinWidth());
//		weightsChart.setMinWidth(TopBox.getMinWidth() / 2);

		Tools.forceResisingWidth(0.1, vboxForSliders);
//		updatehistograms(0);

		radiosInitialisation();
		radioService.get(0).fire();
	}

	private void radiosInitialisation() {
		ToggleGroup radiosgroup = new ToggleGroup();
		ServiceSet.getServicesList().forEach(serviceName -> {
			RadioButton r = new RadioButton(serviceName);
			radioService.add(r);

			r.setOnAction(_ -> {
				updatehistograms(serviceName);
				TopBox.getChildren().removeIf(node -> ("randomCapitalServiceChart".equals(node.getId())));
				LineChart<Number, Number> chart = productivitySampleChartService(serviceName, true);
				if (chart != null)
					TopBox.getChildren().add(chart);

			});
			r.setToggleGroup(radiosgroup);
			vboxForSliders.getChildren().add(r);
		});
	}

	private void updatehistograms(String serviceName) {
		if (!ProjectLoader.getScenario().equalsIgnoreCase("Baseline")) {
			updateHistoService(serviceName);
			updateHistoAftSensitivity(serviceName);
		}

	}

	void updateHistoService(String serviceName) {
		// initialise container
		Map<String, Double> hash = new HashMap<>();
		Map<String, Double> counter = new HashMap<>();

		for (String capitalName : CapitalUpdater.getCapitalsList()) {
//			boolean isCapitalsumNotNull = false;

			for (Aft a : AFTsLoader.getActivateAFTsHash().values()) {
				if (a.getSensByService().get(serviceName) != null
						&& a.getSensByService().get(serviceName).get(capitalName) != 0) {
					hash.merge(capitalName, a.getSensByService().get(serviceName).get(capitalName), Double::sum);
					counter.merge(capitalName, 1., Double::sum);
				}
			}
		}
		hash.forEach((key, value) -> {
			hash.put(key, value / counter.get(key));
		});

		Histogram.histo("Service (" + serviceName + ") Average sensitivity for Capitals", histoCapitalS, hash);
		Histogram.mouseHistogrameController(histoCapitalS);
	}

	void updateHistoAftSensitivity(String serviceName) {
		// initialise container
		Map<String, Double> hashS = new HashMap<>();
		Map<String, Double> hashP = new HashMap<>();

		// loop for AFTs
		AFTsLoader.getActivateAFTsHash().forEach((aftName, a) -> {
			// loop for Services
			CapitalUpdater.getCapitalsList().forEach(capitalName -> {
				// aggreagte by service
				if (a.getSensByService().get(serviceName) != null
						&& a.getSensByService().get(serviceName).get(capitalName) != 0) {
					hashS.merge(aftName, a.getSensByService().get(serviceName).get(capitalName), Double::sum);
				}
				if (a.getProductivityLevel().get(serviceName) != null
						&& a.getProductivityLevel().get(serviceName) != 0) {
					hashP.put(aftName, a.getProductivityLevel().get(serviceName));
				}
			});
		});

		Histogram.histo("Service (" + serviceName + ") aggregate sensitivity for AFTs", histoAftSensitivity, hashS);
		Histogram.mouseHistogrameController(histoAftSensitivity);
		Histogram.histo(serviceName + "Optimal Productivity Level", histoAftProductivity, hashP);
		Histogram.mouseHistogrameController(histoAftProductivity);
	}

	public static LineChart<Number, Number> productivitySampleChartService(String serviceName, boolean withShade) {
		Map<String, List<Double>> data = AftAnalyzer.productivitySampleByServices(1000, 100, serviceName);
		if (data.size() == 0) {
			return null;
		}

		LineChart<Number, Number> chart = LineChartTools.createLineChartWithSmoothLines(serviceName, data, withShade);
		chart.setAnimated(false);
		HashMap<String, Consumer<String>> othersMenuItems = new HashMap<>();
		Consumer<String> relaod = _ -> {
			Pane parent = (Pane) chart.getParent();
			parent.getChildren().removeIf(node -> ("randomCapitalServiceChart".equals(node.getId())));
			parent.getChildren().add(productivitySampleChartService(serviceName, withShade));
		};
		othersMenuItems.put("Reload and Update", relaod);
		Consumer<String> switchView = _ -> {
			Pane parent = (Pane) chart.getParent();
			parent.getChildren().removeIf(node -> ("randomCapitalServiceChart".equals(node.getId())));
			parent.getChildren().add(productivitySampleChartService(serviceName, !withShade));
		};

		othersMenuItems.put("Update and show the " + (!withShade ? " Deviation" : "Original Points"), switchView);

		MousePressed.mouseControle((Pane) chart.getParent(), chart, othersMenuItems);
		chart.setId("randomCapitalServiceChart");
		return chart;
	}

	// change the WorldDemand for plot
	public static Map<String, List<Double>> serialisationWorldDemand() {
		Map<String, List<Double>> serviceSerialisation = new HashMap<>();
		ServiceSet.worldService.forEach((serviceName, service) -> {
			serviceSerialisation.put(serviceName, new ArrayList<>(service.getDemands().values()));
		});
		return serviceSerialisation;
	}
}
