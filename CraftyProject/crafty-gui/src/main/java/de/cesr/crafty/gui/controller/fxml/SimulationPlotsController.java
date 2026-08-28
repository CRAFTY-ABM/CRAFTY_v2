package de.cesr.crafty.gui.controller.fxml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import de.cesr.crafty.core.cli.Config;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.SupplyUpdater;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.gui.utils.graphical.ColorsTools;
import de.cesr.crafty.gui.utils.graphical.LineChartTools;
import de.cesr.crafty.gui.utils.graphical.MousePressed;
import de.cesr.crafty.gui.utils.graphical.SaveAs;
import de.cesr.crafty.gui.utils.graphical.Tools;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.Node;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class SimulationPlotsController {

	@FXML
	private VBox TopBox;
	@FXML
	private GridPane gridPaneLinnChart;
	@FXML
	private ScrollPane scroll;

	private static SimulationPlotsController instance;
	private ArrayList<LineChart<Number, Number>> lineChart;
	private LineChart<Number, Number> aftChart;
	private boolean logarithmicAftPopulation;

	public SimulationPlotsController() {
		instance = this;
	}

	public static void refreshAll() {
		if (instance != null) {
			instance.updateSupplyDemandLineChart();
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
			int column = m % colmunNBR;
			int row = m / colmunNBR;
			Node chartView = chart == aftChart ? createAftChartView() : chart;
			gridPaneLinnChart.add(chartView, column, row);
			GridPane.setHgrow(chartView, Priority.ALWAYS);
			attachChartMenu(chart);
		}
	}

	private VBox createAftChartView() {
		CheckBox logarithmic = new CheckBox("Logarithmic population (Y)");
		logarithmic.setSelected(logarithmicAftPopulation);
		logarithmic.setOnAction(_ -> setAftPopulationLogarithmic(logarithmic.isSelected()));
		VBox view = new VBox(2, logarithmic, aftChart);
		VBox.setVgrow(aftChart, Priority.ALWAYS);
		view.setMaxWidth(Double.MAX_VALUE);
		return view;
	}

	private void setAftPopulationLogarithmic(boolean logarithmic) {
		logarithmicAftPopulation = logarithmic;
		((NumberAxis) aftChart.getYAxis()).setLabel(logarithmic
				? "log10(population + 1)"
				: "Population");
		for (Series<Number, Number> series : aftChart.getData()) {
			for (XYChart.Data<Number, Number> point : series.getData()) {
				Number population = (Number) point.getExtraValue();
				if (population != null) {
					point.setYValue(displayAftPopulation(population.intValue()));
				}
			}
		}
	}

	private double displayAftPopulation(int population) {
		return logarithmicAftPopulation ? Math.log10(population + 1.0) : population;
	}

	void initilaseChart(ArrayList<LineChart<Number, Number>> lineChart) {
		aftChart = null;
		logarithmicAftPopulation = false;
		ServiceSet.getServicesList().forEach(service -> {
			Series<Number, Number> s1 = new XYChart.Series<Number, Number>();
			Series<Number, Number> s2 = new XYChart.Series<Number, Number>();
			s1.setName("Demand");
			s2.setName("Supply");
			LineChart<Number, Number> l = new LineChart<>(
					new NumberAxis(Timestep.getStartYear(), Timestep.getEndtYear(), 5), new NumberAxis());
			configureChartSize(l);
			l.getStyleClass().add("simulation-service-chart");
			l.setTitle(service);
			l.setLegendVisible(false);
			l.setCreateSymbols(false);
			l.getData().add(s1);
			l.getData().add(s2);
			LineChartTools.configurexAxis(l, Timestep.getStartYear(), Timestep.getEndtYear());
			lineChart.add(l);
			LineChartTools.addSeriesTooltips(l);

		});

		LineChart<Number, Number> l = new LineChart<>(
				new NumberAxis(Timestep.getStartYear(), Timestep.getEndtYear(), 5), new NumberAxis());
		aftChart = l;
		configureChartSize(l);
		l.getStyleClass().add("simulation-aft-chart");
		l.setTitle("AFT populations");
		((NumberAxis) l.getYAxis()).setLabel("Population");
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
		LineChartTools.labelcolor(l);
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

	private void attachChartMenu(LineChart<Number, Number> chart) {
		HashMap<String, Consumer<String>> actions = new HashMap<>();
		actions.put("Save as CSV", _ -> SaveAs.exportLineChartDataToCSV(chart));
		MousePressed.mouseControle(gridPaneLinnChart, chart, actions, chart.getTitle());
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
			ObservableList<Series<Number, Number>> observable = aftChart.getData();
			List<String> listofNames = observable.stream().map(Series::getName).collect(Collectors.toList());
			AFTsLoader.hashAgentNbr.forEach((name, value) -> {
				int seriesIndex = listofNames.indexOf(name);
				if (seriesIndex >= 0) {
					XYChart.Data<Number, Number> point = new XYChart.Data<>(completedYear,
							displayAftPopulation(value));
					point.setExtraValue(value);
					observable.get(seriesIndex).getData().add(point);
				}
			});
		}
	}

	private void resetCharts() {
		gridPaneLinnChart.getChildren().clear();
		lineChart.clear();
		initilaseChart(lineChart);
		initializeGridpane(3);
	}
}
