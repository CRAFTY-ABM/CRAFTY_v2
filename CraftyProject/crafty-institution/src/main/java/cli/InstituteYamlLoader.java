package cli;

import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.PathTools;
import large_language_models_institutions.Institute;
import large_language_models_institutions.Paradigm;
import large_language_models_institutions.Policy;
import large_language_models_institutions.Target;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InstituteYamlLoader {

	private InstituteYamlLoader() {
		// Utility class
	}

	private static final CustomLogger LOGGER = new CustomLogger(InstituteYamlLoader.class);
	private static final List<String> VALID_EFFECT_TYPES = List.of("AFT", "Service", "Capital", "External");

	public static List<Institute> loadInstitutes(Path instituteYamlFile, Map<String, Target> availableTargets,
			Paradigm paradigm) {

		Path promptBaseDir = instituteYamlFile.getParent();

		if (promptBaseDir == null) {
			promptBaseDir = Path.of(".");
		}

		try {
			return loadInstitutes(instituteYamlFile, promptBaseDir, availableTargets, paradigm);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static List<Institute> loadInstitutes(Path instituteYamlFile, Path promptBaseDir,
			Map<String, Target> availableTargets, Paradigm paradigm) throws IOException {

		Map<?, ?> root = readYamlRoot(instituteYamlFile);

		Object institutesObj = root.get("institutes");

		if (!(institutesObj instanceof List<?> instituteList)) {
			throw new IllegalArgumentException("Invalid institute YAML: missing or invalid 'institutes' list.");
		}

		List<Institute> institutes = new ArrayList<>();

		for (int i = 0; i < instituteList.size(); i++) {
			Institute institute = parseInstitute(instituteList.get(i), i, promptBaseDir, availableTargets, paradigm);
			institutes.add(institute);
		}

		return institutes;
	}

	public static Map<String, Institute> loadInstitutesAsMap(Path instituteYamlFile,
			Map<String, Target> availableTargets, Paradigm paradigm) throws IOException {

		List<Institute> institutes = loadInstitutes(instituteYamlFile, availableTargets, paradigm);

		Map<String, Institute> result = new LinkedHashMap<>();

		for (Institute institute : institutes) {
			if (result.containsKey(institute.getName())) {
				throw new IllegalArgumentException("Duplicate institute name: " + institute.getName());
			}

			result.put(institute.getName(), institute);
		}

		return result;
	}

	private static Map<?, ?> readYamlRoot(Path yamlFile) throws IOException {
		LoaderOptions options = new LoaderOptions();

		/*
		 * Important: This prevents YAML from silently accepting duplicate keys. For
		 * example, duplicate C3pulses entries would otherwise overwrite each other.
		 */
		options.setAllowDuplicateKeys(false);

		Yaml yaml = new Yaml(new SafeConstructor(options));

		try (InputStream input = Files.newInputStream(yamlFile)) {
			Object loaded = yaml.load(input);

			if (!(loaded instanceof Map<?, ?> root)) {
				throw new IllegalArgumentException("Invalid institute YAML: root element must be a map.");
			}

			return root;
		}
	}

	private static Institute parseInstitute(Object instituteObj, int index, Path promptBaseDir,
			Map<String, Target> availableTargets, Paradigm paradigm) throws IOException {

		if (!(instituteObj instanceof Map<?, ?> map)) {
			throw new IllegalArgumentException(
					"Invalid institute at index " + index + ": each institute must be a map.");
		}

		String name = readRequiredString(map, "name", index);
		String paradigmName = readRequiredStringFlexible(map, index, "paradigm_name", "paradigmName");
		if (!paradigmName.equalsIgnoreCase(paradigm.getName())) {
			LOGGER.error("Paradigm Name are not correct, will be ignourd: paradigme is: " + paradigm.getName());
		}
		int timeLag = readRequiredIntFlexible(map, index, "time_lag", "timeLag");

		int startYear = readRequiredIntFlexible(map, index, "start_year", "startYear");

		int endYear = readRequiredIntFlexible(map, index, "end_year", "endYear", "endyear");

		String promptFile = readRequiredStringFlexible(map, index, "prompt_file", "prompt", "base_prompt_file");

		Path promptPath = resolvePath(promptBaseDir, promptFile);
		String basePrompt = PathTools.readFileToString(promptPath);

		HashMap<String, Target> targets = parseTargetsToObserve(map.get("targets_to_observe"), name, availableTargets);

		HashMap<String, Policy> policies = parsePolicies(map.get("policies"), name,paradigm);

		Institute institute = new Institute(name, paradigm);

		institute.setTimeLag(timeLag);

		if (startYear < Timestep.getStartYear()) {
			startYear = Timestep.getStartYear();
		}
		if (endYear < Timestep.getStartYear() || endYear > Timestep.getEndtYear()) {
			endYear = Timestep.getEndtYear();
		}
		institute.setStartYear(startYear);
		institute.setEndYear(endYear);

		institute.setPolicies(policies);
		institute.setTargets(targets);
		/*
		 * Use the setter name from your Institute class. If your setter is called
		 * setBasePrompts(...), replace this line.
		 */
		institute.setBase_prompts(basePrompt);

		return institute;
	}

	private static HashMap<String, Policy> parsePolicies(Object policiesObj, String instituteName, Paradigm paradigm) {

		if (!(policiesObj instanceof List<?> policyList)) {
			throw new IllegalArgumentException("Institute '" + instituteName + "' must contain a 'policies' list.");
		}

		HashMap<String, Policy> policies = new HashMap<>();

		for (int i = 0; i < policyList.size(); i++) {
			Policy policy = parsePolicy(policyList.get(i), instituteName, i,paradigm);

			if (policies.containsKey(policy.getName())) {
				throw new IllegalArgumentException(
						"Institute '" + instituteName + "' has duplicate policy name: " + policy.getName());
			}

			policies.put(policy.getName().toLowerCase(), policy);
		}

		return policies;
	}

	private static Policy parsePolicy(Object policyObj, String instituteName, int policyIndex, Paradigm paradigm) {

		if (!(policyObj instanceof Map<?, ?> map)) {
			throw new IllegalArgumentException(
					"Institute '" + instituteName + "', policy index " + policyIndex + ": each policy must be a map.");
		}

		String policyName = readRequiredString(map, "name", policyIndex);

		Object effectsObj = map.get("effects");

		if (effectsObj == null) {
			throw new IllegalArgumentException(
					"Policy '" + policyName + "' in institute '" + instituteName + "' must contain an 'effects' map.");
		}

		Map<String, Map<String, Double>> craftyElem = parseEffects(effectsObj, instituteName, policyName);

		Policy policy = new Policy(policyName, paradigm);

		policy.setCraftyElem(craftyElem);

		return policy;
	}

	private static Map<String, Map<String, Double>> parseEffects(Object effectsObj, String instituteName,
			String policyName) {

		if (!(effectsObj instanceof Map<?, ?> rawEffects)) {
			throw new IllegalArgumentException(
					"Policy '" + policyName + "' in institute '" + instituteName + "': 'effects' must be a map.");
		}

		Map<String, Map<String, Double>> effects = new LinkedHashMap<>();

		for (Map.Entry<?, ?> typeEntry : rawEffects.entrySet()) {
			String rawType = toNonEmptyString(typeEntry.getKey(),
					"Policy '" + policyName + "': effect type cannot be empty.");

			String type = canonicalEffectType(rawType, instituteName, policyName);

			Object elementWeightsObj = typeEntry.getValue();

			Map<String, Double> elementWeights = parseElementWeights(elementWeightsObj, instituteName, policyName,
					type);

			effects.put(type, elementWeights);
		}

		if (effects.isEmpty()) {
			throw new IllegalArgumentException("Policy '" + policyName + "' in institute '" + instituteName
					+ "' must contain at least one effect type.");
		}

		return effects;
	}

	private static Map<String, Double> parseElementWeights(Object elementWeightsObj, String instituteName,
			String policyName, String type) {

		if (!(elementWeightsObj instanceof Map<?, ?> rawWeights)) {
			throw new IllegalArgumentException("Policy '" + policyName + "' in institute '" + instituteName
					+ "', type '" + type + "': expected a map of element weights.");
		}

		Map<String, Double> weights = new LinkedHashMap<>();

		for (Map.Entry<?, ?> entry : rawWeights.entrySet()) {
			String elementName = toNonEmptyString(entry.getKey(),
					"Policy '" + policyName + "', type '" + type + "': element name cannot be empty.");

			if ("Capital".equals(type) && !elementName.contains(":")) {
				throw new IllegalArgumentException("Policy '" + policyName + "' in institute '" + instituteName
						+ "': Capital effect key must use 'AFT:capital' format, e.g. \"ExtBF:human\". "
						+ "Invalid key: " + elementName);
			}

			double weight = parseDouble(entry.getValue(),
					"Policy '" + policyName + "', type '" + type + "', element '" + elementName + "'");

			weights.put(elementName, weight);
		}

		if (weights.isEmpty()) {
			throw new IllegalArgumentException("Policy '" + policyName + "' in institute '" + instituteName
					+ "', type '" + type + "' must contain at least one element.");
		}

		return weights;
	}

	private static HashMap<String, Target> parseTargetsToObserve(Object targetsObj, String instituteName,
			Map<String, Target> availableTargets) {

		if (!(targetsObj instanceof List<?> targetNames)) {
			throw new IllegalArgumentException(
					"Institute '" + instituteName + "' must contain a 'targets_to_observe' list.");
		}

		HashMap<String, Target> selectedTargets = new HashMap<>();

		for (Object targetNameObj : targetNames) {
			String targetName = toNonEmptyString(targetNameObj,
					"Institute '" + instituteName + "': target name in 'targets_to_observe' cannot be empty.");

			Target target = availableTargets.get(targetName.toLowerCase());

			if (target == null) {
				throw new IllegalArgumentException("Institute '" + instituteName + "' references unknown target: "
						+ targetName + ". Check that this name exists in the target-set YAML.");
			}

			if (selectedTargets.containsKey(targetName)) {
				throw new IllegalArgumentException(
						"Institute '" + instituteName + "' contains duplicate target reference: " + targetName);
			}

			selectedTargets.put(targetName, target);
		}

		return selectedTargets;
	}

	private static String canonicalEffectType(String rawType, String instituteName, String policyName) {

		for (String validType : VALID_EFFECT_TYPES) {
			if (validType.equalsIgnoreCase(rawType)) {
				return validType;
			}
		}

		throw new IllegalArgumentException("Policy '" + policyName + "' in institute '" + instituteName
				+ "' has invalid effect type: " + rawType + ". Allowed types are: " + VALID_EFFECT_TYPES);
	}

	private static String readRequiredString(Map<?, ?> map, String key, int index) {

		Object value = map.get(key);

		if (value == null) {
			throw new IllegalArgumentException("Missing required field '" + key + "' at index " + index + ".");
		}

		return toNonEmptyString(value, "Field '" + key + "' at index " + index + " cannot be empty.");
	}

	private static String readRequiredStringFlexible(Map<?, ?> map, int index, String... possibleKeys) {

		for (String key : possibleKeys) {
			Object value = map.get(key);

			if (value != null) {
				return toNonEmptyString(value, "Field '" + key + "' at index " + index + " cannot be empty.");
			}
		}

		throw new IllegalArgumentException("Missing required field. Expected one of: " + String.join(", ", possibleKeys)
				+ " at index " + index + ".");
	}

	private static double parseDouble(Object value, String context) {
		if (value instanceof Number number) {
			return number.doubleValue();
		}

		if (value instanceof String str) {
			try {
				return Double.parseDouble(str.trim());
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(context + " must be a valid number, but was: " + str, e);
			}
		}

		throw new IllegalArgumentException(context + " must be numeric, but was: " + value);
	}

	private static String toNonEmptyString(Object value, String errorMessage) {
		if (value == null) {
			throw new IllegalArgumentException(errorMessage);
		}

		String str = value.toString().trim();

		if (str.isEmpty()) {
			throw new IllegalArgumentException(errorMessage);
		}

		return str;
	}

	private static Path resolvePath(Path baseDir, String fileName) {
		Path path = Path.of(fileName);

		if (path.isAbsolute()) {
			return path.normalize();
		}

		return baseDir.resolve(path).normalize();
	}

	private static int readRequiredIntFlexible(Map<?, ?> map, int index, String... possibleKeys) {

		for (String key : possibleKeys) {
			Object value = map.get(key);

			if (value != null) {
				return parseInt(value, "Field '" + key + "' at index " + index);
			}
		}

		throw new IllegalArgumentException("Missing required integer field. Expected one of: "
				+ String.join(", ", possibleKeys) + " at index " + index + ".");
	}

	private static int parseInt(Object value, String context) {
		if (value instanceof Number number) {
			double doubleValue = number.doubleValue();
			int intValue = number.intValue();

			if (doubleValue != intValue) {
				throw new IllegalArgumentException(context + " must be an integer, but was: " + value);
			}

			return intValue;
		}

		if (value instanceof String str) {
			try {
				return Integer.parseInt(str.trim());
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(context + " must be a valid integer, but was: " + str, e);
			}
		}

		throw new IllegalArgumentException(context + " must be an integer, but was: " + value);
	}

}