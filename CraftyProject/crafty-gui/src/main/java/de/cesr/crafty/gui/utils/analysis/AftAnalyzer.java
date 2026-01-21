package de.cesr.crafty.gui.utils.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import de.cesr.crafty.gui.utils.graphical.LineChartTools;
import de.cesr.crafty.gui.utils.graphical.MousePressed;
import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.crafty.ManagerTypes;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.CapitalUpdater;
import de.cesr.crafty.gui.utils.graphical.SaveAs;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.Pane;

public class AftAnalyzer {

//	public static void main(String[] args) {
//		MainHeadless.modelInitialisation();
//		Manager a = AFTsLoader.getAftHash().values().iterator().next();
//		System.out.println(a.getLabel());
////		productivitySample(5000, 100, a);
//	}

	static ConcurrentHashMap<String, Double> capitalRandomGenerator() {
		ConcurrentHashMap<String, Double> RadnomCapitalSample = new ConcurrentHashMap<>();
		CapitalUpdater.getCapitalsList().forEach(cn -> {
			RadnomCapitalSample.put(cn, Math.random());
		});
		return RadnomCapitalSample;
	}

	static ConcurrentHashMap<String, Double> productivityCalculator(Aft... a) {
		ConcurrentHashMap<String, Double> CapitalVector = capitalRandomGenerator();
		if (a != null) {
			Cell c = new Cell(0, 0);
			c.getCapitals().putAll(CapitalVector);
			ConcurrentHashMap<String, Double> services = new ConcurrentHashMap<>();
			for (int i = 0; i < a.length; i++) {
				Aft manager = a[i];
				ServiceSet.getServicesList().forEach(s -> {
					double product = c.getCapitals().entrySet().stream()
							.mapToDouble(e -> (manager.getSensByService().get(s).get(e.getKey()) != null
									? Math.pow(e.getValue(), manager.getSensByService().get(s).get(e.getKey()))
									: 0))
							.reduce(1.0, (x, y) -> x * y);
					services.put(manager.getLabel() + "_" + s, product * manager.getProductivityLevel().get(s));
				});
			}
			return services;
		}
		return null;
	}

	static ConcurrentHashMap<String, Double> productivityCalculatorByservice(String serviceName) {
		ConcurrentHashMap<String, Double> CapitalVector = capitalRandomGenerator();
		Cell c = new Cell(0, 0);
		c.getCapitals().putAll(CapitalVector);
		ConcurrentHashMap<String, Double> aftsP = new ConcurrentHashMap<>();
		AFTsLoader.getActivateAFTsHash().values().forEach(manager -> {
			if (manager.getType() != ManagerTypes.Abandoned) {
//				System.out.println("@@  "+manager.getLabel()+"-> "+manager.getSensByService());
				double product = c.getCapitals().entrySet().stream().mapToDouble(
						e -> Math.pow(e.getValue(), manager.getSensByService().get(serviceName).get(e.getKey())))
						.reduce(1.0, (x, y) -> x * y);
				aftsP.put(manager.getLabel() + "_" + serviceName,
						product * manager.getProductivityLevel().get(serviceName));
			}
		});
		return aftsP;
	}

	public static Map<String, List<Double>> productivitySampleByAFTs(int sampleSize, int subIntervalNbr, Aft... a) {
		Set<ConcurrentHashMap<String, Double>> set = new HashSet<>();
		for (int i = 0; i < sampleSize; i++) {
			set.add(productivityCalculator(a));
		}
		Map<String, List<Double>> fq = new HashMap<>();
		for (int i = 0; i < a.length; i++) {
			Aft manager = a[i];
			ServiceSet.getServicesList().forEach(s -> {
				ArrayList<Double> numbers = new ArrayList<>();
				set.forEach(hash -> {
					numbers.add(hash.get(manager.getLabel() + "_" + s));
				});
				ArrayList<Double> intList = logNumbersInIntervals(numbers, subIntervalNbr);
				if (!isAllZero(intList))
					fq.put(/* manager.getLabel() + "_" + */ s, intList);
			});
		}
		return fq;
	}

	public static Map<String, List<Double>> productivitySampleByServices(int sampleSize, int subIntervalNbr,
			String service) {
		Set<ConcurrentHashMap<String, Double>> set = new HashSet<>();
		for (int i = 0; i < sampleSize; i++) {
			set.add(productivityCalculatorByservice(service));
		}
		Map<String, List<Double>> fq = new HashMap<>();

		AFTsLoader.getActivateAFTsHash().values().forEach(manager -> {
			if (manager.getType() != ManagerTypes.Abandoned) {

				ArrayList<Double> numbers = new ArrayList<>();
				set.forEach(hash -> {
					numbers.add(hash.get(manager.getLabel() + "_" + service));
				});
				ArrayList<Double> intList = logNumbersInIntervals(numbers, subIntervalNbr);
				if (!isAllZero(intList))
					fq.put(/* manager.getLabel() + "_" + */ manager.getLabel(), intList);

			}
		});
		return fq;
	}

	public static ArrayList<Double> logNumbersInIntervals(ArrayList<Double> numbers, int intervalNBR) {
		int[] counts = new int[(int) (intervalNBR) + 1];
		for (Double number : numbers) {
			number = number <= 1 ? number : 1;
			counts[(int) (number * intervalNBR)]++;
		}
		ArrayList<Double> result = new ArrayList<>();
		for (int count : counts) {
			result.add(Math.log(count + 1));
		}
		return result;
	}

	public static boolean isAllZero(ArrayList<Double> list) {
		for (int i = 1; i < list.size(); i++) {
			if (list.get(i) != 0.0) {
				return false;
			}
		}

		return true;
	}

	public static AreaChart<Number, Number> generateAreaChart(String titel, Map<String, List<Double>> data) {
		AreaChart<Number, Number> chart = new AreaChart<>(new NumberAxis(), new NumberAxis());
		chart.setTitle(titel);
		areachart(chart, data, titel);
		String ItemName = "Save as CSV";
		Consumer<String> action = _ -> {
			SaveAs.exportLineChartDataToCSV(chart);
		};
		HashMap<String, Consumer<String>> othersMenuItems = new HashMap<>();
		othersMenuItems.put(ItemName, action);
		LineChartTools.strokeColor(chart, data);
		return chart;
	}

	public static void areachart(AreaChart<Number, Number> lineChart, Map<String, List<Double>> data, String titel) {
		List<XYChart.Series<Number, Number>> seriesList = new ArrayList<>();
		AtomicInteger i = new AtomicInteger();
		List<String> sortedKeys = new ArrayList<>(data.keySet());
		Collections.sort(sortedKeys);
		for (String key : sortedKeys) {
			ArrayList<Double> value = (ArrayList<Double>) data.get(key);
			if (value != null) {
				XYChart.Series<Number, Number> series = new XYChart.Series<>();
				series.setName(key);
				lineChart.getData().add(series);
				seriesList.add(series);
				i.getAndIncrement();
			}
		}

		AtomicInteger k = new AtomicInteger();
		sortedKeys.forEach((key) -> {
			ArrayList<Double> value = (ArrayList<Double>) data.get(key);
			for (int j = 0; j < value.size(); j++) {
				seriesList.get(k.get()).getData().add(new XYChart.Data<>(j, value.get(j)));
			}
			k.getAndIncrement();
		});

		MousePressed.mouseControle((Pane) lineChart.getParent(), lineChart, titel);
		LineChartTools.addSeriesTooltips(lineChart);
	}

}
