package de.cesr.crafty.gui.canvasFx;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import de.cesr.crafty.gui.utils.graphical.ColorsTools;
import de.cesr.crafty.gui.utils.graphical.NewWindow;
import de.cesr.crafty.gui.utils.graphical.SaveAs;
import de.cesr.crafty.gui.utils.graphical.SmoothMockField;
import de.cesr.crafty.gui.controller.fxml.RegionController;
import de.cesr.crafty.gui.controller.fxml.TabPaneController;
import de.cesr.crafty.gui.main.FxMain;
import de.cesr.crafty.gui.main.GuiScaler;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.afts.AftCategorised;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.land.GisLoader;
import de.cesr.crafty.core.dataLoader.land.MaskLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.CapitalUpdater;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.SubScene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * @author Mohamed Byari
 *
 */

public class CellsCanvas {
	private static final CustomLogger LOGGER = new CustomLogger(CellsCanvas.class);
	private static Canvas canvas;
	static GraphicsContext gc;
	private static PixelWriter pixelWriter;
	private static WritableImage writableImage;

	private static String colortype = "AFT";
	private static volatile String displayedCapital;
	private static volatile Integer displayedCapitalYear;
	private static volatile Map<Cell, Double> displayedCapitalValues = Map.of();
	private static volatile String displayedAftLabel;
	// private static CellsLoader cellsSet;

//	public static Pane root = new Pane();
	public static SubScene subScene;// = new SubScene(root, FxMain.defaultWidth / 2, FxMain.defaultHeight);
	public static int maxX, maxY, minX, minY;
	static SmoothMockField field;

	public static void plotCells() {
		initialMaxMinXY();
		canvas = new Canvas();
		gc = canvas.getGraphicsContext2D();
		installMapContextMenu();
		writableImage = new WritableImage(maxX - minX, maxY - minY);
		pixelWriter = writableImage.getPixelWriter();
		gc.setImageSmoothing(false);
		MapPane canvasPane = new MapPane();

		Rectangle2D screen = GuiScaler.lastScreen.getVisualBounds();
		subScene = new SubScene(canvasPane, Math.max(600, screen.getWidth() * 0.48),
				Math.max(400, screen.getHeight() * 0.8));
		MapPane.fitMapInWindow();
		
	}

	private static void initialMaxMinXY() {
		ArrayList<Integer> X = new ArrayList<>();
		ArrayList<Integer> Y = new ArrayList<>();

		CellsLoader.hashCell.values().forEach(c -> {
			X.add(c.getX());
			Y.add(c.getY());
		});
		maxX = Collections.max(X) + 1;
		maxY = Collections.max(Y) + 1;
		minX = Collections.min(X);
		minY = Collections.min(Y);
		field = new SmoothMockField((maxX - minX), (maxY - minY), 100, 25, 0.05);
	}

	public static void ColorP(Cell c, Color color) {
		pixelWriter.setColor(c.getX() - minX, c.getY() - minY, color);
	}

	public static void ColorP(Cell c, String color) {
		ColorP(c, Color.web(color));
	}

	public void ColorP(Cell c) {
		ColorP(c, c.getColor());
	}

	public static ConcurrentHashMap<String, Cell> getSubset(ConcurrentHashMap<String, Cell> cellsHash,
			double percentage) {

		int numberOfElementsToSelect = (int) (cellsHash.size() * (percentage));
		ConcurrentHashMap<String, Cell> subset = new ConcurrentHashMap<>();
		cellsHash.keySet().parallelStream().unordered().limit(numberOfElementsToSelect)
				.forEach(key -> subset.put(key, cellsHash.get(key)));
		return subset;
	}

	public static void showOnlyOneAFT(Aft a) {
		if (a == null) {
			return;
		}
		clearCapitalDisplayOverride();
		displayedAftLabel = a.getLabel();
		colortype = "AFT";
		colorMap();
	}

	public static void colorMap(String str) {
		clearCapitalDisplayOverride();
		clearAftDisplayFilter();
		colortype = str;
		colorMap();
	}

	/** Displays capital values loaded for a year without changing any cell state. */
	public static void colorCapitalMap(String capital, int year, Map<Cell, Double> values) {
		clearAftDisplayFilter();
		displayedCapitalValues = Map.copyOf(values);
		displayedCapital = capital;
		displayedCapitalYear = year;
		colortype = capital;
		colorMap();
	}

	public static Double getDisplayedCapitalValue(Cell cell, String capital) {
		if (capital != null && capital.equals(displayedCapital)) {
			return displayedCapitalValues.get(cell);
		}
		return cell.getCapitals().get(capital);
	}

	public static Integer getDisplayedCapitalYear(String capital) {
		return capital != null && capital.equals(displayedCapital) ? displayedCapitalYear : null;
	}

	private static void clearCapitalDisplayOverride() {
		displayedCapital = null;
		displayedCapitalYear = null;
		displayedCapitalValues = Map.of();
	}

	private static void clearAftDisplayFilter() {
		displayedAftLabel = null;
	}

	public static String getColorType() {
		return colortype;
	}

	public static WritableImage getMapImage() {
		return writableImage;
	}

	static AtomicInteger step = new AtomicInteger(1);

	public static void colorMap() {
//		LOGGER.info("Changing the map colors...");
		Set<Double> values = Collections.synchronizedSet(new HashSet<>());
		if (colortype.equalsIgnoreCase("Agent") || colortype.equalsIgnoreCase("AFT")) {
			String aftFilter = displayedAftLabel;
			CellsLoader.hashCell.values().parallelStream().forEach(c -> {
				if (c.getOwner() != null
						&& (aftFilter == null || c.getOwner().getLabel().equals(aftFilter))) {
					ColorP(c, c.getOwner().getColor());
				} else if (aftFilter != null) {
					ColorP(c, Color.GRAY);
				} else {
					ColorP(c, AFTsLoader.getAftHash().get("Abandoned").getColor());
				}
			});
		} else if (CapitalUpdater.getCapitalsList().contains(colortype)) {
			CellsLoader.hashCell.values().parallelStream().forEach(c -> {
				if (c != null) {
					Double value = getDisplayedCapitalValue(c, colortype);
					ColorP(c, value == null ? Color.GRAY : ColorsTools.getColorForValue(value));
				}
			});

		} else if (ServiceSet.getServicesList().contains(colortype)) {
			CellsLoader.hashCell.values().parallelStream().forEach(c -> {
				values.add(c.getCurrentProd()[ServiceSet.getServicesList().indexOf(colortype)]);
			});
			double max = values.size() > 0 ? Collections.max(values) : 0;

			CellsLoader.hashCell.values().parallelStream().forEach(c -> {
				ColorP(c, ColorsTools.getColorForValue(max,
						c.getCurrentProd()[ServiceSet.getServicesList().indexOf(colortype)]));
			});
		} else if (colortype.equalsIgnoreCase("Mask")) {
			ArrayList<String> listOfMasks = new ArrayList<>(MaskLoader.mask_paths.keySet());
			CellsLoader.hashCell.values().parallelStream().forEach(c -> {
				if (c.getMaskType() != null) {
					ColorP(c, ColorsTools.colorlist(listOfMasks.indexOf(c.getMaskType())));
				} else {
					ColorP(c, Color.gray(0.75));
				}
			});
		} else if (colortype.equalsIgnoreCase("Categories")) {
			if (AftCategorised.aftCategories.size() != 0) {
				CellsLoader.hashCell.values().parallelStream().forEach(c -> {
					if (c.getOwner() != null)
						ColorP(c, AftCategorised.categoriesColor.get(c.getOwner().getCategory().getName()));
				});
			}
//		} else if (colortype.equalsIgnoreCase("Shocks")) {
//			System.out.println(Capital_Degradation_Updater.cellsShocks.size());
//			CellsLoader.hashCell.values().forEach(c -> {
//				if (c != null) {
//					ColorP(c, ColorsTools
//							.getColorForValue(Capital_Degradation_Updater.cellsShocks.get(c).get("ExtConifer")));
//				}
//
//			});

		} else if (colortype.equalsIgnoreCase("Mock")) {
			// loop

			field.color(step.getAndIncrement());

		}

		else {
			HashMap<String, Color> colorGis = new HashMap<>();

			CellsLoader.hashCell.values().parallelStream().forEach(c -> {
				if (c.getCurrentRegion() != null) {
					colorGis.put(c.getCurrentRegion(),
							ColorsTools.colorlist(GisLoader.regionIDs.get(c.getCurrentRegion())));
				}
			});
			CellsLoader.hashCell.values().parallelStream().forEach(c -> {
				ColorP(c, colorGis.getOrDefault(c.getCurrentRegion(), Color.WHITE));
			});
		}
		gc.drawImage(writableImage, 0, 0);
		MapStatisticsPane.refresh(colortype);
	}

	static AtomicInteger nbr = new AtomicInteger(52);

	private static void installMapContextMenu() {
		canvas.setOnContextMenuRequested(event -> {
			double worldX = (event.getX() - MapPane.offsetX) / MapPane.scale + minX;
			double worldY = (event.getY() - MapPane.offsetY) / MapPane.scale + minY;
			Cell cell = CellsLoader.getCell((int) Math.floor(worldX), (int) Math.floor(worldY));

			MenuItem printCell = new MenuItem(cell == null
					? "Print cell info (no cell at this position)"
					: "Print cell info to console");
			printCell.setDisable(cell == null);
			if (cell != null) {
				printCell.setOnAction(_ -> System.out.println(cell));
			}

			MenuItem savePng = new MenuItem("Save map as PNG");
			savePng.setOnAction(_ -> SaveAs.png("CRAFTY-map", canvas));

			MenuItem detach = new MenuItem("Detach map");
			TabPaneController controller = TabPaneController.getInstance();
			detach.setDisable(controller == null);
			if (controller != null) {
				detach.setOnAction(_ -> controller.detachMap());
			}

			ContextMenu menu = new ContextMenu(printCell, new SeparatorMenuItem(), savePng, detach);
			menu.show(canvas.getScene().getWindow(), event.getScreenX(), event.getScreenY());
			event.consume();
		});
	}

	static VBox box = new VBox();

	private static void openRegions(Cell c) {
		if (c.getCurrentRegion() != null) {
			Platform.runLater(() -> {
				URL fxml = FxMain.class.getResource("/fxmlControllers/Region.fxml");
				NewWindow win = new NewWindow();
				try {
					box.getChildren().add(FXMLLoader.load(fxml));
					win.creatwindows(c.getCurrentRegion(), box);
				} catch (IOException e) {
				}
			});
		}
	}

	private static void selectRegion(Cell c) {
		CellsLoader.hashCell.values()./* parallelStream(). */forEach(cs -> {
			if (c.getCurrentRegion().equals(cs.getCurrentRegion())) {
				gc.setFill(Color.GRAY);
				gc.fillRect(cs.getX(), cs.getY(), 1, 1);
				// initial cells
				Cell newCEll = copyCell(cs);
				
				if (cs.getOwner() != null)
					newCEll.setColor(cs.getOwner().getColor());
				else
					newCEll.setColor("#000000");
				RegionController.getRegionCells().put(cs.getX() + "," + cs.getY(), newCEll);
			}
		});
	}

	public static GraphicsContext getGc() {
		return gc;
	}

	public static Canvas getCanvas() {
		return canvas;
	}

	public static void setCanvas(Canvas canvas) {
		CellsCanvas.canvas = canvas;
	}
	
	// --------------------------
	private static Cell  copyCell(Cell cell) {
		Cell c= new Cell(cell.getX(), cell.getY());
		c.setColor(cell.getColor());
		c.setCurrentRegion(cell.getCurrentRegion());
		c.setCapitals(cell.getCapitals());
		c.setId(cell.getId());
		c.setOwner(cell.getOwner());
		return c;
	}

}
