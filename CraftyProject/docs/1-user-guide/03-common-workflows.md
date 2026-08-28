# Common workflows

This page is a set of practical “recipes” you’ll use again and again: batch runs, HPC/SLURM runs, reproducible
experiments, parameter sweeps, debugging tricks, and optional coupling workflows.

---

## 0) A good habit: one base config, many runs

In most projects, you want **one** well-commented YAML file (your *base config*), then vary only:
- scenario (`--scenario-name`)
- project path (`--project-dir`) when moving between machines
- output location (`--output-path`)
- (optionally) a few key parameters (seed / rates)

This keeps experiments consistent and reduces config drift.

---

## 1) Reproducible run folders (recommended structure)

Crafty automatically generates a copy of the conf.yaml file used in the run.
It contains:
- the name of the JAR file used for the run
- any comments you may add in the falg '"put any comment here for the run"'
- the logs (`stdout`/`stderr`)
- all other parameters 

**Suggested output naming**
- Encode what matters (scenario + a few knobs), not everything.
- Examples:
  - `ssp126_rank_ab03_comp05_take80`
  - `ssp245_seed1234_ab05_comp01_mapsOff`

set:
```yaml
output_folder_name: "ssp126_rank_ab03_comp05"
```
Or leave it empty to timestamp automatically:
```yaml
output_folder_name: ""
```

---

## 3) HPC / SLURM workflow (recommended)
### 3.1 SLURM job array over scenarios
This pattern is robust and easy to extend.
```bash
#!/bin/bash
#...
#SBATCH --array=0-4   # Run N independent jobs in parallel(adjust as needed), specify the number of loops N 


module purge
module load openjdk/21.0.3_9-gcc-11.3.1 


# Define constants
CRAFTY_JAR="/bg/data/luc/projects/crafty-eu-workspace/crafty-releases/crafty-core-v2-1.1.0.jar"
#change OUT_DIR_BASE to your personal direction
OUT_DIR_BASE="/bg/data/luc/projects/crafty-eu-workspace/reference-scenarios/output/"

# Define configuration files
CONFIGS=(
"/bg/data/luc/projects/crafty-eu-workspace/inputs-template/config_ref_ranking/config-ssp126_ref.yaml"
"/bg/data/luc/projects/crafty-eu-workspace/inputs-template/config_ref_ranking/config-ssp245_ref.yaml"
"/bg/data/luc/projects/crafty-eu-workspace/inputs-template/config_ref_ranking/config-ssp370_ref.yaml"
"/bg/data/luc/projects/crafty-eu-workspace/inputs-template/config_ref_ranking/config-ssp445_ref.yaml"
"/bg/data/luc/projects/crafty-eu-workspace/inputs-template/config_ref_ranking/config-ssp585_ref.yaml"
)

# Pick the config for this array index
CONFIG=${CONFIGS[$SLURM_ARRAY_TASK_ID]}
BASENAME=$(basename "$CONFIG" .yaml)
OUT_DIR="${OUT_DIR_BASE}/${BASENAME}_out"

echo "=== Running array task ${SLURM_ARRAY_TASK_ID} ==="
echo "Config file: $CONFIG"
echo "Output dir: $OUT_DIR"
echo "==============================================="

# Run CRAFTY
java -jar "$CRAFTY_JAR" -c "$CONFIG" -o "$OUT_DIR"

echo "Task ${SLURM_ARRAY_TASK_ID} completed."
```
---

Submit:
```bash
mkdir -p logs
sbatch run_crafty_array.sbatch
```

### 3.2 Replicates (array over scenario × replicate)
If you need stochastic replicates, include a replicate index and a seed:

```bash
# Example mapping:
# array 0..(N-1): decode scenario + replicate from task id
```

Tip: even with deterministic `rank` seeding, some components may still be stochastic depending on your settings.

---

## 4) Parameter sweeps (rates, flags, variants)

### 4.1 The “template config + small edits” pattern
Keep a `config.template.yaml`, then produce per-run configs by replacing a few lines, e.g.:
- `land_abandonment_fraction`
- `participating_cell_fraction`
- `cell_selection` and `random_seed`
- map output flags

**Simple sweep in Bash (example)**
```bash
for AB in 0.01 0.03 0.05; do
  for COMP in 0.01 0.05; do
    OUT=/scratch/$USER/crafty/sweep_ab${AB}_c${COMP}
    mkdir -p "$OUT"
    yq ".land_abandonment_fraction=$AB | .participating_cell_fraction=$COMP" \
      config.template.yaml > "$OUT/config.yaml"

    java -jar "$JAR" --config-file "$OUT/config.yaml" --output-path "$OUT"
  done
done
```

If you don’t want extra tooling (`yq`), keep multiple configs and only override scenario/output via CLI.

### 4.2 CRAFTY always store the used config
Whatever method you use, ** crafty will copy automatically the effective config into the output folder**.

---

## 5) Overriding inputs (common “swap one folder” recipes)

### 5.1 Use scenario defaults (starting point)
Set only:
```yaml
project_path: "/path/to/project"
scenario: "ssp126"
```
Let CRAFTY discover the rest from the dataset structure.

### 5.2 Override one specific input (e.g., capitals or baseline)
Use this when you want a controlled experiment.

Examples (keys depend on your template):
```yaml
baseline_path: "/path/to/Baseline_map.csv"
capitals_directory: "/path/to/capitals/ssp126_variantA"
```

### 5.3 Add or swap masks / land-use control
Typical uses:
- enforce protected areas
- block transitions in restricted zones
- test “urban mask” variants

```yaml
land_control_directories:
  - "/path/to/LandUseControl/UrbanMask/ssp126"
  - "/path/to/LandUseControl/ProtectedAreas/all"
```

---

## 6) Debugging workflows (fast diagnosis)

### 6.1 Run a short time window
When debugging, don’t run 100 years.

If your config supports start/end years, set them short (e.g., 5–10 years).

### 6.2 Disable heavy outputs first
Map export can be expensive. For debugging:
```yaml
generate_output_files: true
generate_map_output_files: false
```

### 6.3 Isolate suspected inputs
A common diagnostic is to run a scenario:
- **with degradation/shocks**
- and **without degradation/shocks**

If outcomes are identical, the degradation pipeline may not be applied (or the file discovery is wrong).
If outcomes differ dramatically, check if the degradation intensity/scaling is realistic.

### 6.4 Compare “rank” vs “random” seeding
If you suspect selection bias, compare:
- `cell_selection: rank` with a fixed `random_seed` (systematic)
- `cell_selection: random` with the same `random_seed` (reproducible random)

Keep everything else constant and compare outputs.

---

## 7) Regionalisation workflows

### 7.1 Start in single-region mode
```yaml
regionalisation: false
```

### 7.2 Then enable regionalisation
```yaml
regionalisation: true
```

Make sure region-level service inputs exist (especially demands).  
If the model falls back to one “world” region, check logs and region-file naming.

---

## 8) Coupling workflow (optional): waiting flags

CRAFTY can optionally “pause” at configured years and wait until an external process creates a flag file/folder.
This is useful for loose coupling (e.g., CRAFTY ↔ another model pipeline).

Typical pattern:
1) run until year Y
2) write outputs
3) wait until a “flag” appears (meaning the other model finished and wrote new inputs)
4) continue

Practical advice:
- Use this only when you truly need stepwise coupling.
- In HPC pipelines, ensure your jobs have enough wall-time or implement a clean restart strategy.
---

## 9) Move HPC outputs to the GUI (visualise results locally)

A common workflow is:
1) run headless on HPC (fast, many runs)
2) copy results to your workstation
3) open the GUI and point it to the output folder

Tips:
- keep outputs organised by scenario + run-id
- compress old runs (`zip`/`tar.gz`) if needed
- if your GUI expects a particular output layout, keep that consistent across runs

---

## 10) Storage hygiene (recommended)

When you run many experiments, storage becomes the bottleneck—not CPU.

Suggestions:
- write outputs to fast scratch during the run
- copy only final outputs to long-term storage
- keep logs + config snapshots; delete huge intermediate maps if you don’t need them
- document what each run is for (a `README.txt` in each run folder works)

---

## Next pages

- Outputs and map export: [`04-outputs.md`](04-outputs.md)
- Troubleshooting: [`05-troubleshooting.md`](05-troubleshooting.md)

Appendices:
- Default scenario layout: `docs/appendices/default-scenario-layout.md`
- CSV schemas: `docs/appendices/file-formats.md`
