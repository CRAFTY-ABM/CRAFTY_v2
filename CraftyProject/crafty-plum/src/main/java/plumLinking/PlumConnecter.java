package plumLinking;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.RegionsModelRunnerUpdater;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.file.PathTools;

public class PlumConnecter {

	static HashMap<String, String> map;
	static HashMap<String, HashMap<String, Double>> countryMapProduction = new HashMap<>();

	public static void initialze() {
		map = new HashMap<>();
		map.put("monogastrics", "monogastrics");
		map.put("pulses", "pulses");
		map.put("ruminants", "ruminants");
		map.put("sugar", "sugar");
		map.put("Pasture", "ruminants");//
		map.put("C4crops", "maize");
		map.put("C3rice", "rice");//
		map.put("C3oilNFix", "oilcropsNFix");
		map.put("C3oilcrops", "oilcropsOther");
		map.put("C3starchyroots", "starchyRoots");
		map.put("C3cereals", "wheat");
		map.put("C3fruitveg", "fruitveg");
		map.put("BioenergyG2", "energycrops");
	}

	private static void initialCsvHash() {
		countryMapProduction.clear();
		PlumCommodityMapping.countryLongToShortName.keySet().forEach(country -> {
			HashMap<String, Double> h = new HashMap<>();
			map.keySet().forEach(serviceName -> {
				h.put(map.get(serviceName), 0.);
			});
			countryMapProduction.put(country, h);
		});
	}

	private static void getComodityProduction(String country, String craftyServiceName) {

		double value = RegionsModelRunnerUpdater.regionsModelRunner.get(country).getRegionalSupply()
				.get(craftyServiceName)
				* RegionsModelRunnerUpdater.regionsModelRunner.get(country).R.getServiceCalibration_Factor()
						.get(craftyServiceName);
		String countryLongName = PlumCommodityMapping.countryShortToLongName.get(country);
		if (craftyServiceName.equals("Foddercrops") || craftyServiceName.equals("BioenergyG1")) {
			countryMapProduction.get(countryLongName).merge("maize", value / 4, Double::sum);
			countryMapProduction.get(countryLongName).merge("oilcropsNFix", value / 4, Double::sum);
			countryMapProduction.get(countryLongName).merge("rice", value / 4, Double::sum);
			countryMapProduction.get(countryLongName).merge("wheat", value / 4, Double::sum);
		} else {
			countryMapProduction.get(countryLongName).merge(map.get(craftyServiceName), value, Double::sum);
		}
	}

	public static void associeteProduction(int year) {
		initialCsvHash();
		if (ConfigLoader.config.regionalization) {
			RegionsModelRunnerUpdater.regionsModelRunner.values().forEach(RegionalRunner -> {
				RegionalRunner.R.getServicesHash().forEach((serviceName, service) -> {
					getComodityProduction(RegionalRunner.R.getName(), serviceName);
				});
			});
		} else {
			noRegioncountryMapProduction();
		}

		///////////////
		HashMap<String, Double> getCalibration_Factor = new HashMap<>();
		RegionsModelRunnerUpdater.regionsModelRunner.values().forEach(RegionalRunner -> {
			RegionalRunner.R.getServicesHash().forEach((ns, s) -> {
				getCalibration_Factor.put(ns, s.getCalibration_Factor());
			});
		});
		//////////////

		ArrayList<String> list = new ArrayList<>();
		list.add("Country,Crop,Production,MonogastricFeed,RuminantFeed,NetImportsExpected");
		System.out.println("==> " + getCalibration_Factor);
		countryMapProduction.forEach((country, servicesHash) -> {
//			servicesHash.forEach((servceName, value) -> {
//				if (getCalibration_Factor.get(servceName) != null) {
//					list.add(country + "," + servceName + "," + (value * getCalibration_Factor.get(servceName))
//							+ ",1,1,1");
//				}
//			});
			servicesHash.forEach((servceName, value) -> {
				list.add(country + "," + servceName + "," + (value) + ",1,1,1");
			});
		});
		String[][] csv = new String[list.size()][1];
		for (int i = 0; i < list.size(); i++) {
			csv[i][0] = list.get(i);

		}
		String path = PathTools
				.makeDirectory(ConfigLoader.config.plumOutPutPath + File.separator + "crafty" + File.separator + year);
		PathTools.writeFile(path + File.separator + "done", "", false);
		CsvTools.writeCSVfile(csv, Paths.get(path + File.separator + "production.csv"));
	}

	public static void noRegioncountryMapProduction() {
		CellsLoader.hashCell.values().forEach(c -> {
			String country = PlumCommodityMapping.countryShortToLongName.get(c.getCurrentRegion());
			if (country != null) {
				for (int i = 0; i < ServiceSet.getServicesList().size(); i++) {
					String serviceName = ServiceSet.getServicesList().get(i);
					if (map.get(serviceName) != null) {
						countryMapProduction.get(country).merge(map.get(serviceName), c.getCurrentProd()[i],
								Double::sum);
					}
				}
			}
		});
	}

}
