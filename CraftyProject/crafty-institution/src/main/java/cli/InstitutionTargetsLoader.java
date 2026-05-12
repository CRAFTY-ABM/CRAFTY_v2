package cli;

import org.yaml.snakeyaml.Yaml;

import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import large_language_models_institutions.Target;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class InstitutionTargetsLoader {

	private static final CustomLogger LOGGER = new CustomLogger(InstitutionTargetsLoader.class);
	private static final Set<String> VALID_TYPES = Set.of("AFT", "Service", "External");

	private InstitutionTargetsLoader() {
		// Utility class
	}

	public static List<Target> loadTargets(Path yamlFile) throws IOException {
		Yaml yaml = new Yaml();

		try (InputStream input = Files.newInputStream(yamlFile)) {
			Object loaded = yaml.load(input);

			if (!(loaded instanceof Map<?, ?> root)) {
				throw new IllegalArgumentException("Invalid targets YAML: root element must be a map.");
			}

			Object targetsObj = root.get("targets");

			if (!(targetsObj instanceof List<?> targetList)) {
				throw new IllegalArgumentException("Invalid targets YAML: missing or invalid 'targets' list.");
			}

			List<Target> targets = new ArrayList<>();

			for (int i = 0; i < targetList.size(); i++) {
				Object targetObj = targetList.get(i);
				targets.add(parseTarget(targetObj, i));
			}

			return targets;
		}
	}

	public static Map<String, Target> loadTargetsAsMap(Path yamlFile) {
		List<Target> targets;
		Map<String, Target> result = new LinkedHashMap<>();
		try {
			targets = loadTargets(yamlFile);
			for (Target target : targets) {
				if (result.containsKey(target.getName())) {
					throw new IllegalArgumentException("Duplicate target name in YAML: " + target.getName());
				}
				result.put(target.getName(), target);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return result;
	}

	private static Target parseTarget(Object targetObj, int index) {
		if (!(targetObj instanceof Map<?, ?> map)) {
			throw new IllegalArgumentException("Invalid target at index " + index + ": each target must be a map.");
		}

		String name = readRequiredString(map, "name", index).trim();
		String type = readRequiredString(map, "type", index).trim();

		if (!VALID_TYPES.contains(type)) {
			throw new IllegalArgumentException(
					"Invalid target type for target '" + name + "': " + type + ". Allowed types are: " + VALID_TYPES);
		}

		Object weightsObj = map.get("crafty_elements");

		if (weightsObj == null) {
			throw new IllegalArgumentException("Target '" + name + "' must contain a 'crafty_elements' map.");
		}

		Map<String, Double> craftyElements = parseWeights(weightsObj, name, type);

		Target target = new Target(name);
		target.setType(type);
		target.setCraftyElem(craftyElements);
		return target;
	}

	private static String readRequiredString(Map<?, ?> map, String key, int index) {
		Object value = map.get(key);

		if (value == null) {
			throw new IllegalArgumentException(
					"Invalid target at index " + index + ": missing required field '" + key + "'.");
		}

		if (!(value instanceof String str)) {
			throw new IllegalArgumentException(
					"Invalid target at index " + index + ": field '" + key + "' must be a string.");
		}

		if (str.trim().isEmpty()) {
			throw new IllegalArgumentException(
					"Invalid target at index " + index + ": field '" + key + "' cannot be empty.");
		}

		return str;
	}

	private static Map<String, Double> parseWeights(Object weightsObj, String targetName, String type) {
		if (!(weightsObj instanceof Map<?, ?> rawWeights)) {
			throw new IllegalArgumentException("Target '" + targetName + "': 'crafty_elements' must be a map.");
		}

		Map<String, Double> weights = new LinkedHashMap<>();

		for (Map.Entry<?, ?> entry : rawWeights.entrySet()) {
			Object keyObj = entry.getKey();
			Object valueObj = entry.getValue();

			if (keyObj == null) {
				throw new IllegalArgumentException("Target '" + targetName + "' contains a null CRAFTY element name.");
			}

			String craftyElement = keyObj.toString().trim();

			if (craftyElement.isEmpty()) {
				throw new IllegalArgumentException(
						"Target '" + targetName + "' contains an empty CRAFTY element name.");
			}

			double weight = parseDouble(valueObj, targetName, craftyElement);

			boolean tmp = true;
			if (type.equalsIgnoreCase("AFT")) {
				tmp = AFTsLoader.getAftHash().containsKey(craftyElement);
			} else if (type.equalsIgnoreCase("Service")) {
				tmp = ServiceSet.getServicesList().contains(craftyElement);
			}

			if (tmp) {
				weights.put(craftyElement, weight);
			} else {
				LOGGER.error("Error in Target configuration "+craftyElement +" is not found in list of  "+ type +"s");
			}

		}

		if (weights.isEmpty()) {
			throw new IllegalArgumentException("Target '" + targetName + "' must contain at least one weight.");
		}

		return weights;
	}

	private static double parseDouble(Object valueObj, String targetName, String craftyElement) {
		if (valueObj == null) {
			throw new IllegalArgumentException(
					"Target '" + targetName + "', element '" + craftyElement + "': weight is null.");
		}

		if (valueObj instanceof Number number) {
			return number.doubleValue();
		}

		if (valueObj instanceof String str) {
			try {
				return Double.parseDouble(str.trim());
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Target '" + targetName + "', element '" + craftyElement
						+ "': weight is not a valid number: " + str, e);
			}
		}

		throw new IllegalArgumentException(
				"Target '" + targetName + "', element '" + craftyElement + "': weight must be numeric.");
	}
}