# CRAFTY Documentation

Welcome to the CRAFTY documentation.

This `docs/` folder provides a structured guide to **installing**, **running**, and **understanding** the CRAFTY
framework, including a **minimal runnable dataset** and an **example YAML configuration**.

---

## Quick links

- New to CRAFTY? Start here: `user-guide/01-quickstart.md`
- Want to run your first scenario? `user-guide/02-running-scenarios.md`
- Need help with outputs? `user-guide/04-outputs.md`
- Looking for config keys? `reference/03-config-reference.md`
- Preparing input data? `appendices/default-scenario-layout.md` and `appendices/file-formats.md`

---

## Folder structure

```text
CraftyProject/
  README.md
  docs/
    index.md                 (this file)
    user-guide/
      00-overview.md
      01-quickstart.md
      02-running-scenarios.md
      03-common-workflows.md
      04-outputs.md
      05-troubleshooting.md
    reference/
      00-glossary.md
      01-architecture.md
      02-data-model.md
      03-config-reference.md
      04-components/
        services.md
        afts.md
        cells-regions.md
        masks.md
        updaters.md
        outputs.md
    appendices/
      file-formats.md
      default-scenario-layout.md
      minimal-scenario-data-template.md
  examples/
    configs/
      example-config.yaml
    scenarios/
      minimal-scenario/...
```

---

## What to read in what order

### 1) User Guide (task-oriented)
Use these pages when you want to **run** CRAFTY and work with scenarios.

- `user-guide/00-overview.md` — what CRAFTY is, core concepts, and typical workflows
- `user-guide/01-quickstart.md` — the fastest way to run a first simulation (headless or GUI)
- `user-guide/02-running-scenarios.md` — how to select scenarios, set paths, and organise runs
- `user-guide/03-common-workflows.md` — common tasks: calibration, batch runs, parameter changes
- `user-guide/04-outputs.md` — where outputs go and what files you should expect
- `user-guide/05-troubleshooting.md` — common errors and how to fix them

### 2) Reference (technical, component-by-component)
Use these pages when you want to understand **how CRAFTY works internally**.

- `reference/00-glossary.md` — key terms and abbreviations
- `reference/01-architecture.md` — high-level architecture and execution flow
- `reference/02-data-model.md` — core data types: Cells, AFTs, Services, Capitals, Regions
- `reference/03-config-reference.md` — YAML configuration keys (meaning and defaults)
- `reference/04-components/` — deep dives into each component:
  - `services.md`
  - `afts.md`
  - `cells-regions.md`
  - `masks.md`
  - `updaters.md`
  - `outputs.md`

### 3) Appendices (data formats and templates)
Use these when preparing datasets or debugging file discovery/formatting.

- `appendices/default-scenario-layout.md` — a typical “full” dataset layout (CRAFTY‑EU style)
- `appendices/file-formats.md` — CSV conventions and common input/output table formats
- `appendices/minimal-scenario-data-template.md` — template layout for the minimal example dataset

---

## Examples

### Example configuration
- `examples/configs/example-config.yaml` — a commented YAML config showing common options

### Minimal runnable dataset
- `examples/scenarios/minimal-scenario/` — a small dataset you can run quickly to verify setup

This minimal dataset is designed to:
- run fast (good for smoke tests)
- illustrate the standard input folder structure
- generate representative outputs (tables + optional plots)

---

## Contributing to docs

If you update or add a feature, please:
1. update the relevant page in `reference/` (technical)
2. update the relevant page in `user-guide/` (how to use)
3. update `appendices/` if a file format or required input changed
