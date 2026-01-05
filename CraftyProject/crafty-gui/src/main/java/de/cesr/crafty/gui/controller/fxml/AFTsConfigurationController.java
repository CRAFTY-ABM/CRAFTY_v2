package de.cesr.crafty.gui.controller.fxml;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.gui.canvasFx.CellsCanvas;
import de.cesr.crafty.gui.utils.analysis.CsvToHtml;
import de.cesr.crafty.gui.utils.graphical.CSVTableView;
import de.cesr.crafty.gui.utils.graphical.ColorsTools;
import de.cesr.crafty.gui.utils.graphical.Histogram;
import de.cesr.crafty.gui.utils.graphical.MousePressed;
import de.cesr.crafty.gui.utils.graphical.NewWindow;
import de.cesr.crafty.gui.utils.graphical.Tools;
import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.CapitalUpdater;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;
import eu.hansolo.fx.charts.Category;
import eu.hansolo.fx.charts.ChartType;
import eu.hansolo.fx.charts.YChart;
import eu.hansolo.fx.charts.YPane;
import eu.hansolo.fx.charts.data.ValueChartItem;
import eu.hansolo.fx.charts.series.YSeries;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.chart.BarChart;
import javafx.scene.control.TableView;
import javafx.scene.control.ChoiceBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

public class AFTsConfigurationController {

	@FXML
	private VBox TopBox;
	@FXML
	private ChoiceBox<String> AFTChoisButton;
	@FXML
	private Label AFTNameLabel;
	@FXML
	Rectangle rectangleColor;
	@FXML
	private Button addNewAftBtn;
	@FXML
	private Button removeBtn;
	@FXML
	private Button saveModeficationBtn;
	@FXML
	private Button ResetBtn;
	@FXML
	private Button AFTAnalisisBtn;

	NewAFT_Controller newAftPane;
	RadioButton plotInitialDistrebution = new RadioButton("  Distribution map ");
	RadioButton plotOptimalLandon = new RadioButton("Cumulative expected service productivity");
	NewWindow Analysewin = new NewWindow();
	private boolean isNotInitialsation = false;

	private static AFTsConfigurationController instance;

	public AFTsConfigurationController() {
		instance = this;
	}

	public static AFTsConfigurationController getInstance() {
		return instance;
	}

	public ChoiceBox<String> getAFTChoisButton() {
		return AFTChoisButton;
	}

	public void initialize() {
		System.out.println("initialize " + getClass().getSimpleName());
		newAftPane = new NewAFT_Controller(this);

		ConcurrentHashMap<String, Aft> InteractAFTs = new ConcurrentHashMap<>();
		AFTsLoader.getActivateAFTsHash().entrySet().stream().filter(entry -> entry.getValue().isInteract())
				.forEach(entry -> InteractAFTs.put(entry.getKey(), entry.getValue()));
		Tools.choiceBox(AFTChoisButton, new ArrayList<>(InteractAFTs.keySet()));

		plotOptimalLandon.setOnAction(_ -> {
			plotInitialDistrebution.setSelected(false);
			colorland(AFTsLoader.getAftHash().get(AFTChoisButton.getValue()));
		});
		isNotInitialsation = true;
		Tools.forceResisingWidth(TopBox);
	}

	@FXML
	public void choiceAgnetSetOnAction() {
		Aft a = AFTsLoader.getAftHash().get(AFTChoisButton.getValue());
		AFTNameLabel.setText(a.getCompleteName());
		rectangleColor.setFill(Color.web(a.getColor()));
		if (isNotInitialsation) {
			CellsCanvas.showOnlyOneAFT(a);
		}
		BarChart<String, Number> histogramePlevel = AFTsProductionController.getInstance().getHistogramePlevel();
		// histogramePlevel.getData().clear();
		AFTsProductionController.getInstance().getBox2().getChildren().clear();
		AFTsProductionController.getInstance().getHBox1().getChildren().clear();
		AFTsProductionController.getInstance().getHBox1().getChildren()
				.add(AFTsProductionController.productivitySampleChart(a.getLabel(), false));
		Histogram.histo((Pane) histogramePlevel.getParent(), "Productivity levels", histogramePlevel,
				a.getProductivityLevel());
		ArrayList<Path> paths = PathTools.fileFilter(PathTools.asFolder("production"), ProjectLoader.getScenario(),
				a.getLabel(), ".csv"); // to be change
		if (paths == null) {
			paths = PathTools.fileFilter(PathTools.asFolder("production"), "default_production", a.getLabel(), ".csv");
		}
		if (paths != null) {
			AFTsProductionController.getInstance().getBox2().getChildren().add(CsvToHtml.tabeWeb(paths.get(0)));
		}
		GridPane grid = new GridPane();
		ubdateRadarchart(AFTsLoader.getAftHash().get(AFTChoisButton.getValue()), grid);
		grid.setMinWidth(TopBox.getMinWidth());
		AFTsProductionController.getInstance().getBox2().getChildren().forEach(child -> {
			if (child instanceof GridPane) {
				AFTsProductionController.getInstance().getBox2().getChildren().remove(child);
			}
		});
		AFTsProductionController.getInstance().getBox2().getChildren().add(grid);
	}

	void colorland(Aft a) {
//		CellsLoader.hashCell.values().forEach(_ -> {
//			// C.landStored(a);
//		});
		CellsCanvas.colorMap("tmp");
	}

	static void updateSensitivty(Aft newAFT, GridPane grid, TableView<ObservableList<String>> tabV) {
		String[][] tab = CSVTableView.tableViewToArray(tabV);
		for (int i = 1; i < tab.length; i++) {
			newAFT.getSensByService().put(tab[i][0], new ConcurrentHashMap<>());
			for (int j = 1; j < tab[0].length; j++) {
				newAFT.getSensByService().get(tab[i][0]).put(tab[0][j], Utils.sToD(tab[i][j]));
			}
		}
		ubdateRadarchart(newAFT, grid);
	}

	@FXML
	public void addAFTSetOnAction() {
		// newAftPane.addaft();
		// CapitalsAnalyzer.generateGrapheDataByScenarios();

	};

	@FXML
	public void removeBtnSetOnAction() {
		AFTsLoader.getAftHash().remove(AFTChoisButton.getValue());
		AFTsLoader.getActivateAFTsHash().remove(AFTChoisButton.getValue());
		AFTChoisButton.getItems().remove(AFTChoisButton.getValue());
		AFTChoisButton.setValue(AFTsLoader.getActivateAFTsHash().keySet().iterator().next());
	}

	@FXML
	public void saveModefication() {
		// creatCsvFiles(AFTsLoader.getAftHash().get(AFTChoisButton.getValue()), "");
	}

	@FXML
	public void AftAnalyseSetOnAction() {
		VBox v = Tools.vBox(plotInitialDistrebution, plotOptimalLandon);
		Analysewin.creatwindows("", v);
	};

	static void ubdateRadarchart(Aft newAFT, GridPane grid) {
		grid.getChildren().clear();
		int j = 0, k = 0, nbrColumn = 5;

		for (int i = 0; i < ServiceSet.getServicesList().size(); i++) {
			VBox vbox = new VBox();
			vbox.setAlignment(Pos.CENTER);
			Text text = new Text(ServiceSet.getServicesList().get(i));
			text.setFont(Font.font("Verdana", FontWeight.BOLD, 10));
			text.setFill(Color.BLUE);
			YChart<ValueChartItem> ychart = ychart(vbox, newAFT, ServiceSet.getServicesList().get(i));
			if (ychart != null) {
				vbox.getChildren().addAll(ychart(vbox, newAFT, ServiceSet.getServicesList().get(i)), text);
				grid.add(vbox, j++, k);
				if (j % nbrColumn == 0) {
					k++;
					j = 0;
				}
			}
			MousePressed.mouseControle(grid, vbox);
		}

	}

	public static YChart<ValueChartItem> ychart(Pane box, Aft agent, String servicesName) {
		List<ValueChartItem> listvalues = new ArrayList<>();
		List<Category> categories = new ArrayList<>();
		CapitalUpdater.getCapitalsList().forEach(capitalName -> {

			double y = Math.min(100, agent.getSensByService().get(servicesName).get(capitalName) * 100);
			if (y != 0) {
				listvalues.add(new ValueChartItem(y, capitalName + "|" + servicesName));
				categories.add(new Category(capitalName));
			}
		});

		if (listvalues.size() <= 1) {
			return null;
		}
		YSeries<ValueChartItem> series = new YSeries<ValueChartItem>(listvalues, ChartType.RADAR_POLYGON// RADAR_SECTOR//
				, new RadialGradient(0, 0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
						ColorsTools.colorYchart(new Random().nextInt(4))),
				Color.GRAY);
		@SuppressWarnings("unchecked")
		YPane<ValueChartItem> pane = new YPane<>(categories, series);
		YChart<ValueChartItem> chart = new YChart<>(pane);
//		YChart<ValueChartItem> chart = new YChart(new YPane(categories, series));
		// chart.setPrefSize(scale, scale);

		return chart;
	}

	String[][] sensitivityTable(Aft a) {
		String[][] sensetivtyTable = new String[ServiceSet.getServicesList().size()
				+ 1][CapitalUpdater.getCapitalsList().size() + 1];
		for (int i = 0; i < ServiceSet.getServicesList().size(); i++) {
			sensetivtyTable[i + 1][0] = ServiceSet.getServicesList().get(i);
			for (int j = 0; j < CapitalUpdater.getCapitalsList().size(); j++) {
				sensetivtyTable[0][j + 1] = CapitalUpdater.getCapitalsList().get(j);
				sensetivtyTable[i + 1][j + 1] = String.valueOf(a.getSensByService()
						.get(ServiceSet.getServicesList().get(i)).get(CapitalUpdater.getCapitalsList().get(j)));
			}

		}
		return sensetivtyTable;
	}

	String[][] productionTable(Aft a) {
		String[][] production = new String[2][ServiceSet.getServicesList().size()];
		for (int j = 0; j < ServiceSet.getServicesList().size(); j++) {
			production[0][j] = ServiceSet.getServicesList().get(j);
			production[1][j] = a.getProductivityLevel().get(ServiceSet.getServicesList().get(j)) + "";
		}
		return production;
	}

	static void creatCsvFiles(Aft a, String descreption) {
		String[][] tab = new String[ServiceSet.getServicesList().size() + 1][CapitalUpdater.getCapitalsList().size()
				+ 2];
		tab[0][0] = "";
		tab[0][CapitalUpdater.getCapitalsList().size() + 1] = "Production";
		for (int i = 0; i < CapitalUpdater.getCapitalsList().size(); i++) {
			tab[0][i + 1] = CapitalUpdater.getCapitalsList().get(i);

			for (int j = 0; j < ServiceSet.getServicesList().size(); j++) {
				tab[j + 1][0] = ServiceSet.getServicesList().get(j);
				tab[j + 1][i + 1] = String.valueOf(a.getSensByService().get(ServiceSet.getServicesList().get(j))
						.get(CapitalUpdater.getCapitalsList().get(i)));

				tab[j + 1][CapitalUpdater.getCapitalsList().size() + 1] = a.getProductivityLevel()
						.get(ServiceSet.getServicesList().get(j)) + "";
			}
		}

		String folder = PathTools
				.fileFilter(File.separator + "production" + File.separator, ProjectLoader.getScenario()).get(0).toFile()
				.getParent();
		CsvTools.writeCSVfile(tab, Paths.get(folder + File.separator + a.getLabel() + ".csv"));
		String[][] tab2 = new String[2][7];
		tab2[0] = "givingInDistributionMean,givingInDistributionSD,givingUpDistributionMean,givingUpDistributionSD,serviceLevelNoiseMin,serviceLevelNoiseMax,givingUpProb"
				.split(",");
		tab2[1][0] = a.getGiveInMean() + "";
		tab2[1][1] = a.getGiveInSD() + "";
		tab2[1][2] = a.getGiveUpMean() + "";
		tab2[1][3] = a.getGiveUpSD() + "";
		tab2[1][4] = a.getServiceLevelNoiseMin() + "";
		tab2[1][5] = a.getServiceLevelNoiseMax() + "";
		tab2[1][6] = a.getGiveUpProbabilty() + "";
		String folder2 = PathTools.fileFilter(File.separator + "agents" + File.separator, ProjectLoader.getScenario())
				.get(0).toFile().getParent();
		CsvTools.writeCSVfile(tab2, Paths.get(folder2 + File.separator + "AftParams_" + a.getLabel() + ".csv"));
		// add also in csv folder
		Path pathCSV = ProjectLoader.getAftMetaData();
		String[][] tmp = CsvTools.csvReader(ProjectLoader.getAftMetaData());
		boolean isExiste = false;
		for (int i = 0; i < tmp.length; i++) {
			if (a.getLabel().equalsIgnoreCase(tmp[i][Utils.indexof("Label", tmp[0])])) {
				isExiste = true;
				break;
			}
		}
		if (!isExiste) {
			String[][] tmp2 = new String[tmp.length + 1][tmp[0].length];
			for (int i = 0; i < tmp2.length - 1; i++) {
				for (int j = 0; j < tmp2[0].length; j++) {
					tmp2[i][j] = tmp[i][j].replace(",", ".").replace("\"", "");
				}
			}
			tmp2[tmp.length][Utils.indexof("Label", tmp[0])] = a.getLabel();
			tmp2[tmp.length][Utils.indexof("name", tmp[0])] = a.getCompleteName();
			tmp2[tmp.length][Utils.indexof("Color", tmp[0])] = a.getColor();
			tmp2[tmp.length][Utils.indexof("Description", tmp[0])] = descreption.replace(",", ".").replace("\"", "")
					.replace("\n", " ");
			CsvTools.writeCSVfile(tmp2, pathCSV);
		}
	}

	public void updaChoisButton() {
		// AFTChoisButton.getItems().clear();
		Set<String> set = new HashSet<>();
		AFTsLoader.getAftHash().keySet().forEach(name -> {
			if (!AFTChoisButton.getItems().contains(name)) {
				set.add(name);
			}
		});
		AFTChoisButton.getItems().addAll(set);
		// AFTChoisButton.setValue(M.AFtsSet.getAftHash().keySet().iterator().next());
	}
}
