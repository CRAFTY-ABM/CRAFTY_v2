package large_language_models_institutions;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;

public class Paradigms_Set implements AutoCloseable {
	private static final CustomLogger LOGGER = new CustomLogger(Paradigms_Set.class);
	public static ConcurrentHashMap<String, Paradigm> paradigms = new ConcurrentHashMap<>();

	private ExecutorService llmExecutor;

	@Override
	public void close() throws Exception {
		llmExecutor.shutdown();
	}

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
		int size = 0;
		for (Paradigm paradigm : paradigms.values()) {
			size += paradigm.getInstitutes().size();
		}
		llmExecutor = Executors.newFixedThreadPool(size);
	}

	public void step() {
//		clearOldTaxes();

		// Take a stable snapshot of the paradigms for this step
		List<Paradigm> currentParadigms = new ArrayList<>(paradigms.values());

		// Step 1: fast, sequential
		currentParadigms.forEach(p -> {
			p.step1_preparePrompts();
		});

		// Step 2: slow LLM calls, parallel
		List<CompletableFuture<Void>> llmTasks = currentParadigms.stream().map(p -> CompletableFuture.runAsync(() -> {
			try {
				p.step2_connectLLMs();
			} catch (Exception e) {
				throw new CompletionException("LLM  call failed for paradigm: " + p.getName(), e);
			}
		}, llmExecutor).orTimeout(5, TimeUnit.MINUTES)).toList();

		// Barrier: wait until all LLM responses are finished
		try {
			CompletableFuture.allOf(llmTasks.toArray(new CompletableFuture[0])).join();
		} catch (CompletionException e) {
			LOGGER.error("At least one LLM call failed. Policies were not applied..." + e);
		}

		// Step 3: fast, sequential, only after all LLMs responded
		currentParadigms.forEach(p -> {
			p.step3_appliedPolicies();
		});
	}

	private void clearOldTaxes() {
		LOGGER.info("Clear Old Policies..");
		CellsLoader.hashCell.values().forEach(c -> {
			c.getServicesTax().clear();
			c.getLandTax().clear();
			c.getCapitalsAdjusment().clear();
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
