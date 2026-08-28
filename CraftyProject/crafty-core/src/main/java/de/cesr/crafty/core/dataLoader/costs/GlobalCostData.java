package de.cesr.crafty.core.dataLoader.costs;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.CsvProcessors;

public class GlobalCostData {
	private static final CustomLogger LOGGER = new CustomLogger(GlobalCostData.class);

	private double nfertUnitCost = 0.0;
	private Map<String, Double> intensityCosts = new HashMap<>();

	public GlobalCostData(Path globalCostsCsv) {
		Map<String, List<String>> csv = CsvProcessors.ReadAsaHash(globalCostsCsv);
		if (csv == null) {
			LOGGER.fatal("Failed to read global_costs.csv from: " + globalCostsCsv);
			return;
		}
		List<String> items = csv.get("Item");
		List<String> costs = csv.get("Cost");
		if (items == null || costs == null) {
			LOGGER.fatal("global_costs.csv must have 'Item' and 'Cost' columns");
			return;
		}
		for (int i = 0; i < items.size(); i++) {
			String item = items.get(i).trim();
			double cost = Double.parseDouble(costs.get(i).trim());
			if (item.equalsIgnoreCase("Nfert")) {
				nfertUnitCost = cost;
			} else {
				intensityCosts.put(item, cost);
			}
		}
		LOGGER.info("Loaded global costs: Nfert=" + nfertUnitCost
				+ ", intensity items=" + intensityCosts.keySet());
	}

	public double getNfertUnitCost() {
		return nfertUnitCost;
	}

	public Map<String, Double> getIntensityCosts() {
		return intensityCosts;
	}
}
