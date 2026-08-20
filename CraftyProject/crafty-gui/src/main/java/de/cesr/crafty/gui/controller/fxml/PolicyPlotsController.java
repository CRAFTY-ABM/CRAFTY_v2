package de.cesr.crafty.gui.controller.fxml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import de.cesr.crafty.core.cli.Config;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.gui.institutes.InstitutionViewModel;
import de.cesr.crafty.gui.institutes.Institutes_Set;
import de.cesr.crafty.gui.institutes.PolicyViewModel;
import de.cesr.crafty.gui.utils.graphical.LineChartTools;
import de.cesr.crafty.gui.utils.graphical.MousePressed;
import de.cesr.crafty.gui.utils.graphical.Tools;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class PolicyPlotsController {

	@FXML
	private VBox TopBox;
	@FXML
	private GridPane gridPaneLinnChart;
	@FXML
	private ScrollPane scroll;

	private static PolicyPlotsController instance;
	private final Map<String, LineChart<Number, Number>> policyChartsByInstitute = new LinkedHashMap<>();
	private final Map<String, Map<String, Series<Number, Number>>> policySeriesByInstitute = new LinkedHashMap<>();
	private ArrayList<LineChart<Number, Number>> lineChart;

	public PolicyPlotsController() {
		instance = this;
	}

	public static void refreshAll() {
		if (instance != null) {
			instance.updatePolicyCharts();
		}
	}

	public static void resetAll() {
		if (instance != null) {
			instance.resetCharts();
		}
	}

	public void initialize() {
		lineChart = new ArrayList<>();
		initilaseChart(lineChart);
		initializeGridpane(3);
		Tools.forceResisingWidth(TopBox);
		Tools.forceResisingHeight(1, scroll);
	}

	private void initilaseChart(ArrayList<LineChart<Number, Number>> lineChart) {
		lineChart.clear();
		policyChartsByInstitute.clear();
		policySeriesByInstitute.clear();
		Institutes_Set.getInstitutes().values().forEach(this::addPolicyChart);
	}

	private void addPolicyChart(InstitutionViewModel institute) {
		LineChart<Number, Number> chart = new LineChart<>(
				new NumberAxis(Timestep.getStartYear(), Timestep.getEndtYear(), 5), new NumberAxis());
		chart.setTitle("Policy values - " + institute.getName());
		chart.setCreateSymbols(false);
		configureChartSize(chart);
		LineChartTools.configurexAxis(chart, Timestep.getStartYear(), Timestep.getEndtYear());

		Map<String, Series<Number, Number>> seriesByPolicy = new LinkedHashMap<>();
		for (PolicyViewModel policy : institute.getPolicies().values()) {
			Series<Number, Number> series = new XYChart.Series<Number, Number>();
			series.setName(policy.getName());
			chart.getData().add(series);
			seriesByPolicy.put(policy.getName(), series);
		}

		LineChartTools.addSeriesTooltips(chart);
		policyChartsByInstitute.put(institute.getName(), chart);
		policySeriesByInstitute.put(institute.getName(), seriesByPolicy);
		lineChart.add(chart);
	}

	private void updatePolicyCharts() {
		if (chartsNeedRebuild()) {
			resetCharts();
		}

		if (Config.chart_synchronisation && (Timestep.getTick() % Config.chart_synchronisation_gap == 0
				|| Timestep.getCurrentYear() == Timestep.getEndtYear())) {
			policySeriesByInstitute.forEach((instituteName, seriesByPolicy) -> {
				InstitutionViewModel institute = Institutes_Set.getInstitutes().get(instituteName);
				if (institute == null) {
					return;
				}
				seriesByPolicy.forEach((policyName, series) -> {
					PolicyViewModel policy = institute.getPolicies().get(policyName);
					if (policy != null) {
						series.getData().add(new XYChart.Data<>(Timestep.getCurrentYear(),
								policy.snapshot().effectiveValue()));
					}
				});
			});
		}
	}

	private boolean chartsNeedRebuild() {
		return !policyChartsByInstitute.keySet().equals(Institutes_Set.getInstitutes().keySet());
	}

	private void resetCharts() {
		gridPaneLinnChart.getChildren().clear();
		initilaseChart(lineChart);
		initializeGridpane(3);
	}

	private void initializeGridpane(int colmunNBR) {
		gridPaneLinnChart.setHgap(8);
		gridPaneLinnChart.setVgap(8);
		gridPaneLinnChart.setMaxWidth(Double.MAX_VALUE);
		if (gridPaneLinnChart.getColumnConstraints().isEmpty()) {
			for (int column = 0; column < colmunNBR; column++) {
				ColumnConstraints constraints = new ColumnConstraints();
				constraints.setPercentWidth(100.0 / colmunNBR);
				constraints.setHgrow(Priority.ALWAYS);
				constraints.setFillWidth(true);
				gridPaneLinnChart.getColumnConstraints().add(constraints);
			}
		}
		for (int m = 0; m < lineChart.size(); m++) {
			LineChart<Number, Number> chart = lineChart.get(m);
			gridPaneLinnChart.add(chart, m % colmunNBR, m / colmunNBR);
			GridPane.setHgrow(chart, Priority.ALWAYS);
			MousePressed.mouseControle(gridPaneLinnChart, chart, chart.getTitle());
		}
	}

	private void configureChartSize(LineChart<Number, Number> chart) {
		chart.getStyleClass().add("analysis-chart");
		chart.setMinWidth(0);
		chart.setPrefWidth(280);
		chart.setMaxWidth(Double.MAX_VALUE);
		// Keep only a readable minimum. JavaFX can grow the chart when a long
		// policy legend needs additional wrapped rows.
		chart.setMinHeight(230);
		chart.setAnimated(false);
	}
}
