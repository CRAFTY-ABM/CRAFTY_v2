# CRAFTY – ABM (Competition for Resources between Agent Functional Types)

**CRAFTY** is an open-source, agent-based modelling framework for simulating land-use change ([project site](https://landchange.imk-ifu.kit.edu/CRAFTY)).  

This repository follows a **multi-module Maven layout**, separating the headless simulation engine (**`crafty-core`**) from the JavaFX desktop interface (**`crafty-gui`**). This supports both large batch/HPC experiments and interactive exploration via the GUI.

---

## Choose how to run CRAFTY (4 options)

### Option A — Install the GUI as a Windows `.exe` (recommended for most users)
- Best if you just want to **use the interface** without touching Java/Maven.
- Windows-only (for now).
- Download: [Crafty-gui.exe](https://nextcloud.imk-ifu.kit.edu/s/Exy7Q58gd6g5icZ).

### Option B — Run the packaged `.jar` (with a YAML config, no IDE)
- Best if you want a **portable run** (Windows/Linux/macOS) and you have a JDK installed.
- You can run:
  - **Headless jar** (batch / server / HPC)
- Download: https: [crafty-core-v2...jar]( //nextcloud.imk-ifu.kit.edu/s/Exy7Q58gd6g5icZ).

### Option C — Run headless from an IDE (developers / debugging)
- Best for **core development**, debugging, and unit tests.
- Run the headless entry point from `crafty-core` with a YAML config.

### Option D — Run the GUI from an IDE (developers / GUI work)
- Best for **GUI development** and debugging.
- Requires JavaFX libraries/module path (depending on your setup).

> If you’re new: start with **Option A** (Windows `.exe`) or **Option B** (GUI `.jar`).

---

## Documentation (start here)
- **User Guide (task-oriented):** `docs/user-guide/`
- **Reference Manual (inputs, config, components):** `docs/reference/`
- **Examples:** `examples/` (example configs, minimal scenarios, etc.)

> Suggested starting point: `docs/user-guide/01-quickstart.md`.

---

## Table of Contents
1. [Project Structure](#project-structure)
2. [Prerequisites](#prerequisites)
3. [Build](#build)
4. [Run without an IDE](#run-without-an-ide)
5. [Run from an IDE](#run-from-an-ide)
6. [Configuration Basics (YAML)](#configuration-basics-yaml)
7. [Data / Scenario Structure](#data--scenario-structure)
8. [Outputs](#outputs)
9. [Contributing](#contributing--license)

---

## Project Structure
```text
CraftyProject/                 (parent Maven project)
├── crafty-core/               (headless simulation engine)
│   └── target/
│       └── crafty-core-*-headless-*.jar     (fat JAR, CLI entry-point)
├── crafty-gui/                (JavaFX desktop interface)
│   └── target/
│       └── crafty-gui-*.jar
├── docs/                      (User Guide + Reference Manual)
└── pom.xml                    (parent POM)
```

---

## Prerequisites
| Tool | Version | When you need it |
|------|---------|------------------|
| **Windows installer (.exe)** | — | No extra tools needed (Option A) |
| **JDK** | ≥ 17 | Required for running `.jar` (Option B) and for development (Options C/D) |
| **Apache Maven** | ≥ 3.9 | Required to build from source (Options C/D, or if you build your own jars) |
| **(Optional) Eclipse / IntelliJ** | — | For running from an IDE (Options C/D) |

---

## Build
If you are building from source:
```bash
git clone <REPO_URL>
cd CraftyProject
mvn clean install
```

Artifacts (typical):
- `crafty-core/target/…headless….jar`
- `crafty-gui/target/…gui….jar`

---

## Run without an IDE

### A) Windows `.exe` installer (GUI)
1. Download the installer: https://nextcloud.imk-ifu.kit.edu/s/Exy7Q58gd6g5icZ
2. Install and launch CRAFTY from the Start Menu.
3. In the GUI, select your data using Open Projects From File Systeme. > see (#data--scenario-structure)

### B) Run a `.jar`
> You need **JDK 17+** installed.

#### Run the GUI jar
```bash
java -jar crafty-gui-<version>.jar
```

#### Run the headless jar
```bash
java -jar "$CRAFTY_JAR.jar" -c "$CONFIG.yaml" -o "$OUT_DIR"
```
---

## Run from an IDE

### C) Headless (`crafty-core`) from IDE
1. Import the parent Maven project.
2. Create a **Run Configuration** in the `crafty-core` module:
   - Main class: `de.cesr.crafty.core.main.MainHeadless`
   - Program args:
     ```
     --config-file "/path/to/config.yaml"
     ```
3. Run / debug.

### D) GUI (`crafty-gui`) from IDE (JavaFX)
1. Import the parent Maven project.
2. Create a **Run Configuration** in the `crafty-gui` module:
   - Main class: main.FxMain
3. If your IDE setup requires it, add JavaFX VM args, e.g.:
   ```
   --module-path "<path-to-javafx-sdk>/lib" --add-modules javafx.controls,javafx.fxml
   ```
4. Run / debug.

---

## Configuration Basics (YAML)
At minimum, a config needs a **project directory** and a **scenario**:

```yaml
project_path: "/path/to/CRAFTY_DATA/CRAFTY-EU-1km_upscaled"
scenario: "ssp126"
```

Most runs then extend this with:
- **Mechanisms / switches** (regionalization, neighbour effects, mutation, seeding, etc.)
- **Competitiveness / abandonment settings**
- **Output controls** (CSV/maps/plots + frequency)

See:
- `docs/user-guide/02-running-scenarios.md`
- `docs/reference/03-config-reference.md`

---

## Data / Scenario Structure
CRAFTY expects a project data directory containing:
- **Metadata tables** (services, AFTs, capitals, scenarios)
- **World / scenario inputs** (baseline map, capitals time series, demands, optional masks, optional shocks, …)

By default, many inputs are discovered from the scenario folder structure.  
You can override most of them explicitly in YAML (e.g., baseline path, capitals directory, service demand paths, weight/tax files, mask folders, degradation directory, …).

See:
- `docs/reference/02-data-model.md`
- `docs/appendices/default-scenario-layout.md`
- `docs/appendices/file-formats.md`

---

## Outputs
When enabled, CRAFTY writes:
- Global (world/total) CSV time series (AFT composition, service demand/supply, equilibrium summaries, …)
- Optional **region-level outputs** (one subfolder per region)
- Optional **cell-level map snapshots** (CSV + PNG) controlled by map output settings
- Optional diagnostics (e.g., supply composition tracking)

See:
- `docs/user-guide/04-outputs.md`
- `docs/reference/04-components/outputs.md`

---

## Contributing | License
- Contributions are welcome via PRs (tests + docs updates encouraged).
- License: see `LICENSE`.
