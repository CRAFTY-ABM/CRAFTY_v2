package de.cesr.crafty.core.utils.analysis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.main.MainHeadless;
import de.cesr.crafty.core.modelRunner.ModelRunner;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;

public class SplitByRegions {

//	1. initila crafty by region 
//	2. create a hash map country to groups <country,group>
//	3. create a folder for each groups (use group Names)
//		3.1. create the folders strecture
//		3.2 copy No-maps data 
//	4. cut map data create the same Name

	static HashMap<String, String> countryToG = new HashMap<>();

	static String[] G = {  "Northern","Western","Southern", "Estern" };
	static String[] DataFolderPath = new String[G.length];

	public static void main(String[] args) {
		System.out.println("--Starting CRAFTY GroupSplit --");
		MainHeadless.initializeConfig(args);
		ProjectLoader.pathInitialisation(Paths.get(ConfigLoader.config.project_path));
		MainHeadless.runner = new ModelRunner();
		MainHeadless.runner.start();
		groups();

		for (int i = 0; i < G.length; i++) {
			DataFolderPath[i] = PathTools.makeDirectory(ProjectLoader.getProjectPath().getParent() + File.separator
					+ "CRAFTY-EU-Upscaled-" + G[i] + "-Region");
			CraftyDataUpscaler.createDataTemplate(DataFolderPath[i]);
		}
		MapsSpliter(ProjectLoader.getProjectPath() + PathTools.asFolder("worlds"));
		MapsSpliter(ProjectLoader.getProjectPath() + PathTools.asFolder("GIS"));
	}

	private static void groups() {

		String[] g0 = {"NO", "SE", "FI","DK","UK", "IE", "EE", "LV", "LT"};
		initialGroup(G[0], g0);
		String[] g1 = {"FR",  "DE", "BE", "NL","CH","AT", "LU"};
		initialGroup(G[1], g1);
		String[] g2 = { "ES","PT",  "MT",  "HR", "SI", "CY"};
		initialGroup(G[2], g2);
		String[] g3 = {"PL","CZ", "SK", "HU", "RO", "BG"};//, "EL"
		initialGroup(G[3], g3);
	}

	static void MapsSpliter(String folderPath) {
		for (int i = 0; i < G.length; i++) {
			int ii = i;
			PathTools.makeDirectory(CraftyDataUpscaler.switchPaths(folderPath.toString(), DataFolderPath[ii]));
			List<Path> listSubFolders = PathTools.getAllFolders(folderPath);
			listSubFolders.forEach(l -> {
				String outPutPath = CraftyDataUpscaler.switchPaths(l.toString(), DataFolderPath[ii]);
				System.out.println("Make Directory: " + outPutPath);
				PathTools.makeDirectory(outPutPath);
			});
		}

		ArrayList<Path> foldersinCapitals = PathTools.fileFilter(folderPath);
		foldersinCapitals.forEach(path -> {
			if (path.toString().contains(".csv")) {
				if (!path.toString().contains("Restrictions")) {
					cutByregion(path);
				} else {
					try {
						for (int i = 0; i < G.length; i++) {
							int ii = i;
							Files.copy(path,
									Paths.get(CraftyDataUpscaler.switchPaths(path.toString(), DataFolderPath[ii])),
									StandardCopyOption.REPLACE_EXISTING);
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		});
	}

	static void cutByregion(Path pathInput) {
		Map<String, List<String>> reader = CsvProcessors.ReadAsaHash(pathInput);

		List<List<HashMap<String, String>>> newMapList = new ArrayList<>();
		String[][][] csv = new String[G.length][][];

		for (int i = 0; i < G.length; i++) {
			newMapList.add(new ArrayList<>());
		}
		for (int i = 0; i < reader.get("X").size(); i++) {
			int x = (int) (Utils.sToD(reader.get("X").get(i)));
			int y = (int) (Utils.sToD(reader.get("Y").get(i)));
			Cell c = CellsLoader.getCell(x, y);
			String country = c.getCurrentRegion();
//			find the country in which group
			int index = Utils.indexof(countryToG.get(country), G);
			if (index != -1) {
				HashMap<String, String> line = new HashMap<>();
				for (String colmunName : reader.keySet()) {
					line.putIfAbsent(colmunName, reader.get(colmunName).get(i));
				}
				newMapList.get(index).add(line);
			}
		}

		for (int I = 0; I < G.length; I++) {
			int ii = I;
			csv[ii] = new String[newMapList.get(ii).size() + 1][newMapList.get(ii).iterator().next().size()];
			ArrayList<String> ky = new ArrayList<>(newMapList.get(ii).iterator().next().keySet());

			int k = 0;
			if (ky.contains("ID")) {
				csv[ii][0][k] = "ID";
				k++;
			}

			csv[ii][0][k++] = "X";
			csv[ii][0][k++] = "Y";
			for (String s : ky) {
				if (!s.equals("X") && !s.equals("Y") && !s.equals("ID")) {
					csv[ii][0][k++] = s;
				}
			}
			AtomicInteger lineIndex = new AtomicInteger(1);
			newMapList.get(ii).forEach(line -> {
				line.forEach((kye, value) -> {
					int index = Utils.indexof(kye, csv[ii][0]);
					if (index != -1) {
						csv[ii][lineIndex.get()][index] = value;
					}
				});
				lineIndex.getAndIncrement();
			});
			Path pathOutput = Paths.get(CraftyDataUpscaler.switchPaths(pathInput.toString(), DataFolderPath[ii]));
			System.out.println("Generate " + pathOutput + "  from  " + pathInput.getFileName());
			CsvTools.writeCSVfile(csv[ii], pathOutput);
		}

	}

	private static void initialGroup(String groupName, String... countries) {
		for (int i = 0; i < countries.length; i++) {
			countryToG.put(countries[i], groupName);
		}
	}

}
