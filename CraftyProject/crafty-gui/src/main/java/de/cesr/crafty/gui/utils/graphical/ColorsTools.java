package de.cesr.crafty.gui.utils.graphical;

import java.util.ArrayList;
import java.util.Random;
import java.util.function.Consumer;

import de.cesr.crafty.gui.canvasFx.CellsCanvas;
import de.cesr.crafty.gui.main.GuiScaler;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.RadioButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Screen;
import net.mahdilamb.colormap.Colormap;
import net.mahdilamb.colormap.Colormaps;

/**
 * @author Mohamed Byari
 *
 */

public class ColorsTools {
	public static String colorPaletteType = "Viridis";

	static Color getColorForValue(String colortyp, double MAX, double value) {
		Colormap colormap = Colormaps.get(colortyp);
		if (colormap == null) {
			colormap = Colormaps.get("Viridis");
		}

		int red = colormap.get(value / MAX).getRed();
		int green = colormap.get(value / MAX).getGreen();
		int blue = colormap.get(value / MAX).getBlue();
		return Color.rgb(red, green, blue);
	}

	public static Color getColorForValue(double value) {
		return getColorForValue(colorPaletteType, 1, value);
	}

	public static Color getColorForValue(double MAX, double value) {
		return getColorForValue(colorPaletteType, MAX, value);
	}

	public static Color colorlist(Integer nbr) {
		if (nbr == null) {
			return Color.BLACK;
		}
		String str =nbr % 2 == 0 ? "Dark24" : "Light24";
		int n = nbr / 2;
		Color color = getColorForValue(str, 24, n % 24);
		return color != null ? color : Color.BLACK;

	}

	public static String toHex(Color color) {
		return String.format("#%02X%02X%02X", (int) (color.getRed() * 255), (int) (color.getGreen() * 255),
				(int) (color.getBlue() * 255));
	}

	public static Stop[] colorYchart(int nbr) {
		if (nbr % 4 == 0)
			return new Stop[] { new Stop(0.0, Color.rgb(0, 0, 255, 0.25)), new Stop(0.5, Color.rgb(255, 0, 0, 0.5)),
					new Stop(1.0, Color.rgb(50, 0, 255, 0.75)) };
		else if (nbr % 4 == 1)
			return new Stop[] { new Stop(0.0, Color.rgb(0, 255, 255, 0.25)), new Stop(0.5, Color.rgb(255, 255, 0, 0.5)),
					new Stop(1.0, Color.rgb(255, 0, 255, 0.75)) };
		else if (nbr % 4 == 2)
			return new Stop[] { new Stop(0.0, Color.rgb(0, 255, 255, 0.25)), new Stop(0.5, Color.rgb(0, 255, 255, 0.5)),
					new Stop(1.0, Color.rgb(0, 0, 255, 0.75)) };
		else if (nbr % 5 == 2)
			return new Stop[] { new Stop(0.0, Color.rgb(240, 255, 240, 0.25)),
					new Stop(0.5, Color.rgb(176, 196, 222, 0.5)), new Stop(1.0, Color.rgb(112, 128, 144, 0.75)) };
		else
			return new Stop[] { new Stop(0.0, Color.rgb(0, 0, 255, 0.25)), new Stop(0.5, Color.rgb(0, 255, 255, 0.5)),
					new Stop(1.0, Color.rgb(0, 0, 200, 0.75)) };

	}

	public static Color RandomColor() {
		// return Color.color(Math.random(), Math.random(),Math.random());
		return getColorForValue("Alphabet", 24, new Random().nextInt(24));
	}

	public static String getStringColor(Color color) {
		if (color == null)
			return String.format("#%02x%02x%02x", 255, 255, 255);
		int r = (int) (255 * color.getRed());
		int g = (int) (255 * color.getGreen());
		int b = (int) (255 * color.getBlue());

		return String.format("#%02x%02x%02x", r, g, b);
	}

	public static ArrayList<RadioButton> RadioList(Consumer<RadioButton> action, String... r) {
		ArrayList<RadioButton> paletteList = new ArrayList<>();
		for (int i = 0; i < r.length; i++) {
			paletteList.add(new RadioButton(r[i]));
		}
		for (int i = 0; i < r.length; i++) {
			int k = i;
			paletteList.get(i).setOnAction(_ -> {
				action.accept(paletteList.get(k));
				for (int j = 0; j < r.length; j++) {
					if (k != j) {
						paletteList.get(j).setSelected(false);
					}
				}
			});
		}
		return paletteList;
	}

	public static void windowzpalette(NewWindow win) {

		Pane legendPane = new Pane();
		ArrayList<RadioButton> paletteList = RadioList(e -> {
			colorPaletteType = e.getText();
			CellsCanvas.colorMap();
			drawLegend(legendPane);
		}, "Viridis", "BrBG", "Spectral", "AgSunset", "Turbo", "BlackbodyAlt", "Jet", "Portland", "MYGBM", "Geyser",
				"Temps", "SmoothCoolWarm", "BlackBodyExtended");
		VBox v = Tools.vBox(Tools.text("  Color Palette:  ", Color.BLUE));
		drawLegend(legendPane);
		paletteList.forEach(r -> {
			v.getChildren().add(r);
		});

		v.getChildren().add(legendPane);
		win.creatwindows("Color palette", v, stage -> {
			stage.setOpacity(0.7);
			// stage.initStyle(StageStyle.TRANSPARENT);

			stage.widthProperty().addListener(new ChangeListener<Number>() {
				@Override
				public void changed(ObservableValue<? extends Number> observableValue, Number oldSceneWidth,
						Number newSceneWidth) {
					stage.setX(GuiScaler.lastScreen.getBounds().getWidth() - stage.getWidth() * 1.2);
				}
			});
			stage.heightProperty().addListener(new ChangeListener<Number>() {
				@Override
				public void changed(ObservableValue<? extends Number> observableValue, Number oldSceneWidth,
						Number newSceneWidth) {
					stage.setY(GuiScaler.lastScreen.getBounds().getHeight() - stage.getHeight() * 1.2);
				}
			});

			// stage.setY(100); // Set the Y position
		});

	}

	private static void drawLegend(Pane legendPane) {
		legendPane.getChildren().clear();
		int legendWidth = 200;
		double startY = 0;
		double height = 40;

		// Creating a group of rectangles to form the gradient of colors
		for (int i = 0; i < legendWidth; i++) {
			double value = i / (double) legendWidth;
			Color color = getColorForValue(1, value);

			Rectangle rect = new Rectangle(i, startY, 1, height);
			rect.setFill(color);
			legendPane.getChildren().add(rect);
		}

		Text minLabel = new Text("0");
		minLabel.setY(startY + height + 20);
		legendPane.getChildren().add(minLabel);

		Text maxLabel = new Text("1");
		maxLabel.setX(legendWidth - 20);
		maxLabel.setY(startY + height + 20);
		legendPane.getChildren().add(maxLabel);

		Text midLabel = new Text("0.5");
		midLabel.setX(legendWidth / 2 - 10);
		midLabel.setY(startY + height + 20);
		legendPane.getChildren().add(midLabel);

	}

}
