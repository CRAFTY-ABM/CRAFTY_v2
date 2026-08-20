package de.cesr.crafty.institution.config;

import de.cesr.crafty.institution.model.CraftyElementRef;
import de.cesr.crafty.institution.model.DecisionEngineType;
import de.cesr.crafty.institution.model.EffectType;
import de.cesr.crafty.institution.model.InstitutionConfiguration;
import de.cesr.crafty.institution.model.InstitutionDefinition;
import de.cesr.crafty.institution.model.NormalizationType;
import de.cesr.crafty.institution.model.SpatialScope;
import de.cesr.crafty.institution.model.TargetDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CanonicalInstitutionYamlLoaderTest {
	@Test
	void loadsConvertedGuiSummerSchoolExample() throws Exception {
		Path workingDirectory = Path.of("").toAbsolutePath().normalize();
		Path directory = workingDirectory.resolve("examples/gui-eu-summer-school");
		if (!Files.isDirectory(directory)) {
			directory = workingDirectory.resolve("crafty-institution/examples/gui-eu-summer-school");
		}

		InstitutionConfiguration configuration = CanonicalInstitutionYamlLoader.load(
				directory.resolve("targets.yaml"), directory.resolve("institutions.yaml"));

		assertEquals(15, configuration.targets().size());
		assertEquals(4, configuration.institutions().size());
		assertEquals(19, configuration.institutions().values().stream()
				.mapToInt(institution -> institution.policies().size()).sum());
		assertTrue(configuration.institutions().values().stream()
				.allMatch(institution -> institution.decisionEngine().type() == DecisionEngineType.MANUAL));
		assertEquals(13400, configuration.institutions().get("agriculture_and_rural_development")
				.policies().get("subsidies_for_agricultural_land_users_with_high_landscape_diversity")
				.cost().estimatedTotalCost());
	}

	@Test
	void loadsManualEngineWithoutEngineSpecificConfiguration(@TempDir Path directory) throws IOException {
		Path targets = directory.resolve("targets.yaml");
		Path institutions = directory.resolve("institutions.yaml");
		Files.writeString(targets, """
				schema_version: 1
				targets:
				  - {id: output, name: Output, type: Service, crafty_elements: {food: 1}}
				""");
		Files.writeString(institutions, """
				schema_version: 1
				institutions:
				  - id: operator
				    name: Operator
				    schedule: {start_year: 2020, end_year: 2030, interval_years: 1}
				    scope: {type: all_cells}
				    budget: {amount: 100}
				    targets:
				      - {target: output}
				    policies:
				      - id: support
				        name: Support
				        effects: {Service: {food: 1}}
				        cost: {unit_cost: 2, estimated_quantity: 4}
				        constraints: {value: {min: -10, max: 10}}
				    decision_engine: {type: manual}
				""");

		InstitutionConfiguration configuration = CanonicalInstitutionYamlLoader.load(targets, institutions);

		assertEquals(DecisionEngineType.MANUAL,
				configuration.institutions().get("operator").decisionEngine().type());
		assertNull(configuration.institutions().get("operator").decisionEngine().fuzzy());
		assertNull(configuration.institutions().get("operator").decisionEngine().llm());

	}

	@Test
	void loadsFuzzyAndLlmInstitutionsIntoSharedImmutableDefinitions() throws Exception {
		InstitutionConfiguration configuration = loadCanonicalFixture();

		assertEquals(1, configuration.schemaVersion());
		assertEquals(Set.of("biodiversity_supply", "food_crop_supply"), configuration.targets().keySet());
		assertEquals(Set.of("environment_policy", "food_policy_llm"), configuration.institutions().keySet());

		TargetDefinition biodiversity = configuration.targets().get("biodiversity_supply");
		assertEquals(NormalizationType.BASELINE_RATIO, biodiversity.normalization());
		assertEquals(1.4, biodiversity.goalTrajectories().get("realist").get(2050));
		assertEquals(new CraftyElementRef(EffectType.EXTERNAL, "msa", 1.0), biodiversity.observations().get(0));

		InstitutionDefinition fuzzy = configuration.institutions().get("environment_policy");
		assertEquals(DecisionEngineType.FUZZY, fuzzy.decisionEngine().type());
		assertEquals(SpatialScope.Type.ALL_CELLS, fuzzy.scope().type());
		assertEquals(1.4, fuzzy.decisionEngine().fuzzy().targets().get("biodiversity_supply").desiredValue());
		assertTrue(fuzzy.decisionEngine().fuzzy().fclFile().isAbsolute());
		assertTrue(fuzzy.decisionEngine().fuzzy().optimizeBudget());
		assertEquals("BIODIVERSITY",
				fuzzy.decisionEngine().fuzzy().policies().get("biodiversity_subsidy").functionBlock());

		InstitutionDefinition llm = configuration.institutions().get("food_policy_llm");
		assertEquals(DecisionEngineType.LLM, llm.decisionEngine().type());
		assertNull(llm.policies().get("food_producer_subsidy").cost());
		assertNull(llm.policies().get("food_producer_subsidy").constraints());
		assertEquals("EM", llm.scope().name());
		assertEquals(4, llm.decisionEngine().llm().retryCount());

		assertThrows(UnsupportedOperationException.class,
				() -> configuration.targets().put("other", biodiversity));
		assertThrows(UnsupportedOperationException.class,
				() -> fuzzy.policies().get("biodiversity_subsidy").effects().add(
						new CraftyElementRef(EffectType.AFT, "AF", 1.0)));
	}

	@Test
	void aggregatesStructuralAndCrossReferenceErrors(@TempDir Path tempDir) throws Exception {
		Path targets = tempDir.resolve("targets.yaml");
		Files.writeString(targets, """
				schema_version: 1
				targets:
				  - name: Known Target
				    type: External
				    crafty_elements: {signal: 1}
				""");
		Path institutions = tempDir.resolve("institutions.yaml");
		Files.writeString(institutions, """
				schema_version: 1
				institutions:
				  - name: Broken Institution
				    schedule: {start_year: 2050, end_year: 2020, interval_years: 0}
				    scope: {type: all_cells}
				    budget: {amount: -1}
				    targets:
				      - target: Missing Target
				    policies:
				      - name: Broken Policy
				        effects:
				          Capital: {invalidCapitalKey: 1}
				        cost: {unit_cost: -1, estimated_quantity: -2}
				        constraints:
				          value: {min: 10, max: 1}
				    decision_engine:
				      type: fuzzy
				      fuzzy:
				        fcl_file: missing.fcl
				        start_at_first_step: true
				        policies: {}
				""");

		ConfigurationException error = assertThrows(ConfigurationException.class,
				() -> CanonicalInstitutionYamlLoader.load(targets, institutions));

		assertTrue(error.errors().size() >= 8, error.getMessage());
		assertTrue(error.getMessage().contains("unknown target"));
		assertTrue(error.getMessage().contains("Range minimum cannot exceed maximum"));
		assertTrue(error.getMessage().contains("does not reference an existing file"));
		assertTrue(error.getMessage().contains("AFT:capital format"));
	}

	@Test
	void rejectsIdentifiersThatCollideAfterNormalization(@TempDir Path tempDir) throws Exception {
		Path targets = tempDir.resolve("targets.yaml");
		Files.writeString(targets, """
				schema_version: 1
				targets:
				  - name: Bio Diversity
				    type: External
				    crafty_elements: {first: 1}
				  - name: bio-diversity
				    type: External
				    crafty_elements: {second: 1}
				""");
		Path institutions = tempDir.resolve("institutions.yaml");
		Files.writeString(institutions, "schema_version: 1\ninstitutions: []\n");

		ConfigurationException error = assertThrows(ConfigurationException.class,
				() -> CanonicalInstitutionYamlLoader.load(targets, institutions));

		assertTrue(error.getMessage().contains("duplicates normalized identifier 'bio_diversity'"));
	}

	@Test
	void rejectsDuplicateYamlKeys(@TempDir Path tempDir) throws Exception {
		Path targets = tempDir.resolve("targets.yaml");
		Files.writeString(targets, """
				schema_version: 1
				targets:
				  - name: First Name
				    name: Second Name
				    type: External
				    crafty_elements: {signal: 1}
				""");
		Path institutions = tempDir.resolve("institutions.yaml");
		Files.writeString(institutions, "schema_version: 1\ninstitutions: []\n");

		ConfigurationException error = assertThrows(ConfigurationException.class,
				() -> CanonicalInstitutionYamlLoader.load(targets, institutions));

		assertTrue(error.getMessage().contains("duplicate key name"));
	}

	@Test
	void rejectsNonCellExternalPolicyEffects(@TempDir Path tempDir) throws Exception {
		Path targets = tempDir.resolve("targets.yaml");
		Files.writeString(targets, "schema_version: 1\ntargets: []\n");
		Path institutions = tempDir.resolve("institutions.yaml");
		Files.writeString(institutions, """
				schema_version: 1
				institutions:
				  - id: invalid
				    name: Invalid
				    schedule: {start_year: 2020, end_year: 2030, interval_years: 1}
				    scope: {type: all_cells}
				    budget: {amount: 0}
				    targets: []
				    policies:
				      - id: signal
				        name: Signal
				        effects: {External: {global_signal: 1}}
				        cost: {unit_cost: 0, estimated_quantity: 0}
				        constraints: {value: {min: -1, max: 1}}
				    decision_engine: {type: manual}
				""");

		ConfigurationException error = assertThrows(ConfigurationException.class,
				() -> CanonicalInstitutionYamlLoader.load(targets, institutions));

		assertTrue(error.getMessage().contains("EXTERNAL is only valid for target observations"));
	}

	private static InstitutionConfiguration loadCanonicalFixture() throws Exception {
		return CanonicalInstitutionYamlLoader.load(resourcePath("canonical/targets.yaml"),
				resourcePath("canonical/institutions.yaml"));
	}

	private static Path resourcePath(String resource) throws URISyntaxException {
		return Path.of(CanonicalInstitutionYamlLoaderTest.class.getClassLoader().getResource(resource).toURI());
	}
}
