package de.cesr.crafty.institution.decision.llm;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.file.PathTools;

public final class CsvLlmOutputWriter implements LlmOutputWriter {
	private final Map<String, List<Double>> decisions = new LinkedHashMap<>();
	private final Map<String, List<Double>> effective = new LinkedHashMap<>();

	@Override
	public void write(LlmOutputSnapshot snapshot) {
		decisions.computeIfAbsent("Year", key -> new ArrayList<>()).add((double) snapshot.year());
		effective.computeIfAbsent("Year", key -> new ArrayList<>()).add((double) snapshot.year());
		snapshot.decisions().forEach((name, value) -> decisions.computeIfAbsent(name, key -> new ArrayList<>()).add(value));
		snapshot.effectiveValues().forEach(
				(name, value) -> effective.computeIfAbsent(name, key -> new ArrayList<>()).add(value));
		String dir = PathTools.makeDirectory(ConfigLoader.config.output_folder_name + File.separator + "LLM_outputs"
				+ File.separator + snapshot.scopeName());
		CsvTools.writeCSVfile(decisions,
				Paths.get(dir + File.separator + snapshot.institutionName() + "_policy_changes.csv"));
		CsvTools.writeCSVfile(effective,
				Paths.get(dir + File.separator + snapshot.institutionName() + "_policies_accumulation.csv"));
		if (!snapshot.rawOutput().isEmpty()) {
			dir = PathTools.makeDirectory(dir + File.separator + snapshot.institutionName());
			PathTools.writeFile(dir + File.separator + (snapshot.year() - 1) + ".txt",
					"######### Prompt (LLM inputs) #########\n" + snapshot.prompt()
							+ "\n\n######### LLM output #########\n" + snapshot.rawOutput()
							+ "\n\n######### Effective policies #########\n" + snapshot.effectiveValues(),
					false);
		}
	}
}
