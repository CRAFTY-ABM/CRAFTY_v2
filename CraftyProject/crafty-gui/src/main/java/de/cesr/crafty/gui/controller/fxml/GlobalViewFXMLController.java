package de.cesr.crafty.gui.controller.fxml;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.gui.utils.graphical.Tools;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class GlobalViewFXMLController {
	private static final Set<String> AFT_EXCLUDED_COLUMNS = Set.of(
			"Color",
			"Category_Color",
			"Intesity_name",
			"Intesity_level");

	@FXML
	private VBox TopBox;
	@FXML
	private ScrollPane scroll;
	@FXML
	private VBox contentBox;

	public void initialize() {
		Tools.forceResisingWidth(TopBox);
		Tools.forceResisingHeight(1, scroll);
		refreshTables();
	}

	@FXML
	public void refreshTables() {
		contentBox.getChildren().clear();
		addMetadataSection("AFTs", ProjectLoader.getAftMetaData(), AFT_EXCLUDED_COLUMNS);
		addMetadataSection("Capitals", ProjectLoader.getCapitalsMetadata(), Set.of());
		addMetadataSection("Services", ProjectLoader.getServiceMetadata(), Set.of());
		addMetadataSection("Scenarios", ProjectLoader.getScenarioMetaData(), Set.of());
	}

	private void addMetadataSection(String title, Path metadataFile, Set<String> excludedColumns) {
		Label sectionTitle = new Label(title);
		sectionTitle.getStyleClass().add("institution-section-title");

		VBox card = new VBox(7, sectionTitle);
		card.setFillWidth(true);
		card.setMaxWidth(Double.MAX_VALUE);
		card.getStyleClass().add("institution-card");

		List<List<String>> rows = metadataFile == null ? List.of() : CsvTools.readCsvFile(metadataFile);
		if (rows == null || rows.isEmpty()) {
			card.getChildren().add(createEmptyLabel("No " + title.toLowerCase() + " metadata available."));
		} else {
			card.getChildren().add(createTable(rows, excludedColumns));
		}
		contentBox.getChildren().add(card);
	}

	private GridPane createTable(List<List<String>> rows, Set<String> excludedColumns) {
		GridPane table = new GridPane();
		table.getStyleClass().add("institution-config-table");
		table.setHgap(0);
		table.setVgap(0);
		table.setMaxWidth(Double.MAX_VALUE);

		int sourceColumnCount = rows.stream().mapToInt(List::size).max().orElse(0);
		List<Integer> visibleColumns = new ArrayList<>();
		List<String> headers = rows.get(0);
		for (int column = 0; column < sourceColumnCount; column++) {
			String header = column < headers.size() ? headers.get(column) : "";
			boolean excluded = excludedColumns.stream()
					.anyMatch(excludedHeader -> excludedHeader.equalsIgnoreCase(header.trim()));
			if (!excluded) {
				visibleColumns.add(column);
			}
		}

		int columnCount = visibleColumns.size();
		double columnWidth = columnCount == 0 ? 100 : 100.0 / columnCount;
		for (int column = 0; column < columnCount; column++) {
			ColumnConstraints constraints = new ColumnConstraints();
			constraints.setMinWidth(100);
			constraints.setPercentWidth(columnWidth);
			constraints.setHgrow(Priority.ALWAYS);
			constraints.setFillWidth(true);
			table.getColumnConstraints().add(constraints);
		}

		for (int row = 0; row < rows.size(); row++) {
			List<String> values = rows.get(row);
			for (int column = 0; column < visibleColumns.size(); column++) {
				int sourceColumn = visibleColumns.get(column);
				String value = sourceColumn < values.size() ? values.get(sourceColumn) : "";
				table.add(createCell(value, row == 0, column == 0 && row > 0), column, row);
			}
		}
		return table;
	}

	private Label createCell(String value, boolean header, boolean displayName) {
		String rawValue = value == null ? "" : value;
		Label label = new Label(displayName ? toDisplayName(rawValue) : rawValue);
		label.setWrapText(true);
		label.setMaxWidth(Double.MAX_VALUE);
		label.setMaxHeight(Double.MAX_VALUE);
		label.setMinHeight(34);
		label.setAlignment(Pos.CENTER_LEFT);
		label.getStyleClass().add(header ? "institution-table-header" : "institution-table-cell");
		GridPane.setVgrow(label, Priority.ALWAYS);
		if (displayName && !rawValue.isBlank()) {
			label.setTooltip(new Tooltip(rawValue));
		}
		return label;
	}

	private Label createEmptyLabel(String text) {
		Label label = new Label(text);
		label.setWrapText(true);
		label.setPadding(new Insets(4, 0, 4, 0));
		label.getStyleClass().add("institution-empty-note");
		return label;
	}

	private String toDisplayName(String value) {
		String displayName = value.replace('_', ' ').trim();
		if (displayName.isEmpty()) {
			return displayName;
		}
		return Character.toUpperCase(displayName.charAt(0)) + displayName.substring(1);
	}
}
