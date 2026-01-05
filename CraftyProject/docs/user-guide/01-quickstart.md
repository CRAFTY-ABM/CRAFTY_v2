````md
# Quickstart (01)

This page gets you from **“I have CRAFTY + data” → “I can run a scenario and find outputs”** with the minimum number of steps.

---

## 1) What you need

Pick one of these run modes:

### A) Windows `.exe` (GUI)
- You only need: the installer + a dataset + a config YAML (or GUI project/scenario picker, depending on your build).
- Best for most users on Windows.

### B) `.jar` (GUI or headless)
- You need: **JDK 17+** installed.
- Best for Linux/macOS/Windows, and for HPC/headless runs.

### C/D) From source (IDE or Maven)
- You need: **JDK 17+** + **Maven 3.9+**.
- Best for developers and debugging.

---

## 2) Get the data (project directory)

Download/unzip a CRAFTY dataset to a folder, e.g.:

- Windows: `C:\CRAFTY_DATA\CRAFTY-EU-1km_upscaled\`
- Linux: `/data/crafty/CRAFTY-EU-1km_upscaled/`

This folder is your **`project_path`**.

> If your dataset is organised by scenarios, you’ll select one scenario name such as `ssp126`.

---

## 3) Create a minimal config YAML

Create a file like `config.yaml`:

```yaml
# Minimal CRAFTY config
project_path: "/path/to/CRAFTY_DATA/CRAFTY-EU-1km_upscaled"
scenario: "ssp126"

# Optional but recommended (keeps outputs away from input folders)
output_path: "/path/to/output/my_run"
````

Windows example:

```yaml
project_path: "C:\\CRAFTY_DATA\\CRAFTY-EU-1km_upscaled"
scenario: "ssp126"
output_path: "C:\\CRAFTY_OUTPUT\\my_run"
```

---

## 4) Run it

### Option A — Windows `.exe` (GUI)

1. Install CRAFTY using the Windows installer.
2. Launch CRAFTY.
3. Open/select your `config.yaml` (or set `project_path` + `scenario` in the GUI, depending on the build).
4. Run and monitor results in the interface.

### Option B1 — Run the GUI `.jar`

```bash
java -jar crafty-gui-<version>.jar
```

Then open/select your `config.yaml` inside the GUI.

### Option B2 — Run headless `.jar` (recommended for batch/HPC)

```bash
java -Djava.awt.headless=true -jar crafty-core-headless-<version>.jar \
  --config-file path/to/config.yaml
```

Common overrides (handy for scripts):

```bash
java -jar crafty-core-headless-<version>.jar \
  --config-file path/to/config.yaml \
  --project-dir /path/to/project_data \
  --scenario-name ssp126 \
  --output-path /path/to/output
```

### Option C — Run headless from an IDE (dev/debug)

* Import the Maven project.
* Create a Run Configuration pointing to the headless main class (in `crafty-core`).
* Program args:

  ```
  --config-file "/path/to/config.yaml"
  ```

### Option D — Run the GUI from an IDE (dev/debug)

* Import the Maven project.
* Create a Run Configuration for the GUI main class (in `crafty-gui`).
* If required by your IDE setup, provide JavaFX VM args, e.g.:

  ```
  --module-path "<path-to-javafx-sdk>/lib" --add-modules javafx.controls,javafx.fxml
  ```

---

## 5) Where outputs go

If `output_path` is set, outputs should appear there.
If not set, CRAFTY may write into a default output folder under the project/scenario (depends on your configuration defaults).

Typical things you should see:

* aggregated CSV time series (global totals)
* optional region-level outputs (if regionalisation is enabled)
* optional maps (CSV + PNG) for configured years/frequency

Next: see **`04-outputs.md`** for the output structure and how to enable/limit map export.

---

## 6) Quick troubleshooting

### “Scenario not found”

* Check `scenario: "..."` matches a scenario folder/name that exists in the dataset.

### “File not found / metadata missing”

* Confirm `project_path` points to the dataset root, not one level above/below.
* Check the dataset is fully unzipped and not missing metadata tables.

### “No regional outputs”

* Regionalisation may be disabled or missing required region inputs.
* Start with a non-regional run first, then enable regionalisation once the base run works.

---

## Next steps

* Run scenarios and overrides: **`02-running-scenarios.md`**
* Outputs & maps: **`04-outputs.md`**
* File formats and scenario layout: `docs/appendices/file-formats.md`, `docs/appendices/default-scenario-layout.md`

```

If you want, the next good page to write is **`02-running-scenarios.md`** (scenario discovery vs YAML path overrides, regionalisation on/off, and “batch/HPC patterns”).
```
