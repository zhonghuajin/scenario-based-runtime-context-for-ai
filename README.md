# runtime-context-for-ai

**Essays, and demos on using runtime evidence to build cleaner code context for AI.**

👉 **[Read the Quick Start Guide](QUICKSTART.md)** to see how it works in practice.
👉 **[Browse the Examples](examples/)** to see real pipeline outputs and AI responses without running anything.

## Overview

This repository explores a simple but important question:

> Can runtime evidence help construct cleaner and more relevant code context for AI?

Modern code understanding often defaults to static structure: files, symbols, call graphs, and repository-wide search.  
But many real engineering tasks are about explaining a **specific runtime behavior**:

- why a request produced a result
- why a failure occurred
- which code paths actually executed
- which parts of the codebase are relevant to that behavior

This repository explores the idea that, for these tasks, context quality may improve when it is grounded in **runtime facts** rather than assembled only from static proximity.

## Focus

The work collected here centers on a few themes:

- **scenario-first understanding**  
  Start from a concrete behavior rather than from the whole system.

- **runtime-grounded relevance**  
  Use execution evidence to identify which code actually mattered.

- **context reduction**  
  Remove theoretically related but behaviorally irrelevant code.

- **AI-oriented context building**  
  Organize code context in a form that is easier for models to reason about.

## Contents

- 🚀 **[QUICKSTART.md](QUICKSTART.md)** — Step-by-step guide to generating and using runtime evidence
- 📂 **[examples/](examples/)** — Real pipeline outputs and LLM responses you can inspect immediately
- `essays/` — writing, arguments, and position notes
- `demos/` — small demonstrations
- `engineering/` — notes on enterprise-level implementation and production readiness

## Examples: See the Output Without Running Anything

The [`examples/`](examples/) directory contains real, unedited artifacts produced by this pipeline, so you can evaluate the approach without setting up any environment.

### Denoised Runtime Data

The raw output of the pipeline — noise-free, scenario-specific runtime evidence in two formats:

| Directory | Purpose |
|---|---|
| [`examples/denoised_data/for_ai/`](examples/denoised_data/for_ai/) | Structured Markdown files optimized for LLM consumption (`calltree`, `happensbefore`, `combined`) |
| [`examples/denoised_data/for_human_beings/`](examples/denoised_data/for_human_beings/) | JSON format for human inspection and tooling integration |

### Prompt Templates & LLM Responses

Three concrete use cases, each with the exact prompt sent to the LLM and the unedited response received:

| Use Case | Prompt | LLM Response |
|---|---|---|
| **Secondary Development** | [`AI_Dev_Prompt.md`](examples/prompt_and_response/prompt_template/AI_Dev_Prompt.md) | [`AI_Dev_LLM_Response.md`](examples/prompt_and_response/llm_response/AI_Dev_LLM_Response.md) |
| **Bug Localization** | [`AI_Bug_Localization_Prompt.md`](examples/prompt_and_response/prompt_template/AI_Bug_Localization_Prompt.md) | [`AI_Bug_Localization_LLM_Response.md`](examples/prompt_and_response/llm_response/AI_Bug_Localization_LLM_Response.md) |
| **Code Audit** | [`AI_Code_Audit_Prompt.md`](examples/prompt_and_response/prompt_template/AI_Code_Audit_Prompt.md) | [`AI_Code_Audit_LLM_Response.md`](examples/prompt_and_response/llm_response/AI_Code_Audit_LLM_Response.md) |

These examples demonstrate how zero-noise runtime context changes the quality of AI reasoning — from vague, speculative analysis to precise, causally grounded understanding.

## Scope

What is shared here is intentionally limited to concepts, reasoning, and lightweight demos. A working implementation covering the full pipeline — from runtime evidence collection through context assembly to model consumption — exists but is not included here. This repository is focused on the public discussion of the ideas themselves.

## Status

This is an early-stage exploratory repository.  
It is not a complete framework or product, but a place to develop and test an engineering direction.

## Non-goals

This repository does not aim to present:

- a universal solution
- a finalized architecture
- proprietary implementation details
- production-ready tooling

## Why it matters

As AI becomes more involved in software engineering, one important bottleneck is no longer only model capability, but **context quality**.

In many cases, better results may come not from giving models more code, but from giving them:

- cleaner boundaries
- less noise
- stronger relevance
- closer alignment with what actually happened at runtime

## Contact

Thoughts, questions, or ideas are welcome. 

*(Note: As my English speaking and listening skills are limited, I strongly prefer text-based communication. Thank you for understanding!)*

- ✉️ Email: zhonghuajin79@gmail.com
- 💬 WhatsApp: 62655442
- 💬 WeChat: 58628428

**Holiday Notice:**  
During Hong Kong public holidays, I am usually in Mainland China spending time with my family. If you need to contact me via phone during these periods, please send an SMS to **`+86 18602045865`** first to explain your intent before making a call.

## License

[MIT License](LICENSE) © 2026 钟华锦.