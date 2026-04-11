package newCoupler;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;

public class Plum_Listener {
	Mapper mapper;
	private static final CustomLogger LOGGER = new CustomLogger(Plum_Listener.class);

	Set<String> comoSet = new HashSet<>();
//	Set<String> comoFullSet = new HashSet<>();
	Map<String, Map<String, Double>> comoDemandsByCountry = new HashMap<>(); // <country,como,demand_value>
	Map<String, Map<String, Double>> comoLeftDemandsByCountry = new HashMap<>(); // <country,como,demand_value>

	Map<String, Map<String, Double>> foddercropsFractionByCountry = new HashMap<>();// <country,
																					// <como,fraction>>
	private Map<String, Double> foddercropsFraction = new HashMap<>();
	Map<String, Map<String, Double>> bioFractionByCountry = new HashMap<>(); // <country, <como,fraction>>

	Map<String, Map<String, Double>> rumFactor = new HashMap<>();// <contry,como,factor>
	Map<String, Map<String, Double>> monFactor = new HashMap<>();// <contry,como,factor>
	Map<String, Map<String, Double>> initialImport = new HashMap<>();// <contry,como,factor>

	Map<String, Double> woodfuel = new HashMap<>();// <contry,WoodfuelDemand>
	Map<String, Double> roundwood = new HashMap<>();// <contry,RoundwoodDemand>

	Map<String, Double> comoDemandsAgg = new HashMap<>(); // <como,demand_value>
	private Map<String, List<Double>> dmListner = new LinkedHashMap<>();

	HashMap<String, Double> commoditiesPrice = new HashMap<>();
	private Map<String, List<Double>> comoPriceListner = new LinkedHashMap<>();

	public Plum_Listener(Mapper mapper) {
		this.mapper = mapper;
	}

	public void setup() {
		getInitialFactors();
		getComoDemands(Timestep.getCurrentYear());
	}

	public void step() {
		LOGGER.info("Plum_Listener step(): Timestep.getCurrentYear():  " + Timestep.getCurrentYear());
		getComoDemands(Timestep.getCurrentYear());
		commoditiesPrice(Timestep.getCurrentYear());
		exportCSVs();
	}

	private void commoditiesPrice(int year) {
		commoditiesPrice.clear();
		
		Path path = PathTools.fileFilter(Mapper.allpaths, "domestic.txt").get(0);
		LOGGER.info("commoditiesPrice: domestic path:  " + path);
		if (path == null || !path.toFile().isFile()) {
			LOGGER.fatal("System can not find domestic.txt in Plum outputs");
		}
		Map<String, List<String>> csv = CsvProcessors.ReadAsaHash(path);

		for (int i = 0; i < csv.get("Year").size(); i++) {
			if (Utils.sToI(csv.get("Year").get(i)) == year) {
				commoditiesPrice.putIfAbsent(csv.get("Crop").get(i), Utils.sToD(csv.get("Export_price").get(i)));
			}
		}
		// add wood
		path = PathTools.fileFilter(Mapper.allpaths, "wood.txt").get(0);

		Map<String, List<String>> csvWood = CsvProcessors.ReadAsaHash(path);
		for (int i = 0; i < csvWood.get("Year").size(); i++) {
			if (Utils.sToI(csvWood.get("Year").get(i)) == year) {
				commoditiesPrice.putIfAbsent("wood", Utils.sToD(csvWood.get("Export_price").get(i)));
				break;
			}
		}
		comoPriceListner.putIfAbsent("Year", new ArrayList<>());
		comoPriceListner.get("Year").add((double) year);
		commoditiesPrice.forEach((como, value) -> {
			comoPriceListner.putIfAbsent(como, new ArrayList<>());
			comoPriceListner.get(como).add(value);
		});
		LOGGER.info("commoditiesPrice: comoPriceListner.keySet():  " + comoPriceListner.keySet());
	}

	private void getInitialFactors() {
		Path path = PathTools.fileFilter(Mapper.allpaths, "domestic.txt").get(0);
		Map<String, List<String>> csv = CsvProcessors.ReadAsaHash(path);
		for (int i = 0; i < csv.get("Country").size(); i++) {
			rumFactor.putIfAbsent(csv.get("Country").get(i), new HashMap<>());
			monFactor.putIfAbsent(csv.get("Country").get(i), new HashMap<>());
			initialImport.putIfAbsent(csv.get("Country").get(i), new HashMap<>());
//			comoFullSet.add(csv.get("Crop").get(i));
		}

		for (int i = 0; i < csv.get("Year").size(); i++) {
			if (Utils.sToI(csv.get("Year").get(i)) == Timestep.getStartYear()) {
				String country = csv.get("Country").get(i);
				String crop = csv.get("Crop").get(i);

				double production = Utils.sToD(csv.get("Production").get(i));

				if (!comoSet.contains(crop)) {
					double netImport = Utils.sToD(csv.get("Net_imports").get(i));
					double factorRum = Utils.sToD(csv.get("Rum_feed_amount").get(i)) / (production + netImport);
					double factorMon = Utils.sToD(csv.get("Mon_feed_amount").get(i)) / (production + netImport);
					rumFactor.get(country).put(crop,
							Double.isNaN(factorRum) || Double.isInfinite(factorRum) ? 0 : factorRum);
					monFactor.get(country).put(crop,
							Double.isNaN(factorMon) || Double.isInfinite(factorMon) ? 0 : factorMon);
					initialImport.get(country).put(crop, netImport);
				}
			}
		}

		dmListner.put("Year", new ArrayList<>());
		rumFactor.values().iterator().next().keySet().forEach(como -> {
			dmListner.put(como, new ArrayList<>());
		});
		dmListner.put("wood", new ArrayList<>());
		// add wood
		Path wood = PathTools.fileFilter(Mapper.allpaths, "wood.txt").get(0);
		Map<String, List<String>> woodCSV = CsvProcessors.ReadAsaHash(wood);
		for (int i = 0; i < woodCSV.get("Country").size(); i++) {
			if (mapper.countryLongToShortName.containsKey(woodCSV.get("Country").get(i))) {
				String country = woodCSV.get("Country").get(i);
				double pro = Utils.sToD(woodCSV.get("Production").get(i));
				double impo = Utils.sToD(woodCSV.get("Net_imports").get(i));
				double round = Utils.sToD(woodCSV.get("RoundwoodDemand").get(i)) / (pro + impo);
				round = Double.isInfinite(round) || Double.isNaN(round) ? 0 : round;
				double fuel = Utils.sToD(woodCSV.get("WoodfuelDemand").get(i)) / (pro + impo);
				fuel = Double.isInfinite(fuel) || Double.isNaN(fuel) ? 0 : fuel;
				roundwood.put(country, round);
				woodfuel.put(country, fuel);
				initialImport.get(country).put("wood", impo);
			}
		}

	}

	void getComoDemands(int year) {
		comoDemandsByCountry.clear();
//		comoLeftDemandsByCountry.clear();
		comoDemandsAgg.clear();
		Path path = PathTools.fileFilter(Mapper.allpaths, "domestic.txt").get(0);
		LOGGER.info("Domestic path:  " + path);
		Map<String, List<String>> csv = CsvProcessors.ReadAsaHash(path);
		for (int i = 0; i < csv.get("Country").size(); i++) {
			if (mapper.countryLongToShortName.containsKey(csv.get("Country").get(i))) {
				comoDemandsByCountry.putIfAbsent(csv.get("Country").get(i), new HashMap<>());
				comoLeftDemandsByCountry.putIfAbsent(csv.get("Country").get(i), new HashMap<>());
			}
		}
		for (int i = 0; i < csv.get("Country").size(); i++) {
			if (mapper.countryLongToShortName.containsKey(csv.get("Country").get(i))
					&& Utils.sToI(csv.get("Year").get(i)) == year) {
				comoDemandsByCountry.get(csv.get("Country").get(i)).put(csv.get("Crop").get(i),
						Utils.sToD(csv.get("Production").get(i)));
				comoLeftDemandsByCountry.get(csv.get("Country").get(i)).putIfAbsent(csv.get("Crop").get(i),
						Utils.sToD(csv.get("Production").get(i)));
			}
		}

		// add wood
		path = PathTools.fileFilter(Mapper.allpaths, "wood.txt").get(0);

		Map<String, List<String>> csvWood = CsvProcessors.ReadAsaHash(path);
		for (int i = 0; i < csvWood.get("Year").size(); i++) {
			if (csvWood.get("Year").get(i).equals(String.valueOf(year))) {
				if (mapper.countryLongToShortName.containsKey(csvWood.get("Country").get(i))) {
					comoDemandsByCountry.get(csvWood.get("Country").get(i)).put("wood",
							Utils.sToD(csvWood.get("Production").get(i))
									+ Utils.sToD(csvWood.get("Net_imports").get(i)));
				}
			}
		}

		comoDemandsByCountry.forEach((country, hash) -> {
			hash.forEach((como, value) -> {
				comoDemandsAgg.merge(como, value, Double::sum);
			});
		});
		
		LOGGER.info("comoDemandsByCountry.keySet():  " + comoDemandsByCountry.keySet());

	}

	void getFoddercropsFraction() {
		foddercropsFractionByCountry.clear();
		Map<String, List<String>> csv = CsvProcessors
				.ReadAsaHash(PathTools.fileFilter(Mapper.allpaths, "domestic.txt").get(0));

		for (int i = 0; i < csv.get("Country").size(); i++) {
			foddercropsFractionByCountry.putIfAbsent(csv.get("Country").get(i), new HashMap<>());
		}

		for (int i = 0; i < csv.get("Crop").size(); i++) {
			double fraction = (Utils.sToD(csv.get("Rum_feed_amount").get(i))
					+ Utils.sToD(csv.get("Mon_feed_amount").get(i)))
					/ (Utils.sToD(csv.get("Production").get(i)) + Utils.sToD(csv.get("Net_imports").get(i)));
			foddercropsFractionByCountry.get(csv.get("Country").get(i)).put(csv.get("Crop").get(i),
					Double.isNaN(fraction) ? 0 : fraction);
		}
		foddercropsFractionByCountry.forEach((country, v) -> {
			v.forEach((como, vv) -> {
				if (!como.equals("rice"))
					foddercropsFraction.merge(como, vv / foddercropsFractionByCountry.size(), Double::sum);
			});
		});
	}

	void getbioFractionBycountry() {
		Path bio_fractions_df = PathTools.fileFilter(Mapper.allpaths, "bio_fractions_df.csv").get(0);
		Map<String, List<String>> csv = CsvProcessors.ReadAsaHash(bio_fractions_df);
		for (int i = 0; i < csv.get("Country").size(); i++) {
			bioFractionByCountry.putIfAbsent(csv.get("Country").get(i), new HashMap<>());
		}
		for (int i = 0; i < csv.get("Crop").size(); i++) {
			bioFractionByCountry.get(csv.get("Country").get(i)).putIfAbsent(csv.get("Crop").get(i),
					Utils.sToD(csv.get("BioFraction").get(i)));
		}
	}

	private void exportCSVs() {
		String dir = PathTools.makeDirectory(ConfigLoader.config.output_folder_name + File.separator + "plum");

		dmListner.get("Year").add((double) Timestep.getCurrentYear());
		comoDemandsAgg.forEach((como, value) -> {
			dmListner.get(como).add(value);
		});

		CsvTools.writeCSVfile(dmListner, Paths.get(dir + File.separator + "comoDemands.csv"));
		CsvTools.writeCSVfile(comoPriceListner, Paths.get(dir + File.separator + "comoPrices.csv"));
	}

//	private Map<String, Double> getYearDM_fromDomestic(int year, Map<String, List<String>> csv) {
//		Map<String, Double> out = new HashMap<>();// <year,como,v>
//
//		for (int i = 0; i < csv.values().iterator().next().size(); i++) {
//			if (mapper.countryLongToShortName.containsKey(csv.get("Country").get(i))
//					&& Utils.sToI(csv.get("Year").get(i)) == year) {
//				out.merge(csv.get("Crop").get(i), Utils.sToD(csv.get("Production").get(i)), Double::sum);
//			}
//
//		}
//		return out;
//
//	}

//	private void exportComosFromDomestic() {
//		Map<String, List<String>> csv = CsvProcessors
//				.ReadAsaHash(PathTools.fileFilter(Mapper.allpaths, "domestic.txt").get(0));
//
//		Map<Integer, Map<String, Double>> tmp = new LinkedHashMap<>();
//
//		for (int i = 0; i < csv.get("Country").size(); i++) {
//			if (mapper.countryLongToShortName.containsKey(csv.get("Country").get(i))) {
//				tmp.putIfAbsent(Utils.sToI(csv.get("Year").get(i)), new LinkedHashMap<>());
//				tmp.get(Utils.sToI(csv.get("Year").get(i))).merge(csv.get("Crop").get(i),
//						Utils.sToD(csv.get("Production").get(i)), Double::sum);
//			}
//		}
//
//		Map<String, List<Double>> out = new LinkedHashMap<>();
//		out.put("Year", new ArrayList<>());
//		
//		tmp.values().iterator().next().keySet().forEach(como -> {
//			out.putIfAbsent(como, new ArrayList<>());
//		});
//		tmp.forEach((year, hash) -> {
//			out.get("Year").add((double) year);
//			hash.forEach((como, v) -> {
//				out.get(como).add(v);
//			});
//		});
//
//		String dir = PathTools.makeDirectory(ConfigLoader.config.output_folder_name + File.separator + "plum");
//		CsvTools.writeCSVfile(out, Paths.get(dir + File.separator + "comoDemandsTester.csv"));
//	}

}
