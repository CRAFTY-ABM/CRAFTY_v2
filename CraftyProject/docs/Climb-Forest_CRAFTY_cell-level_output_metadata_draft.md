# Climb-Forest map viewer metadata: CRAFTY cell-level outputs

## Drafting basis

This draft treats the **cell-level outputs from one CRAFTY scenario** as one dataset series. Before submission, replace every item in square brackets. If Climb-Forest will publish several scenarios or model ensembles separately, either complete one form per scenario/ensemble or agree with Oppla that they will be catalogued as one clearly structured series.

**Proposed dataset title:** CRAFTY cell-level land-use and ecosystem-service simulation outputs - [SCENARIO_NAME], [START_YEAR]-[END_YEAR]

CRAFTY is a spatial, cell-based land-use agent-based model. Its cell snapshot CSV files can contain `ID`, model-grid coordinates `X` and `Y`, the current Agent Functional Type (AFT), and simulated service values. Depending on the output configuration, utility, owner lifetime, capitals, and tax/subsidy variables can also be included. The model normally advances in annual time steps, while the years actually exported for mapping are configurable.

> **Important ingestion requirement:** `X` and `Y` are model-grid indices, not necessarily real-world longitude/easting and latitude/northing. Supply Oppla with the grid geometry or an authoritative cell-ID-to-geometry lookup, coordinate reference system (CRS/EPSG code), geographic extent, cell size, and no-data/mask rules. A PNG alone is not an interrogable geospatial dataset.

## INSPIRE discovery metadata table

| Category | INSPIRE discovery metadata element | Proposed response for the form |
|---|---|---|
| Identification | Resource title | **CRAFTY cell-level land-use and ecosystem-service simulation outputs - [SCENARIO_NAME], [START_YEAR]-[END_YEAR]** |
| Identification | Resource abstract | This dataset series contains spatially explicit outputs from the CRAFTY land-use agent-based model for the [SCENARIO_NAME] scenario over [STUDY_AREA]. For each published simulation year, it reports the simulated Agent Functional Type/land manager occupying each model grid cell and selected ecosystem-service outputs. Depending on the final export configuration, it may also include utility, owner lifetime, capital, and policy tax/subsidy variables. The dataset supports exploration of scenario-dependent changes in land management, forest-related land use, ecosystem-service supply, and spatial trade-offs through time. These are modelled scenario results, not observations or forecasts. |
| Identification | Resource type | **Series** - a time-indexed collection of cell-level spatial snapshot datasets. Use **dataset** instead if the final publication is delivered as one consolidated space-time file. |
| Identification | Resource locator | Dataset landing page/download: **[DATASET_URL]**. Supporting CRAFTY software repository: <https://github.com/m-byari/crafty_policy_challenge_summer_school_26> (software/source context only; do not use this as the data locator unless the final data are actually released there). |
| Identification | Unique resource identifier | **[DOI, repository URI, or UUID assigned to the published dataset]**. No persistent dataset identifier is present in the inspected project repository. |
| Identification | Coupled resource | **Not applicable** because this record describes a dataset series, not a service. If a WMS/WMTS/WFS/OGC API service is later published, link that service record to this dataset identifier. |
| Identification | Resource language | **English** for file headers, configuration, documentation, and category labels; numeric/categorical data are language-neutral. Confirm whether any AFT or scenario labels require a multilingual glossary. |
| Classification of spatial and data services | Topic category | Primary: **environment**. Additional relevant ISO 19115 categories: **biota** and **farming**. |
| Classification of spatial and data services | Spatial data service type | **Not applicable** for a dataset series. For a later map service, likely **view**; for an API/file endpoint, likely **download**. |
| Temporal reference | Temporal extent | Simulation period: **[START_YEAR]-[END_YEAR], inclusive**. Published map years: **[LIST_OF_EXPORTED_YEARS]**. The repository documentation uses annual model steps and provides an illustrative map-year configuration of 2020, 2025, 2030, 2050, and 2100; replace this with the actual Climb-Forest run. |
| Temporal reference | Temporal resolution | **Annual model time step.** Viewer/data-package resolution is **[annual / every N years / selected milestone years]**, according to the years actually exported. |
| Temporal reference | Date of publication | **[YYYY-MM-DD]** - date of first public release in the selected repository. |
| Temporal reference | Date of last revision | **[YYYY-MM-DD]** - date of the most recent published dataset revision. Record a new version when inputs, parameters, software, seed/ensemble, or processing change. |
| Temporal reference | Date of creation | **[YYYY-MM-DD]** - completion date of the model run or final derived geospatial package. Keep the model-run date distinct from the publication date. |
| Quality and validity | Lineage | CRAFTY initialises a cell grid from a baseline land-use/owner map and associates cells with optional regions. The run uses scenario-specific spatial capitals, service-demand trajectories, service utility weights, AFT production/sensitivity and behaviour parameters, policy taxes/subsidies, land-use masks/restrictions, and optional capital degradation or shock layers. At each annual step, year-specific inputs are applied, regional supply-demand and marginal-utility calculations are performed, and cells may undergo abandonment, competition, or land-manager change. Selected model states are exported as UTF-8 comma-separated cell tables and optional PNG previews. The publication package should preserve the exact configuration used, CRAFTY software version or Git commit, input-data versions, random seed and/or ensemble definition, scenario name, export settings, and processing used to attach CRS-aware geometry. |
| Quality and validity | Spatial resolution | **[GRID_CELL_SIZE and unit]**. If the Climb-Forest run uses the documented `CRAFTY-EU-1km` input, report **1 km grid cells**, but verify this against the actual baseline dataset. Also provide **[CRS/EPSG]**, geographic extent, grid origin/alignment, and geometry type. The small example dataset in this repository is a toy grid and does not establish the resolution of the Climb-Forest output. |
| Conformity | Specification | Discovery metadata drafted against **Commission Regulation (EC) No 1205/2008 implementing Directive 2007/2/EC as regards metadata**, using ISO 19115 topic categories. No evidence was found that the output data themselves have been transformed to an INSPIRE data model. Add any Climb-Forest/Oppla delivery profile or OGC specification used for the final viewer package. |
| Conformity | Degree | **Not evaluated**. Change only after formal validation against the named specification. |
| Constraints related to access or use | Conditions applying to access and use | **[FINAL DATA LICENCE AND ATTRIBUTION TEXT]**. Recommended for open publication, subject to input-data rights and partner approval: a standard licence such as **CC BY 4.0** for data and documentation. The inspected repository contains no dataset licence file, so a licence must not be inferred. Document any third-party input licences and whether they constrain redistribution of derived cell outputs. |
| Constraints related to access or use | Limitations on public access | Proposed response if approved for release: **No public-access limitations.** Before using this, confirm that no third-party input licence, confidential scenario assumption, sensitive location, embargo, or partner agreement restricts publication. If restrictions exist, state the legal/contractual reason and release date rather than writing only “restricted”. |
| Organisations responsible | Responsible party | **[LEGAL NAME OF THE CLIMB-FOREST PARTNER THAT PRODUCED AND WILL PUBLISH THE RUN]**, with contributions from **[MODELLING/DATA PARTNERS]**. The code namespace identifies CESR, but the legally responsible dataset organisation must be confirmed rather than inferred from the software package name. |
| Organisations responsible | Responsible party role | Suggested roles: **originator / processor** for the modelling team; **owner** for the partner accountable for the dataset; **publisher** for [REPOSITORY OR PUBLISHING ORGANISATION]; and **point of contact** for [CONTACT NAME/TEAM]. Use only the roles that apply. |
| Metadata on metadata | Metadata point of contact | **[NAME OR TEAM, ORGANISATION, EMAIL]**. Prefer a maintained team mailbox over a personal address if available. |
| Metadata on metadata | Metadata date | **2026-08-05** for this draft; replace with the date on which the submitted metadata record is approved or last updated. |
| Metadata on metadata | Metadata language | **English**. |

## Key messages

### What key messages can the data tell us?

- CRAFTY shows how land-manager/land-use patterns may change spatially under an explicitly defined scenario; it does not claim to predict the future.
- The maps can show where forest-related AFTs persist, expand, contract, or transition to other management types over time.
- Cell-level service layers can reveal spatial co-benefits and trade-offs among outputs such as carbon, timber, food, or other services configured for the Climb-Forest run.
- Regional and time-based comparisons can show how demand, policy assumptions, capitals, and land-use constraints influence simulated outcomes.
- Scenario comparisons can help audiences discuss which assumptions and interventions lead to materially different landscape pathways.

### What are the limitations of the data?

- Results are model simulations conditional on the selected inputs, behavioural rules, parameters, policy assumptions, seed, and scenario. They are not observations, official land-cover products, or deterministic forecasts.
- Uncertainty should be represented using ensembles/sensitivity runs where available; a single run should not be presented as the only plausible future.
- AFTs are model categories and may not correspond one-to-one with conventional land-cover classes. Provide a plain-language legend and class definitions.
- Service values require documented names, units, calibration, and interpretation. Values from different services may not be directly comparable.
- Model-grid `X`/`Y` values require an authoritative georeferencing step before web mapping. Misidentifying grid indices as coordinates would place cells incorrectly.
- The spatial resolution inherits the baseline grid and does not imply matching accuracy. Fine-looking maps must not be interpreted as field-scale truth.
- Published years may be a subset of annual model steps; missing viewer years do not necessarily indicate missing simulation years.
- CRAFTY PNG map colours are scaled to each output year's own minimum and maximum. Colours should not be compared across years unless the viewer applies a fixed cross-year scale.
- Input-data licences, coverage gaps, masks, no-data rules, and model validation should be documented with the final release.

### How could the story be communicated?

- A short animated map or time slider showing forest-related AFT changes from the baseline to selected milestone years.
- A “what changed here?” click interaction showing the cell's AFT history and service trajectories in plain language.
- A side-by-side scenario comparison with a fixed legend and the same colour scale across years.
- A two-minute video explainer: what an AFT is, how CRAFTY makes annual land-use decisions, and why the results are scenarios rather than predictions.
- A guided story with three locations or regions illustrating persistence, transition, and service trade-offs, supported by a chart and short audio/text narrative.
- Downloadable methodology and uncertainty notes, plus a glossary for AFTs, services, capitals, and scenario names.

## Data availability

| Form question | Draft response |
|---|---|
| Where will you host the outputs? | **[CONFIRM HOST]**. Recommended structure: a persistent repository landing page with DOI for the archival data package, plus viewer-optimised files/services operated by Climb-Forest/Oppla. The software GitHub repository is not a substitute for a versioned data repository. |
| How long will you host it for in the long term? | **[CONFIRM RETENTION COMMITMENT]**. Prefer a repository with a stated long-term preservation policy and persistent DOI rather than a temporary project drive. |
| If known, when will the data become outdated? | Simulation results do not expire like monitoring data, but they are **superseded** when a new version changes inputs, scenario definitions, parameters, calibration, software, seed/ensemble design, or geospatial processing. Preserve and label every published version. |
| When will test/sample data be available? | A small toy CRAFTY output is already present in the project repository for technical testing, but it is not Climb-Forest production data. Climb-Forest sample package: **[YYYY-MM-DD]**. |
| When will the final data be available? | **[YYYY-MM-DD or project milestone]**. |

## Information still required before submission

1. Final scenario name, study area, temporal extent, and exact published years.
2. Confirmed cell size, CRS/EPSG code, spatial extent, grid alignment, and geometry/lookup file.
3. Final list of variables, definitions, units, category codes, no-data values, and whether values are totals, densities, or indices.
4. Model version/Git commit, run configuration, random seed or ensemble definition, run-completion date, and input-data versions.
5. Public data landing page, download/service URLs, DOI/UUID, repository version, and publication/revision dates.
6. Approved data licence, attribution statement, third-party input rights, embargoes, and public-access limitations.
7. Responsible organisation, publisher, owner, metadata contact, and maintained email address.
8. Validation, calibration, uncertainty, and fitness-for-use statement for the Climb-Forest run.

## Repository evidence used for this draft

- [CRAFTY outputs overview](1-user-guide/04-outputs.md)
- [PNG map output configuration and interpretation](1-user-guide/04-outputs-map_png_yaml.md)
- [CRAFTY model overview](1-user-guide/00-overview.md)
- [CRAFTY data model](2-reference/02-data-model.md)
- [CSV conventions](3-appendices/file-formats.md)
- [Illustrative 1 km/scenario configuration](4-examples/example-config.yaml)
- [Example cell snapshot for 2020](4-examples/minimal-scenario_data_example/output/minHigh/Default_Run_Output_2026_01_08_13_41/minHigh-Cell-2020.csv)

