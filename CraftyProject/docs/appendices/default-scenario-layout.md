# Default scenario layout (regionalisation-ready)

This appendix documents a **typical CRAFTY dataset structure** used for regionalised runs, based on the
CRAFTY‑EU 1km “data0.8” layout you shared.

It explains:
- what each top-level folder is for
- how files are named and discovered by the loaders
- which parts are scenario-specific (e.g., `ssp126`) vs shared defaults
- the practical “rules of thumb” you can follow when preparing a new dataset

> This layout works well for both **headless runs** and the **GUI**, and scales to large HPC experiments.

---

## 0) Top-level folder overview

A typical project data root contains:

```text
CRAFTY-EU-1km/
├───AFTs
│   ├───agents
│   ├───land_taxes_subsidies
│   └───production
├───csv
├───GIS
├───output
│   └───<scenario>                   (generated)
├───Input-Data-Analyses              (Not used by the model, safe to ignore)
├───services
│   ├───demand
│   ├───services_taxes_subsidies
│   └───Service_Utility_Weights
└───worlds
    ├───behaviour
    ├───capitals
    └───LandUseControl
        ├───Urban
        ├───Water
        └───...
```

### 0.1 What is “scenario” here?
In this layout, scenario names are folders such as:
- `ssp126`, `ssp245`, `ssp370`, `ssp445`, `ssp585`

Scenario folders appear under:
- `services/demand/<scenario>/`
- `worlds/capitals/<scenario>/`
- `worlds/behaviour/<scenario>/`
- `worlds/LandUseControl/<MaskType>/<scenario>/`
- `AFTs/land_taxes_subsidies/` (scenario prefix in filename)

---

## 1) `csv/` (global metadata tables) (required)

This folder contains metadata tables that define the *canonical lists* and labels.

**Typical files**
- `AFTsMetaData.csv`  
  Defines the AFT list (labels + optional names/categories).
- `Capitals.csv`  
  Defines the capital list (labels + optional normalisation metadata).
- `Services.csv`  
  Defines the service list (labels + optional categories).
- `scenarios.csv`  
  scenario registry (names, start/end years, (optional) descriptions).

**Why this matters**
- Loader joins rely on **exact string matches** between:
  - AFT labels in baseline and AFT definitions
  - capital names in capital layers and AFT production tables
  - service names in demands/weights/taxes and service registry

---

## 2) `GIS/` (regionalisation and spatial joins) (optional)

This folder contains GIS helper tables used during initialisation.

**Typical files**
- `EU_Regions.csv`  
  Region definitions and/or lookup table used to assign cells to regions.

This is the place for:
- region naming conventions (e.g., `DE`, `FR`, `AT`, …)
- the mapping between grid cell coordinates and region id (dataset-dependent)

---

## 3) `worlds/` (baseline, capitals, land-use control)

### 3.1 `worlds/Baseline_map.csv` (required)
This is the core baseline file defining the grid and initial owner.

**Common columns**
- `X`, `Y`
- baseline owner label (must match AFT labels or placeholders)

Baseline is the anchor for:
- creating cells
- loading per-cell capitals (joined by X/Y)
- applying masks (joined by X/Y)

---

### 3.2 `worlds/capitals/<scenario>/` (required)
Time-varying capital layers, typically one CSV per year:

Example pattern:
```text
EU_capitals_ssp126_2020.csv
EU_capitals_ssp126_2021.csv
...
EU_capitals_ssp126_2100.csv
```

**Common structure**
- `X`, `Y`
- one column per capital (column header must match `Capitals.csv`)

**Rule of thumb**
- One file per year is easiest to reason about and debug.
- If some years are missing, most loaders will fail late in the run—so make sure the series covers the run horizon.

---

### 3.3 `worlds/behaviour/<scenario>/` (optional)
Cell-level (or AFT-level) behaviour parameters used by abandonment/competition heuristics.

Example:
```text
Cell_behaviour_parameters_2020.csv
```

In many datasets this file is “static” but stored under each scenario for clarity and compatibility.

---

### 3.4 `worlds/LandUseControl/<MaskType>/<scenario>/` (optional but common)
Time-varying masks and restrictions.

for example:
- `Urban/`
- `Water/`

Each mask type usually has:
- a default restriction matrix (applies unless overridden)
- per-scenario mask layers
- optionally year-specific restriction matrices

Example (Urban):
```text
default_UrbanMask_Restrictions.csv
2020_UrbanMask_Restrictions.csv          (optional override)
2025_UrbanMask_Restrictions.csv          (optional override)
Urban_Mask_SSP126_Year_2020.csv
Urban_Mask_SSP126_Year_2021.csv
...
Urban_Mask_SSP126_Year_2100.csv
```

Example (Water):
```text
default_WaterMask_Restrictions.csv
Water_Mask_SSP126_Year_2020.csv
Water_Restrictions_2020.csv              (optional custom name)
```

**Rule of thumb**
- name mask layers with both scenario and year
- keep restriction matrices in the same folder as the mask type and outside of scenarios maks (parent dir where csv files masks)
- if you override restrictions for some years, make the year explicit

---

## 4) `services/` (demand, weights, service taxes/subsidies)

This folder contains the scenario drivers for services.

### 4.1 `services/demand/<scenario>/` (required)
Demand time series for each region, plus an EU total file.

Example:
```text
ssp126_demands_EU.csv
ssp126_demand_DE.csv
ssp126_demand_FR.csv
...
```

**Common structure**
- one column per service (header must match `Services.csv`)
- each row corresponds to one year index (aligned to run horizon)

**Regionalisation rule**
- If `regionalization: true`, you typically provide *one demand file per region*.

---

### 4.2 `services/Service_Utility_Weights/` (optional)
Scenario-level weights (often EU-level only):

Example:
```text
ssp126_Utility_Weight_EU.csv
ssp245_Utility_Weight_EU.csv
...
```

Defaults:
- if missing, weights default to 1.0 for all services and years.

---

### 4.3 `services/services_taxes_subsidies/` (optional)
Scenario-level service incentives (often EU-level only):

Example:
```text
ssp126_services_taxes_subsidies_EU.csv
```

Defaults:
- if missing, taxes/subsidies default to 0.0 for all services and years.

---

## 5) `AFTs/` (agents, production, and optional AFT-level policies)

### 5.1 `AFTs/agents/default_agents/` (required)
AFT parameter files, typically one CSV per AFT:

Example:
```text
AftParams_IntC3C.csv
AftParams_ExtBF.csv
...
```

the dataset also could contains a subfolder:
```text
default_agents/2025/
```
which can be used for year-tagged AFT parameter variants if default are used.

**Rule of thumb**
- keep one file per AFT
- make the filename include the AFT label to avoid ambiguity

---

### 5.2 `AFTs/production/default_production/` (required)
AFT production tables, typically one CSV per AFT:

Example:
```text
IntC3C.csv
ExtBF.csv
...
```

These tables define:
- productivity level per service
- sensitivity exponents per service × capital

(See `reference/04-components/afts.md` for details.)

---

### 5.3 `AFTs/land_taxes_subsidies/` (optional)
AFT-level land taxes/subsidies.

Example:
```text
ssp126_land_taxes_subsidies_EU.csv
```
---

## 6) `output/` (generated by the model)

Outputs are written under:
```text
output/<scenario>/<run_id>/
```

Your example:
```text
output/ssp126/Default_Run_Output_2026_01_06_20_07/
  config.txt
  LOGGER.txt
  ssp126-AverageUtilities.csv
  ssp126-landEventCounter.csv
  ssp126Total-AggregateAFTComposition.csv
  ...
  MapsPlots/
    map_2020.png
    map_2021.png
    map_2022.png
```

**Rule of thumb**
- treat `output/` as generated content; don’t mix it with inputs
- store the config used inside the run folder

---

## 7) Minimal checklist for a new dataset

To run (regionalised or not), make sure you have at least:

- `csv/Services.csv`, `csv/Capitals.csv`, `csv/AFTsMetaData.csv`  
- `worlds/Baseline_map.csv`  
- `worlds/capitals/<scenario>/..._YYYY.csv` for all years needed  
- `services/demand/<scenario>/...csv` (EU-level, and per region if `regionalization: true`)  
- `AFTs/agents/default_agents/*.csv`  
- `AFTs/production/default_production/*.csv`  

Optional:
- `worlds/LandUseControl/...` (masks)
- service weights and taxes/subsidies
- AFT land taxes/subsidies

---

## 8) Example
Directory template:
```text
CRAFTY-EU-1km/
├───AFTs
│   ├───agents
│   │   └───default_agents
│   │       └───2025
│   ├───land_taxes_subsidies
│   └───production
│       └───default_production
├───csv
├───GIS
├───Input-Data-Analyses
│   └───Capitals-trends-through-Scenarios
├───output
│   └───ssp126
│       └───Default_Run_Output_2026_01_06_20_07
│           └───MapsPlots
├───services
│   ├───demand
│   │   ├───ssp126
│   │   ├───...
│   │   └───ssp585
│   ├───services_taxes_subsidies
│   └───Service_Utility_Weights
└───worlds
    ├───behaviour
    │   ├───ssp126
    │   ├───...
    │   └───ssp580
    ├───capitals
    │   ├───ssp126
    │   ├───...
    │   └───ssp585
    └───LandUseControl
        ├───Urban
        │   ├───ssp126
        │   ├───...
        │   └───ssp585
        └───Water
            ├───ssp126
            ├───...
            └───ssp585
```

## Related pages

- Running scenarios: `../user-guide/02-running-scenarios.md`
- Config reference: `../reference/03-config-reference.md`
- Components:
  - Services: `../reference/04-components/services.md`
  - AFTs: `../reference/04-components/afts.md`
  - Masks: `../reference/04-components/masks.md`
