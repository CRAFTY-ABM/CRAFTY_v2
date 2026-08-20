package de.cesr.crafty.institution.decision.fuzzy.integration;

import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.modelRunner.ModelRunner;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.institution.decision.fuzzy.FuzzyInstitutionRuntimeSet;
import de.cesr.crafty.institution.model.InstitutionConfiguration;

/*
 * @author Mohamed Byari
 *
 */
public final class FuzzyInstitutionRunner {
	private static final CustomLogger LOGGER = new CustomLogger(FuzzyInstitutionRunner.class);

	private FuzzyInstitutionRunner() {
	}

	public static void run(InstitutionConfiguration configuration) {
		FuzzyExternalValues.initialize();
		var institutions = FuzzyInstitutionRuntimeSet.from(configuration);
		LOGGER.info("Loaded " + configuration.institutions().size() + " fuzzy institutions");
		InstitutionOutput institutionOutput = institutions.initialPolicies();
		FuzzyConnector model = new FuzzyConnector(configuration);
		for (int year = Timestep.getStartYear(); year <= Timestep.getEndtYear(); year++) {
			TargetModelOutput targetModelOutput = model.step(institutionOutput);
			institutionOutput = institutions.step(targetModelOutput);
		}
		ModelRunner.exportChartsPlots();
	}
}
