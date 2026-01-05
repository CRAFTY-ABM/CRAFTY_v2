package de.cesr.crafty.gui.utils.graphical;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import de.cesr.crafty.gui.utils.graphical.helpres.NestedVoronoiDiskChart;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class VoronoiDiskChart extends Application {
	static Map<String, Map<String, Double>> data = new LinkedHashMap<>();
	static Map<String, Map<String, Color>> colors = new HashMap<>();
	
	private static void inits() {
		data.put("North America", new LinkedHashMap<>(Map.of("U.S.", 1., "Canada", 1., "Mexico", 1.)));
		data.put("Asia", new LinkedHashMap<>(Map.of("China", 1., "Japan", 1., "India", 1., "S. Korea", 1.)));
		data.put("Europe", new LinkedHashMap<>(Map.of("Germany", 1., "UK", 1., "France", 1., "Italy", 1.)));
		data.put("South America", new LinkedHashMap<>(Map.of("Brazil", .1, "Argentina", .5, "Chile", .5)));
		colors.put("Asia", Map.of("China", Color.LIGHTCYAN, "Japan", Color.LIGHTCYAN, "India", Color.LIGHTCYAN,
				"S. Korea", Color.LIGHTCYAN));
		colors.put("North America", Map.of("U.S.", Color.CYAN, "Canada", Color.CYAN, "Mexico", Color.CYAN));
		colors.put("Europe", Map.of("Italy", Color.DARKCYAN, "France", Color.DARKCYAN, "UK", Color.DARKCYAN, "Germany",
				Color.DARKCYAN));
		colors.put("South America", Map.of("Chile", Color.ORANGE, "Argentina", Color.ORANGE, "Brazil", Color.ORANGE));

	}
	@Override
	public void start(Stage stage) {
		inits();
		NestedVoronoiDiskChart chart=voronoiChart(data, colors) ;

		StackPane root = new StackPane(chart);
		root.setPadding(new Insets(40));
		root.setBackground(new Background(new BackgroundFill(Color.web("#F6F7FB"), CornerRadii.EMPTY, Insets.EMPTY)));

		stage.setScene(new Scene(root, 860, 720));
		stage.setTitle("Nested Voronoi Disk Chart (JavaFX)");
		stage.show();

//		stage.setOnCloseRequest(_ -> chart.dispose());
	}

	public static NestedVoronoiDiskChart voronoiChart(Map<String, Map<String, Double>> data,
			Map<String, Map<String, Color>> colors ) {
		NestedVoronoiDiskChart chart = new NestedVoronoiDiskChart();
		chart.setData(data, colors);
		chart.setPreferredResolution(1000);
		chart.setBoundaryThickness(0);
//        chart.setOuterPaddingFrac(.8);
		return chart;
	}
	public static NestedVoronoiDiskChart voronoiChart() {
		inits();
		return  voronoiChart(data,colors);
	}

	public static void main(String[] args) {
		launch(args);
	}
}
