package de.cesr.crafty.institution.config;

import de.cesr.crafty.institution.model.ActivationSchedule;
import de.cesr.crafty.institution.model.BudgetDefinition;
import de.cesr.crafty.institution.model.CraftyElementRef;
import de.cesr.crafty.institution.model.DecisionEngineDefinition;
import de.cesr.crafty.institution.model.DecisionEngineType;
import de.cesr.crafty.institution.model.EffectType;
import de.cesr.crafty.institution.decision.fuzzy.FuzzyEngineConfig;
import de.cesr.crafty.institution.decision.fuzzy.FuzzyPolicySettings;
import de.cesr.crafty.institution.decision.fuzzy.FuzzyTargetSettings;
import de.cesr.crafty.institution.model.InstitutionConfiguration;
import de.cesr.crafty.institution.model.InstitutionDefinition;
import de.cesr.crafty.institution.decision.llm.LlmEngineConfig;
import de.cesr.crafty.institution.model.NormalizationType;
import de.cesr.crafty.institution.model.NumericRange;
import de.cesr.crafty.institution.model.PolicyConstraints;
import de.cesr.crafty.institution.model.PolicyCost;
import de.cesr.crafty.institution.model.PolicyDefinition;
import de.cesr.crafty.institution.model.SpatialScope;
import de.cesr.crafty.institution.model.TargetDefinition;
import de.cesr.crafty.institution.model.TargetReference;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

public final class CanonicalInstitutionYamlLoader {
	private CanonicalInstitutionYamlLoader() {
	}

	public static InstitutionConfiguration load(Path targetsFile, Path institutionsFile) throws IOException {
		List<String> errors = new ArrayList<>();
		Map<?, ?> targetsRoot = readRoot(targetsFile, errors);
		Map<?, ?> institutionsRoot = readRoot(institutionsFile, errors);

		int targetVersion = integer(targetsRoot.get("schema_version"), "targets.schema_version", errors, -1);
		int institutionVersion = integer(institutionsRoot.get("schema_version"), "institutions.schema_version", errors,
				-1);
		if (targetVersion != 1) {
			errors.add("targets.schema_version must be 1");
		}
		if (institutionVersion != 1) {
			errors.add("institutions.schema_version must be 1");
		}

		Map<String, TargetDefinition> targets = parseTargets(targetsRoot.get("targets"), errors);
		Map<String, InstitutionDefinition> institutions = parseInstitutions(institutionsRoot.get("institutions"),
				institutionsFile.toAbsolutePath().normalize().getParent(), targets, errors);

		if (!errors.isEmpty()) {
			throw new ConfigurationException(errors);
		}
		return new InstitutionConfiguration(1, targets, institutions);
	}

	private static Map<?, ?> readRoot(Path file, List<String> errors) throws IOException {
		LoaderOptions options = new LoaderOptions();
		options.setAllowDuplicateKeys(false);
		Yaml yaml = new Yaml(new SafeConstructor(options));
		try (InputStream input = Files.newInputStream(file)) {
			Object value = yaml.load(input);
			if (value instanceof Map<?, ?> map) {
				return map;
			}
			errors.add(file + ": YAML root must be a map");
			return Map.of();
		} catch (YAMLException e) {
			errors.add(file + ": " + e.getMessage());
			return Map.of();
		}
	}

	private static Map<String, TargetDefinition> parseTargets(Object value, List<String> errors) {
		List<?> list = list(value, "targets.targets", errors);
		Map<String, TargetDefinition> targets = new LinkedHashMap<>();
		for (int i = 0; i < list.size(); i++) {
			String path = "targets.targets[" + i + "]";
			Map<?, ?> map = map(list.get(i), path, errors);
			String name = requiredString(map.get("name"), path + ".name", errors);
			String id = optionalString(map.get("id"), name);
			EffectType type = enumValue(map.get("type"), EffectType.class, path + ".type", errors);
			List<CraftyElementRef> observations = parseElements(type, map.get("crafty_elements"),
					path + ".crafty_elements", errors);
			NormalizationType normalization = parseNormalization(map.get("normalization"), path + ".normalization",
					errors);
			Map<String, Map<Integer, Double>> goals = parseGoalTrajectories(map.get("goals"), path + ".goals", errors);

			TargetDefinition target = construct(path, errors,
					() -> new TargetDefinition(id, name, observations, normalization, goals));
			if (target != null) {
				putUnique(targets, target.id(), target, path + ".id", errors);
			}
		}
		return targets;
	}

	private static Map<String, InstitutionDefinition> parseInstitutions(Object value, Path baseDir,
			Map<String, TargetDefinition> availableTargets, List<String> errors) {
		List<?> list = list(value, "institutions.institutions", errors);
		Map<String, InstitutionDefinition> institutions = new LinkedHashMap<>();
		for (int i = 0; i < list.size(); i++) {
			String path = "institutions.institutions[" + i + "]";
			Map<?, ?> map = map(list.get(i), path, errors);
			String name = requiredString(map.get("name"), path + ".name", errors);
			String id = optionalString(map.get("id"), name);
			String description = optionalString(map.get("description"), "");
			ActivationSchedule schedule = parseSchedule(map.get("schedule"), path + ".schedule", errors);
			SpatialScope scope = parseScope(map.get("scope"), path + ".scope", errors);
			BudgetDefinition budget = parseBudget(map.get("budget"), path + ".budget", errors);
			List<TargetReference> targets = parseTargetReferences(map.get("targets"), path + ".targets",
					availableTargets, errors);
			Map<String, PolicyDefinition> policies = parsePolicies(map.get("policies"), path + ".policies", errors);
			DecisionEngineDefinition engine = parseEngine(map.get("decision_engine"), path + ".decision_engine", baseDir,
					targets, policies, errors);

			InstitutionDefinition institution = construct(path, errors,
					() -> new InstitutionDefinition(id, name, description, schedule, scope, budget, targets, policies,
							engine));
			if (institution != null) {
				putUnique(institutions, institution.id(), institution, path + ".id", errors);
			}
		}
		return institutions;
	}

	private static ActivationSchedule parseSchedule(Object value, String path, List<String> errors) {
		Map<?, ?> map = map(value, path, errors);
		int start = integer(map.get("start_year"), path + ".start_year", errors, 0);
		int end = integer(map.get("end_year"), path + ".end_year", errors, -1);
		int interval = integer(map.get("interval_years"), path + ".interval_years", errors, 0);
		return construct(path, errors, () -> new ActivationSchedule(start, end, interval));
	}

	private static SpatialScope parseScope(Object value, String path, List<String> errors) {
		Map<?, ?> map = map(value, path, errors);
		String rawType = requiredString(map.get("type"), path + ".type", errors);
		SpatialScope.Type type = null;
		if (rawType != null) {
			String canonical = rawType.trim().toUpperCase(Locale.ROOT).replace('-', '_');
			type = enumValue(canonical, SpatialScope.Type.class, path + ".type", errors);
		}
		String name;
		if (type == SpatialScope.Type.REGIONS && map.get("regions") != null) {
			List<?> regions = list(map.get("regions"), path + ".regions", errors);
			name = regions.stream().map(Object::toString).map(String::trim).filter(region -> !region.isEmpty())
					.distinct().collect(java.util.stream.Collectors.joining(","));
			if (name.isEmpty()) {
				errors.add(path + ".regions must contain at least one region code");
			}
		} else {
			name = optionalString(map.get("name"), "");
		}
		SpatialScope.Type finalType = type;
		return construct(path, errors, () -> new SpatialScope(finalType, name));
	}

	private static BudgetDefinition parseBudget(Object value, String path, List<String> errors) {
		Map<?, ?> map = map(value, path, errors);
		double amount = number(map.get("amount"), path + ".amount", errors, -1);
		return construct(path, errors, () -> new BudgetDefinition(amount));
	}

	private static List<TargetReference> parseTargetReferences(Object value, String path,
			Map<String, TargetDefinition> availableTargets, List<String> errors) {
		List<?> list = list(value, path, errors);
		List<TargetReference> result = new ArrayList<>();
		Map<String, Boolean> seen = new LinkedHashMap<>();
		for (int i = 0; i < list.size(); i++) {
			String itemPath = path + "[" + i + "]";
			Map<?, ?> map = map(list.get(i), itemPath, errors);
			String targetName = requiredString(map.get("target"), itemPath + ".target", errors);
			String targetId = normalize(targetName, itemPath + ".target", errors);
			TargetReference reference = construct(itemPath, errors,
					() -> new TargetReference(targetId));
			if (reference != null) {
				if (!availableTargets.containsKey(reference.targetId())) {
					errors.add(itemPath + " references unknown target '" + reference.targetId() + "'");
				}
				if (seen.put(reference.targetId(), true) != null) {
					errors.add(itemPath + " duplicates target reference '" + reference.targetId() + "'");
				} else {
					result.add(reference);
				}
			}
		}
		return result;
	}

	private static Map<String, PolicyDefinition> parsePolicies(Object value, String path, List<String> errors) {
		List<?> list = list(value, path, errors);
		Map<String, PolicyDefinition> policies = new LinkedHashMap<>();
		for (int i = 0; i < list.size(); i++) {
			String policyPath = path + "[" + i + "]";
			Map<?, ?> map = map(list.get(i), policyPath, errors);
			String name = requiredString(map.get("name"), policyPath + ".name", errors);
			String id = optionalString(map.get("id"), name);
			List<CraftyElementRef> effects = parseEffects(map.get("effects"), policyPath + ".effects", errors);
			PolicyCost cost = map.get("cost") == null ? null
					: parseCost(map.get("cost"), policyPath + ".cost", errors);
			PolicyConstraints constraints = map.get("constraints") == null ? null
					: parseConstraints(map.get("constraints"), policyPath + ".constraints", errors);
			PolicyDefinition policy = construct(policyPath, errors,
					() -> new PolicyDefinition(id, name, effects, cost, constraints));
			if (policy != null) {
				putUnique(policies, policy.id(), policy, policyPath + ".id", errors);
			}
		}
		return policies;
	}

	private static PolicyCost parseCost(Object value, String path, List<String> errors) {
		Map<?, ?> map = map(value, path, errors);
		double unitCost = number(map.get("unit_cost"), path + ".unit_cost", errors, -1);
		double quantity = number(map.get("estimated_quantity"), path + ".estimated_quantity", errors, -1);
		return construct(path, errors, () -> new PolicyCost(unitCost, quantity));
	}

	private static PolicyConstraints parseConstraints(Object value, String path, List<String> errors) {
		Map<?, ?> map = map(value, path, errors);
		NumericRange policyValue = map.get("value") == null ? null
				: parseRange(map.get("value"), path + ".value", errors);
		return construct(path, errors, () -> new PolicyConstraints(policyValue));
	}

	private static NumericRange parseRange(Object value, String path, List<String> errors) {
		Map<?, ?> map = map(value, path, errors);
		double min = number(map.get("min"), path + ".min", errors, Double.NaN);
		double max = number(map.get("max"), path + ".max", errors, Double.NaN);
		return construct(path, errors, () -> new NumericRange(min, max));
	}

	private static DecisionEngineDefinition parseEngine(Object value, String path, Path baseDir,
			List<TargetReference> targets, Map<String, PolicyDefinition> policies, List<String> errors) {
		Map<?, ?> map = map(value, path, errors);
		DecisionEngineType type = enumValue(map.get("type"), DecisionEngineType.class, path + ".type", errors);
		if (type == DecisionEngineType.FUZZY) {
			FuzzyEngineConfig fuzzy = parseFuzzyEngine(map.get("fuzzy"), path + ".fuzzy", baseDir, targets, policies,
					errors);
			return construct(path, errors, () -> new DecisionEngineDefinition(type, fuzzy, null));
		}
		if (type == DecisionEngineType.LLM) {
			LlmEngineConfig llm = parseLlmEngine(map.get("llm"), path + ".llm", baseDir, errors);
			return construct(path, errors, () -> new DecisionEngineDefinition(type, null, llm));
		}
		if (type == DecisionEngineType.MANUAL) {
			return construct(path, errors, () -> new DecisionEngineDefinition(type, null, null));
		}
		return null;
	}

	private static FuzzyEngineConfig parseFuzzyEngine(Object value, String path, Path baseDir,
			List<TargetReference> targets, Map<String, PolicyDefinition> policies, List<String> errors) {
		Map<?, ?> map = map(value, path, errors);
		Path fcl = resolveAsset(baseDir, requiredString(map.get("fcl_file"), path + ".fcl_file", errors),
				path + ".fcl_file", errors);
		boolean startAtFirst = bool(map.get("start_at_first_step"), path + ".start_at_first_step", errors, false);
		boolean optimizeBudget = bool(map.get("optimize_budget"), path + ".optimize_budget", errors, false);
		Map<?, ?> targetMap = map(map.get("targets"), path + ".targets", errors);
		Map<String, FuzzyTargetSettings> targetSettings = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : targetMap.entrySet()) {
			String targetId = normalize(entry.getKey() == null ? null : entry.getKey().toString(), path + ".targets",
					errors);
			Map<?, ?> settingMap = map(entry.getValue(), path + ".targets." + targetId, errors);
			double desiredValue = number(settingMap.get("desired_value"),
					path + ".targets." + targetId + ".desired_value", errors, Double.NaN);
			FuzzyTargetSettings setting = construct(path + ".targets." + targetId, errors,
					() -> new FuzzyTargetSettings(desiredValue));
			if (setting != null) {
				putUnique(targetSettings, targetId, setting, path + ".targets", errors);
			}
		}
		for (TargetReference target : targets) {
			if (!targetSettings.containsKey(target.targetId())) {
				errors.add(path + ".targets is missing fuzzy settings for target '" + target.targetId() + "'");
			}
		}
		for (String targetId : targetSettings.keySet()) {
			if (targets.stream().noneMatch(target -> target.targetId().equals(targetId))) {
				errors.add(path + ".targets contains unreferenced target '" + targetId + "'");
			}
		}
		Map<?, ?> policyMap = map(map.get("policies"), path + ".policies", errors);
		Map<String, FuzzyPolicySettings> settings = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : policyMap.entrySet()) {
			String policyId = normalize(entry.getKey() == null ? null : entry.getKey().toString(), path + ".policies",
					errors);
			Map<?, ?> settingMap = map(entry.getValue(), path + ".policies." + policyId, errors);
			String block = requiredString(settingMap.get("function_block"),
					path + ".policies." + policyId + ".function_block", errors);
			double stepSize = number(settingMap.get("step_size"), path + ".policies." + policyId + ".step_size", errors,
					-1);
			NumericRange change = parseRange(settingMap.get("change"),
					path + ".policies." + policyId + ".change", errors);
			FuzzyPolicySettings setting = construct(path + ".policies." + policyId, errors,
					() -> new FuzzyPolicySettings(block, stepSize, change));
			if (setting != null) {
				putUnique(settings, policyId, setting, path + ".policies", errors);
			}
		}
		for (String policyId : policies.keySet()) {
			if (!settings.containsKey(policyId)) {
				errors.add(path + ".policies is missing fuzzy settings for policy '" + policyId + "'");
			}
		}
		for (String policyId : settings.keySet()) {
			if (!policies.containsKey(policyId)) {
				errors.add(path + ".policies contains unknown policy '" + policyId + "'");
			}
		}
		return construct(path, errors,
				() -> new FuzzyEngineConfig(fcl, startAtFirst, optimizeBudget, targetSettings, settings));
	}

	private static LlmEngineConfig parseLlmEngine(Object value, String path, Path baseDir, List<String> errors) {
		Map<?, ?> map = map(value, path, errors);
		Path prompt = resolveAsset(baseDir, requiredString(map.get("prompt_file"), path + ".prompt_file", errors),
				path + ".prompt_file", errors);
		int retries = integer(map.get("retry_count"), path + ".retry_count", errors, 0);
		return construct(path, errors, () -> new LlmEngineConfig(prompt, retries));
	}

	private static List<CraftyElementRef> parseEffects(Object value, String path, List<String> errors) {
		Map<?, ?> map = map(value, path, errors);
		List<CraftyElementRef> effects = new ArrayList<>();
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			EffectType type = enumValue(entry.getKey(), EffectType.class, path, errors);
			effects.addAll(parseElements(type, entry.getValue(), path + "." + entry.getKey(), errors));
		}
		if (effects.isEmpty()) {
			errors.add(path + " must contain at least one effect mapping");
		}
		return effects;
	}

	private static List<CraftyElementRef> parseElements(EffectType type, Object value, String path,
			List<String> errors) {
		Map<?, ?> map = map(value, path, errors);
		List<CraftyElementRef> elements = new ArrayList<>();
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			String name = entry.getKey() == null ? null : entry.getKey().toString().trim();
			double weight = number(entry.getValue(), path + "." + name, errors, Double.NaN);
			CraftyElementRef element = construct(path + "." + name, errors,
					() -> new CraftyElementRef(type, name, weight));
			if (element != null) {
				if (type == EffectType.CAPITAL && !name.contains(":")) {
					errors.add(path + "." + name + " must use AFT:capital format");
				}
				elements.add(element);
			}
		}
		if (elements.isEmpty()) {
			errors.add(path + " must contain at least one CRAFTY element");
		}
		return elements;
	}

	private static Map<String, Map<Integer, Double>> parseGoalTrajectories(Object value, String path,
			List<String> errors) {
		if (value == null) {
			return Map.of();
		}
		Map<?, ?> map = map(value, path, errors);
		Map<String, Map<Integer, Double>> result = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			String trajectory = normalize(entry.getKey() == null ? null : entry.getKey().toString(), path, errors);
			Map<?, ?> years = map(entry.getValue(), path + "." + trajectory, errors);
			Map<Integer, Double> values = new LinkedHashMap<>();
			for (Map.Entry<?, ?> yearEntry : years.entrySet()) {
				int year = integer(yearEntry.getKey(), path + "." + trajectory + ".year", errors, -1);
				double goal = number(yearEntry.getValue(), path + "." + trajectory + "." + year, errors,
						Double.NaN);
				if (values.put(year, goal) != null) {
					errors.add(path + "." + trajectory + " duplicates year " + year);
				}
			}
			result.put(trajectory, values);
		}
		return result;
	}

	private static NormalizationType parseNormalization(Object value, String path, List<String> errors) {
		if (value == null) {
			return NormalizationType.RAW;
		}
		return enumValue(value, NormalizationType.class, path, errors);
	}

	private static Path resolveAsset(Path baseDir, String value, String path, List<String> errors) {
		if (value == null) {
			return null;
		}
		Path candidate = Path.of(value);
		Path resolved = candidate.isAbsolute() ? candidate.normalize() : baseDir.resolve(candidate).normalize();
		if (!Files.isRegularFile(resolved)) {
			errors.add(path + " does not reference an existing file: " + resolved);
		}
		return resolved;
	}

	private static Map<?, ?> map(Object value, String path, List<String> errors) {
		if (value instanceof Map<?, ?> map) {
			return map;
		}
		errors.add(path + " must be a map");
		return Map.of();
	}

	private static List<?> list(Object value, String path, List<String> errors) {
		if (value instanceof List<?> list) {
			return list;
		}
		errors.add(path + " must be a list");
		return List.of();
	}

	private static String requiredString(Object value, String path, List<String> errors) {
		if (value != null && !value.toString().isBlank()) {
			return value.toString().trim();
		}
		errors.add(path + " is required and cannot be blank");
		return null;
	}

	private static String optionalString(Object value, String defaultValue) {
		return value == null ? defaultValue : value.toString().trim();
	}

	private static String normalize(String value, String path, List<String> errors) {
		try {
			return Identifiers.normalize(value);
		} catch (IllegalArgumentException e) {
			errors.add(path + ": " + e.getMessage());
			return "invalid";
		}
	}

	private static double number(Object value, String path, List<String> errors, double fallback) {
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		if (value instanceof String string) {
			try {
				return Double.parseDouble(string.trim());
			} catch (NumberFormatException ignored) {
			}
		}
		errors.add(path + " must be numeric");
		return fallback;
	}

	private static int integer(Object value, String path, List<String> errors, int fallback) {
		double parsed = number(value, path, errors, fallback);
		if (!Double.isFinite(parsed) || parsed != Math.rint(parsed)) {
			errors.add(path + " must be an integer");
			return fallback;
		}
		return (int) parsed;
	}

	private static boolean bool(Object value, String path, List<String> errors, boolean fallback) {
		if (value instanceof Boolean bool) {
			return bool;
		}
		if (value instanceof String string && (string.equalsIgnoreCase("true") || string.equalsIgnoreCase("false"))) {
			return Boolean.parseBoolean(string);
		}
		errors.add(path + " must be true or false");
		return fallback;
	}

	private static <E extends Enum<E>> E enumValue(Object value, Class<E> type, String path, List<String> errors) {
		if (value != null) {
			String canonical = value.toString().trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
			try {
				return Enum.valueOf(type, canonical);
			} catch (IllegalArgumentException ignored) {
			}
		}
		errors.add(path + " must be one of " + List.of(type.getEnumConstants()));
		return null;
	}

	private static <T> T construct(String path, List<String> errors, Factory<T> factory) {
		try {
			return factory.create();
		} catch (RuntimeException e) {
			errors.add(path + ": " + e.getMessage());
			return null;
		}
	}

	private static <T> void putUnique(Map<String, T> map, String id, T value, String path, List<String> errors) {
		if (map.putIfAbsent(id, value) != null) {
			errors.add(path + " duplicates normalized identifier '" + id + "'");
		}
	}

	@FunctionalInterface
	private interface Factory<T> {
		T create();
	}
}
