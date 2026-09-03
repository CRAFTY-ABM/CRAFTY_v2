package de.cesr.crafty.core.updaters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.CsvKind;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.utils.file.PathTools;

/**
 * Updater responsible for loading and applying (time-varying) capital values to all cells.
 *
 * This component does two things:
 * 1) During construction it reads the capital *metadata* file (from {@link ProjectLoader#getCapitalsMetadata()})
 *    to build the canonical list of capital names used by the model ({@link #capitalsList}).
 * 2) It also builds a year to file mapping ({@link #capitals_directory}) pointing to the capital CSV that should
 *    be applied at each simulation year, either from an explicit path provided in the YAML configuration
 *    ({@link ConfigLoader#config} - {@code capitals_directory}) or from the default scenario/worlds folder layout.
 *
 * On every tick/year ({@link #step()}):
 * - It selects the capital CSV for {@link Timestep#getCurrentYear()} and processes it via
 *   {@link CsvProcessors#processCSV(Path, #CAPITALS)}. This delegates to
 *   {@link CsvProcessors#associateCapitalsToCells}, which assigns the parsed capital values
 *   to the corresponding {@link Cell} instances (looked up by X/Y through {@link CellsLoader}).
 *
 * Input expectations (capitals CSV):
 * - Must contain at least "X" and "Y" columns plus one column per capital name.
 * - Any missing capital column is interpreted as 0 for that cell (see {@code associateCapitalsToCells}).
 *
 * Notes / assumptions:
 * - {@link Timestep} must be initialized (start/end/current year) before this updater is constructed
 *   because the constructor precomputes {@link #capitals_directory} for the whole simulation range.
 * - The model expects exactly one capitals file per year; if a year is missing, the updater calls
 *   {@link CustomLogger#fatal(String)} (fail-fast) during construction.
 * - The capitals list is stored statically and is therefore shared across runs within the same JVM.
 */
/**
 * @author Mohamed Byari
 *
 */
public class CapitalUpdater extends AbstractUpdater {
    private static final CustomLogger LOGGER = new CustomLogger(CapitalUpdater.class);
    // add to the Schedule then run everything later
    // define the list of path will be use dusring the simulation HashMap<year,path>

    private static List<String> capitalsList = new ArrayList<>();
    private static Map<String, String> capitalTypes = new ConcurrentHashMap<>();
    private static Map<Integer, Path> capitals_directory = new TreeMap<>();

    public CapitalUpdater() {
        capitalsList = Collections.synchronizedList(new ArrayList<>());
        capitalTypes = new ConcurrentHashMap<>();
        Map<String, List<String>> capitalsFile = CsvProcessors.ReadAsaHash(ProjectLoader.getCapitalsMetadata());
        String label = capitalsFile.keySet().contains("Label") ? "Label" : "Name";
        setCapitalsList(capitalsFile.get(label));
        loadCapitalTypes(capitalsFile);
        LOGGER.info("Capitals size=" + getCapitalsList().size() + " : " + getCapitalsList());
        // fill the path any way
        // <year,csv file>
        if (!ConfigLoader.config.capitals_directory.isEmpty()) {
            ArrayList<Path> ps = PathTools.findAllFilePaths(Paths.get(ConfigLoader.config.capitals_directory));
            for (int i = Timestep.getStartYear(); i <= Timestep.getEndtYear(); i++) {
                try {
                    capitals_directory.put(i, PathTools.fileFilter(ps, "_" + i, "capitals", ".csv").get(0));
                } catch (NullPointerException e) {
                    LOGGER.fatal(
                            "Capitals for " + i + " Not found in Directory: " + ConfigLoader.config.capitals_directory);
                }
            }
        } else {
            ArrayList<Path> ps = PathTools.fileFilter(PathTools.asFolder(ProjectLoader.getScenario()),
                    PathTools.asFolder("worlds"), PathTools.asFolder("capitals"));
            for (int i = Timestep.getStartYear(); i <= Timestep.getEndtYear(); i++) {
                try {
                    capitals_directory.put(i, PathTools.fileFilter(ps, "_" + i, "capitals", ".csv").get(0));
                } catch (NullPointerException e) {
                    LOGGER.fatal(
                            "Capitals for " + i + " Not found in Directory: " + ConfigLoader.config.capitals_directory);
                }
            }
        }
    }

    @Override
    public void toSchedule() {
        modelRunner.scheduleRepeating(this);
    }

    @Override
    public void step() {

        Path path = capitals_directory.get(Timestep.getCurrentYear());
        LOGGER.info("Cells.updateCapitals" + path);
        CsvProcessors.processCSV(path, CsvKind.CAPITALS);

    }

    private void loadCapitalTypes(Map<String, List<String>> capitalsFile) {
        List<String> types = capitalsFile.get("Type");
        if (types == null) {
            if (ConfigLoader.config.separate_production_competitiveness) {
                LOGGER.fatal("Separate production and competitiveness requires "
                        + "the capital type to be specified in meta-data");
            }
            return;
        }
        List<String> names = getCapitalsList();
        for (int i = 0; i < names.size() && i < types.size(); i++) {
            String raw = types.get(i).trim();
            if (raw.equalsIgnoreCase("Capital")) {
                capitalTypes.put(names.get(i), "Capital");
            } else if (raw.equalsIgnoreCase("Suitability")) {
                capitalTypes.put(names.get(i), "Suitability");
            } else {
                LOGGER.warn("Unknown capital type '" + raw + "' for capital '"
                        + names.get(i) + "', defaulting to Suitability");
                capitalTypes.put(names.get(i), "Suitability");
            }
        }
    }

    public static Map<String, String> getCapitalTypes() {
        return capitalTypes;
    }

    public static boolean isSuitability(String capitalName) {
        return !"Capital".equals(capitalTypes.get(capitalName));
    }

    public static List<String> getCapitalsList() {
        return capitalsList;
    }

    public static void setCapitalsList(List<String> capitalsList) {
        CapitalUpdater.capitalsList = capitalsList;
    }

    public static Path getCapitalPath(int year) {
        return capitals_directory.getOrDefault(year, null);
    }

}
