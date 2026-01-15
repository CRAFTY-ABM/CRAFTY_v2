package de.cesr.crafty.gui.canvasFx;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import de.cesr.crafty.gui.utils.graphical.ColorsTools;
import de.cesr.crafty.gui.utils.graphical.NewWindow;
import de.cesr.crafty.gui.utils.graphical.SaveAs;
import de.cesr.crafty.gui.utils.graphical.SmoothMockField;
import de.cesr.crafty.gui.utils.graphical.Tools;
import de.cesr.crafty.gui.controller.fxml.RegionController;
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
import de.cesr.crafty.core.updaters.Capital_Degradation_Updater;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.SubScene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
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
	// private static CellsLoader cellsSet;

//	public static Pane root = new Pane();
	public static SubScene subScene;// = new SubScene(root, FxMain.defaultWidth / 2, FxMain.defaultHeight);
	public static int maxX, maxY, minX, minY;
	static SmoothMockField field;

	public static void plotCells() {
		initialMaxMinXY();
		canvas = new Canvas();
		gc = canvas.getGraphicsContext2D();
		writableImage = new WritableImage(maxX - minX, maxY - minY);
		pixelWriter = writableImage.getPixelWriter();
		gc.setImageSmoothing(false);
		MapPane canvasPane = new MapPane();

		subScene = new SubScene(canvasPane, GuiScaler.lastScreen.getBounds().getWidth() / (2 * GuiScaler.graphicScaleX),
				(GuiScaler.lastScreen.getBounds().getHeight() / GuiScaler.graphicScaleY));
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
		CellsLoader.hashCell.values().parallelStream().forEach(cell -> {
			if (cell.getOwner() == null || !cell.getOwner().getLabel().equals(a.getLabel())) {
				ColorP(cell, Color.GRAY);
			} else {
				ColorP(cell, a.getColor());
			}
		});
		gc.drawImage(writableImage, 0, 0);
	}

	public static void colorMap(String str) {
		colortype = str;
		colorMap();
	}

	static AtomicInteger step = new AtomicInteger(1);

	public static void colorMap() {
		LOGGER.info("Changing the map colors...");
		Set<Double> values = Collections.synchronizedSet(new HashSet<>());
		if (colortype.equalsIgnoreCase("Agent") || colortype.equalsIgnoreCase("AFT")) {
			CellsLoader.hashCell.values().parallelStream().forEach(c -> {
				if (c.getOwner() != null) {
					ColorP(c, c.getOwner().getColor());
				} else {
					ColorP(c, AFTsLoader.getAftHash().get("Abandoned").getColor());
				}
			});
		} else if (CapitalUpdater.getCapitalsList().contains(colortype)) {
			CellsLoader.hashCell.values().parallelStream().forEach(c -> {
				if (c != null && c.getCapitals().get(colortype) != null)
					ColorP(c, ColorsTools.getColorForValue(c.getCapitals().get(colortype)));
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
		} else if (colortype.equalsIgnoreCase("Shocks")) {
			System.out.println(Capital_Degradation_Updater.cellsShocks.size());
			CellsLoader.hashCell.values().forEach(c -> {
				if (c != null) {
					ColorP(c, ColorsTools
							.getColorForValue(Capital_Degradation_Updater.cellsShocks.get(c).get("ExtConifer")));
				}

			});

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
	}

	static AtomicInteger nbr = new AtomicInteger(52);

	public static void MapControlerBymouse() {
		CellsCanvas.getCanvas().setOnMouseClicked(event -> {
			if (event.getButton() != MouseButton.SECONDARY) {
				return; // ignore other buttons
			}

			double worldX = (event.getX() - MapPane.offsetX) / MapPane.scale + CellsCanvas.minX;
			double worldY = (event.getY() - MapPane.offsetY) / MapPane.scale + CellsCanvas.minY;

			int cellX = (int) worldX;
			int cellY = (int) worldY;

			Cell cell = CellsLoader.getCell(cellX, cellY);
			if (cell != null) {
				ColorP(cell, Color.RED);
				gc.drawImage(writableImage, 0, 0);
//				gc.fillRect(worldX, worldY, Cell.getSize(), Cell.getSize());
				HashMap<String, Consumer<String>> menu = new HashMap<>();
				menu.put("Print Cell Info into the Console", _ -> {
					System.out.println(cell);
				});
				menu.put("Save Map as PNG", _ -> {
					SaveAs.png("", canvas);
				});
//				menu.put("Selecet Region", _ -> {
//					selectRegion(cell);
//				});
//				menu.put("Clean Regions", _ -> {
//					box.getChildren().clear();
//					RegionController.getRegionCells().clear();
//				});
//				menu.put("Open Regions Selected", _ -> {
//					openRegions(cell);
//				});
				menu.put("Detach", _ -> {
					try {
						VBox mapBox = (VBox) subScene.getParent();
						VBox parent = (VBox) subScene.getParent().getParent();
						List<Integer> findpath = Tools.findIndexPath(mapBox, parent);
						Tools.reInsertChildAtIndexPath(new Separator(), parent, findpath);
						NewWindow win = new NewWindow();
						double origineW = subScene.getWidth();
						double originH = subScene.getHeight();

						subScene.setWidth(GuiScaler.lastScreen.getBounds().getWidth() * .8);
						subScene.setHeight(GuiScaler.lastScreen.getBounds().getHeight() * 0.8);

						win.creatwindows("Map", mapBox);
						MapPane.fitMapInWindow();
						win.setOnCloseRequest(_ -> {
							parent.getChildren().add(mapBox);
							subScene.setWidth(origineW);
							subScene.setHeight(originH);
						});
					} catch (ClassCastException d) {
						LOGGER.warn(d.getMessage());
					}
				});

				ContextMenu cm = new ContextMenu();

				MenuItem[] item = new MenuItem[menu.size()];
				AtomicInteger i = new AtomicInteger();
				menu.forEach((k, v) -> {
					item[i.get()] = new MenuItem(k);
					cm.getItems().add(item[i.get()]);
					item[i.get()].setOnAction(_ -> {
						v.accept(k);
					});
					i.getAndIncrement();
				});
				cm.show(canvas.getScene().getWindow(), event.getScreenX(), event.getScreenY());
				event.consume();
			}
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
