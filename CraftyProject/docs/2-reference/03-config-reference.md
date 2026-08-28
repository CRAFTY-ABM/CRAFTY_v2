# Config reference (YAML)

This page documents the main configuration keys used by CRAFTY runs.

> **Rule of thumb:** keep one well-commented base config, then override only scenario/project/output via CLI.

---

## 0) How configuration is applied

CRAFTY configuration comes from two layers:

1) **YAML file** (the main config)
2) **CLI overrides** (optional, highest priority), typically:
   - `--config-file <path>`
   - `--project-dir <path>`
   - `--output-path <path>`

**Precedence (highest → lowest)**  
CLI override → YAML value → dataset default discovery under the project/scenario structure.

### 0.1 Renamed keys and backward compatibility

Canonical keys use lowercase `snake_case` and UK English. Historical keys remain accepted as deprecated aliases.
When both forms are present, the canonical key wins and CRAFTY prints a warning.

| Historical key | Canonical key |
|---|---|
| `start_End_Year` | `simulation_year_range` |
| `metaData_directory` | `metadata_directory` |
| `BASELINE_path` | `baseline_path` |
| `CAPITALS_directory` | `capitals_directory` |
| `gisPath` / `GIS_data_path` | `gis_path` |
| `landControle_directories` | `land_control_directories` |
| `Behevoir_Cells_directory` | `cell_behaviour_parameters_directory` |
| `SHOCKS_Maps_directory` | `shock_maps_directory` |
| `aft_production_directory` | `aft_production_parameters_directory` |
| `aft_behevoir_directory` | `aft_behaviour_parameters_directory` |
| `service_utility_weight_path` | `service_utility_weights_path` |
| `waitingFlag_directories_path` | `waiting_flags_path` |
| `AFT_capital_adjustments` | `aft_capital_adjustments_directory` |
| `regionalization` | `regionalisation` |
| `MostCompetitorAFTProbability` | `most_competitive_aft_probability` |
| `use_AFTs_categories_GiveIn` | `use_category_based_give_in` |
| `use_neighbor_priority` | `use_neighbour_priority` |
| `neighbor_priority_probability` | `neighbour_priority_probability` |
| `neighbor_radius` | `neighbour_radius` |
| `participating_cells_percentage` | `participating_cell_fraction` |
| `land_abandonment_percentage` | `land_abandonment_fraction` |
| `takeOverUnmanageCells_percentage` | `unmanaged_cell_takeover_fraction` |
| `Output_path` | `output_path` |
| `generate_charts_plots_PNG` | `generate_chart_plots_png` |
| `generate_map_PAs_forced` | `generate_forced_mask_outputs` |
| `export_LOGGER` | `export_logger` |
| `LOGGER_info` | `logger_info` |
| `LOGGER_warn` | `logger_warn` |
| `LOGGER_trace` | `logger_trace` |
| `printRegionalModelRunnerMeasures` | `print_regional_model_runner_measures` |
| `printAbstractModelRunnerMeasures` | `print_abstract_model_runner_measures` |
| `chartSynchronisation` | `chart_synchronisation` |
| `chartSynchronisationGap` | `chart_synchronisation_gap` |
| `mapSynchronisation` | `map_synchronisation` |
| `mapSynchronisationGap` | `map_synchronisation_gap` |
| `categories_givingInDistribution` | `category_give_in_distributions_directory` |
| `steepness_logistic_eq` | `cell_behaviour_logistic_steepness` |
| `LLM_model_name` | `llm_model_name` |
| `LLM_API_KEY` | `llm_api_key` |
| `LLM_provider` | `llm_provider` |
| `COUPLED_WITH_PLUM` | `coupled_with_plum` |
| `plumCalibPath` | `plum_calibration_path` |
| `plumOutPutPath` | `plum_output_path` |
| `services_commodities_map` | `service_commodity_mapping_path` |

Removed options are ignored with a warning when found in an older YAML file. This includes the regional tax
inputs, `mutate_on_competition_win`, `mutation_interval`, `generate_chart_plots_pdf`,
`generate_charts_plots_PDF`, and `generate_map_plots_tif`. Chart export supports PNG; map snapshots support
PNG and CSV.

---

## 1) Minimum config (to run)

At minimum you usually need:

```yaml
project_path: "C:/path/to/CRAFTY-project-dataset"
scenario: "ssp126"
generate_output_files: true
```

Everything else is optional *if your dataset follows the default scenario layout*.

---

## 2) Project and scenario selection

| Key | Type | Required | Meaning |
|---|---:|---:|---|
| `project_path` | string | ✅ | Root folder of the dataset (the “project”). |
| `scenario` | string | ✅ | Scenario name (must match scenario folder naming in the dataset). |

**Tips**
- Use quotes on Windows paths.
- Prefer forward slashes (`C:/...`) to reduce escaping issues.

---

## 3) Input path overrides (optional)

These keys let you *override* the dataset’s default scenario discovery and point to explicit files/folders.

> If you do **not** set an override, CRAFTY tries to resolve the input from the standard project/scenario structure.

### 3.1 Common override keys (from the template)

| Key | Type | Meaning (override) |
|---|---:|---|
| `metadata_directory` | string | Folder containing metadata tables (services list, capitals list, etc.). |
| `baseline_path` | string | Baseline CSV path (initial ownership/land use). |
| `capitals_directory` | string | Folder containing capital maps for the selected scenario (often year-indexed). |
| `land_control_directories` | list[string] | One or more LandUseControl / mask directories to apply (can stack multiple controls). |
| `aft_production_parameters_directory` | string | AFT production definitions directory (default production tables). |
| `aft_behaviour_parameters_directory` | string | Per-AFT give-in, give-up, and abandonment behaviour parameters. |
| `service_demands_path` | string | Demand file or demand directory for services (world or region-aware). |
| `service_utility_weights_path` | string | Service utility weights file (or directory, depending on template). |
| `capital_degradation_directory` | string | Folder with degradation/shock maps applied to capitals (often year-indexed). |
| `waiting_flags_path` | string | Path to a waiting-flags definition file (used only if coupling/waiting updater is enabled). |
| `gis_path` | string | Optional GIS attributes path (regions, auxiliary spatial fields) if your dataset separates it. |
| `category_give_in_distributions_directory` | string | Optional category-to-category give-in mean and standard-deviation matrices. |
| `cell_behaviour_parameters_directory` | string | Optional year-specific, per-cell behaviour parameter files. After the first successful load, a missing later year retains the latest available parameters. |

### 3.2 `service_demands_path` resolution logic (important)

The service demand loader supports flexible inputs:

- If `service_demands_path` points to a **file** → that file is used directly.
- If it points to a **directory** → the loader searches inside it for region-specific demand files
  (matching the region name by token).
- If not provided → CRAFTY falls back to the default scenario folder structure and searches under the
  scenario’s demand directory.

This is the usual mechanism for **regional** demand discovery.

---

## 4) Regionalisation and initialisation

| Key | Type | Meaning |
|---|---:|---|
| `regionalisation` | boolean | If `true`, build multiple regions (requires region identifiers + region-aware inputs such as demands). If `false`, run as one “world” region. |
| `initial_demand_supply_equilibrium` | boolean | If `true`, attempts to start from a balanced initial supply–demand state at tick 0 (calibration-style initialisation). |

> If you enable `regionalisation` but only see “world” behaviour, check logs for missing regional demands/region IDs.

---

## 6) Core mechanisms (behaviour and evolution)

| Key | Type | Meaning |
|---|---:|---|
| `use_abandonment_threshold` | boolean | Enables abandonment decisions based on threshold logic. |
| `use_category_based_give_in` | boolean | Use category-to-category give-in distributions when valid category metadata and both matrices are available. |
| `use_cell_behaviour_model` | boolean | Use the cell-level logistic behaviour model when category-based give-in and cell parameters are available. |
| `cell_behaviour_logistic_steepness` | number | Steepness of the cell-level logistic give-in equation. |

---

## 7) Neighbour effects

Neighbour effects reduce the competitor search space by prioritising AFTs present in nearby cells.

| Key | Type | Meaning |
|---|---:|---|
| `use_neighbour_priority` | boolean | If `true`, the model can derive a candidate competitor set from an extended Moore neighbourhood but still use random . |
| [`neighbour_radius`](../1-user-guide/neighbour_radius-sensitivities.md) | integer | Moore-neighbourhood radius (in cells) used when neighbour effects are enabled. See the linked SSP370 sensitivity results for radii one to five. |
| [`neighbour_priority_probability`](../1-user-guide/neighbour_priority_probability-sensitivities.md) | number | Probability of applying neighbour priority logic. See the linked SSP370 sensitivity results for a fixed radius of two cells. |

---

## 8) Competition, seeding, and rates

### 8.1 Cell-selection strategy and reproducibility

| Key | Type | Meaning |
|---|---:|---|
| `cell_selection` | string | Selection mode: `rank` chooses the lowest-utility cells; `random` chooses a deterministic pseudo-random subset. |
| `random_seed` | integer | Fixed run seed used for random selection, ranking tie-breaks, and other stochastic model decisions. |

Examples:

```yaml
# Ranking-based selection
cell_selection: rank
random_seed: 1

# Reproducible pseudo-random selection
cell_selection: random
random_seed: 1234
```

The former overloaded `seedID` key is deprecated. A legacy value of `rank` is migrated to
`cell_selection: rank`; a numeric value is migrated to `cell_selection: random` plus the corresponding
`random_seed`. Seed-file paths are no longer supported.

### 8.2 Process rates and knobs

| Key | Type | Range | Meaning |
|---|---:|---:|---|
| `land_abandonment_fraction` | number | 0..1 | Max fraction of cells that can abandon land per tick (year). |
| `participating_cell_fraction` | number | 0..1 | Fraction of cells participating in the competitiveness/competition process per tick. |
| `unmanaged_cell_takeover_fraction` | number | 0..1 | Fraction of unmanaged/abandoned cells considered for take-over. |
| [`most_competitive_aft_probability`](../1-user-guide/most_competitive_aft_probability-sensitivities.md) | number | 0..1 | Probability of choosing the best-performing competitor (the highest utility); otherwise a random competitor is tested. See the linked SSP370 sensitivity results. |
| [`marginal_utility_calculations_per_tick`](../1-user-guide/marginal_utility_calculations_per_tick-sensitivities.md) | integer | ≥1 | How many times marginal utility is recalculated per tick; see the linked SSP126 sensitivity results. |

---

## 9) Outputs and plotting

### 9.1 Output folder behaviour

| Key | Type | Meaning |
|---|---:|---|
| `generate_output_files` | boolean | Enable writing output CSV tables. |
| `generate_land_fragmentation_output` | boolean | Write annual AFT adjacency and connected-patch metrics to `<scenario>-land-fragmentation.csv`. |
| `output_folder_name` | string | If empty, use a timestamped folder. |

### 9.2 Charts / plots

| Key | Type | Meaning |
|---|---:|---|
| `generate_chart_plots_png` | boolean | Export charts as PNG (if chart exporters are enabled). |

### 9.3 Map exports

| Key | Type | Meaning |
|---|---:|---|
| `generate_map_output_files` | boolean | Enable exporting cell-level “map snapshots” (large outputs). |
| `map_output_years` | list[int] | Export maps only for these exact years. |

Some builds also support:
- `map_output_frequency: N` (export every N years)

> Recommendation: keep maps **off by default** for HPC sweeps; enable only for selected runs/years.

### 9.4 Change tracking (debug / calibration)

| Key | Type | Meaning |
|---|---:|---|
| `track_changes` | boolean | Enables extra diagnostic exports (e.g., supply composition by AFT) when output files are enabled. |

---

## 10) Logging and diagnostics

CRAFTY supports optional logging controls via configuration flags.

| Key | Type | Meaning |
|---|---:|---|
| `export_logger` | boolean | Enable file logging export (if supported by your build and logging setup). |
| `logger_info` | boolean | Enable config-driven `info` logs via the CustomLogger wrapper. |
| `logger_warn` | boolean | Enable `warn` logs via CustomLogger. |
| `logger_trace` | boolean | Enable `trace` logs via CustomLogger. |
| `print_abstract_model_runner_measures` | boolean | Print timing/performance measures from the abstract runner. |
| `print_regional_model_runner_measures` | boolean | Print timing/performance measures from the regional runner. |

---

## 11) Worked examples

### 11.1 Minimal “use dataset defaults”
```yaml
project_path: "/data/CRAFTY/CRAFTY-EU-1km_upscaled"
scenario: "ssp126"

regionalisation: false
cell_selection: rank
random_seed: 1

generate_output_files: true
generate_map_output_files: false
```

### 11.2 Override only demands (keep everything else default)
```yaml
project_path: "/data/CRAFTY/CRAFTY-EU-1km_upscaled"
scenario: "ssp126"

service_demands_path: "/data/custom-demands/ssp126"
generate_output_files: true
```

### 11.3 Publication run (selected map years)
```yaml
project_path: "/data/CRAFTY/CRAFTY-EU-1km_upscaled"
scenario: "ssp126"

generate_output_files: true
generate_map_output_files: true
map_output_years: [2020, 2030, 2050, 2100]
```

---

## Related pages

- Running scenarios + path resolution: `../user-guide/02-running-scenarios.md`
- Common workflows (HPC/sweeps): `../user-guide/03-common-workflows.md`
- Outputs: `../user-guide/04-outputs.md`
- Architecture: `01-architecture.md`
- Data model: `02-data-model.md`
