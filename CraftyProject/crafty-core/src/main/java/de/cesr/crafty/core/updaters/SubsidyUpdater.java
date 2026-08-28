package de.cesr.crafty.core.updaters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.CsvKind;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.utils.file.PathTools;

public class SubsidyUpdater extends AbstractUpdater {
	private static final CustomLogger LOGGER = new CustomLogger(SubsidyUpdater.class);

	private Map<Integer, Path> subsidyPaths = new TreeMap<>();

	public SubsidyUpdater() {
		if (!ConfigLoader.isUsePriceExplicitGivingUp()) {
			LOGGER.info("Price-explicit giving up disabled — subsidy loading skipped");
			return;
		}

		buildPathMap();
	}

	private void buildPathMap() {
		for (int year = Timestep.getStartYear(); year <= Timestep.getEndtYear(); year++) {
			Path subsidy = findSubsidyFile(year);
			if (subsidy == null) {
				LOGGER.warn("Subsidy CSV not found for year " + year);
				continue;
			}
			subsidyPaths.put(year, subsidy);
		}
		if (subsidyPaths.isEmpty()) {
			LOGGER.info("No subsidy files found — subsidies will be 0 "
					+ "(institutional model may still set them)");
		}
	}

	private Path findSubsidyFile(int year) {
		String ltsPath = ConfigLoader.config.prescribed_subsidies_path;
		ArrayList<Path> results;
		if (ltsPath != null && !ltsPath.isEmpty()) {
			Path p = Paths.get(ltsPath);
			ArrayList<Path> ps;
			if (p.toFile().isDirectory()) {
				ps = PathTools.findAllFilePaths(p);
			} else {
				ps = PathTools.findAllFilePaths(p.getParent());
			}
			results = PathTools.fileFilter(ps, "Subsidies_" + year, ".csv");
		} else {
			results = PathTools.fileFilter(true,
					PathTools.asFolder("land_taxes_subsidies"), "Subsidies_" + year, ".csv");
		}
		if (results == null || results.isEmpty()) {
			return null;
		}
		return results.get(0);
	}

	@Override
	public void step() {
		if (!ConfigLoader.isUsePriceExplicitGivingUp()) return;
		if (subsidyPaths.isEmpty()) return;

		int year = Timestep.getCurrentYear();
		Path subsidyPath = subsidyPaths.get(year);
		if (subsidyPath != null) {
			LOGGER.info("Loading spatial subsidies for year " + year + ": " + subsidyPath);
			CsvProcessors.processCSV(subsidyPath, CsvKind.SUBSIDY);
		}
	}

	@Override
	public void toSchedule() {
		modelRunner.scheduleRepeating(this);
	}
}
