package de.cesr.crafty.core.utils.file;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class DirectoryWatcherTest {

	@TempDir
	Path tempDir;

	/**
	 * If the target directory already exists, waitForYearFolder should return
	 * almost immediately (no long wait or timeout).
	 */
	@Test
	@Timeout(value = 5, unit = TimeUnit.SECONDS)
	void waitForYearFolderReturnsImmediatelyWhenDirectoryAlreadyExists() throws IOException {
		// Arrange: create the directory that we will wait for
		Path targetDir = tempDir.resolve("existingDir");
		Files.createDirectory(targetDir);

		Instant start = Instant.now();

		// Act
		DirectoryWatcher.waitForYearFolder(targetDir);

		Instant end = Instant.now();
		Duration elapsed = Duration.between(start, end);

		// Assert: this should be quick (certainly far less than the 30-minute internal
		// timeout)
		assertTrue(elapsed.getSeconds() < 2,
				"Method should return quickly when directory already exists, but took " + elapsed.toMillis() + " ms");
	}

	/**
	 * If the target directory does not exist yet, waitForYearFolder should block
	 * until it is created, then return.
	 */
	@Test
	@Timeout(value = 10, unit = TimeUnit.SECONDS)
	void waitForYearFolderReturnsAfterDirectoryIsCreated() throws Exception {
		// Arrange: directory that does NOT exist yet
		Path targetDir = tempDir.resolve("newDir");

		// Start waiting in a separate thread
		Thread watcherThread = new Thread(() -> DirectoryWatcher.waitForYearFolder(targetDir));
		watcherThread.start();

		// Give the watcher a moment to start up and register the WatchService
		Thread.sleep(5000);

		// Act: now create the directory that the watcher is waiting for
		Files.createDirectory(targetDir);

		// Wait for the watcher thread to finish
		watcherThread.join(5000);

		// Assert: the thread should have completed
		assertFalse(watcherThread.isAlive(), "waitForYearFolder should have returned after the directory was created");
	}
}
