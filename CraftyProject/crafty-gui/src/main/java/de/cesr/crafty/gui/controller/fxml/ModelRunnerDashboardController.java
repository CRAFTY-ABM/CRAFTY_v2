package de.cesr.crafty.gui.controller.fxml;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.gui.institutes.InstitutionViewModel;
import de.cesr.crafty.gui.institutes.Institutes_Set;
import de.cesr.crafty.gui.institutes.PolicyViewModel;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;

/**
 * Alternative model runner pane.
 *
 * The dashboard FXML keeps the same fx:id and action method names as
 * {@link ModelRunnerController}, so it can reuse the proven simulation controls
 * while presenting them in a cleaner layout.
 */
public class ModelRunnerDashboardController extends ModelRunnerController {
	@FXML
	private ChoiceBox<Integer> pauseEveryYears;
	@FXML
	private CheckBox institutionPauseEnabled;

	private static final DecimalFormat COST_FORMAT = new DecimalFormat("#,##0.##");

	private final List<InstituteSelection> instituteSelections = new ArrayList<>();
	private Alert activePolicyDashboard;
	private int lastPausedYear = Integer.MIN_VALUE;
	private volatile boolean institutionsEnabled = true;
	private volatile int pauseIntervalYears = 5;

	@Override
	public void initialize() {
		super.initialize();
		for (int interval = 5; interval <= 25; interval+=5) {
			pauseEveryYears.getItems().add(interval);
		}
		pauseEveryYears.setValue(5);
		institutionsEnabled = institutionPauseEnabled.isSelected();
		pauseIntervalYears = pauseEveryYears.getValue();
		pauseEveryYears.disableProperty().bind(institutionPauseEnabled.selectedProperty().not());
		institutionPauseEnabled.selectedProperty().addListener((_, _, enabled) -> institutionsEnabled = enabled);
		pauseEveryYears.valueProperty().addListener((_, _, interval) -> {
			if (interval != null) {
				pauseIntervalYears = interval;
			}
		});
	}

	@Override
	protected void onRunStarted() {
		lastPausedYear = Integer.MIN_VALUE;
	}

	@Override
	protected boolean shouldPauseBeforeFirstStep() {
		return institutionsEnabled && Timestep.getCurrentYear() == Timestep.getStartYear()
				&& Timestep.getCurrentYear() < Timestep.getEndtYear();
	}

	@Override
	protected void onSimulationReset() {
		lastPausedYear = Integer.MIN_VALUE;
		closePolicyDashboard();
	}

	@Override
	protected boolean shouldPauseAfterStep() {
		if (!institutionsEnabled) {
			return false;
		}

		int interval = pauseIntervalYears;
		int currentYear = Timestep.getCurrentYear();
		int yearsFromStart = currentYear - Timestep.getStartYear();

		return interval > 0 && currentYear < Timestep.getEndtYear() && yearsFromStart > 0
				&& yearsFromStart % interval == 0 && currentYear != lastPausedYear;
	}

	@Override
	protected boolean shouldRunInstitutions() {
		return institutionsEnabled;
	}

	@Override
	protected void showRunPauseDialog() {
		lastPausedYear = Timestep.getCurrentYear();

		ButtonType continueWithPolicyButton = new ButtonType("Apply policies and continue", ButtonBar.ButtonData.OK_DONE);
		ButtonType continueWithoutPolicyButton = new ButtonType("Continue without policy changes", ButtonBar.ButtonData.OTHER);
		ButtonType stopButton = new ButtonType("Cancel simulation", ButtonBar.ButtonData.CANCEL_CLOSE);

		Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.initModality(Modality.NONE);
		alert.setResizable(true);
		alert.setTitle("Policy dashboard");
		alert.setHeaderText("Policy dashboard — year " + Timestep.getCurrentYear());
		alert.getDialogPane().setContent(createPoliciesDashboardContent());
		alert.getDialogPane().getStyleClass().add("policies-dashboard-dialog");
		alert.getDialogPane().getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
		alert.getButtonTypes().setAll(continueWithPolicyButton, continueWithoutPolicyButton, stopButton);
		updatePolicyCostSummary();
		activePolicyDashboard = alert;

		ButtonType response = alert.showAndWait().orElse(stopButton);
		if (activePolicyDashboard != alert) {
			return;
		}
		activePolicyDashboard = null;
		if (response == continueWithPolicyButton) {
			applySelectedPolicies();
			printSelectedPolicies();
			writeDecisionNotes(true);
			scheduleNextStep(0);
		} else if (response == continueWithoutPolicyButton) {
			System.out.println("Policy dashboard year " + Timestep.getCurrentYear() + ": continue without policy.");
			writeDecisionNotes(false);
			scheduleNextStep(0);
		} else {
			stop();
		}
	}

	private void closePolicyDashboard() {
		if (activePolicyDashboard == null) {
			return;
		}
		Alert dashboard = activePolicyDashboard;
		activePolicyDashboard = null;
		instituteSelections.clear();
		dashboard.close();
	}

	private VBox createPoliciesDashboardContent() {
		instituteSelections.clear();

		Label intro = new Label("Select how each institution should adjust its policies until the next policy round.");
		intro.setWrapText(true);
		intro.getStyleClass().add("policies-dashboard-intro");

		FlowPane key = createPolicyKey();

		VBox instituteBoxes = new VBox(12);
		instituteBoxes.setFillWidth(true);
		Institutes_Set.getInstitutes().values()
				.forEach(institute -> instituteBoxes.getChildren().add(createInstitutePolicyBox(institute)));

		if (Institutes_Set.getInstitutes().isEmpty()) {
			Label empty = new Label("No institutions were loaded from institutes/institutions.yaml.");
			empty.setWrapText(true);
			empty.getStyleClass().add("policies-dashboard-note");
			instituteBoxes.getChildren().add(empty);
		}

		ScrollPane institutesScroll = new ScrollPane(instituteBoxes);
		institutesScroll.setFitToWidth(true);
		institutesScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		institutesScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
		institutesScroll.setPrefViewportHeight(430);
		institutesScroll.setMaxHeight(460);
		institutesScroll.getStyleClass().add("policies-dashboard-scroll");

		Label note = new Label(
				"Note: selected policies will be applied every year until the next round of policy implementation.");
		note.setWrapText(true);
		note.getStyleClass().add("policies-dashboard-note");

		VBox content = new VBox(14, intro, key, institutesScroll, note);
		content.setPadding(new Insets(16, 20, 14, 20));
		content.setPrefWidth(1000);
		content.setMinWidth(760);
		return content;
	}

	private VBox createInstitutePolicyBox(InstitutionViewModel institute) {
		Label instituteName = new Label(institute.getName());
		instituteName.getStyleClass().add("policies-dashboard-institute-title");

		Label budgetLabel = new Label("Budget:");
		budgetLabel.getStyleClass().add("policies-dashboard-budget");
		HBox budgetControls = createBudgetControls(institute);

		Label totalCost = new Label();
		totalCost.getStyleClass().add("policies-dashboard-total");

		HBox header = new HBox(14, instituteName, budgetLabel, budgetControls, totalCost);
		header.setAlignment(Pos.CENTER_LEFT);

		GridPane table = new GridPane();
		table.setHgap(12);
		table.setVgap(10);
		table.getStyleClass().add("policies-dashboard-table");

		ColumnConstraints policyColumn = new ColumnConstraints();
		policyColumn.setMinWidth(240);
		policyColumn.setHgrow(Priority.ALWAYS);
		ColumnConstraints actionColumn = new ColumnConstraints();
		actionColumn.setMinWidth(360);
		actionColumn.setPrefWidth(380);
		actionColumn.setHgrow(Priority.ALWAYS);
		ColumnConstraints costColumn = new ColumnConstraints();
		costColumn.setMinWidth(90);
		table.getColumnConstraints().addAll(policyColumn, actionColumn, costColumn);

		addTableHeader(table);

		TextArea decisionReason = new TextArea();
		decisionReason.setPromptText("Explain why this institution selected these policy actions...");
		decisionReason.setWrapText(true);
		decisionReason.setPrefRowCount(2);
		decisionReason.getStyleClass().add("policy-reason-text");

		Label reasonLabel = new Label("Decision rationale");
		reasonLabel.getStyleClass().add("policy-reason-label");
		VBox reasonBox = new VBox(5, reasonLabel, decisionReason);
		reasonBox.getStyleClass().add("policy-reason-box");

		InstituteSelection instituteSelection = new InstituteSelection(institute, totalCost, decisionReason);
		instituteSelections.add(instituteSelection);
		List<PolicyViewModel> policies = new ArrayList<>(institute.getPolicies().values());
		for (int i = 0; i < policies.size(); i++) {
			int row = (i * 2) + 1;
			addPolicyRow(table, row, policies.get(i), instituteSelection);
			if (i < policies.size() - 1) {
				addPolicySeparator(table, row + 1);
			}
		}

		if (policies.isEmpty()) {
			Label empty = new Label("No policies are configured for this institution.");
			empty.setWrapText(true);
			GridPane.setColumnSpan(empty, 3);
			table.add(empty, 0, 1);
		}

		VBox box = new VBox(10, header, table, reasonBox);
		box.getStyleClass().add("policies-dashboard-institute");
		return box;
	}

	private HBox createBudgetControls(InstitutionViewModel institute) {
		double startingBudget = institute.getBudget();
		ChoiceBox<Double> budgetChoice = new ChoiceBox<>();
		budgetChoice.getItems().addAll(startingBudget / 2, startingBudget, startingBudget * 2,
				startingBudget * 3);
		budgetChoice.setValue(startingBudget);
		budgetChoice.setDisable(true);
		budgetChoice.setPrefWidth(110);
		budgetChoice.setConverter(new javafx.util.StringConverter<>() {
			@Override
			public String toString(Double value) {
				return value == null ? "" : formatCost(value);
			}

			@Override
			public Double fromString(String value) {
				return null;
			}
		});
		budgetChoice.getStyleClass().add("policies-dashboard-budget-choice");

		CheckBox allowBudgetChange = new CheckBox("Allow budget change");
		allowBudgetChange.selectedProperty().addListener((_, _, allowed) -> {
			budgetChoice.setDisable(!allowed);
			if (!allowed) {
				budgetChoice.setValue(startingBudget);
				institute.setBudget(startingBudget);
				updatePolicyCostSummary();
			}
		});
		budgetChoice.valueProperty().addListener((_, _, value) -> {
			if (allowBudgetChange.isSelected() && value != null) {
				institute.setBudget(value);
				updatePolicyCostSummary();
			}
		});

		HBox controls = new HBox(8, allowBudgetChange, budgetChoice);
		controls.setAlignment(Pos.CENTER_LEFT);
		return controls;
	}

	private FlowPane createPolicyKey() {
		FlowPane key = new FlowPane(10, 6);
		key.setAlignment(Pos.CENTER_LEFT);
		key.getStyleClass().add("policies-dashboard-key");

		Label title = new Label("Symbols:");
		title.getStyleClass().add("policies-dashboard-key-title");
		key.getChildren().add(title);

		for (PolicyAction action : PolicyAction.values()) {
			Label item = new Label(action.label + " " + action.description);
			item.getStyleClass().add("policies-dashboard-key-item");
			key.getChildren().add(item);
		}
		return key;
	}

	private void addTableHeader(GridPane table) {
		Label policy = new Label("Policy");
		Label action = new Label("Intervention");
		Label cost = new Label("Annual cost");
		policy.getStyleClass().add("policies-dashboard-header");
		action.getStyleClass().add("policies-dashboard-header");
		cost.getStyleClass().add("policies-dashboard-header");
		table.add(policy, 0, 0);
		table.add(action, 1, 0);
		table.add(cost, 2, 0);
	}

	private void addPolicySeparator(GridPane table, int row) {
		Separator separator = new Separator();
		separator.getStyleClass().add("policies-dashboard-row-separator");
		separator.setMaxWidth(Double.MAX_VALUE);
		GridPane.setColumnSpan(separator, 3);
		table.add(separator, 0, row);
	}

	private void addPolicyRow(GridPane table, int row, PolicyViewModel policy,
			InstituteSelection instituteSelection) {
		Label policyName = new Label(policy.getName());
		policyName.setWrapText(true);
		policyName.getStyleClass().add("policies-dashboard-policy-name");

		ToggleGroup group = new ToggleGroup();
		HBox choices = new HBox(4);
		choices.setAlignment(Pos.CENTER_LEFT);

		PolicySelection selection = new PolicySelection(policy, group);
		instituteSelection.policies.add(selection);
		ToggleButton maintainButton = null;

		for (PolicyAction action : PolicyAction.values()) {
			ToggleButton button = new ToggleButton(action.label);
			button.setUserData(action);
			button.setToggleGroup(group);
			// Never abbreviate the intervention symbols (especially --- and +++).
			button.setMinWidth(Region.USE_PREF_SIZE);
			button.setPrefWidth(Region.USE_COMPUTED_SIZE);
			button.setMaxWidth(Region.USE_PREF_SIZE);
			button.getStyleClass().add("policy-choice-button");
			if (action == PolicyAction.MAINTAIN) {
				button.setSelected(true);
				maintainButton = button;
			}
			button.setOnAction(_ -> updatePolicyCostSummary());
			choices.getChildren().add(button);
		}
		ToggleButton defaultButton = maintainButton;
		group.selectedToggleProperty().addListener((_, _, selected) -> {
			if (selected == null && defaultButton != null) {
				defaultButton.setSelected(true);
			}
			updatePolicyCostSummary();
		});

		Label cost = new Label("0");
		cost.getStyleClass().add("policy-row-cost");
		selection.costLabel = cost;

		table.add(policyName, 0, row);
		table.add(choices, 1, row);
		table.add(cost, 2, row);
	}

	private void updatePolicyCostSummary() {
		for (InstituteSelection instituteSelection : instituteSelections) {
			double totalCost = instituteSelection.getCost();
			instituteSelection.totalCostLabel.setText(
					"Selected cost: " + formatCost(totalCost) + " / " + formatCost(instituteSelection.budget()));
			instituteSelection.totalCostLabel.getStyleClass().removeAll("policies-dashboard-over-budget");
			if (!instituteSelection.isWithinBudget()) {
				instituteSelection.totalCostLabel.getStyleClass().add("policies-dashboard-over-budget");
			}
		}
	}

	private void applySelectedPolicies() {
		for (InstituteSelection instituteSelection : instituteSelections) {
			for (PolicySelection selection : instituteSelection.policies) {
				selection.policy.setValue(selection.policy.getValue() + selection.getAction().level);
			}
		}
	}

	private void printSelectedPolicies() {
		System.out.println("Policy dashboard year " + Timestep.getCurrentYear() + ":");
		for (InstituteSelection instituteSelection : instituteSelections) {
			System.out.println("Institute " + instituteSelection.institute.getName() + " (budget "
					+ formatCost(instituteSelection.budget()) + ", selected cost "
					+ formatCost(instituteSelection.getCost()) + "):");
			for (PolicySelection selection : instituteSelection.policies) {
				PolicyAction action = selection.getAction();
				System.out.println(" - " + selection.policy.getName() + " = " + action.description + " (cost "
						+ formatCost(selection.getCost()) + ")");
			}
		}
	}

	private void writeDecisionNotes(boolean policiesApplied) {
		String outputRoot = ConfigLoader.config.output_folder_name;
		if (!ConfigLoader.config.generate_output_files || outputRoot == null || outputRoot.isBlank()) {
			return;
		}

		int year = Timestep.getCurrentYear();
		String institutesDirectory = PathTools.makeDirectory(outputRoot + File.separator + "institutes");
		if (institutesDirectory == null) {
			return;
		}

		for (InstituteSelection selection : instituteSelections) {
			String reason = selection.decisionReason.getText().strip();
			if (reason.isEmpty()) {
				reason = "(No reason provided)";
			}

			StringBuilder note = new StringBuilder();
			note.append("============================================================").append(System.lineSeparator());
			note.append("Institution: ").append(selection.institute.getName()).append(System.lineSeparator());
			note.append("Year: ").append(year).append(System.lineSeparator());
			note.append("Decision: ").append(policiesApplied ? "Policies applied" : "Continued without policy changes")
					.append(System.lineSeparator());
			double consumedBudget = policiesApplied ? selection.getCost() : 0;
			note.append("Budget: ").append(formatCost(selection.budget())).append(System.lineSeparator());
			note.append("Budget consumed: ").append(formatCost(consumedBudget)).append(System.lineSeparator());
			note.append("Budget exceeded: ").append(consumedBudget > selection.budget() ? "Yes" : "No")
					.append(System.lineSeparator());
			note.append(System.lineSeparator()).append("Reason:").append(System.lineSeparator());
			note.append(reason).append(System.lineSeparator());
			note.append(System.lineSeparator())
					.append(policiesApplied ? "Policy actions:" : "Policy actions shown (not applied):")
					.append(System.lineSeparator());
			for (PolicySelection policySelection : selection.policies) {
				PolicyAction action = policySelection.getAction();
				note.append("- ").append(policySelection.policy.getName()).append(": ").append(action.label)
						.append(" (").append(action.description).append(")").append(System.lineSeparator());
			}
			note.append(System.lineSeparator());

			PathTools.writeFile(
					institutesDirectory + File.separator + safePathPart(selection.institute.getName()) + ".txt",
					note.toString(), true);
		}
	}

	private String safePathPart(String value) {
		return value == null ? "unnamed_institute" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	private String formatCost(double value) {
		return COST_FORMAT.format(value);
	}

	private static final class InstituteSelection {
		private final InstitutionViewModel institute;
		private final Label totalCostLabel;
		private final TextArea decisionReason;
		private final List<PolicySelection> policies = new ArrayList<>();

		private InstituteSelection(InstitutionViewModel institute, Label totalCostLabel, TextArea decisionReason) {
			this.institute = institute;
			this.totalCostLabel = totalCostLabel;
			this.decisionReason = decisionReason;
		}

		private double budget() {
			return institute.getBudget();
		}

		private double getCost() {
			double totalCost = 0;
			for (PolicySelection selection : policies) {
				double cost = selection.getCost();
				totalCost += cost;
				if (selection.costLabel != null) {
					selection.costLabel.setText(COST_FORMAT.format(cost));
				}
			}
			return totalCost;
		}

		private boolean isWithinBudget() {
			return getCost() <= budget();
		}
	}

	private static final class PolicySelection {
		private final PolicyViewModel policy;
		private final ToggleGroup group;
		private Label costLabel;

		private PolicySelection(PolicyViewModel policy, ToggleGroup group) {
			this.policy = policy;
			this.group = group;
		}

		private PolicyAction getAction() {
			if (group.getSelectedToggle() == null) {
				return PolicyAction.MAINTAIN;
			}
			return (PolicyAction) group.getSelectedToggle().getUserData();
		}

		private double getCost() {
			if (getAction().level == -3) {
				return -policy.getCost();
			} else if (getAction().level == -1 || getAction().level == -2) {
				return 0;
			}
			return getAction().level * policy.getCost();
		}
	}

	private enum PolicyAction {
		VERY_STRONG_DECREASE("---", "very strong decrease", -3), STRONG_DECREASE("--", "strong decrease", -2),
		DECREASE("-", "decrease", -1), MAINTAIN("0", "maintain", 0), INCREASE("+", "increase", 1),
		STRONG_INCREASE("++", "strong increase", 2), VERY_STRONG_INCREASE("+++", "very strong increase", 3);

		private final String label;
		private final String description;
		private final int level;

		PolicyAction(String label, String description, int level) {
			this.label = label;
			this.description = description;
			this.level = level;
		}
	}
}
