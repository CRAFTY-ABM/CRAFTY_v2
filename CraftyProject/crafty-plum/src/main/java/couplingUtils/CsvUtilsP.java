package couplingUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Stream;

import de.cesr.crafty.core.utils.file.PathTools;

public class CsvUtilsP {

	private static final String SPLIT_REGEX = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"; // “split on commas not inside
																					// quotes”

	/**
	 * Reads a CSV file (UTF-8) into a {@code List<Map<String,String>>}. The first
	 * row is treated as the header.
	 *
	 * @param filePath path to the CSV file
	 * @return list of records, each record is a header-to-value map
	 * @throws IOException if the file cannot be read
	 */
	public static List<Map<String, String>> readCsvIntoList(Path filePath) {
		List<Map<String, String>> records = new ArrayList<>();

		try (BufferedReader br = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
			// ---- 1) read header line ----
			String headerLine = br.readLine();
			if (headerLine == null) {
				return records; // empty file
			}
			String[] headers = splitCsvLine(headerLine);

			// ---- 2) read data lines ----
			String line;
			while ((line = br.readLine()) != null) {
				if (line.isEmpty())
					continue; // skip blank lines
				String[] fields = splitCsvLine(line);
				Map<String, String> row = new HashMap<>();

				for (int i = 0; i < headers.length; i++) {
					String header = headers[i];
					String value = (i < fields.length) ? fields[i] : "";
					row.put(header, value);
				}
				records.add(row);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return records;
	}

	// ---------- helpers ----------

	/** Splits a CSV line using the regex and unescapes quotes. */
	private static String[] splitCsvLine(String line) {
		String[] raw = line.split(SPLIT_REGEX, -1); // keep trailing empty fields
		for (int i = 0; i < raw.length; i++) {
			raw[i] = unquote(raw[i].trim());
		}
		return raw;
	}

	/** Removes surrounding quotes, replaces doubled quotes with single quotes. */
	private static String unquote(String s) {
		if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
			s = s.substring(1, s.length() - 1); // strip outer quotes
		}
		return s;
	}

	public static void cleanDirectory(Path dir) {
		// walk the tree, deepest path first, so files are removed before the dir that
		// contains them
		try (Stream<Path> walk = Files.walk(dir)) {
			walk.sorted(Comparator.reverseOrder()) // children before parent
					.forEach(path -> {
						try {
							Files.delete(path);
						} catch (IOException ex) {
							// re-throw or log; you might want to collect failures
							throw new RuntimeException("Failed to delete " + path, ex);
						}
					});
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		PathTools.makeDirectory(dir.toString());

	}

	public static void deletePath(String pathStr) {
        Path path = Paths.get(pathStr);

        if (Files.notExists(path)) {
            return;                     // nothing to do
        }

        // Walk the tree depth-first and delete as we go.
        try {
			Files.walkFileTree(path, new SimpleFileVisitor<>() {

			    // delete files as they’re encountered
			    @Override
			    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
			            throws IOException {
			        Files.delete(file);
			        return FileVisitResult.CONTINUE;
			    }

			    // after visiting a directory’s children, delete the dir itself
			    @Override
			    public FileVisitResult postVisitDirectory(Path dir, IOException exc)
			            throws IOException {
			        if (exc != null) throw exc;
			        Files.delete(dir);
			        return FileVisitResult.CONTINUE;
			    }
			});
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
	
	
	public static void  copyFile(Path sourcePath,Path destinationPath) {
		 try {
	            // Copy file from source to destination
	            Files.copy(sourcePath, destinationPath);
	            System.out.println("File copied successfully.");
	        } catch (IOException e) {
	            System.out.println("Error occurred while copying file: " + e.getMessage());
	        }
	}
	public static String readAsString(String filePath) {
		Scanner scanner;
		String line = "";
		try {
			scanner = new Scanner(new File(filePath));
			while (scanner.hasNextLine()) {
				line = line + "\n" + scanner.nextLine();
			}
		} catch (FileNotFoundException e) {
		}
		return line;
	}

}
