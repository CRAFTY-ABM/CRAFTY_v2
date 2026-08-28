package de.cesr.crafty.gui.utils.analysis;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.modelRunner.ModelRunner;
import de.cesr.crafty.core.updaters.CapitalUpdater;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.gui.utils.graphical.LineChartTools;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Series;

public class CapitalsAnalyzer {

//	public static void main(String[] args) {
//		MainHeadless.initializeConfig(args);
//		ProjectLoader.modelInitialisation();
//		generateGrapheData();

//	}

	static ConcurrentHashMap<String, Double> mapToValues(int year) {
		Timestep.setCurrentYear(year);
		System.out.println(ConfigLoader.config.scenario + " scenario is running for year: " + year);
		ModelRunner.capitalUpdater = new CapitalUpdater();
		ModelRunner.capitalUpdater.step();
		ConcurrentHashMap<String, Double> capiHash = new ConcurrentHashMap<>();

		CellsLoader.hashCell.values().forEach(c -> {
			c.getCapitals().forEach((cn, cv) -> {
				capiHash.merge(cn, cv, Double::sum);
			});
		});
		System.out.println( CellsLoader.getNbrOfCells() + " cells in the map for year: " + year);
		CapitalUpdater.getCapitalsList().forEach(cn -> {
			capiHash.put(cn, capiHash.get(cn) / CellsLoader.getNbrOfCells());
		});

		return capiHash;
	}

	static Map<String, ArrayList<Double>> generateGrapheData() {
		Map<String, ArrayList<Double>> hash = new ConcurrentHashMap<>();

		CapitalUpdater.getCapitalsList().forEach(nc -> {
			hash.put(nc, new ArrayList<Double>());
		});

		for (int i = Timestep.getStartYear(); i < Timestep.getEndtYear(); i++) {
			ConcurrentHashMap<String, Double> h = mapToValues(i);
			h.forEach((cn, cv) -> {
				hash.get(cn).add(cv);
			});
		}
		return hash;
	}

	public static void generateGrapheDataByScenarios(String outputPath) {
		Map<String, Map<String, ArrayList<Double>>> hash = new ConcurrentHashMap<>();
		ProjectLoader.getScenariosList().forEach(scenario -> {
			if (!scenario.equals("Baseline")) {
				ProjectLoader.setScenario(scenario);
				System.out.println("Reading the capitals map for scenario:  " + scenario);
				hash.put(scenario, generateGrapheData());
			}
		});

		CapitalUpdater.getCapitalsList().forEach(capitalName -> {
			Map<String, List<Double>> data = new ConcurrentHashMap<>();
			ProjectLoader.getScenariosList().forEach(scenario -> {

				if (!scenario.equals("Baseline")) {
					data.put(scenario, hash.get(scenario).get(capitalName));
				}
			});
			CsvTools.writeCSVfile(data, Paths.get(outputPath + capitalName + ".csv"));
			// generateChart(data, capitalName);
		});
	}

	public static LineChart<Number, Number> generateCapitalChart(String titel, Map<String, List<Double>> data) {
		LineChart<Number, Number> chart = new LineChart<>(new NumberAxis(), new NumberAxis());
		chart.setTitle(titel);
		lineChart(chart, data, titel);

		LineChartTools.configurexAxis(chart, Timestep.getStartYear(), Timestep.getEndtYear());
		double minY = getMinimumValue(data);
		double maxY = getMaximumValue(data);
		LineChartTools.configurexYxis(chart, minY, maxY);
		return chart;
	}

	public static void lineChart(LineChart<Number, Number> lineChart, Map<String, List<Double>> hash,
			String titel) {

		List<Series<Number, Number>> series = new ArrayList<>();

		AtomicInteger i = new AtomicInteger();
		List<String> sortedKeys = new ArrayList<>(hash.keySet());
		Collections.sort(sortedKeys);
		for (String key : sortedKeys) {
			List<Double> value = hash.get(key);
			if (value != null) {
				// Create a new series for each key
				Series<Number, Number> s = new XYChart.Series<Number, Number>();
				s.setName(key);
				series.add(s);
				lineChart.getData().add(series.get(i.get()));
				i.getAndIncrement();
			}
		}

		AtomicInteger k = new AtomicInteger();
		sortedKeys.forEach((key) -> {
			List<Double> value = hash.get(key);
			for (int j = 0; j < value.size(); j++) {
				series.get(k.get()).getData().add(new XYChart.Data<>(j + Timestep.getStartYear(), value.get(j)));
			}
			k.getAndIncrement();
		});

		LineChartTools.addSeriesTooltips(lineChart);
	}

	public static double getMinimumValue(Map<String, List<Double>> hash) {
		double min = Double.MAX_VALUE;

		for (Map.Entry<String, List<Double>> entry : hash.entrySet()) {
			for (double val : entry.getValue()) {
				if (val < min) {
					min = val;
				}
			}
		}
		return min;
	}

	public static double getMaximumValue(Map<String, List<Double>> hash) {
		double max = Double.MIN_VALUE;

		for (Map.Entry<String, List<Double>> entry : hash.entrySet()) {
			for (double val : entry.getValue()) {
				if (val > max) {
					max = val;
				}
			}
		}
		return max;
	}
}
