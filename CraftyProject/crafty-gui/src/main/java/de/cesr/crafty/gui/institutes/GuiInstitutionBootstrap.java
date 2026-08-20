package de.cesr.crafty.gui.institutes;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.institution.config.CanonicalInstitutionYamlLoader;
import de.cesr.crafty.institution.model.InstitutionConfiguration;
import de.cesr.crafty.institution.model.InstitutionDefinition;
import de.cesr.crafty.institution.model.TargetDefinition;

/** Institution YAML entry point for the GUI. */
public final class GuiInstitutionBootstrap {
	private static final CustomLogger LOGGER = new CustomLogger(GuiInstitutionBootstrap.class);
	private static boolean initialized;
	private static Map<String, TargetViewModel> targets = Map.of();
	private static Map<String, InstitutionViewModel> institutes = Map.of();

	private GuiInstitutionBootstrap() {
	}

	public static synchronized void initialize() {
		if (ConfigLoader.config != null) {
			ConfigLoader.config.use_cell_level_taxes = true;
		}
		Path targetsFile = first("targets.yaml");
		Path institutionsFile = first("institutions.yaml");
		if (targetsFile == null && institutionsFile == null) {
			LOGGER.warn("No institution configuration was found. Institutions were not initialized.");
			setState(new InstitutionConfiguration(1, Map.of(), Map.of()));
			return;
		}
		if (targetsFile == null || institutionsFile == null) {
			throw new IllegalStateException(
					"GUI institutions require both schema_version 1 targets.yaml and institutions.yaml.");
		}
		try {
			setState(CanonicalInstitutionYamlLoader.load(targetsFile, institutionsFile));
			LOGGER.info("Loaded institution configuration from " + institutionsFile);
		} catch (IOException | IllegalArgumentException exception) {
			throw new IllegalStateException("Could not initialize GUI institutions", exception);
		}
	}

	public static synchronized void ensureInitialized() {
		if (!initialized) {
			initialize();
		}
	}

	static Map<String, TargetViewModel> targets() {
		ensureInitialized();
		return targets;
	}

	static Map<String, InstitutionViewModel> institutes() {
		ensureInitialized();
		return institutes;
	}

	private static void setState(InstitutionConfiguration configuration) {
		Map<String, TargetViewModel> byId = new LinkedHashMap<>();
		Map<String, TargetViewModel> targetViews = new LinkedHashMap<>();
		for (TargetDefinition definition : configuration.targets().values()) {
			TargetViewModel target = new TargetViewModel(definition);
			byId.put(definition.id(), target);
			putDisplayName(targetViews, definition.name(), target, "target");
		}

		Map<String, InstitutionViewModel> instituteViews = new LinkedHashMap<>();
		for (InstitutionDefinition definition : configuration.institutions().values()) {
			Map<String, TargetViewModel> selectedTargets = new LinkedHashMap<>();
			definition.targets().forEach(reference -> {
				TargetViewModel target = byId.get(reference.targetId());
				if (target == null) {
					throw new IllegalArgumentException("Institution '" + definition.name()
							+ "' references missing target '" + reference.targetId() + "'");
				}
				selectedTargets.put(target.getName(), target);
			});
			putDisplayName(instituteViews, definition.name(),
					new InstitutionViewModel(definition, selectedTargets), "institution");
		}

		initialized = true;
		targets = targetViews;
		institutes = instituteViews;
		targets.values().forEach(target -> LOGGER.info("Target Name = " + target.getName() + ", type= "
				+ target.getType() + " => " + target.getCraftyElem()));
		institutes.values().forEach(institute -> LOGGER.info("Institute Name = " + institute.getName() + ", targets= "
				+ institute.getTargets().keySet() + ", policies= " + institute.getPolicies().keySet()));
	}

	private static Path first(String fileName) {
		ArrayList<Path> matches = PathTools.fileFilter(PathTools.asFolder("institutes"), fileName);
		return matches == null || matches.isEmpty() ? null : matches.getFirst();
	}

	private static <T> void putDisplayName(Map<String, T> values, String name, T value, String kind) {
		if (values.putIfAbsent(name, value) != null) {
			throw new IllegalArgumentException("Duplicate " + kind + " display name '" + name + "'");
		}
	}
}
