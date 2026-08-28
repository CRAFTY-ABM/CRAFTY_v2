package de.cesr.crafty.core.utils.general;

import java.util.ArrayList;
import java.util.List;

public class MapPngConfig {
	public boolean enabled = false;

	public List<String> single_value = new ArrayList<>();
	public List<List<String>> dual_value = new ArrayList<>();
	public List<List<String>> triple_value = new ArrayList<>();

	public void initialize() {
		if (single_value == null) {
			single_value = new ArrayList<>();
		}
		if (dual_value == null) {
			dual_value = new ArrayList<>();
		}
		if (triple_value == null) {
			triple_value = new ArrayList<>();
		}
	}


	@Override
	public String toString() {
		return "MapPngConfig{" + "enabled=" + enabled + ",\n single=" + single_value + ",\n dual=" + dual_value + ",\n triple="
				+ triple_value + '}';
	}
}
