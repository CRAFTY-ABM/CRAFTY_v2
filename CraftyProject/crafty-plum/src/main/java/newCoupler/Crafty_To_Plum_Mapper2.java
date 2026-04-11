package newCoupler;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.file.PathTools;

public class Crafty_To_Plum_Mapper2 {
	Mapper mapper;
	Plum_Listener pl;
	CraftyListener cl;
	Map<String, Map<String, Double>> inversMatrixAgg = new HashMap<>();// <comodity,service,weight>
	Map<String, Map<String, Map<String, Double>>> inversMatrixByCountry = new HashMap<>();// <country,comodity,service,weight>
	Map<String, Map<String, Double>> comoSupply = new HashMap<>(); // <country,comodity,supply>
	Map<String, Double> woodSupply = new HashMap<>(); // <country,supply>

	public Crafty_To_Plum_Mapper2(Mapper mapper) {
		this.mapper = mapper;
		this.pl = mapper.plum_listner;
		this.cl = mapper.crafty_listner;
	}

	void setup() {
		setupInvers();
		supplyComoByCountry();
		step();
	}

	void step() {
		supplyComoByCountry();
		exportProductionCSV(Timestep.getCurrentYear());
		exportWoodCSV(Timestep.getCurrentYear());
	}

	private void supplyComoByCountry() {
		comoSupply.clear();
		mapper.countryLongToShortName.keySet().forEach(country -> {
			inversMatrixByCountry.get(country).forEach((como, map) -> {
				comoSupply.putIfAbsent(country, new HashMap<>());
				map.forEach((service, weight) -> {
					Double sVal = cl.factoredServiceSupplyBycountry.get(country).get(service);
					if (sVal != null) {
						comoSupply.get(country).merge(como, sVal * weight, Double::sum);
					}
				});
			});
		});

		pl.comoLeftDemandsByCountry.forEach((country, hash) -> {
			hash.forEach((como, d) -> {
				comoSupply.get(country).putIfAbsent(como, d);
			});
		});
		comoSupply.forEach((country, hash) -> {
			hash.forEach((como, supply) -> {
				hash.put(como, Math.max(supply, 0));
			});
		});
	}

	private void setupInvers() {
		mapper.countryShortToLongName.values().forEach(country -> {
			inversMatrixByCountry.put(country, LeftInverse.leftInverseRidge(country,
					Plum_To_Crafty_Mapper.matrixByCountry.get(country), pl.comoDemandsByCountry.get(country)));
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

		comoSupply.forEach((country, hash) -> {
			hash.forEach((como, supply) -> {
				if (!como.equals("wood")) {
					double imp = pl.initialImport.get(country).get(como);
					imp = supply + imp > 0 ? imp : 0;
					dataInput.get("NetImportsExpected").add(String.valueOf(imp));
					dataInput.get("Country").add(country);
					dataInput.get("Crop").add(como);
					dataInput.get("Production").add(String.valueOf(supply));
					double mono = (supply + imp) * pl.monFactor.get(country).get(como);
					double rum = (supply + imp) * pl.rumFactor.get(country).get(como);
					double denominator = mono + rum;
					if (supply + imp - mono - rum < 0) {
						mono = mono / denominator;
						rum = rum / denominator;
					}

					dataInput.get("MonogastricFeed").add(String.valueOf(mono));
					dataInput.get("RuminantFeed").add(String.valueOf(rum));
				} else {
					woodSupply.put(country, supply);
				}
			});
		});

		// make a directory
		// create csv fine then done file
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
			dataInput.get("Country").add(country);
			dataInput.get("RoundwoodDemand").add(String.valueOf(supply * pl.roundwood.get(country)));
			dataInput.get("WoodfuelDemand").add(String.valueOf(supply * pl.woodfuel.get(country)));
			dataInput.get("Production").add(String.valueOf(supply));
			dataInput.get("Net_imports").add("0");
		});
		CsvTools.writeCSVfileString(dataInput, Paths.get(ConfigLoader.config.plumOutPutPath
				+ PathTools.asFolder("crafty") + year + File.separator + "wood.csv"));
	}
}
