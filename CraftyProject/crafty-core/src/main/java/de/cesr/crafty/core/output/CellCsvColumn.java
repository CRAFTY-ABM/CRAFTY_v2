package de.cesr.crafty.core.output;


public enum CellCsvColumn {
	ID("ID"),
	X("X"),
	Y("Y"),
	AGENT_ID("AFT_id"),
	AGENT("Agent"),
	UTILITY("utility"),
	OWNER_LIFE_COUNTER("owner_life_counter"),

	SERVICES("services"),
	CAPITALS("capitals"),
	SERVICES_TAXES("service_taxes"),
	AFT_TAXES("aft_taxes");

	private final String header;

	CellCsvColumn(String header) {
		this.header = header;
	}

	public String getHeader() {
		return header;
	}
}