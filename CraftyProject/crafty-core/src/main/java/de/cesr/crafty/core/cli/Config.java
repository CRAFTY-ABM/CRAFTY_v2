package de.cesr.crafty.core.cli;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import de.cesr.crafty.core.main.MainHeadless;
import de.cesr.crafty.core.utils.non_java_code_controller.RScriptRunnerConfig;

/**
 * Holds all user-configurable settings for a CRAFTY run.
 *
 * This class is populated from YAML via {@link ConfigLoader} (SnakeYAML maps
 * YAML keys directly to these public fields). Defaults are defined inline, and
 * {@link #inialize()} performs basic post-load initialization and validation
 * (e.g., seed handling).
 *
 * Field groups (high-level): - Paths and input directories (project, baseline,
 * capitals, demands, behaviour, shocks, etc.) - Model switches and parameters
 * (regionalisation, competition, neighbourhood effects, mutation, etc.) -
 * Output configuration (folders, maps/plots, frequencies, tracking) - Logging
 * switches - Optional synchronisation flags used by UI / plotting pipelines
 *
 *
 * {@link #toString()} prints a readable configuration summary and includes
 * basic runtime context (whether CRAFTY is running from a JAR or from an
 * IDE/classes).
 */
/*
 * @author Mohamed Byari
 *
 */
public class Config {

	// Paths
	public String project_path = "";
	public String scenario = "";

	///
	public List<Integer> start_End_Year;

	public String metaData_directory = "";
	public String BASELINE_path = "";
	public String CAPITALS_directory = "";
	public String gisPath = "";
	public List<String> landControle_directories = null;
	public String Behevoir_Cells_directory = "";
	public String SHOCKS_Maps_directory = "";
	public String aft_production_directory = "";
	public String aft_behevoir_directory = "";
	public String service_demands_path = "";;
	public String service_utility_weight_path = "";
	public String services_taxes_subsidies_path = "";
	public String land_taxes_subsidies_path = "";
	public String capital_degradation_directory = "";
	public String waitingFlag_directories_path = "";
	public String AFT_capital_adjustments = "";

	// Regionalisation
	public boolean regionalization = false;
	// CRAFTY Mechanisms
	public boolean initial_demand_supply_equilibrium = true;
	public boolean remove_negative_marginal_utility = false;
	public boolean use_abandonment_threshold = true;
	public boolean mutate_on_competition_win = false;
	public boolean separate_production_competitiveness = false;
	public boolean use_price_only_competition = false;
	public double mutation_interval = 0.01;
	public double MostCompetitorAFTProbability = 0.8;
	public boolean averaged_residual_demand_per_cell = false;
	public boolean use_AFTs_categories_GiveIn = true;
	// Neighboring Effects
	public boolean use_neighbor_priority = true;
	public double neighbor_priority_probability = 0.95;
	public int neighbor_radius = 2;
	// Competitiveness Process
	public String seedID = "rank";
	public double participating_cells_percentage = 0.03;
	public int marginal_utility_calculations_per_tick = 1;
	public double land_abandonment_percentage = 0.03;
	public double takeOverUnmanageCells_percentage = 0.8;
	public boolean use_relative_marginal_utility = true;

	// Output Configurati
	public String output_folder_name = "";
	public String Output_path = "";
	public boolean generate_output_files = true;
	public boolean generate_charts_plots_PNG = false;
	public boolean generate_charts_plots_PDF = false;
	public boolean generate_map_output_files = true;
	public boolean generate_map_plots_tif = false;
	public boolean generate_map_PAs_forced = false;

	public int map_output_frequency = 10;
	public boolean track_changes = false;
	public boolean export_LOGGER = true;
	public boolean LOGGER_info = true;
	public boolean LOGGER_warn = true;
	public boolean LOGGER_trace = false;

	public boolean printRegionalModelRunnerMeasures = false;
	public boolean printAbstractModelRunnerMeasures = true;

	public static boolean chartSynchronisation = true;
	public static int chartSynchronisationGap = 5;
	public static boolean mapSynchronisation = true;
	public static int mapSynchronisationGap = 5;
	public Object map_output_years = null;
	public String comments = "";;

	public AtomicLong longSeedID = new AtomicLong(1);

	// cached configs
	public boolean consider_subsidies_taxes = true;

	// New section
	public MapPngConfig map_png = new MapPngConfig();
	
	public RScriptRunnerConfig r_script_runner = new RScriptRunnerConfig();

	@Override
	public String toString() {
		String jar = JarInfo.jarFileName(MainHeadless.class);
		if (!jar.isEmpty()) {
			jar = "CARFTY running from: " + jar;
		} else {
			jar = "CRAFTY running from: classes/IDE Not a JAR file";
		}

		return "\n # Configuration for CRAFTY Model \n\n" + jar + "\n\n" + str();
	}

	String str() {
		return "Config [" + "\n" + "|-> project_path=" + project_path + "\n" + "|-> scenario=" + scenario + "\n"
				+ "|-> start_End_Year=" + start_End_Year + "\n" + "|-> metaData_directory=" + metaData_directory + "\n"
				+ "|-> BASELINE_path=" + BASELINE_path + "\n" + "|-> CAPITALS_directory=" + CAPITALS_directory + "\n"
				+ "|-> landControle_directories=" + landControle_directories + "\n" + "|-> Behevoir_Cells_directory="
				+ Behevoir_Cells_directory + "\n" + "|-> aft_production_directory=" + aft_production_directory + "\n"
				+ "|-> aft_behevoir_directory=" + aft_behevoir_directory + "\n" + "|-> service_demands_path="
				+ service_demands_path + "\n" + "|-> service_utility_weight_path=" + service_utility_weight_path + "\n"
				+ "|-> services_taxes_subsidies_path=" + services_taxes_subsidies_path + "\n"
				+ "|-> land_taxes_subsidies_path=" + land_taxes_subsidies_path + "\n"
				+ "|-> capital_degradation_directory=" + capital_degradation_directory + "\n" + "|-> regionalization="
				+ regionalization + "\n" + "|-> initial_demand_supply_equilibrium=" + initial_demand_supply_equilibrium
				+ "\n" + "|-> remove_negative_marginal_utility=" + remove_negative_marginal_utility + "\n"
				+ "|-> use_abandonment_threshold=" + use_abandonment_threshold + "\n" + "|-> mutate_on_competition_win="
				+ mutate_on_competition_win + "\n" + "|-> mutation_interval=" + mutation_interval + "\n"
				+ "|-> MostCompetitorAFTProbability=" + MostCompetitorAFTProbability + "\n"
				+ "|-> averaged_residual_demand_per_cell=" + averaged_residual_demand_per_cell + "\n"
				+ "|-> use_AFTs_categories_GiveIn=" + use_AFTs_categories_GiveIn + "\n" + "|-> use_neighbor_priority="
				+ use_neighbor_priority + "\n" + "|-> neighbor_priority_probability=" + neighbor_priority_probability
				+ "\n" + "|-> neighbor_radius=" + neighbor_radius + "\n" + "|-> seedID=" + seedID + "\n"
				+ "|-> participating_cells_percentage=" + participating_cells_percentage + "\n"
				+ "|-> marginal_utility_calculations_per_tick=" + marginal_utility_calculations_per_tick + "\n"
				+ "|-> land_abandonment_percentage=" + land_abandonment_percentage + "\n"
				+ "|-> takeOverUnmanageCells_percentage=" + takeOverUnmanageCells_percentage + "\n"
				+ "|-> output_folder_name=" + output_folder_name + "\n" + "|-> Output_path=" + Output_path + "\n"
				+ "|-> generate_output_files=" + generate_output_files + "\n" + "|-> generate_charts_plots_PNG="
				+ generate_charts_plots_PNG + "\n" + "|-> generate_charts_plots_PDF=" + generate_charts_plots_PDF + "\n"
				+ "|-> generate_map_output_files=" + generate_map_output_files + "\n" + "|-> generate_map_plots_tif="
				+ generate_map_plots_tif + "\n" + "|-> map_output_frequency=" + map_output_frequency + "\n"
				+ "|-> track_changes=" + track_changes + "\n" + "|-> export_LOGGER=" + export_LOGGER + "\n"
				+ "|-> LOGGER_info=" + LOGGER_info + "\n" + "|-> LOGGER_warn=" + LOGGER_warn + "\n"
				+ "|-> LOGGER_trace=" + LOGGER_trace + "\n" + "|-> map_output_years=" + map_output_years + "\n"
				+ "|-> comments=" + comments + "\n" + "] \n\n";
	}

	// behevoir model
	public String categories_givingInDistribution = "";
	public double steepness_logistic_eq = 7;
	// institutions
	public String institutions_directory = "";
	public String external_variable_values_directory = "";
	public String LLM_model_name = "gpt-4.1-nano";
	public String LLM_API_KEY = "";
	public int start_year_of_policy_effect = 0;
	public int end_year_of_policy_effect = Integer.MAX_VALUE;
	public String LLM_provider = "";
	public boolean use_cell_level_taxes = false;

//	public int institution_time_lag = 1;
//	 Only when CRAFTY coupled with PLUM
	public boolean COUPLED_WITH_PLUM = false;
	public String plumCalibPath = "";
	public String plumOutPutPath = "";
	public String services_commodities_map = "";

}