package newCoupler;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.RegionsModelRunnerUpdater;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.file.PathTools;

public class CraftyListener {
	Mapper mapper;

	Map<String, Map<String, Double>> serviceSupplyBycountry = new ConcurrentHashMap<>();// <country,service,supply_value>
	Map<String, Map<String, Double>> factoredServiceSupplyBycountry = new ConcurrentHashMap<>();// <country,service,supply_value>
	Map<String, Map<String, Double>> initialServiceDemandsByCountry = new HashMap<>();// <country,service,demand>
	Map<String, Map<String, Double>> factors = new HashMap<>(); // <country,service,supply_factor>
	// listners
	Map<String, List<Double>> factoredSuppListener = new LinkedHashMap<>();
	Map<String, List<String>> factorsListener = new LinkedHashMap<>();
//	Map<String, List<Double>> dm_serviceListener = new LinkedHashMap<>();

	public CraftyListener(Mapper mapper) {
		this.mapper = mapper;
	}

	void setup() {
		getInitialServiceDemands();
		computeSupplyFactors();
		getFactoredSupply();
		String dir = PathTools.makeDirectory(ConfigLoader.config.output_folder_name + File.separator + "plum");
		CsvTools.writeCSVfileString(factorsListener, Paths.get(dir + File.separator + "factorsCountries.csv"));
	}

	void step() {
		servicesSupplyBycountry();
		getFactoredSupply();
		exportCSVs();
	}

	private void getFactoredSupply() {
		factoredServiceSupplyBycountry.clear();
		serviceSupplyBycountry.forEach((country, hash) -> {
			factoredServiceSupplyBycountry.put(country, new HashMap<>());
			hash.forEach((service, supply) -> {
				Double f = factors.get(country).get(service);
				f = f == null ? 0 : f;
				double fact = supply / f;
				fact = Double.isInfinite(fact) || Double.isNaN(fact) ? 0 : fact;
				factoredServiceSupplyBycountry.get(country).put(service, fact);
			});
		});
		factoredSuppListener.putIfAbsent("Year", new ArrayList<>());
		factoredSuppListener.get("Year").add((double) Timestep.getCurrentYear());
		HashMap<String, Double> tmp = new HashMap<>();
		factoredServiceSupplyBycountry.forEach((country, hash) -> {
			hash.forEach((service, supply) -> {
				factoredSuppListener.putIfAbsent(service, new ArrayList<>());
				tmp.merge(service, supply, Double::sum);
			});
		});
		tmp.forEach((service, totalSupp) -> factoredSuppListener.get(service).add(totalSupp));
	}

	private void computeSupplyFactors() {
//		initial supply (without factors)
		RegionsModelRunnerUpdater.regionsModelRunner.values().forEach(RegionalRunner -> {
			RegionalRunner.regionalSupply();
		});
		servicesSupplyBycountry();
		factorsListener.put("Country", new ArrayList<>());

// Compute factors 
		initialServiceDemandsByCountry.forEach((country, hash) -> {
			factors.putIfAbsent(country, new HashMap<>());
			factorsListener.get("Country").add(country);
			hash.forEach((service, demand) -> {
				if (mapper.countryLongToShortName.containsKey(country)) {
					double f = serviceSupplyBycountry.get(country).get(service) / demand;
					f = (Double.isNaN(f) || Double.isInfinite(f)) ? 0 : f;
					factors.get(country).put(service, f);
					factorsListener.putIfAbsent(service, new ArrayList<>());
					factorsListener.get(service).add(String.valueOf(f));
				}
			});
		});
	}

	private void getInitialServiceDemands() {
//		coupute initial service demands
		Plum_To_Crafty_Mapper.matrixByCountry.forEach((country, hash) -> {
			initialServiceDemandsByCountry.putIfAbsent(country, new HashMap<>());
			hash.forEach((service, ha) -> {
				ha.forEach((como, weight) -> {
					double comoV = mapper.plum_listner.comoDemandsByCountry.get(country).getOrDefault(como, 0.);
					initialServiceDemandsByCountry.get(country).merge(service, comoV * weight, Double::sum);
				});
			});
		});
	}

	private void servicesSupplyBycountry() {
		serviceSupplyBycountry.clear();
		mapper.countryShortToLongName.forEach((cou, country) -> {
			serviceSupplyBycountry.put(country, new HashMap<>());
		});

		CellsLoader.hashCell.values().forEach(c -> {
			if (c.getCurrentRegion() != null) {
				ServiceSet.getServicesList().forEach(service -> {
					serviceSupplyBycountry.get(mapper.countryShortToLongName.get(c.getCurrentRegion())).merge(service,
							c.getCurrentProd()[ServiceSet.getServicesList().indexOf(service)], Double::sum);
				});
			}
		});
	}

	private void exportCSVs() {
		String dir = PathTools.makeDirectory(ConfigLoader.config.output_folder_name + File.separator + "plum");
		CsvTools.writeCSVfile(factoredSuppListener, Paths.get(dir + File.separator + "factoredSupply.csv"));
		CsvTools.writeCSVfile(Plum_To_Crafty_Mapper.servicePriceListener,
				Paths.get(dir + File.separator + "servicePrices.csv"));
		CsvTools.writeCSVfile(Plum_To_Crafty_Mapper.serviceDMListener,
				Paths.get(dir + File.separator + "serviceDemands.csv"));
	}
}
