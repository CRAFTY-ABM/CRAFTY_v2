# Cells and regions

This page documents the **Cells** and **Regions** components: how the grid is represented, how regions are created,
how region membership changes (or not), and how cells flow through annual processes (competition, abandonment, masks).

This page complements:
- `../02-data-model.md` (concepts + input mapping)
- `../03-config-reference.md` (regionalisation config key)
- `../../user-guide/02-running-scenarios.md` (scenario data discovery)

---

## 0) Core idea

CRAFTY is a **cell-based** land-use change model:

- A **Cell** is the smallest spatial unit (grid cell)
- A **Region** is a named group of cells (used for regional demands and region outputs)

A cell always belongs to exactly one region in a regionalised run.
In non-regional runs, all cells belong to a single implicit “world” region.

---

## 1) The Cell object (what it stores)

A cell typically stores:

### 1.1 Identity
- `x`, `y` (integer grid coordinates)
- a stable index key such as `"x,y"` (common in lookup maps)

### 1.2 Ownership / land use
- `owner` (AFT label or placeholder)
- `maskType` (optional, used by land-use control updaters)

Ownership changes over time due to:
- competition
- abandonment
- take-over of unmanaged/abandoned cells (optional)

### 1.3 Capitals
- map of capital values: `capitalName -> value`
- capital values may be:
  - static
  - updated yearly (scenario inputs)
  - modified by degradation/shocks (optional)

### 1.4 Derived per-year values
Depending on enabled modules, the model caches derived values per year, e.g.:
- productivity per service
- marginal utility contributions
- total utility

These are computed by updaters and used by selection/competition logic.

---

## 2) How the grid is created (baseline loading)

Cells are created during initialisation from the baseline map:

- baseline file provides `X`, `Y`
- baseline assigns initial `owner` (AFT label)
- some datasets also provide region ID or other attributes in baseline or GIS tables

If the baseline includes an owner label not defined in the AFT list, the run usually fails or falls back to a placeholder
(depending on validation settings). Always keep baseline and AFT labels consistent.

---

## 3) Regions (what they store)

A Region stores:

- `name` (region identifier string)
- `cells` (all cell references in this region)
- `unmanageCellsR` (cells currently unmanaged/abandoned in this region)
- `servicesHash` (region-specific Service objects: demand/weights/taxes time series)
- region-level outputs (time series tables, if enabled)

Region membership is normally **fixed** for the whole run.

---

## 4) Regionalisation modes

### 4.1 Non-regional run (`regionalisation: false`)
- model builds one region (world)
- demand files are treated as “world demand” (or a single region)
- outputs are global only

### 4.2 Regionalised run (`regionalisation: true`)
- model builds multiple named regions
- each region has its own service demands/weights/taxes
- allocation decisions are driven by **regional** marginal utility
- outputs can be written per region (if enabled)

---

## 5) How regions are assigned to cells

Common patterns (dataset-dependent):

### 5.1 Region id in baseline or a GIS table
The region loader reads a region id for each cell and assigns it to a region.

This is usually the most robust approach.

### 5.2 Region id via spatial mask / region map
Some datasets provide a “region map” similar to a capital layer (X,Y → regionId).

The loader joins it to cells using X/Y.

### 5.3 Fallback
If region assignment fails (missing file for demands, missing keys, mismatch), CRAFTY may fall back to one “world” region.

**If you expected multiple regions but got one:**
- check logs for missing region inputs
- check demand discovery for region files (region token matching)
- verify region identifiers are consistent across region map and filenames

---

## 6) What changes over time: cells vs regions

### 6.1 Region membership (usually does NOT change)
Region membership is normally fixed: a cell remains in its region for the whole run.

### 6.2 Ownership (DOES change)
Ownership changes through annual processes:
- competition
- abandonment
- take-over (optional)

This is the main land-use change mechanism.

### 6.3 Mask type (can change if masks are time-aware)
If you use time-indexed masks (e.g., year-specific land-use control), a cell’s `maskType` can change when:
- a mask updater applies a year layer
- land-use control rules update the allowed/forbidden transitions

---

## 7) How cells participate in annual processes

Each year, cells flow through a schedule of operations (implemented as updaters).
The most important cell-handling steps are:

### 7.1 Seed selection (participating cells)
Many processes operate only on a subset of cells each year (“seed”).
Seed selection is controlled by `cell_selection`, `random_seed`, and the participating fraction.

Seed selection affects:
- competition workload
- speed of land-use change
- spatial patterns (rank seeding can create systematic change fronts)

### 7.2 Competition
For each seed cell:
- a competitor candidate set is built (all AFTs or a reduced set using neighbour priority)
- restrictions/masks filter invalid competitors
- the best candidate (or a probabilistic choice) can take over

Cell owner is updated when a competitor wins.

### 7.3 Abandonment
A subset of cells (also seed-based) may abandon their owner if:
- their utility is below a threshold (if enabled)
- they are selected under abandonment percentage constraints

When abandoned, cell owner becomes an unmanaged/abandoned placeholder.

### 7.4 Take-over of unmanaged cells (optional)
A subset of unmanaged cells can be offered to competitors.
This mechanism helps reallocate abandoned land when conditions improve.

---

## 8) Masks and restrictions (cell-level controls)

Masks and restrictions influence cell processes by:

- assigning a `maskType` to selected cells
- applying transition rules such as:
  - forbid certain competitors on masked cells
  - force a fixed owner for some years
  - block land-use change entirely in protected areas

Common mask file pattern:
- columns `X`, `Y`
- one or more `Year_*` column (activate mask in specific years)

Restriction matrix pattern:
- current owner on rows
- competitor on columns
- boolean-like entries (`1` allowed, `0` forbidden)

---

## 9) Outputs related to cells and regions

### 9.1 Cell-level (maps)
If map export is enabled, you may get:
- owner maps per selected year
- capital maps per selected year
- optional diagnostic maps (utility, productivity) depending on your build

### 9.2 Region-level
If regional outputs are enabled, you may get:
- demand vs supply time series per region
- AFT areas per region
- service composition by AFT per region (if tracking enabled)

World totals are typically:
- sum across regions (for demand and supply)
- average across regions for some parameters (weights/taxes), depending on implementation

---

## 10) Troubleshooting (cells/regions)

### 10.1 “All cells are in one region”
- region assignment failed → world fallback
- demand discovery could not find region files
- region IDs mismatch between region map and filenames

**Fix**
- verify region map file exists and has correct X/Y keys
- ensure region names appear in demand filenames as expected (token format)
- check logs for region loader warnings

### 10.2 “No land-use change”
- participating percentage too low
- masks block transitions
- restriction matrix forbids all transitions
- competitor set is empty due to filtering

**Fix**
- temporarily disable masks and rerun short window
- check restriction matrix orientation (current vs competitor)
- increase participating percentage for a diagnostic run

### 10.3 “Unmanaged cells never get taken over”
- take-over mechanism disabled or set to 0%
- restrictions prevent competitors taking unmanaged cells
- unmanaged placeholder label not recognised in restriction table

**Fix**
- enable take-over and set a non-zero percentage
- confirm unmanaged placeholder label matches the restriction matrix keys

---

## Related pages

- Data model: `../02-data-model.md`
- Config reference: `../03-config-reference.md`
- Running scenarios: `../../user-guide/02-running-scenarios.md`
- Troubleshooting: `../../user-guide/05-troubleshooting.md`
