package de.cesr.crafty.gui.controller.fxml;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.stream.Collectors;

import de.cesr.crafty.gui.institutes.InstitutionViewModel;
import de.cesr.crafty.gui.institutes.Institutes_Set;
import de.cesr.crafty.gui.institutes.PolicyViewModel;
import de.cesr.crafty.gui.institutes.TargetViewModel;
import de.cesr.crafty.gui.utils.graphical.MousePressed;
import de.cesr.crafty.gui.utils.graphical.Tools;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class InstitutionDashboardController {

	private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,##0.###");

	@FXML
	private VBox TopBox;
	@FXML
	private Label yearLabel;
	@FXML
	private ScrollPane scroll;
	@FXML
	private VBox contentBox;

	private static InstitutionDashboardController instance;

	public InstitutionDashboardController() {
		instance = this;
	}

	public static void refreshAll() {
		if (instance != null) {
			instance.refreshDashboard();
		}
	}

	public void initialize() {
		Tools.forceResisingWidth(TopBox);
		Tools.forceResisingHeight(1, scroll);
		refreshDashboard();
	}

	@FXML
	public void refreshDashboard() {
		contentBox.getChildren().clear();

		if (Institutes_Set.getInstitutes().isEmpty()) {
			yearLabel.setText("YAML configuration");
			contentBox.getChildren().add(createEmptyLabel(
					"No institutions were loaded from institutes/targets.yaml and institutions.yaml."));
			return;
		}

		int instituteCount = Institutes_Set.getInstitutes().size();
		yearLabel.setText(instituteCount + (instituteCount == 1 ? " institution" : " institutions") + " from YAML");
		Institutes_Set.getInstitutes().values().forEach(institute -> {
			VBox card = createInstitutionCard(institute);
			contentBox.getChildren().add(card);
			MousePressed.mouseControle(contentBox, card, institute.getName());
		});
	}

	private VBox createInstitutionCard(InstitutionViewModel institute) {
		Label name = createNamedLabel(institute.getName());
		name.getStyleClass().add("institution-card-title");

		int policyCount = institute.getPolicies().size();
		int targetCount = institute.getTargets().size();
		Label summary = new Label(policyCount + (policyCount == 1 ? " policy" : " policies") + "  |  "
				+ targetCount + (targetCount == 1 ? " target" : " targets"));
		summary.getStyleClass().add("institution-card-summary");

		Label budget = new Label("Initial budget: " + formatNumber(institute.getBudget()));
		budget.getStyleClass().add("institution-card-budget");

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox header = new HBox(12, name, summary, spacer, budget);
		header.setAlignment(Pos.CENTER_LEFT);
		header.getStyleClass().add("institution-card-header");

		VBox card = new VBox(12, header);
		if (!institute.getDescription().isBlank()) {
			card.getChildren().add(createDescriptionSection(institute.getDescription()));
		}
		card.getChildren().addAll(createPoliciesSection(institute), createTargetsSection(institute));
		card.setFillWidth(true);
		card.setMaxWidth(Double.MAX_VALUE);
		card.getStyleClass().add("institution-card");
		return card;
	}

	private VBox createDescriptionSection(String description) {
		Label descriptionLabel = new Label(description);
		descriptionLabel.setWrapText(true);
		descriptionLabel.setMaxWidth(Double.MAX_VALUE);
		descriptionLabel.getStyleClass().add("institution-card-description");

		Label sectionTitle = new Label("Description");
		sectionTitle.getStyleClass().add("institution-section-title");

		return new VBox(7, sectionTitle, descriptionLabel);
	}

	private VBox createPoliciesSection(InstitutionViewModel institute) {
		GridPane table = createTable(30, 15, 55);
		addHeader(table, 0, "Policy", "Effect", "CRAFTY elements and weights");

		int row = 1;
		if (institute.getPolicies().isEmpty()) {
			addRow(table, row, "No policies configured", "", "");
		} else {
			for (PolicyViewModel policy : institute.getPolicies().values()) {
				if (policy.getCraftyElem().isEmpty()) {
					addNamedRow(table, row++, policy.getName(), "", "");
					continue;
				}

				for (Map.Entry<String, Map<String, Double>> effect : policy.getCraftyElem().entrySet()) {
					addNamedRow(table, row++, policy.getName(), effect.getKey(),
							formatWeightedElements(effect.getValue()));
				}
			}
		}

		return createSection("Policies", table);
	}

	private VBox createTargetsSection(InstitutionViewModel institute) {
		GridPane table = createTable(30, 15, 55);
		addHeader(table, 0, "Observed target", "Type", "CRAFTY elements and weights");

		int row = 1;
		if (institute.getTargets().isEmpty()) {
			addRow(table, row, "No targets configured", "", "");
		} else {
			for (TargetViewModel target : institute.getTargets().values()) {
				addNamedRow(table, row++, target.getName(), target.getType(),
						formatWeightedElements(target.getCraftyElem()));
			}
		}

		return createSection("Observed targets", table);
	}

	private VBox createSection(String title, GridPane table) {
		Label sectionTitle = new Label(title);
		sectionTitle.getStyleClass().add("institution-section-title");

		VBox section = new VBox(7, sectionTitle, table);
		section.setPadding(new Insets(0, 0, 4, 0));
		section.setFillWidth(true);
		return section;
	}

	private GridPane createTable(double... percentages) {
		GridPane table = new GridPane();
		table.getStyleClass().add("institution-config-table");
		table.setHgap(0);
		table.setVgap(0);
		table.setMaxWidth(Double.MAX_VALUE);

		for (double percentage : percentages) {
			ColumnConstraints column = new ColumnConstraints();
			column.setMinWidth(70);
			column.setPercentWidth(percentage);
			column.setHgrow(Priority.ALWAYS);
			column.setFillWidth(true);
			table.getColumnConstraints().add(column);
		}
		return table;
	}

	private void addHeader(GridPane table, int row, String... values) {
		for (int column = 0; column < values.length; column++) {
			Label label = createCell(values[column], true);
			table.add(label, column, row);
		}
	}

	private void addRow(GridPane table, int row, String... values) {
		for (int column = 0; column < values.length; column++) {
			Label label = createCell(values[column], false);
			table.add(label, column, row);
		}
	}

	private void addNamedRow(GridPane table, int row, String name, String... values) {
		Label nameCell = createNamedLabel(name);
		nameCell.setMaxWidth(Double.MAX_VALUE);
		nameCell.setMaxHeight(Double.MAX_VALUE);
		nameCell.setMinHeight(34);
		nameCell.setAlignment(Pos.CENTER_LEFT);
		nameCell.getStyleClass().add("institution-table-cell");
		GridPane.setVgrow(nameCell, Priority.ALWAYS);
		table.add(nameCell, 0, row);

		for (int column = 0; column < values.length; column++) {
			table.add(createCell(values[column], false), column + 1, row);
		}
	}

	private Label createNamedLabel(String value) {
		String rawValue = value == null ? "" : value;
		Label label = new Label(toDisplayName(rawValue));
		label.setWrapText(true);
		if (!rawValue.isBlank()) {
			label.setTooltip(new Tooltip(rawValue));
		}
		return label;
	}

	private String toDisplayName(String value) {
		String displayName = value.replace('_', ' ').trim();
		if (displayName.isEmpty()) {
			return displayName;
		}
		return Character.toUpperCase(displayName.charAt(0)) + displayName.substring(1);
	}

	private Label createCell(String text, boolean header) {
		Label label = new Label(text == null ? "" : text);
		label.setWrapText(true);
		label.setMaxWidth(Double.MAX_VALUE);
		label.setMaxHeight(Double.MAX_VALUE);
		label.setMinHeight(34);
		label.setAlignment(Pos.CENTER_LEFT);
		label.getStyleClass().add(header ? "institution-table-header" : "institution-table-cell");
		GridPane.setVgrow(label, Priority.ALWAYS);
		return label;
	}

	private Label createEmptyLabel(String text) {
		Label label = new Label(text);
		label.setWrapText(true);
		label.getStyleClass().add("institution-empty-note");
		return label;
	}

	private String formatWeightedElements(Map<String, Double> values) {
		if (values == null || values.isEmpty()) {
			return "";
		}
		return values.entrySet().stream()
				.map(entry -> entry.getKey() + ": " + formatNumber(entry.getValue()))
				.collect(Collectors.joining(", "));
	}

	private String formatNumber(double value) {
		return NUMBER_FORMAT.format(value);
	}
}
