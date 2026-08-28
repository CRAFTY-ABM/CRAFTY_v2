package de.cesr.crafty.core.utils.graphics;

import de.cesr.crafty.core.crafty.Cell;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

public final class MapPngSpecHandler {

	public enum ValueType {
		OWNER, SERVICE, CAPITAL, BEHAVIOUR, SERVICE_TAXES_SUBSIDIES, lAND_TAXES_SUBSIDIES, COMPETITIVENESS,
		OWNERS_LIFE_COUNTER
	}

	/**
	 * This keeps the parser independent from the exact CRAFTY data structure. You
	 * only need to tell it how to get service/capital/tax values from a Cell.
	 */
	public interface Registry {

		Function<Cell, String> ownerExtractor();
		
		List<String> serviceNames();

		List<String> capitalNames();

		List<String> behaviourParameterNames();

		ToDoubleFunction<Cell> serviceExtractor(String serviceName);

		ToDoubleFunction<Cell> capitalExtractor(String capitalName);

		ToDoubleFunction<Cell> behaviourParameterExtractor(String parameterName);

		ToDoubleFunction<Cell> serviceTaxExtractor(String serviceName);

		ToDoubleFunction<Cell> landTaxExtractor();

		ToDoubleFunction<Cell> competitivnessExtractor();

		ToDoubleFunction<Cell> ownerLifeCounterExtractor();
	}

	public static final class MapValueSpec {
		public final ValueType type;
		public final String name;
		public final String raw;

		private MapValueSpec(ValueType type, String name, String raw) {
			this.type = type;
			this.name = name;
			this.raw = raw;
		}

		public boolean isAll() {
			return name != null && "ALL".equalsIgnoreCase(name);
		}

		public String label() {
			if (type == ValueType.OWNER) {
				return "owner";
			}
			if (type == ValueType.lAND_TAXES_SUBSIDIES) {
				return "land_taxes_subsidies";
			}
			if (type == ValueType.COMPETITIVENESS) {
				return "competitiveness";
			}
			if (type == ValueType.OWNERS_LIFE_COUNTER) {
				return "owners_life_counter";
			}
			return type.name().toLowerCase(Locale.ROOT) + ":" + name;
		}

		public String outputToken() {
			return safeFileToken(label());
		}

		@Override
		public String toString() {
			return label();
		}
	}

	public static final class MapValue {
		public final MapValueSpec spec;
		public final ToDoubleFunction<Cell> doubleExtractor;
		public final Function<Cell, String> stringExtractor;
		public final String label;
		public final String outputToken;

		private MapValue(
				MapValueSpec spec,
				ToDoubleFunction<Cell> doubleExtractor,
				Function<Cell, String> stringExtractor
		) {
			this.spec = spec;
			this.doubleExtractor = doubleExtractor;
			this.stringExtractor = stringExtractor;
			this.label = spec.label();
			this.outputToken = spec.outputToken();
		}

		public static MapValue numeric(MapValueSpec spec, ToDoubleFunction<Cell> extractor) {
			return new MapValue(spec, extractor, null);
		}

		public static MapValue text(MapValueSpec spec, Function<Cell, String> extractor) {
			return new MapValue(spec, null, extractor);
		}

		public boolean isNumeric() {
			return doubleExtractor != null;
		}

		public boolean isText() {
			return stringExtractor != null;
		}
	}

	public static final class SingleMapRequest {
		public final MapValue value;
		public final String outputName;

		private SingleMapRequest(MapValue value) {
			this.value = value;
			this.outputName = value.outputToken;
		}
	}

	public static final class DualMapRequest {
		public final MapValue value1;
		public final MapValue value2;
		public final String outputName;

		private DualMapRequest(MapValue value1, MapValue value2) {
			this.value1 = value1;
			this.value2 = value2;
			this.outputName = value1.outputToken + "__" + value2.outputToken;
		}
	}

	public static final class TripleMapRequest {
		public final MapValue red;
		public final MapValue green;
		public final MapValue blue;
		public final String outputName;

		private TripleMapRequest(MapValue red, MapValue green, MapValue blue) {
			this.red = red;
			this.green = green;
			this.blue = blue;
			this.outputName = red.outputToken + "__" + green.outputToken + "__" + blue.outputToken;
		}
	}

	private final Registry registry;

	public MapPngSpecHandler(Registry registry) {
		this.registry = Objects.requireNonNull(registry, "registry must not be null");
	}

	// ---------------------------------------------------------------------
	// Public API
	// ---------------------------------------------------------------------

	public List<SingleMapRequest> buildSingleRequests(List<String> rawSingles) {
		List<SingleMapRequest> result = new ArrayList<>();

		if (rawSingles == null) {
			return result;
		}

		for (String raw : rawSingles) {
			MapValueSpec spec = parse(raw);

			if (spec.isAll()) {
				for (MapValueSpec expanded : expandAll(spec)) {
					result.add(new SingleMapRequest(resolve(expanded)));
				}
			} else {
				result.add(new SingleMapRequest(resolve(spec)));
			}
		}

		return result;
	}

	public List<DualMapRequest> buildDualRequests(List<List<String>> rawDuals) {
		List<DualMapRequest> result = new ArrayList<>();

		if (rawDuals == null) {
			return result;
		}

		for (List<String> rawPair : rawDuals) {
			if (rawPair == null || rawPair.size() != 2) {
				throw new IllegalArgumentException(
						"Each map_png.dual entry must contain exactly 2 values. Found: " + rawPair);
			}

			MapValueSpec s1 = parse(rawPair.get(0));
			MapValueSpec s2 = parse(rawPair.get(1));

			rejectAllOutsideSingle(s1, "dual");
			rejectAllOutsideSingle(s2, "dual");

			result.add(new DualMapRequest(resolve(s1), resolve(s2)));
		}

		return result;
	}

	public List<TripleMapRequest> buildTripleRequests(List<List<String>> rawTriples) {
		List<TripleMapRequest> result = new ArrayList<>();

		if (rawTriples == null) {
			return result;
		}

		for (List<String> rawTriple : rawTriples) {
			if (rawTriple == null || rawTriple.size() != 3) {
				throw new IllegalArgumentException(
						"Each map_png.triple entry must contain exactly 3 values. Found: " + rawTriple);
			}

			MapValueSpec red = parse(rawTriple.get(0));
			MapValueSpec green = parse(rawTriple.get(1));
			MapValueSpec blue = parse(rawTriple.get(2));

			rejectAllOutsideSingle(red, "triple");
			rejectAllOutsideSingle(green, "triple");
			rejectAllOutsideSingle(blue, "triple");

			result.add(new TripleMapRequest(resolve(red), resolve(green), resolve(blue)));
		}

		return result;
	}

	// ---------------------------------------------------------------------
	// Parsing
	// ---------------------------------------------------------------------

	public static MapValueSpec parse(String raw) {
		if (raw == null || raw.trim().isEmpty()) {
			throw new IllegalArgumentException("Empty map value specification.");
		}

		String s = raw.trim();

		if (s.equalsIgnoreCase("land_taxes_subsidies")) {
			return new MapValueSpec(ValueType.lAND_TAXES_SUBSIDIES, null, raw);
		}
		if (s.equalsIgnoreCase("owner")) {
			return new MapValueSpec(ValueType.OWNER, null, raw);
		}
		if (s.equalsIgnoreCase("COMPETITIVENESS")) {
			return new MapValueSpec(ValueType.COMPETITIVENESS, null, raw);
		}
		if (s.equalsIgnoreCase("OWNERS_LIFE_COUNTER")) {
			return new MapValueSpec(ValueType.OWNERS_LIFE_COUNTER, null, raw);
		}

		String[] parts = s.split(":", 2);

		if (parts.length != 2) {
			throw new IllegalArgumentException("Invalid map value specification: '" + raw + "'. "
					+ "Expected for example: service:s1, capital:c1, behaviour:Weight_inertia, "
					+ "service_tax:s1, or land_tax.");
		}

		String typeRaw = parts[0].trim().toLowerCase(Locale.ROOT);
		String name = parts[1].trim();

		if (name.isEmpty()) {
			throw new IllegalArgumentException("Missing name in map value specification: '" + raw + "'.");
		}

		ValueType type = parseType(typeRaw);

		if (type == ValueType.lAND_TAXES_SUBSIDIES) {
			throw new IllegalArgumentException("land_tax does not take a name. Use 'land_tax', not '" + raw + "'.");
		}

		return new MapValueSpec(type, name, raw);
	}

	private static ValueType parseType(String typeRaw) {
		switch (typeRaw) {
		case "service":
		case "services":
			return ValueType.SERVICE;

		case "capital":
		case "capitals":
			return ValueType.CAPITAL;

		case "behaviour":
		case "behavior":
		case "beheviour":
		case "behevoir":
			return ValueType.BEHAVIOUR;

		case "service_taxes_subsidies":
		case "service_tax_subsidy":
		case "services_tax_subsidy":
		case "services_taxes_subsidies":
			return ValueType.SERVICE_TAXES_SUBSIDIES;
		case "owner":
		case "agents":
		case "agent":
		case "aft":
			return ValueType.OWNER;

		case "land_tax_subsidy":
		case "land_taxes_subsidies":
			return ValueType.lAND_TAXES_SUBSIDIES;
		case "competitiveness":
			return ValueType.COMPETITIVENESS;
		case "owners_life_counter":
			return ValueType.OWNERS_LIFE_COUNTER;

		default:
			throw new IllegalArgumentException("Unknown map value type: '" + typeRaw + "'. "
					+ "Supported types: service, capital, behaviour, service_tax, land_tax.");
		}
	}

	// ---------------------------------------------------------------------
	// ALL expansion
	// ---------------------------------------------------------------------

	private List<MapValueSpec> expandAll(MapValueSpec spec) {
		if (!spec.isAll()) {
			List<MapValueSpec> one = new ArrayList<>();
			one.add(spec);
			return one;
		}

		if (spec.type == ValueType.lAND_TAXES_SUBSIDIES) {
			throw new IllegalArgumentException("ALL is not supported for land_tax.");
		}

		List<String> names = namesFor(spec.type);
		List<MapValueSpec> expanded = new ArrayList<>();

		for (String name : names) {
			if (name == null || name.trim().isEmpty()) {
				continue;
			}
			expanded.add(new MapValueSpec(spec.type, name.trim(), spec.raw));
		}

		return expanded;
	}

	private List<String> namesFor(ValueType type) {
		switch (type) {
		case SERVICE:
			return nullSafeList(registry.serviceNames());

		case CAPITAL:
			return nullSafeList(registry.capitalNames());

		case BEHAVIOUR:
			return nullSafeList(registry.behaviourParameterNames());

		case SERVICE_TAXES_SUBSIDIES:
			return nullSafeList(registry.serviceNames());

		default:
			throw new IllegalArgumentException("Cannot expand ALL for type: " + type);
		}
	}

	private static List<String> nullSafeList(List<String> list) {
		return list == null ? List.of() : list;
	}

	private static void rejectAllOutsideSingle(MapValueSpec spec, String section) {
		if (spec.isAll()) {
			throw new IllegalArgumentException(
					"ALL is allowed only in map_png.single. Invalid entry in map_png." + section + ": " + spec.raw);
		}
	}

	// ---------------------------------------------------------------------
	// Resolve spec to ToDoubleFunction<Cell>
	// ---------------------------------------------------------------------

	private MapValue resolve(MapValueSpec spec) {

		if (spec.type == ValueType.OWNER) {
			Function<Cell, String> extractor = registry.ownerExtractor();

			if (extractor == null) {
				throw new IllegalArgumentException("No extractor found for map value: " + spec);
			}

			return MapValue.text(spec, extractor);
		}

		ToDoubleFunction<Cell> extractor;

		switch (spec.type) {
		case SERVICE:
			extractor = registry.serviceExtractor(spec.name);
			break;

		case CAPITAL:
			extractor = registry.capitalExtractor(spec.name);
			break;

		case BEHAVIOUR:
			extractor = registry.behaviourParameterExtractor(spec.name);
			break;

		case SERVICE_TAXES_SUBSIDIES:
			extractor = registry.serviceTaxExtractor(spec.name);
			break;

		case lAND_TAXES_SUBSIDIES:
			extractor = registry.landTaxExtractor();
			break;

		case COMPETITIVENESS:
			extractor = registry.competitivnessExtractor();
			break;

		case OWNERS_LIFE_COUNTER:
			extractor = registry.ownerLifeCounterExtractor();
			break;

		default:
			throw new IllegalArgumentException("Unsupported value type: " + spec.type);
		}

		if (extractor == null) {
			throw new IllegalArgumentException("No extractor found for map value: " + spec);
		}

		return MapValue.numeric(spec, extractor);
	}

	private static String safeFileToken(String s) {
		return s.trim().toLowerCase(Locale.ROOT).replace(":", "_").replace("/", "_").replace("\\", "_")
				.replace(" ", "_").replace("[", "").replace("]", "").replace("(", "").replace(")", "").replace(",", "_")
				.replace("__", "_");
	}
}
