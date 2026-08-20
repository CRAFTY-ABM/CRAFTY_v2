package de.cesr.crafty.institution.decision.llm.runtime;

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
import de.cesr.crafty.institution.runtime.CellPolicyState;
import de.cesr.crafty.institution.model.InstitutionConfiguration;
import de.cesr.crafty.institution.model.DecisionEngineType;
import de.cesr.crafty.institution.model.InstitutionDefinition;
import de.cesr.crafty.institution.model.SpatialScope;

public final class ParadigmRuntimeSet implements AutoCloseable {
	private static final CustomLogger LOGGER = new CustomLogger(ParadigmRuntimeSet.class);
	static final String ALL_CELLS_RUNTIME_KEY = "scope:all_cells";
	static final String ALL_CELLS_RUNTIME_NAME = "all";
	public static ConcurrentHashMap<String, ParadigmRuntime> paradigms = new ConcurrentHashMap<>();

	private ExecutorService llmExecutor;

	@Override
	public void close() throws Exception {
		if (llmExecutor != null) {
			llmExecutor.shutdown();
		}
	}

	public void setup(InstitutionConfiguration configuration) {
		java.util.Objects.requireNonNull(configuration, "configuration");
		paradigms.clear();
		boolean needsSpatialMapping = configuration.institutions().values().stream()
				.filter(definition -> definition.decisionEngine().type() == DecisionEngineType.LLM)
				.anyMatch(definition -> definition.scope().type() != SpatialScope.Type.ALL_CELLS);
		if (needsSpatialMapping) {
			loadParadigms();
			cellsToParadigm(configuration);
			setupRegionalInstitutions(configuration);
		}
		setupAllCellInstitutions(configuration);
		int size = 0;
		for (ParadigmRuntime paradigm : paradigms.values()) {
			size += paradigm.getInstitutes().size();
		}
		llmExecutor = Executors.newFixedThreadPool(Math.max(1, size));
	}

	private void loadParadigms() {
		List<Path> institutionFiles = PathTools
				.findAllFilePaths(Paths.get(ConfigLoader.config.institutions_directory));
		Path paradigmsFile = PathTools.fileFilter(institutionFiles, true, "paradigms.csv").get(0);
		Map<String, List<String>> csv = CsvProcessors.ReadAsaHash(paradigmsFile);
		for (int i = 0; i < csv.get("Paradigm").size(); i++) {
			String paradigmName = csv.get("Paradigm").get(i);
			String code = csv.get("Region_Code").get(i);
			paradigms.putIfAbsent(paradigmName, new ParadigmRuntime(paradigmName));
			paradigms.get(paradigmName).getSubRegions().put(code, ConcurrentHashMap.newKeySet());
			paradigms.get(paradigmName).getDelay().put(code, Utils.sToI(csv.get("Delay").get(i)));
		}
	}

	private void setupAllCellInstitutions(InstitutionConfiguration configuration) {
		List<InstitutionDefinition> definitions = configuration.institutions().values().stream()
				.filter(definition -> definition.decisionEngine().type() == DecisionEngineType.LLM)
				.filter(definition -> definition.scope().type() == SpatialScope.Type.ALL_CELLS)
				.toList();
		if (definitions.isEmpty()) {
			return;
		}
		ParadigmRuntime runtime = new ParadigmRuntime(ALL_CELLS_RUNTIME_NAME);
		runtime.setupAllCells(definitions, configuration.targets(), CellsLoader.hashCell.values());
		paradigms.put(ALL_CELLS_RUNTIME_KEY, runtime);
	}

	public void step() {
		clearOldTaxes();

		// Take a stable snapshot of the paradigms for this step
		List<ParadigmRuntime> currentParadigms = new ArrayList<>(paradigms.values());

		// Step 1: fast, sequential
		currentParadigms.forEach(p -> {
			p.step1_preparePrompts();
		});

		// Step 2: slow LLM calls, parallel
		List<CompletableFuture<Void>> llmTasks = currentParadigms.stream()
				.map(paradigm -> CompletableFuture.runAsync(paradigm::step2_connectLLMs, llmExecutor)
						.orTimeout(5, TimeUnit.MINUTES)).toList();

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
		LOGGER.info("Clear Old Policies from all cells..");
		CellPolicyState.clear(CellsLoader.hashCell.values());
	}

	private void setupRegionalInstitutions(InstitutionConfiguration configuration) {
		Map<String, List<InstitutionDefinition>> byScope = configuration.institutions().values().stream()
				.filter(definition -> definition.decisionEngine().type() == DecisionEngineType.LLM)
				.filter(definition -> definition.scope().type() == SpatialScope.Type.REGIONS)
				.collect(java.util.stream.Collectors.groupingBy(
						definition -> String.join(",", definition.scope().regions()),
						java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()));

		byScope.forEach((scopeKey, definitions) -> {
			List<String> regions = definitions.getFirst().scope().regions();
			ParadigmRuntime runtime = new ParadigmRuntime(String.join("+", regions));
			for (String region : regions) {
				ParadigmRuntime owner = paradigms.values().stream()
						.filter(candidate -> candidate.getSubRegions().containsKey(region)).findFirst()
						.orElseThrow(() -> new IllegalArgumentException(
								"Region '" + region + "' is missing from paradigms.csv"));
				runtime.getSubRegions().put(region, owner.getSubRegions().get(region));
				runtime.getDelay().put(region, owner.getDelay().getOrDefault(region, 0));
			}
			runtime.setupRegionalInstitutes(definitions, configuration.targets());
			paradigms.put("regions:" + scopeKey, runtime);
		});
	}

	private void cellsToParadigm(InstitutionConfiguration configuration) {
		CellsLoader.hashCell.values().forEach(c -> {
			for (ParadigmRuntime p : paradigms.values()) {
				if (c.getCurrentRegion() != null)
					if (p.getSubRegions().containsKey(c.getCurrentRegion())) {
						p.getSubRegions().get(c.getCurrentRegion()).add(c);
						break;
					}
			}
		});
		for (ParadigmRuntime p : paradigms.values()) {
			p.setup(configuration);
		}
	}

}
