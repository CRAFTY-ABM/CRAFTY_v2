# CRAFTY Code Bug Report

Date: 2026-08-05  
Review target: current working tree (including existing uncommitted changes)  
Scope: `crafty-core`, `crafty-gui`, `crafty-institution`, and `crafty-plum`

## Executive summary

The Maven reactor compiles and all 114 originally available tests pass. The original review identified seven items. BUG-01 and BUG-04 have since been corrected and verified, and BUG-02 is closed as intentional behavior because masks are designed to take priority over the minimum lifecycle. Four items remain open.

## Findings

### BUG-01 — Cell-level tax utility is calculated and discarded

- **Severity:** High
- **Status:** Resolved and verified on 2026-08-05
- **Location:** `crafty-core/src/main/java/de/cesr/crafty/core/crafty/Competitiveness.java:68-75`
- **Condition:** `use_cell_level_taxes` is enabled and CRAFTY is not coupled with PLUM.
- **Observed behavior:** `utilityUseMarginalWithTexes(c, a, r)` is called, but its return value is ignored. Execution then always returns `utilityUseMarginal(c, a, r)`.
- **Impact:** Enabling cell-level taxes has no effect on competition utility or land-use decisions. Runs can appear valid while producing results that omit the configured policy signal.
- **Resolution:** The tax-aware result is now returned directly:

  ```java
  if (use_cell_level_taxes) {
      return utilityUseMarginalWithTexes(c, a, r);
  }
  ```

- **Verification:** The implementation was inspected and `mvn -pl crafty-core -Dtest=CompetitivenessTest test` passed (5 tests). A direct regression test comparing utility with the flag disabled/enabled using non-zero cell service taxes is still recommended.

### BUG-02 — A mask bypasses the owner's minimum lifecycle constraint

- **Severity:** High
- **Status:** Closed as intended behavior on 2026-08-05
- **Location:** `crafty-core/src/main/java/de/cesr/crafty/core/crafty/Competitiveness.java:145-159`
- **Condition:** A cell has any non-null `maskType`, and its current owner has not reached `min_life_cycle`.
- **Observed behavior:** The lifecycle check is in an `else if` attached to the mask check. Therefore every masked cell skips minimum-lifecycle enforcement, even when the mask has no restriction map or the relevant transition is simply allowed.
- **Accepted behavior:** Masks intentionally have higher priority than the minimum lifecycle. No production change is required.
- **Recommended test/documentation:** Add a test and configuration note explicitly showing that an allowed mask transition overrides `min_life_cycle`, so the precedence cannot be mistaken for a regression later.

### BUG-03 — Unmanaged takeovers count one ownership change twice

- **Severity:** Medium
- **Location:**
  - `crafty-core/src/main/java/de/cesr/crafty/core/crafty/Competitiveness.java:211-220`
  - `crafty-core/src/main/java/de/cesr/crafty/core/crafty/RegionalModelRunner.java:401-409`
- **Condition:** An unmanaged/abandoned cell is successfully taken over.
- **Observed behavior:** `applyCompetitionDecision()` reaches `takeOverAcell()`, which increments `Listener.landUseChangeCounter`. The takeover loop then increments the same counter again after removing the cell from the unmanaged set.
- **Impact:** `landEventCounter` and any downstream change-rate metrics over-report unmanaged-to-managed transitions by one event each.
- **Recommended fix:** Remove the second increment in `RegionalModelRunner`; ownership mutation should have one authoritative accounting point.
- **Missing test:** Run one guaranteed unmanaged takeover and assert both the final owner and a counter delta of exactly one.

### BUG-04 — Mask names can assign an arbitrary owner by substring and map iteration order

- **Severity:** Medium
- **Status:** Resolved on 2026-08-05 by metadata-driven forced flags and deterministic priorities
- **Location:** `crafty-core/src/main/java/de/cesr/crafty/core/updaters/LandMaskUpdater.java:243-274`
- **Condition:** A mask name contains one or more AFT labels but is not exactly equal to a single label (for example, descriptive mask names or overlapping short labels).
- **Observed behavior:** `maskToOwner()` treats every AFT label contained anywhere in `maskType` as a match and repeatedly replaces the owner. `AFTsLoader.getAftHash()` is a `ConcurrentHashMap`, so the final non-exact match depends on unspecified iteration order. `cleanMaskType()` uses the same substring rule and may clear an owner that was not uniquely encoded by the mask.
- **Impact:** Identical data can select the wrong forced owner; overlapping labels make the result ambiguous and potentially non-reproducible.
- **Resolution:** `csv/LandUseControl-metadata.csv` now controls forced behavior and priority. Overlaps use the smallest priority number, with file order as the deterministic fallback. Forced candidates are derived from allowed target columns in the restriction matrix. Multiple candidates use the configured most-competitive probability or deterministic pseudo-random selection without neighbor filtering.
- **Verification:** Focused metadata, forced-target, multi-target selection, reproducibility, and overlap-priority tests pass.

### BUG-05 — A zero fuzzy-institution time lag crashes at runtime

- **Severity:** Medium
- **Location:**
  - `crafty-institution/src/main/java/institutions_Fuzzy/Institution.java:84-115`
  - `crafty-institution/src/main/java/institutions_Fuzzy/Institution.java:150-161`
- **Condition:** Either constructor receives `time_lag == 0`.
- **Observed behavior:** Constructors store the value without validation; `step()` evaluates `simulation_steps % time_lag`, causing `ArithmeticException: / by zero`.
- **Impact:** A malformed institution configuration terminates the simulation only when the institution first steps, rather than failing clearly during loading.
- **Recommended fix:** Reject `time_lag <= 0` in both constructors (and ideally in the JSON/config loader) with an `IllegalArgumentException` naming the invalid field.
- **Missing test:** Constructor validation for zero and negative time lags.

### BUG-06 — A missing or malformed requested config silently launches defaults

- **Severity:** Medium
- **Location:** `crafty-core/src/main/java/de/cesr/crafty/core/cli/ConfigLoader.java:55-86`
- **Condition:** `--config-file` names a missing file, or the supplied YAML cannot be parsed.
- **Observed behavior:** A missing requested path falls back to bundled `/config.yaml`; parse errors return `new Config()`. In both cases startup continues.
- **Impact:** A user can believe a scenario-specific configuration is running while CRAFTY actually runs defaults, potentially writing plausible but invalid scientific output.
- **Recommended fix:** Distinguish “no path supplied” from “an explicit path was supplied but is invalid.” Fail fast for the latter. Include the requested absolute path and parse cause in the exception/error message.
- **Missing test:** Assert that an explicitly missing path and malformed YAML stop startup; retain fallback only when no path was requested.

### BUG-07 — GUI packaging is tied to one developer's Windows filesystem

- **Severity:** Medium (build/release)
- **Location:** `crafty-gui/pom.xml:20-23, 85-118, 137`
- **Condition:** Packaging on another machine, account, OS, or CI agent.
- **Observed behavior:** The `prepare-package` phase copies JavaFX from `C:/Users/byari-m/Documents/JavafxSDK/...`, forces the JavaFX platform to `win`, and configures `jpackage` output as `exe` unconditionally.
- **Impact:** `mvn package` is not reproducible outside that specific workstation setup; macOS/Linux packaging and most CI builds cannot use the reactor POM as written.
- **Recommended fix:** Use Maven-resolved JavaFX artifacts and OS-specific profiles/properties. Move Windows `jpackage` settings into a Windows profile and avoid absolute user paths.
- **Missing check:** Run `mvn package` in a clean Windows CI image and the documented macOS packaging environment.

## Verification performed

- `mvn -pl crafty-core test`: **passed**, 114 tests, 0 failures/errors/skips.
- `mvn test -DskipTests=false`: **passed** for all five reactor projects.
- `crafty-gui`, `crafty-institution`, and `crafty-plum` currently have no effective automated test coverage in the reactor output.
- `git diff --check`: no whitespace errors; line-ending conversion warnings were reported for multiple modified files.

## Recommended priority

1. Fix BUG-03 and add event-accounting regression coverage before comparing change counts across runs.
2. Validate institution timing (BUG-05).
3. Fail fast on invalid explicit configs (BUG-06).
4. Make packaging portable (BUG-07) before relying on CI or release builds.
