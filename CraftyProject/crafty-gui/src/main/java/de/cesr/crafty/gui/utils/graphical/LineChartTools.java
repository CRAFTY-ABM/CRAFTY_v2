package de.cesr.crafty.gui.utils.graphical;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.modelRunner.ModelRunner;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.gui.utils.analysis.LoessStandalone;
import de.cesr.crafty.gui.utils.analysis.MixedLineChart;
import de.cesr.crafty.gui.utils.analysis.MultiShadowLineChart;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * @author Mohamed Byari
 *
 */

public class LineChartTools {

	public void lineChart(Pane box, LineChart<Number, Number> lineChart, Map<String, List<Double>> hash) {
		if (hash == null) {
			return;
		}
		configurexAxis(lineChart, Timestep.getStartYear(), Timestep.getEndtYear());
		lineChart.getData().clear();
		List<Series<Number, Number>> series = new ArrayList<>();

		AtomicInteger i = new AtomicInteger();
		List<String> sortedKeys = new ArrayList<>(hash.keySet());

		Collections.sort(sortedKeys);

		for (String key : sortedKeys) {
			ArrayList<Double> value = (ArrayList<Double>) hash.get(key);
			if (value != null) {
				XYChart.Series<Number, Number> s = new XYChart.Series<>();
				s.setName(key);
				series.add(s);
				lineChart.getData().add(series.get(i.get()));
				i.getAndIncrement();
			}
		}

		AtomicInteger k = new AtomicInteger();
		sortedKeys.forEach((key) -> {
			ArrayList<Double> value = (ArrayList<Double>) hash.get(key);
			if (value != null) {
				for (int j = 0; j < value.size(); j++) {
					series.get(k.get()).getData().add(new XYChart.Data<>(
							j + ((NumberAxis) lineChart.getXAxis()).getLowerBound(), (Number) (value.get(j))));
				}
				k.getAndIncrement();
			}
		});
		strokeColor(lineChart, hash);
		LineChartTools.addSeriesTooltips(lineChart);
		NumberAxis yAxis = ((NumberAxis) lineChart.getYAxis());
		yAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(yAxis) {
			@Override
			public String toString(Number object) {
				return String.format("%.2f", object.doubleValue());
			}
		});

	}

	public static void strokeColor(XYChart<Number, Number> lineChart, Map<String, List<Double>> hash) {
		List<String> sortedKeys = new ArrayList<>(hash.keySet());
		Collections.sort(sortedKeys);
		if (hash.size() > 8) {
			AtomicInteger K = new AtomicInteger();
			sortedKeys.forEach((key) -> {
				ArrayList<Double> value = (ArrayList<Double>) hash.get(key);
				if (value != null) {
					for (int j = 0; j < value.size(); j++) {
						lineChart.getData().get(K.get()).getData().add(new XYChart.Data<>(j, +value.get(j)));
						lineChart.getData().get(K.get()).getNode().lookup(".chart-series-line").setStyle(
								"-fx-stroke: " + ColorsTools.getStringColor(ColorsTools.colorlist(K.get())) + ";");
					}
					K.getAndIncrement();
				}
			});
			if (ModelRunner.cellsSet != null)
				labelcolor(lineChart);
			((LineChart<Number, Number>) lineChart).setCreateSymbols(false);
		}
	}

	public static void addSeriesTooltips(XYChart<Number, Number> lineChart) {
		for (XYChart.Series<Number, Number> series : lineChart.getData()) {
			// Building the tooltip text
			String tooltipText = "Series: " + series.getName() + "\nData Points: " + series.getData().size();
			// Set the tooltip for the line
			Tooltip seriesTooltip = new Tooltip(tooltipText);
			seriesTooltip.setShowDelay(Duration.millis(100));
			Tooltip.install(series.getNode(), seriesTooltip);

			// Apply the same tooltip to each data point in the series
			for (XYChart.Data<Number, Number> data : series.getData()) {
				Tooltip dataPointTooltip = new Tooltip(tooltipText);
				dataPointTooltip.setShowDelay(Duration.millis(100));
				if (data.getNode() != null) {
					Tooltip.install(data.getNode(), dataPointTooltip);
					// Optional: Highlight data points when hovered
					data.getNode()
							.setOnMouseEntered(_ -> data.getNode().setStyle("-fx-scale-x: 1.5; -fx-scale-y: 1.5;"));
					data.getNode().setOnMouseExited(_ -> data.getNode().setStyle("-fx-scale-x: 1; -fx-scale-y: 1;"));
				}
			}
		}
	}

	public static void labelcolor(XYChart<Number, Number> lineChart) {
		int m = 0;
		for (Node item : lineChart.lookupAll("Label.chart-legend-item")) {
			Label label = (Label) item;
			Color co = AFTsLoader.getAftHash().get(label.getText()) != null
					? Color.web(AFTsLoader.getAftHash().get(label.getText()).getColor())
					: ColorsTools.colorlist(m);
			final Rectangle rectangle = new Rectangle(10, 10, co);
			label.setGraphic(rectangle);
			m++;
		}
	}

	public static void configurexAxis(LineChart<Number, Number> demandsChart, int start, int end) {
		NumberAxis xAxis = ((NumberAxis) demandsChart.getXAxis());
		xAxis.setAutoRanging(false);
		xAxis.setLowerBound(start);
		xAxis.setUpperBound(end);
		xAxis.setTickUnit((end - start) / 10);
		xAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(xAxis) {
			@Override
			public String toString(Number object) {
				return String.format("%d", object.intValue());
			}
		});
	}

	public static void configurexYxis(LineChart<Number, Number> demandsChart, double start, double end) {
		NumberAxis yAxis = ((NumberAxis) demandsChart.getYAxis());
		yAxis.setAutoRanging(false);
		yAxis.setLowerBound(start);
		yAxis.setUpperBound(end);
		yAxis.setTickUnit((end - start) / 10);
		yAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(yAxis) {
			@Override
			public String toString(Number object) {
				// Use %.3f to format to 3 decimal places
				return String.format("%.2f", object.doubleValue());
			}
		});
	}

	public static LineChart<Number, Number> createLineChartWithSmoothLines(String titel,
			Map<String, List<Double>> points, boolean withShade) {
		Map<String, Color> colors = new HashMap<>();
		AtomicInteger i = new AtomicInteger();
		points.keySet().forEach(name -> {
			colors.put(name, ColorsTools.colorlist(i.getAndIncrement()));
		});

		return LineChartTools.createLineChartWithSmoothLines(titel, points, colors, withShade);
	}

	public static LineChart<Number, Number> createLineChartWithSmoothLines(String titel,
			Map<String, List<Double>> points, Map<String, Color> colors, boolean withShade) {
		Map<String, List<Double>> lines = new HashMap<>();
		points.forEach((k, v) -> {
			lines.put(k, loess_smoothing_data(v));
			// colors.put(k + "|", colors.get(k));
		});
		Map<String, List<Double>> points2 = new HashMap<>();
		points.forEach((k, v) -> {
			List<Double> nv = new ArrayList<>();
			for (int i = 0; i < v.size(); i++) {
				nv.add(v.get(i) != 0 ? Math.sqrt(Math.abs(v.get(i) - lines.get(k).get(i))) : 0);
			}

			points2.put(k, nv);
		});
		LineChart<Number, Number> chart = new MultiShadowLineChart(titel, lines, points2, colors);//
		lines.clear();
		points.forEach((k, v) -> {
			lines.put(k + "|", loess_smoothing_data(v));
			colors.put(k + "|", colors.get(k));
		});

		if (!withShade) {
			chart = MixedLineChart.mixedLineChart(titel, lines, points, colors);
			// LineChartTools.addSeriesTooltips(chart);
		}

		return chart;
	}

	private static ArrayList<Double> loess_smoothing_data(List<Double> input) {
		LoessStandalone.loessSmoothingData(input);
		ArrayList<Double> output = LoessStandalone.loessSmoothingData(input);
		return output;
	}

}
