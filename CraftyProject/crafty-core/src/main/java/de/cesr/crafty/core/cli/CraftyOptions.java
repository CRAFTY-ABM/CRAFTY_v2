package de.cesr.crafty.core.cli;

	/**
	 * Simple container for command-line overrides and runtime options.
	 *
	 * This class is populated by the CLI parser and used to pass user-provided arguments
	 * (such as the YAML config path, project directory, scenario name, and output path) into the
	 * initialization workflow before the full {@link Config} is loaded.
	 *
	 * It intentionally contains only basic getters/setters and no validation; validation and fallback
	 * logic handled by the CLI layer and/or {@link ConfigLoader}.
	 */
	
	/*
	 * @author Mohamed Byari
	 *
	 */

public class CraftyOptions {
	private String yamlConfigFilePath;
	private String project_path;
	private String scenario_Name;
	private String output_path;
	private String external_variables_path;
	private String output_foder_name;
	private String annual_waiting_flag_path;
	
	

	public String getAnnual_waiting_flag_path() {
		return annual_waiting_flag_path;
	}

	public void setAnnual_waiting_flag_path(String annual_waiting_flag_path) {
		this.annual_waiting_flag_path = annual_waiting_flag_path;
	}

	public String getConfigFilePath() {
		return yamlConfigFilePath;
	}

	public void setConfigFilePath(String configFilePath) {
		this.yamlConfigFilePath = configFilePath;
	}

	public String getProjectDirectoryPath() {
		return project_path;
	}

	public void setProjectDirectoryPath(String projectDirectoryPath) {
		this.project_path = projectDirectoryPath;
	}

	public String getScenario_Name() {
		return scenario_Name;
	}

	public void setScenario_Name(String scenario_Name) {
		this.scenario_Name = scenario_Name;
	}

	public String getOutputPath() {
		return output_path;
	}

	public void setOutputPath(String output_path) {
		this.output_path = output_path;
	}

	public String getExternal_variables_path() {
		return external_variables_path;
	}

	public void setExternal_variables_path(String external_variables_path) {
		this.external_variables_path = external_variables_path;
	}

	public String getOutput_foder_name() {
		return output_foder_name;
	}

	public void setOutput_foder_name(String output_foder_name) {
		this.output_foder_name = output_foder_name;
	}
	
	
}
