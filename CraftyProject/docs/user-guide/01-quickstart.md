# Quickstart

This page gets you from **zero → first successful run** as fast as possible.

You will:
1) choose how to run CRAFTY  
2) download/install it  
3) point CRAFTY to a dataset + scenario  
4) run  
5) find outputs

---

## 0) Choose how you want to run CRAFTY

**Option A — Windows `.exe` (GUI)**  
Best for most users on Windows. No IDE, no Maven.

**Option B — `.jar` (GUI or headless)**  
Best if you want portability (Windows/Linux/macOS) and/or want to run on servers/HPC.

**Option C — IDE (headless)**  
Best for developers/debugging.

**Option D — IDE (GUI/JavaFX)**  
Best for GUI development/debugging.

> If you’re new: start with **Option A** or **Option B**.

---

## 1) Get CRAFTY

### Option A: Install the Windows GUI (`.exe`)
1. Download the installer: **<ADD_WINDOWS_INSTALLER_DOWNLOAD_PAGE>** (e.g., GitHub Releases)
2. Install and launch **CRAFTY** from the Start Menu.

### Option B: Download the `.jar`
1. Download:
   - **GUI jar** (interactive), and/or
   - **Headless jar** (batch/server/HPC)  
   from: **<ADD_JAR_DOWNLOAD_PAGE>** (e.g., GitHub Releases)
2. Ensure you have **JDK 17+** installed:
   ```bash
   java -version
   ```

---

## 2) Get example data (or use your own)

If you don’t have a dataset yet, download the example dataset:
- Example data (OSF): https://osf.io/v67jy/files/osfstorage

Unzip it somewhere on your disk, for example:
- Windows: `C:\CRAFTY_DATA\CRAFTY-EU-1km_upscaled\`
- Linux: `/data/CRAFTY/CRAFTY-EU-1km_upscaled/`

You will reference this folder in the config as `project_path`.

> If you already have a project dataset, just use that path.

---

## 3) Create a minimal config (`config.yaml`)

Create a file called `config.yaml` anywhere you like.

### Minimal config (recommended starting point)
```yaml
# Required
project_path: "C:/CRAFTY_DATA/CRAFTY-EU-1km_upscaled"
scenario: "ssp126"

# Recommended (outputs)
generate_output_files: true
output_folder_name: "quickstart-run"   # empty = timestamped folder
```

Notes:
- Use quotes around paths.
- Forward slashes work well on Windows too (`C:/...`).
- The scenario must exist in your dataset (see the scenario layout appendix).

---

## 4) Run CRAFTY

### Option A: Run via the Windows `.exe` (GUI)
1. Start the CRAFTY application.
2. Open/select your `config.yaml` (depending on your GUI workflow).
3. Run the model.
4. Check the output folder (next section).

### Option B1: Run the GUI `.jar`
```bash
java -jar crafty-gui-<version>.jar
```

### Option B2: Run headless `.jar`
```bash
java -Djava.awt.headless=true -jar crafty-core-headless-<version>.jar \
  --config-file path/to/config.yaml
```

Optional CLI overrides (useful in scripts/HPC):
```bash
java -jar crafty-core-headless-<version>.jar \
  --config-file path/to/config.yaml \
  --project-dir /path/to/project_data \
  --scenario-name ssp126 \
  --output-path /path/to/output
```

---

## 5) Check outputs (how to know it worked)

Typical output location is inside the project folder, e.g.:

```text
<project_path>/
  output/
    <scenario>/
      <output_folder_name or timestamp>/
        ... CSV outputs, logs, optional maps/plots ...
```

If you don’t see outputs:
- Ensure `generate_output_files: true`
- Ensure your `project_path` and `scenario` are valid
- Look at the console/log output for “file not found” messages

---

## 6) Next steps

- Run other scenarios + overrides: [`02-running-scenarios.md`](02-running-scenarios.md)
- Understand outputs and map export: [`04-outputs.md`](04-outputs.md)
- Common recipes (batch runs, reproducibility, coupling flags): [`03-common-workflows.md`](03-common-workflows.md)
- Data layout and CSV schemas:
  - `docs/appendices/default-scenario-layout.md`
  - `docs/appendices/file-formats.md`
