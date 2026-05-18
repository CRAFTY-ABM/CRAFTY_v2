package de.cesr.crafty.core.utils.non_java_code_controller;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RScriptRunnerConfig {

    public String config_path;

    public boolean enabled = false;

    public boolean stop_on_error = true;

    public List<RScriptTaskConfig> scripts = new ArrayList<>();

    public boolean isEnabled() {
        return enabled && scripts != null && !scripts.isEmpty();
    }

    public List<RScriptTaskConfig> scriptsForYear(int year) {
        if (!isEnabled()) {
            return List.of();
        }

        List<RScriptTaskConfig> selected = new ArrayList<>();

        for (RScriptTaskConfig script : scripts) {
            if (script != null && script.shouldRunInYear(year)) {
                selected.add(script);
            }
        }

        return selected;
    }

    public static class RScriptTaskConfig {

        public String name;

        public String path;

        public List<Object> years = new ArrayList<>();

        public Map<String, String> inputs = new LinkedHashMap<>();

        public Map<String, String> outputs = new LinkedHashMap<>();

        public Map<String, String> args = new LinkedHashMap<>();

        public boolean shouldRunInYear(int year) {
            if (years == null || years.isEmpty()) {
                return false;
            }

            for (Object value : years) {
                if (value == null) {
                    continue;
                }

                String text = String.valueOf(value).trim();

                if (text.equalsIgnoreCase("ALL")) {
                    return true;
                }

                try {
                    if (Integer.parseInt(text) == year) {
                        return true;
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Invalid year value in r_script_runner for script '" + displayName() + "': " + text
                    );
                }
            }

            return false;
        }

        public String displayName() {
            if (name != null && !name.isBlank()) {
                return name;
            }
            return path;
        }
    }
}