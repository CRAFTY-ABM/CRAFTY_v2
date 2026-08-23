# File formats (CSV conventions used by CRAFTY)

This appendix documents the **common CSV file formats and conventions** used across CRAFTY datasets. It focuses on:
- column naming rules
- how time is encoded (years)
- how region files are named
- the typical shapes of the main input tables (baseline, capitals, demands, AFT params, etc.)
- frequent mistakes and how to avoid them

> CRAFTY primarily uses **CSV** files. In some workflows there are optional GIS formats,
> but the “portable default” remains CSV.

---

## 0) General conventions (apply everywhere)

### 0.1 Encoding and separators
- Crafty is case senstive `IntBF` and `IntBf` are not the same
- Encoding: **UTF‑8** recommended
- Separator: `,` (comma)
- Decimal point: `.` (dot)
- Avoid thousands separators (`1,000`) inside numeric cells

### 0.2 Headers are required
Almost all loaders expect the first row to be a header row.

### 0.3 Strings must match exactly
Many joins are string-based:
- service names
- capital names
- AFT labels
- region ids

So keep spelling/case consistent across:
- `csv/*.csv` metadata files
- filenames
- column headers

### 0.4 Coordinate columns
The canonical coordinate columns are:

- `X` (integer)
- `Y` (integer)

These are used to join:
- baseline → capitals
- baseline → masks
- baseline → region map/tables (dataset-dependent)

---

## 1) Baseline map (`worlds/Baseline_map.csv`)

**Purpose**
- defines the grid
- defines the initial owner (AFT label or placeholder)

**Typical structure**
```text
X,Y,FR/AFT/Owner/Owners
10,20,IntC3C
10,21,Forest
...
```

**Rules**
- `X`,`Y` must cover the full model grid
- `Owner` values must match AFT labels (or placeholders recognised by the model)
- Avoid quotes (\")  for String

**Common problems**
- owner labels not in AFT list → run fails early
- duplicated X,Y rows → unpredictable behaviour (The last one read will be considered)
- missing cells (holes) → model grid smaller than expected

---

## 2) Capital layers (`worlds/capitals/<scenario>/*_YYYY.csv`)

**Purpose**
- provide per-cell capital values for each year

**Typical structure**
```text
X,Y,financial,human,manufactured,social
10,20,0.21,0.73,0.10,0.55
...
```

**Rules**
- headers must match `csv/Capitals.csv` names
- one file per year is safest
- numeric values must parse as doubles

**Year discovery patterns**
- many datasets use filename year tokens (e.g., `_2020.csv`)
- the model reads the correct file for the current year based on naming conventions

**Common problems**
- missing a year file → crash at that year
- wrong scenario name in filename → file not discovered
- capitals swapped/missing header → “capital not found” warnings

---

## 3) Region definitions (`GIS/*.csv` and/or `services/demand/EU_Regions.csv`)

Regionalisation is dataset-dependent, but common patterns include:

### 3.1 Region list table
A table listing region ids and names:

```text
Region_Code,RegionName
DE,Germany
FR,France
...
```

### 3.2 Cell-to-region mapping table
A table mapping each X,Y to a region id:

```text
X,Y,Region_Code
10,20,DE
...
```

**Rules**
- Region ids must match the suffix tokens used in demand filenames (e.g., `_DE.csv`)

---

## 4) Service demand tables (`services/demand/<scenario>/*.csv`)

**Purpose**
- provide time series of service demand
- per region in regionalised runs

**Typical file naming**
- EU total: `ssp126_demands_EU.csv`
- per region: `ssp126_demand_DE.csv`, `ssp126_demand_FR.csv`, …

**Typical structure (wide format)**
```text
Year,Food,Timber,Carbon
2020,100,50,80
2021,101,50,82
...
```

**Rules**
- service column headers must match `csv/Services.csv`
- years must cover run horizon (or at least model will need values for each simulated year)

**Common problems**
- missing region file → run fails
- different service list between EU file and region file → mismatched outputs
- wrong region suffix (`EL` vs `GR`) → file not discovered → run fails

---

## 5) Utility weights (`services/Service_Utility_Weights/*.csv`)

**Purpose**
- weight services in marginal utility calculation

**Typical naming**
- `ssp126_Utility_Weight_EU.csv` (often EU-level but works by regions too)

**Typical structure**
```text
Year,Food,Timber,Carbon
2020,1.0,1.0,1.0
2021,1.0,1.0,1.0
...
```

**Defaults**
- if missing: weights often default to `1.0`

---

## 7) AFT parameter files (`AFTs/agents/default_agents/*.csv`)

**Purpose**
- define behavioural + economic parameters per AFT

**Naming**
- `AftParams_<AFT>.csv` 

**Typical structure**
This file is a table of named parameters:
Examples vary across projects, but common fields include:
- decision parameters (e.g., give-up/give-in thresholds)

**Rule of thumb**
- keep parameter names stable and documented
- avoid changing parameter names without updating loaders

(See `reference/04-components/afts.md`.)

---

## 8) AFT production tables (`AFTs/production/default_production/<AFT>.csv`)

**Purpose**
- define how capitals translate into service supply for each AFT

**Common shapes**

### 8.1 Productivity by service
```text
Service,Productivity
Food,1.2
Timber,0.4
Carbon,0.9
```

### 8.2 Sensitivity by service × capital (exponents)
```text
Service,financial,human,manufactured,social
Food,0.2,0.3,0.1,0.0
Timber,0.0,0.1,0.3,0.2
...
```

each AFT has a separet file for sensitivity.
Always follow the loader expected format for your build.

---

## 10) Mask files (`worlds/LandUseControl/...`)

### 10.1  One file per year (common in your dataset)
Example filename:
- `Urban_Mask_SSP126_Year_2020.csv`

Typical structure:
```text
X,Y,Year_2020
10,20,1
...
```

### 10.3 Restriction matrices
Matrix table:
- rows = current owner
- cols = competitor owner
- values = 0/1

(See `reference/04-components/masks.md`.)

---

## 11) Output tables (what to expect)

CRAFTY writes CSV outputs such as:
- demand/supply time series
- AFT composition
- cell snapshots (sometimes per year)
- event counters

Output formats are documented in:
- `user-guide/04-outputs.md`
- `reference/04-components/outputs.md`

### 11.1 Landscape fragmentation output

When `generate_land_fragmentation_output: true`, CRAFTY writes
`<scenario>-land-fragmentation.csv` with one row per year:

| Column | Interpretation |
|---|---|
| `year` | Simulation year represented by the row. |
| `total_cells` | Number of spatial cells included. |
| `aft_classes` | Number of owner/AFT classes present, including unmanaged cells when present. |
| `adjacent_pairs` | Moore-neighbour cell pairs (shared edge or corner), counted once. |
| `same_aft_adjacent_pairs` | Moore-neighbour pairs whose cells have the same AFT. |
| `different_aft_adjacent_pairs` | Moore-neighbour pairs whose cells have different AFTs. |
| `same_aft_adjacency` | Fraction of adjacent pairs with the same AFT; higher means more aggregation. |
| `adjacency_clustering_index` | Same-AFT adjacency adjusted for the adjacency expected from current AFT shares; higher means more clustering beyond composition alone. |
| `boundary_edge_density` | Different-AFT Moore-neighbour pairs per cell; higher means more fragmentation. |
| `patch_count` | Number of eight-neighbour (Moore) connected same-AFT patches. |
| `patch_density` | Patch count divided by total cells. |
| `mean_patch_size_cells` | Mean number of cells per patch. |
| `largest_patch_size_cells` | Number of cells in the largest patch. |
| `largest_patch_share` | Fraction of all cells belonging to the largest patch. |
| `effective_mesh_size_cells` | Area-weighted mean patch size, expressed in cells. |
| `normalized_effective_mesh_size` | Effective mesh size divided by total cells; higher means larger connected patches. |
| `shannon_diversity` | Diversity of AFT cell shares, included to distinguish composition change from spatial rearrangement. |

Off-map neighbours are ignored. All cells without an owner form one unmanaged class.

---

## 12) Validation tips (recommended before long runs)

### 12.1 Run quick checks on every CSV
- header exists
- no duplicated X,Y in spatial tables
- numeric parsing works (no `NaN`, `null`, commas inside numbers)

### 12.2 Sanity-check your labels
- AFT labels in baseline match `AFTsMetaData.csv`
- capital columns match `Capitals.csv`
- service columns match `Services.csv`
- region ids match demand filename suffixes

### 12.3 Test with a short horizon
Run 2020–2022 with:
- no maps
- deterministic seeding
- logs on

This catches 80% of “file discovery” and “format mismatch” issues.

---

## Related pages

- Dataset layout: `default-scenario-layout.md`
- Components:
  - Services: `../reference/04-components/services.md`
  - AFTs: `../reference/04-components/afts.md`
  - Masks: `../reference/04-components/masks.md`
- Config reference: `../reference/03-config-reference.md`
