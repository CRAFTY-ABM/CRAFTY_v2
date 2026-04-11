package newCoupler;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.RegionsModelRunnerUpdater;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;

public class Plum_To_Crafty_Mapper {
	private static final CustomLogger LOGGER = new CustomLogger(Plum_To_Crafty_Mapper.class);
	Mapper mapper;
	Plum_Listener pl;
	static Map<String, Map<String, Map<String, Double>>> matrixByCountry = new HashMap<>();// <country,service,como,weight>
	static Map<String, Map<String, Double>> matrixAgg = new HashMap<>();// <service,comodity,weight>

	static Map<String, List<Double>> servicePriceListener = new LinkedHashMap<>();
	static Map<String, List<Double>> serviceDMListener = new LinkedHashMap<>();
	Map<String, Map<String, Double>> serviceDM_ByCountry = new ConcurrentHashMap<>();// <country,service,supply_value>

	public Plum_To_Crafty_Mapper(Mapper mapper) {
		this.mapper = mapper;
		this.pl = mapper.plum_listner;
	}

	public void setup() {
		setupMatrixBycountry();
		setupMatrixAgg();
	}

	public void step() {
//		correctFactors();
		injectServicePrices(Timestep.getCurrentYear());
		exportCSVs();
	}

	private void injectServicePrices(int year) {
		servicePriceListener.putIfAbsent("Year", new ArrayList<>());
		servicePriceListener.get("Year").add((double) year);

		RegionsModelRunnerUpdater.regionsModelRunner.values().forEach(r -> {
			r.R.getServicesHash().forEach((serviceName, service) -> {
				double v = getComoToServiceMapper(serviceName, pl.commoditiesPrice);
				service.getWeights().put(year, v);
				servicePriceListener.putIfAbsent(serviceName, new ArrayList<>());
				servicePriceListener.get(serviceName).add(v);
			});
		});
	}

	double getComoToServiceMapper(String serviceName, Map<String, Double> comoValue) {
		double[] out = { 0.0 };
		matrixAgg.get(serviceName).forEach((como, weight) -> {
			out[0] += comoValue.get(como) * weight;
		});
		return out[0];
	}

	private void mapComoToserviceDM() {
		serviceDMListener.putIfAbsent("Year", new ArrayList<>());
		serviceDMListener.get("Year").add((double) Timestep.getCurrentYear());

		ServiceSet.getServicesList().forEach(service -> {
			serviceDMListener.putIfAbsent(service, new ArrayList<>());
			serviceDMListener.get(service).add(getComoToServiceMapper(service, pl.comoDemandsAgg));
		});
	}

	private void setupMatrixBycountry() {
		Map<String, Map<String, Double>> matrixBase = setupBaseMatrix();
		pl.getbioFractionBycountry();
		pl.getFoddercropsFraction();
		mapper.countryLongToShortName.keySet().forEach(country -> {
			matrixByCountry.put(country, new HashMap<>());
		});

		matrixByCountry.forEach((country, hash) -> {
			matrixBase.forEach((service, h) -> {
				if (service.equals("BioenergyG1")) {
					hash.put(service, pl.bioFractionByCountry.get(country));
				} else if (service.equals("Foddercrops")) {
					hash.put(service, pl.foddercropsFractionByCountry.get(country));
				} else {
					Map<String, Double> tmp = new HashMap<>();
					h.forEach((como, weight) -> {
						// max(1-bio+fo,0)
						double bio = pl.bioFractionByCountry.get(country).get(como) != null
								? pl.bioFractionByCountry.get(country).get(como)
								: 0;
						double fo = pl.foddercropsFractionByCountry.get(country).get(como) != null
								? pl.foddercropsFractionByCountry.get(country).get(como)
								: 0;
						if (fo + bio > 1) {
							fo = fo / (fo + bio);
							bio = bio / (fo + bio);
						}
						tmp.put(como, Math.max(weight - bio - fo, 0));
					});
					hash.put(service, tmp);
				}
			});
		});
	}

	private void setupMatrixAgg() {
		ServiceSet.getServicesList().forEach(service -> {
			matrixAgg.put(service, new HashMap<>());
		});
		matrixByCountry.forEach((country, hash) -> {
			hash.forEach((service, ha) -> {
				ha.forEach((como, weight) -> {
					if (pl.comoSet.contains(como)) {
						matrixAgg.get(service).merge(como, weight / matrixByCountry.size(), Double::sum);
					}
				});
			});
		});
	}

	private Map<String, Map<String, Double>> setupBaseMatrix() {
		Map<String, Map<String, Double>> matrixBase = new HashMap<>();// <service,comodity,weight>
		Path path = Paths.get(ConfigLoader.config.services_commodities_map);
		if (!path.toFile().isFile()) {
			LOGGER.fatal("ConfigLoader.config.services_commodities_map is not a file");
		}
		String[][] csv = CsvTools.csvReader(path);
		for (int i = 1; i < csv.length; i++) {
			matrixBase.put(csv[i][0], new HashMap<>());
			for (int j = 1; j < csv[0].length; j++) {
				pl.comoSet.add(csv[0][j]);
				matrixBase.get(csv[i][0]).put(csv[0][j], Utils.sToD(csv[i][j]));
			}
		}
		return matrixBase;
	}

	private void correctFactors() {
		if (Timestep.getTick() == 0) {
			getServiceDM();
			mapper.crafty_listner.factors.clear();
			serviceDM_ByCountry.forEach((country, hash) -> {
				mapper.crafty_listner.factors.putIfAbsent(country, new HashMap<>());
				hash.forEach((service, demand) -> {
					double f = mapper.crafty_listner.serviceSupplyBycountry.get(country).get(service) / demand;
					f = (Double.isNaN(f) || Double.isInfinite(f)) ? 0 : f;
					mapper.crafty_listner.factors.get(country).put(service, f);
				});
			});
			System.out.println("$$$$ " + mapper.crafty_listner.factors);
		}
	}

	private void getServiceDM() {
		// update demands from domistic
		pl.getComoDemands(Timestep.getCurrentYear());
		serviceDM_ByCountry.clear();
		pl.comoDemandsByCountry.forEach((country, comoHash) -> {
			serviceDM_ByCountry.putIfAbsent(country, new HashMap<>());
			ServiceSet.getServicesList().forEach(service -> {
				serviceDM_ByCountry.get(country).put(service, getComoToServiceMapper(service, comoHash));
			});
		});
	}

	private void exportCSVs() {
		String dir = PathTools.makeDirectory(ConfigLoader.config.output_folder_name + File.separator + "plum");
		CsvTools.writeCSVfile(servicePriceListener, Paths.get(dir + File.separator + "servicePrices.csv"));
		mapComoToserviceDM();
		CsvTools.writeCSVfile(serviceDMListener, Paths.get(dir + File.separator + "serviceDemands.csv"));
	}
}
