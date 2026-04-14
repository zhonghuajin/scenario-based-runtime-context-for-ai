# runtime-context-for-ai

**Essays, and poc on using runtime evidence to build cleaner code context for AI.**

👉 **[Follow the Workflow Guide](evidence/spring-boot-50021/Workflow%20Guide.md)** to see how it works in practice and run your own tests.
👉 **[Browse the Evidence Archive](evidence/)** to see real-world case studies from open-source projects.

---

## ⚡ Why This Project Exists: Fighting AI Hallucination

**The Problem:** When AI analyzes code, it only sees static structure — files, symbols, and call graphs across the entire codebase. It cannot know which code paths *actually executed* for a specific scenario. So it guesses. This guessing is hallucination — AI reasoning built on assumption rather than fact.

**The Solution:** This project captures **runtime evidence** from real executions and uses it to build **zero-noise, scenario-specific code context** for AI. Instead of flooding the model with the whole codebase, it gives the model only what actually happened — making AI reasoning grounded in facts, not speculation.

### Static Analysis vs. Runtime Evidence

| | Traditional (Static Analysis) | This Project (Runtime Evidence) |
|---|---|---|
| **What AI sees** | Entire codebase — hundreds of files, most irrelevant to the scenario | Only the code paths that *actually executed* for the specific scenario |
| **Context quality** | High noise, low relevance — AI must guess what matters | Zero noise, high relevance — every line in context actually ran |
| **AI reasoning** | Speculative: *"This code might be related..."* | Factual: *"These are the exact paths that executed"* |
| **Hallucination risk** | High — AI fills gaps with plausible-sounding but wrong answers | Low — AI reasons from verified runtime facts |

> **In short:** Runtime evidence turns AI from a guesser into a witness.

---

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

- 🚀 **[Workflow Guide](evidence/spring-boot-50021/Workflow%20Guide.md)** — Step-by-step guide to testing and using runtime evidence based on a real Spring Boot case
- 🔍 **[evidence/](evidence/)** — Case studies from real open-source projects — shows *what the approach can solve*
- 📝 **[essays/](essays/)** — Writing, arguments, and position notes
- 🎮 **[poc/](poc/)** — Small demonstrations
- 🏗️ **[engineering/](engineering/)** — Notes on enterprise-level implementation and production readiness

## Runtime Evidence Archive

The [`evidence/`](evidence/) directory contains case studies from **real open-source projects** where runtime evidence was used to diagnose framework-level issues that static analysis alone could not solve. These are real bugs, confirmed by the upstream project maintainers, where the runtime calltree was the key artifact that revealed the root cause.

*(Note: New case studies are continuously being added to this archive.)*

Each case study includes the raw runtime artifacts (calltree, condition evaluation reports), the denoised context sent to the LLM, and the LLM's diagnostic response.

| # | Case | Bug Category | Key Insight |
|---|------|-------------|-------------|
| 1 | [Spring Boot #50021](evidence/spring-boot-50021/) | Framework-level issue | Calltree revealed the exact execution path and runtime state that caused the issue — details invisible in static code. |
| 2 | [Spring Boot #49951](evidence/spring-boot-49951/) | Auto-configuration ordering | Calltree exposed the precise chain of `@Conditional` evaluations at runtime, explaining unexpected bean registration. |
| 3 | [Spring Boot #49854](evidence/spring-boot-49854/) | Property resolution / binding | Runtime evidence showed the actual `Environment` state and fallback paths taken during property binding. |

→ See the [full evidence archive](evidence/) for details, artifact descriptions, and a reading guide.

## Scope

This repository contains a working proof-of-concept pipeline — 
from source code instrumentation, through runtime log collection 
and denoising, to AI prompt generation — demonstrated against real 
Spring Boot issues.

The [Workflow Guide](evidence/spring-boot-50021/Workflow%20Guide.md) 
walks through the full process step by step, and each case study in 
the [evidence archive](evidence/) includes the complete artifacts 
(calltree, denoised context, and LLM response).

The focus is on validating the methodology, not on providing 
production-ready tooling. Enterprise-level considerations 
(performance, scalability, CI integration) are discussed 
separately in [engineering/](engineering/).

## Status

This is a proven methodology repository.  
It is not presented as a universal solution or a production-ready tool, but as a place to demonstrate and validate a specific engineering direction.

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