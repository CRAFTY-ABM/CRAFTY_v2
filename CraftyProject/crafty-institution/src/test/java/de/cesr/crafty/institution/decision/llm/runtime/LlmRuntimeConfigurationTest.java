package de.cesr.crafty.institution.decision.llm.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.institution.config.CanonicalInstitutionYamlLoader;
import de.cesr.crafty.institution.model.EffectType;

class LlmRuntimeConfigurationTest {
	private int originalStartYear;
	private int originalEndYear;

	@BeforeEach
	void setSimulationYears() {
		originalStartYear = Timestep.getStartYear();
		originalEndYear = Timestep.getEndtYear();
		Timestep.setStartYear(2020);
		Timestep.setEndtYear(2100);
	}

	@AfterEach
	void restoreSimulationYears() {
		Timestep.setStartYear(originalStartYear);
		Timestep.setEndtYear(originalEndYear);
	}

	@Test
	void sharedTargetCreatesInterpolatedLlmObservationState(@TempDir Path directory) throws Exception {
		var configuration = llmConfiguration(directory);
		var definition = configuration.targets().get("biodiversity_supply");
		LlmTargetState target = new LlmTargetState(definition);

		assertEquals(Map.of("msa", 1.0), target.getCraftyElem());
		assertEquals(1.0, target.getRealist_goals().get(2020));
		assertEquals(1.1, target.getRealist_goals().get(2025), 1.0e-9);
		assertEquals(1.2, target.getRealist_goals().get(2030), 1.0e-9);
		assertEquals(1.2, target.getRealist_goals().get(2100), 1.0e-9);
	}

	@Test
	void sharedPoliciesContainOnlyCellLevelEffects(@TempDir Path directory) throws Exception {
		var configuration = llmConfiguration(directory);
		var institution = configuration.institutions().get("environment_policy");
		var policy = institution.policies().get("biodiversity_subsidy");

		assertEquals(4, institution.schedule().intervalYears());
		assertFalse(policy.effects().stream().anyMatch(effect -> effect.type() == EffectType.EXTERNAL));
		assertEquals(Map.of("CW", 1.0, "AF", 0.5), policy.effects().stream()
				.filter(effect -> effect.type() == EffectType.AFT)
				.collect(java.util.stream.Collectors.toMap(effect -> effect.name(), effect -> effect.weight())));
	}

	@Test
	void allCellsScopeCreatesGlobalLlmRuntime(@TempDir Path directory) throws Exception {
		var configuration = llmConfiguration(directory);
		Map<String, Cell> previousCells = Map.copyOf(CellsLoader.hashCell);
		ParadigmRuntimeSet runtimeSet = new ParadigmRuntimeSet();
		try {
			CellsLoader.hashCell.clear();
			Cell first = new Cell(1, 1);
			Cell second = new Cell(2, 2);
			CellsLoader.hashCell.put("1,1", first);
			CellsLoader.hashCell.put("2,2", second);

			runtimeSet.setup(configuration);

			ParadigmRuntime all = ParadigmRuntimeSet.paradigms.get(ParadigmRuntimeSet.ALL_CELLS_RUNTIME_KEY);
			assertEquals("all", all.getName());
			assertEquals(2, all.getCells().size());
			assertTrue(all.getCells().containsAll(List.of(first, second)));
			assertTrue(all.getInstitutes().containsKey("environment_policy"));
		} finally {
			runtimeSet.close();
			ParadigmRuntimeSet.paradigms.clear();
			CellsLoader.hashCell.clear();
			CellsLoader.hashCell.putAll(previousCells);
		}
	}

	private static de.cesr.crafty.institution.model.InstitutionConfiguration llmConfiguration(Path directory)
			throws Exception {
		Path source = exampleDirectory();
		for (String file : new String[] {"targets.yaml", "institutions.yaml", "rules.fcl", "prompt.yaml"}) {
			Files.copy(source.resolve(file), directory.resolve(file));
		}
		Path institutions = directory.resolve("institutions.yaml");
		Files.writeString(institutions, Files.readString(institutions)
				.replace("type: fuzzy", "type: llm")
				.replaceAll("(?m)^[ ]{8}cost:.*(?:\\R|$)", "")
				.replaceAll("(?m)^[ ]{8}constraints:\\R[ ]{10}value:.*(?:\\R|$)", ""));
		return CanonicalInstitutionYamlLoader.load(directory.resolve("targets.yaml"), institutions);
	}

	private static Path exampleDirectory() {
		Path workingDirectory = Path.of("").toAbsolutePath().normalize();
		Path moduleExample = workingDirectory.resolve("examples/simple");
		return Files.isDirectory(moduleExample)
				? moduleExample
				: workingDirectory.resolve("crafty-institution/examples/simple");
	}
}
