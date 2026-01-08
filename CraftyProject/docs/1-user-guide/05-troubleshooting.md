# Troubleshooting

This page lists the most common problems when running CRAFTY and the fastest ways to diagnose them.
When in doubt: **read the logs carefully**—most issues are missing inputs, wrong paths, or scenario discovery problems.

---

## 0) Before anything: collect the basics

When reporting an issue (or debugging your own runs), always note:

- How you ran CRAFTY:
  - Windows `.exe`, `.jar` (GUI/headless), or IDE
- The YAML config used
- CLI arguments used (especially overrides)
- The output folder and log file (`run.log` / SLURM output)

**Minimum useful info**
- `project_path`
- `scenario`
- whether `regionalization` is on
- whether outputs/maps are enabled

---

## 1) “File not found” or “Scenario not found”

### Symptoms
- Run stops at startup
- Logs show missing baseline/capitals/demand files
- Scenario discovery returns empty paths

### Quick checks
1. Verify `project_path` exists and is readable.
2. Verify the scenario name exists in your dataset (case-sensitive in general).
3. If using overrides, confirm overridden paths exist (baseline, capitals directory, demands).

### Common causes
- Typo in `scenario`
- Using Windows backslashes without quoting correctly
- Moving the dataset to a new machine but not updating `project_path`

**Fix**
- Prefer forward slashes in YAML:
  ```yaml
  project_path: "C:/CRAFTY_DATA/CRAFTY-EU-1km_upscaled"
  ```
- Use CLI override for portability:
  ```bash
  --project-dir /path/to/project
  ```

---

## 2) The model starts but produces no outputs

### Symptoms
- The run completes (or appears to run) but output folder is empty
- Only logs exist

### Quick checks
- Ensure:
  ```yaml
  generate_output_files: true
  ```
- Ensure the output location is what you expect:
  - default under `<project_path>/output/<scenario>/...`
  - or overridden by `--output-path`

### Common causes
- `generate_output_files: false`
- output path points somewhere you don’t have write permission
- output folder name clashes with an existing run and is overwritten

**Fix**
- Use a unique output folder per run:
  ```yaml
  output_folder_name: ""
  ```
- Or set a run id:
  ```yaml
  output_folder_name: "ssp126_test01"
  ```

---

## 3) Regionalisation expected, but run behaves like “world” only

### Symptoms
- You set `regionalization: true`, but outputs show only one region
- No per-region folders in output
- Logs mention missing regional demand/weights/taxes

### Why it happens
Regionalisation requires region information **and** region-aware service inputs (especially demand files).
If inputs are missing or cannot be matched, CRAFTY may fall back to a single region.

### Fix checklist
- Confirm your dataset provides region definitions
- Confirm regional service demand files exist
- Confirm naming/token conventions match your region identifiers
- Check logs for warnings about missing region inputs

---

## 4) “Too much abandonment” or “land-use changes too fast”

### Symptoms
- Large contiguous abandoned areas appear
- Land-use changes are unrealistically fast across years
- Outputs show high abandonment/competition counts

### Typical causes
- Percentages/rates are too high for your resolution
- Using ranking-based selection (`seedID: rank`) with large participating fractions
- Degradation or mask logic increases pressure on certain areas

### What to try
1. Reduce change rates:
   ```yaml
   land_abandonment_percentage: 0.01
   participating_cells_percentage: 0.01
   ```
2. Compare seeding strategies:
   ```yaml
   seedID: rank
   # vs
   seedID: 1234
   ```
3. Run a short window (5–10 years) to test sensitivity quickly.

---

## 5) “Degradation/shocks make no difference” (or too much difference)

### Symptoms
- With and without degradation inputs, outputs look identical
- Or degradation causes extreme/unrealistic collapse

### Likely causes
- Degradation files are not being discovered (wrong folder or naming)
- Degradation is applied but scaling/intensity is wrong
- Degradation affects capitals that are not actually used by key AFTs/services

### Debug workflow
- Check the log information to ensure that the files were found and read correctly.
- Run two runs identical except for degradation enabled/disabled
- Export a few map years for the affected capital layers
- Verify in outputs/maps that the capital values actually change

---

## 6) Java memory errors (OutOfMemoryError) or severe slowdown

### Symptoms
- `java.lang.OutOfMemoryError`
- Runs slow down dramatically near the end of loading or late in the run
- Large map exports overwhelm disk/memory

### Fixes
1. Increase JVM memory:
   ```bash
   java -Xms2G -Xmx16G -jar crafty-core-headless-<version>.jar --config-file config.yaml
   ```
2. Disable map exports while debugging:
   ```yaml
   generate_map_output_files: false
   ```
3. Reduce output frequency / years (if maps are needed):
   ```yaml
   map_output_years: [2000, 2010, 2020]
   ```
4. On HPC: request enough memory in SLURM:
   ```bash
   #SBATCH --mem=32G
   ```

---

## 7) GUI starts but crashes / blank window

### Symptoms
- GUI jar opens then closes immediately
- “JavaFX runtime components are missing” (common)
- Missing module errors in IDE runs

### Causes
- JavaFX not bundled
- Wrong Java version
- Missing `--module-path` / `--add-modules` in IDE runs

### Fix
- Confirm Java version:
  ```bash
  java -version
  ```
- If running from IDE, add JavaFX VM args:
  ```
  --module-path "<path-to-javafx-sdk>/lib" --add-modules javafx.controls,javafx.fxml
  ```

---

## 8) Permission issues (HPC / shared storage)

### Symptoms
- “Permission denied” when writing output
- Can read inputs but cannot create directories

### Fix
- Write to scratch first (`--output-path /scratch/...`)
- Check directory permissions and group ownership
- Avoid writing directly into shared reference datasets

---

## 9) If you need help: what to include in a bug report

Please attach:
- your `config.yaml`
- CLI args used
- the first ~50 lines and last ~200 lines of the log
- the exact error message/stack trace
- the expected behaviour vs observed behaviour
- dataset/scenario name

---

## Next pages

- Outputs: [`04-outputs.md`](04-outputs.md)
- Common workflows (HPC & sweeps): [`03-common-workflows.md`](03-common-workflows.md)
- Scenarios: [`02-running-scenarios.md`](02-running-scenarios.md)

