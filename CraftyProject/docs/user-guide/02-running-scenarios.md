# Running scenarios

This page explains what a **scenario** is in CRAFTY, how to select one, and how to control which inputs are used
(via scenario defaults vs. explicit YAML overrides). It also covers **regionalisation**, **seeding**, and how to
run many scenarios in batch/HPC workflows.

---

## 0) Mental model: project vs scenario

A CRAFTY run always needs:

- **`project_path`**: a project data directory (contains metadata + scenario inputs)
- **`scenario`**: which scenario to run (e.g., `ssp1rcp26`, `ssp2rcp45`, …)

Think of it as:

- **Project** = “the dataset”
- **Scenario** = “one configuration of time-varying inputs inside that dataset”

A scenario typically provides (depending on your dataset):
- time-varying **capitals**
- regional **service demand** trajectories
- optional **service utility weights** and **taxes/subsidies**
- optional **land-use control / masks / restrictions**
- optional **degradation / shocks**

---

## 1) Selecting a scenario in YAML

```yaml
project_path: "/path/to/CRAFTY-EU-1km_upscaled"
scenario: "ssp126"
```
---

## 2) Input resolution: scenario defaults vs YAML/CLI overrides

CRAFTY resolves inputs using a **precedence order**. In practice:

1) **Explicit YAML paths** (highest priority)  
3) **Scenario folder discovery** (default)  
4) **Fallback defaults** (for some optional inputs)

> Rule of thumb: if you set a path in YAML, CRAFTY uses it; otherwise it tries to find the expected file(s)
> inside the scenario structure.



### YAML explicit path overrides (most common)
Depending on your dataset/config template, you may override things like:
- metadata directory
- baseline map path
- capitals directory
- land-use control directories
- institutions / policy directory
- service inputs (demands/weights/taxes)

Example (typical pattern; keys depend on your config template):
```yaml
# Optional overrides (examples)
metaData_directory: "/path/to/csv"
BASELINE_path: "/path/to/worlds/Baseline_map.csv"
CAPITALS_directory: "/path/to/worlds/capitals/ssp126"

institutions_directory: "/path/to/institutions"
landControle_directories:
  - "/path/to/worlds/LandUseControl/Water/ssp126"
```

---

## 3) Service inputs are region-aware (demands, weights, taxes/subsidies)

CRAFTY supports **region-specific** service inputs. For each of the following, you can provide either:
- a **single file** (used directly), or
- a **directory** containing one file per region, or
- nothing → CRAFTY falls back to scenario discovery.

### 3.1 Demands (`service_demands_path`)
```yaml
service_demands_path: "/path/to/demand"   # directory OR file
```

Resolution logic (conceptual):
- If `service_demands_path` is a file → use it.
- If it is a directory → pick a region-specific file (by name/token).
- Else → discover under the scenario’s `demand/` folder.

**CSV expectation (high level):**
- columns = service names
- values = yearly demand vector (aligned to model years)

### 3.2 Utility weights (`service_utility_weight_path`)
```yaml
service_utility_weight_path: "/path/to/Service_Utility_Weights"   # directory OR file
```

If no weights file is found for a region:
- CRAFTY assigns a default constant weight (typically `1.0`) so the model remains runnable.

### 3.3 Taxes/subsidies (`services_taxes_subsidies_path`)
```yaml
services_taxes_subsidies_path: "/path/to/services_taxes_subsidies"  # directory OR file
```

If no taxes/subsidies file is found for a region:
- CRAFTY assigns a default constant value (typically `0.0`) so the model remains runnable.

> The exact region-file naming/token conventions depend on your dataset conventions (often a region token embedded in the
> filename). If regionalisation is enabled and CRAFTY can’t match files to regions, it will log warnings and may fall back.

---

## 4) Regionalisation (multi-region vs “world” run)

### 4.1 Enable / disable
```yaml
regionalization: true
```

- If `regionalization: false`, CRAFTY runs everything as one region (“world”).
- If `regionalization: true`, CRAFTY attempts to build multiple regions (based on GIS/region info in the dataset)
  and expects regional service inputs to be available.

### 4.2 Automatic fallback to single region
If regionalisation is enabled but required regional service inputs are missing, CRAFTY can automatically fall back to
a single-region configuration so the model remains runnable.

**How to verify what happened**
- Check logs: you should see whether multiple regions were created or whether it fell back to a single “world” region.
- Outputs: if per-region outputs are enabled, you’ll see one folder per region only when regionalisation is active.

---

## 5) Seeding & participation (important for scenario behaviour)

Many CRAFTY processes operate only on a subset of cells per tick (e.g., competition, abandonment). Two key ideas:

- **Which cells participate** (percentages)
- **How the participating “seed” cells are selected** (seed strategy)

### 5.1 Seed selection strategy (`seedID`)
Common patterns used in your configs:

```yaml
seedID: rank
```

Typical meanings:
- `rank` → deterministic / ranking-based selection (e.g., lowest-utility cells)
- integer (e.g., `1234`) → random seed with fixed ID (reproducible randomness)
- file path / directory → read explicit seed selection from file(s)

### 5.2 Participation and change rates
Examples:
```yaml
land_abandonment_percentage: 0.03
participating_cells_percentage: 0.05
takeOverUnmanageCells_percentage: 0.8
```

Practical advice:
- Start conservative, then tune.
- These parameters represent Crafty's sensitivity to changes in demands. Thus, if the values are high, supply will be more sensitive to changes in demand, and you will likely see spikes in the supply graphs.
- If results show “too much” abandonment or overly fast change, these rates are the first parameters to revisit.
- When comparing scenarios, keep these settings constant unless you are explicitly testing behaviour sensitivity.

---



---

## 7) Common problems (and quick fixes)

### “Scenario not found” / no inputs discovered
- Check spelling/case of `scenario`
- Confirm the scenario exists in your dataset structure
- If you are using overrides, confirm the override paths exist

### Regionalisation expected but you only got “world”
- Make sure regional service demand files exist and can be matched to regions
- Check logs for warnings about missing region inputs

### Outputs overwritten / mixed between runs
- Use a unique `output_path` (CLI) per run, or
- set `output_folder_name` per run, or
- leave `output_folder_name: ""` to timestamp automatically

### Windows path issues
- Prefer forward slashes in YAML: `C:/CRAFTY_DATA/...`
- Keep paths in quotes
### Recommended naming for outputs
To avoid confusion:
- set `output_folder_name` to something meaningful (or leave empty to auto-timestamp)
- encode scenario + key parameters into folder names for experiment tracking

Example:
```yaml
output_folder_name: "rank_seed-ab03-comp05"
```
---

## Next pages

- Recipes and automation patterns: [`03-common-workflows.md`](03-common-workflows.md)
- Outputs and map export: [`04-outputs.md`](04-outputs.md)
- Troubleshooting: [`05-troubleshooting.md`](05-troubleshooting.md)

Appendices:
- Default scenario layout: `docs/appendices/default-scenario-layout.md`
- CSV schemas: `docs/appendices/file-formats.md`
