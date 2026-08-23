# Data model

This page describes the *in-memory objects* CRAFTY uses during a run, how they relate to each other,
and which input files populate which parts of the model state.

If you’re looking for “where do I put my CSV files?”, start with:
- `docs/user-guide/02-running-scenarios.md` (scenario discovery + path overrides)
- `docs/appendices/default-scenario-layout.md` (recommended folder layout)

---

## 0) Big picture

CRAFTY uses a **cell-based grid**. Each cell has:
- a current **owner** (an AFT, or unmanaged/abandoned, or a non-competing mask placeholder)
- a vector of **capitals**
- a **region** membership (optional, used when regionalisation is enabled)

A run is driven by time-varying inputs (usually yearly) such as:
- service **demands**
- service **utility weights**
- taxes/subsidies (service-level and land/AFT-level)
- masks / restrictions
- capital updates and/or degradation “shock” maps

---

## 1) Core identifiers and conventions

These naming conventions are important because CRAFTY relies on string keys to match inputs to model entities.

### 1.1 Coordinates
- Cells are identified by integer coordinates: `X`, `Y`
- Internally, a common index key is the string `"X,Y"`

### 1.2 Years
- Time is discrete (typically yearly).
- Many inputs are either:
  - **one file per year**, or
  - **one CSV containing a vector** that spans the model horizon (start..end)

### 1.3 Labels (must match)
- **Service names** must match the service metadata labels.
- **Capital names** must match the capital metadata labels.
- **AFT labels** must match AFT definitions and baseline owner labels.

Unknown columns are usually ignored; missing files often fall back to defaults (see below).

---

## 2) Core entities (what exists in memory)

### 2.1 Project + Scenario
A *project* is the dataset folder referenced by `project_path`.  
A *scenario* selects one set of time-varying inputs and is referenced by `scenario`.

At runtime, CRAFTY resolves input locations using:
1) explicit config overrides (if provided), otherwise
2) default scenario folder structure under the project

See `docs/user-guide/02-running-scenarios.md` for the resolution rules.

---

### 2.2 Timestep (model clock)
The model clock holds:
- `startYear`
- `endYear` (inclusive)
- `currentYear`
- `tick` (iteration counter)

Many update modules select files using `currentYear`.

---

### 2.3 Cell
A cell is the fundamental spatial unit. Typical cell state includes:

- `x`, `y` (integer coordinates)
- `owner` (AFT label or special placeholder)
- `maskType` (optional, used for restrictions)
- `capitals` (map: `capitalName -> value`)
- derived per-step values (productivities, utilities, etc.)

Owners are commonly classified as:
- **AFT** (active, competing manager)
- **Abandoned / Unmanaged** (eligible for take-over)
- **MASK** (non-competing placeholder used by land-use control layers)

---

### 2.4 Region
A Region is a named subset of cells and contains region-specific state:

- `name`
- `cells` (all cells belonging to the region)
- `unmanageCellsR` (cells currently unmanaged/abandoned in that region)
- `servicesHash` (per-service objects for that region)

If regionalisation is enabled, each region is stepped separately and then aggregated to “world totals”.

---

### 2.5 Service
A Service stores region-specific time series used by marginal utility and competition:

- `demands[year] -> value`
- `weights[year] -> value` (utility weights)
- `taxes_subsidies[year] -> value` (policy signal)
- `calibration_Factor` (used when initial equilibrium calibration is enabled)
- optional metadata such as `category`

The service registry is global (one authoritative list of service labels), but each region has its own Service objects.

---

### 2.6 AFT
An AFT defines production and behaviour. Typical AFT state includes:

**Identity**
- `label`, `completeName`
- `type` (active / abandoned / mask classification)
- optional category (if using categorisation-based “give-in”)

**Production**
- `productivityLevel[service] -> multiplier`
- `sensByService[service][capital] -> exponent` (sensitivity matrix)

**Behaviour**
- “give-up” / “give-in” distribution parameters
- noise ranges
- optional behaviour model parameters

**Policy**
- land and service policy effects are stored per cell while the cell-level policy mechanism is active

AFT parameters can be time-varying if you provide update files (see below).

---

## 3) Inputs and how they populate the model

This section explains the *schema expectations* and the corresponding model component that consumes each input.

> Important: the exact filenames depend on your dataset template. The point here is the **columns** and **meaning**.

---

## 3.1 Metadata tables (define the canonical names)

### Services metadata
Defines the authoritative service list (used everywhere).
Typical columns:
- `Label` or `Name` (used as the identifier)
- optional: `Category`
- optional: `Penalise_Oversupply` (boolean-like)

### Capitals metadata
Defines the authoritative capital list (used by capital loading and by AFT sensitivity matrices).
Typical columns:
- `Label` or `Name`

---

## 3.2 Baseline map (initial state)
Creates cells and sets initial owners.

**Minimal schema (baseline CSV)**
- `X`, `Y`
- a column indicating the initial owner/AFT label (baseline owner labels must match AFT labels).

---

## 3.3 Capitals (time-varying capital values)
Loads (or updates) capital values on cells.

**Typical schema**
- `X`, `Y`
- one column per capital (column header must match capital list)
- Either one file per year

---

## 3.4 Capital degradation / shock maps (optional)
Applies a degradation factor to capitals.

**Typical schema**
- `X`, `Y`
- one column per capital (values interpreted as degradation factors)

**Typical interpretation**
- `capital := capital * (1 - degradationFactor)`

If no degradation file exists for a year, capitals are unchanged that year.

---

## 3.5 Service demands (time series)
Provides exogenous demand trajectories per service.

**Typical schema**
- columns = service names
- rows = yearly values (aligned with the model horizon)

**Regional vs world**
- In regionalised runs, demand is typically provided per region.
- CRAFTY aggregates regional demands into a world-level demand series for reporting and plots.

Missing values beyond the vector length are typically treated as `0.0` so every year has a demand value.

---

## 3.6 Service utility weights (time series, optional)
Scales the importance of each service in utility calculations.

**Typical schema**
- columns = service names
- rows = yearly values

**Defaults**
- If no file is found, weights default to `1.0` for all services and all years.

---

## 3.7 Service taxes/subsidies (time series, optional)
Adds a service-level incentive term.

**Typical schema**
- columns = service names
- rows = yearly values

**Defaults**
- If no file is found, taxes/subsidies default to `0.0` for all services and all years.

---

## 3.8 AFT production + sensitivity (time-varying, optional)
Defines how AFTs convert capitals into service production.

**Typical production file schema**
- rows correspond to services
- includes a `Production` column (per-service productivity level)
- includes sensitivity information used to build:
  - `service -> (capital -> exponent)`

Only services in the canonical service list and capitals in the canonical capital list are applied.
if there is any service/capital missing -> error

---

## 3.9 AFT behaviour (time-varying, optional)
Behaviour is typically provided as a **key/value** style CSV and populates parameters controlling:
- give-up / abandonment
- give-in thresholds / acceptance conditions
- noise bounds

---

## 3.10 Land taxes/subsidies (optional)
Adds an AFT-level term (often region-aware).

**Typical schema**
- AFT label
- year-indexed values

**Defaults**
- If no file is found, a constant `0.0` is applied.

---

## 3.11 Masks / land-use control (optional but common)
Masks assign a `maskType` to cells and optionally enforce transition restrictions.

### Mask CSV (cell assignment)
CRAFTY mask files are typically time-aware and include:

- `X`, `Y`
- one  `Year_*` columns (e.g., `Year_2020`, `Year_2025`, ...)

A row “activates” the mask for a given year when the corresponding `Year_*` entry contains `"1"`.

### Restriction CSV (allowed/forbidden transitions)
A restriction table is typically a matrix:

- first column = current owner headers
- first row = competitor headers
- values interpreted as boolean-like (commonly `"1"` = allowed)

At runtime, restrictions are often stored with a composite key like:
`"<currentOwner>_<competitor>" -> true/false`

---

## 3.12 Waiting flags (optional, for loose coupling)
Some workflows pause the model at specific years until an external process creates a “flag” file/folder.

Input is typically:
- a CSV listing years and corresponding “flag” paths
- used only if your coupling updater is enabled

---

## 4) Derived runtime state (computed during the run)

CRAFTY constructs several runtime “tables” each step/year:

### 4.1 Supply snapshots
Per region and per service, the model computes:
- realised supply (aggregated from cells)

It then aggregates to world totals for reporting.

### 4.2 Demand/weight/tax snapshots
Per region and per service, each year CRAFTY snapshots:
- demand
- weight
- service tax/subsidy
and computes useful diagnostics such as a normalised demand–supply gap.

---

## 5) Minimal dataset to run (required vs optional)

### Required (typical)
- project + scenario resolution (`project_path`, `scenario`)
- services metadata
- capitals metadata
- baseline map (X,Y + initial owner)
- capitals for at least the initial year (or a consistent initial dataset)
- AFT definitions (at least production/sensitivity sufficient to compute supply)
- service demands for the run mode (world or per region)

### Optional but common
- service weights (defaults to 1)
- service taxes/subsidies (defaults to 0)
- land taxes/subsidies (defaults to 0)
- masks + restrictions
- degradation/shocks
- waiting flags (for coupling)
- institution/policy module inputs

---

## 6) Related pages

- Glossary: `00-glossary.md`
- Architecture (code structure): `01-architecture.md`
- Running scenarios (path resolution): `../user-guide/02-running-scenarios.md`
- Outputs produced: `../user-guide/04-outputs.md`
