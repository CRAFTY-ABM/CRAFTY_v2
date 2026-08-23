# Services

This page documents the **Services** component: how services are defined, where service inputs live in the dataset,
how CRAFTY loads them, and how they influence allocation (competition, abandonment, take-over).

Services are the model “currency”: AFTs produce **service supply** from **capitals**, and the model compares that
supply to exogenous **demand** (plus weights and policy signals) to compute marginal utility and competitiveness.

---

## 0) What a “Service” is in CRAFTY

In the code, a `Service` stores *region-specific* time-varying drivers used during allocation:

- **Demand** time series: `year → demand`
- **Utility weight** time series: `year → weight` (scales marginal utility by service importance)
- **Taxes/Subsidies** time series: `year → value` (policy incentive term)
- **Calibration factor**: `calibration_Factor` used when initial supply–demand equilibrium is enabled

Services also have optional **metadata**:
- `Category` (for reporting/plotting)
- `Penalise_Oversupply` flag (optional behaviour used by downstream calculations)

---

## 1) How services exist in memory (world + regions)

CRAFTY maintains:

- a **global registry** of service names (authoritative list)
- one `Service` object per **region** and per **service name**
- a **world-aggregate** service container used for reporting/plotting

### 1.1 ServiceSet (global registry)
`ServiceSet` is the central place that:
- reads the services metadata CSV (canonical service list)
- creates `Service` instances for every region
- creates a “world” service container
- checks whether regional demand files exist for all regions (if regionalisation is enabled)
- triggers the “service update pipeline” (demand → weights → taxes/subsidies)

### 1.2 World vs Region meaning
- **Regional services** drive allocation when `regionalisation: true`
- **World services** are aggregates, mainly for plots and diagnostics

Aggregation rules (world container):
- demand is **summed** across regions
- weights and taxes/subsidies are typically aggregated as **simple averages across regions**
  (implementation uses number of regions as the divisor)

---

## 2) Input files that define services

Services are driven by **four** input families:

1) **Service metadata** (required)  
2) **Demands** (required for meaningful runs; and required per region when `regionalisation: true`)
3) **Utility weights** (optional; defaults exist)  
4) **Service taxes/subsidies** (optional; defaults exist)

### 2.1 Service metadata (required)

This CSV defines the canonical service list.

**Required columns**
- `Label` (preferred) *or* `Name` (fallback)

**Optional columns**
- `Category`
- `Penalise_Oversupply` (boolean-like values {0,1})

CRAFTY will:
- build `servicesList` from `Label` if present, otherwise from `Name`
- store `Category` and `Penalise_Oversupply` if provided

---

## 3) Service demands

### 3.1 What demands represent
Demand is an exogenous time series per service (and typically per region) representing the desired
consumption/target level. Demand drives marginal utility: if demand is high and supply is low,
the service becomes “valuable” and AFTs producing it become more competitive.

### 3.2 CSV shape expected by the loader
Demand files are read as **one column per service** (column header = service label).

A typical demand file conceptually looks like:

```text
Year,Food,Timber,Carbon
2020,  100,  20,   80
2021,  105,  22,   82
...
```

The loader interprets each column as a vector of values aligned with the simulation horizon
`[startYear .. endYear]`.

**Important behaviour**
- Missing values beyond the vector length are filled with `0.0` so every year has a demand value.

### 3.3 Demand discovery and path resolution
Demand inputs are resolved using:

1) `service_demands_path` from YAML (if set)
2) otherwise: default scenario discovery under the project/scenario structure

If `service_demands_path` points to:
- a **file**: that file is used directly
- a **directory**: the loader searches inside it for region-specific files

Region-specific discovery uses a *filename token* that includes the region name. In the current template,
the search expects the scenario name, region name and should be in demand folder to appear as a semicolon-delimited token (e.g., `...;EU;...`).

---

## 4) Service utility weights

### 4.1 What weights do
Utility weights scale the contribution of each service to marginal utility / competitiveness.
They let you prioritise certain services over others without changing demand magnitudes.

### 4.2 Defaults (important)
Weights are optional. If a weight file cannot be found for a region, CRAFTY uses:
- **weight = 1.0** for all services and all years

### 4.3 Discovery logic
Resolved using:

1) `service_utility_weights_path` (if set)
2) otherwise: scenario discovery under `Service_Utility_Weights/`

If the configured path is:
- a **file**: used directly (often one file for all regions)
- a **directory**: searched for region-specific files containing a region token (e.g., `scenarioName_RegionName;`)

---

## 5) Service taxes/subsidies

### 5.1 What taxes/subsidies do
Service-level taxes/subsidies are an incentive term that can increase or decrease the competitiveness
of AFTs via the service pathway.

Conceptually:
- a **subsidy** makes producing a service more attractive
- a **tax** makes producing a service less attractive

Exact scaling depends on your runner’s policy formulation (often interacting with gaps and calibration factors).

### 5.2 Cell-level policy effects

Regional service tax files are no longer loaded. Institution policy effects are stored on individual cells and
are included only when cell-level taxes are enabled.

---

## 6) How services influence allocation

Services affect allocation through three derived constructs computed each year:

1) **Supply** per region and per service  
2) **Demand–supply gap** per region and per service  
3) **Marginal utility / utility signals** used in competition and abandonment

### 6.1 Supply (from AFT production)
At each year, each cell produces services based on:
- its owner (AFT)
- its capital values
- AFT production/sensitivity settings

Region supply is the aggregation of cell-level production in that region.

### 6.2 Demand–supply gap (tracked by ServicesUpdater)
Each year the `ServicesUpdater` snapshots:
- demand
- weight
- taxes/subsidies
- realised supply

It also computes a normalised gap:
- `gap = (demand - supply) / demand`
- safe fallback to `0` when `demand == 0`

These snapshots are stored for fast lookup and diagnostics.

### 6.3 Oversupply penalisation (optional metadata)
If a service is marked `Penalise_Oversupply = true`, downstream calculations can reduce
utility/marginal-utility signals when supply exceeds demand.

(How the penalisation is applied is runner-specific; it is controlled by the flag stored in `ServiceSet`.)

---

## 7) Calibration factor and initial equilibrium

If your config enables:
```yaml
initial_demand_supply_equilibrium: true
```

CRAFTY can compute a per-service `calibration_Factor` during initialisation to reconcile baseline supply with
initial demand. This makes services comparable even when baseline units differ.

---

## 8) Config keys related to services (quick reference)

Most commonly used keys:

```yaml
# required for all real runs
project_path: "/path/to/project"
scenario: "ssp126"

# optional path overrides
service_demands_path: ""
service_utility_weights_path: ""

# regional mode control
regionalisation: false
```

Related keys that interact with service behaviour:
```yaml
initial_demand_supply_equilibrium: true
averaged_residual_demand_per_cell: false
```

---

## 9) Practical tips

- **Start simple:** run with demands only, no weights, no taxes/subsidies.
- **Regionalisation sanity check:** confirm every region has a demand file before enabling `regionalisation: true`.
- **Keep names consistent:** service labels must match metadata and CSV headers exactly.
- **Use defaults intentionally:** weights default to 1.0 and taxes/subsidies default to 0.0 great for a baseline run.
- **Avoid “silent mismatch”:** if a CSV column name doesn’t match a service label, it is ignored.

---

## Related pages

- Data model (how services relate to cells/AFTs): `../02-data-model.md`
- Config reference (keys and overrides): `../03-config-reference.md`
- Running scenarios (path resolution rules): `../../user-guide/02-running-scenarios.md`
