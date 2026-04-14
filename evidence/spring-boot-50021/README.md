# Case Study: Spring Boot #50021

## Bug Summary

**Issue:** [spring-projects/spring-boot#50021](https://github.com/spring-projects/spring-boot/issues/50021)

This issue reports a framework-level bug in Spring Boot where the runtime behavior deviates from what the static code structure would suggest. Static analysis alone cannot reveal the root cause because the problem lies in **which code paths actually execute and what runtime state they encounter** — information that is invisible without execution evidence.

## What Runtime Evidence Revealed

By instrumenting the Spring Boot source code and running a minimal reproducer, the runtime calltree captured the **exact execution path** leading to the failure. Key observations:

- The calltree showed which beans were actually created, in what order, and through which factory methods
- The happens-before trace revealed the precise sequence of framework-internal callbacks that led to the incorrect state
- These details are **not deducible from reading the source code alone** — they depend on runtime configuration, classpath scanning results, and conditional evaluation

This denoised runtime context was then fed to an LLM, which successfully identified the root cause and proposed a fix.

## AI Diagnostic Result

The LLM's analysis, based solely on the runtime evidence provided, produced a fix whose **core remediation logic aligns exactly** with the official Spring Boot commit:

**Official Fix:** [`edcc937`](https://github.com/spring-projects/spring-boot/commit/edcc937adde0f168e3e9781ba19c6e923864f9b9)

## Directory Structure

```
spring-boot-50021/
│
├── 1_runtime_evidence/            ← Start here: the denoised runtime artifacts
│   ├── calltree.md                    Execution call tree (compact, used in AI prompt)
│   ├── calltree.json                  Execution call tree (structured, for human inspection)
│   ├── combined.md                    Combined runtime context (compact)
│   ├── combined.json                  Combined runtime context (structured)
│   ├── happensbefore.md               Happens-before ordering (compact)
│   └── happensbefore.json             Happens-before ordering (structured)
│
├── 2_ai_prompt/                   ← The exact prompt sent to the LLM
│   └── AI_Bug_Localization_Prompt.md
│
├── 3_ai_response/                 ← The LLM's diagnostic output
│   └── Bug_Localization_and_Fix_Plan.md
│
├── reproducer/                    ← Minimal project to reproduce the bug
│   ├── src/
│   └── pom.xml
│
└── Workflow Guide.md              ← Full step-by-step reproduction instructions
```

## How to Read This Case Study

| Your goal | Where to go |
|-----------|-------------|
| **See what the AI received** | Read [`1_runtime_evidence/calltree.md`](1_runtime_evidence/calltree.md) — this is the core artifact that made accurate diagnosis possible |
| **See what the AI produced** | Read [`3_ai_response/Bug_Localization_and_Fix_Plan.md`](3_ai_response/Bug_Localization_and_Fix_Plan.md) — the LLM's full diagnostic output |
| **Understand the full prompt** | Read [`2_ai_prompt/AI_Bug_Localization_Prompt.md`](2_ai_prompt/AI_Bug_Localization_Prompt.md) |
| **Compare with the official fix** | Read the LLM's proposed fix, then compare with commit [`edcc937`](https://github.com/spring-projects/spring-boot/commit/edcc937adde0f168e3e9781ba19c6e923864f9b9) |
| **Reproduce everything yourself** | Follow the [Workflow Guide](Workflow%20Guide.md) |

## Key Takeaway

> Static analysis sees the **entire codebase** and must guess which parts matter.
> Runtime evidence sees **only what actually executed** — and guessing becomes unnecessary.
>
> In this case, that difference was the gap between "plausible speculation" and "correct root cause."
