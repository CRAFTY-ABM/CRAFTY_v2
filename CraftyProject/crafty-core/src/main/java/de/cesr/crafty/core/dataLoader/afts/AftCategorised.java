package de.cesr.crafty.core.dataLoader.afts;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.crafty.AftCategory;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;

/**
 * Optional AFT categorisation layer used to parameterise “give-in” behaviour between agent groups.
 *
 * This helper class enriches {@link Aft} instances with a {@link AftCategory} (category name + intensity level)
 * read from the AFT metadata file, and maintains fast lookup structures for:
 * 
 *   which AFTs belong to each category ({@link #aftCategories}),
 *   which intensity labels exist per category ({@link #CategoriesIntestisy}),
 *   display colours per category ({@link #categoriesColor}).
 * 
 *
 * If enabled via {@code config.use_AFTs_categories_GiveIn} and the metadata contains a {@code Category} column,
 * {@link #CategoriesLoader()} assigns each AFT a category and intensity (name + numeric level). This supports
 * behaviour rules that depend on whether a competitor is in the same category and/or at a higher/lower intensity.
 *
 * Category-specific give-in thresholds can also be loaded via {@link #initializeBehevoirByCategories()}.
 * This searches for two matrix CSV files (mean and standard deviation) describing give-in distributions between
 * category pairs. The matrices are stored as flattened maps (keyed by {@code rowLabel|colLabel}) in {@link #mean}
 * and {@link #SD}. When both matrices are available, {@link #useCategorisationGivIn} is set to {@code true} and
 * the competition logic may use these thresholds instead of per-AFT defaults.
 *
 * Notes:
 * 
 *   This module is intentionally optional: if configuration is off, metadata columns are missing, or files are
 *   not found, the model falls back to default give-in parameters defined per AFT.
 *   Most fields are static because categories are global, scenario-dependent state shared across the run.
 *
 */
/**
 * @author Mohamed Byari
 *
 */
public class AftCategorised {

	private static final CustomLogger LOGGER = new CustomLogger(AftCategorised.class);

	public static ConcurrentHashMap<String, Set<Aft>> aftCategories = new ConcurrentHashMap<>();

	public static ConcurrentHashMap<String, Set<String>> CategoriesIntestisy = new ConcurrentHashMap<>();
	public static ConcurrentHashMap<String, String> categoriesColor = new ConcurrentHashMap<>();

	private static HashMap<String, Double> mean = new HashMap<>();
	private static HashMap<String, Double> SD = new HashMap<>();
	public static boolean useCategorisationGivIn = false;

	private static boolean useCategories() {
		// if (ConfigLoader.config.use_AFTs_categories_GiveIn) {
		Map<String, List<String>> csv = CsvProcessors.ReadAsaHash(ProjectLoader.getAftMetaData());
		return csv.keySet().contains("Category");
		// }
		// return false;
	}

	public static void CategoriesLoader() {

		if (useCategories()) {
			Map<String, List<String>> csv = CsvProcessors.ReadAsaHash(ProjectLoader.getAftMetaData());
			for (int i = 0; i < csv.get("Category").size(); i++) {
				aftCategories.put(csv.get("Category").get(i), new HashSet<>());
				CategoriesIntestisy.put(csv.get("Category").get(i), new HashSet<>());
			}
			for (int i = 0; i < csv.get("Label").size(); i++) {
				Aft a = AFTsLoader.getAftHash().get(csv.get("Label").get(i));
				String ca = csv.get("Category").get(i);
				aftCategories.get(ca).add(a);
				categoriesColor.put(ca, csv.get("Category_Color").get(i));
				AftCategory category = new AftCategory(ca);
				a.setCategory(category);
				a.getCategory().setIntensity(csv.get("Intesity_name").get(i));
				a.getCategory().setIntensityLevel((int) Utils.sToD(csv.get("Intesity_level").get(i)));
				CategoriesIntestisy.get(ca).add(a.getCategory().getIntensity());
			}
		}
		LOGGER.info("AFT Categories= " + aftCategories.keySet());

		for (String c : aftCategories.keySet()) {
			StringJoiner joiner = new StringJoiner(", ", c + "= [", "]");
			for (Aft a : aftCategories.get(c)) {
				joiner.add(a.getLabel());
			}
			LOGGER.info(joiner.toString());
		}

	}

	public static void initializeBehevoirByCategories() {
		if (aftCategories.size() > 1) {
			ArrayList<Path> paths = PathTools.fileFilter(PathTools.asFolder("AFTs"), PathTools.asFolder("behaviour"),
					"categories_givingInDistribution");
			if (paths != null) {
				Path mean_path = paths.stream()
						.filter(path -> path.toString().contains("Mean_" + ProjectLoader.getScenario())).findFirst()
						.orElse(paths.stream().filter(path -> path.toString().contains("Mean_Default")).findFirst()
								.orElse(null));
				Path SD_path = paths.stream()
						.filter(path -> path.toString().contains("SD_" + ProjectLoader.getScenario())).findFirst()
						.orElse(paths.stream().filter(path -> path.toString().contains("SD_Default")).findFirst()
								.orElse(null));
				if (mean_path != null && SD_path != null) {
					mean = CsvProcessors.readCsvToMatrixMap(mean_path);
					SD = CsvProcessors.readCsvToMatrixMap(SD_path);
				}
			}
			useCategorisationGivIn = mean != null && SD != null;
		}
	}

	public static HashMap<String, Double> getMean() {
		return mean;
	}

	public static HashMap<String, Double> getSD() {
		return SD;
	}

}
