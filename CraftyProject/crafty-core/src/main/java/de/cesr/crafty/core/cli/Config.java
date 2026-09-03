package de.cesr.crafty.core.cli;

import java.util.List;

import de.cesr.crafty.core.main.MainHeadless;
import de.cesr.crafty.core.utils.general.MapPngConfig;
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
 * (regionalisation, competition, neighbourhood effects, etc.) -
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

    //
    public List<Integer> simulation_year_range;

    public String metadata_directory = "";
    public String baseline_path = "";
    public String capitals_directory = "";
    public String gis_path = "";
    public List<String> land_control_directories = null;
    public String cell_behaviour_parameters_directory = "";
    public String shock_maps_directory = "";
    public String aft_production_parameters_directory = "";
    public String aft_behaviour_parameters_directory = "";
    public String service_demands_path = "";;
    public String service_utility_weights_path = "";
    public String capital_degradation_directory = "";
    public String waiting_flags_path = "";
    public String aft_capital_adjustments_directory = "";

    // Production costs
    public boolean use_production_costs = false;
    public boolean spatial_production_costs = false;
    public String costs_directory = "";

    // Price-explicit giving up
    public boolean use_price_explicit_giving_up = false;
    public String prescribed_subsidies_path = "";

    // Twinned AFTs
    public boolean use_twinned_afts = false;
    public boolean use_twinned_cost = false;
    public double twinned_competition_rate = 1.0;

    // Regionalisation
    public boolean regionalisation = false;
    // CRAFTY Mechanisms
    public boolean initial_demand_supply_equilibrium = true;
    public boolean remove_negative_marginal_utility = false;
    public boolean use_abandonment_threshold = true;
    public boolean separate_production_competitiveness = false;
    public boolean use_explicit_price_utility = false;
    public boolean use_price_only_utility = false;
    public boolean use_normalised_price_competition = false;
    public double most_competitive_aft_probability = 0.85;
    public boolean averaged_residual_demand_per_cell = false;
    public boolean use_category_based_give_in = true;
    public boolean use_cell_behaviour_model = true;
    // Neighbouring effects
    public boolean use_neighbour_priority = true;
    public double neighbour_priority_probability = 0.95;
    public int neighbour_radius = 2;
    // Competitiveness Process
    public String cell_selection = "rank";
    public long random_seed = 1L;
    public double participating_cell_fraction = 0.03;
    /**
     * Number of deterministic competition batches evaluated per simulation tick.
     * Regional supply and marginal utility are refreshed after every batch, allowing
     * later batches to respond to land-use changes made by earlier batches. Values
     * must be at least one; increasing this value changes decision feedback frequency,
     * not the number of participating cells.
     */
    public int marginal_utility_calculations_per_tick = 5;
    public double land_abandonment_fraction = 0.02;
    public double unmanaged_cell_takeover_fraction = 0.8;
    public boolean use_relative_marginal_utility = true;

    // Output Configurati
    public String output_folder_name = "";
    public String output_path = "";
    public boolean generate_output_files = true;
    /** Write annual adjacency- and patch-based AFT fragmentation metrics. */
    public boolean generate_land_fragmentation_output = false;
    public boolean generate_chart_plots_png = false;
    public boolean generate_map_output_files = true;
    public boolean generate_forced_mask_outputs = false;

    public int map_output_frequency = 10;
    public boolean track_changes = false;
    public boolean export_logger = true;
    public boolean logger_info = true;
    public boolean logger_warn = true;
    public boolean logger_trace = false;

    public boolean print_regional_model_runner_measures = false;
    public boolean print_abstract_model_runner_measures = true;

    public static boolean chart_synchronisation = true;
    public static int chart_synchronisation_gap = 1;
    public static boolean map_synchronisation = true;
    public static int map_synchronisation_gap = 1;
    public Object map_output_years = null;
    public String comments = "";;

    // New section
    public MapPngConfig map_png = new MapPngConfig();
    // cvs maps
    public List<Object> cell_output_columns;

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
                + "|-> simulation_year_range=" + simulation_year_range + "\n" + "|-> metadata_directory=" + metadata_directory + "\n"
                + "|-> baseline_path=" + baseline_path + "\n" + "|-> capitals_directory=" + capitals_directory + "\n"
                + "|-> land_control_directories=" + land_control_directories + "\n"
                + "|-> cell_behaviour_parameters_directory=" + cell_behaviour_parameters_directory + "\n"
                + "|-> aft_production_parameters_directory=" + aft_production_parameters_directory + "\n"
                + "|-> aft_behaviour_parameters_directory=" + aft_behaviour_parameters_directory + "\n"
                + "|-> service_demands_path="
                + service_demands_path + "\n" + "|-> service_utility_weights_path=" + service_utility_weights_path + "\n"
                + "|-> capital_degradation_directory=" + capital_degradation_directory + "\n" + "|-> regionalisation="
                + regionalisation + "\n" + "|-> initial_demand_supply_equilibrium=" + initial_demand_supply_equilibrium
                + "\n" + "|-> remove_negative_marginal_utility=" + remove_negative_marginal_utility + "\n"
                + "|-> use_abandonment_threshold=" + use_abandonment_threshold + "\n"
                + "|-> separate_production_competitiveness=" + separate_production_competitiveness + "\n"
                + "|-> use_explicit_price_utility=" + use_explicit_price_utility + "\n"
                + "|-> use_price_only_utility=" + use_price_only_utility + "\n"
                + "|-> use_normalised_price_competition=" + use_normalised_price_competition + "\n"
                + "|-> most_competitive_aft_probability=" + most_competitive_aft_probability + "\n"
                + "|-> averaged_residual_demand_per_cell=" + averaged_residual_demand_per_cell + "\n"
                + "|-> use_category_based_give_in=" + use_category_based_give_in + "\n"
                + "|-> use_cell_behaviour_model=" + use_cell_behaviour_model + "\n" + "|-> use_neighbour_priority="
                + use_neighbour_priority + "\n" + "|-> neighbour_priority_probability=" + neighbour_priority_probability
                + "\n" + "|-> neighbour_radius=" + neighbour_radius + "\n" + "|-> cell_selection=" + cell_selection
                + "\n" + "|-> random_seed=" + random_seed + "\n"
                + "|-> participating_cell_fraction=" + participating_cell_fraction + "\n"
                + "|-> marginal_utility_calculations_per_tick=" + marginal_utility_calculations_per_tick + "\n"
                + "|-> land_abandonment_fraction=" + land_abandonment_fraction + "\n"
                + "|-> unmanaged_cell_takeover_fraction=" + unmanaged_cell_takeover_fraction + "\n"
                + "|-> output_folder_name=" + output_folder_name + "\n" + "|-> output_path=" + output_path + "\n"
                + "|-> generate_output_files=" + generate_output_files + "\n"
                + "|-> generate_land_fragmentation_output=" + generate_land_fragmentation_output + "\n"
                + "|-> generate_chart_plots_png="
                + generate_chart_plots_png + "\n" + "|-> generate_map_output_files=" + generate_map_output_files + "\n"
                + "|-> map_output_frequency=" + map_output_frequency + "\n"
                + "|-> track_changes=" + track_changes + "\n" + "|-> export_logger=" + export_logger + "\n"
                + "|-> logger_info=" + logger_info + "\n" + "|-> logger_warn=" + logger_warn + "\n"
                + "|-> logger_trace=" + logger_trace + "\n" + "|-> map_output_years=" + map_output_years + "\n"
                + "|-> comments=" + comments + "\n"
                + "|-> use_production_costs=" + use_production_costs + "\n"
                + "|-> spatial_production_costs=" + spatial_production_costs + "\n"
                + "|-> costs_directory=" + costs_directory + "\n"
                + "|-> use_price_explicit_giving_up=" + use_price_explicit_giving_up + "\n"
                + "|-> prescribed_subsidies_path=" + prescribed_subsidies_path + "\n"
                + "|-> use_twinned_afts=" + use_twinned_afts + "\n"
                + "|-> use_twinned_cost=" + use_twinned_cost + "\n"
                + "|-> twinned_competition_rate=" + twinned_competition_rate + "\n"
                + "] \n\n";
    }

    // Behaviour model
    public String category_give_in_distributions_directory = "";
    public double cell_behaviour_logistic_steepness = 7;
    // institutions
    public String institutions_directory = "";
    public String external_variable_values_directory = "";
    public String llm_model_name = "gpt-4.1-nano";
    public String llm_api_key = "";
    public String llm_provider = "";
    public boolean use_cell_level_taxes = false;
    //	 Only when CRAFTY coupled with PLUM
    public boolean coupled_with_plum = false;
    public String plum_calibration_path = "";
    public String plum_output_path = "";
    public String service_commodity_mapping_path = "";

}