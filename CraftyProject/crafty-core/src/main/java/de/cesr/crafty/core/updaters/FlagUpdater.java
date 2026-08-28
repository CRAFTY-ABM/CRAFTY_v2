package de.cesr.crafty.core.updaters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.main.MainHeadless;
import de.cesr.crafty.core.utils.file.DirectoryWatcher;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;
import de.cesr.crafty.core.utils.non_java_code_controller.RScriptRunnerController;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Implements an optional “waiting flag” mechanism to pause the simulation at specific years until an external
 * file/folder flag becomes available.
 *
 * This updater reads a {@code waitingFlags.csv} configuration (either from
 * {@code ConfigLoader.config.waiting_flags_path} if it points to a file, or from the default
 * {@code config/waitingFlags.csv} found in the project structure).
 * The CSV is expected to contain:
 * - {@code Year}: the simulation year when the model should pause
 * - {@code Waiting_Flag}: a filesystem path used as a synchronization flag (e.g., a folder/file created by a
 *   coupled model or external workflow)
 *
 * At each tick, if a flag is configured for {@link Timestep#getCurrentYear()}, the updater calls
 * {@link DirectoryWatcher#waitForYearFolder(Path)} to block progress until the
 * required flag is detected, enabling loose coupling / co-simulation with external processes.
 */
/**
 * @author Mohamed Byari
 *
 */
public class FlagUpdater extends AbstractUpdater {
	private static final CustomLogger LOGGER = new CustomLogger(FlagUpdater.class);

	HashMap<Integer, Path> flags = new HashMap<>();

	public FlagUpdater() {
		flags.clear();
		checkCLIforAnnualFlags();
		if (flags.isEmpty()) {
			Path csv;
			if (Paths.get(ConfigLoader.config.waiting_flags_path).toFile().isFile()) {
				csv = Paths.get(ConfigLoader.config.waiting_flags_path);
			} else {
				ArrayList<Path> matches = PathTools.fileFilter(PathTools.asFolder("config"), "waitingFlags.csv");
				csv = (matches != null && !matches.isEmpty()) ? matches.get(0) : null;
			}
			if (csv == null) {
				return;
			}

			Map<String, List<String>> hash = CsvProcessors.ReadAsaHash(csv);
			if (hash == null || hash.isEmpty()) {
				return;
			}

			List<String> years = hash.get("Year");
			List<String> paths = hash.get("Waiting_Flag");
			if (years == null || paths == null) {
				return;
			}

			int n = Math.min(years.size(), paths.size());
			for (int i = 0; i < n; i++) {
				Integer y = Utils.sToI(years.get(i));
				String p = paths.get(i);
				if (p == null || p.isBlank())
					continue;
				flags.put(y, Paths.get(p));
			}
		}
		LOGGER.info(
				"CRAFTY will wait for these flag files  before starting a new iteration for the corresponding years."
						+ flags);
	}

	private void checkCLIforAnnualFlags() {
		if (MainHeadless.options == null || MainHeadless.options.getAnnual_waiting_flag_path() == null) {
			return;
		}
		String waitingPath = MainHeadless.options.getAnnual_waiting_flag_path();
		if (!Paths.get(waitingPath).toFile().isDirectory()) {
			return;
		}
		for (int i = Timestep.getStartYear() + 1; i < Timestep.getEndtYear() - 1; i++) {
			flags.put(i, Paths.get(waitingPath).resolve("done_" + i));
		}
	}

	public void addNewWaitingFlags(HashMap<Integer, Path> f) {
		flags.clear();
		flags.putAll(f);
	}

	@Override
	public void toSchedule() {
		modelRunner.scheduleRepeating(this);
	}

	@Override
	public void step() {
//		wait For a flag
		if (flags != null && !flags.isEmpty()) {
			Path p = flags.get(Timestep.getCurrentYear());
			if (p != null) {
				DirectoryWatcher.waitForYearFolder(p);
			}
		}
		if (ConfigLoader.config.r_script_runner.enabled) {
			RScriptRunnerController.runScriptsForCurrentYear();
		}

	}

}
