package de.cesr.crafty.core.utils.non_java_code_controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RScriptRunner {

    public static int runRScript(Path rScriptPath, List<String> args)
            throws IOException, InterruptedException {

        if (rScriptPath == null || !Files.isRegularFile(rScriptPath)) {
            throw new IOException("R script file does not exist: " + rScriptPath);
        }

        List<String> candidates = getRscriptCandidates();

        IOException lastStartException = null;

        for (String rscriptCommand : candidates) {
            try {
                System.out.println("[R] Trying Rscript command: " + rscriptCommand);
                return runRScriptWithCommand(rscriptCommand, rScriptPath, args);

            } catch (IOException e) {
                // This means Java could not start Rscript.
                // Try the next candidate.
                lastStartException = e;
                System.out.println("[R] Could not start Rscript using: " + rscriptCommand);
                System.out.println("[R] Reason: " + e.getMessage());
            }
        }

        throw new IOException(
                "Could not find/start Rscript. " +
                "On HPC, make sure your SLURM script contains something like: module load r/4.2.2-gcc-11.3.1",
                lastStartException
        );
    }

    private static int runRScriptWithCommand(String rscriptCommand, Path rScriptPath, List<String> args)
            throws IOException, InterruptedException {

        List<String> command = new ArrayList<>();

        command.add(rscriptCommand);
        command.add("--vanilla");
        command.add(rScriptPath.toAbsolutePath().toString());

        if (args != null) {
            command.addAll(args);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[R] " + line);
            }
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException(
                    "R script started but failed with exit code: " + exitCode +
                    ". This is probably an error inside the R script, not an Rscript path problem."
            );
        }

        return exitCode;
    }

    private static List<String> getRscriptCandidates() {
        List<String> candidates = new ArrayList<>();

        // Optional: allow user/HPC to override by environment variable
        String envRscript = System.getenv("CRAFTY_RSCRIPT");
        if (envRscript != null && !envRscript.isBlank()) {
            candidates.add(envRscript);
        }

        // Main automatic option.
        // On HPC, this works after: module load r/...
        candidates.add("Rscript");

        // Windows-specific fallback
        if (isWindows()) {
            candidates.add("C:\\Program Files\\R\\R-4.6.0\\bin\\Rscript.exe");
        }

        return candidates;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}