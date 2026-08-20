# GUI institution configuration for the EU summer-school project

This is the canonical schema-version 1 conversion of the legacy GUI files.

- `targets.yaml` contains the 15 original target definitions.
- `institutions.yaml` contains the 4 original institutions and 19 policies.
- All institutions use `decision_engine: {type: manual}` for GUI decisions.
- All policies use cell-level effects with `scope: {type: all_cells}`.
- Policy cost calibration is preserved: each canonical `unit_cost` multiplied
  by `estimated_quantity` equals the legacy `initial_expected_policy_cost`.
- Existing `targetsToBeCompare_<scenario>.csv` files remain valid because
  target display names were preserved exactly.

