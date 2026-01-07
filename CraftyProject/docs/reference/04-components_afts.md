# AFTs (Agent Functional Types)

This page documents **AFTs** (Agent Functional Types): what they represent, which inputs define them,
how they behave in the model, and how to troubleshoot common AFT-related issues.

AFTs are the “agents” in CRAFTY: they **own cells**, **produce services** from capitals, and **compete**
(or abandon land) based on utility signals.

---

## 0) What is an AFT?

An **AFT** is a parameterised land-manager type. It defines:

- **Production**: how the AFT converts capitals into service supply
- **Behaviour**: thresholds and heuristics that control abandonment and give-in/give-up decisions
- **Eligibility/constraints**: whether it is a competing AFT, an abandoned/unmanaged placeholder, or a mask placeholder
- **Policy terms** (optional): taxes/subsidies affecting the AFT or its services

AFTs appear in outputs as:
- land-use categories (owner map)
- area per AFT time series
- service supply composition by AFT (if tracking is enabled)

---

## 1) AFT lifecycle in the model

At runtime, AFTs exist in three layers:

1) **Definitions** (loaded once at startup)  
2) **Active instances** (used during a run, per region and/or globally)  
3) **(Optional) mutated variants** created during competition (evolution-style runs)

### 1.1 Definitions vs instances
Most runs use a fixed set of AFTs defined at startup.  
If evolution/mutation is enabled, new variants can appear during the run.

Mutation is controlled by config keys such as:
```yaml
mutate_on_competition_win: false
mutation_interval: 0
```

---

## 2) AFT identity and classification

CRAFTY typically distinguishes AFT classes by type:

### 2.1 Competing AFTs
- “normal” agents that can compete for land
- appear as candidates in competition

### 2.2 Unmanaged / abandoned placeholder
- represents land with no active owner
- can be taken over by competing AFTs

### 2.3 Mask placeholder
- used by land-use control to block changes on specific cells
- mask placeholders do **not** compete and are not valid takeover targets (unless configured otherwise)

**Important consistency rule**  
Baseline owners and mask owners must match actual AFT labels or placeholders defined in the AFT set.

---

## 3) Core AFT inputs (what files define AFTs)

AFT data typically comes from three input families:

1) **AFT definitions and metadata** (identity + high-level attributes)  
2) **AFT production** (productivity + sensitivities: services × capitals)  
3) **AFT behaviour** (give-up/give-in, noise, thresholds, optional categories)

Some datasets also provide:
- land taxes/subsidies per AFT (optional)
- time-varying updates for AFT parameters (optional)

---

## 4) AFT definitions / metadata (identity layer)

This table defines:
- the list of AFT labels
- optional full names
- optional categories


**Typical columns**
- `Label` (canonical AFT id)
- `Name` or `CompleteName` (human-friendly)
- `Category` (optional; used if category-based give-in is enabled and for reporting outputs)
- optional flags used by your behaviour logic

**Must match**
- the baseline map owner labels
- restriction matrices (if using masks/transition restrictions)
- any policy tables keyed by AFT label

---

## 5) AFT production (supply model)

AFT production is what turns capitals into service supply.

### 5.1 Two common components

**(A) Productivity level per service**  
A per-service multiplier:
- `productivityLevel[service] → value`

**(B) Sensitivity matrix (service × capital)**  
Exponent/elasticity-like parameters:
- `sensByService[service][capital] → exponent`

In many CRAFTY projects, production is conceptually:
```text
Supply(service) ≈ productivityLevel(service) × Π_capital (capitalValue ^ sensitivityExponent)
```
(Exact functional form depends on your implementation, but this is the common pattern.)

### 5.2 Typical CSV structure used by loaders
A common pattern is a service-indexed CSV where:
- rows correspond to services
- columns correspond to capitals (plus a productivity column)

Example conceptually:
```text
Service,Production,CapitalA,CapitalB
Food,   1.0,       0.7,     0.2
Timber, 0.6,       0.1,     0.9
...
```

The loader populates:
- productivityLevel from the `Production` column
- sensitivities from the capital columns

**Important**
- service labels must match the canonical service list
- capital labels must match the canonical capital list

Unknown services/capitals are ignored. missing ones will report error.

### 5.3 Sensitivity updates (runtime)
If your build supports sensitivity updates, the model can load updated production/sensitivity tables at certain years.
This is often implemented via a dedicated updater reading from a directory of year-indexed files.

---

## 6) AFT behaviour (decision model)

Behaviour files populate the AFT “decision personality”, commonly including:

- give-up thresholds (how easily the AFT abandons land)
- give-in thresholds (how easily it accepts taking over land)
- noise ranges
- optional risk/heterogeneity parameters (project-specific)
- category membership (if category-based give-in is used)

### 6.1 Typical behaviour file pattern
Default is:
each file AFT has a separet csv file define parameters also possible to define as one file as a key/value-style CSV where each row is one AFT and each column is a parameter.

Example conceptually:
```text
AFT,giveUpMean,giveUpSD,giveInMean,giveInSD,noiseMin,noiseMax
IntBF,0.5,0.1,0.6,0.1,0.0,0.2
...
```

### 6.2 Category-based give-in (optional)
If enabled:
```yaml
use_AFTs_categories_GiveIn: true
```
then the give-in logic may restrict candidates based on `Category` labels.
This is useful when you want “within-category” substitution rather than global substitution.

---

## 7) AFT-related policy inputs (optional)

### 7.1 Land taxes/subsidies (AFT-level)
Some runs include an AFT-level incentive term:
```yaml
land_taxes_subsidies_path: "/path/to/land_taxes_subsidies"
```

This can be:
- one file for all regions, or
- one file per region (matched by token)

Defaults:
- if missing, land taxes/subsidies are treated as `0.0`

### 7.2 Service taxes/subsidies (service-level)
This is covered in `services.md` but matters for AFTs because it modifies competitiveness via service pathways.

---

## 8) How AFTs participate in annual processes

In each year, AFTs influence the simulation through:

1) **Supply computation** (cell-level production)
2) **Utility computation** (service marginal utility × AFT supply contribution)
3) **Competition** (candidate AFTs evaluated on cells)
4) **Abandonment** (owners may abandon if utility is below threshold)
5) **Take-over of unmanaged cells** 

AFT participation is often constrained by:
- masks/restrictions (transition constraints)
- neighbour priority candidate reduction (optional)
- category give-in rules (optional)

---

## 9) Config keys most relevant to AFTs (quick reference)

```yaml
# Seeding and annual rates (strongly affects how much AFTs can change the landscape)
seedID: rank
participating_cells_percentage: 0.05
land_abandonment_percentage: 0.03
takeOverUnmanageCells_percentage: 0.80

# Behaviour switches
use_abandonment_threshold: true
use_AFTs_categories_GiveIn: false

# Mutation/evolution (optional)
mutate_on_competition_win: false
mutation_interval: 0
```

---

## 10) Troubleshooting AFT issues

### 10.1 “Unknown owner label in baseline”
**Cause**
- baseline owner column includes an AFT label not defined in the AFT definitions

**Fix**
- add the missing AFT to the AFT definitions
- or correct the baseline owner labels

### 10.2 “AFT produces zero/NaN supply”
**Causes**
- productivity level is 0 or NaN
- sensitivity exponents are missing/misnamed (capital header mismatch)
- required capital layers are missing or all zero

**Fix**
- confirm capital names in metadata match capital columns in sensitivity table
- confirm capitals are loaded and vary spatially

### 10.3 “One AFT dominates everything”
**Common causes**
- demands strongly favour one service, and one AFT is best at producing it
- participating percentage is too high, so it spreads too fast
- weights or taxes/subsidies heavily favour one pathway
- The initial supply is 0 for a service that ignores the initial equilibrium.

**Fix**
- reduce `participating_cells_percentage`
- inspect demands/weights/taxes
- compare `seedID: rank` vs a reproducible random seed

### 10.4 “No land-use change occurs”
**Causes**
- `participating_cells_percentage` too small (or 0)
- masks block transitions everywhere
- restrictions forbid all transitions

**Fix**
- test with masks off
- check restriction matrix orientation (current-owner vs competitor)
- increase participating percentage for a short diagnostic run

---

## Related pages

- Services component: `services.md`
- Data model overview: `../02-data-model.md`
- Config reference: `../03-config-reference.md`
- Troubleshooting: `../../user-guide/05-troubleshooting.md`
