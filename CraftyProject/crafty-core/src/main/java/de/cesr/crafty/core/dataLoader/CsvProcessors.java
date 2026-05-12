package de.cesr.crafty.core.dataLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.CapitalUpdater;
import de.cesr.crafty.core.utils.general.Utils;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.AddCellToColumnException;
import tech.tablesaw.io.csv.CsvReadOptions;

/**
 * CSV parsing and processing utilities used during model initialization and updates.
 *
 * This class provides two complementary approaches:
 * 1) Table-style loading via Tablesaw ({@link #ReadAsaHash(Path)} / {@link #ReadAsaHashDouble(Path)}),
 *    which reads a CSV into a column-oriented map (header -> list of values).
 * 2) Streaming line-by-line processing via {@link #processCSV(Path, CsvKind)}, which is optimized for
 *    large files: it reads the header once, builds a column index, then processes remaining rows in parallel
 *    and delegates row handling to a {@link CsvKind} implementation.
 *
 * The streaming pathway is used for performance-critical imports (e.g., baseline cells, capitals, outputs),
 * where each row is parsed and immediately applied to the model state without building a full in-memory table.
 *
 * Core model bindings (package-private helpers):
 * - {@link #createCells(Map, String)}: instantiate cells from baseline-like CSV rows and assign initial owners.
 * - {@link #associateCapitalsToCells(Map, String)}: attach capital values to existing cells.
 * - {@link #associateCapitalsDegradationToCells(Map, String)}: apply degradation factors and update cell capitals
 *   while storing per-cell shocks in {@link Capital_Degradation_Updater}.
 * - {@link #associateOutPutServicesToCells(Map, String)}: restore cell owners and service production from output CSV.
 *
 * Implementation notes:
 * - Column indexing is case-insensitive and strips quotes; indices are stored in an unmodifiable map
 *   (built once per file) to make the read-only intent explicit.
 * - Streaming parsing uses a precompiled comma splitter; it is fast but assumes simple comma-separated input
 *   (it does not fully implement RFC-style quoted CSV in the streaming path).
 * - The parallel stream in {@link #processCSV(Path, CsvKind)} assumes that the target {@link CsvKind#apply}
 *   implementation writes to thread-safe structures (e.g., ConcurrentHashMap) or otherwise handles concurrency.
 */

/**
 * @author Mohamed Byari
 *
 */

public class CsvProcessors {
	private static final CustomLogger LOGGER = new CustomLogger(CsvProcessors.class);

	private static final Pattern COMMA = Pattern.compile(",", Pattern.LITERAL);

	public static Map<String, List<Double>> ReadAsaHashDouble(Path filePath) {
		Map<String, List<String>> str = ReadAsaHash(filePath);
		Map<String, List<Double>> dou = new HashMap<>();
		str.forEach((k, list) -> {
			List<Double> tmp = new ArrayList<>();
			list.forEach(s -> {
				tmp.add(Utils.sToD(s));
			});
			dou.put(k, tmp);
		});
		return dou;
	}

	public static Map<String, List<String>> ReadAsaHash(Path filePath) {
		return ReadAsaHash(filePath, false);
	}

	public static Map<String, List<String>> ReadAsaHash(Path filePath, boolean ignoreIfFileNotExists) {
		LOGGER.info("Reading : " + filePath);
		Map<String, List<String>> hash = new HashMap<>();
		Table T = null;
		try {
			CsvReadOptions options = CsvReadOptions.builder(filePath.toFile()).separator(',').build();
			T = Table.read().usingOptions(options);
			LOGGER.trace(T.print());
		} catch (AddCellToColumnException s) {
			LOGGER.error(s.getMessage());
			return null;
		} catch (Exception e) {
			if (ignoreIfFileNotExists) {
				LOGGER.error(e.getMessage() + " \n     return null");
				return null;
			} else {
				e.printStackTrace();
				LOGGER.error(e.getMessage() + " \n     return null");
				// filePath = WarningWindowes.alterErrorNotFileFound("The file path could not be
				// found:", filePath);
				// T = Table.read().csv(filePath);
			}
		}
		if (T == null) {
			LOGGER.error("Error in reading file:" + filePath);
			return null;
		}
		List<String> columnNames = T.columnNames();

		for (Iterator<String> iterator = columnNames.iterator(); iterator.hasNext();) {
			String name = String.valueOf(iterator.next());
			ArrayList<String> tmp = new ArrayList<String>();
			for (int i = 0; i < T.column(name).size(); i++) {
				Object s = T.column(name).get(i);
				if (s instanceof Double) {
					tmp.add(String.valueOf(s));
				} else {
					tmp.add(T.column(name).getString(i));
				}
			}
			hash.put(name, tmp);
		}
		return hash;
	}

	public static void processCSV(Path file, CsvKind kind) {
		LOGGER.info("Importing data for " + kind + " from : " + file + "...");
		try (Stream<String> lines = Files.lines(file)) {
			Iterator<String> it = lines.iterator();
			String row0 = it.next();
			if (kind.equals(CsvKind.BASELINE)) {
				row0 = row0.replace("AFT", "FR").replace("Agent", "FR").replace("Owner", "FR").replace("Agents", "FR");
			}
			Map<String, Integer> index = buildIndex(row0);

			/* the remaining lines are processed in parallel */
			StreamSupport.stream(Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED), true) // true = parallel
					.forEach(line -> kind.apply(line, index));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static Map<String, Integer> buildIndex(String headerLine) {
		String[] cols = COMMA.split(headerLine, -1);
		Map<String, Integer> idx = new HashMap<>(cols.length);
		for (int i = 0; i < cols.length; i++) {
			// trim in case the file has “X, Y ,Z” with spaces
			idx.put(cols[i].trim().toUpperCase().replace("\"", ""), i);
		}
		/*
		 * The parsing thread is the only writer; thereafter the map is read-only, so we
		 * wrap it to make the intention explicit.
		 */
		return Collections.unmodifiableMap(idx);
	}

//	private static ConcurrentHashMap<String, String> tmp = new ConcurrentHashMap<>();

//	static void associateCapitalsToCells(Map<String, Integer> indexof, String data) {
//
//		List<String> immutableList = Collections.unmodifiableList(Arrays.asList(COMMA.split(data, -1)));
//		int x = (int) Utils.sToD(immutableList.get(indexof.get("X")));
//		int y = (int) Utils.sToD(immutableList.get(indexof.get("Y")));
//		Cell c = CellsLoader.getCell(x, y);
//		if (c != null) {
//			CapitalUpdater.getCapitalsList().forEach(capital_name -> {
//				if (indexof.get(capital_name.toUpperCase()) == null) {
//					c.getCapitals().put(capital_name, 0.);
//				} else if (indexof.get(capital_name.toUpperCase()) < immutableList.size()) {
//					double capital_value = Utils.sToD(immutableList.get(indexof.get(capital_name.toUpperCase())));
//					c.getCapitals().put(capital_name, capital_value);
//				}
//			});
//		}
//	}
	
	static void associateCapitalsToCells(Map<String, Integer> indexof, String data) {

	    String[] row = COMMA.split(data, -1);

	    int x = (int) Utils.sToD(row[indexof.get("X")]);
	    int y = (int) Utils.sToD(row[indexof.get("Y")]);

	    Cell c = CellsLoader.getCell(x, y);
	    if (c == null) {
	        return;
	    }

	    Map<String, Double> capitals = c.getCapitals();

	    for (String capitalName : CapitalUpdater.getCapitalsList()) {

	        Integer col = indexof.get(capitalName.toUpperCase());

	        if (col == null) {
	            capitals.put(capitalName, 0.0);
	            continue;
	        }

	        if (col < row.length) {
	            double capitalValue = Utils.sToD(row[col]);
	            capitals.put(capitalName, capitalValue);
	        }
	    }
	}

	static void associateCapitalsDegradationToCells(Map<String, Integer> indexof, String data) {

		List<String> immutableList = Collections.unmodifiableList(Arrays.asList(COMMA.split(data, -1)));
		int x = (int) Utils.sToD(immutableList.get(indexof.get("X")));
		int y = (int) Utils.sToD(immutableList.get(indexof.get("Y")));
		Cell c = CellsLoader.getCell(x, y);
		if (c != null) {
			CapitalUpdater.getCapitalsList().forEach(capital_name -> {
				if (indexof.get(capital_name.toUpperCase()) != null) {
					double degradation_value = Utils.sToD(immutableList.get(indexof.get(capital_name.toUpperCase())));

					try {
						c.getCapitals().put(capital_name, c.getCapitals().get(capital_name) * (1 - degradation_value));
					} catch (NullPointerException e) {
						LOGGER.warn("Problem in Capitals for cell (" + c.getX() + " , " + c.getY()
								+ "): capitals values = " + c.getCapitals());
					}
				}
			});
		}
	}

//	static void associateCapitalsDegradationToCells(Map<String, Integer> indexof, String data) {
//
//		String[] row = COMMA.split(data, -1);
//		int x = (int) Utils.sToD(row[indexof.get("X")]);
//		int y = (int) Utils.sToD(row[indexof.get("Y")]);
//
//		Cell c = CellsLoader.getCell(x, y);
//		if (c == null) {
//			return;
//		}
//
//		Map<String, Double> capitals = c.getCapitals();
//		for (String capitalName : CapitalUpdater.getCapitalsList()) {
//			Integer col = indexof.get(capitalName.toUpperCase());
//			if (col == null) {
//				continue;
//			}
//			Double currentValue = capitals.get(capitalName);
//			if (currentValue == null) {
//				LOGGER.warn("Problem in Capitals for cell (" + c.getX() + " , " + c.getY() + "): missing capital "
//						+ capitalName + ", capitals values = " + capitals);
//				continue;
//			}
//
//			double degradationValue = Utils.sToD(row[col]);
//			capitals.put(capitalName, currentValue * (1.0 - degradationValue));
//		}
//	}

	static void createCells(Map<String, Integer> indexof, String data) {
		List<String> immutableList = Collections.unmodifiableList(Arrays.asList(COMMA.split(data, -1)));
		int x = (int) Utils.sToD(immutableList.get(indexof.get("X")));
		int y = (int) Utils.sToD(immutableList.get(indexof.get("Y")));

		Cell c = new Cell(x, y);

		if (c != null) {
			c.setOwner(AFTsLoader.getAftHash().get(immutableList.get(indexof.get("FR"))));

			CellsLoader.hashCell.put(x + "," + y, c);
			c.setID(immutableList.get(indexof.get("ID")));
		}
//		CellsLoader.getCapitalsList().forEach(capital_name -> {
//			double capital_value = Utils.sToD(immutableList.get(indexof.get(capital_name.toUpperCase())));
//			c.getCapitals().put(capital_name, capital_value);//
//		});
	}

	static void associateOutPutServicesToCells(Map<String, Integer> indexof, String data) {
		List<String> immutableList = Collections.unmodifiableList(Arrays.asList(COMMA.split(data, -1)));
		int x = (int) Utils.sToD(immutableList.get(indexof.get("X")));
		int y = (int) Utils.sToD(immutableList.get(indexof.get("Y")));
		String aft_name = immutableList.get(indexof.get("AGENT"));
		Cell c = CellsLoader.getCell(x, y);
		if (c != null) {
			c.setOwner(AFTsLoader.getAftHash().get(aft_name));
			for (int j = 0; j < ServiceSet.getServicesList().size(); j++) {
				double service_value = Utils
						.sToD(immutableList.get(indexof.get(ServiceSet.getServicesList().get(j).toUpperCase())));
				c.getCurrentProd()[j] = service_value;
			}
		}
	}

	public static HashMap<String, Double> readCsvToMatrixMap(Path csvFilePath) {
		// Read the CSV with headers enabled
		CsvReadOptions options = CsvReadOptions.builder(csvFilePath.toFile()).header(true).separator(',').build();
		Table table = null;
		try {
			table = Table.read().usingOptions(options);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		List<String> columnNames = table.columnNames();

		HashMap<String, Double> matrixMap = new HashMap<>();

		for (int rowIndex = 0; rowIndex < table.rowCount(); rowIndex++) {
			String rowLabel = table.getString(rowIndex, 0);

			for (int colIndex = 1; colIndex < table.columnCount(); colIndex++) {
				String colName = columnNames.get(colIndex);
				String cellValue = table.getString(rowIndex, colIndex);
				String key = rowLabel + "|" + colName;
				matrixMap.put(key, Utils.sToD(cellValue));
			}
		}

		return matrixMap;
	}

}
