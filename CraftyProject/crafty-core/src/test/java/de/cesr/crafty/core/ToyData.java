package de.cesr.crafty.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.cli.Config;
import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.crafty.AftCategory;
import de.cesr.crafty.core.crafty.ManagerTypes;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.afts.AftCategorised;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.CapitalUpdater;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.CsvTools;

public class ToyData {

//	@TempDir
//	private Path tempDir;
//
//	@Test
//	void main() {
//		System.out.println("ToyData_Main_running");
//		resetStaticState(tempDir);
//	}

	public void resetStaticState(Path projectDir) {
		
		// Clear category-related static state before each test
		AftCategorised.aftCategories.clear();
		AftCategorised.CategoriesIntestisy.clear();
		AftCategorised.categoriesColor.clear();

		// Reset mean / SD maps as well
		AftCategorised.getMean().clear();
		AftCategorised.getSD().clear();
		AftCategorised.useCategorisationGivIn = false;

		// Ensure AFTsLoader map is empty before we add test AFTs
		AFTsLoader.getAftHash().clear();

		// Minimal config object so useCategories() can read the flag
		if (ConfigLoader.config == null) {
			ConfigLoader.config = new Config();
		}
		ConfigLoader.config.use_AFTs_categories_GiveIn = true;
		ConfigLoader.config.LOGGER_info = false;

		// Point metadata dir to the temp project folder
		ConfigLoader.config.metaData_directory = projectDir.toString();

		// Create empty production / behaviour directories so AFTsLoader
		// can safely scan them without NPEs
		try {
			Path productionDir = projectDir.resolve("production");
			Path behaviourDir = projectDir.resolve("agents");
			Files.createDirectories(productionDir);
			Files.createDirectories(behaviourDir);

			ConfigLoader.config.aft_production_directory = productionDir.toString();
			ConfigLoader.config.aft_behevoir_directory = behaviourDir.toString();

			metaData(projectDir);
			capitals(projectDir);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	static void metaData(Path projectDir) throws IOException {
//		AFTs
		Path aftsMetaData = projectDir.resolve("AFTsMetaData.csv");
//		List<String> lines = List.of("Label,Name,Color,Type,Category,Category_Color,Intesity_name,Intesity_level",
//				"AFT1,AFT 1,#00FF00,AFT,Forest,#00FF00,Low,1", "AFT2,AFT 2,#00FF00,AFT,Forest,#00FF00,High,3",
//				"AFT3,AFT 3,#FF0000,Mask,Agriculture,#FF0000,Medium,2");
		Files.write(aftsMetaData, List.of(""));
		Aft aft1 = new Aft("AFT1");
		Aft aft2 = new Aft("AFT2");
		Aft aft3 = new Aft("AFT3");

		AFTsLoader.getAftHash().put("AFT1", aft1);
		AFTsLoader.getAftHash().put("AFT2", aft2);
		AFTsLoader.getAftHash().put("AFT3", aft3);
		AftCategorised.aftCategories.put("Crop", new HashSet<>());
		AftCategorised.aftCategories.put("Forest", new HashSet<>());
		AftCategorised.aftCategories.put("Pasture", new HashSet<>());

		aft1.setCategory(new AftCategory("Forest"));
		aft1.getCategory().setIntensity("Low");
		aft1.getCategory().setIntensityLevel(1);
		aft2.setCategory(new AftCategory("Forest"));
		aft2.getCategory().setIntensity("Medium");
		aft2.getCategory().setIntensityLevel(2);
		aft3.setCategory(new AftCategory("Pasture"));
		aft3.getCategory().setIntensity("High");
		aft3.getCategory().setIntensityLevel(3);

		AFTsLoader.getAftHash().values().forEach(a -> {
			a.setType(ManagerTypes.AFT);
			AFTsLoader.getActivateAFTsHash().put(a.getLabel(), a);
			AftCategorised.aftCategories.get(a.getCategory().getName()).add(a);
			// AftCategorised.CategoriesIntestisy.get(a.getCategory().getName()).add("");
		});
		AftCategorised.aftCategories.keySet().forEach(categoryName -> {
			AftCategorised.CategoriesIntestisy.put(categoryName, Set.of("Low", "Medium", "High"));
		});

		AFTsLoader.getAftHash().put("Abandoned", new Aft("Abandoned"));
//		AFTsLoader.addAbandonedAftIfNotExiste();

//	    Minimal scenario table
		Path scenarios = projectDir.resolve("scenarios.csv");
		String[][] scenarioTable = { { "Name", "startYear", "endtYear" }, { "TestScenario", "2000", "2050" } };
		CsvTools.writeCSVfile(scenarioTable, scenarios);
//		services
		Path services = projectDir.resolve("Services.csv");
		CsvTools.writeCSVfile(new String[0][0], services);
		ServiceSet.getServicesList().clear();
		ServiceSet.getServicesList().addAll(List.of("ser1", "ser2", "ser3"));
		// capitals
		Path capitalsMetaData = projectDir.resolve("Capitals.csv");
		CsvTools.writeCSVfile(new String[0][0], capitalsMetaData);
		CapitalUpdater.getCapitalsList().clear();
		CapitalUpdater.getCapitalsList().addAll(List.of("capi1", "capi2", "capi3"));

		ProjectLoader.pathInitialisation(projectDir);
		ProjectLoader.setScenario("TestScenario");
	}

	static void capitals(Path projectDir) throws IOException {
		AFTsLoader.getAftHash().forEach((label, a) -> {
			ServiceSet.getServicesList().forEach(serviceName -> {
				a.getProductivityLevel().put(serviceName, 1.);
				a.getSensByService().put(serviceName, new ConcurrentHashMap<>());
				CapitalUpdater.getCapitalsList().forEach(capitalName -> {
					a.getSensByService().get(serviceName).put(capitalName, 1.);
				});
			});
		});

		Path production = projectDir.resolve("production");

		for (int i = 1; i < 4; i++) {
			Path path = production.resolve("AFT" + i + ".csv");
			String[][] p = { { "", "capi1", "capi2", "capi3" }, { "ser1", "0", "0", "0" }, { "ser1", "0", "0", "0" },
					{ "ser1", "0", "0", "0" } };
			CsvTools.writeCSVfile(p, path);
		}

		Path worlds = projectDir.resolve("worlds");
		Path capitals = worlds.resolve("capitals");
		ConfigLoader.config.CAPITALS_directory = capitals.toString();
//		System.out.println("000            "+ConfigLoader.config.CAPITALS_directory);

		for (int i = Timestep.getStartYear(); i < Timestep.getEndtYear(); i++) {
			Path csv = capitals.resolve(ProjectLoader.getScenario() + "_" + i + ".csv");
//			System.out.println(":::0:   " + csv);
			String[][] p = { { "ID,X,Y,capi1,capi2,capi3" }, { "0,0,0,0,0,0" } };
			CsvTools.writeCSVfile(p, csv);
		}
	}

}
