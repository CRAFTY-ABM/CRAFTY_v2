package newCoupler;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.main.MainHeadless;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.file.DirectoryWatcher;
import de.cesr.crafty.core.utils.file.PathTools;

public class Mapper {

//	private static final CustomLogger LOGGER = new CustomLogger(Mapper.class);

	static ArrayList<Path> allpaths;
	Plum_Listener plum_listner;
	Plum_To_Crafty_Mapper plum_To_Crafty_Mapper;
	CraftyListener crafty_listner;
	Crafty_To_Plum_Mapper crafty_To_Plum_Mapper;
	Map<String, String> countryLongToShortName = new HashMap<>();
	Map<String, String> countryShortToLongName = new HashMap<>();
	boolean connected = false;

	public void setup() {
		Eu_countries();
		plum_listner = new Plum_Listener(this);
		plum_To_Crafty_Mapper = new Plum_To_Crafty_Mapper(this);
		crafty_listner = new CraftyListener(this);
		crafty_To_Plum_Mapper = new Crafty_To_Plum_Mapper(this);
		allpaths = PathTools.findAllFilePaths(Paths.get(ConfigLoader.config.plumCalibPath));
		plum_listner.setup();
		plum_To_Crafty_Mapper.setup();
		crafty_listner.setup();
		crafty_To_Plum_Mapper.setup();
	}

	public void step() {
//	# 	Plum Calibration should be rann before and domistic + wood should be done.
//		run initial supply based on that
//		genearte crafty2020
//		run plum initial state 2020

//		crafty wait for the flag from Plum
		if (connected) {
			DirectoryWatcher.waitForYearFolder(Paths.get(ConfigLoader.config.plumOutPutPath + File.separator
					+ Timestep.getCurrentYear() + File.separator + "done"));
		}
		allpaths.clear();
		allpaths = PathTools.findAllFilePaths(Paths.get(ConfigLoader.config.plumOutPutPath));
//		import comos price and demands from Plum domistic + wood
		plum_listner.step();
//		map comos to services and inject price (+ demands ?)
		plum_To_Crafty_Mapper.step();

//		run one step of crafty and go to next iteration
		MainHeadless.runner.step();
//		coumpute supply by contry and generate facroed supply to match with plum 
		crafty_listner.step();
//		map service supply to comos (production, rum, Mon and inport) and generate .csv file as plum input as (t+1).
		crafty_To_Plum_Mapper.step();
//		wait for plum to generate next iteration
		// add done geneated file in plum output and add flag waiting in crafty
	}

	private void Eu_countries() {
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

}
