package de.cesr.crafty.gui.controller.fxml;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import de.cesr.crafty.gui.utils.analysis.AftAnalyzer;
import de.cesr.crafty.gui.utils.graphical.CSVTableView;
import de.cesr.crafty.gui.utils.graphical.LineChartTools;
import de.cesr.crafty.gui.utils.graphical.MousePressed;
import de.cesr.crafty.gui.utils.graphical.Tools;
import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.utils.general.Utils;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public class AFTsProductionController {
	@FXML
	private VBox TopBox;
	@FXML
	private BarChart<String, Number> histogramePlevel;
//	@FXML
//	private VBox box2;
	@FXML
	private VBox hbox1;

	Button productionFire = new Button();
	Button sensitivtyFire = new Button();
	RadioButton plotInitialDistrebution = new RadioButton("Distribution map");
	RadioButton plotOptimalLandon = new RadioButton("Cumulative expected service productivity");

	private static AFTsProductionController instance;

	public AFTsProductionController() {
		instance = this;
	}

	public static AFTsProductionController getInstance() {
		return instance;
	}

	public BarChart<String, Number> getHistogramePlevel() {
		return histogramePlevel;
	}

//	public VBox getBox2() {
//		return box2;
//	}

	public VBox getHBox1() {
		return hbox1;
	}

	public void initialize() {
		System.out.println("initialize " + getClass().getSimpleName());

//		Tools.forceResisingWidth(TopBox, box2);

		histogramePlevel.setMinWidth(TopBox.getMinWidth() / 2);
		hbox1.setMinWidth(TopBox.getMinWidth() / 2);
//		box2.getChildren().add(new Rectangle(100, 100, Color.BLUE));
//		hbox1.getChildren().add(new Rectangle(100, 100, Color.RED));
	}

	public static LineChart<Number, Number> productivitySampleChart(String aftLabel, boolean withShade) {
		Map<String, List<Double>> data = AftAnalyzer.productivitySampleByAFTs(1000, 100,
				AFTsLoader.getAftHash().get(aftLabel));
		if (data.size() == 0) {
			return null;
		}
		LineChart<Number, Number> chart = LineChartTools.createLineChartWithSmoothLines(aftLabel, data, withShade);
		chart.getStyleClass().add("analysis-chart");
		chart.setMinWidth(0);
		chart.setMaxWidth(Double.MAX_VALUE);
		chart.setMinHeight(260);
		chart.setPrefHeight(300);
		chart.setMaxHeight(300);
		HashMap<String, Consumer<String>> othersMenuItems = new HashMap<>();
		Consumer<String> relaod = _ -> {
			Pane parent = (Pane) chart.getParent();
			parent.getChildren().removeIf(node -> ("productivitySampleChart".equals(node.getId())));
			parent.getChildren().add(productivitySampleChart(aftLabel, withShade));
		};
		othersMenuItems.put("Reload and Update", relaod);
		Consumer<String> switchView = _ -> {
			Pane parent = (Pane) chart.getParent();
			parent.getChildren().removeIf(node -> ("productivitySampleChart".equals(node.getId())));
			parent.getChildren().add(productivitySampleChart(aftLabel, !withShade));
		};

		othersMenuItems.put("Update and show the " + (!withShade ? " Deviation" : "Original Points"), switchView);
		chart.setId("productivitySampleChart");
		Platform.runLater(() -> {
			if (chart.getParent() instanceof Pane parent) {
				MousePressed.mouseControle(parent, chart, othersMenuItems);
			}
		});
		return chart;
	}

	

	static void updateProduction(Aft newAFT, TableView<ObservableList<String>> tabV) {
		String[][] tab = CSVTableView.tableViewToArray(tabV);
		for (int i = 0; i < tab[0].length; i++) {
			newAFT.getProductivityLevel().put(tab[0][i], Utils.sToD(tab[1][i]));
		}
	}
}
