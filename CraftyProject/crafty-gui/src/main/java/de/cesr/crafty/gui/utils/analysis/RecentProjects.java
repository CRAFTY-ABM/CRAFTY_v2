package de.cesr.crafty.gui.utils.analysis;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class RecentProjects {
	private static final int MAX_ENTRIES = 10;
	private static final String KEY = "recentProjects";
	private static final String SEP = File.pathSeparator;
	private static final Preferences PREFS = Preferences.userNodeForPackage(RecentProjects.class);

	private RecentProjects() {
	}

	public static List<Path> load() {
		String joined = PREFS.get(KEY, "");
		if (joined.isEmpty())
			return new ArrayList<>();

		/* split on the platform-specific separator */
		return Arrays.stream(joined.split(Pattern.quote(SEP))).map(Path::of)
				.collect(Collectors.toCollection(LinkedList::new));
	}

	public static void add(Path p) {
		LinkedHashSet<Path> set = new LinkedHashSet<>(load());
		set.remove(p);
		set.add(p);
		while (set.size() > MAX_ENTRIES) {
			set.remove(set.iterator().next());
		}
		save(set);
	}

	public static void clear() {
		PREFS.remove(KEY);
	}

	private static void save(Collection<Path> paths) {
		String joined = paths.stream().map(Path::toString).collect(Collectors.joining(SEP));

		PREFS.put(KEY, joined);

		try {
			PREFS.flush(); // force the update to the registry/.plist
		} catch (BackingStoreException e) {
			e.printStackTrace(); // or log properly
		}
	}

	static public void writePathRecentProject(String path, String text) {
		String paths = "";
		try {
			paths = Files.readString(Paths.get(path));
		} catch (IOException e) {
		}
		if (!paths.contains(text)) {
			File file = new File(path);
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
				writer.write(text);
			} catch (IOException ex) {
				System.out.println("Error writing to file: " + ex.getMessage());
			}
		}
	}
}
