# Glossary

This glossary defines the core terms used across CRAFTY documentation and configuration.
It is written to match how CRAFTY is implemented (core entities, loaders, updaters, and outputs).

---

## AFT (Agent Functional Type)
A behavioural and productive “agent class” representing a land manager type (e.g., a specific farming system, forestry type,...).
An AFT defines:
- production behaviour (how services are produced from capitals)
- behavioural rules (how it competes, abandons land, reacts to utility/marginal utility)
- optional policy terms (taxes/subsidies, restrictions)
---

## Baseline (baseline map)
The starting land-use/ownership state of the landscape at the first simulation year.
Usually provided as a cell table (X/Y → initial owner/AFT) plus optional metadata.

---

## Capital
A spatial layer (cell attribute) used in production functions.
Capitals can be:
- static (constant over time)
- time-varying (one file per year)
- modified by degradation/shocks (optional)
---

## Cell
The basic spatial unit of the model (grid cell).
A cell typically stores:
- coordinates (X, Y)
- region identifier (optional)
- current owner (AFT)
- capital values
- computed production and utility values
- any restrection and PA

CRAFTY operations such as competition and abandonment act on sets of cells.

---

## Competition
A decision process where candidate AFTs compete for cells.
Competition typically uses a utility function derived from:
- service demands vs. supply (marginal utility)
- AFT production based on capitals
- optional policy terms (tax/subsidy)
- constraints (masks/restrictions)

The winner becomes the new “owner” of the cell (land-use change).

---

## Config (YAML config)
The main configuration file that controls:
- where the dataset is (`project_path`)
- which scenario is run (`scenario`)
- enabled mechanisms (regionalization, updaters, seeding, output options, etc.)
- optional path overrides (baseline, capitals, demands, masks, institutions, …)

---

## Coupling / Waiting flags
An optional workflow where CRAFTY pauses at configured years and waits for an external “flag” file/folder.
Used for loose coupling with other models/pipelines (e.g., a demand model, a vegetation model, or a calibration loop).

---

## Demand (service demand)
A target/trajectory for service consumption (often per region and per year).
Demand is a key driver because it determines marginal utilities (how valuable additional supply is).

---

## Degradation / Shock
Optional processes that modify capitals (or derived productivity) over time.
Degradation inputs are typically time-indexed spatial layers applied each year or at specified years.

“Shock” is used as a generic term for hazards (e.g., drought, wildfire, pest outbreaks) if enabled in the project.

---

## GUI
The JavaFX desktop interface for CRAFTY.
Used for:
- browsing inputs
- editing parameters (AFT tables, behaviour, etc.)
- running simulations interactively
- visualising maps and time series outputs

---

## Headless run
Running CRAFTY without a GUI (command line / server / HPC).
Headless runs use the same YAML config approach and are best for batch experiments.

---

## Institution / Policy (taxes/subsidies)
Optional components representing policy interventions that modify competition outcomes.
Typically implemented via:
- service-level taxes/subsidies
- land-use / AFT-specific taxes/subsidies
- restrictions or controls that block/shape transitions

---

## Land-use change
A change in the owner-AFT of one or more cells due to:
- competition
- abandonment of land
- take-over of unmanaged land

Land-use change is a primary outcome analysed in outputs.

---

## Land-use control / Mask
Inputs that restrict or steer what can happen on certain cells.
Examples:
- protected areas where certain transitions are blocked
- “urban mask” where land cannot change
- cell-specific ownership restrictions

Masks are typically time-indexed and can be updated per year. crafty keep the same Masks until find an updated year.

---

## Marginal utility
A derived measure representing the “value” of increasing supply of a service, often dependent on demand–supply gaps.
Marginal utility is used in utility calculations and thus influences competition and land-use change.

---

## Metadata directory
A folder containing core reference tables used across scenarios, such as:
- AFT definitions
- service list and parameters
- capital names
- scenario names and time windows

Exact filenames depend on the dataset template.

---

## Output folder / Run id
The folder created for each run, typically under:
`<project_path>/output/<scenario>/<run_id>/`

The run id may be:
- explicitly set (e.g., `output_folder_name`)
- or an automatic timestamp

---

## Project
A dataset folder containing:
- metadata tables
- one or more scenarios
- baseline map(s)
- time-varying inputs (capitals, demands, masks, etc.)

In YAML, this is referenced by `project_path`.

---

## Region / Regionalisation
A mode where the model operates on multiple sub-areas (“regions”) instead of a single global (“world”) region.
Regions can have:
- their own service demand trajectories
- their own outputs
- potentially region-specific parameter sets (dataset-dependent)

In YAML, this is controlled by `regionalization: true/false`.

---

## Runner (ModelRunner)
The core engine that:
- initialises model state from input data
- executes the simulation loop over years
- triggers scheduled modules (updaters)
- triggers output writing

---

## Scenario
A named configuration of time-varying inputs within a project.
Scenarios usually correspond to external scenario families (e.g., SSPs), but the naming is dataset-specific.

In YAML, this is referenced by `scenario`.

---

## Seed / Seeding strategy
A strategy controlling which subset of cells participates in certain processes each year
(e.g., competition and abandonment).

Common modes:
- `rank`: deterministic ranking-based selection (e.g., lowest utility cells)
- numeric seed: reproducible pseudo-random selection
- explicit seed file: user-specified list of cells

In YAML, controlled via `seedID` .

---

## Service
A modelled quantity produced on the landscape (e.g., food, timber, carbon storage).
Services are produced by AFTs based on capitals and then compared to demand to compute marginal utilities.

Service configuration can include:
- demand trajectories
- utility weights
- taxes/subsidies (optional)

---

## Supply
The amount of a service produced by the landscape (global and/or per region).
Supply is computed from cell-level production (AFT × capitals) aggregated to region totals.

---

## Tick / Year
One simulation time step (typically one year).
Each tick runs a schedule of updaters and then writes outputs (depending on settings).

---

## Updater
A scheduled model module executed during each tick (year).
Updaters can:
- load year-specific inputs (capitals, masks, degradation)
- compute productivity/utility
- perform competition/abandonment
- enforce restrictions
- write outputs
- coordinate coupling (waiting flags)

Updaters are central to “what happens each year” in the model.

---

## Utility
A score used to compare and rank AFT performance on a cell.
Utility typically combines:
- service marginal utilities
- AFT production per service
- weights and policy terms
- constraints and penalties

Utility drives decisions such as competition outcomes and ranked seeding.

---
