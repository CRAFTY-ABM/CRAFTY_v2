# Minimal scenario data template

This document explains the **minimal-scenario_data_example** folder layout used by the CRAFTY minimal dataset.
It is intended as a **copy‑and‑edit template** for creating new small datasets, debugging loaders, and running
quick smoke tests (headless or GUI).

> Tip: Keep this dataset *small* (e.g., 10×10 grid, 2020–2025) so it can run in seconds and be used in CI.

---

## 0) What this template is for

Use this template when you want:

- a **self-contained** scenario dataset that can live inside the repo
- a dataset that exercises the **full workflow**: baseline → capitals → services → AFT competition → outputs
- a stable example for documentation and “does CRAFTY still run?” checks

This layout supports:
- multiple scenarios ( two)
- multiple regions (regionalisation enabled)
- optional masks and capital degradation
- standard outputs (tables + optional maps/plots)

---

## 1) Folder tree

Your dataset root (the value you provide as `project_path`) contains:
```text
minimal-scenario_data_example
├───AFTs
│   ├───agents
│   │   └───default_agents
│   │           AftParams_<AFT_1>.csv
│   │           AftParams_<AFT_2>.csv
│   │           AftParams_<AFT_3>.csv
│   │
│   └───production
│       └───default_production
│               <AFT_1>.csv
│               <AFT_2>.csv
│               <AFT_3>.csv
│
├───csv
│       AFTsMetaData.csv
│       Capitals.csv
│       scenarios.csv
│       Services.csv
│
├───GIS (optional)
│       <region_world>_Regions.csv
│
├───output
│   └───<scenario_1>
│       ├───Default_Run_Output_2026_01_08_13_41
│       │   │   config.txt
│       │   │   <scenario_1>-Cell-2020.csv (optional)
│       │   │   <scenario_1>-Cell-2025.csv (optional)
│       │   │   <scenario_1>-landEventCounter.csv
│       │   │   <scenario_1>Total-AggregateAFTComposition.csv
│       │   │   <scenario_1>Total-AggregateDemandServicesEquilibrium.csv
│       │   │   <scenario_1>Total-AggregateServiceDemand.csv
│       │   │
│       │   ├───MapsPlots (optional)
│       │   │       map_2020.png
│       │   │       map_2025.png
│       │   │
│       │   ├───plots (optional)
│       │   │       <service_1>.PNG
│       │   │       <service_2>.PNG
│       │   │       Land_use_trends.PNG
│       │   │       <service_3>.PNG
│       │   │
│       │   ├───region_<region_1>
│       │   │       region_<region_1>-AggregateAFTComposition.csv
│       │   │       region_<region_1>-AggregateServiceDemand.csv
│       │   │       region_<region_1>-AverageUtilities.csv
│       │   │       region_<region_1>-DemandServicesEquilibrium.csv
│       │   │
│       │   └───region_<region_2>
│       │           region_<region_2>-AggregateAFTComposition.csv
│       │           region_<region_2>-AggregateServiceDemand.csv
│       │           region_<region_2>-AverageUtilities.csv
│       │           region_<region_2>-DemandServicesEquilibrium.csv
├───services
│   ├───demand
│   │   ├───<scenario_1>
│   │   │       demand_<region_1>.csv
│   │   │       demand_<region_2>.csv
│   │   │       demand_world.csv
│   │   │
│   │   └───<scenario_2>
│   │           demand_<region_1>.csv
│   │           demand_<region_2>.csv
│   │           demand_<region_world>.csv
│   │
│   └───Service_Utility_Weights (optional)
│       ├───<scenario_1>
│       │       Utility_Weight_<region_1>.csv
│       │       Utility_Weight_<region_2>.csv
│       │       Utility_Weight_<region_world>.csv
│       │
│       └───<scenario_2>
│               Utility_Weight_<region_1>.csv
│               Utility_Weight_<region_2>.csv
│               Utility_Weight_<region_world>.csv
│
└───worlds
    │   Baseline_map.csv
    │
    ├───capitals
    │   ├───<scenario_1>
    │   │       <region_world>_capitals_<year_0>.csv
    │   │       <region_world>_capitals_<year_1>.csv
    │   │       ...
    │   │       <region_world>_capitals_<year_N>.csv
    │   │
    │   └───<scenario_2>
    │           <region_world>_capitals_<year_0>.csv
    │           <region_world>_capitals_<year_1>.csv
    │           ...
    │           <region_world>_capitals_<year_N>.csv
    │
    ├───capitals_degradation (optional)
    │   └───default_capitals_degradation
    │           <year_i>.csv
    │           <year_j>.csv
    │
    └───LandUseControl (optional)
        ├───<Mask_1>
        │   │   default_<Mask_1>_Mask_Restrictions.csv
        │   │
        │   ├───<scenario_1>
        │   │       Mask_Year_<year_0>.csv
        │   │       Mask_Year_<year_k>.csv
        │   │
        │   └───<scenario_2>
        │           Mask_Year_<year_0>.csv
        │           Mask_Year_<year_i>.csv
        │           Mask_Year_<year_j>.csv
        │
        └───<Mask_2>
            │   default_<Mask_2>_Mask_Restrictions.csv
            │
            ├───<scenario_1>
            │       Mask_Year_<year_0>.csv
            │       Mask_Year_<year_m>.csv
            │
            └───<scenario_2>
                    Mask_Year_<year_0>.csv
                    Mask_Year_<year_n>.csv
```
