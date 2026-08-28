package de.cesr.crafty.institution.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import org.apache.logging.log4j.LogManager;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.main.MainHeadless;
import de.cesr.crafty.core.modelRunner.ModelRunner;
import de.cesr.crafty.institution.config.ConfigurationException;
import de.cesr.crafty.institution.config.CanonicalInstitutionYamlLoader;
import de.cesr.crafty.institution.decision.fuzzy.integration.FuzzyInstitutionRunner;
import de.cesr.crafty.institution.decision.llm.runtime.LlmRunner;
import de.cesr.crafty.institution.model.DecisionEngineType;
import de.cesr.crafty.institution.model.InstitutionConfiguration;

/** Single headless entry point for a homogeneous fuzzy or LLM configuration. */
public final class InstitutionRunner {
	private InstitutionRunner() {
	}

	public static void main(String[] args) {
		MainHeadless.initializeConfig(args);
		Path institutionDirectory = Paths.get(ConfigLoader.config.institutions_directory);
		try {
			InstitutionConfiguration configuration = load(institutionDirectory);
			DecisionEngineType type = singleEngineType(configuration);
			if (type == DecisionEngineType.MANUAL) {
				throw new IllegalArgumentException("Manual institutions require the CRAFTY GUI");
			}

			initializeCrafty();
			switch (type) {
			case FUZZY -> FuzzyInstitutionRunner.run(configuration);
			case LLM -> LlmRunner.run(configuration);
			case MANUAL -> throw new IllegalStateException("Manual runner dispatch is not available headlessly");
			}
		} catch (java.io.IOException exception) {
			throw new IllegalStateException("Could not load institution configuration from " + institutionDirectory,
					exception);
		} finally {
			CustomLogger.shutdownRunFileLoggers();
			LogManager.shutdown();
		}
	}

	static InstitutionConfiguration load(Path directory) throws IOException {
		Path normalized = directory.toAbsolutePath().normalize();
		Path targets = normalized.resolve("targets.yaml");
		Path institutions = normalized.resolve("institutions.yaml");
		if (!Files.isRegularFile(targets) || !Files.isRegularFile(institutions)) {
			throw new ConfigurationException(java.util.List.of(
					"Institution configuration requires both " + targets + " and " + institutions + "."));
		}
		return CanonicalInstitutionYamlLoader.load(targets, institutions);
	}

	static DecisionEngineType singleEngineType(InstitutionConfiguration configuration) {
		Set<DecisionEngineType> types = configuration.institutions().values().stream()
				.map(institution -> institution.decisionEngine().type())
				.collect(java.util.stream.Collectors.toSet());
		if (types.isEmpty()) {
			throw new IllegalArgumentException("Headless institution configuration must contain an institution");
		}
		if (types.size() != 1) {
			throw new IllegalArgumentException("Headless institution configuration must use one engine type, found "
					+ types);
		}
		return types.iterator().next();
	}

	private static void initializeCrafty() {
		ProjectLoader.pathInitialisation(Paths.get(ConfigLoader.config.project_path));
		ConfigLoader.config.use_cell_level_taxes = true;
		MainHeadless.runner = new ModelRunner();
		MainHeadless.runner.start();
		MainHeadless.runner.initialzeRun();
	}
}
