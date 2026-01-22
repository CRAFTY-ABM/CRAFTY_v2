package de.cesr.crafty.core.utils.file;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PathToolsTest {

	@TempDir
	Path tempDir;

	// ---------- listSubdirectories ----------

	@Test
	void listSubdirectoriesShouldReturnOnlyImmediateDirectories() throws IOException {
		Path dirA = Files.createDirectory(tempDir.resolve("A"));
		Path dirB = Files.createDirectory(tempDir.resolve("B"));
		Files.createFile(tempDir.resolve("file.txt"));

		Set<Path> subs = PathTools.listSubdirectories(tempDir);

		assertNotNull(subs);
		assertEquals(2, subs.size());
		assertTrue(subs.contains(dirA));
		assertTrue(subs.contains(dirB));
		// should not contain the file
		assertFalse(subs.contains(tempDir.resolve("file.txt")));
	}

	// ---------- asFolder ----------

	@Test
	void asFolderShouldWrapInputWithFileSeparator() {
		String s = PathTools.asFolder("data");
		assertTrue(s.startsWith(File.separator));
		assertTrue(s.endsWith(File.separator));
		assertTrue(s.contains("data"));
	}

	// ---------- fileFilter (list-based overloads) ----------

	@Test
	void fileFilterWithConditionsShouldReturnOnlyMatchingPaths() {
		ArrayList<Path> all = new ArrayList<>();
		all.add(Paths.get("worlds/capitals.csv"));
		all.add(Paths.get("worlds/restrictions.csv"));
		all.add(Paths.get("GIS/capitals.csv"));

		// Must contain both "worlds" AND "capitals"
		ArrayList<Path> filtered = PathTools.fileFilter(all, "worlds", "capitals");

		assertNotNull(filtered);
		assertEquals(1, filtered.size());
		assertEquals(Paths.get("worlds/capitals.csv"), filtered.get(0));
	}

	@Test
	void fileFilterShouldReturnNullWhenNoMatchesAndIgnoreFalse() {
		ArrayList<Path> all = new ArrayList<>();
		all.add(Paths.get("worlds/capitals.csv"));

		ArrayList<Path> filtered = PathTools.fileFilter(all, false, "GIS"); // no GIS in any path

		assertNull(filtered, "When no match and ignoreIfFileNotExists=false, should return null");
	}

	// ---------- findAllFilePaths / creatListPaths ----------

	@Test
	void findAllFilePathsShouldFindAllFilesRecursively() throws IOException {
		Path sub1 = Files.createDirectory(tempDir.resolve("sub1"));
		Path sub2 = Files.createDirectory(tempDir.resolve("sub2"));
		Path f1 = Files.createFile(tempDir.resolve("rootFile.txt"));
		Path f2 = Files.createFile(sub1.resolve("sub1File.txt"));
		Path f3 = Files.createFile(sub2.resolve("sub2File.txt"));

		ArrayList<Path> paths = PathTools.findAllFilePaths(tempDir);

		assertEquals(3, paths.size());
		assertTrue(paths.contains(f1));
		assertTrue(paths.contains(f2));
		assertTrue(paths.contains(f3));
	}

	// ---------- writeFile ----------

	@Test
	void writeFileShouldOverwriteOrAppendDependingOnFlag() throws IOException {
		Path file = tempDir.resolve("out.txt");

		// First write (overwrite mode has no effect on non-existing file)
		PathTools.writeFile(file.toString(), "hello", false);
		String content1 = Files.readString(file);
		assertEquals("hello", content1);

		// Append
		PathTools.writeFile(file.toString(), " world", true);
		String content2 = Files.readString(file);
		assertEquals("hello world", content2);

		// Overwrite again
		PathTools.writeFile(file.toString(), "new", false);
		String content3 = Files.readString(file);
		assertEquals("new", content3);
	}

	// ---------- detectFolders ----------

	@Test
	void detectFoldersShouldReturnOnlySubdirectories() throws IOException {
		Files.createDirectory(tempDir.resolve("A"));
		Files.createDirectory(tempDir.resolve("B"));
		Files.createFile(tempDir.resolve("file.txt"));

		List<File> dirs = PathTools.detectFolders(tempDir.toString());

		List<String> names = new ArrayList<>();
		dirs.forEach(d -> names.add(d.getName()));

		assertEquals(2, dirs.size());
		assertTrue(names.contains("A"));
		assertTrue(names.contains("B"));
		assertFalse(names.contains("file.txt"));
	}

	// ---------- getAllFolders ----------

	@Test
	void getAllFoldersShouldReturnAllSubdirectoriesRecursively() throws IOException {
		Path sub1 = Files.createDirectory(tempDir.resolve("sub1"));
		Path sub2 = Files.createDirectory(sub1.resolve("sub2"));
		Files.createFile(tempDir.resolve("file.txt"));

		List<Path> folders = PathTools.getAllFolders(tempDir.toString());

		assertNotNull(folders);
		assertTrue(folders.contains(sub1));
		assertTrue(folders.contains(sub2));
		assertFalse(folders.contains(tempDir)); // root excluded by design
	}

	@Test
	void getAllFoldersShouldThrowOnNonDirectory() throws IOException {
		Path file = Files.createFile(tempDir.resolve("notDir.txt"));
		assertThrows(IllegalArgumentException.class, () -> PathTools.getAllFolders(file.toString()));
	}

	// ---------- makeDirectory ----------

	@Test
	void makeDirectoryShouldCreateDirectoryAndReturnPathString() {
		String dirPath = tempDir.resolve("newFolder").toString();

		String result = PathTools.makeDirectory(dirPath);

		assertNotNull(result);
		assertEquals(dirPath, result);
		assertTrue(Files.isDirectory(Paths.get(result)));
	}

	@Test
	void makeDirectoryShouldReturnNullForNullInput() {
		assertNull(PathTools.makeDirectory(null));
	}
}
