**New feature in CRAFTY (v1.1.1+): Owner life cycle (min/max) per AFT**

We added a new mechanism to control how long an owner (AFT) stays on a cell before it can participate in land-use change processes.

### What’s new

* **Each cell** now stores an `owner_life_counter` (how many years the current owner has been on that cell).
* **Each AFT** can optionally define:

  * `min_life_cycle`
  * `max_life_cycle`

### Why

* **Minimum life cycle (`min_life_cycle`)**: adds realism (e.g., forest AFTs should remain on land for a reasonable time before being eligible for abandonment/competition).
* **Maximum life cycle (`max_life_cycle`)**: supports institutional/policy-style constraints (e.g., forcing periodic review/competition after a certain tenure).

### How it works

* For each cell, `owner_life_counter` increases every simulation year.
* When ownership changes, the counter **resets** for the new owner.
* The counter is compared to the AFT’s min/max rules:

  * If `owner_life_counter < min_life_cycle` → the cell **does not participate** in:

    * competitiveness
    * land abandonment
  * If `owner_life_counter > max_life_cycle` → the cell is **forced into competitiveness** (i.e., must be considered as a competition candidate that year).

### How to use it

* Use **`crafty1.1.1.jar` or later**
* Add one or both columns to `aftMetaData.csv`:

  * `min_life_cycle`
  * `max_life_cycle`
* You can use **only one** of them (min only or max only). Both are optional.

### How to ignore it

* Do not include `min_life_cycle` / `max_life_cycle` in `aftMetaData.csv`, **or**
* run an older CRAFTY version (< 1.1.1)

### Notes / important considerations

* If a cell is under a **mask**, the min/max life cycle mechanism **does not apply**.
* `cellsWhereOwnerExceededMaxLifeCycle` are **additional** cells on top of the usual participation percentages for abandonment/competition (i.e., supplementary to the standard “seed” selection).
* Exceeding `max_life_cycle` **forces competition**, but does **not** guarantee the owner will lose the land.

### Outputs

* A new column **`OwnerLifeCounter`** is added in:

  * `sspScenario-Cell-year.csv`
* This column is written **even if you do not use** min/max life cycle inputs.
