package de.cesr.crafty.institution.decision.llm.runtime;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.institution.model.InstitutionConfiguration;
import de.cesr.crafty.institution.model.InstitutionDefinition;
import de.cesr.crafty.institution.model.TargetDefinition;

public final class ParadigmRuntime {
	private String name;
	private Set<Cell> cells = ConcurrentHashMap.newKeySet();

	private Map<String, Set<Cell>> subRegions = new ConcurrentHashMap<>();// <region,cells>
	private Map<String, Integer> delay = new ConcurrentHashMap<>();// <region,delay>
	private Map<String, LlmInstitutionRuntime> institutes = new ConcurrentHashMap<>();// <name,institute>

	public ConcurrentHashMap<String, Double> supply = new ConcurrentHashMap<>();
	public ConcurrentHashMap<String, Double> supplyInitial = new ConcurrentHashMap<>();

	public ConcurrentHashMap<String, Integer> aftNbr = new ConcurrentHashMap<>();
	public ConcurrentHashMap<String, Integer> aftNbrInitial = new ConcurrentHashMap<>();

	public ParadigmRuntime(String name) {
		this.name = name;
	}

	public void setup(InstitutionConfiguration configuration) {
		initializeCells();
		setupInstitutes(configuration);
		initializeSupply();
	}

	public void setupRegionalInstitutes(Collection<InstitutionDefinition> definitions,
			Map<String, TargetDefinition> targets) {
		initializeCells();
		setupInstitutes(definitions, targets);
		initializeSupply();
	}

	public void setupAllCells(Collection<InstitutionDefinition> definitions,
			Map<String, TargetDefinition> targets, Collection<Cell> allCells) {
		cells.clear();
		cells.addAll(allCells);
		setupInstitutes(definitions, targets);
		initializeSupply();
	}

	private void setupInstitutes(Collection<InstitutionDefinition> definitions,
			Map<String, TargetDefinition> targets) {
		definitions.forEach(definition -> institutes.put(definition.id(),
				new LlmInstitutionRuntime(definition, targets, this)));
	}

	private void initializeCells() {
		cells.clear();
		subRegions.values().forEach(cells::addAll);
		System.out.println(name + ":  " + cells.size());
	}

	private void initializeSupply() {
		supply(supplyInitial);
		supply(supply);
		AFTnbr(aftNbrInitial);
		AFTnbr(aftNbr);
	}

	private void setupInstitutes(InstitutionConfiguration configuration) {
		configuration.institutions().values().stream()
				.filter(definition -> definition.scope().type() == de.cesr.crafty.institution.model.SpatialScope.Type.PARADIGM)
				.filter(definition -> definition.scope().name().equalsIgnoreCase(name))
				.forEach(definition -> institutes.put(definition.id(),
						new LlmInstitutionRuntime(definition, configuration.targets(), this)));
	}

	public void step1_preparePrompts() {
		supply(supply);
		AFTnbr(aftNbr);
		getInstitutes().values().forEach(LlmInstitutionRuntime::preparePrompt);
	}

	public void step2_connectLLMs() {
		getInstitutes().values().forEach(LlmInstitutionRuntime::connectLlm);
	}

	public void step3_appliedPolicies() {
		getInstitutes().values().forEach(LlmInstitutionRuntime::applyPolicies);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Map<String, Set<Cell>> getSubRegions() {
		return subRegions;
	}

	public Map<String, Integer> getDelay() {
		return delay;
	}

	@Override
	public String toString() {
		final int maxLen = 3;
		return "Paradigm [name=" + name + ", subRegions="
				+ (subRegions != null ? toString(subRegions.entrySet(), maxLen) : null) + ", delay="
				+ (delay != null ? toString(delay.entrySet(), maxLen) : null) + "]";
	}

	private String toString(Collection<?> collection, int maxLen) {
		StringBuilder builder = new StringBuilder();
		builder.append("[");
		int i = 0;
		for (Iterator<?> iterator = collection.iterator(); iterator.hasNext() && i < maxLen; i++) {
			if (i > 0) {
				builder.append(", ");
			}
			builder.append(iterator.next());
		}
		builder.append("]");
		return builder.toString();
	}

	public Set<Cell> getCells() {
		return cells;
	}

	public void setCells(Set<Cell> cells) {
		this.cells = cells;
	}

	public Map<String, LlmInstitutionRuntime> getInstitutes() {
		return institutes;
	}

	public void AFTnbr(ConcurrentHashMap<String, Integer> nbr) {
		nbr.clear();
		cells.forEach(c -> {
			if (c.getOwner() != null)
				nbr.merge(c.getOwner().getLabel(), 1, Integer::sum);
			else {
				nbr.merge("Abandoned", 1, Integer::sum);
			}
		});
		AFTsLoader.getAftHash().keySet().forEach(aftName -> {
			nbr.putIfAbsent(aftName, 0);
		});

	}

	public void supply(ConcurrentHashMap<String, Double> supply) {
		supply.clear();
		cells.forEach(c -> {
			for (int i = 0; i < ServiceSet.getServicesList().size(); i++) {
				supply.merge(ServiceSet.getServicesList().get(i), c.getCurrentProd()[i], Double::sum);
			}
		});
	}

}
