package de.cesr.crafty.gui.controller.fxml;

import java.util.function.Consumer;

import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.CapitalUpdater;
import de.cesr.crafty.gui.utils.graphical.CSVTableView;
import de.cesr.crafty.gui.utils.graphical.ColorsTools;
import de.cesr.crafty.gui.utils.graphical.NewWindow;
import de.cesr.crafty.gui.utils.graphical.Tools;
import javafx.collections.ObservableList;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

/**
 * @author Mohamed Byari
 *
 */

public class NewAFT_Controller extends AFTsConfigurationController {
	AFTsConfigurationController parent;

	public NewAFT_Controller(AFTsConfigurationController parent) {
		this.parent = parent;

	}

	static VBox vbox = new VBox();

	public void addaft() {
		TextField fieldText = new TextField("AFT_Name");
		NewWindow windowAddAFT = new NewWindow();
		BorderPane rootPane = new BorderPane();
		Button addToThisSimulation = Tools.button("Add To This Simulation", "b6e7c9");
		Button addToDATA = Tools.button("ADD To Input Data", "b6e7c9");
		ColorPicker colorPicker = new ColorPicker();
		TextArea textArea = new TextArea();

		windowAddAFT.creatwindows("Add New Agent Functional Type", 0.7, 0.9, rootPane);
		Aft newAFT = new Aft("newAFT");

		colorPicker.setOnAction(_ -> {
			newAFT.setColor(ColorsTools.toHex(colorPicker.getValue()));
		});

		String[][] production = new String[2][ServiceSet.getServicesList().size()];

		for (int j = 0; j < newAFT.getProductivityLevel().keySet().toArray().length; j++) {
			production[0][j] = (String) newAFT.getProductivityLevel().keySet().toArray()[j];
			production[1][j] = "0.0";
		}
		Button productionFire = new Button();
		Consumer<String> actionP = _ -> {
			productionFire.fire();
		};

		TableView<ObservableList<String>> tableProduction = CSVTableView.newtable(production, actionP);
		BarChart<String, Number> histogram = new BarChart<String, Number>(new CategoryAxis(), new NumberAxis());

//		productionFire.setOnAction(e -> {
//			updateProduction(newAFT, tableProduction);
//			Histogram.histo(vbox, "Productivity levels", histogram, newAFT.getProductivityLevel());
//		});

		String[][] sensetivtyTable = new String[ServiceSet.getServicesList().size()
				+ 1][CapitalUpdater.getCapitalsList().size() + 1];
		for (int i = 0; i < ServiceSet.getServicesList().size(); i++) {
			sensetivtyTable[i + 1][0] = ServiceSet.getServicesList().get(i);
			for (int j = 0; j < CapitalUpdater.getCapitalsList().size(); j++) {
				sensetivtyTable[0][j + 1] = CapitalUpdater.getCapitalsList().get(j);
				sensetivtyTable[i + 1][j + 1] = "0.0";
			}
		}
		Button sensitivtyFire = new Button();
		Consumer<String> action = _ -> {
			sensitivtyFire.fire();
		};

		GridPane gridBehevoir = new GridPane();
		// AgentParametre(newAFT, gridBehevoir);

		TableView<ObservableList<String>> tableSensetivty = CSVTableView.newtable(sensetivtyTable, action);

		GridPane gridRadar = Tools.grid(10, 15);
		Text name = new Text();

		sensitivtyFire.setOnAction(_ -> {
			updateSensitivty(newAFT, gridRadar, tableSensetivty);
		});
		fieldText.setOnAction(_ -> {
			String n = fieldText.getText();
			name.setText(n);
			newAFT.setCompleteName(n);
			newAFT.setLabel(n);

		});
		addToDATA.setOnAction(_ -> {
			addToThisSimulation.fire();
			creatCsvFiles(newAFT, textArea.getText());
		});

		addToThisSimulation.setOnAction(_ -> {
			System.out.println(newAFT);
			AFTsLoader.getAftHash().put(newAFT.getLabel(), newAFT);
			parent.updaChoisButton();
		});

		sensitivtyFire.fire();
		productionFire.fire();
		vbox.getChildren().addAll(
				Tools.hBox(Tools.text("Agent Functional Type name:   ", Color.BLUE), fieldText, name,
						new Text("AFT Color"), colorPicker),
				Tools.hBox(Tools.vBox(tableProduction, Tools.T(" Behevoir Parametrs ", true, gridBehevoir),
						new Text("Description"), textArea), histogram),
				tableSensetivty, Tools.T("", true, gridRadar));
		rootPane.setCenter(vbox);
		rootPane.setBottom(Tools.hBox(addToThisSimulation, addToDATA));
	}

}
