package de.cesr.crafty.gui.controller.fxml;

import javafx.fxml.FXML;

import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import de.cesr.crafty.core.cli.Config;
import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.utils.general.Utils;
import de.cesr.crafty.gui.utils.graphical.Tools;
import javafx.event.ActionEvent;
import javafx.scene.control.Slider;
import javafx.scene.control.CheckBox;

public class RunCofigController {
	@FXML
	private VBox TopBox;
	@FXML
	private CheckBox InitialEquilibrium;
	@FXML
	private CheckBox removeNegative;
	@FXML
	private CheckBox gUP;
	@FXML
	private CheckBox neighbours;
	@FXML
	private Slider NeighbourRadiusS;
	@FXML
	private TextField NeighbourRadiusT;
	@FXML
	private Slider cellsPersS;
	@FXML
	private TextField CellPersT;
	@FXML
	private CheckBox MapSync;
	@FXML
	private CheckBox chartSync;
	@FXML
	private CheckBox creatCSV;
	@FXML
	private CheckBox isAveragedPerCellResidualDemand;
	@FXML
	private Slider BestAftS;
	@FXML
	private TextField BestAftT;
	@FXML
	private Slider RandomAftS;
	@FXML
	private TextField RandomAftT;
//	@FXML
//	private CheckBox neighboursCollaboration;
	@FXML
	private Slider MapSync_GapS;
	@FXML
	private TextField MapSync_GapT;
	@FXML
	private Slider chartSync_GapS;
	@FXML
	private TextField chartSync_GapT;
	@FXML
	private TextField CSV_GapT;
	@FXML
	private Slider CSV_GapS;
	@FXML
	private Slider nbrOfSubSetS;
	@FXML
	private TextField nbrOfSubSetT;
	@FXML
	private Slider percentageOfGiveUpS;
	@FXML
	private TextField percentageOfGiveUpT;
	@FXML
	private CheckBox traker;
	@FXML
	private CheckBox logger;
	@FXML
	private CheckBox png;
	@FXML
	private CheckBox pdf;
	@FXML
	private CheckBox tif;
	@FXML
	private CheckBox info;
	@FXML
	private CheckBox warn;

	@FXML
	public void png(ActionEvent event) {
		System.out.println("png");
	}

	@FXML
	public void pdf(ActionEvent event) {
		System.out.println("pdf");
	}

	@FXML
	public void tif(ActionEvent event) {
		System.out.println("tif");
	}

	@FXML
	public void info(ActionEvent event) {
		System.out.println("info");
	}

	@FXML
	public void warn(ActionEvent event) {
		System.out.println("warn");
	}

	static public ModelRunnerController CA;

//	mutationInterval.setValue(CA.R.mutationIntval );
//	mutationInterval.valueProperty().addListener((_, _, _) -> {
//		CA.R.mutationIntval = mutationInterval.getValue();
//	});

	public void initialize() {
		System.out.println("initialize " + getClass().getSimpleName());
		InitialEquilibrium.setSelected(ConfigLoader.config.initial_demand_supply_equilibrium);

		removeNegative.setSelected(ConfigLoader.config.remove_negative_marginal_utility);
		MapSync.setSelected(Config.map_synchronisation);
		neighbours.setSelected(ConfigLoader.config.use_neighbour_priority);
		creatCSV.setSelected(ConfigLoader.config.generate_output_files);
		gUP.setSelected(ConfigLoader.config.use_abandonment_threshold);
		// neighboursCollaboration.setSelected(CA.R.NeighboorEffect);
		chartSync.setSelected(Config.chart_synchronisation);

		cellsPersS.setValue(ConfigLoader.config.participating_cell_fraction * 100);
		CellPersT.setText(Math.round(cellsPersS.getValue() * 10) / 10. + "");
		cellsPersS.valueProperty().addListener((_, _, _) -> {
			ConfigLoader.config.participating_cell_fraction = cellsPersS.getValue() / 100;
			CellPersT.setText(Math.round(cellsPersS.getValue() * 10) / 10. + ""); // ;
		});

		MapSync_GapS.setValue(Config.map_synchronisation_gap);
		MapSync_GapT.setText((int) MapSync_GapS.getValue() + "");
		MapSync_GapS.valueProperty().addListener((_, _, _) -> {
			Config.map_synchronisation_gap = (int) MapSync_GapS.getValue();
			MapSync_GapT.setText((int) MapSync_GapS.getValue() + "");
		});

		chartSync_GapS.setValue(Config.chart_synchronisation_gap);
		chartSync_GapT.setText((int) chartSync_GapS.getValue() + "");
		chartSync_GapS.valueProperty().addListener((_, _, _) -> {
			Config.chart_synchronisation_gap = (int) chartSync_GapS.getValue();
			chartSync_GapT.setText((int) chartSync_GapS.getValue() + "");
		});
		CSV_GapS.setValue(ConfigLoader.config.map_output_frequency);
		CSV_GapT.setText((int) CSV_GapS.getValue() + "");
		CSV_GapS.valueProperty().addListener((_, _, _) -> {
			ConfigLoader.config.map_output_frequency = (int) CSV_GapS.getValue();
			CSV_GapT.setText((int) CSV_GapS.getValue() + "");
		});
//		nbrOfSubSetS.setValue(ModelRunner.nbrOfSubSet);
//		nbrOfSubSetT.setText((int) nbrOfSubSetS.getValue() + "");
//		nbrOfSubSetS.valueProperty().addListener((_, _, _) -> {
//			ModelRunner.nbrOfSubSet = (int) nbrOfSubSetS.getValue();
//			nbrOfSubSetT.setText((int) nbrOfSubSetS.getValue() + "");
//		});
		NeighbourRadiusS.setValue(ConfigLoader.config.neighbour_radius);
		NeighbourRadiusT.setText((int) NeighbourRadiusS.getValue() + "");
		NeighbourRadiusS.valueProperty().addListener((_, _, _) -> {
			ConfigLoader.config.neighbour_radius = (int) NeighbourRadiusS.getValue();
			NeighbourRadiusT.setText((int) NeighbourRadiusS.getValue() + "");
		});

		percentageOfGiveUpS.setValue(ConfigLoader.config.land_abandonment_fraction * 100);
		percentageOfGiveUpT.setText(Math.round(percentageOfGiveUpS.getValue() * 10) / 10. + "");
		percentageOfGiveUpS.valueProperty().addListener((_, _, _) -> {
			ConfigLoader.config.land_abandonment_fraction = percentageOfGiveUpS.getValue() / 100;
			percentageOfGiveUpT.setText(Math.round(percentageOfGiveUpS.getValue() * 10) / 10. + ""); // ;
		});

		BestAftS.setValue(ConfigLoader.config.most_competitive_aft_probability * 100);
		BestAftT.setText(Math.round(BestAftS.getValue() * 10) / 10. + "");
		BestAftS.valueProperty().addListener((_, _, _) -> {
			ConfigLoader.config.most_competitive_aft_probability = BestAftS.getValue() / 100;
			BestAftT.setText(Math.round(BestAftS.getValue() * 10) / 10. + "");
			RandomAftT.setText(Math.round(1000 - BestAftS.getValue() * 10) / 10. + "");
			RandomAftS.setValue(Utils.sToD(RandomAftT.getText()));
		});

		RandomAftS.setValue(100 - ConfigLoader.config.most_competitive_aft_probability * 100);
		RandomAftT.setText(100 - Math.round(BestAftS.getValue() * 10) / 10. + "");
		RandomAftS.valueProperty().addListener((_, _, _) -> {
			ConfigLoader.config.most_competitive_aft_probability = 1 - RandomAftS.getValue() / 100;
			RandomAftT.setText(Math.round(RandomAftS.getValue() * 10) / 10. + ""); // ;
			BestAftT.setText(Math.round(1000 - RandomAftS.getValue() * 10) / 10. + "");
			BestAftS.setValue(Utils.sToD(BestAftT.getText()));
		});
		traker.setSelected(ConfigLoader.config.track_changes);
		logger.setSelected(ConfigLoader.config.export_logger);
		Tools.forceResisingWidth(TopBox);
	}

	@FXML
	public void initialEquilibrium(ActionEvent event) {
		ConfigLoader.config.initial_demand_supply_equilibrium = InitialEquilibrium.isSelected();
	}

	// Event Listener on CheckBox[#removeNegative].onAction
	@FXML
	public void removeNegativeMarginal(ActionEvent event) {
		ConfigLoader.config.remove_negative_marginal_utility = removeNegative.isSelected();
	}

	// Event Listener on CheckBox[#MapSync].onAction
	@FXML
	public void mapSyn(ActionEvent event) {
		Config.map_synchronisation = MapSync.isSelected();
	}

	// Event Listener on CheckBox[#gUP].onAction
	@FXML
	public void giveUpMechanisme(ActionEvent event) {
		ConfigLoader.config.use_abandonment_threshold = gUP.isSelected();
	}

	@FXML
	public void percentageOfGiveUpT(ActionEvent event) {
		ConfigLoader.config.land_abandonment_fraction = Utils.sToD(percentageOfGiveUpT.getText()) / 100;
		percentageOfGiveUpS.setValue((int) Utils.sToD(percentageOfGiveUpT.getText()));

	}

	@FXML
	public void NeighboursAction(ActionEvent event) {
		ConfigLoader.config.use_neighbour_priority = neighbours.isSelected();

		NeighbourRadiusS.setDisable(!neighbours.isSelected());
		NeighbourRadiusT.setDisable(!neighbours.isSelected());

	}

	@FXML
	public void averagedPerCellResidualDemand(ActionEvent event) {
		ConfigLoader.config.averaged_residual_demand_per_cell = isAveragedPerCellResidualDemand.isSelected();
	}

	@FXML
	public void NeighbourRadiusT(ActionEvent event) {
		ConfigLoader.config.neighbour_radius = (int) Utils.sToD(NeighbourRadiusT.getText());
		NeighbourRadiusS.setValue((int) Utils.sToD(NeighbourRadiusT.getText()));
	}

	// Event Listener on CheckBox.onAction
//	@FXML
//	public void NeighboursCollaboration(ActionEvent event) {
//		CA.R.NeighboorEffect = neighboursCollaboration.isSelected();
//	}
	@FXML
	public void BestAftT(ActionEvent event) {
		ConfigLoader.config.most_competitive_aft_probability = Utils.sToD(BestAftT.getText()) / 100;
		BestAftS.setValue(Utils.sToD(BestAftT.getText()));
		RandomAftS.setValue(100 - Utils.sToD(BestAftT.getText()));
	}

	@FXML
	public void RandomAftT(ActionEvent event) {
		ConfigLoader.config.most_competitive_aft_probability = 1 - Utils.sToD(RandomAftT.getText()) / 100;
		RandomAftS.setValue(Utils.sToD(RandomAftT.getText()));
		BestAftS.setValue(100 - Utils.sToD(RandomAftT.getText()));
	}

	// Event Listener on TextField[#CellPersT].onAction
	@FXML
	public void cellspersT(ActionEvent event) {
		ConfigLoader.config.participating_cell_fraction = Utils.sToD(CellPersT.getText()) / 100;
		cellsPersS.setValue((int) Utils.sToD(CellPersT.getText()));
	}

	@FXML
	public void nbrOfSubSetT(ActionEvent event) {
		ConfigLoader.config.marginal_utility_calculations_per_tick = (int) Utils.sToD(nbrOfSubSetT.getText()) / 100;
		nbrOfSubSetS.setValue((int) Utils.sToD(nbrOfSubSetT.getText()));
	}

	@FXML
	public void mapSync_GapAction(ActionEvent event) {
		MapSync_GapS.setValue((int) Utils.sToD(MapSync_GapT.getText()));
	}

	@FXML
	public void chartSyncAction(ActionEvent event) {
		chartSync_GapS.setValue((int) Utils.sToD(chartSync_GapT.getText()));
	}

	@FXML
	public void CSVAction(ActionEvent event) {
		CSV_GapS.setValue((int) Utils.sToD(CSV_GapT.getText()));
	}

	// Event Listener on CheckBox[#chartSync].onAction
	@FXML
	public void chartSyn(ActionEvent event) {
		Config.chart_synchronisation = chartSync.isSelected();
	}

	// Event Listener on CheckBox[#creatCSV].onAction
	@FXML
	public void creatCSV(ActionEvent event) {
		ConfigLoader.config.generate_output_files = creatCSV.isSelected();
		ConfigLoader.config.generate_map_output_files = creatCSV.isSelected();

	}

	@FXML
	public void trakerAction() {
		ConfigLoader.config.track_changes = traker.isSelected();
	}

	@FXML
	public void loggerAction() {
		ConfigLoader.config.export_logger = logger.isSelected();
	}
}
