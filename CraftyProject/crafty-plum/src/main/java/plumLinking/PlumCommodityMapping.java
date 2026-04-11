package plumLinking;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import couplingUtils.CsvUtilsP;
import de.cesr.crafty.core.cli.ConfigLoader;
//import ac.ed.lurg.ModelConfig;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;

public class PlumCommodityMapping {
	ArrayList<Path> allpaths;
	private List<Map<String, String>> bio_fractions;
	private List<Map<String, String>> waste_df;
	private List<Map<String, String>> plumCountry_demandData;
	private List<Map<String, String>> domestic;
	private HashMap<String, Set<String>> FilterHash = new HashMap<>();
	public static Map<String, String> countryLongToShortName = new HashMap<>();
	public static Map<String, String> countryShortToLongName = new HashMap<>();

	public Map<String, Map<String, Double>> finalCountriesDemands;
	public Map<String, Double> totalDemands = new HashMap<>();
	public Map<String, Map<String, Double>> finalCountriesPrice;
	public Map<String, Double> totalPrice = new HashMap<>();

	void Eu_countries() {
		String[] EuCountries = { "Cyprus", "Czechia", "Portugal", "Greece", "Austria", "Latvia", "Netherlands",
				"Sweden", "Ireland", "Belgium & Luxembourg", "Poland", "Slovakia", "Slovenia", "Bulgaria", "France",
				"Lithuania", "Croatia", "Italy & Malta", "Romania", "Hungary", "United Kingdom", "Switzerland", "Spain",
				"Norway", "Finland", "Denmark", "Germany", "Estonia" };
		String[] shortNames = { "CY", "CZ", "PT", "EL", "AT", "LV", "NL", "SE", "IE", "BE", "PL", "SK", "SI", "BG",
				"FR", "LT", "HR", "MT", "RO", "HU", "UK", "CH", "ES", "NO", "FI", "DK", "DE", "EE" };

		for (int i = 0; i < EuCountries.length; i++) {
			countryLongToShortName.put(EuCountries[i], shortNames[i]);
			countryShortToLongName.put(shortNames[i], EuCountries[i]);
		}
	}

	public void initialize() {
		allpaths = PathTools.findAllFilePaths(Paths.get(ConfigLoader.config.plumOutPutPath));
		Eu_countries();
		FilterHash.put("Country", new HashSet<>(countryLongToShortName.keySet()));
		staticFilesinitialisation();
	}

	void fromPlumDataToDemandsAndPrice(int tick) {
		iterativeFileReadingAndFilter(tick);
		mappingDemandsAndPrice();
	}

	void staticFilesinitialisation() {
		bio_fractions = LinkingTools.filterMapsByCriteria(
						CsvUtilsP.readCsvIntoList(PathTools.fileFilter(allpaths, "bio_fractions_df.csv").get(0)),
						FilterHash);
		waste_df = CsvUtilsP.readCsvIntoList(PathTools.fileFilter(allpaths, "waste_df.csv").get(0));
	}

	void iterativeFileReadingAndFilter(int year) {
		FilterHash.put("Year", Set.of(String.valueOf(year)));
		plumCountry_demandData = LinkingTools.filterMapsByCriteria(
				CsvUtilsP.readCsvIntoList(PathTools.fileFilter(allpaths, "countryDemand.txt").get(0)), FilterHash);
		domestic = LinkingTools.filterMapsByCriteria(
				CsvUtilsP.readCsvIntoList(PathTools.fileFilter(allpaths, "domestic.txt").get(0)), FilterHash);
		System.out.println("||" + PathTools.fileFilter(allpaths, "domestic.txt").get(0));
	}

	List<Map<String, String>> bio_crop_demand_df() {
		List<Map<String, String>> merge = LinkingTools.left_join_many_to_many(plumCountry_demandData, bio_fractions,
				"Country", "Commodity");
		merge = LinkingTools.left_join(merge, waste_df, "Crop");
		merge.forEach(map -> {
			double tmp = Utils.sToD(map.get("BioenergyDemand")) * Utils.sToD(map.get("BioFraction"))
					/ (1 - Utils.sToD(map.get("WasteRate")));
			map.put("BioenergyDemand", String.valueOf(tmp));
		});
		merge.forEach(map -> {
			map.remove("Demand");
			map.remove("Commodity");
			map.remove("WasteRate");
			map.remove("BioFraction");
			map.remove("ConsumerPrice");
		});
		return merge;
	}

	void mappingDemandsAndPrice() {
		List<Map<String, String>> domistic = domestic_prod();

		// split by countries
		Map<String, List<Map<String, String>>> countriesData = new HashMap<>();
		for (String country : countryLongToShortName.keySet()) {
			List<Map<String, String>> d = new ArrayList<>();
			domistic.forEach(line -> {
				if (line.get("Country").equals(country)) {
					d.add(line);
				}
			});
			countriesData.put(countryLongToShortName.get(country), d);
		}

//		countriesData.forEach((country, data) -> {
//			System.out.println(country+": ");
//			data.forEach(m->{
//				System.out.println(m+"=>");
//				System.out.println(getCropPrice(m, "pasture"));
//			});
//		});

		getDemandsFromData(countriesData);
		getPriceFromData(countriesData);
	}

	private void getPriceFromData(Map<String, List<Map<String, String>>> countriesData) {
		finalCountriesPrice = new HashMap<>();
		totalPrice = new HashMap<>();

		countriesData.forEach((country, data) -> {
			Map<String, Double> map = new HashMap<>();
			for (Map<String, String> line : data) {
				double Fodder_price = (getCropPrice(line, "wheat") + getCropPrice(line, "maize")
						+ getCropPrice(line, "rice") + getCropPrice(line, "oilcropsNFix")) / 4;
				double pasture = getCropPrice(line, "pasture") + getCropPrice(line, "ruminants");
				map.merge("Pasture", pasture, Double::sum);
				map.merge("Foddercrops", Fodder_price, Double::sum);
				map.merge("BioenergyG1", Fodder_price, Double::sum);
				map.merge("C4crops", getCropPrice(line, "maize"), Double::sum);
				map.merge("C3oilcrops", getCropPrice(line, "oilcropsOther"), Double::sum);
				map.merge("C3starchyroots", getCropPrice(line, "starchyRoots"), Double::sum);
				map.merge("C3cereals", getCropPrice(line, "wheat"), Double::sum);
				map.merge("C3fruitveg", getCropPrice(line, "fruitveg"), Double::sum);
				map.merge("BioenergyG2", getCropPrice(line, "energycrops"), Double::sum);
			}
			finalCountriesPrice.put(country, map);
		});
		finalCountriesPrice.forEach((country, mp) -> {
			mp.forEach((serviceName, value) -> {
				totalPrice.merge(serviceName, value / finalCountriesPrice.size(), Double::sum);
			});
		});

	}

	private void getDemandsFromData(Map<String, List<Map<String, String>>> countriesData) {
		finalCountriesDemands = new HashMap<>();
		totalDemands = new HashMap<>();
		countriesData.forEach((country, data) -> {
			Map<String, Double> map = new HashMap<>();
			for (Map<String, String> line : data) {

				double Fodder_crops = getCrop(line, "wheat", "Rum_feed_produced")
						+ getCrop(line, "wheat", "Mon_feed_produced") + getCrop(line, "maize", "Rum_feed_produced")
						+ getCrop(line, "maize", "Mon_feed_produced") + getCrop(line, "rice", "Rum_feed_produced")
						+ getCrop(line, "rice", "Mon_feed_produced")
						+ getCrop(line, "oilcropsNFix", "Rum_feed_produced")
						+ getCrop(line, "oilcropsNFix", "Mon_feed_produced");
				map.merge("Foddercrops", Fodder_crops, Double::sum);
				double Bioenergy1G = getCrop(line, "wheat", "Bioenergy_produced")
						+ getCrop(line, "maize", "Bioenergy_produced") + getCrop(line, "rice", "Bioenergy_produced")
						+ getCrop(line, "oilcropsNFix", "Bioenergy_produced");
				double pasture = getCrop(line, "pasture", "Rum_feed_produced")
						+ getCrop(line, "pasture", "Mon_feed_produced") + getCrop(line, "pasture", "Food_produced")
						+ getCrop(line, "ruminants", "Mon_feed_produced")
						+ getCrop(line, "ruminants", "Rum_feed_produced") + getCrop(line, "ruminants", "Food_produced");
				map.merge("Pasture", pasture, Double::sum);
				map.merge("BioenergyG1", Bioenergy1G, Double::sum);
				map.merge("C4crops", getCrop(line, "maize", "Food_produced"), Double::sum);
				// map.merge("C3rice", getCrop(line, "rice", "Food_produced"), Double::sum);//
				// map.merge("C3oilNFix", getCrop(line, "oilcropsNFix", "Food_produced"),
				// Double::sum);//
				map.merge("C3oilcrops", getCrop(line, "oilcropsOther", "Food_produced"), Double::sum);
				map.merge("C3starchyroots", getCrop(line, "starchyRoots", "Food_produced"), Double::sum);
				map.merge("C3cereals", getCrop(line, "wheat", "Food_produced"), Double::sum);
				map.merge("C3fruitveg", getCrop(line, "fruitveg", "Food_produced"), Double::sum);
				map.merge("BioenergyG2", getCrop(line, "energycrops", "Bioenergy_produced"), Double::sum);

			}
			finalCountriesDemands.put(country, map);
		});
		finalCountriesDemands.forEach((country, mp) -> {
			mp.forEach((serviceName, value) -> {
				totalDemands.merge(serviceName, value, Double::sum);
			});
		});
	}

	List<Map<String, String>> domestic_prod() {
		List<Map<String, String>> bio_crop_demand = bio_crop_demand_df();
		List<Map<String, String>> left_join = LinkingTools.left_join(domestic, bio_crop_demand, "Year", "Country",
				"Crop");

		left_join.forEach(map -> {
			double tmp = Utils.sToD(map.get("Production")) + Utils.sToD(map.get("Net_imports"));
			map.put("Supply", String.valueOf(tmp));
		});

		left_join.forEach(map -> {
			double tmp = Utils.sToD(map.get("BioenergyDemand"));
			if (map.get("Crop").equals("energycrops")) {
				tmp = Utils.sToD(map.get("Supply"));
			} else if (map.get("Crop").equals("pasture") || map.get("Crop").equals("setaside")) {
				tmp = 0;
			}
			map.put("BioenergyDemand", String.valueOf(tmp));
		});
		left_join.forEach(map -> {
			double tmp = Utils.sToD(map.get("Supply")) - Utils.sToD(map.get("Mon_feed_amount"))
					- Utils.sToD(map.get("Rum_feed_amount")) - Utils.sToD(map.get("BioenergyDemand"));
			map.put("Food", String.valueOf(tmp));
		});

		left_join.forEach(map -> {
			map.remove("Import_price");
			map.put("Bioenergy", map.get("BioenergyDemand"));
			map.remove("BioenergyDemand");
			map.remove("Net_import_cost");
			// map.remove("Export_price");////
			map.remove("Area");
			map.remove("Production_cost");
			map.put("Rum_feed", map.get("Rum_feed_amount"));
			map.remove("Rum_feed_amount");
			map.remove("Production_cost");
			map.remove("Consumer_price");
			map.remove("Prod_shock");
			map.put("Mon_feed", map.get("Mon_feed_amount"));
			map.remove("Mon_feed_amount");
		});
		left_join.forEach(map -> {
			double tmp = Utils.sToD(map.get("Supply")) > 0
					? Utils.sToD(map.get("Production")) / Utils.sToD(map.get("Supply"))
					: 0;
			map.put("ProductionRatio", String.valueOf(tmp));
			double tmp2 = Utils.sToD(map.get("Food")) * Utils.sToD(map.get("ProductionRatio"));
			map.put("Food_produced", String.valueOf(tmp2));
			double tmp3 = Utils.sToD(map.get("Bioenergy")) * Utils.sToD(map.get("ProductionRatio"));
			map.put("Bioenergy_produced", String.valueOf(tmp3));
			double tmp4 = Utils.sToD(map.get("Mon_feed")) * Utils.sToD(map.get("ProductionRatio"));
			map.put("Mon_feed_produced", String.valueOf(tmp4));
			double tmp5 = Utils.sToD(map.get("Rum_feed")) * Utils.sToD(map.get("ProductionRatio"));
			map.put("Rum_feed_produced", String.valueOf(tmp5));
		});
		left_join.forEach(map -> {
			map.remove("Supply");
			map.remove("Net_imports");
			map.remove("Production");
			map.remove("Rum_feed");
			map.remove("Bioenergy");
			map.remove("Mon_feed");
			map.remove("Food");
			map.remove("ProductionRatio");
		});
		return left_join;
	}

	double getCrop(Map<String, String> map, String val1, String key2) {
		double tmp = map.get("Crop").equals(val1) ? Utils.sToD(map.get(key2)) : 0;
		if (tmp < 0) {
			return 0;
		}

		return tmp;
	}

	double getCropPrice(Map<String, String> map, String val1) {
		return map.get("Crop").equals(val1) ? Utils.sToD(map.get("Export_price")) : 0;
	}
}
