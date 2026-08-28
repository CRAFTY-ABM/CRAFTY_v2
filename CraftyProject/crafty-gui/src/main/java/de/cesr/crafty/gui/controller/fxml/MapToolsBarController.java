package de.cesr.crafty.gui.controller.fxml;

import javafx.fxml.FXML;
import javafx.scene.Cursor;
import de.cesr.crafty.gui.canvasFx.CellsCanvas;
import de.cesr.crafty.gui.canvasFx.MapPane;
import de.cesr.crafty.gui.canvasFx.MapPane.MouseMode;
import de.cesr.crafty.gui.utils.graphical.ColorsTools;
import de.cesr.crafty.gui.utils.graphical.NewWindow;
import de.cesr.crafty.gui.main.FxMain;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;

import javafx.util.Duration;

public class MapToolsBarController {

	@FXML
	private Button colorPallet, center;
	@FXML
	private ToggleButton hand, zoom, earth, gis;

	private final ToggleGroup interactionModes = new ToggleGroup();
	private final ToggleGroup mapViews = new ToggleGroup();

	@FXML
	private void initialize() {
		hand.setToggleGroup(interactionModes);
		zoom.setToggleGroup(interactionModes);
		earth.setToggleGroup(mapViews);
		gis.setToggleGroup(mapViews);
		hand.setSelected(true);
		earth.setSelected(true);

		for (Button button : new Button[] { colorPallet, center }) {
			button.getTooltip().setShowDelay(Duration.millis(100));
		}
		for (ToggleButton button : new ToggleButton[] { hand, zoom, earth, gis }) {
			button.getTooltip().setShowDelay(Duration.millis(100));
		}

		MapPane.mouseMode = MouseMode.PAN;
		if (CellsCanvas.getCanvas() != null) {
			CellsCanvas.colorMap("AFT");
		}

	}

	// Event Listener on Button[#handButton].onAction
	@FXML
	public void pointer(ActionEvent event) {
		MapPane.mouseMode = MouseMode.SELECT;
		FxMain.scene.setCursor(Cursor.DEFAULT);
	}

	// Event Listener on Button[#handButton].onAction
	@FXML
	public void handleHandAction(ActionEvent event) {
		hand.setSelected(true);
		MapPane.mouseMode = MouseMode.PAN;
		FxMain.scene.setCursor(Cursor.OPEN_HAND);
	}

	// Event Listener on Button[#zoomButton].onAction
	@FXML
	public void zoomAction(ActionEvent event) {
		zoom.setSelected(true);
		MapPane.mouseMode = MouseMode.ZOOM;
		FxMain.scene.setCursor(Cursor.CROSSHAIR);
	}

	// Event Listener on Button[#zoomInButton].onAction
	@FXML
	public void handleZoomInAction(ActionEvent event) {
		FxMain.scene.setCursor(Cursor.CROSSHAIR);
		MapPane.zoom(1);
	}

	// Event Listener on Button[#zoomOutButton].onAction
	@FXML
	public void handleZoomOutAction(ActionEvent event) {
		FxMain.scene.setCursor(Cursor.CROSSHAIR);
		MapPane.zoom(-1);
	}

	// Event Listener on Button[#earthButton].onAction
	@FXML
	public void handleearthAction(ActionEvent event) {
		earth.setSelected(true);
		FxMain.scene.setCursor(Cursor.DEFAULT);
		CellsCanvas.colorMap("AFT");
		MapPane.fitMapInWindow();
	}

	// Event Listener on Button[#eyeButton].onAction
	@FXML
	public void gisAction(ActionEvent event) {
		gis.setSelected(true);
		FxMain.scene.setCursor(Cursor.DEFAULT);
		CellsCanvas.colorMap("Region_Code");
	}

	@FXML
	public void centerMap(ActionEvent event) {
		FxMain.scene.setCursor(Cursor.DEFAULT);
		MapPane.fitMapInWindow();
	}

	@FXML
	public void colorPallet(ActionEvent event) {
		FxMain.scene.setCursor(Cursor.DEFAULT);
		NewWindow winColor = new NewWindow();
		ColorsTools.windowzpalette(winColor);
	}
}
