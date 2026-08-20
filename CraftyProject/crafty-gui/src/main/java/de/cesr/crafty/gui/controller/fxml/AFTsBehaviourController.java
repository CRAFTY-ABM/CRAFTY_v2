package de.cesr.crafty.gui.controller.fxml;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.dataLoader.afts.AftCategorised;
import de.cesr.crafty.core.updaters.CellBehaviourUpdater;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.gui.utils.analysis.AftAnalyzer;
import de.cesr.crafty.gui.utils.analysis.CsvToHtml;
import de.cesr.crafty.gui.utils.analysis.NonGraphic;
import de.cesr.crafty.gui.utils.graphical.MousePressed;
import de.cesr.crafty.gui.utils.graphical.Tools;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.AreaChart;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class AFTsBehaviourController {

	@FXML
	private GridPane grid;
	@FXML
	private VBox box;
	@FXML
	private VBox TopBox;

	public void initialize() {
		System.out.println("initialize " + getClass().getSimpleName());
		Tools.forceResisingWidth(TopBox);
		grid.setHgap(20);
		grid.setVgap(30);

		// Check whether the behavior model data is available.
		if (AftCategorised.useCategorisationGivIn && CellBehaviourUpdater.behaviourUsed) {
			addTables();
			AtomicInteger i = new AtomicInteger(), j = new AtomicInteger();

			AftCategorised.aftCategories.keySet().forEach(Categoryname -> {
				AreaChart<Number, Number> chart = AftAnalyzer.generateAreaChart(Categoryname, dataGraph(Categoryname));
				chart.setMaxWidth(TopBox.getMaxWidth() / 2);
				chart.setMinWidth(TopBox.getMinWidth() / 2);
				if (chart != null) {

					if (i.get() % 2 == 0) {
						i.set(0);
						j.getAndIncrement();
					}
					i.getAndIncrement();
					grid.add(chart, i.get(), j.get());
					MousePressed.mouseControle((Pane) chart.getParent(), chart);
				}
			});
		} else {
			grid.getChildren().add(new Text(
					"Behaviour model data is unavailable. Define AFT categories and provide both category give-in distribution files."));
		}

	}

	private void addTables() {
		ArrayList<Path> paths = PathTools.fileFilter(PathTools.asFolder("AFTs"), PathTools.asFolder("behaviour"),
				"categories_givingInDistribution");
		if (paths != null) {
			Path mean_path = paths.stream()
					.filter(path -> path.toString().contains("Mean_" + ProjectLoader.getScenario())).findFirst()
					.orElse(paths.stream().filter(path -> path.toString().contains("Mean_Default")).findFirst()
							.orElse(null));
			Path SD_path = paths.stream().filter(path -> path.toString().contains("SD_" + ProjectLoader.getScenario()))
					.findFirst().orElse(paths.stream().filter(path -> path.toString().contains("SD_Default"))
							.findFirst().orElse(null));
			if (mean_path != null && SD_path != null) {
				Node TablMean = CsvToHtml.tabeWeb(mean_path);
				Node TablSD = CsvToHtml.tabeWeb(SD_path);
				box.getChildren().addAll(new Text("Giving-in normal distribution mean by category"), TablMean,
						new Text("Giving-in normal distribution standard deviation (SD) by category"), TablSD);
			}
		}
	}

	private static Map<String, List<Double>> dataGraph(String categoryName) {
		Map<String, List<Double>> output = new HashMap<>();
		double maxMean = 0, maxsd = 0;
		for (String name : AftCategorised.aftCategories.keySet()) {
			double mean = AftCategorised.getMean().get(categoryName + "|" + name);
			double sd = AftCategorised.getSD().get(categoryName + "|" + name);
			if (maxMean < mean) {
				maxMean = mean;
				maxsd = sd;
			}
		}
		double mm = maxMean, sdsd = maxsd;
		AftCategorised.aftCategories.keySet().forEach(name -> {
			double mean = AftCategorised.getMean().get(categoryName + "|" + name);
			double sd = AftCategorised.getSD().get(categoryName + "|" + name);
			if (sd > 0)
				output.put(name, NonGraphic.generateNormalData(mean, sd, mm, sdsd));

		});
		// AftAnalyzer.generateChart(categoryName, output);
		return output;
	}

}
