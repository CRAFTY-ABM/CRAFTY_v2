package large_language_models_institutions;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import cli.InstituteYamlLoader;
import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.utils.file.PathTools;

public class Paradigm {

	private static final CustomLogger LOGGER = new CustomLogger(Paradigm.class);
	private String name;
	private Set<Cell> cells = ConcurrentHashMap.newKeySet();

	private Map<String, Set<Cell>> subRegions = new ConcurrentHashMap<>();// <region,cells>
	private Map<String, Integer> delay = new HashMap<>();// <region,delay>
	private Map<String, Institute> institutes = new HashMap<>();// <name,institute>

	public Paradigm(String name) {
		this.name = name;
	}

	public void setup() {
		subRegions.values().forEach(set -> {
			getCells().addAll(set);
		});
		System.out.println(name + ":  " + getCells().size());
		setup_instutites();
//		institutes.values().forEach(i->{
//			System.out.println(i.getBase_prompts()+"-------------- \n\n\n");
//		});
	}

	public void step() {
		institutes.values().forEach(institute -> {
			institute.step();
		});
	}

	private void setup_instutites() {

		ArrayList<Path> list = PathTools.findAllFilePaths(Paths.get(ConfigLoader.config.institutions_directory));
		ArrayList<Path> path = PathTools.fileFilter(list, "institutes_config", name, ".yaml");
		if (path == null || path.isEmpty()) {
			LOGGER.fatal("Configuration file not found: expected file  institutes_config_<paradigm_Name>.Yaml");
		}

		List<Institute> instituteList = InstituteYamlLoader.loadInstitutes(path.getFirst(), Targets_Set.getTargets(),
				this);
		instituteList.forEach(inst -> {
			institutes.put(inst.getName(), inst);
		});

//List<File> instututionsFiles = PathTools
//				.detectFolders(ConfigLoader.config.institutions_directory + PathTools.asFolder("institutes"));
//		instututionsFiles.forEach(dir -> {
//			Institute inst = new Institute(dir.getName().replace("institute@", "").toLowerCase(), name);
//			inst.setCells(cells);
//			inst.setup(dir.toPath(), name);
//			institutes.put(inst.getName(), inst);
//		});
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

}
