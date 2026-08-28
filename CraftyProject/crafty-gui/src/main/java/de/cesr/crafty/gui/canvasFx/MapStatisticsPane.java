package de.cesr.crafty.gui.canvasFx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.cesr.crafty.core.cli.Config;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.afts.AftCategorised;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.land.GisLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.CapitalUpdater;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.gui.utils.graphical.ColorsTools;
import de.cesr.crafty.gui.utils.graphical.LineChartTools;
import de.cesr.crafty.gui.utils.graphical.NewWindow;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.layout.Pane;

/** A compact summary of the data represented by the current map layer. */
public final class MapStatisticsPane extends BorderPane {
	private static final int HISTOGRAM_BINS = 10;
	private static final int MAX_PIE_SLICES = 10;

	private static MapStatisticsPane instance;
	private static volatile boolean simulationRunning;

	private final Label title = new Label("Map statistics");
	private final Label summary = new Label();
	private final Button minimizeButton = new Button("Minimize");
	private final Button detachButton = new Button("Detach");
	private Node currentContent;
	private boolean minimized;
	private boolean detached;
	private SplitPane hostSplit;
	private int hostIndex;
	private double restoreDividerPosition = 0.60;
	private NewWindow detachedWindow;
	private final Map<String, List<AftPopulationPoint>> aftPopulationHistory = new LinkedHashMap<>();
	private String currentLayer = "AFT";
	private boolean aftPopulationLogarithmic;

	private record AftPopulationPoint(int year, int population) {
	}

	public MapStatisticsPane() {
		instance = this;
		getStyleClass().add("map-statistics-pane");

		title.getStyleClass().add("map-statistics-title");
		summary.getStyleClass().add("map-statistics-summary");
		title.setMinWidth(0);
		summary.setMinWidth(0);
		summary.setWrapText(true);
		minimizeButton.getStyleClass().add("map-statistics-action");
		detachButton.getStyleClass().add("map-statistics-action");
		minimizeButton.setOnAction(_ -> toggleMinimized());
		detachButton.setOnAction(_ -> toggleDetached());
		HBox spacer = new HBox();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox heading = new HBox(8, title, new Separator(), summary, spacer, minimizeButton, detachButton);
		heading.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
		heading.setPadding(new Insets(8, 12, 0, 12));
		setTop(heading);
		
		refresh(CellsCanvas.getColorType());
		
	}

	private void toggleMinimized() {
		if (detached) {
			return;
		}
		minimized = !minimized;
		SplitPane split = findHostSplit();
		if (split != null && !split.getDividers().isEmpty()) {
			restoreDividerPosition = split.getDividerPositions()[0];
		}
		if (minimized) {
			setCenter(null);
			summary.setVisible(false);
			summary.setManaged(false);
			setMinHeight(44);
			setPrefHeight(44);
			setMaxHeight(44);
			minimizeButton.setText("Restore");
			adjustDivider(0.95);
		} else {
			summary.setVisible(true);
			summary.setManaged(true);
			setMinHeight(230);
			setPrefHeight(300);
			setMaxHeight(Double.MAX_VALUE);
			setCenter(currentContent);
			minimizeButton.setText("Minimize");
			adjustDivider(Math.min(0.80, restoreDividerPosition));
		}
	}

	private void adjustDivider(double position) {
		Platform.runLater(() -> setHostDivider(position));
	}

	private void setHostDivider(double position) {
		SplitPane split = findHostSplit();
		if (split != null && !split.getDividers().isEmpty()) {
			split.setDividerPosition(0, position);
		}
	}

	private SplitPane findHostSplit() {
		Parent current = getParent();
		while (current != null) {
			if (current instanceof SplitPane split && split.getItems().contains(this)) {
				return split;
			}
			current = current.getParent();
		}
		return null;
	}

	private void toggleDetached() {
		if (detached) {
			restoreDetached();
			if (detachedWindow != null) {
				detachedWindow.close();
			}
			return;
		}
		SplitPane split = findHostSplit();
		if (split == null) {
			return;
		}
		hostSplit = split;
		hostIndex = split.getItems().indexOf(this);
		if (!split.getDividers().isEmpty()) {
			restoreDividerPosition = split.getDividerPositions()[0];
		}
		hostSplit.getItems().remove(this);
		detached = true;
		detachButton.setText("Reattach");
		minimizeButton.setDisable(true);
		setMinHeight(230);
		setMaxHeight(Double.MAX_VALUE);

		detachedWindow = new NewWindow();
		detachedWindow.creatwindows("Map statistics", 0.55, 0.60, this);
		detachedWindow.setMinWidth(640);
		detachedWindow.setMinHeight(360);
		var stylesheet = getClass().getResource("/styles.css");
		if (stylesheet != null) {
			detachedWindow.getScene().getStylesheets().add(stylesheet.toExternalForm());
		}
		detachedWindow.setOnCloseRequest(_ -> restoreDetached());
	}

	private void restoreDetached() {
		if (!detached || hostSplit == null) {
			return;
		}
		if (getParent() instanceof Pane detachedRoot) {
			detachedRoot.getChildren().remove(this);
		}
		hostSplit.getItems().add(Math.min(hostIndex, hostSplit.getItems().size()), this);
		detached = false;
		detachButton.setText("Detach");
		minimizeButton.setDisable(false);
		Platform.runLater(() -> hostSplit.setDividerPosition(0, restoreDividerPosition));
	}

	public static void refresh(String layer) {
		MapStatisticsPane pane = instance;
		if (pane == null) {
			return;
		}
		Runnable refresh = () -> pane.update(layer);
		if (Platform.isFxApplicationThread()) {
			refresh.run();
		} else {
			Platform.runLater(refresh);
		}
	}

	/** Switches the AFT statistics between its normal pie chart and the live run history. */
	public static void setSimulationRunning(boolean running) {
		MapStatisticsPane pane = instance;
		simulationRunning = running;
		if (pane == null) {
			return;
		}
		Runnable update = () -> {
			if (running) {
				pane.aftPopulationHistory.clear();
			}
			pane.update(pane.currentLayer);
		};
		if (Platform.isFxApplicationThread()) {
			update.run();
		} else {
			Platform.runLater(update);
		}
	}

	private void update(String layer) {
		String selectedLayer = layer == null || layer.isBlank() ? "AFT" : layer;
		currentLayer = selectedLayer;
		if (simulationRunning) {
			recordAftPopulations();
		}
		if (selectedLayer.equalsIgnoreCase("AFT") || selectedLayer.equalsIgnoreCase("Agent")) {
			if (simulationRunning) {
				showAftPopulationHistory();
			} else {
				showCategories("AFT distribution", "AFTs", aftCounts(), aftColors());
			}
		} else if (selectedLayer.equalsIgnoreCase("Categories")) {
			showCategories("AFT category distribution", "categories", categoryCounts(), categoryColors());
		} else if (selectedLayer.equalsIgnoreCase("Mask")) {
			showCategories("Mask distribution", "mask classes", maskCounts(), Map.of());
		} else if (CapitalUpdater.getCapitalsList().contains(selectedLayer)) {
			Integer displayYear = CellsCanvas.getDisplayedCapitalYear(selectedLayer);
			String heading = displayYear == null ? selectedLayer : selectedLayer + " - " + displayYear;
			showHistogram(heading, heading + " distribution", "capital", capitalValues(selectedLayer));
		} else if (ServiceSet.getServicesList().contains(selectedLayer)) {
			showHistogram(selectedLayer, selectedLayer + " distribution", "service production",
					serviceValues(selectedLayer));
		} else {
			showCategories(selectedLayer + " distribution", "regions", regionCounts(), regionColors());
		}
	}

	private void recordAftPopulations() {
		int completedYear = Timestep.getCurrentYear() - 1;
		if (completedYear < Timestep.getStartYear()
				|| !Config.chart_synchronisation
				|| (Timestep.getTick() % Config.chart_synchronisation_gap != 0
						&& completedYear != Timestep.getStartYear()
						&& completedYear != Timestep.getEndtYear())) {
			return;
		}
		AFTsLoader.hashAgentNbr.forEach((name, population) -> {
			List<AftPopulationPoint> history = aftPopulationHistory.computeIfAbsent(name, _ -> new ArrayList<>());
			if (history.isEmpty() || history.getLast().year() != completedYear) {
				history.add(new AftPopulationPoint(completedYear, population));
			}
		});
	}

	private void showAftPopulationHistory() {
		title.setText("AFT populations");
		int latestYear = aftPopulationHistory.values().stream()
				.filter(history -> !history.isEmpty())
				.mapToInt(history -> history.getLast().year())
				.max().orElse(Timestep.getStartYear());
		summary.setText(aftPopulationHistory.isEmpty()
				? "Waiting for the first completed simulation year."
				: format(aftPopulationHistory.size()) + " AFTs | through " + latestYear);

		NumberAxis xAxis = new NumberAxis(Timestep.getStartYear(), Timestep.getEndtYear(), 5);
		xAxis.setLabel("Year");
		NumberAxis yAxis = new NumberAxis();
		yAxis.setForceZeroInRange(true);
		LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
		chart.setTitle("AFT populations");
		chart.setAnimated(false);
		chart.setCreateSymbols(false);
		chart.setMinHeight(170);
		chart.setMaxWidth(Double.MAX_VALUE);

		aftPopulationHistory.forEach((name, history) -> {
			XYChart.Series<Number, Number> series = new XYChart.Series<>();
			series.setName(name);
			for (AftPopulationPoint point : history) {
				double displayed = aftPopulationLogarithmic
						? Math.log10(point.population() + 1.0)
						: point.population();
				series.getData().add(new XYChart.Data<>(point.year(), displayed));
			}
			chart.getData().add(series);
			if (series.getNode() != null && AFTsLoader.getAftHash().get(name) != null) {
				series.getNode().lookup(".chart-series-line").setStyle("-fx-stroke: "
						+ ColorsTools.getStringColor(Color.web(AFTsLoader.getAftHash().get(name).getColor())) + ";");
			}
		});
		yAxis.setLabel(aftPopulationLogarithmic ? "log10(population + 1)" : "Population");
		LineChartTools.labelcolor(chart);
		LineChartTools.addSeriesTooltips(chart);

		CheckBox logarithmic = new CheckBox("Logarithmic population (Y)");
		logarithmic.setSelected(aftPopulationLogarithmic);
		logarithmic.getStyleClass().add("map-log-toggle");
		logarithmic.setOnAction(_ -> {
			aftPopulationLogarithmic = logarithmic.isSelected();
			showAftPopulationHistory();
		});
		VBox content = new VBox(2, logarithmic, chart);
		VBox.setVgrow(chart, Priority.ALWAYS);
		setChart(content);
	}

	private Map<String, Color> aftColors() {
		Map<String, Color> colors = new LinkedHashMap<>();
		AFTsLoader.getAftHash().forEach((name, aft) -> {
			try {
				colors.put(name, Color.web(aft.getColor()));
			} catch (RuntimeException exception) {
				colors.put(name, Color.BLACK);
			}
		});
		return colors;
	}

	private Map<String, Long> aftCounts() {
		Map<String, Long> counts = new LinkedHashMap<>();
		for (Cell cell : CellsLoader.hashCell.values()) {
			String name = cell.getOwner() == null ? "Abandoned" : cell.getOwner().getLabel();
			counts.merge(name, 1L, Long::sum);
		}
		return counts;
	}

	private Map<String, Long> categoryCounts() {
		Map<String, Long> counts = new LinkedHashMap<>();
		for (Cell cell : CellsLoader.hashCell.values()) {
			String name = cell.getOwner() == null || cell.getOwner().getCategory() == null
					? "Uncategorized"
					: cell.getOwner().getCategory().getName();
			counts.merge(name, 1L, Long::sum);
		}
		return counts;
	}

	private Map<String, Color> categoryColors() {
		Map<String, Color> colors = new LinkedHashMap<>();
		AftCategorised.categoriesColor.forEach((name, color) -> {
			try {
				colors.put(name, Color.web(color));
			} catch (RuntimeException exception) {
				colors.put(name, Color.BLACK);
			}
		});
		colors.putIfAbsent("Uncategorized", Color.WHITE);
		return colors;
	}

	private Map<String, Long> maskCounts() {
		Map<String, Long> counts = new LinkedHashMap<>();
		for (Cell cell : CellsLoader.hashCell.values()) {
			String name = cell.getMaskType() == null ? "No mask" : cell.getMaskType();
			counts.merge(name, 1L, Long::sum);
		}
		return counts;
	}

	private Map<String, Long> regionCounts() {
		Map<String, Long> counts = new LinkedHashMap<>();
		for (Cell cell : CellsLoader.hashCell.values()) {
			String name = cell.getCurrentRegion() == null ? "No region" : cell.getCurrentRegion();
			counts.merge(name, 1L, Long::sum);
		}
		return counts;
	}

	private Map<String, Color> regionColors() {
		Map<String, Color> colors = new LinkedHashMap<>();
		for (String region : regionCounts().keySet()) {
			colors.put(region, "No region".equals(region)
					? Color.WHITE
					: ColorsTools.colorlist(GisLoader.regionIDs.get(region)));
		}
		return colors;
	}

	private List<Double> capitalValues(String capital) {
		List<Double> values = new ArrayList<>();
		for (Cell cell : CellsLoader.hashCell.values()) {
			Double value = CellsCanvas.getDisplayedCapitalValue(cell, capital);
			if (value != null && Double.isFinite(value)) {
				values.add(value);
			}
		}
		return values;
	}

	private List<Double> serviceValues(String service) {
		List<Double> values = new ArrayList<>();
		int index = ServiceSet.getServicesList().indexOf(service);
		for (Cell cell : CellsLoader.hashCell.values()) {
			double[] production = cell.getCurrentProd();
			if (production != null && index >= 0 && index < production.length && Double.isFinite(production[index])) {
				values.add(production[index]);
			}
		}
		return values;
	}

	private void showCategories(String heading, String itemName, Map<String, Long> counts,
			Map<String, Color> colors) {
		long total = counts.values().stream().mapToLong(Long::longValue).sum();
		title.setText(heading);
		summary.setText(String.format(Locale.ROOT, "%s mapped cells | %s %s", format(total),
				format(counts.size()), itemName));

		List<Map.Entry<String, Long>> sorted = counts.entrySet().stream()
				.sorted(Map.Entry.<String, Long>comparingByValue().reversed()).toList();
		List<PieChart.Data> slices = new ArrayList<>();
		Map<PieChart.Data, Color> sliceColors = new LinkedHashMap<>();
		boolean compactAftLegend = "AFTs".equals(itemName);
		long other = 0;
		int maximumSlices = colors.isEmpty() ? MAX_PIE_SLICES : Integer.MAX_VALUE;
		for (int i = 0; i < sorted.size(); i++) {
			Map.Entry<String, Long> entry = sorted.get(i);
			if (i < maximumSlices - 1 || sorted.size() <= maximumSlices) {
				String legendText = compactAftLegend
						? entry.getKey()
						: entry.getKey() + " (" + format(entry.getValue()) + ")";
				PieChart.Data slice = new PieChart.Data(legendText, entry.getValue());
				slices.add(slice);
				if (colors.containsKey(entry.getKey())) {
					sliceColors.put(slice, colors.get(entry.getKey()));
				}
			} else {
				other += entry.getValue();
			}
		}
		if (other > 0) {
			slices.add(new PieChart.Data("Other (" + format(other) + ")", other));
		}

		PieChart chart = new PieChart(FXCollections.observableArrayList(slices));
		chart.setAnimated(false);
		chart.setLabelsVisible(false);
		chart.setLegendVisible(true);
		chart.setLegendSide(Side.RIGHT);
		if (compactAftLegend) {
			chart.getStyleClass().add("map-aft-statistics-pie");
		}
		chart.setMinHeight(150);
		setChart(chart);
		applyPieColors(chart, sliceColors);
	}

	private void applyPieColors(PieChart chart, Map<PieChart.Data, Color> sliceColors) {
		if (sliceColors.isEmpty()) {
			return;
		}
		sliceColors.forEach((slice, color) -> {
			Runnable apply = () -> {
				if (slice.getNode() != null) {
					slice.getNode().setStyle("-fx-pie-color: " + colorToCss(color) + ";");
				}
			};
			slice.nodeProperty().addListener((_, _, _) -> apply.run());
			apply.run();
		});

		Platform.runLater(() -> {
			for (Node node : chart.lookupAll(".chart-legend-item")) {
				if (node instanceof Label legend) {
					for (Map.Entry<PieChart.Data, Color> entry : sliceColors.entrySet()) {
						if (legend.getText().equals(entry.getKey().getName())) {
							legend.setGraphic(new Rectangle(10, 10, entry.getValue()));
							break;
						}
					}
				}
			}
		});
	}

	private void showHistogram(String layer, String heading, String valueType, List<Double> values) {
		title.setText(heading);
		if (values.isEmpty()) {
			summary.setText("No numeric values are available for this layer.");
			setChart(new Label("No data"));
			return;
		}

		double min = values.stream().min(Comparator.naturalOrder()).orElse(0.0);
		double max = values.stream().max(Comparator.naturalOrder()).orElse(0.0);
		double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
		summary.setText(String.format(Locale.ROOT, "%s cells | %s values | min %s | mean %s | max %s",
				format(values.size()), valueType, number(min), number(mean), number(max)));

		CategoryAxis xAxis = new CategoryAxis();
		xAxis.setLabel("Value range");
		NumberAxis yAxis = new NumberAxis();
		yAxis.setLabel("Cells");
		yAxis.setForceZeroInRange(true);
		BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
		chart.setAnimated(false);
		chart.setLegendVisible(false);
		chart.setCategoryGap(2);
		chart.setBarGap(0);

		List<String> rangeLabels = new ArrayList<>();
		List<Long> frequencies = new ArrayList<>();
		if (Double.compare(min, max) == 0) {
			rangeLabels.add(number(min));
			frequencies.add((long) values.size());
		} else {
			double width = (max - min) / HISTOGRAM_BINS;
			long[] bins = new long[HISTOGRAM_BINS];
			for (double value : values) {
				int bin = Math.min(HISTOGRAM_BINS - 1, (int) ((value - min) / width));
				bins[bin]++;
			}
			for (int i = 0; i < HISTOGRAM_BINS; i++) {
				double from = min + i * width;
				double to = i == HISTOGRAM_BINS - 1 ? max : from + width;
				rangeLabels.add(number(from) + " - " + number(to));
				frequencies.add(bins[i]);
			}
		}

		XYChart.Series<String, Number> series = new XYChart.Series<>();
		CheckBox logarithmic = new CheckBox("Log frequency (Y)");
		logarithmic.getStyleClass().add("map-log-toggle");
		ImageView heatMap = new ImageView();
		heatMap.setPreserveRatio(true);
		heatMap.setSmooth(false);
		heatMap.setFitWidth(280);
		heatMap.setFitHeight(170);
		Runnable updateFrequencyScale = () -> {
			series.getData().clear();
			List<Color> displayColors = displayBinColors(frequencies, logarithmic.isSelected());
			for (int i = 0; i < rangeLabels.size(); i++) {
				double displayedFrequency = logarithmic.isSelected()
						? Math.log10(frequencies.get(i) + 1.0)
						: frequencies.get(i);
				XYChart.Data<String, Number> bar = new XYChart.Data<>(rangeLabels.get(i), displayedFrequency);
				Color binColor = binColor(i, rangeLabels.size());
				bar.nodeProperty().addListener((_, _, node) -> {
					if (node != null) {
						node.setStyle("-fx-bar-fill: " + colorToCss(binColor) + ";");
					}
				});
				series.getData().add(bar);
			}
			yAxis.setLabel(logarithmic.isSelected() ? "log10(cells + 1)" : "Cells");
			heatMap.setImage(createBinnedHeatMap(layer, min, max, displayColors));
		};
		logarithmic.setOnAction(_ -> updateFrequencyScale.run());
		updateFrequencyScale.run();
		chart.getData().add(series);
		chart.setMinHeight(150);
		chart.setMaxWidth(Double.MAX_VALUE);
		VBox histogramBox = new VBox(2, logarithmic, chart);
		VBox.setVgrow(chart, Priority.ALWAYS);
		histogramBox.setMinWidth(280);

		Label heatMapTitle = new Label("Spatial bins (color = range, intensity = frequency)");
		heatMapTitle.getStyleClass().add("map-heatmap-title");
		VBox heatMapBox = new VBox(4, heatMapTitle, heatMap);
		heatMapBox.getStyleClass().add("map-heatmap-box");
		SplitPane continuousViews = new SplitPane(histogramBox, heatMapBox);
		continuousViews.setDividerPosition(0, 0.62);
		continuousViews.getStyleClass().add("map-continuous-views");
		setChart(continuousViews);
	}

	private List<Color> displayBinColors(List<Long> frequencies, boolean logarithmic) {
		List<Double> displayed = frequencies.stream()
				.map(value -> logarithmic ? Math.log10(value + 1.0) : value.doubleValue()).toList();
		double maximum = displayed.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
		List<Color> colors = new ArrayList<>();
		for (int i = 0; i < frequencies.size(); i++) {
			double relativeFrequency = maximum == 0 ? 0 : displayed.get(i) / maximum;
			double intensity = 0.30 + relativeFrequency * 0.70;
			colors.add(Color.WHITE.interpolate(binColor(i, frequencies.size()), intensity));
		}
		return colors;
	}

	private WritableImage createBinnedHeatMap(String layer, double min, double max, List<Color> binColors) {
		int width = Math.max(1, CellsCanvas.maxX - CellsCanvas.minX);
		int height = Math.max(1, CellsCanvas.maxY - CellsCanvas.minY);
		WritableImage image = new WritableImage(width, height);
		PixelWriter writer = image.getPixelWriter();
		double binWidth = Double.compare(min, max) == 0 ? 0 : (max - min) / binColors.size();
		for (Cell cell : CellsLoader.hashCell.values()) {
			Double value = valueForLayer(cell, layer);
			if (value == null || !Double.isFinite(value)) {
				continue;
			}
			int bin = binWidth == 0 ? 0 : Math.min(binColors.size() - 1, (int) ((value - min) / binWidth));
			writer.setColor(cell.getX() - CellsCanvas.minX, cell.getY() - CellsCanvas.minY,
					binColors.get(bin));
		}
		return image;
	}

	private Double valueForLayer(Cell cell, String layer) {
		if (CapitalUpdater.getCapitalsList().contains(layer)) {
			return CellsCanvas.getDisplayedCapitalValue(cell, layer);
		}
		int serviceIndex = ServiceSet.getServicesList().indexOf(layer);
		double[] production = cell.getCurrentProd();
		return production != null && serviceIndex >= 0 && serviceIndex < production.length
				? production[serviceIndex]
				: null;
	}

	private Color binColor(int bin, int binCount) {
		return binCount <= 1 ? ColorsTools.getColorForValue(0.5)
				: ColorsTools.getColorForValue(binCount - 1, bin);
	}

	private void setChart(Node chart) {
		currentContent = chart;
		if (!minimized) {
			setCenter(chart);
		}
		BorderPane.setMargin(chart, new Insets(0, 6, 4, 6));
	}

	private String format(long value) {
		return String.format(Locale.ROOT, "%,d", value);
	}

	private String number(double value) {
		double absolute = Math.abs(value);
		if (absolute != 0 && (absolute < 0.001 || absolute >= 10_000)) {
			return String.format(Locale.ROOT, "%.2e", value);
		}
		return String.format(Locale.ROOT, "%.3f", value).replaceAll("\\.?0+$", "");
	}

	private String colorToCss(Color color) {
		return String.format(Locale.ROOT, "#%02x%02x%02x", (int) Math.round(color.getRed() * 255),
				(int) Math.round(color.getGreen() * 255), (int) Math.round(color.getBlue() * 255));
	}
}
