package de.cesr.crafty.core.output;

import java.io.File;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.crafty.CellBehaviour;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.CapitalUpdater;
import de.cesr.crafty.core.updaters.CellBehaviourUpdater;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.graphics.Dual_value_PNG;
import de.cesr.crafty.core.utils.graphics.MapPngExporter;
import de.cesr.crafty.core.utils.graphics.MapPngSpecHandler;
import de.cesr.crafty.core.utils.graphics.Triple_value_PNG;

public class PngGenerator {

	private static MapPngSpecHandler.Registry registry = new MapPngSpecHandler.Registry() {

		@Override
		public List<String> serviceNames() {
			return ServiceSet.getServicesList();
		}

		@Override
		public List<String> capitalNames() {
			return CapitalUpdater.getCapitalsList();
		}

		@Override
		public List<String> behaviourParameterNames() {
			return CellBehaviour.parameterNames();
		}

		@Override
		public ToDoubleFunction<Cell> serviceExtractor(String serviceName) {
			return c -> c.getCurrentProd()[ServiceSet.getServicesList().indexOf(serviceName)];
		}

		@Override
		public ToDoubleFunction<Cell> capitalExtractor(String capitalName) {
			return c -> c.getCapitals().getOrDefault(capitalName, Double.NaN)
					* (1 + c.getCapitalsAdjusment().getOrDefault(capitalName, 0.));
		}

		@Override
		public ToDoubleFunction<Cell> behaviourParameterExtractor(String parameterName) {
			// Validate the parameter while building the request rather than during pixel export.
			new CellBehaviour(null).parameterValue(parameterName);
			return c -> {
				CellBehaviour behaviour = CellBehaviourUpdater.cellBehaviours.get(c);
				return behaviour == null ? Double.NaN : behaviour.parameterValue(parameterName);
			};
		}

		@Override
		public ToDoubleFunction<Cell> serviceTaxExtractor(String serviceName) {
			return c -> c.getServicesTax().getOrDefault(serviceName, Double.NaN);
		}

		@Override
		public ToDoubleFunction<Cell> landTaxExtractor() {
			return c -> c.getLandTax().getOrDefault(c.getOwnerName(), 0d);
		}

		@Override
		public ToDoubleFunction<Cell> competitivnessExtractor() {
			return c -> c.getCurrentUtility();
		}

		@Override
		public ToDoubleFunction<Cell> ownerLifeCounterExtractor() {
			return c -> c.getOwnerLifeCounter();
		}

		@Override
		public Function<Cell, String> ownerExtractor() {
			return c -> c.getOwnerName();
		}

	};
	public static MapPngSpecHandler handler = new MapPngSpecHandler(registry);

	public static void generatePNGs() {
		String outDir = PathTools.makeDirectory(ConfigLoader.config.output_folder_name + File.separator + "MapsPlots");

		List<MapPngSpecHandler.SingleMapRequest> singles = handler
				.buildSingleRequests(ConfigLoader.config.map_png.single_value);

		if (!singles.isEmpty()) {
			String singlespath = PathTools.makeDirectory(outDir + File.separator + "singles_value_maps");
			for (MapPngSpecHandler.SingleMapRequest req : singles) {
				if (!req.outputName.equalsIgnoreCase("owner")) {
					MapPngExporter.exportNumericCellMapAsPng(new File(singlespath), CellsLoader.hashCell,
							req.outputName, req.value.doubleExtractor);
				} else {
					MapPngExporter.exportOwnerMapAsPng(new File(outDir), CellsLoader.hashCell, "AFTs_maps");
				}
			}
		}
		List<MapPngSpecHandler.DualMapRequest> duals = handler
				.buildDualRequests(ConfigLoader.config.map_png.dual_value);
		if (!duals.isEmpty()) {
			String dualsImage = PathTools.makeDirectory(outDir + File.separator + "duals_value_maps");
			for (MapPngSpecHandler.DualMapRequest req : duals) {
				Dual_value_PNG.exportBivariateMapAsPng(
						new File(PathTools.makeDirectory(dualsImage + File.separator + req.outputName)),
						CellsLoader.hashCell, req.outputName, req.value1.doubleExtractor, req.value2.doubleExtractor,
						req.value1.label, req.value2.label);
			}
		}

		List<MapPngSpecHandler.TripleMapRequest> triples = handler
				.buildTripleRequests(ConfigLoader.config.map_png.triple_value);
		if (!triples.isEmpty()) {
			String triplesImage = PathTools.makeDirectory(outDir + File.separator + "triple_value_maps");
			for (MapPngSpecHandler.TripleMapRequest req : triples) {
				Triple_value_PNG.exportRgbMapAsPng(
						new File(PathTools.makeDirectory(triplesImage + File.separator + req.outputName)),
						CellsLoader.hashCell, req.outputName, req.red.doubleExtractor, req.green.doubleExtractor,
						req.blue.doubleExtractor, req.red.label, req.green.label, req.blue.label);
			}
		}

	}
}
