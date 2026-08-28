package de.cesr.crafty.gui.controller.fxml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import de.cesr.crafty.core.cli.Config;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.gui.institutes.TargetViewModel;
import de.cesr.crafty.gui.institutes.Targets_Set;
import de.cesr.crafty.gui.utils.graphical.LineChartTools;
import de.cesr.crafty.gui.utils.graphical.MousePressed;
import de.cesr.crafty.gui.utils.graphical.Tools;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class TargetsPlotController {
	private static final String OBSERVED_SERIES = "Observed";
	private static final String REFERENCE_SERIES = "Reference";
	private static final String GOAL_SERIES = "Goal";

	@FXML
	private VBox TopBox;
	@FXML
	private Label tickTxt;
	@FXML
	private ScrollPane scroll;
	@FXML
	private GridPane gridPaneLinnChart;

	private static TargetsPlotController instance;
	private final Map<String, LineChart<Number, Number>> targetCharts = new LinkedHashMap<>();
	private final Map<String, ArrayList<Integer>> plottedObservedYears = new HashMap<>();
	private ArrayList<LineChart<Number, Number>> lineChart;

	public TargetsPlotController() {
		instance = this;
	}

	public static void refreshAll() {
		if (instance != null) {
			instance.refreshTargetCharts();
		}
	}

	public static void updateAll() {
		if (instance != null) {
			instance.updateTargetLineChart();
		}
	}

	public static void resetAll() {
		if (instance != null) {
			instance.resetCharts();
		}
	}

	public void initialize() {
		tickTxt.setText(String.valueOf(Timestep.getCurrentYear()));
		lineChart = new ArrayList<>();
		Tools.forceResisingWidth(TopBox);
		Tools.forceResisingHeight(1, scroll);
		refreshTargetCharts();
	}

	@FXML
	public void refreshTargetCharts() {
		tickTxt.setText(String.valueOf(Timestep.getCurrentYear()));
		if (chartsNeedRebuild()) {
			initilaseChart(lineChart);
			initializeGridpane(3);
		}
		targetCharts.forEach((targetName, chart) -> refillChart(chart, Targets_Set.getTargets().get(targetName)));
	}

	private void updateTargetLineChart() {
		tickTxt.setText(String.valueOf(Timestep.getCurrentYear()));
		if (chartsNeedRebuild()) {
			initilaseChart(lineChart);
			initializeGridpane(3);
		}

		if (Config.chart_synchronisation && (Timestep.getTick() % Config.chart_synchronisation_gap == 0
				|| Timestep.getCurrentYear() == Timestep.getEndtYear())) {
			targetCharts.forEach((targetName, chart) -> appendObservedValues(chart, Targets_Set.getTargets().get(targetName)));
		}
	}

	private boolean chartsNeedRebuild() {
		return !Targets_Set.getTargets().isEmpty() && !targetCharts.keySet().equals(Targets_Set.getTargets().keySet());
	}

	private void resetCharts() {
		initilaseChart(lineChart);
		initializeGridpane(3);
		tickTxt.setText(String.valueOf(Timestep.getCurrentYear()));
	}

	private void initilaseChart(ArrayList<LineChart<Number, Number>> lineChart) {
		lineChart.clear();
		targetCharts.clear();
		plottedObservedYears.clear();
		gridPaneLinnChart.getChildren().clear();

		Targets_Set.getTargets().values().forEach(target -> {
			LineChart<Number, Number> chart = createTargetChart(target);
			targetCharts.put(target.getName(), chart);
			MousePressed.mouseControle(gridPaneLinnChart, chart);
			lineChart.add(chart);
		});
	}

	private LineChart<Number, Number> createTargetChart(TargetViewModel target) {
		NumberAxis xAxis = new NumberAxis(Timestep.getStartYear(), Timestep.getEndtYear(), 5);
		NumberAxis yAxis = new NumberAxis();
		LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
		chart.setTitle(target.getName());
		configureChartSize(chart);
//		chart.setCreateSymbols(false);

		XYChart.Series<Number, Number> values = new XYChart.Series<>();
		values.setName(OBSERVED_SERIES);
		chart.getData().add(values);

		if (!target.getReferenceHistory().isEmpty()) {
			XYChart.Series<Number, Number> baseline = new XYChart.Series<>();
			baseline.setName(REFERENCE_SERIES);
			chart.getData().add(baseline);
			target.getReferenceHistory().forEach((year, value) -> baseline.getData().add(new XYChart.Data<>(year, value)));
		}
		if (!target.getGoalHistory().isEmpty()) {
			XYChart.Series<Number, Number> goal = new XYChart.Series<>();
			goal.setName(GOAL_SERIES);
			chart.getData().add(goal);
			target.getGoalHistory().forEach((year, value) -> goal.getData().add(new XYChart.Data<>(year, value)));
		}

		LineChartTools.configurexAxis(chart, Timestep.getStartYear(), Timestep.getEndtYear());
		LineChartTools.addSeriesTooltips(chart);
		return chart;
	}

	private void refillChart(LineChart<Number, Number> chart, TargetViewModel target) {
		if (target == null || chart.getData().isEmpty()) {
			return;
		}

		var snapshot = target.snapshot();
		updateSeries(chart, OBSERVED_SERIES, snapshot.observedHistory());
		updateSeries(chart, REFERENCE_SERIES, snapshot.referenceHistory());
		updateSeries(chart, GOAL_SERIES, snapshot.goalHistory());
		plottedObservedYears.computeIfAbsent(target.getName(), _ -> new ArrayList<>()).clear();
		plottedObservedYears.get(target.getName()).addAll(target.getHistory().keySet());
	}

	private void appendObservedValues(LineChart<Number, Number> chart, TargetViewModel target) {
		if (target == null || chart.getData().isEmpty()) {
			return;
		}

		ArrayList<Integer> plottedYears = plottedObservedYears.computeIfAbsent(target.getName(), _ -> new ArrayList<>());
		chart.getData().stream().filter(series -> series.getName().equals(OBSERVED_SERIES)).findFirst().ifPresent(series -> {
			target.snapshot().observedHistory().forEach((year, value) -> {
				if (!plottedYears.contains(year)) {
					series.getData().add(new XYChart.Data<>(year, value));
					plottedYears.add(year);
				}
			});
		});
	}

	private void updateSeries(LineChart<Number, Number> chart, String name, Map<Integer, Double> values) {
		XYChart.Series<Number, Number> series = chart.getData().stream()
				.filter(candidate -> candidate.getName().equals(name))
				.findFirst()
				.orElse(null);

		if (values.isEmpty()) {
			if (series != null && !OBSERVED_SERIES.equals(name)) {
				chart.getData().remove(series);
			}
			return;
		}

		if (series == null) {
			series = new XYChart.Series<>();
			series.setName(name);
			if (REFERENCE_SERIES.equals(name)) {
				int goalIndex = findSeriesIndex(chart, GOAL_SERIES);
				chart.getData().add(goalIndex < 0 ? chart.getData().size() : goalIndex, series);
			} else {
				chart.getData().add(series);
			}
		}

		series.getData().clear();
		XYChart.Series<Number, Number> targetSeries = series;
		values.forEach((year, value) -> targetSeries.getData().add(new XYChart.Data<>(year, value)));
	}

	private int findSeriesIndex(LineChart<Number, Number> chart, String name) {
		for (int i = 0; i < chart.getData().size(); i++) {
			if (chart.getData().get(i).getName().equals(name)) {
				return i;
			}
		}
		return -1;
	}

	void initializeGridpane(int colmunNBR) {
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
		}
	}

	private void configureChartSize(LineChart<Number, Number> chart) {
		chart.getStyleClass().add("analysis-chart");
		chart.setMinWidth(0);
		chart.setPrefWidth(280);
		chart.setMaxWidth(Double.MAX_VALUE);
		chart.setMinHeight(190);
		chart.setPrefHeight(230);
		chart.setMaxHeight(230);
		chart.setAnimated(false);
	}
}
