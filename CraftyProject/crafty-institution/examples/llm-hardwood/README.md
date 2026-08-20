# Minimal LLM institution example

This directory is configured for the EU project data and assigns German cells
(`DE`) to the `example` paradigm. `institutions.yaml` uses that same paradigm
name in its cell scope.

Point the main CRAFTY configuration at this directory:

```yaml
institutions_directory: 'C:\Users\byari-m\Desktop\institutions\llm_example'
llm_provider: openai
llm_model_name: gpt-5.4-nano
llm_api_key: '<your-key>'
```

Run this Eclipse main class:

```text
de.cesr.crafty.institution.runner.InstitutionRunner
```

with:

```text
--config-file "C:\Users\byari-m\Desktop\Advence-config.yaml"
```

The API key belongs in the main CRAFTY configuration or another secure runtime
configuration mechanism; do not put it in this example directory.
