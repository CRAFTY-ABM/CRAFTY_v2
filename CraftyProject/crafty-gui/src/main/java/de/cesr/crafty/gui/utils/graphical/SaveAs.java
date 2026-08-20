package de.cesr.crafty.gui.utils.graphical;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.imageio.ImageIO;

import de.cesr.crafty.core.dataLoader.ProjectLoader;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.XYChart;
import javafx.scene.image.WritableImage;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;

/**
 * @author Mohamed Byari
 *
 */

public class SaveAs {

	public static void png(String title, Node node) {
		File file = openDirectory(title, "png", new FileChooser.ExtensionFilter("PNG Files", "*.png"))
				.showSaveDialog((Stage) node.getScene().getWindow());
		if (file != null) {
			// user selected a file, save HBox as PNG
			SnapshotParameters parameters = new SnapshotParameters();
			parameters.setDepthBuffer(true);
			WritableImage snapshot = node.snapshot(parameters, null);
			try {
				ImageIO.write(SwingFXUtils.fromFXImage(snapshot, null), "png", file);

			} catch (IOException e) {
				System.out.println("Failed to save HBox as PNG file: " + e.getMessage());
			}
		}
	}

	public static void exportLineChartDataToCSV(XYChart<Number, Number> lineChart) {

		File file = openDirectory("csv", new FileChooser.ExtensionFilter("CSV Files", "*.csv"))
				.showSaveDialog((Stage) lineChart.getScene().getWindow());
		if (file != null) {
			// Use a TreeSet to automatically sort and eliminate duplicate X values
			Set<Number> allXValues = new TreeSet<>((n1, n2) -> Double.compare(n1.doubleValue(), n2.doubleValue()));
			// Use a LinkedHashMap to maintain the insertion order of series
			Map<String, Map<Number, Number>> seriesData = new LinkedHashMap<>();

			// Populate allXValues and seriesData
			for (XYChart.Series<Number, Number> series : lineChart.getData()) {
				Map<Number, Number> dataMap = new LinkedHashMap<>();
				for (XYChart.Data<Number, Number> data : series.getData()) {
					allXValues.add(data.getXValue());
					dataMap.put(data.getXValue(), data.getYValue());
				}
				seriesData.put(series.getName(), dataMap);
			}

			// Write to CSV
			try (FileWriter csvWriter = new FileWriter(file)) {
				// Write the header row
				csvWriter.append("X Value");
				for (String seriesName : seriesData.keySet()) {
					csvWriter.append(",").append(seriesName);
				}
				csvWriter.append("\n");

				// Write data rows
				for (Number xValue : allXValues) {
					csvWriter.append(String.format("%f", xValue.doubleValue()));
					for (Map<Number, Number> dataMap : seriesData.values()) {
						csvWriter.append(",");
						if (dataMap.containsKey(xValue)) {
							csvWriter.append(String.format("%f", dataMap.get(xValue).doubleValue()));
						} else {
							// Handle missing Y values for this X in certain series, if any
							csvWriter.append("NA");
						}
					}
					csvWriter.append("\n");
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static FileChooser openDirectory(String type, ExtensionFilter extensionFilter) {
		return openDirectory("title", type, extensionFilter);
	}

	private static FileChooser openDirectory(String title, String type, ExtensionFilter extensionFilter) {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Save as");
		fileChooser.setInitialFileName(title + "." + type); // set initial file name
		File initialDirectory = ProjectLoader.getProjectPath().toFile();// new File(PathsLoader.getProjectPath());
		fileChooser.setInitialDirectory(initialDirectory);
		fileChooser.getExtensionFilters().addAll(extensionFilter, new FileChooser.ExtensionFilter("All Files", "*.*"));
		return fileChooser;
	}

}
