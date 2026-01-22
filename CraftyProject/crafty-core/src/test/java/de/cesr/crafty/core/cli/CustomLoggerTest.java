package de.cesr.crafty.core.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CustomLoggerTest {

    @TempDir
    Path tempDir;

    @Test
    void loggerMethodsShouldNotThrowWhenConfigIsNull() {
        // Simulate early startup / unit-test environment
        ConfigLoader.config = null;

        CustomLogger logger = new CustomLogger(CustomLoggerTest.class);

        assertDoesNotThrow(() -> {
            logger.info("info message");
            logger.warn("warn message");
            logger.error("error message");
            logger.debug("debug message");
            logger.trace("trace message");
            logger.fatal("fatal message (should be ignored because config is null)");
        });
    }

    @Test
    void ensureDirectoryExistsShouldCreateParentDirectory()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        Path logFile = tempDir.resolve("logs/subdir/crafty.log");
        Path parentDir = logFile.getParent();

        assertFalse(Files.exists(parentDir), "Parent directory should not exist before call");

        // Call private static method ensureDirectoryExists(Path) via reflection
        Method ensureDir = CustomLogger.class.getDeclaredMethod("ensureDirectoryExists", Path.class);
        ensureDir.setAccessible(true);
        ensureDir.invoke(null, logFile);

        assertTrue(Files.exists(parentDir),
                "ensureDirectoryExists should create the parent directory if missing");
    }
}
