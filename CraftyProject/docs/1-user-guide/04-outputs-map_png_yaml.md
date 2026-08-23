# PNG Map Outputs from YAML

The `map_png` option allows CRAFTY to export spatial PNG maps directly during a run, without additional post-processing scripts. This is useful for quickly checking cell-level patterns in services, capitals, behaviour parameters, taxes/subsidies, competitiveness, and owner life counters. It also helps users visually inspect spatial relationships, such as whether a capital is associated with higher service supply, or whether competitiveness is concentrated in specific areas.

## How it works

PNG map exporting is controlled by two YAML options:

```yaml
map_output_years: 5
```

`map_output_years` defines when maps are exported. A single integer means “export every N years”. For example, `5` exports maps every five years. A list means “export only these years”:

```yaml
map_output_years: [2020, 2025, 2030, 2050]
```

The `map_png` section defines which maps should be generated:

```yaml
map_png:
  enabled: true

  single_value:
    - service:ALL
    - capital:human
    - land_taxes_subsidies
    - service_taxes_subsidies:Softwood
    - behaviour:Attitude_intensification
    - competitiveness
    - owners_life_counter

  dual_value:
    - [service:Hardwood, capital:human]
    - [behaviour:Weight_inertia, behaviour:Weight-social]
    - [service:Carbon, service:Softwood]

  triple_value:
    - [service:Hardwood, capital:human, service:Carbon]
    - [behaviour:Attitude_intensification, behaviour:Critical_mass, behaviour:MaxGive_in]
    - [service:Softwood, capital:human, service:Carbon]
    - [service:Softwood, capital:human, service:Hardwood]
```

Set `enabled: true` to activate PNG map exporting.

## Map types

### Single-value maps

`single_value` maps show one cell attribute at a time.

Examples:

```yaml
single_value:
  - service:ALL
  - capital:human
  - service_taxes_subsidies:Softwood
  - land_taxes_subsidies
  - competitiveness
  - owners_life_counter
```

`service:ALL` generates one map for every service. A specific service or capital can also be selected, for example `service:Hardwood` or `capital:human`.

Cell behaviour parameters use the canonical `behaviour:<parameter>` prefix. Supported parameters are:

- `Attitude_intensification`
- `Weight_inertia`
- `Weight-social`
- `Critical_mass`
- `MaxGive_in`
- `Neighborhood_size`

For example, `behaviour:Attitude_intensification` creates a single map. `behaviour:ALL` creates one map for
each supported parameter. The legacy misspelling `beheviour:` is accepted, but `behaviour:` should be used in
new configurations. Write the colon without a following space, or quote the complete value, so YAML treats it
as a string: `behaviour:Weight_inertia` or `"behaviour: Weight_inertia"`.

Behaviour data exists for cells loaded from the latest available annual behaviour parameter file. If a later
year has no file, CRAFTY retains the previous values. Cells without a behaviour entry are rendered as
transparent/no-data pixels.

### Dual-value maps

`dual_value` maps combine two cell attributes into one bivariate map.

Example:

```yaml
dual_value:
  - [service:Hardwood, capital:human]
  - [behaviour:Attitude_intensification, behaviour:Weight_inertia]
```

This helps inspect the spatial link between Hardwood supply and human capital. Each pair must contain exactly two values. `ALL` is not supported here.

### Triple-value maps

`triple_value` maps combine three cell attributes into one RGB map.

Example:

```yaml
triple_value:
  - [service:Hardwood, capital:human, service:Carbon]
  - [behaviour:Weight-social, behaviour:Critical_mass, behaviour:MaxGive_in]
```

The values are mapped as:

```text
first value  -> red channel
second value -> green channel
third value  -> blue channel
```

This makes it possible to inspect the spatial overlap between three variables in one map. Each entry must contain exactly three values. `ALL` is not supported here.

## Output structure

CRAFTY writes the generated maps into the run output folder under `MapsPlots`.

For the example above, the structure will look similar to:

```text
MapsPlots
├── AFTs_maps
├── singles_value_maps
│   ├── capital_human
│   ├── competitiveness
│   ├── land_taxes_subsidies
│   ├── owners_life_counter
│   ├── service_hardwood
│   ├── service_softwood
│   └── service_taxes_subsidies_softwood
├── duals_value_maps
│   ├── service_carbon__service_softwood
│   └── service_hardwood__capital_human
└── triple_value_maps
    ├── service_hardwood__capital_human__service_carbon
    ├── service_softwood__capital_human__service_carbon
    └── service_softwood__capital_human__service_hardwood
```

Each subfolder contains the PNG files for the configured output years.

## Important interpretation note

For each map, the color scale is calculated from the minimum and maximum values of that variable in the current output year. This makes the maps very useful for checking spatial patterns within a year.

However, direct color comparison between different years should be done carefully. A dark color in 2020 and a dark color in 2050 do not necessarily represent the same absolute value. They represent high values relative to the range in that specific year. For strict year-to-year comparison, a fixed min/max scale would be needed.


Generating PNG could make the model slower.
