package de.cesr.crafty.core.updaters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import de.cesr.crafty.core.ToyData;
import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.utils.file.DirectoryWatcher;
import de.cesr.crafty.core.utils.file.PathTools;

class FlagUpdaterTest {

	@TempDir
	private Path tempDir;
	
	@BeforeEach
	void resetStaticState() {
		new ToyData().resetStaticState(tempDir);
		Path flag = tempDir.resolve("flag.csv");
		ConfigLoader.config.waitingFlag_directories_path = flag.toString();
	}
    @AfterEach
    void tearDown() {
    	CustomLogger.shutdownRunFileLoggers();
    }

	@Test
	void constructor_doesNotThrow_whenWaitingFlagsCsvMissing_orEmptyList() {
		Path configDir = Paths.get("config");
		try (MockedStatic<PathTools> pt = Mockito.mockStatic(PathTools.class)) {
			pt.when(() -> PathTools.asFolder("config")).thenReturn(configDir.toString());

			// Case 1: null list
			pt.when(() -> PathTools.fileFilter(configDir.toString(), "waitingFlags.csv")).thenReturn(null);
			assertDoesNotThrow(FlagUpdater::new);

			// Case 2: empty list (THIS used to crash with iterator().next())
			pt.when(() -> PathTools.fileFilter(configDir.toString(), "waitingFlags.csv")).thenReturn(new ArrayList<>());
			FlagUpdater u = assertDoesNotThrow(FlagUpdater::new);
			assertNotNull(u.flags);
			assertTrue(u.flags.isEmpty());
		}
	}

	@Test
	void constructor_loadsFlagsFromCsv() {
		Path configDir = Paths.get("config");
		Path csvPath = Paths.get("config/waitingFlags.csv");

		Map<String, List<String>> csv = new HashMap<>();
		csv.put("Year", List.of("2020", "2021"));
		csv.put("Waiting_Flag", List.of("/tmp/flag2020", "/tmp/flag2021"));

		try (MockedStatic<PathTools> pt = Mockito.mockStatic(PathTools.class);
				MockedStatic<CsvProcessors> cp = Mockito.mockStatic(CsvProcessors.class)) {

			pt.when(() -> PathTools.asFolder("config")).thenReturn(configDir.toString());
			pt.when(() -> PathTools.fileFilter(configDir.toString(), "waitingFlags.csv"))
					.thenReturn(new ArrayList<>(List.of(csvPath)));

			cp.when(() -> CsvProcessors.ReadAsaHash(csvPath)).thenReturn(csv);

			FlagUpdater u = new FlagUpdater();

			assertEquals(2, u.flags.size());
			assertEquals(Paths.get("/tmp/flag2020"), u.flags.get(2020));
			assertEquals(Paths.get("/tmp/flag2021"), u.flags.get(2021));
		}
	}

	@Test
	void step_doesNothing_whenNoFlagForCurrentYear() {
		FlagUpdater u = new FlagUpdater();
		u.flags.clear();
		u.flags.put(2020, Paths.get("/tmp/flag2020"));

		try (MockedStatic<Timestep> ts = Mockito.mockStatic(Timestep.class);
				MockedStatic<DirectoryWatcher> dw = Mockito.mockStatic(DirectoryWatcher.class)) {

			ts.when(Timestep::getCurrentYear).thenReturn(2021);

			u.step();

			dw.verifyNoInteractions();
		}
	}

	@Test
	void step_callsDirectoryWatcher_whenFlagExistsForCurrentYear() {
		FlagUpdater u = new FlagUpdater();
		u.flags.clear();
		Path p = Paths.get("/tmp/flag2021");
		u.flags.put(2021, p);

		try (MockedStatic<Timestep> ts = Mockito.mockStatic(Timestep.class);
				MockedStatic<DirectoryWatcher> dw = Mockito.mockStatic(DirectoryWatcher.class)) {

			ts.when(Timestep::getCurrentYear).thenReturn(2021);

			u.step();

			dw.verify(() -> DirectoryWatcher.waitForYearFolder(p), times(1));
		}
	}
}
