# Updaters

This page documents **Updaters**: the main mechanism that defines “what happens each year” in CRAFTY.

Updaters are scheduled modules executed by the runner during each tick (year). They are responsible for:
- loading year-specific inputs (capitals, masks, degradation, policy tables)
- computing productivity / utility
- selecting participating cells (seeding)
- applying processes (competition, abandonment, take-over)
- writing outputs (or triggering output writers)

If you want to add a new yearly mechanism, you almost always implement it as an updater.

---

## 0) The annual schedule (mental model)

A typical CRAFTY year looks like:

```text
for year in start..end:
  (A) update time-varying inputs (capitals, masks, degradation)
  (B) compute supply + marginal utility + utility signals
  (C) select participating cells (seed)
  (D) run processes (abandonment, competition, take-over)
  (E) write outputs (CSV, optional maps)
```

Exactly which steps happen depends on which updaters are enabled and how they are ordered.

---

## 1) What is an updater (in code terms)

Updaters typically:
- implement a common interface (or extend an abstract updater base)
- have access to:
  - the current year
  - the cell registry / grid
  - the region registry
  - global registries (AFTs, services, capitals)
- run as part of the runner schedule

Common implementation patterns:
- an `initialize()`-style setup at model startup
- a `step()` method executed each tick
- config-driven switches that enable/disable the updater

---

## 2) Important updater categories

Even if class names differ slightly across branches, the functional roles are stable.

### 2.1 Data update updaters (load time-varying inputs)
These updaters update model state from scenario files.

Typical responsibilities:
- load capital layers for the current year
- apply degradation/shock maps
- load year-specific masks or update maskTypes
- refresh policy signals (taxes/subsidies)

Examples (conceptual names):
- `CapitalUpdater`
- `CapitalDegradationUpdater`
- `LandMaskUpdater`

### 2.2 Service updater (demand/weight/tax snapshots + supply aggregation)
Responsible for:
- reading demand / weights / taxes for the current year (per region)
- computing region supply from cell production
- computing demand–supply gaps and derived marginal utility signals
- maintaining “world aggregates” for plots and global outputs

### 2.3 Utility / productivity updaters
Responsible for:
- computing cell-level production values (AFT × capitals → service supply)
- computing cell-level utility signals from service marginal utilities
- storing utility on cells for ranking/seeding and diagnostics

Conceptual names:
- `ProductivityUpdater`
- `UtilityUpdater`


### 2.4 Seed selection updater
Selects the set of cells that will participate in processes each year.
Often controlled by `seedID`:
- `rank` (lowest-utility cells)
- numeric seed (pseudo-random)
- seed file/directory (project-specific)


### 2.5 Process updaters (competition / abandonment / take-over)
These implement land-use change decisions.

- **Abandonment**: owners give up cells (often bounded by a yearly percentage)
- **Competition**: candidate AFTs compete for ownership
- **Take-over**: unmanaged/abandoned cells are offered to competitors

Conceptual names:
- `LandAbandonmentUpdater`
- `CompetitionUpdater`
- `TakeOverUnmanagedUpdater`

### 2.6 Output updaters / listeners
These handle writing:
- time series CSV tables
- optional map snapshots (PNG/CSV/GeoTIFF depending on build)
- diagnostics and trackers

Conceptual names:
- `OutputUpdater`
- `MapOutputUpdater`

(Some projects implement outputs via listeners rather than explicit updaters.)

### 2.7 Coupling / waiting updaters (optional)
Used when running CRAFTY as part of a coupled pipeline:
- pause at certain years until an external “flag” is present
- write “ready” flags for downstream components

Conceptual name:
- `WaitingFlagUpdater`

---

## 3) Updater ordering (why it matters)

Order is crucial. Common dependencies:

- You must load **capitals** before computing **productivity**.
- You must compute **supply** before computing **demand–supply gaps**.
- You must compute **utility** before `seedID: rank` can select lowest-utility cells.
- Outputs should run after processes if you want end-of-year snapshots but AFT distribution is capured befor land use change.

**Typical safe order**
1) update capitals (+ degradation)
2) update masks/restrictions
3) compute productivity (outputs maps)
4) compute supply + marginal utility
5) compute utility
6) select seed
7) abandonment
8) competition
9) take-over unmanaged
10) outputs (CSV)

Your build may merge some steps, but the dependency logic remains.

---

## 4) Config keys that control updater behaviour

Updaters are often toggled or parameterised via config.

### 4.1 Input updates
```yaml
CAPITALS_directory: ""
capital_degradation_directory: ""
landControle_directories: []
```

### 4.2 Seeding
```yaml
seedID: rank
participating_cells_percentage: 0.05
```

### 4.3 Process rates
```yaml
land_abandonment_percentage: 0.03
takeOverUnmanageCells_percentage: 0.80
MostCompetitorAFTProbability: 0.70
```

### 4.4 Services / utility
```yaml
marginal_utility_calculations_per_tick: 1
averaged_residual_demand_per_cell: false
initial_demand_supply_equilibrium: false
```

### 4.5 Outputs
```yaml
generate_output_files: true
generate_map_output_files: false
map_output_years: [2020, 2030]
```

---

## 5) Practical debugging: “which updater caused this?”

### 5.1 Use a short run window
Run 5–10 years with small output and compare runs with one feature toggled.

### 5.2 Toggle heavy/complex updaters
Common isolations:
- masks off → check if restrictions are blocking change
- degradation off → check if capitals were being modified
- maps off → check if slowdown is output-related
- random seeding vs rank → check if ranking is driving patterns

### 5.3 Log the schedule
Good practice (recommended for developers):
- print the list/order of updaters at startup
- print per-updater timing each year (when debug enabled)

This makes it obvious where time is spent and where failures occur.

---

## 6) Adding a new updater (developer workflow)

A typical pattern:

1) Create a new class in `crafty-core/.../updaters`
2) Implement the base interface or extend `AbstractUpdater`
3) Add config keys to enable/disable and configure behaviour
4) Register it in the runner schedule (or config-driven schedule)
5) Add a minimal unit test (or toy run) covering:
   - input missing behaviour (defaults)
   - one-year stepping logic
   - idempotence for repeated runs (if relevant)

**Design tips**
- keep input parsing in loaders; keep per-year application in the updater
- keep the updater deterministic when possible (or seed randomness explicitly)
- avoid writing outputs inside non-output updaters (separation of concerns)

---

## 7) Troubleshooting updaters

### 7.1 “Updater runs but changes nothing”
- it is disabled via config
- it cannot resolve its input path
- the year column/file does not exist for the current year
- its selection subset is empty (e.g., seed percentage 0)

### 7.2 “Updater causes crash late in the run”
Common causes:
- missing year-specific file for a later year
- memory pressure from large intermediate maps/lists
- output explosion (maps written each year)

Fix:
- add safe fallback when year file missing
- reduce map outputs
- increase JVM memory

### 7.3 “Updater changes results unexpectedly between runs”
Common causes:
- non-deterministic ordering in parallel processing
- random tie-breaking without fixed seed
- implicit reliance on HashMap iteration ordering

Fix:
- use stable comparators with explicit tie-breaking seeds
- log the seed used
- prefer deterministic collections where needed

---

## Related pages

- Architecture (where updaters live): `../01-architecture.md`
- Config reference: `../03-config-reference.md`
- Components: `services.md`, `afts.md`, `cells-regions.md`, `masks.md`
- Outputs: `../../user-guide/04-outputs.md`
