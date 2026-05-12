package large_language_models_institutions;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.CapitalUpdater;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;

public class Paradigms_Set {

	public static ConcurrentHashMap<String, Paradigm> paradigms = new ConcurrentHashMap<>();

	public void setup() {
		List<Path> instututionsFiles = PathTools
				.findAllFilePaths(Paths.get(ConfigLoader.config.institutions_directory));
		Path p = PathTools.fileFilter(instututionsFiles, true, "paradigms.csv").get(0);
		Map<String, List<String>> csv = CsvProcessors.ReadAsaHash(p);
		for (int i = 0; i < csv.get("Paradigm").size(); i++) {
			String paradimName = csv.get("Paradigm").get(i);
			String code = csv.get("Region_Code").get(i);
			paradigms.putIfAbsent(paradimName, new Paradigm(paradimName));
			paradigms.get(paradimName).getSubRegions().put(code, ConcurrentHashMap.newKeySet());
			paradigms.get(paradimName).getDelay().put(code, Utils.sToI(csv.get("Delay").get(i)));
		}
		cellsToParadigm();
//		paradigms.forEach((k, v) -> {
//			v.getSubRegions().forEach((kk, vv) -> {
//				System.out.println(k + ", " + kk + ":  " + vv.size());
//			});
//		});
	}

	public void step() {
		clearOldTaxes();
		paradigms.values().forEach(p -> {
			p.step();
		});
	}

	private void clearOldTaxes() {
		CellsLoader.hashCell.values().forEach(c -> {
			ServiceSet.getServicesList().forEach(serviceName -> c.getServicesTax().put(serviceName, 0.));
			AFTsLoader.getAftHash().keySet().forEach(aftName -> c.getLandTax().put(aftName, 0.));
			CapitalUpdater.getCapitalsList().forEach(capitalName -> c.getCapitalsAdjusment().put(capitalName, 0.));
		});

	}

	private void cellsToParadigm() {
		CellsLoader.hashCell.values().forEach(c -> {
			for (Paradigm p : paradigms.values()) {
				if (c.getCurrentRegion() != null)
					if (p.getSubRegions().containsKey(c.getCurrentRegion())) {
						p.getSubRegions().get(c.getCurrentRegion()).add(c);
						break;
					}
			}
		});
		for (Paradigm p : paradigms.values()) {
			p.setup();
		}
	}

}
