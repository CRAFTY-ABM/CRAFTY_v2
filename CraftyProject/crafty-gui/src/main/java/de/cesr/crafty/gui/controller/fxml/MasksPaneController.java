package de.cesr.crafty.gui.controller.fxml;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import de.cesr.crafty.gui.canvasFx.CellsCanvas;
import de.cesr.crafty.gui.utils.graphical.MousePressed;
import de.cesr.crafty.gui.utils.graphical.Tools;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.land.MaskLoader;
import de.cesr.crafty.core.updaters.LandMaskUpdater;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;
import eu.hansolo.fx.charts.CircularPlot;
import eu.hansolo.fx.charts.CircularPlotBuilder;
import eu.hansolo.fx.charts.data.PlotItem;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

public class MasksPaneController {
	@FXML
	private VBox TopBox;
	GridPane grid = new GridPane();
	static ArrayList<CheckBox> radioListOfMasks;
	// cell.getMaskTyp->hash(owner_competitor-> true or false)

	CircularPlot[] circularPlot;
	private static boolean iscolored = false;

	private static MasksPaneController instance;

	public MasksPaneController() {
		instance = this;
	}

	public static MasksPaneController getInstance() {
		return instance;
	}

	@SuppressWarnings("unchecked")
	public void initialize() {

		radioListOfMasks = new ArrayList<>();

		initializeBoxs();

		circularPlot = new CircularPlot[radioListOfMasks.size()];
		TitledPane[] T = new TitledPane[radioListOfMasks.size()];
		AtomicInteger x = new AtomicInteger();
		AtomicInteger y = new AtomicInteger();
		radioListOfMasks.forEach(r -> {
			r.setOnAction(e -> {
//				MaskLoader.maskByScenario(r.getText());
				int i = radioListOfMasks.indexOf(r);
				if (r.isSelected()) {
					// initial nodes
					ChoiceBox<String> boxYears = Tools.choiceBox(filePathToYear(r.getText()));
					ArrayList<CheckBox> radioListOfAFTs = new ArrayList<>();
					VBox boxOfAftRadios = initilazeAFTboxs(radioListOfAFTs);
					ArrayList<PlotItem> itemsList = initPlotItem();

//					MaskLoader.restrictionsByScenario(r.getText());
					ConcurrentHashMap<String, Boolean> restrictionsRul = LandMaskUpdater.restrictions.get(r.getText());
					// default circular plot
					List<PlotItem> items = circularPlot(itemsList, restrictionsRul, radioListOfAFTs.get(0).getText(),
							true);
					circularPlot[i] = CircularPlotBuilder.create().items(items).decimals(0).connectionOpacity(0.9)
							.minorTickMarksVisible(false).build();

					boxYears.setOnAction(_ -> {
						yearAction(r, boxYears, restrictionsRul, radioListOfAFTs);
					});
					boxYears.fireEvent(e);

					VBox boxMask = new VBox();
					T[i] = Tools.T("  Possible transitions for " + r.getText() + " Restriction ", true, boxMask);

					Text text = Tools.text(
							"Select the AFT (landowner) to display the possible transitions from this AFT to other AFTs (competitors):",
							Color.BLUE);
					boxMask.getChildren().addAll(Tools.hBox(text, boxYears),
							Tools.hBox(boxOfAftRadios, circularPlot[i]));
					radioListOfAFTs.forEach(rad -> {
						rad.setOnAction(_ -> {
							circularPlot[i].setItems(
									circularPlot(itemsList, restrictionsRul, rad.getText(), rad.isSelected()));
						});
					});

					MousePressed.mouseControle(boxMask, circularPlot[i]);
//					int place = TopBox.getChildren().indexOf(r) + 1;
					// TopBox.getChildren().add(place, T[i]);
					if (x.get() % 2 == 0) {
						x.set(0);
						y.getAndIncrement();
					}
					x.getAndIncrement();
					grid.add(T[i], x.get(), y.get());

				} else {
					removeMask(r, T[i]);
				}
				if (iscolored) {
					CellsCanvas.colorMap("Mask");
				}
			});
		});
		initialiseMask();
		TopBox.getChildren().add(grid);
		Tools.forceResisingWidth(TopBox, grid);
		Tools.forceResisingHeight(TopBox);

	}

	private void removeMask(CheckBox r, TitledPane T) {
		LandMaskUpdater.cleanMaskType(r.getText());
		LandMaskUpdater.restrictions.remove(r.getText());
		grid.getChildren().removeAll(T);
		MaskLoader.mask_paths.remove(r.getText());
	}

	private void initializeBoxs() {
		MaskLoader.mask_paths.keySet().forEach(maskName -> {
			CheckBox r = new CheckBox(maskName);
			radioListOfMasks.add(r);
			TopBox.getChildren().add(r);
		});
	}

	private VBox initilazeAFTboxs(ArrayList<CheckBox> radioListOfAFTs) {
		VBox boxOfAftRadios = new VBox();
		AFTsLoader.getAftHash().keySet().forEach(n -> {
			CheckBox radio = new CheckBox(n);
			radioListOfAFTs.add(radio);
			boxOfAftRadios.getChildren().add(radio);
		});
		radioListOfAFTs.get(0).setSelected(true);
		return boxOfAftRadios;
	}

	private void yearAction(CheckBox r, ChoiceBox<String> boxYears, Map<String, Boolean> restrictionsRul,
			ArrayList<CheckBox> radioListOfAFTs) {
		LandMaskUpdater.cellOneMaskUpdater(r.getText(), (int) Utils.sToD(boxYears.getValue()));
		CellsCanvas.colorMap("Mask");
		updateRestrections(r.getText(), boxYears.getValue(), restrictionsRul);
		radioListOfAFTs.get(0).setSelected(true);
	}

	private List<String> filePathToYear(String maskType) {
		List<String> years = new ArrayList<>();
		MaskLoader.mask_paths.get(maskType).values().forEach(path -> {
			for (int i = Timestep.getStartYear(); i < Timestep.getEndtYear(); i++) {
				if (path.toString().contains(String.valueOf(i))) {
					years.add(i + "");
					break;
				}
			}
		});
		return years;
	}

	// Event Listener on Button[#handButton].onAction
	@FXML
	public void clear(ActionEvent event) {
		radioListOfMasks.forEach(r -> {
			r.setSelected(false);
			r.fireEvent(event);
		});
	}

	static void initialiseMask() {
		iscolored = false;
		radioListOfMasks.forEach(r -> {
			r.setSelected(true);
			r.fireEvent(new ActionEvent());
		});
		iscolored = true;
		CellsCanvas.colorMap("AFT");
	}

	private ArrayList<PlotItem> initPlotItem() {
		ArrayList<PlotItem> itemsList = new ArrayList<>();
		AFTsLoader.getAftHash().values().forEach(a -> {
			itemsList.add(new PlotItem(a.getLabel(), 10, Color.web(a.getColor())));
		});
		return itemsList;
	}

	private List<PlotItem> circularPlot(ArrayList<PlotItem> itemsList, Map<String, Boolean> restrictions, String ow,
			boolean toAdd) {

		// itemsList.forEach(owner -> {});

		PlotItem own = null;
		for (Iterator<PlotItem> iterator = itemsList.iterator(); iterator.hasNext();) {
			PlotItem plotItem = (PlotItem) iterator.next();
			if (plotItem.getName().equals(ow)) {
				own = plotItem;
				break;
			}
		}
		PlotItem owner = own;

		itemsList.forEach(competitor -> {
			int nbr = -1;
			if (restrictions.get(owner.getName() + "_" + competitor.getName()) != null) {
				nbr = restrictions.get(owner.getName() + "_" + competitor.getName()) ? 1 : -1;
			}
			if (toAdd) {
				owner.addToOutgoing(competitor, nbr);
			} else
				owner.removeFromOutgoing(competitor);
		});

		PlotItem[] its = new PlotItem[itemsList.size()];
		for (int i = 0; i < its.length; i++) {
			its[i] = itemsList.get(i);
		}

		List<PlotItem> items = List.of(its);

		return items;
	}

	public static void updateRestrections(String maskType, String currentyear, Map<String, Boolean> restriction) {
		ArrayList<Path> restrictionsFile = PathTools.fileFilter(currentyear, ProjectLoader.getScenario(),
				"LandUseControl", "Restrictions", maskType, ".csv");
		if (restrictionsFile == null || restrictionsFile.isEmpty()) {
			System.out.println(maskType + " Restrections updated ");
			return;
		}
		restriction.clear();
		String[][] matrix = CsvTools.csvReader(restrictionsFile.get(0));
		if (matrix != null) {
			for (int i = 1; i < matrix.length; i++) {
				for (int j = 1; j < matrix[0].length; j++) {
					restriction.put(matrix[i][0] + "_" + matrix[0][j], matrix[i][j].contains("1"));
				}
			}
		}
		System.out.println(maskType + " Restrections updated ");
	}

}
