# Outputs (what CRAFTY writes)

This page documents **outputs** produced by CRAFTY and how they relate to configuration and run structure.

It complements:
- `../../user-guide/04-outputs.md` (user-facing overview)
- `../03-config-reference.md` (output-related config keys)

---

## 0) Output philosophy

CRAFTY outputs are designed to support:

- **reproducibility**: each run has a self-contained folder (config + logs + outputs)
- **analysis at multiple scales**:
  - global (world)
  - per-region (if `regionalization: true`)
  - per-AFT (land-use composition / service composition)
- **lightweight defaults** for batch runs
- optional heavy exports (maps) for visual inspection and publications

---

## 1) Output folder structure

A typical output path looks like:

```text
<project_path>/
  output/
    <scenario>/
      <run_id>/
        config.txt
        run.log
        ...
        tables/
        maps/
        plots/
```

Where:
- `<scenario>` comes from `scenario: ...`
- `<run_id>` is either:
  - `output_folder_name` (if set), or
  - an automatic timestamp folder (recommended for batch runs)

### 1.1 Key config controls
```yaml
generate_output_files: true
output_folder_name: ""          # empty → timestamped folder
```

---

## 2) Output families

CRAFTY outputs can be grouped into 4 main families:

1) **Core tables (CSV)** — always the most important outputs  
2) **Maps (cell-level snapshots)** — heavy, optional  
3) **Plots (PNG/PDF)** — optional (depends on build/exporters)  
4) **Diagnostics/logs** — always useful, lightweight  

---

## 3) Core tables (CSV)

Core tables are designed for post-processing in R/Python and for comparison runs.

### 3.1 Demand vs supply time series
Typically includes, per year (and optionally per region):
- demand per service
- realised supply per service

This is the first table you check to confirm the model is responding to scenario drivers.

### 3.2 Land-use / ownership area time series
Typical forms:
- area (number of cells × cell area) per AFT per year

This is the primary “land-use change” output.

### 3.3 Service composition by AFT (optional tracking)
If tracking is enabled (e.g., `track_changes: true`), outputs can include:
- service supply contribution by each AFT
- helpful to understand *why* a service target is met (or not)

### 3.4 Event counters / process summaries
Common diagnostic exports:
- number of competition wins per year
- number of abandonments per year
- number of take-over events per year
- number of masked cells (if tracked)

These are useful for calibration and stability checking.

---

## 4) Region-specific outputs (when regionalisation is enabled)

When:
```yaml
regionalization: true
```
outputs are often written in one of two styles:

### One folder per region
```text
tables/
  world/
  region_<nameA>/
  region_<nameB>/
```

---

## 5) Map outputs (cell-level snapshots)

Map exports are usually controlled by:

```yaml
generate_map_output_files: false
map_output_years: [2020, 2030, 2050]
```

Maps are large because they export cell-level values. Typical exported map types:

### 5.1 Ownership / land-use maps
- for each selected year: `owner(x,y)`

### 5.2 Capital maps
- capital value per cell for selected years
- useful for debugging degradation/shocks and scenario capital updates

### 5.3 Diagnostic maps (project-specific)
Some builds export:
- utility maps
- productivity maps

**Performance note**  
Map exports can dominate runtime and disk usage on large grids. Keep them off for sweeps.

---

## 6) Plot outputs (PNG/PDF)

If enabled, CRAFTY can export plots for quick inspection:

```yaml
generate_charts_plots_PNG: true
generate_charts_plots_PDF: false
```

Common plots:
- service demand vs supply curves
- time series of AFT areas
- histograms of capital distributions (GUI-derived exports in some setups)

Plot export availability depends on your build (headless chart exporters vs GUI-only plots).

---

## 7) Logs and run metadata

Even when outputs are disabled, logs are essential.

### 7.1 What to store in each run folder
Recommended best practice:
- crafty will copy autoamatically the used `config.yaml` into the run folder
- store the log file (or SLURM output) alongside results

This makes runs reproducible and allows debugging months later.

### 7.2 Timing/performance measures
If enabled in config for debuging:
```yaml
printAbstractModelRunnerMeasures: true
printRegionalModelRunnerMeasures: true
```
you can get:
- load time
- per-updater timing
- total runtime

---

## 8) Output configuration (quick reference)

Minimal outputs for batch experiments:
```yaml
generate_output_files: true
generate_map_output_files: false
generate_charts_plots_PNG: false
generate_charts_plots_PDF: false
track_changes: false
```

Debug outputs for a single run:
```yaml
generate_output_files: true
track_changes: true
generate_map_output_files: true
map_output_years: [2020, 2030]
```

Publication outputs:
```yaml
generate_output_files: true
generate_map_output_files: true
map_output_years: [2020, 2050, 2100]
generate_charts_plots_PNG: true
```

---

## 9) Common issues and fixes

### 9.1 “Output folder is empty”
- `generate_output_files: false`
- output path not writable
- you are looking at a different run_id than you expect

### 9.2 “Maps are missing”
- `generate_map_output_files: false`
- current year not in `map_output_years`
- map exporters disabled or dependencies missing.

### 9.3 “Outputs differ between runs”
- random seeding not fixed (`seedID` differs)
- multi-threading changes tie-breaking order
- input files changed (most common)

Fix:
- store config + inputs with each run
- use deterministic seeding for comparison runs

---

## Related pages

- User outputs overview: `../../user-guide/04-outputs.md`
- Config reference: `../03-config-reference.md`
- Services component: `services.md`
- AFTs component: `afts.md`
- Updaters: `updaters.md`
