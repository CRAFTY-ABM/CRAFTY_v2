package de.cesr.crafty.gui.controller.fxml;

import java.util.HashSet;
import java.util.Set;

import de.cesr.crafty.gui.utils.graphical.CSVTableView;
import de.cesr.crafty.gui.utils.graphical.Tools;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.utils.file.CsvTools;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;

public class GlobalViewFXMLController {
	@FXML
	private VBox TopBox;
	@FXML
	private TableView<ObservableList<String>> TablCapitals;
	@FXML
	private TableView<ObservableList<String>> TablServices;
	@FXML
	private TableView<ObservableList<String>> TabScenarios;
	@FXML
	private TableView<ObservableList<String>> TablAFTs;

	public void initialize() {
		initilaseTabls();
		Tools.forceResisingWidth(TopBox);
	}

	void initilaseTabls() {
		System.out.println("initialize " + getClass().getSimpleName());
		CSVTableView.updateTableView(CsvTools.csvReader(ProjectLoader.getAftMetaData()), null, TablAFTs);
		CSVTableView.updateTableView(CsvTools.csvReader(ProjectLoader.getCapitalsMetadata()), null, TablCapitals);
		CSVTableView.updateTableView(CsvTools.csvReader(ProjectLoader.getServiceMetadata()), null, TablServices);
//		CSVTableView.updateTableView(CsvTools.csvReader(PathTools.fileFilter(File.separator+"scenarios.csv").get(0)), null,
//				TabScenarios);
		CSVTableView.createTableFromRows(TabScenarios, CsvTools.readCsvFile(ProjectLoader.getScenarioMetaData()));
		CSVTableView.createTableFromRows(TablAFTs, CsvTools.readCsvFile(ProjectLoader.getAftMetaData()));
		CSVTableView.createTableFromRows(TablServices, CsvTools.readCsvFile(ProjectLoader.getServiceMetadata()));
		CSVTableView.createTableFromRows(TablCapitals, CsvTools.readCsvFile(ProjectLoader.getCapitalsMetadata()));

		Set<TitledPane> panes = new HashSet<>();

		TopBox.getChildren().forEach(node -> {
			if (node instanceof TitledPane) {
				panes.add(((TitledPane) node));
			}
		});
	}
}
