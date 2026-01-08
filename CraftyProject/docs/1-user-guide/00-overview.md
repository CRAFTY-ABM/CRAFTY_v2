# Overview

This documentation explains how to run **CRAFTY** and how to organise the **data + configuration** needed for a simulation.

CRAFTY supports two main use cases:

- **Interactive exploration (GUI)**: inspect inputs, run the model, visualise outputs.
- **Batch / HPC runs (headless)**: run many scenarios/replicates efficiently and export outputs automatically.

---

## Where to start

1. **Choose how you want to run CRAFTY**
   - Windows `.exe` (GUI) – simplest for most users
   - `.jar` (GUI or headless) – portable, no IDE needed
   - IDE (headless) – development/debugging
   - IDE (GUI/JavaFX) – GUI development/debugging

2. Then go to:
   - **Quickstart:** [`01-quickstart.md`](01-quickstart.md)
   - **Running scenarios:** [`02-running-scenarios.md`](02-running-scenarios.md)
   - **Outputs:** [`04-outputs.md`](04-outputs.md)
   - **Troubleshooting:** [`05-troubleshooting.md`](05-troubleshooting.md)

If you are setting up a new dataset or a new scenario layout, also read:
- Default scenario layout: `docs/appendices/default-scenario-layout.md`
- File formats (CSV schemas): `docs/appendices/file-formats.md`

---

## What CRAFTY simulates (key concepts)

CRAFTY is a spatial land-use ABM where:

- **Cells** are the spatial units (grid locations) holding:
  - location (X/Y), region label, current owner (AFT)
  - capital values (e.g., productivity drivers)
  - computed production and utility values

- **AFTs (Agent Functional Types)** represent land managers (e.g., cropland types, forest types,...).
  - AFTs differ in production parameters, behavioural parameters, and policy terms (tax/subsidy).

- **Services** represent quantities produced on the landscape.
  - Each region has service demand trajectories; the model derives marginal utilities from demand–supply gaps.

- **Capitals** are spatial layers used in production functions.
  - Capitals can change over time (yearly files) and can be modified by shocks/degradation.

- **Regions** (optional) allow the model to run multiple sub-models, one per region, each with its own demands and outputs.

- **Updaters** are scheduled model components (modules) executed each simulation year:
  - load/apply year-specific data (capitals, AFT params, masks, shocks)
  - perform the regional decision cycle and land-use transitions
  - write outputs (tables and maps)

---

## The lifecycle of a run (high-level story)

A typical run follows this sequence:

1. **Read configuration**
   - Load a YAML config file
   - Apply optional CLI overrides (project directory, scenario name, output path)

2. **Resolve project + scenario**
   - Identify metadata tables (services, AFTs, capitals, scenarios)
   - Set the simulation time window (start year / end year)

3. **Load and initialise the model state**
   - Create all cells from the baseline land-use map
   - Build region subsets (if regionalisation is enabled and region inputs are available)
   - Initialise services and attach demand trajectories
   - Load AFT definitions (production, behaviour, land taxes/subsidies)
   - Pre-resolve time-indexed inputs (capitals, masks, degradation/shocks)

4. **Run yearly steps**
   Each year (tick), CRAFTY typically:
   - Applies year-specific inputs (capitals, shocks/degradation, masks/restrictions, AFT updates)
   - Executes the regional decision cycle (supply → marginal utility → utility → abandonment/competition → land use change)
   - Writes outputs (global + optionally per-region) and optional maps/plots
   - Advances the simulation year counter

5. **Finish**
   - Final outputs are available in the configured output folder.

> The exact schedule is controlled by the runner and which updaters are enabled in the config, but the logic above is the “mental model” that will help you understand what happens in each year.

---

## Inputs and configuration: how CRAFTY finds files

CRAFTY supports two ways to locate inputs:

### 1) Scenario-driven discovery (default)
If your project folder follows the expected scenario structure, CRAFTY can auto-discover many files
(e.g., capitals per year, masks, degradation files).

### 2) Explicit paths in YAML (override)
You can also point directly to:
- a baseline file
- a capitals directory
- service demand/weights/taxes files
- masks and restriction tables
- degradation / shock directories
- output folder

In practice, many users start with scenario defaults and override only what they need.

---

## Outputs (what you get)

CRAFTY can export:

- **Aggregated CSV time series** (global totals; optionally per-region)
- **Land-use change counters / diagnostics**
- **Cell-level map snapshots** as CSV and PNG (for selected years or at a frequency)
- Optional diagnostic trackers (useful for calibration/debugging)

See: [`04-outputs.md`](04-outputs.md)

---

## What each page covers

- **01 Quickstart**: minimal config, how to launch, where outputs go
- **02 Running scenarios**: scenario selection, overrides, regionalisation, performance tips
- **03 Common workflows**: typical recipes (swap input folders, run many scenarios, reproducibility)
- **04 Outputs**: what files are produced, naming, map export settings
- **05 Troubleshooting**: missing files, regionalisation fallback, common mistakes

---

## A note on coupling / synchronisation (optional)

CRAFTY can optionally pause at configured years and wait for external “flag” folders/files
to appear (useful for loose coupling with other models or pipelines).  
If you don’t need coupling, you can ignore these settings.

(Details will be in `03-common-workflows.md` and the config reference.)
