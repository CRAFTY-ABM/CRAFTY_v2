# CRAFTY R Script Runner

The `r_script_runner` feature allows CRAFTY to call one or more external R scripts during a simulation run.

The main use case is coupling CRAFTY with an external R workflow, for example to calculate external variables such as MSA, update demand files, or produce intermediate outputs needed by the next CRAFTY iteration.

R scripts are checked and executed at the start of each CRAFTY iteration/year. If the current year matches the script configuration, CRAFTY runs the R script, waits until it finishes, and then continues the simulation.

---

## Basic YAML structure

Add the following block to the CRAFTY main YAML configuration:
```yaml
r_script_runner:
  config_path: 'path\to\R_scripts_config.yaml'
        mode: "annual"
```
*R_scripts_config.yaml* file:

```yaml
enabled: true
stop_on_error: true

scripts:
  - name: update_MSA
    path: "/bg/data/luc/CRAFTY/R/test2.R"
    years: [ALL]

    outputs:
      output: "{output_folder_name}/external/MSA_{year}.csv"

    inputs:
      crafty_supply: "{output_folder_name}/R_input/supply_{year}.csv"
      previous_demand: "{project_path}/services/demand/{scenario}/demand_{previous_year}.csv"

    args:
      mode: "annual"
```

> Recommended path style: use `/` in YAML paths where possible, even on Windows. Java can usually handle this correctly.

---

## YAML fields

### `enabled`

Turns the R script runner on or off.

```yaml
enabled: true
```

If `enabled` is `false`, no R scripts are executed.

---

### `stop_on_error`

Controls what happens if an R script fails.

```yaml
stop_on_error: true
```

If `true`, CRAFTY stops when an R script returns a non-zero exit code.

If `false`, CRAFTY logs the error and continues.

Recommended value for coupled model runs:

```yaml
stop_on_error: true
```

This avoids continuing the simulation with missing or invalid external data.

---

### `scripts`

A list of R scripts that CRAFTY may run.

```yaml
scripts:
  - name: update_MSA
    path: "C:/Users/userName/Desktop/TheFolder/R/test2.R"
    years: [ALL]
```

Multiple scripts can be configured. If more than one script matches the same year, CRAFTY runs them sequentially, not in parallel.

Example:

```yaml
scripts:
  - name: update_MSA
    path: "{project_path}/R/updateMSA.R"
    years: [ALL]

  - name: special_policy_2030
    path: "{project_path}/R/specialPolicy2030.R"
    years: [2030]
```

---

## Script fields

### `name`

A readable name used in logs.

```yaml
name: update_MSA
```

The name does not need to match the R file name, but it should be unique and descriptive.

---

### `path`

The path to the R script.

```yaml
path: "{project_path}/R/updateMSA.R"
```

The path can use placeholders such as `{project_path}`.

On Windows, both styles can work:

```yaml
path: "C:/Users/userName/Desktop/TheFolder/R/test2.R"
```

or:

```yaml
path: "C:\\Users\\userName\\Desktop\\TheFolder\\R\\test2.R"
```

The first style is usually easier to read in YAML.

---

### `years`

Defines when the script should be executed.

Run every year:

```yaml
years: [ALL]
```

Run only in selected years:

```yaml
years: [2020, 2025, 2030]
```

Run only once:

```yaml
years: [2030]
```

---

## Special placeholder names

The R script runner supports dynamic path placeholders. These placeholders are replaced by CRAFTY before the R script is called.

| Placeholder | Meaning |
|---|---|
| `{year}` | Current CRAFTY simulation year |
| `{previous_year}` | Current year minus one |
| `{next_year}` | Current year plus one |
| `{project_path}` | Value of `ConfigLoader.config.project_path` |
| `{output_folder_name}` | Value of `ConfigLoader.config.output_folder_name` |
| `{scenario}` | Value of `ConfigLoader.config.scenario` |

Example:

```yaml
outputs:
  output: "{output_folder_name}/external/MSA_{year}.csv"
```

If the current year is `2030`, this becomes:

```text
<output_folder_name>/external/MSA_2030.csv
```

Another example:

```yaml
inputs:
  previous_demand: "{project_path}/services/demand/{scenario}/demand_{previous_year}.csv"
```

If the current year is `2030`, this becomes:

```text
<project_path>/services/demand/<scenario>/demand_2029.csv
```

---

## `inputs`

`inputs` are named file paths that the R script should read.

```yaml
inputs:
  crafty_supply: "{output_folder_name}/R_input/supply_{year}.csv"
  previous_demand: "{project_path}/services/demand/{scenario}/demand_{previous_year}.csv"
```

CRAFTY passes these to R as command-line arguments:

```text
--input.crafty_supply=<resolved path>
--input.previous_demand=<resolved path>
```

In R, these can be read using:

```r
input_supply <- params[["input.crafty_supply"]]
input_previous_demand <- params[["input.previous_demand"]]
```

---

## `outputs`

`outputs` are named file paths that the R script should write.

```yaml
outputs:
  output: "{output_folder_name}/external/MSA_{year}.csv"
```

CRAFTY passes this to R as:

```text
--output.output=<resolved path>
```

In R, this can be read using:

```r
output_file <- params[["output.output"]]
```

Important: the key name in YAML must match the key used in R.

For example, this YAML:

```yaml
outputs:
  msa: "{output_folder_name}/external/MSA_{year}.csv"
```

must be read in R as:

```r
output_msa <- params[["output.msa"]]
```

This YAML:

```yaml
outputs:
  new_demand: "{project_path}/services/demand/{scenario}/demand_{year}.csv"
```

must be read in R as:

```r
output_new_demand <- params[["output.new_demand"]]
```

**Important:** Only files listed under `outputs` are intended to be used by CRAFTY as external outputs from the R script.

---

## `args`

`args` are optional extra parameters that are not input or output file paths.

```yaml
args:
  mode: "annual"
```

CRAFTY passes this to R as:

```text
--mode=annual
```

In R:

```r
mode <- params[["mode"]]
```

Use `args` for settings such as:

```yaml
args:
  mode: "annual"
  method: "linear"
  overwrite: "true"
```

Use `inputs` and `outputs` for files. Use `args` for general options.

---

## Full example YAML

```yaml
enabled: true
stop_on_error: true

# R scripts are checked and run at the start of each CRAFTY iteration/year.

scripts:
  - name: update_MSA
    path: "C:/Users/userName/Desktop/TheFolder/R/test2.R"
    years: [ALL]

    outputs:
      output: "{output_folder_name}/external/MSA_{year}.csv"

    inputs:
      crafty_supply: "{output_folder_name}/R_input/supply_{year}.csv"
      previous_demand: "{project_path}/services/demand/{scenario}/demand_{previous_year}.csv"

    args:
      mode: "annual"
```

---

## Example R script

This R script reads the command-line arguments passed by CRAFTY, prints them, and writes a simple MSA output CSV.

```r
args <- commandArgs(trailingOnly = TRUE)

parse_args <- function(args) {
  result <- list()

  for (arg in args) {
    if (startsWith(arg, "--")) {
      clean <- substring(arg, 3)
      parts <- strsplit(clean, "=", fixed = TRUE)[[1]]

      key <- parts[1]
      value <- paste(parts[-1], collapse = "=")

      result[[key]] <- value
    }
  }

  return(result)
}

params <- parse_args(args)

year <- as.integer(params[["year"]])

input_supply <- params[["input.crafty_supply"]]
input_previous_demand <- params[["input.previous_demand"]]

# This must match the YAML key:
#
# outputs:
#   output: "{output_folder_name}/external/MSA_{year}.csv"
#
output_file <- params[["output.output"]]

mode <- params[["mode"]]

print(paste("args:", paste(args, collapse = " ")))
print(paste("Running R script for year:", year))
print(paste("Mode:", mode))
print(paste("Input supply:", input_supply))
print(paste("Previous demand:", input_previous_demand))
print(paste("Output file:", output_file))

# Example external variable output
df <- data.frame(
  variable = "MSA",
  value = 25.358 * year
)

write.csv(df, output_file, row.names = FALSE, quote = FALSE)

quit(status = 0)
```

---

## Expected command sent from Java to R

For the YAML example above, CRAFTY builds a command similar to this:

```text
Rscript --vanilla test2.R \
  --year=2030 \
  --input.crafty_supply=<output_folder_name>/R_input/supply_2030.csv \
  --input.previous_demand=<project_path>/services/demand/<scenario>/demand_2029.csv \
  --output.output=<output_folder_name>/external/MSA_2030.csv \
  --mode=annual
```

On Windows, CRAFTY may internally use the full `Rscript.exe` path, for example:

```text
C:/Program Files/R/R-4.6.0/bin/Rscript.exe
```

On Linux/HPC, CRAFTY usually calls:

```text
Rscript
```

after the R module has been loaded.

---

## HPC usage

In the SLURM script, load both Java and R before starting CRAFTY:

```bash
module purge
module load openjdk/21.0.3_9-gcc-11.3.1
module load R

java -jar crafty-core.jar config.yaml
```

The R script will run inside the same SLURM job allocation as CRAFTY. Java waits until the R script finishes before continuing.

---

## Recommended output format for external variables

Crafty only could associete external varables from a csv file in this structure:

```csv
variable,value
MSA,514767.4
```

Example R code:

```r
df <- data.frame(
  variable = "MSA",
  value = 25.358 * year
)

write.csv(df, output_file, row.names = FALSE, quote = FALSE)
```

This produces a simple file that CRAFTY can later read as an external variable.

---

## Common mistakes

### 1. Output key mismatch

YAML:

```yaml
outputs:
  outp: "{output_folder_name}/external/MSA_{year}.csv"
```

R must use:

```r
output_file <- params[["output.outp"]]
```

Not:

```r
output_file <- params[["outputs.outp"]]
```

---

### 3. Forgetting `quit(status = 0)`

At the end of a successful R script, use:

```r
quit(status = 0)
```

If the script fails, use:

```r
quit(status = 1)
```

CRAFTY uses the R exit code to decide whether the script succeeded.

---

### 4. Rscript not available on HPC

On HPC, make sure the SLURM script loads R:

```bash
module load R
```

Then test:

```bash
Rscript --version
```

If this works in the SLURM environment, CRAFTY can call R scripts.

---

## Minimal test R script

Use this script to test the connection between Java and R:

```r
args <- commandArgs(trailingOnly = TRUE)

print("Hello from R")
print(args)

quit(status = 0)
```

If CRAFTY prints the R output and continues, the Java-to-R connection is working.
