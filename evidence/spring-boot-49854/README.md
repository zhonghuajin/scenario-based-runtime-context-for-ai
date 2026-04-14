# Case Study: Spring Boot #49854

## Bug Summary

**Issue:** [spring-projects/spring-boot#49854](https://github.com/spring-projects/spring-boot/issues/49854)

This issue reports a framework-level bug in Spring Boot where the runtime behavior deviates from what the static code structure would suggest. Static analysis alone cannot reveal the root cause because the problem lies in **which code paths actually execute and what runtime state they encounter** — information that is invisible without execution evidence.

## What Runtime Evidence Revealed

By instrumenting the Spring Boot source code and running a minimal reproducer, the runtime calltree captured the **exact execution path** leading to the failure. Key observations:

- The calltree showed which beans were actually created, in what order, and through which factory methods
- The happens-before trace revealed the precise sequence of framework-internal callbacks that led to the incorrect state
- These details are **not deducible from reading the source code alone** — they depend on runtime configuration, classpath scanning results, and conditional evaluation

This denoised runtime context was then fed to an LLM, which successfully identified the root cause and proposed a fix.

## Directory Structure

```
spring-boot-49854/
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
| **Reproduce everything yourself** | Follow the [Workflow Guide](Workflow%20Guide.md) |

## Key Takeaway

> Static analysis sees the **entire codebase** and must guess which parts matter.
> Runtime evidence sees **only what actually executed** — and guessing becomes unnecessary.
>
> In this case, that difference was the gap between "plausible speculation" and "correct root cause."


## Before / After: Two Approaches to the Same Bug

### Context: Why This Bug Matters as a Test Case

This comparison was conducted on **April 12, 2026**. At this point in time, the bug — a `@WebMvcTest` failure in Spring Boot 4.0.5 caused by auto-configuration ordering — was very recent. No detailed analysis, root cause explanation, or fix had been published online. This means the AI could not have retrieved a ready-made answer from its training data or from the web. Both approaches below represent the AI reasoning from first principles, not recalling a known solution. The only difference is what context the AI was given to reason with.

### Background

In Spring Boot 4.0.5, `@WebMvcTest` slice tests fail because an auto-configuration ordering issue prevents the `DispatcherServletPath` bean from being registered before the security auto-configuration evaluates its `@ConditionalOnBean` condition. We gave the same bug to two different approaches and compared the outputs.

### Before: No Runtime Evidence — Pure AI Reasoning

The issue description and relevant source code were provided to an AI (Claude), which reasoned based on static information and framework experience.

| Dimension | Observation |
|---|---|
| **Reasoning approach** | Experience-based guessing grounded in framework conventions and historical bug patterns |
| **Number of conclusions** | 3 possible fix directions offered, leaving the human to judge which is correct |
| **Certainty** | Hedged language throughout: "most likely", "probably", "less likely but worth mentioning" |
| **Causal chain** | None. Only a high-level summary of the problem nature, no step-by-step trace from symptom to root cause |
| **Localization precision** | Direction-level — identifies that an ordering annotation likely needs to change, but does not pinpoint the exact file, annotation attribute, or metadata entry |
| **Directly actionable** | No. The output is an analytical report, not a submittable fix |

### After: Runtime Evidence — AI Reasoning Grounded in Facts

The project's pipeline captured actual execution data from the `@WebMvcTest` scenario, denoised it, built scenario-specific code context, and provided it to the AI.

| Dimension | Observation |
|---|---|
| **Reasoning approach** | Backward causal tracing grounded in runtime facts |
| **Number of conclusions** | Exactly 1 deterministic conclusion |
| **Certainty** | No hedging. Every step is backed by runtime evidence |
| **Causal chain** | Complete. Traces from `SpringApplication.run()` exception → `OnBeanCondition.getMatchingBeans()` match failure → `AutoConfigurationSorter.sortByAnnotation()` finding no ordering declaration → `AutoConfigurationMetadataLoader` returning no metadata entry, step by step |
| **Localization precision** | Line-level — pinpoints that `MockMvcAutoConfiguration.java`'s `@AutoConfiguration` annotation needs `before = ServletWebSecurityAutoConfiguration.class`, and specifies the exact entry to add in `spring-autoconfigure-metadata.properties` |
| **Directly actionable** | Yes. The output includes ready-to-apply code changes and verification logic |

### Honest Assessment

This bug has a **single causal chain and a small search space** — an experienced engineer or AI could likely guess the correct direction even without runtime evidence. Both approaches ultimately pointed to the same fix strategy.

The differences show up in three areas:

**From "probably right" to "certainly right".** Without runtime evidence, the AI hedges by listing multiple candidate solutions to cover its uncertainty. With runtime evidence, the AI produces a single conclusion because the causal chain is closed and there is no uncertainty left to hedge against.

**From "direction" to "implementation".** Without runtime evidence, the AI output is analytical — it tells you the general approach, but the specific file, code, and configuration changes are left for you to work out. With runtime evidence, the AI output is executable — it specifies exactly what to modify and where.

**From "unverifiable" to "verifiable".** Without runtime evidence, the AI cannot explain why a fix will work, because it has no visibility into the internal execution path. With runtime evidence, the AI can articulate exactly why the fix is correct — it can trace through the sorting algorithm's behavior and explain how the condition evaluation timing changes after the fix is applied.

However, it also clearly demonstrates that even with zero knowledge of Spring Boot's internal implementation mechanisms, there is a more reliable method than relying on AI for pure static analysis to obtain a solution—one that can be effectively used to fix bugs in large-scale projects like Spring Boot.