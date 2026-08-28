package de.cesr.crafty.gui.institutes;

import java.util.Map;

import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.institution.runtime.CellPolicyState;

public class Institutes_Set {
	public static void initialize() {
		GuiInstitutionBootstrap.ensureInitialized();
	}

	public static Map<String, InstitutionViewModel> getInstitutes() {
		return GuiInstitutionBootstrap.institutes();
	}

	public static void stepPolicies() {
		CellPolicyState.clear(CellsLoader.hashCell.values());
		getInstitutes().values().forEach(institute ->
				institute.getPolicies().values().forEach(PolicyViewModel::step));
	}

	public static void resetRuntimeState() {
		CellPolicyState.clear(CellsLoader.hashCell.values());
		getInstitutes().values().forEach(InstitutionViewModel::resetRuntimeState);
	}
}
