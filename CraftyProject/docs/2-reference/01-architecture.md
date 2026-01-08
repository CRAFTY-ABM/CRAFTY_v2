# Architecture

This page explains **how the CRAFTY codebase is organised** (modules + packages) and how the main parts interact at runtime.
It is meant for:
- developers extending CRAFTY (new modules, new updaters, new outputs)
- power users who want to understand what happens “under the hood”

If you only want to run the model, start in `docs/user-guide/`.

---

## 0) Repository structure (multi-module Maven)

CRAFTY is organised as a parent Maven project with separate modules:

- **`crafty-core/`**  
  Headless simulation engine (no JavaFX).  
  This is what you run on servers / HPC for batch experiments.

- **`crafty-gui/`**  
  JavaFX desktop interface.  
  Depends on `crafty-core` and provides interactive exploration and visualisation.

- **(Optional) other modules**  
  Some projects include additional modules (e.g., policy/institutional extensions, prototypes, tools).
  These should depend on `crafty-core` rather than duplicating core logic.

**Design goal:** keep the simulation engine independent of the GUI so the core can be used in headless environments.

---

## 1) Runtime modes (how CRAFTY is used)

CRAFTY supports two main runtime modes:

### 1.1 Headless mode (batch / HPC)
- Entry point: a headless main class in `crafty-core`
- Loads a YAML config (plus optional CLI overrides)
- Loads input data (baseline, AFTs, services, capitals, masks, …)
- Runs the year-by-year simulation loop
- Writes outputs (CSV, optional maps, logs)

### 1.2 GUI mode (interactive)
- Entry point: a JavaFX main class in `crafty-gui`
- Lets users browse inputs, edit parameters, run simulations, and visualise outputs
- Internally calls into `crafty-core` to execute the same simulation logic

**Important:** the GUI does not re-implement the model; it *orchestrates* and *visualises* the core.

---

## 2) Core architecture: “what talks to what”

At a high level, the core looks like this:

```text
CLI / Config
   ↓
Project + Scenario resolution
   ↓
Data loading (baseline, AFTs, services, capitals, masks, institutions...)
   ↓
Model state initialisation (cells, regions, service sets)
   ↓
ModelRunner (year loop)
   ↓
Updaters (scheduled modules)
   ↓
Outputs (listeners/writers + optional maps)
```

Key idea: **Updaters** are the main extension mechanism for “what happens each year”.

---

## 3) `crafty-core` package map (typical responsibilities)

The exact package names may vary slightly across branches, but the functional roles are stable.

### 3.1 `...core.main` (entry points)
- Headless launcher (reads config path, applies CLI overrides, starts runner)
- Sometimes includes small bootstrap utilities for tests or demo runs

### 3.2 `...core.cli` (configuration + logging)
- YAML config loader + validation defaults
- CLI argument parsing (config file path, scenario override, output path override, etc.)
- Central logging helpers / run banners / diagnostics

**Rule:** CLI should configure the run, not contain model logic.

### 3.3 `...core.dataLoader` (input discovery + parsing)
Responsible for:
- discovering scenario files and metadata tables
- reading CSV inputs into in-memory structures
- mapping input rows/columns to model entities

Typical sub-areas:
- AFT loaders (metadata, behaviour, productivity/sensitivity, taxes/subsidies)
- cell/baseline loader
- capital loaders (including time-indexed/yearly files)
- mask/restriction loaders
- service loaders (service lists, demand, weights, taxes/subsidies)
- project/scenario path resolution

**Rule:** loaders create/prepare data; they should not perform annual decision logic.

### 3.4 `...core.crafty` (domain model: what the simulation *is*)
Contains the core entities and model state:
- **Cell** (spatial unit)
- **AFT** (agent functional type)
- **Service** 
- model state containers (e.g., service sets, region registries, global holders)

This layer represents the “objects of the world”.

### 3.5 `...core.updaters` (scheduled model modules)
Updaters are executed by the runner each year and typically implement:
- coupling steps (e.g., waiting flags)
- applying time-varying inputs (capitals, degradation/shocks)
- computing productivity
- computing Marginal utility
- update taxes/subsidies if there avialable
- selecting participating cells (seeding)
- applying masks/restrictions
- abandonment and competition
- output triggers at specific years

**Rule:** if something happens “each year” (or at scheduled years), it belongs in an updater.

### 3.6 `...core.output` (writers, listeners, trackers)
Responsible for exporting:
- time series tables (global + optional per region)
- diagnostics (counts, composition breakdowns)
- map outputs (CSV/PNG when enabled)
- optional trackers for debugging/calibration

**Rule:** output code should read the model state; it should not modify model decisions.

### 3.7 `...core.utils` (shared helpers)
- CSV parsing/writing helpers
- file/path utilities
- performance helpers, selectors, math utilities
- formatting, validation

**Rule:** keep utilities stateless and side-effect-free where possible.

---

## 4) `crafty-gui` architecture (typical responsibilities)

The GUI module typically contains:

- **JavaFX entry point** (starts the app, loads scenes)
- **Controllers** (coordinate actions: open project, load config, start/stop run)
- **Views** (maps, charts, tables, dashboards)
- **GUI utilities** (colour maps, plotting, map rendering, export tools)
- **Run integration** (invokes `crafty-core` runner and subscribes to outputs / progress)

**Dependency rule:** `crafty-gui` depends on `crafty-core`, never the reverse.

---

## 5) Key design boundaries (important for maintainability)

### 5.1 Loading vs running boundary
- Loaders prepare state.
- Updaters modify state during the run.

### 5.3 “Global” vs “Regional” boundary
- Some runs operate as one “world” region.
- In regionalised runs, each region has its own demand and outputs, but uses the same core logic.
---

## 6) Extension points (how to add new features)

### 6.1 Add a new yearly mechanism (recommended: new updater)
Examples:
- new hazard/shock
- new competition rule
- new abandonment heuristic
- new policy mechanism applied annually

Pattern:
1) implement a new `Updater` class (following the existing interface/abstract base)
2) ensure it can be enabled/disabled via config
3) register it in the runner schedule (or config-driven schedule)

### 6.2 Add a new input type
Examples:
- new capital layer family
- new mask type
- new policy table

Pattern:
1) implement a loader to parse the new files
2) store results in the model state (or a dedicated registry)
3) apply it in an updater (if it changes annually)

### 6.3 Add a new output
Pattern:
1) implement a writer/listener for a new CSV table (or map export)
2) add config switches to control frequency/years

---

## 7) Performance & determinism notes (practical)

- Large runs can be dominated by:
  - reading huge CSVs (capitals, masks)
  - per-year utility calculations
  - map export volume

- Crafty use parallelism:
  - prefer thread-safe collections in shared registries
  - be careful with deterministic selection (ranking/ties) if you need reproducibility

- For HPC:
  - disable maps by default
  - write outputs to scratch during the run

(Practical recipes are in `docs/user-guide/03-common-workflows.md`.)

---

## 8) Related pages

- Glossary: `00-glossary.md`
- Running scenarios (resolution + overrides): `../user-guide/02-running-scenarios.md`
- Outputs: `../user-guide/04-outputs.md`
- Appendices (scenario layout + CSV schemas): `docs/appendices/`
