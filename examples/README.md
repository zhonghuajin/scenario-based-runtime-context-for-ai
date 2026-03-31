# Examples

This directory contains real, unedited outputs from the pipeline — so you can evaluate the approach without setting up any environment or running any code.

> *The example used here is a simple multithreaded Java program to demonstrate the complete pipeline flow. For complex real-world systems (e.g., Apache ZooKeeper), where static analysis struggles with deep concurrency and cross-module noise, the runtime-evidence approach shows significantly greater advantage. See the [ZooKeeper testing guide](../demos/zookeeper-test/) for applying this pipeline to a real distributed system.*

## Directory Structure

```
examples/
├── denoised_data/          # Pipeline output: noise-free runtime evidence
│   ├── for_ai/             # Markdown format — optimized for LLM consumption
│   └── for_human_beings/   # JSON format — for human inspection and tooling
│
└── prompt_and_response/    # End-to-end AI usage examples
    ├── prompt_template/    # The exact prompts sent to the LLM
    └── llm_response/       # The unedited responses received
```

## 1. Denoised Data

These are the direct outputs of Phase 1 of the pipeline (see [QUICKSTART.md](../QUICKSTART.md)). All unexecuted code branches have been pruned, leaving only the code paths that were actually triggered during the scenario.

### For AI (`for_ai/`)

Structured Markdown files designed to be fed directly into an LLM's context window:

| File | Description |
|------|-------------|
| [`final-output-calltree.md`](denoised_data/for_ai/final-output-calltree.md) | Per-thread function call relationships and execution timing of code blocks. |
| [`final-output-happensbefore.md`](denoised_data/for_ai/final-output-happensbefore.md) | Inter-thread synchronization dependencies (happens-before relationships). |
| [`final-output-combined.md`](denoised_data/for_ai/final-output-combined.md) | Both of the above combined into a single context document. |

### For Human Beings (`for_human_beings/`)

The same data in JSON format, suitable for human reading, programmatic analysis, or integration with other tools:

| File | Description |
|------|-------------|
| [`final-output-calltree.json`](denoised_data/for_human_beings/final-output-calltree.json) | Call tree data in structured JSON. |
| [`final-output-happensbefore.json`](denoised_data/for_human_beings/final-output-happensbefore.json) | Happens-before relationship data in structured JSON. |
| [`final-output-combined.json`](denoised_data/for_human_beings/final-output-combined.json) | Combined data in structured JSON. |

## 2. Prompt Templates & LLM Responses

These demonstrate three concrete AI-assisted engineering tasks, each using the denoised runtime data as context. Every prompt and response is included exactly as-is, with no editing.

| Use Case | What It Does | Prompt | LLM Response |
|----------|-------------|--------|-------------|
| **Secondary Development** | Add a new feature grounded in the existing runtime behavior | [`AI_Dev_Prompt.md`](prompt_and_response/prompt_template/AI_Dev_Prompt.md) | [`AI_Dev_LLM_Response.md`](prompt_and_response/llm_response/AI_Dev_LLM_Response.md) |
| **Bug Localization** | Locate the root cause of a bug using actual execution paths | [`AI_Bug_Localization_Prompt.md`](prompt_and_response/prompt_template/AI_Bug_Localization_Prompt.md) | [`AI_Bug_Localization_LLM_Response.md`](prompt_and_response/llm_response/AI_Bug_Localization_LLM_Response.md) |
| **Code Audit** | Review code quality along the paths that actually executed | [`AI_Code_Audit_Prompt.md`](prompt_and_response/prompt_template/AI_Code_Audit_Prompt.md) | [`AI_Code_Audit_LLM_Response.md`](prompt_and_response/llm_response/AI_Code_Audit_LLM_Response.md) |

## How to Read These Examples

**If you have 2 minutes:** Open any one LLM response file. Notice how the AI's analysis references specific threads, specific call sequences, and specific synchronization points — rather than speculating about what *might* happen.

**If you have 10 minutes:** Open a prompt template and its corresponding LLM response side by side. See how the denoised runtime data in the prompt directly shapes the precision of the AI's output.

**If you want to reproduce this yourself:** Follow the [Quick Start Guide](../QUICKSTART.md) to run the full pipeline on the included demo application.