package de.cesr.crafty.core.dataLoader.land;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.utils.file.PathTools;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.csv.CsvReadOptions;

public class GisLoader {
	private static final CustomLogger LOGGER = new CustomLogger(GisLoader.class);
	public static ConcurrentHashMap<String, Integer> regionIDs = new ConcurrentHashMap<>();

	public void loadGisData() {
		try {
			Path path = PathTools.fileFilter(true, File.separator + "GIS" + File.separator).get(0);
			ProjectLoader.WorldName = path.toFile().getName().replace("_Regions", "").replace(".csv", "");
			LOGGER.info("WorldName = " + ProjectLoader.WorldName);
			CsvReadOptions options = CsvReadOptions.builder(path.toFile()).separator(',').build();
			Table T = Table.read().usingOptions(options);
			AtomicInteger count = new AtomicInteger();

			for (int i = 0; i < T.columns().iterator().next().size(); i++) {
				String coor = T.column("X").get(i) + "," + T.column("Y").get(i);
				int ii = i;
				if (CellsLoader.hashCell.get(coor) != null) {
					T.columnNames().forEach(name -> {
						if (T.column(name).get(ii) != null && name.contains("Region_Code")) {
							CellsLoader.hashCell.get(coor).setCurrentRegion(T.column(name).get(ii).toString());
							CellsLoader.regionsNamesSet.add(T.column(name).get(ii).toString());
							if (!regionIDs.keySet().contains(T.column(name).get(ii).toString())) {
								count.getAndIncrement();
							}
							regionIDs.putIfAbsent(T.column(name).get(ii).toString(), count.get());
						}
					});
				}
			}
		} catch (NullPointerException | IOException e) {
			CellsLoader.regionalization = false;
			LOGGER.warn(
					"The Regionalization File is not Found in the GIS Folder, this Data Will be Ignored - No Regionalization Will be Possible.");

		}
	}
}
