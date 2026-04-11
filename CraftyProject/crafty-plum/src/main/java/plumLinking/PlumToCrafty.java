package plumLinking;

import de.cesr.crafty.core.crafty.Region;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.RegionsModelRunnerUpdater;
import de.cesr.crafty.core.updaters.Timestep;

public class PlumToCrafty {
	PlumCommodityMapping mapper = new PlumCommodityMapping();

	public void initialize() {
		mapper.initialize();
		mapper.fromPlumDataToDemandsAndPrice(Timestep.getStartYear());
		replaceCraftyDemands(Timestep.getStartYear());

		RegionsModelRunnerUpdater.regionsModelRunner.values().forEach(rRunner -> {
			rRunner.initialDSEquilibriumFactorCalculation();
		});
	}

	public void iterative(int year) {
		System.out.println("mapper.fromPlumTickToCraftyDemands(year)..." + year);
		mapper.fromPlumDataToDemandsAndPrice(year);
		System.out.println("	replaceCraftyDemands(year);..." + year);
		replaceCraftyDemands(year);
		// updateCalibrator();
	}

	void replaceCraftyDemands(int year) {
		int y = year - Timestep.getStartYear();
		mapper.finalCountriesDemands.forEach((country, map) -> {
			if (CellsLoader.regions.keySet().contains(country)) {
				map.forEach((serviceName, value) -> {
					if (ServiceSet.getServicesList().contains(serviceName)) {
						Region R = CellsLoader.regions.get(country);
						R.getServicesHash().get(serviceName).getDemands().put(y, value);
						
					}
				});
			}
		});
	}

//
//	public void updateCalibrator() {
//		ModelRunner.regionsModelRunner.values().forEach(rRunner -> {
//			rRunner.initialDSEquilibrium();
//		});
//	}

}
