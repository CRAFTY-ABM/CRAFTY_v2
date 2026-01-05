package de.cesr.crafty.gui.controller.fxml;

import java.util.concurrent.atomic.AtomicInteger;

import de.cesr.crafty.core.crafty.ManagerTypes;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.gui.main.FxMain;
import de.cesr.crafty.gui.main.GuiScaler;
import de.cesr.crafty.gui.utils.graphical.Tools;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class AftsAnaliserController {
	@FXML
	private GridPane grid;
	@FXML
	private ScrollPane scroll;
	@FXML
	private VBox TopBox;

	public void initialize() {
		System.out.println("initialize " + getClass().getSimpleName());

		AtomicInteger i = new AtomicInteger();
		AtomicInteger j = new AtomicInteger();

		AFTsLoader.getActivateAFTsHash().forEach((name, aft) -> {
			if (aft.getType() == ManagerTypes.AFT) {
				LineChart<Number, Number> chart = AFTsProductionController.productivitySampleChart(name, true);
				if (chart != null) {
					HBox box= new HBox(chart);
					if (i.get() % 3 == 0) {
						i.set(0);
						j.getAndIncrement();
					}
					i.getAndIncrement();
					grid.add(box, i.get(), j.get());
				}
			}
		});

		Tools.forceResisingWidth(TopBox);
		forceResizing();
	}

	private void forceResizing() {
		double scaley = GuiScaler.lastScreen.getBounds().getHeight() / (FxMain.graphicScaleY * 1.2);
		scroll.setMaxHeight(scaley);
		scroll.setMinHeight(scaley);
	}
}
