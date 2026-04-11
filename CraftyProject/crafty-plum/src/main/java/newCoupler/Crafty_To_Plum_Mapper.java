package newCoupler;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.file.PathTools;

public class Crafty_To_Plum_Mapper {

	private static final CustomLogger LOGGER = new CustomLogger(Crafty_To_Plum_Mapper.class);

	// Solver tuning:
	// - increase ALPHA_TEMPORAL for smoother year-to-year commodity trajectories
	// - increase BETA_BASELINE for stronger pull toward baseline in ambiguous cases
	private static final double ALPHA_TEMPORAL = 10.0;
	private static final double BETA_BASELINE = 5.0;
	private static final int MAX_ITERS = 4000;
	private static final double TOL = 1e-8;

	Mapper mapper;
	Plum_Listener pl;
	CraftyListener cl;

	// kept same output containers
	Map<String, Map<String, Double>> comoSupply = new HashMap<>(); // <country,commodity,supply>
	Map<String, Map<String, Double>> InitialComoSupply = new HashMap<>(); // <country,commodity,supply>
	
	Map<String, Double> woodSupply = new HashMap<>(); // <country,supply>

	public Crafty_To_Plum_Mapper(Mapper mapper) {
		this.mapper = mapper;
		this.pl = mapper.plum_listner;
		this.cl = mapper.crafty_listner;
	}

	void setup() {

		supplyComoByCountry();
		step();
	}

	void step() {
		LOGGER.info("Step");
		supplyComoByCountry();
		exportProductionCSV(Timestep.getCurrentYear());
		exportWoodCSV(Timestep.getCurrentYear());
	}

	/**
	 * Replace inverse-based reconstruction by a constrained nonnegative fit:
	 *
	 * min_x ||M x - y||^2 + alpha ||x - x_prev||^2 + beta ||x - x_base||^2 s.t. x
	 * >= 0
	 *
	 * Inputs kept from your current structure: - M =
	 * Plum_To_Crafty_Mapper.matrixByCountry.get(country) - y =
	 * cl.factoredServiceSupplyBycountry.get(country) - xPrev= previous comoSupply
	 * estimate - xBase= pl.comoLeftDemandsByCountry (or current PLUM quantity if
	 * needed)
	 */
	private void supplyComoByCountry() {
		// previous solution is the temporal prior
		Map<String, Map<String, Double>> previousSupply = deepCopy(comoSupply);

		comoSupply.clear();
		woodSupply.clear();

		mapper.countryLongToShortName.keySet().forEach(country -> {

			Map<String, Map<String, Double>> M = Plum_To_Crafty_Mapper.matrixByCountry.getOrDefault(country,
					Collections.emptyMap());
			Map<String, Double> y = cl.factoredServiceSupplyBycountry.getOrDefault(country, Collections.emptyMap());
			Map<String, Double> baseline = pl.comoDemandsByCountry.getOrDefault(country, Collections.emptyMap());

			// Use previous estimated supply if available, otherwise current PLUM
			// quantities, otherwise baseline
			Map<String, Double> prior = previousSupply.get(country);
			if (prior == null || prior.isEmpty()) {
				prior = pl.comoDemandsByCountry.getOrDefault(country, baseline);
			}

			Map<String, Double> solved = ConstrainedCommodityEstimator.solveCountry(M, y, prior, baseline,
					ALPHA_TEMPORAL, BETA_BASELINE, MAX_ITERS, TOL);

			// keep all solved values
			comoSupply.put(country, new HashMap<>(solved));

			// preserve baseline-only commodities not touched by the matrix
			baseline.forEach((como, value) -> comoSupply.get(country).putIfAbsent(como, Math.max(0.0, value)));

			// final nonnegativity clamp
			comoSupply.get(country).replaceAll((como, value) -> Math.max(0.0, value));
		});
	}

	private void exportProductionCSV(int year) {
		Map<String, List<String>> dataInput = new LinkedHashMap<>();
		dataInput.put("Country", new ArrayList<>());
		dataInput.put("Crop", new ArrayList<>());
		dataInput.put("Production", new ArrayList<>());
		dataInput.put("NetImportsExpected", new ArrayList<>());
		dataInput.put("MonogastricFeed", new ArrayList<>());
		dataInput.put("RuminantFeed", new ArrayList<>());

		woodSupply.clear();

		comoSupply.forEach((country, hash) -> {
			hash.forEach((como, supply) -> {
				if (!"wood".equals(como)) {

					double imp = pl.initialImport.getOrDefault(country, Collections.emptyMap()).getOrDefault(como, 0.0);
					imp = (supply + imp > 0.0) ? imp : 0.0;

					dataInput.get("NetImportsExpected").add(String.valueOf(imp));
					dataInput.get("Country").add(country);
					dataInput.get("Crop").add(como);
					dataInput.get("Production").add(String.valueOf(supply));

					double monoFactor = pl.monFactor.getOrDefault(country, Collections.emptyMap()).getOrDefault(como,
							0.0);

					double rumFactor = pl.rumFactor.getOrDefault(country, Collections.emptyMap()).getOrDefault(como,
							0.0);

					double mono = (supply + imp) * monoFactor;
					double rum = (supply + imp) * rumFactor;

					// keep your same style, but with safer renormalization if feed exceeds total
					double total = supply + imp;
					if (mono + rum > total && (mono + rum) > 0.0) {
						double scale = total / (mono + rum);
						mono *= scale;
						rum *= scale;
					}

					dataInput.get("MonogastricFeed").add(String.valueOf(mono));
					dataInput.get("RuminantFeed").add(String.valueOf(rum));

				} else {
					woodSupply.put(country, supply);
				}
			});
		});

		String dir = PathTools.makeDirectory(ConfigLoader.config.plumOutPutPath + File.separator + "crafty");
		dir = PathTools.makeDirectory(dir + File.separator + year);
		CsvTools.writeCSVfileString(dataInput, Paths.get(dir + File.separator + "production.csv"));
		PathTools.writeFile(dir + File.separator + "done", "", false);
	}

	private void exportWoodCSV(int year) {
		Map<String, List<String>> dataInput = new LinkedHashMap<>();
		dataInput.put("Country", new ArrayList<>());
		dataInput.put("RoundwoodDemand", new ArrayList<>());
		dataInput.put("WoodfuelDemand", new ArrayList<>());
		dataInput.put("Production", new ArrayList<>());
		dataInput.put("Net_imports", new ArrayList<>());

		woodSupply.forEach((country, supply) -> {
			double round = pl.roundwood.getOrDefault(country, 0.0);
			double fuel = pl.woodfuel.getOrDefault(country, 0.0);

			dataInput.get("Country").add(country);
			dataInput.get("RoundwoodDemand").add(String.valueOf(supply * round));
			dataInput.get("WoodfuelDemand").add(String.valueOf(supply * fuel));
			dataInput.get("Production").add(String.valueOf(supply));
			dataInput.get("Net_imports").add("0");
		});

		CsvTools.writeCSVfileString(dataInput, Paths.get(ConfigLoader.config.plumOutPutPath
				+ PathTools.asFolder("crafty") + year + File.separator + "wood.csv"));
	}

	private static Map<String, Map<String, Double>> deepCopy(Map<String, Map<String, Double>> in) {
		Map<String, Map<String, Double>> out = new HashMap<>();
		if (in == null) {
			return out;
		}
		in.forEach((k, v) -> out.put(k, v == null ? new HashMap<>() : new HashMap<>(v)));
		return out;
	}
}