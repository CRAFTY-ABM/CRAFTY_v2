package de.cesr.crafty.core.utils.non_java_code_controller;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RScriptRunner {

    public static int runRScript(Path rscriptExe, Path rScriptPath, List<String> args)
            throws IOException, InterruptedException {

        List<String> command = new ArrayList<>();

        command.add(rscriptExe.toAbsolutePath().toString());
        command.add("--vanilla");
        command.add(rScriptPath.toAbsolutePath().toString());

        if (args != null) {
            command.addAll(args);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[R] " + line);
            }
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("R script failed with exit code: " + exitCode);
        }

        return exitCode;
    }
}