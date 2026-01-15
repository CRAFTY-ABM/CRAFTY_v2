package de.cesr.crafty.gui.controller.fxml;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.crafty.AftCategory;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.dataLoader.CsvKind;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.afts.AftCategorised;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.gui.canvasFx.CellsCanvas;
import de.cesr.crafty.gui.utils.analysis.AftAnalyzer;
import de.cesr.crafty.gui.utils.graphical.ColorsTools;
import de.cesr.crafty.gui.utils.graphical.FileTreeView;
import de.cesr.crafty.gui.utils.graphical.Histogram;
import de.cesr.crafty.gui.utils.graphical.ImageExporter;
import de.cesr.crafty.gui.utils.graphical.ImagesToPDF;
import de.cesr.crafty.gui.utils.graphical.LineChartTools;
import de.cesr.crafty.gui.utils.graphical.MousePressed;
import de.cesr.crafty.gui.utils.graphical.NewWindow;
import de.cesr.crafty.gui.utils.graphical.SankeyChart;
import de.cesr.crafty.gui.utils.graphical.Tools;
import de.cesr.crafty.gui.utils.graphical.WarningWindowes;
import de.cesr.crafty.gui.main.FxMain;
import de.cesr.crafty.gui.main.GuiScaler;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.gui.utils.graphical.SaveAs;
import de.cesr.crafty.core.utils.general.Utils;
import eu.hansolo.fx.charts.SankeyPlot;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

public class OutPuterController {
	@FXML
	private VBox TopBox;
	@FXML
	private Button saveAllFilAsPNG;
	@FXML
	private ChoiceBox<String> yearChoice;
	@FXML
	private ChoiceBox<String> sankeyBox;
	@FXML
	private GridPane gridChart;
	@FXML
	private ScrollPane scroll;
	@FXML
	private Button selecserivce;
	@FXML
	private VBox borderPane;
	@FXML
	private ChoiceBox<String> regionsBox;
	@FXML
	private Tab regionTab;
	@FXML
	private ScrollPane scrollRegions;
	@FXML
	private GridPane regionalGridChart;
	@FXML
	private VBox boxTracker;
	@FXML
	private Tab treeTab;
	@FXML
	private VBox treeBox;

	private HBox boxAverageAndLAndEvent = new HBox();

	private static final CustomLogger LOGGER = new CustomLogger(OutPuterController.class);

	public static boolean isCurrentResult = false;
	public static Path outputpath;
	private TreeView<Path> tree;

	HashMap<String, HashMap<String, Integer>> h = new HashMap<>();

	public void initialize() {
		System.out.println("Initialize " + getClass().getSimpleName());
		selectoutPut();
		forceResizing();

		isCurrentResult = false;
		initialiseregionBox();
		if (regionsBox.getItems().size() > 0) {
			regionsBox.setValue(regionsBox.getItems().get(0));
		}
		Tools.forceResisingWidth(TopBox, boxTracker);
		boxTracker.getChildren().add(boxAverageAndLAndEvent);

		PlotLandEventNbr();
		plotAverageUtility();
		plotSupplyTraker();
		treeFiles();
	}

	private void treeFiles() {
		System.out.println(outputpath);
		tree = FileTreeView.build(outputpath, ".csv", "-Cell-", -1);
		double scaleY = GuiScaler.lastScreen.getBounds().getHeight() / (1.2 * GuiScaler.graphicScaleY);
		tree.setMaxHeight(scaleY);
		tree.setMinHeight(scaleY);
		treeBox.getChildren().add(tree);
		mouseTreeFiles(tree);
	}

	@FXML
	public void reload() {
		treeBox.getChildren().remove(tree);
		treeFiles();
	}

	private void mouseTreeFiles(TreeView<Path> tree) {
		tree.setOnMouseClicked(evt -> {
			// react only to a double–click (use 1 for single-click)
			if (evt.getClickCount() == 2) {
				TreeItem<Path> selected = tree.getSelectionModel().getSelectedItem();
				if (selected != null) {
					WarningWindowes.showWaitingDialog(_ -> {
						String name = selected.getValue().getFileName().toString();
						/*
						 * if (name.contains("-Cell-")) { //.. a grid of Map show AFTs service
						 * distribution - Later } else
						 */
						if (name.contains("SupplyTracker")) {
							histo(selected.getValue());
						} else if (name.contains("AggregateAFTComposition.csv")
								|| name.contains("-AverageUtilities.csv") || name.contains("-landEventCounter.csv")
								|| name.contains("Total-AggregateServiceDemand")) {
							chart(selected.getValue());
						} else if (name.contains(".csv")) {
							openFile(selected.getValue());
						}
					});
				}
			}
		});
	}

	public static void openFile(Path csv) {
		// 2. Check whether the Desktop integration is available
		if (!Desktop.isDesktopSupported()) {
			System.err.println("Desktop API not supported on this platform.");
			return;
		}

		Desktop desktop = Desktop.getDesktop();

		// 3. Make sure the OPEN action is available
		if (!desktop.isSupported(Desktop.Action.OPEN)) {
			System.err.println("OPEN action not supported on this platform.");
			return;
		}

		// 4. Open the file
		try {
			desktop.open(csv.toFile());
		} catch (IOException e) {
			e.printStackTrace(); // or log / re‑throw according to your needs
		}
	}

	private void chart(Path p) {
		// readfile as has-double
		Map<String, List<Double>> data = CsvProcessors.ReadAsaHashDouble(p);
		data.remove("Year");
		data.remove("year");
		// create chart
		LineChart<Number, Number> chart = new LineChart<>(new NumberAxis(), new NumberAxis());
		VBox vbox = new VBox(chart);
		new LineChartTools().lineChart(vbox, chart, data);
		// create new window
		NewWindow.createWin(p.getFileName().toString(), vbox);
	}

	private void histo(Path path) {
		ScrollPane scroll = new ScrollPane(gridHisto(path));
		new NewWindow().creatwindows(path.getFileName().toString(), 0.5, 0.5, scroll);
	}

	private void forceResizing() {
		double scaleY = GuiScaler.lastScreen.getBounds().getHeight() / (GuiScaler.graphicScaleY * 1.1);
		scroll.setMaxHeight(scaleY);
		scroll.setMinHeight(scaleY);
		scrollRegions.setMaxHeight(scaleY);
		scrollRegions.setMinHeight(scaleY);
	}

	void initialiseregionBox() {
		List<File> folders = PathTools.detectFolders(outputpath.toString());
		folders.forEach(e -> {
			if (e.getName().contains("region_")) {
				regionsBox.getItems().addAll(e.getName());
			}
		});
		if (regionsBox.getItems().size() == 0) {
			regionTab.setDisable(true);
			regionTab.getTooltip().setText("Output Files by regions are Not Available For This Simulation ");
		}
	}

	@FXML
	public void regionsBoxAction() {
		Graphs(regionalGridChart, regionsBox.getValue() + "-AggregateServiceDemand.csv",
				regionsBox.getValue() + "-AggregateAFTComposition.csv");

	}

	HashMap<String, HashMap<String, Integer>> stateToHashSankey(String lastYear) {
		HashMap<String, String> copyfirstYearHash = new HashMap<>();
		CellsLoader.hashCell.forEach((coor, c) -> {
			if (c.getOwner() != null) {
				copyfirstYearHash.put(coor, c.getOwner().getLabel());
			} else {
				copyfirstYearHash.put(coor, "Abandoned");
			}
		});
		// Find file in the correct folder and update hashCell
		yearChoice.getItems().stream().filter(str -> str.contains(lastYear)).findFirst().ifPresent(this::newOutPut);

		HashMap<String, Integer> h = new HashMap<>();
		copyfirstYearHash.forEach((coor, label) -> {
			Aft owner = CellsLoader.hashCell.get(coor).getOwner();
			if (owner != null) {
				h.merge(label + "," + owner.getLabel(), 1, Integer::sum);
			} else {
				h.merge(label + "," + "Abandoned", 1, Integer::sum);
			}
		});

		copyfirstYearHash.clear();
		HashMap<String, HashMap<String, Integer>> hash = new HashMap<>();

		AFTsLoader.getAftHash().keySet().forEach(label -> {
			HashMap<String, Integer> h1 = new HashMap<>();
			h.forEach((k, v) -> {
				String[] vect = k.split(",");
				if (vect.length == 2) {
					if (vect[0].equals(label)) {
						if (vect[1] != null)
							h1.put(vect[1], v);
					}
				}
			});
			if (h1.size() > 0) {
				hash.put(label, h1);
			}
		});
		return hash;
	}

	public void selectoutPut() {
		System.out.println(!isCurrentResult && outputpath == null);
		if (!isCurrentResult) {
			if (outputpath == null) {
				File selectedDirectory = Tools.selectFolder(ProjectLoader.getProjectPath() + File.separator + "output");
				if (selectedDirectory != null) {
					outputpath = Paths.get(selectedDirectory.getAbsolutePath());
				}
			}
		} else {
			if (ConfigLoader.config.output_folder_name != null) {
				outputpath = Paths.get(ConfigLoader.config.output_folder_name);
				isCurrentResult = false;
			}
		}
		if (outputpath != null) {
			ArrayList<String> yearList = new ArrayList<>();
			PathTools.findAllFilePaths(outputpath).forEach(str -> {
				File file = str.toFile();
				String tmp = new File(file.getParent()).getName() + File.separator + file.getName();

				if (tmp.contains("-Cell-"))
					yearList.add(tmp);
			});
			LOGGER.info("output files List size: " + yearList.size());
			yearChoice.getItems().addAll(yearList);
			yearChoice.setValue(yearList.getFirst());
			sankeyBox.getItems().addAll(yearList);
			sankeyBox.setValue(yearList.getLast());
			yearChoice.setValue(yearList.getLast());
			OutPutTabController.radioColor[OutPutTabController.radioColor.length - 1].setSelected(true);
			CellsCanvas.colorMap(OutPutTabController.radioColor[OutPutTabController.radioColor.length - 1].getText());
			Graphs(gridChart, "Total-AggregateServiceDemand.csv", "Total-AggregateAFTComposition.csv");
		}
	}

	private void saveMaps() {
		for (int i = 0; i < OutPutTabController.radioColor.length; i++) {
			int ii = i;
			if (OutPutTabController.radioColor[i].getText().contains("AFT")) {
				String newfolder = PathTools
						.makeDirectory(outputpath + File.separator + OutPutTabController.radioColor[ii].getText());
				yearChoice.getItems().forEach(filepath -> {
					servicesAndOwneroutPut(filepath, outputpath.toString());
					CellsCanvas.colorMap(OutPutTabController.radioColor[ii].getText());
					String fileyear = new File(filepath).getName().replace(".csv", "").replace("-Cell-", "");
					for (String scenario : ProjectLoader.getScenariosList()) {
						fileyear = fileyear.replace(scenario, "");
					}
					ImageExporter.NodeToImage(CellsCanvas.getCanvas(), newfolder + File.separator + fileyear + ".PNG");
				});
			}
		}
	}

	private void saveCharts() {
		String newfolder = PathTools.makeDirectory(outputpath + File.separator + "Charts");
		// First, create a snapshot of the children with their positions
		List<Node> children = new ArrayList<>(gridChart.getChildren());
		List<Integer> rowIndexes = new ArrayList<>();
		List<Integer> colIndexes = new ArrayList<>();

		for (Node child : children) {
			rowIndexes.add(GridPane.getRowIndex(child));
			colIndexes.add(GridPane.getColumnIndex(child));
		}

		// Clear children to prevent ConcurrentModificationException
		gridChart.getChildren().clear();

		// Process each child, create an image, and then re-add to the original position
		for (int i = 0; i < children.size(); i++) {
			Node child = children.get(i);
			VBox container = (VBox) child;
			@SuppressWarnings("unchecked")
			LineChart<Number, Number> ch = (LineChart<Number, Number>) container.getChildren().iterator().next();
			double w = ch.getWidth();
			double h = ch.getHeight();
			ch.setPrefSize(1000, 1000);

			Group rootPane = new Group();
			rootPane.getChildren().add(child); // Temporarily add to another group

			ImageExporter.NodeToImage(rootPane, newfolder + File.separator + ch.getTitle() + ".PNG");

			// Now re-add the child to the grid at its original position
			GridPane.setRowIndex(child, rowIndexes.get(i));
			GridPane.setColumnIndex(child, colIndexes.get(i));
			gridChart.getChildren().add(child);
			ch.setPrefSize(w, h);
		}

	}

	private void saveSankeys() {
		String newfolder = PathTools.makeDirectory(outputpath + File.separator + "Sankeys");
		List<Node> children = getSankeyPlots(borderPane);
		borderPane.getChildren().clear();

		// Process each child, create an image, and then re-add to the original position
		for (int i = 0; i < children.size(); i++) {
			Node child = children.get(i);
			((SankeyPlot) child).setPrefSize(3000, 2000);
			Group rootPane = new Group();
			rootPane.getChildren().add(child);
			ImageExporter.NodeToImage(rootPane, newfolder + File.separator + child.getId() + ".PNG");
		}
		updateSankeyPlots(AFTsLoader.getAftHash().keySet());

	}

	public static List<Node> getSankeyPlots(Parent parent) {
		List<Node> result = new ArrayList<>();

		for (Node child : parent.getChildrenUnmodifiable()) {
			// Check class name
			if ("SankeyPlot".equals(child.getClass().getSimpleName())) {
				result.add(child);
			}

			// If this node can have children, recurse
			if (child instanceof Parent) {
				result.addAll(getSankeyPlots((Parent) child));
			}
		}
		return result;
	}

	@FXML
	public void saveAllFilAsPNGAction() {
		saveMaps();
		saveCharts();
		saveSankeys();

		List<File> foders = PathTools.detectFolders(outputpath.toString());
		for (File folder : foders) {
			ImagesToPDF.createPDFWithImages(folder.getAbsolutePath(), folder.getName() + ".pdf", 4, 4);
		}
	}

	@FXML
	public void yearChoice() {
		if (Files.exists(outputpath)) {
			newOutPut(yearChoice.getValue());
		}
	}

	@FXML
	public void sankeyPlot() {
		if (Files.exists(outputpath)) {
			h = stateToHashSankey(sankeyBox.getValue());
			Set<String> selectedItemsSet = new HashSet<>();
			AFTsLoader.getAftHash().keySet().forEach(n -> {
				CheckBox radio = new CheckBox(n);
//				radioListOfAFTs.add(radio);
				radio.setSelected(true);
				selectedItemsSet.add(radio.getText());
				radio.setOnAction(_ -> {
					if (radio.isSelected()) {
						selectedItemsSet.add(radio.getText());
					} else {
						if (selectedItemsSet.size() > 1) {
							selectedItemsSet.remove(radio.getText());
						} else {
							radio.setSelected(true);
						}
					}
					updateSankeyPlots(selectedItemsSet);
				});
			});

			updateSankeyPlots(selectedItemsSet);
		}
	}

	private void updateSankeyPlots(Set<String> setManagers) {
		Text txt = new Text("Create a Sankey diagram for  ");
		Text txt2 = Tools.text(new File(yearChoice.getValue()).getName(), Color.BLUE);
		Text txt3 = new Text("  To  ");
		Text txt4 = Tools.text(new File(sankeyBox.getValue()).getName(), Color.RED);
		borderPane.getChildren().clear();
		HashMap<String, Color> colors = new HashMap<>();
		AFTsLoader.getAftHash().forEach((n, a) -> {
			colors.put(n, Color.web(a.getColor()));
		});
		Region sankeyAfts = SankeyChart.sankey(h, colors);
		sankeyAfts.setId("AFTs");
		MousePressed.mouseControle(borderPane, sankeyAfts);
		VBox aftBox = new VBox();
		aftBox.setAlignment(Pos.CENTER);
		HBox hbox = new HBox(aftBox);
		Text aftTX = Tools.text("AFTs", Color.BLUE);
		aftBox.getChildren().addAll(sankeyAfts, aftTX);
		aftTX.setScaleX(2);
		aftTX.setScaleY(2);
		borderPane.getChildren().addAll(Tools.hBox(txt, txt2, txt3, txt4, saveAllFilAsPNG), hbox);

		if (AftCategorised.aftCategories.size() != 0) {
			Region sankeyCategories = addsankeyCategories(h);
			sankeyCategories.setId("Categories");
			Text tx = Tools.text("Categories", Color.BLUE);
			tx.setScaleX(2);
			tx.setScaleY(2);
			VBox box = new VBox();
			box.setAlignment(Pos.CENTER);
			box.getChildren().addAll(sankeyCategories, tx);
			hbox.getChildren().add(box);
			GridPane grid = gridSankeyIntesity(h);

			borderPane.getChildren().add(grid);
		} else {
			double scaleY = GuiScaler.lastScreen.getBounds().getHeight() / (GuiScaler.graphicScaleY * 1.2);
			sankeyAfts.setMaxHeight(scaleY);
			sankeyAfts.setMinHeight(scaleY);
		}

	}

	private GridPane gridSankeyIntesity(HashMap<String, HashMap<String, Integer>> h) {
		HashMap<String, HashMap<String, HashMap<String, Integer>>> R = new HashMap<>();
		HashMap<String, HashMap<String, Color>> Rcolor = new HashMap<>();

		AftCategorised.CategoriesIntestisy.keySet().forEach(categoriName -> {
			R.put(categoriName, new HashMap<>());
			Rcolor.put(categoriName, new HashMap<>());
		});

		h.forEach((sender, hash) -> {
			AftCategory category = AFTsLoader.getAftHash().get(sender).getCategory();
			if (AftCategorised.CategoriesIntestisy.get(category.getName()).size() > 1) {
				HashMap<String, Integer> Rreciever = new HashMap<>();
				String categoryIntesity = category.getName() + "_" + category.getIntensity();
				R.get(category.getName()).put(categoryIntesity, Rreciever);
				Rcolor.get(category.getName()).put(categoryIntesity,
						Color.web(AFTsLoader.getAftHash().get(sender).getColor()));
				hash.forEach((reciever, nbr) -> {
					Aft aR = AFTsLoader.getAftHash().get(reciever);
					if (aR.getCategory().getName().equals(category.getName())) {
						Rreciever.merge(categoryIntesity, nbr, Integer::sum);
					} else {
						Rreciever.merge(aR.getCategory().getName(), nbr, Integer::sum);
						Rcolor.get(category.getName()).put(aR.getCategory().getName(), Color.web(aR.getColor()));
					}
				});
			}
		});
		List<Node> set = new ArrayList<>();
		R.forEach((n, hash) -> {
			if (hash.size() > 0) {
				Region p = SankeyChart.sankey(hash, Rcolor.get(n));

				p.setId("Category-" + n);
				Text txt = Tools.text("Category: " + n, Color.BLUE);
				txt.setScaleX(2);
				txt.setScaleY(2);
				VBox box = new VBox();
				box.setAlignment(Pos.CENTER);
				box.getChildren().addAll(p, txt);
				set.add(box);
				MousePressed.mouseControle(borderPane, p);
			}
		});
		return Tools.initializeGridpane(3, set);
	}

	private HashMap<String, HashMap<String, Integer>> sankyBycategories(HashMap<String, HashMap<String, Integer>> h) {
		HashMap<String, HashMap<String, Integer>> r = new HashMap<>();
		h.forEach((a, hash) -> {
			HashMap<String, Integer> rr = new HashMap<>();
			r.put(AFTsLoader.getAftHash().get(a).getCategory().getName(), rr);
			hash.forEach((af, nbr) -> {
				rr.merge(AFTsLoader.getAftHash().get(af).getCategory().getName(), nbr, Integer::sum);
			});
		});
		return r;
	}

	private Region addsankeyCategories(HashMap<String, HashMap<String, Integer>> h) {
		HashMap<String, HashMap<String, Integer>> m = sankyBycategories(h);

		HashMap<String, Color> colors = AftCategorised.categoriesColor.entrySet().stream().collect(Collectors
				.toMap(Map.Entry::getKey, e -> Color.web(e.getValue()), (_, newValue) -> newValue, HashMap::new));
		Region sankeyCategories = SankeyChart.sankey(m, colors);
		MousePressed.mouseControle(borderPane, sankeyCategories);
		return sankeyCategories;

	}
	
	private void servicesAndOwneroutPut(String year, String outputpath) {
		ProjectLoader.setAllfilesPathInData(PathTools.findAllFilePaths(ProjectLoader.getProjectPath()));
		Path path = PathTools.fileFilter(year, ".csv").get(0);
		CsvProcessors.processCSV(path, CsvKind.SERVICES);
	}

	void newOutPut(String year) {
		servicesAndOwneroutPut(year, outputpath.toString());
		for (int i = 0; i < OutPutTabController.radioColor.length; i++) {
			if (OutPutTabController.radioColor[i].isSelected()) {

				CellsCanvas.colorMap(OutPutTabController.radioColor[i].getText());
			}
		}
	}

	void Graphs(GridPane gridPane, String serviceDemand, String aftComposition) {
		gridPane.getChildren().clear();
		ArrayList<LineChart<Number, Number>> lineChart = new ArrayList<>();
		gridPane.setHgap(10);
		gridPane.setVgap(10);
		ArrayList<Path> servicespath = PathTools.fileFilter(outputpath.toString(), serviceDemand);
		Map<String, List<String>> reder = CsvProcessors.ReadAsaHash(servicespath.get(0));

		ArrayList<HashMap<String, List<Double>>> has = new ArrayList<>();
		ServiceSet.getServicesList().forEach(serviceName -> {
			HashMap<String, List<Double>> ha = new HashMap<>();
			reder.forEach((name, value) -> {
				ArrayList<Double> tmp = new ArrayList<>();
				for (int i = 0; i < value.size(); i++) {
					tmp.add(Utils.sToD(value.get(i)));
				}
				if (name.contains(serviceName)) {
					ha.put(name, tmp);
				}
			});
			if (ha != null) {
				has.add(ha);
			}
			LineChart<Number, Number> chart = new LineChart<>(new NumberAxis(), new NumberAxis());
			chart.setTitle(serviceName);
			lineChart.add(chart);
		});

		has.add(updatComposition(outputpath.toString(), aftComposition));
		LineChart<Number, Number> chart = new LineChart<>(
				new NumberAxis(Timestep.getStartYear(), Timestep.getEndtYear(), 5), new NumberAxis());
		chart.setTitle("Land use trends");
		lineChart.add(chart);
		int j = 0, k = 0;
		for (int i = 0; i < has.size(); i++) {
			LineChart<Number, Number> Ch = lineChart.get(i);
			new LineChartTools().lineChart((Pane) Ch.getParent(), Ch, has.get(i));
			// this for coloring the Chart by the AFTs color after the creation of the chart
			if (i == has.size() - 1) {
				coloringChartByAFts(Ch);
			}
			gridPane.add(Tools.vBox(Ch), j++, k);
			MousePressed.mouseControle((Pane) Ch.getParent(), Ch);
			if (j % 3 == 0) {
				k++;
				j = 0;
			}

			String ItemName = "Save as CSV";
			Consumer<String> action = _ -> {
				SaveAs.exportLineChartDataToCSV(Ch);
			};
			HashMap<String, Consumer<String>> othersMenuItems = new HashMap<>();
			othersMenuItems.put(ItemName, action);
			MousePressed.mouseControle((Pane) Ch.getParent(), Ch, othersMenuItems);
		}
	}

	private void coloringChartByAFts(LineChart<Number, Number> Ch) {
		Ch.setCreateSymbols(false);
		for (int k2 = 0; k2 < Ch.getData().size(); k2++) {
			Aft a = AFTsLoader.getAftHash().get(Ch.getData().get(k2).getName());
			Ch.getData().get(k2).getNode().lookup(".chart-series-line")
					.setStyle("-fx-stroke: " + ColorsTools.getStringColor(Color.web(a.getColor())) + ";");
		}
		LineChartTools.labelcolor(Ch);
	}

	HashMap<String, List<Double>> updatComposition(String path, String nameFile) {

		Map<String, List<String>> reder = CsvProcessors.ReadAsaHash(PathTools.fileFilter(path, nameFile).get(0));
		HashMap<String, List<Double>> has = new HashMap<>();

		reder.forEach((name, value) -> {
			if (AFTsLoader.getAftHash().keySet().contains(name)) {
				ArrayList<Double> tmp = new ArrayList<>();
				for (int i = 0; i < value.size(); i++) {
					tmp.add(Utils.sToD(value.get(i)));
				}
				has.put(name, tmp);
			}
		});
		return has;
	}

	private void plotSupplyTraker() {
		// check if there is a file
		ArrayList<Path> pathsList = PathTools.fileFilter("SupplyTracker_", ".csv");

		if (pathsList != null && pathsList.size() != 0) {
			ChoiceBox<String> filesDispo = new ChoiceBox<>();
			pathsList.forEach(path -> {
				filesDispo.getItems().add(path.getFileName().toString());
			});
			boxTracker.getChildren().add(filesDispo);

			filesDispo.valueProperty().addListener((_, _, newValue) -> {
				boxTracker.getChildren().removeIf(node -> ("grid".equals(node.getId())));
				Path path = pathsList.stream().filter(p -> p.getFileName().toString().equals(newValue)).findFirst()
						.orElse(pathsList.get(0));
				GridPane grid = gridHisto(path);
				boxTracker.getChildren().add(grid);
				grid.setMinWidth(TopBox.getMinWidth());
				Tools.forceResisingHeight(1.4, grid);
			});
			filesDispo.setValue(pathsList.iterator().next().getFileName().toString());
		}
	}

	private GridPane gridHisto(Path path) {
		// readfile
		HashMap<String, Double> rawData = CsvProcessors.readCsvToMatrixMap(path);
		GridPane grid = new GridPane();
		grid.setId("grid");
		AtomicInteger i = new AtomicInteger(), j = new AtomicInteger();
		ServiceSet.getServicesList().forEach(serviceName -> {
			BarChart<String, Number> histogram = new BarChart<>(new CategoryAxis(), new NumberAxis());
			Map<String, Double> data = new HashMap<>();
			AFTsLoader.getAftHash().keySet().forEach(aftName -> {
				if (rawData.get(aftName + "|" + serviceName) != 0)
					data.put(aftName, rawData.get(aftName + "|" + serviceName));
			});
			Histogram.histo(serviceName + " supply", histogram, data);
			HBox box = new HBox(histogram);
			MousePressed.mouseControle(box, histogram);
			if (histogram != null && data.size() > 0) {
				if (i.get() % 5 == 0) {
					i.set(0);
					j.getAndIncrement();
				}
				i.getAndIncrement();
				grid.add(box, i.get(), j.get());
				box.setMaxWidth(TopBox.getMinWidth() / 5);
			}
		});
		return grid;
	}

//	private void chengeMapColorToUtility() {
//		// also add it in png plot head less
//	}
//
//	private void plotLandEventTracker() {
//		// need a Map<> in listnera and frequncy variable
//	}

	private void PlotLandEventNbr() {
		// check if the is a file
		ArrayList<Path> pathsList = PathTools.fileFilter("-landEventCounter.csv");
		if (pathsList != null && pathsList.size() != 0) {
			// readfile as has-double
			Map<String, List<Double>> data = CsvProcessors.ReadAsaHashDouble(pathsList.get(0));
			data.remove("year");

			AreaChart<Number, Number> chart = AftAnalyzer.generateAreaChart("land EventCounter", data);
			VBox vbox = new VBox(chart);
			boxAverageAndLAndEvent.getChildren().add(vbox);
			MousePressed.mouseControle(vbox, chart);
		}

	}

	private void plotAverageUtility() {
		// check if the is a file
		ArrayList<Path> pathsList = PathTools.fileFilter("-AverageUtilities.csv");
		if (pathsList != null && pathsList.size() != 0) {
			// readfile as has-double
			Map<String, List<Double>> data = CsvProcessors.ReadAsaHashDouble(pathsList.get(0));
			data.remove("Year");

			LineChart<Number, Number> chart = new LineChart<>(new NumberAxis(), new NumberAxis());
			new LineChartTools().lineChart((Pane) chart.getParent(), chart, data);

//			AreaChart<Number, Number> chart = AftAnalyzer.generateAreaChart("landEventCounter", m);
			VBox vbox = new VBox(chart);
			boxAverageAndLAndEvent.getChildren().add(vbox);
			vbox.setMinWidth(TopBox.getMinWidth() * 2 / 3);
			MousePressed.mouseControle(vbox, chart);
		}

	}

}
