# Masks (Land-use control and restrictions)

This page documents **Masks** (land-use control) in CRAFTY:
- what masks are and why they exist
- which input files define them (mask layers + restriction matrices)
- how masks are applied each year
- how to debug common mask-related problems

Masks are a key mechanism for implementing:
- protected areas
- “urban fixed” areas
- restricted transitions (e.g., forbid conversion to certain AFTs)
- time-varying land-use control policies

---

## 0) What is a “mask” in CRAFTY?

In CRAFTY, a mask is a **cell-level control** that can:

1) assign a **maskType** (a label) to cells at specific years  
2) optionally enforce **transition restrictions** based on that maskType  
3) optionally force a cell to a specific “owner” category (project-specific)

A mask is not just a map layer—it is a combination of:
- **mask activation layers** (which cells are controlled at which years)
- **restriction rules** (what is allowed/forbidden on controlled cells)

---

## 1) Two parts of mask inputs

### 1.1 Mask activation layers (where/when a mask applies)
These are CSV layers that specify:

- which cells are affected (`X`, `Y`)
- in which years the mask is active (csvName and `Year_*` column)

### 1.2 Restriction matrix (what the mask does)
A restriction file encodes allowed/forbidden transitions, typically as:

- current owner (rows)
- competitor / candidate owner (columns)
- boolean-like values (`1` allowed, `0` forbidden)

In code, restrictions are often stored in a fast lookup map keyed like:
`"<currentOwner>_<competitor>" -> true/false`

---

## 2) Mask activation CSV format (Year_* pattern)

for each year crafty looks for a csv file contien the corresomeda year 

for each tick (year) Crafty search in the directory has a mask name for a csv file named `..._Mask_Year_YYYY.csv` each file has only one year. 
The pattern is:

- `X`, `Y`
- only one `Year_YYYY` column

Example conceptually:

```text
X,Y,Year_2020
10,20,1
11,20,1
...
```

Interpretation:
- in year 2020: (10,20) is masked, (11,20) is masked

### 2.1 Important implementation detail
Mask updaters for each yar:
- scan column that start with `Year_`
- treat a cell as active if the value contains `"1"` (string-based check)

So `"1"` / `"1.0"` works, but be careful with:
- empty strings
- unexpected encodings (e.g., `"true"`)

---

## 3) How masks are applied each year

Masks are typically applied by an updater that runs each tick:

1) resolve the correct mask layer for the current year
2) read the CSV and find rows where `Year_<currentYear>` is active
3) for each activated cell:
   - set the cell’s `maskType` to the configured mask label
   - optionally apply an “owner assignment” rule (project-specific)
4) load/update the restriction matrix for that maskType (if configured)
5) store restrictions for fast access during competition

A common pattern is:
- the mask layer changes through time (new restricted areas appear)
- restrictions are static per maskType (but can also be time-varying if you provide year-specific matrices)

---

## 4) Multiple masks and stacking

CRAFTY supports multiple land-use control directories:

```yaml
landControle_directories:
  - "/path/to/LandUseControl/UrbanMask/ssp126"
  - "/path/to/LandUseControl/ProtectedAreas/all"
```

When multiple masks apply to the same cell and year, the result depends on the implementation:
- “last one wins” (maskType overwritten)

**Practical recommendation**
- Order mask directories from **weakest to strongest**, so strong restrictions override weaker ones.
- Avoid overlapping masks.

---

## 5) Mask types and naming conventions

A **maskType** is a string label (e.g., `"Urban"`, `"Protected"`, `"Natura2000"`).

Consistency matters:
- maskType labels must match the keys used by restriction loaders
- if restrictions are stored in a per-mask registry, the registry key is often the maskType

Avoid spaces and special characters in maskType labels.

---

## 6) Restriction matrix format

A typical restriction CSV is a matrix:

- first row: competitor labels
- first column: current owner labels

Example conceptually:

```text
,Arable,Pasture,Forest,Unmanaged
Arable,1,1,0,1
Pasture,1,1,0,1
Forest,0,0,1,1
Unmanaged,1,1,1,1
```

Interpretation:
- from `Arable` you may switch to `Pasture` but not to `Forest`
- unmanaged can be taken over by any competing AFT

### 6.1 Orientation matters (common source of bugs)
The most frequent issue is swapping:
- current-owner axis
- competitor axis

If the matrix is transposed, you can accidentally forbid everything.

---

## 7) How restrictions influence competition

During competition, when an AFT wants to take over a cell:

1) candidate competitor AFT is chosen (based on utility, neighbour set, random choice, etc.)
2) mask restrictions are checked for that cell:
   - if `allowed(currentOwner, competitor)` is false → competitor rejected
3) if allowed, utility comparison proceeds and takeover may happen

Masks therefore influence:
- which AFTs are eligible on which cells
- how fast land-use changes can occur
- spatial patterns (sharp boundaries in land-use maps)

---

## 8) Common mask workflows

### 8.1 “Fixed urban” areas
- activate a maskType for urban cells
- restrict all transitions (or force owner to Urban placeholder)

### 8.2 Protected areas (no conversion)
- activate protected mask
- forbid conversion to certain AFTs (or forbid all conversion)

### 8.3 Policy phase-in (time-varying)
- Year_* columns gradually expand a protected area footprint
- restrictions remain constant

---

## 9) Config keys related to masks (quick reference)

```yaml
landControle_directories:
  - "/path/to/LandUseControl/UrbanMask/ssp126"
  - "/path/to/LandUseControl/ProtectedAreas/all"
```
---

## 10) Troubleshooting masks

### 10.1 “Mask has no effect”
**Causes**
- mask directory not included in `landControle_directories`
- Year column name does not match expected format (`Year_2020`)
- values are not interpreted as active (`1` missing)
- X/Y coordinates mismatch (different grid/resolution)

**Fix**
- verify directory is loaded (check logs)
- confirm `Year_<currentYear>` exists in the CSV header
- ensure active values contain `"1"`
- validate that X/Y in mask file correspond to the project grid

### 10.2 “Everything is blocked (no land-use change)”
**Causes**
- restrictions forbid all competitors (matrix orientation wrong)
- mask applied everywhere by accident (Year column filled with 1s)
- overlap stacking causes strong mask to override most cells

**Fix**
- print/debug a few restriction lookups:
  - pick one cell, list allowed competitors
- temporarily disable masks and rerun short window
- verify matrix orientation (rows=current, cols=competitor)

### 10.3 “Only some years work”
**Cause**
- mask file doesn’t include `Year_<year>` for all years
- mask updater only activates years present in header

**Fix**
- include Year columns for all years you need, or:
- provide multiple mask files (year-indexed) if your template supports it

### 10.4 “Mask conflicts with baseline owners”
**Cause**
- mask enforces a placeholder owner that is not defined as an AFT/placeholder label

**Fix**
- define the placeholder in AFT definitions
- or use maskType-only restrictions without forcing ownership

---

## Related pages

- Cells & Regions: `cells-regions.md`
- AFTs: `afts.md`
- Data model: `../02-data-model.md`
- Troubleshooting: `../../user-guide/05-troubleshooting.md`
