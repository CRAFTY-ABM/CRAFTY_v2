# Outputs

This page explains:
- where CRAFTY writes outputs
- what files you should expect
- how to control output volume (especially map outputs)
- how to use outputs in the GUI and in analysis scripts

The exact set of outputs depends on your configuration (and which modules are enabled), but the structure below is the
recommended mental model for CRAFTY projects.

---

## 0) Where outputs go

By default, outputs are written under the project folder in a structure like:

```text
<project_path>/
  output/
    <scenario>/
      <run_id>/                        # output_folder_name or timestamp
        config_used.txt                #  copied by crafty
        run.log                        # stdout/stderr if you redirect logs
        ...
```

You can override the output root at runtime:
```bash
java -jar crafty-core-v2-<version>.jar \
  --config-file config.yaml \
  --output-path /scratch/$USER/crafty-out
```

---

## 1) Output switches (what to enable)

Most projects use at least:
```yaml
generate_output_files: true
```

Map exports can be large; enable only when needed:
```yaml
generate_map_output_files: false
```

Some configs also allow per-run naming:
```yaml
output_folder_name: "ssp126_rank_ab03_comp05"
```

If `output_folder_name` is empty, CRAFTY typically creates a timestamped run folder.

---

## 2) What CRAFTY writes (high-level categories)

CRAFTY outputs usually fall into four categories:

1) **Run metadata & logs**  
2) **Aggregated time series (CSV)**  
3) **Per-region outputs (optional)**  
4) **Spatial outputs / maps (optional)**  

---

## 3) Run metadata & logs

Recommended files to keep per run:
- `config_used.yaml` (exact config used for this run)
- `run-info.txt` (scenario, jar version, git commit, machine, CLI args)
- `run.log` (console output)

If you run on HPC, it’s common to store:
- SLURM `.out` / `.err`
- resource usage reports (if available)

These files are essential for reproducibility.

---

## 4) Aggregated time series (CSV)

These are the core outputs most analyses start with.

Typical content (depends on enabled modules):
- **AFT composition** over time (area per AFT, land-use transitions)
- **Service supply and demand** time series
- **Marginal utilities** 
- **Landscape fragmentation and AFT clustering** when `generate_land_fragmentation_output: true`
- **Summary diagnostics** (land use event counts, competitiveness outcomes, etc.)

### 4.1 File naming
Projects often store these in a consistent in outputFiles subfolder, e.g.:
```text
.../scenario-Cell-year.csv
.../ssp126-landEventCounter.csv
.../ssp126Total-AggregateAFTComposition.csv
.../ssp126Total-AggregateDemandServicesEquilibrium.csv
.../ssp126Total-AggregateServiceDemand.csv
.../ssp126-AverageUtilities.csv

```

## 5) Per-region outputs (optional)

If `regionalisation: true` and per-region outputs are enabled, you typically get:

```text
<run_id>/
  regions/
    <region_id_1>/
      ... same style CSV outputs for that region ...
    <region_id_2>/
      ...
```

Region outputs are useful for:
- validating the regional demand inputs
- comparing regional land-use transitions
- diagnosing “one region dominates” behaviour

> If you expected region outputs but only see “world” outputs, check:
> - regionalisation settings
> - whether regional service demands exist and were matched correctly

---

## 6) Spatial outputs / maps (optional but important)

Maps are usually the biggest disk usage. Enable them intentionally.

### 6.1 What map outputs represent
Common map layers:
- land-use / owner (AFT) map
- selected capital layers (by year)
- derived layers (utility, productivity, supply, etc.)

### 6.2 Controlling map volume
Common controls in YAML (names vary by template):
```yaml
generate_map_output_files: true

# Example controls:
map_output_years: [2000, 2010, 2020, 2030]  # only these years
# or
map_output_frequency: 10                    # every N years

# Optional: limit to selected map types
map_output_types: ["AFT", "supply"]
```

Practical advice:
- for debugging: export **only a few years**
- for publications: export specific milestone years
- for HPC sweeps: keep maps off unless you truly need them

### 6.3 Typical map output format
maps will be exported as:
- **CSV** (cell table: x,y,value)
- **PNG** (quick visualisation)

---

## 7) Outputs in the GUI (visualising runs)

A common workflow:
1) run headless on HPC (fast)
2) copy the run folder to your workstation
3) open the GUI and point it to the output directory

Recommended:
- keep run folders self-contained (config + logs + outputs)
- avoid renaming internal files after the run (the GUI may expect conventions)

---

## 8) Performance + storage tips

- Write outputs to **scratch** during the run, then copy final results to long-term storage.
- Keep **CSV time series** always; they are small and essential.
- Turn off map outputs by default; enable them for selected runs.
- If you need maps for many runs, export fewer years and fewer layers.

---

## 9) Quick checklist (did my run produce the expected outputs?)

- [ ] Output folder created under `<project_path>/output/<scenario>/...` (or under `--output-path`)
- [ ] `generate_output_files: true` enabled
- [ ] CSV time series exist and include expected years
- [ ] If regionalisation enabled: region folders exist (or logs explain fallback)
- [ ] If map outputs enabled: only expected years/layers exported

---

## Next pages

- Troubleshooting: [`05-troubleshooting.md`](05-troubleshooting.md)
- Common workflows (HPC, sweeps, reproducibility): [`03-common-workflows.md`](03-common-workflows.md)

Appendices:
- Default scenario layout: `docs/appendices/default-scenario-layout.md`
- CSV schemas: `docs/appendices/file-formats.md`
