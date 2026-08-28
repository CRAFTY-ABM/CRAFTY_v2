package de.cesr.crafty.institution.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.cesr.crafty.institution.config.ConfigurationException;
import de.cesr.crafty.institution.model.DecisionEngineType;

class InstitutionRunnerTest {
	@Test
	void detectsFuzzyEngineFromSimpleConfiguration() throws Exception {
		var configuration = InstitutionRunner.load(exampleDirectory());

		assertEquals(1, configuration.targets().size());
		assertEquals(DecisionEngineType.FUZZY, InstitutionRunner.singleEngineType(configuration));
	}

	@Test
	void fuzzyExampleLoadsForLlmAfterRemovingFuzzyOnlyPolicyParameters(@TempDir Path directory) throws Exception {
		copyExample(directory);
		Path institutions = directory.resolve("institutions.yaml");
		Files.writeString(institutions, asLlmConfiguration(Files.readString(institutions)));

		var configuration = InstitutionRunner.load(directory);

		assertEquals(1, configuration.targets().size());
		assertEquals(DecisionEngineType.LLM,
				configuration.institutions().get("environment_policy").decisionEngine().type());
		assertEquals("prompt.yaml", configuration.institutions().get("environment_policy")
				.decisionEngine().llm().promptFile().getFileName().toString());
	}

	private static String asLlmConfiguration(String yaml) {
		return yaml.replace("type: fuzzy", "type: llm")
				.replaceAll("(?m)^[ ]{8}cost:.*(?:\\R|$)", "")
				.replaceAll("(?m)^[ ]{8}constraints:\\R[ ]{10}value:.*(?:\\R|$)", "");
	}

	@Test
	void detectsLlmEngineFromDedicatedExample() throws Exception {
		var configuration = InstitutionRunner.load(llmExampleDirectory());

		assertEquals(1, configuration.targets().size());
		assertEquals(DecisionEngineType.LLM, InstitutionRunner.singleEngineType(configuration));
		assertTrue(Files.isRegularFile(llmExampleDirectory().resolve("paradigms.csv")));
	}

	@Test
	void requiresBothConfigurationFiles(@TempDir Path directory) throws Exception {
		Files.writeString(directory.resolve("targets.yaml"), "schema_version: 1\ntargets: []\n");

		ConfigurationException error = assertThrows(ConfigurationException.class,
				() -> InstitutionRunner.load(directory));

		assertTrue(error.getMessage().contains("requires both"));
	}

	@Test
	void rejectsMixedEngineTypes() throws Exception {
		Path directory = Path.of(getClass().getClassLoader().getResource("canonical").toURI());
		var configuration = InstitutionRunner.load(directory);

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> InstitutionRunner.singleEngineType(configuration));

		assertTrue(error.getMessage().contains("one engine type"));
	}

	private static void copyExample(Path destination) throws Exception {
		for (String file : new String[] {"targets.yaml", "institutions.yaml", "rules.fcl", "prompt.yaml"}) {
			Files.copy(exampleDirectory().resolve(file), destination.resolve(file));
		}
	}

	private static Path exampleDirectory() {
		Path workingDirectory = Path.of("").toAbsolutePath().normalize();
		Path moduleExample = workingDirectory.resolve("examples/simple");
		return Files.isDirectory(moduleExample)
				? moduleExample
				: workingDirectory.resolve("crafty-institution/examples/simple");
	}

	private static Path llmExampleDirectory() {
		Path workingDirectory = Path.of("").toAbsolutePath().normalize();
		Path moduleExample = workingDirectory.resolve("examples/llm-hardwood");
		return Files.isDirectory(moduleExample)
				? moduleExample
				: workingDirectory.resolve("crafty-institution/examples/llm-hardwood");
	}
}
