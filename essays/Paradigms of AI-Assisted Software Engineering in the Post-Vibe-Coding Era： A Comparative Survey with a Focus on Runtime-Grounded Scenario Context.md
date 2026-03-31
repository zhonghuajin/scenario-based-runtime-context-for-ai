# Paradigms of AI-Assisted Software Engineering in the Post-Vibe-Coding Era: A Comparative Survey with a Focus on Runtime-Grounded Scenario Context

## Abstract

The rapid adoption of Large Language Models (LLMs) in software engineering has triggered a wave of new development paradigms, each attempting to answer a fundamental question: *How should we structure the collaboration between human engineers, AI agents, and complex codebases?* This survey examines the dominant emerging paradigms of 2025–2026—Context Engineering, Spec-Driven Development (SDD), TDD-Driven AI Programming, Harness Engineering, and RAG + Documentation Curation—and compares them with a distinct paradigm we term **Runtime-Grounded Scenario Context (RGSC)**, which argues that the key to unlocking AI's potential in code understanding lies not in better prompts or richer static documents, but in providing LLMs with zero-noise, scenario-specific runtime execution traces and inter-thread synchronization dependencies. We survey recent academic work—most notably TraceCoder (ICSE '26), which demonstrates up to 34.43% improvement through runtime trace-driven debugging—to show that the direction of augmenting LLMs with runtime information has been empirically validated at the highest levels of the software engineering research community. We argue that RGSC occupies a unique and complementary position in the current landscape.

---

## 1. Introduction

The year 2025 saw a significant shift in the use of AI in software engineering—a loose, vibes-based approach gave way to a systematic approach to managing how AI systems process context. In February 2025, Andrej Karpathy coined the term "vibe coding," but the initial excitement quickly gave way to a reckoning: early ventures into vibe coding exposed a degree of complacency about what AI models can actually handle.

This reckoning catalyzed a proliferation of paradigms, each addressing the core tension from a different angle. What all of them share is a recognition that the quality of AI output is fundamentally constrained by the quality of its input. In the evolving landscape of artificial intelligence, one truth is becoming increasingly clear: the quality of your model's input determines the quality of its output.

Yet the paradigms diverge dramatically on *what constitutes good input*. Some focus on static specifications. Others focus on test harnesses. Others on retrieval-augmented knowledge bases. And the RGSC paradigm, as articulated in the essay series under review, argues for something more radical: that for tasks involving understanding existing system behavior—debugging, code review, legacy refactoring, feature expansion—the most valuable input is a **deterministic, scenario-bounded record of what the system actually did at runtime**, stripped of all noise.

This survey organizes the comparison along four analytical dimensions: (1) the nature of the context provided to the LLM, (2) the primary problem domain addressed, (3) the relationship to runtime facts, and (4) the handling of concurrency and multithreading.

---

## 2. The Mainstream Paradigms

### 2.1 Context Engineering

Context Engineering has emerged as the umbrella discipline for the era. Unlike prompt engineering, which focuses on the *how* of asking, context engineering is about the *what*: what data, knowledge, tools, memory, and structure are provided to the model to guide its behavior. Shopify CEO Tobias Lütke popularized the term, calling it "the art of providing all the context for the task to be plausibly solvable by the LLM."

Google's Agent Development Kit (ADK) exemplifies the engineering rigor now being applied: every model call and sub-agent sees the minimum context required. Agents must reach for more information explicitly via tools, rather than being flooded by default. The discipline recognizes that a context window flooded with irrelevant logs, stale tool outputs, or deprecated state can distract the model, causing it to fixate on past patterns rather than the immediate instruction.

Context Engineering is broad and paradigm-agnostic—it defines the *meta-discipline* of input management. The paradigms below can all be viewed as specialized approaches *within* this discipline.

### 2.2 Spec-Driven Development (SDD)

Spec-driven development uses well-crafted software requirement specifications as prompts, aided by AI coding agents, to generate executable code. The workflow typically follows a rigid pipeline: Specify → Plan → Tasks → Implement. Large language models excel at implementation when given clear requirements, and the quality of AI output directly correlates with specification detail and clarity.

GitHub released Spec Kit as an open-source toolkit: instead of coding first and writing docs later, you start with a spec that becomes the source of truth your tools and AI agents use to generate, test, and validate code. However, SDD is not without criticism. Experienced programmers may find that over-formalized specs can cause unnecessary trouble and slow down change and feedback cycles—just as we encountered in the early stages of waterfall development.

### 2.3 TDD-Driven AI Programming

Test-Driven Development has found renewed relevance as a constraining mechanism for AI-generated code. Write a test, watch it fail, then unleash the AI to make it pass. The test provides guardrails—the AI can't wander off into useless territory because the test specifies exactly what's required. TDD provides the fast feedback loops and clear requirements that make AI agents effective, while protecting against the hallucinations and errors that can derail AI-generated code. TDD's minimalist philosophy ("write only what you need to pass the test") counters AI's tendency to over-engineer.

### 2.4 Harness Engineering

If 2025 was the year AI agents proved they could write code, 2026 is the year we learned that the agent isn't the hard part—the harness is. The concept crystallized around OpenAI's Codex team experiment. Harness engineering is the subset of context engineering which primarily involves leveraging harness configuration points to carefully manage the context windows of coding agents. The emphasis is on deterministic constraints: paradoxically, constraining the solution space makes agents more productive, not less. A critical insight from this paradigm is that from the agent's point of view, anything it can't access in-context while running effectively doesn't exist.

### 2.5 RAG + Documentation Curation

Retrieval-Augmented Generation has become infrastructure for enterprise AI. Far from fading into obsolescence as some bold predictions foresaw, RAG has solidified its indispensability as a cornerstone of data infrastructure. For code understanding, RAG retrieves relevant documents, code snippets, or data from external knowledge bases to ground the LLM's output. However, the effectiveness of RAG hinges entirely on the quality of the knowledge base being queried—and the default knowledge base is overwhelmingly *static*.

---

## 3. The Runtime-Grounded Scenario Context (RGSC) Paradigm

The RGSC paradigm, as articulated across the essay series under review, rests on four core positions:

**Position 1: Scenarios, not systems, are the natural unit of understanding.** Rather than trying to comprehend an entire system globally, understanding should be anchored in the lifecycle of a specific external stimulus—from trigger to response. This is not an abstract business category but a real, bounded execution fragment.

**Position 2: Runtime evidence, not static code, is the foundation of accurate analysis.** Static code contains vast amounts of unexecuted branches, irrelevant modules, and theoretical possibilities. Only runtime facts can tell you which code *actually participated* in producing a specific behavior.

**Position 3: Multithreaded complexity can be structurally decomposed through synchronization dependency identification.** Rather than attempting to reason about all possible thread interleavings, the paradigm identifies the actual happens-before synchronization edges that occurred during execution, then reduces the multithreaded problem into a set of single-threaded causal tracing subproblems.

**Position 4: Zero-noise, structured runtime context is the precondition for effective AI-assisted code understanding.** The paradigm argues that AI failure in code analysis is rarely a capability problem—it is a context quality problem. By feeding LLMs pruned execution traces and explicit thread dependency graphs instead of raw static code, the problem shifts from guessing to reasoning.

The engineering realization of this paradigm addresses the instrumentation overhead problem through aggressive deduplication (recording each code block only once per thread per scenario), scenario-bounded lifecycles (clearing state after each scenario completes), and reducing all runtime instrumentation to integer-queue operations with offline dictionary reconstruction.

---

## 4. Comparative Analysis

### 4.1 Nature of Context

The paradigms differ fundamentally in *what* they provide to the LLM. SDD provides **prescriptive static text**—specifications written before code exists. TDD provides **executable behavioral contracts**—tests that define what success looks like. Harness Engineering provides **environmental constraints**—rules, linters, CI checks, and documentation that bound the agent's solution space. RAG provides **retrieved static knowledge**—code snippets, documentation, and historical artifacts fetched by semantic similarity.

RGSC provides something categorically different: **factual execution evidence**—the exact code blocks that ran, the exact synchronization edges between threads, the exact causal chain of a specific scenario, with all unexecuted code pruned away. This is not prescriptive (what *should* happen) nor retrieved (what *might be* relevant), but empirical (what *actually did* happen).

### 4.2 Primary Problem Domain

SDD, TDD, and Harness Engineering are primarily **forward-engineering paradigms**—they address the problem of *building new software* with AI assistance. Their context is designed to guide code generation.

RGSC is primarily a **reverse-engineering and understanding paradigm**—it addresses the problem of *understanding existing system behavior* with AI assistance. Its applications include bug localization, code review, legacy system refactoring, and scenario-based feature expansion. The two concerns are complementary, not competitive.

### 4.3 Relationship to Runtime Facts

This is where RGSC diverges most sharply from all other paradigms. SDD operates entirely in the static, pre-execution world. TDD uses runtime feedback (test pass/fail) but only as a binary signal—it does not provide the LLM with the internal execution path. Harness Engineering may incorporate some dynamic signals (CI results, linter output) but treats them as external feedback loops rather than as primary context. RAG retrieves from static knowledge bases by default.

RGSC makes runtime evidence the *primary and irreducible* input. The paradigm's central claim is that for understanding specific behaviors in complex systems, static information is necessary but fundamentally insufficient.

This distinction is powerfully illustrated by TraceCoder (Huang et al., ICSE '26), which demonstrates the concrete consequences of the gap between binary pass/fail feedback and fine-grained runtime traces. As the authors observe, "most existing self-correction methods operate as 'black-boxes', relying solely on pass/fail feedback from a test suite. This approach, which lacks insight into the program's internal execution, suffers from significant limitations." Their framework addresses this by instrumenting code with diagnostic probes to capture runtime traces, then conducting causal analysis on those traces—a workflow that directly embodies the RGSC principle of grounding AI reasoning in runtime facts rather than static speculation.

### 4.4 Handling of Concurrency and Multithreading

This is perhaps the most distinctive contribution of the RGSC paradigm. None of the mainstream paradigms explicitly address the structural complexity of multithreading in their context strategies. SDD specs do not capture thread interleavings. Tests verify outcomes but cannot expose the causal structure of concurrent execution. Harnesses constrain agent behavior but do not model the concurrency of the *target system*.

RGSC explicitly addresses this gap by capturing actual synchronization dependencies (the happens-before relationships between threads) and using them to decompose the multithreaded understanding problem into single-threaded causal tracing subproblems. This structural decomposition is presented as essential for making AI-assisted analysis of concurrent systems tractable.

---

## 5. Academic Validation: Runtime Information Improves LLM Reasoning About Code

The central thesis of RGSC—that providing LLMs with runtime execution information fundamentally improves their ability to reason about code—has received growing and increasingly rigorous academic validation.

### 5.1 TraceCoder (ICSE '26): The Strongest Direct Evidence

The most compelling and directly relevant validation comes from **TraceCoder** (Huang et al., 2026), published at ICSE '26—the flagship conference in software engineering. TraceCoder presents "a collaborative multi-agent framework that emulates the observe-analyze-repair process of human experts," which "first instruments the code with diagnostic probes to capture fine-grained runtime traces, enabling deep insight into its internal execution. It then conducts causal analysis on these traces to accurately identify the root cause of the failure."

The paper's motivation aligns almost verbatim with the RGSC paradigm's diagnosis of the problem. The authors identify two fundamental limitations in current LLM-based self-debugging methods: "1) they rely on binary final execution results, ignoring the rich semantics in intermediate execution states, which leads to imprecise fault localization; and 2) they adopt a stateless repair paradigm, unable to learn from historical debugging knowledge to avoid repeating past mistakes." This is precisely the RGSC paradigm's argument that pass/fail signals are insufficient and that AI needs the *internal execution path*—the factual causal chain—to reason effectively.

TraceCoder's empirical results are striking. The framework achieves up to a **34.43% relative improvement** in Pass@1 accuracy over existing advanced baselines on class-level code generation benchmarks. But the most relevant evidence for the RGSC thesis comes from the **ablation study**: when the Instrumentation Agent (responsible for capturing runtime traces) is removed, accuracy drops from 89.04% to 78.51% on BigCodeBench. When both Instrumentation and Analysis agents are removed—leaving only raw pass/fail feedback—accuracy drops further to 75.09%. And when the entire iterative repair loop is eliminated, accuracy collapses to 53.77%. This ablation data provides a rigorous, controlled demonstration that runtime trace information is not merely helpful but **structurally essential** for effective AI-assisted debugging.

The paper's case study further illustrates how runtime traces transform AI debugging from guessing to causal reasoning. Given a subtle off-by-one error (using `>= 0` instead of `> 0` for filtering positive numbers), a black-box method that only sees the final assertion error "might try various incorrect fixes... without understanding that the core issue lies in the filtering logic itself." But TraceCoder's instrumentation reveals the exact runtime behavior—showing that `0` satisfies the predicate and is appended—enabling the Analysis Agent to perform precise causal localization and generate a targeted fix.

TraceCoder also validates the RGSC paradigm's emphasis on **noise reduction**. Its Instrumentation Agent follows strict principles including "Instrumentation Purity" (only non-invasive print statements, no modification of computational logic) and "failure-aware instrumentation by prioritizing regions relevant to the observed failure, thereby avoiding indiscriminate logging." This mirrors RGSC's insistence on scenario-bounded, zero-noise context.

### 5.2 Further Corroborating Evidence

Several other recent papers reinforce the same direction from complementary angles.

**CRANE-LLM** (arXiv:2602.18537) augments large language models with structured runtime information extracted from the notebook kernel state to detect and diagnose crashes in ML notebooks. Across three state-of-the-art LLMs, runtime information improves crash detection and diagnosis by 7–10 percentage points in accuracy and 8–11 in F1-score. The paper finds that these improvements depend on "the integration of complementary categories of runtime information, such as structural execution context, object types, and value-level data attributes, rather than relying on any single source."

**RecovSlicing** (arXiv:2508.18721) demonstrates that combining partial runtime traces with LLM reasoning achieves significantly higher accuracy (80.3%, 91.1%, 98.3%) and recall (up to 98.3%) than the best baseline for computing dynamic data dependencies.

**InspectCoder** (arXiv:2510.18327) provides particularly striking evidence for the *indispensability* of actual runtime information. Its experiments reveal a "clear performance hierarchy" where methods with actual program execution achieve greater improvements than those that merely simulate execution traces statically. When LLMs try to simulate debugger outputs instead of using actual runtime data, resolve rates drop by 8.01% and 4.64%, with debug time increasing dramatically—"demonstrating that current LLMs cannot reliably simulate dynamic program behavior, making actual debugger integration indispensable."

Earlier foundational work on **REval** (arXiv:2403.16437) directly argues that "runtime behaviors (e.g., program state and execution path) are essential for program understanding and reasoning for humans; in the meantime, it has proved to be effective for an in-depth understanding of code semantics for language models as well." Their evaluation finds that most LLMs show "unsatisfactory performance on Runtime Behavior Reasoning (i.e., an average accuracy of 44.4%)," highlighting both the importance and difficulty of this capability.

### 5.3 Synthesis

Taken together, these results form a consistent and compelling body of evidence. From ICSE '26 (TraceCoder) to empirical studies across multiple venues, the academic community has converged on a finding that directly validates the RGSC paradigm's central claim: **LLMs reasoning about code are fundamentally improved when provided with runtime execution evidence, and static code analysis alone is insufficient for complex program understanding tasks.** The gap is not incremental—TraceCoder's ablation shows it is structural, with runtime traces alone accounting for over 10 percentage points of accuracy difference.

---

## 6. Positioning RGSC in the Landscape

The relationship between RGSC and the mainstream paradigms is best understood as **complementary and layered**, not competitive.

**Context Engineering** is the overarching discipline; RGSC can be viewed as a specialized approach to context engineering that sources its context from runtime execution facts rather than static documents or human-authored specifications. Where the general context engineering literature focuses on managing prompt structure, memory, tool outputs, and retrieved documents, RGSC contributes a novel *source of context*—the pruned, scenario-specific runtime trace.

**SDD and TDD** are forward-engineering paradigms that define *what to build*. RGSC is a reverse-engineering paradigm that reveals *what actually happened*. In the RGSC essay on scenario expansion, the paradigm bridges into forward engineering: once a clean runtime context of an existing scenario is captured, new features are built as differential extensions of that verified foundation—blending understanding with construction.

**Harness Engineering** designs the *environment* in which AI agents operate. RGSC designs the *input data* that AI receives for analysis tasks. The two are complementary: a harness could incorporate RGSC-style runtime context as a first-class data source for agents performing code review, debugging, or refactoring. TraceCoder's architecture—where the Instrumentation Agent feeds runtime traces to the Analysis Agent, which then guides the Repair Agent—can be viewed as a concrete instance of a harness that integrates runtime-grounded context into an agent workflow.

**RAG** retrieves from a knowledge base that is typically static. RGSC could enhance RAG by populating the knowledge base with structured runtime traces indexed by scenario, transforming RAG from a static-document retrieval system into a *dynamic-behavior retrieval system*.

Industrial runtime sensing has independently validated the value of runtime context for AI-assisted development, while also highlighting the design tradeoffs involved. Hud, which raised $21 million in funding and deployed its "runtime code sensor" across over one million services by late 2025, continuously collects function-level aggregate statistics—call counts, execution times, error rates—in production environments with only 1–2% overhead, exposing this data to AI coding agents via MCP. Hud's commercial success demonstrates that the industry has recognized the insufficiency of static code alone for grounding AI in production reality. However, Hud and RGSC occupy opposite ends of the runtime context spectrum. Hud trades determinism for production-safety, collecting aggregate statistical summaries across many executions; RGSC trades production-deployability for determinism, capturing exact execution paths and synchronization dependencies within a single scenario execution. Hud answers "How has this function been behaving overall?"; RGSC answers "What exactly happened in this specific execution, and why?" The two are complementary rather than competing: Hud can identify which functions are problematic in production, while RGSC can reveal why a specific scenario produces a specific behavior—including the causal structure of concurrent thread interactions that no amount of aggregate statistics can reconstruct.

Taken together with the academic evidence surveyed in Section 5, the emergence of Hud suggests that the value of runtime context for AI-assisted software engineering is being validated simultaneously from two directions—empirically in research and commercially in industry. Within this validated direction, the RGSC paradigm's unique contributions lie in three areas where neither mainstream paradigms nor industrial runtime sensors currently operate: (1) providing deterministic, scenario-bounded execution paths at basic-block granularity as LLM input, (2) explicitly resolving multithreaded synchronization dependencies into tractable single-thread causal tracing subproblems, and (3) addressing the instrumentation overhead barrier through aggressive temporal/spatial deduplication and integer-based minimal-cost recording—engineering choices that accept a test/pre-production deployment constraint in exchange for the zero-noise, causally complete context that production-safe statistical approaches must sacrifice.

It is worth noting that while TraceCoder validates the *direction* of runtime-grounded AI reasoning, it operates at the level of individual functions and classes. The RGSC paradigm extends this direction to **production-scale complex systems** with multithreading, cross-service calls, and scenario lifecycles spanning multiple components—a significantly more challenging domain where the problems of noise explosion, instrumentation overhead, and thread interleaving are far more acute. RGSC's engineering contributions (temporal/spatial deduplication, integer-queue instrumentation, scenario-bounded lifecycle management, synchronization dependency capture) address precisely the scalability barriers that would arise when applying TraceCoder-style thinking to real-world systems.

---

## 7. Limitations and Open Questions

RGSC requires a running system and the ability to trigger specific scenarios. For systems still under initial design, for behaviors that cannot be reliably triggered, or for systems whose runtime environments are inaccessible, the paradigm's foundations are not available.

The coverage of synchronization mechanisms directly determines the completeness of the analysis. If some form of inter-thread communication is not instrumented, its influence becomes a blind spot. The paradigm is honest about this boundary but does not yet propose a solution.

The paradigm has not yet been empirically validated through controlled experiments at the full-system scale. The academic papers cited above validate the *direction* (runtime information helps LLMs) at the function and class level. The specific RGSC pipeline—scenario-bounded deduplication, integer-based instrumentation, offline dictionary reconstruction, structured synchronization topology—remains to be tested end-to-end on production-scale multithreaded systems.

---

## 8. Conclusion

The AI-assisted software engineering landscape in 2025–2026 is converging on a shared recognition: **context quality, not model capability, is the binding constraint.** After years of the industry assuming progress in AI is all about scale and speed, we're starting to see that what matters is the ability to handle context effectively.

Within this convergence, the mainstream paradigms address context quality through static specifications (SDD), executable tests (TDD), environmental constraints (Harness Engineering), and retrieved knowledge (RAG). Each has proven value, particularly for forward-engineering tasks.

The RGSC paradigm contributes a fundamentally different perspective: that for the equally critical tasks of *understanding, diagnosing, auditing, and refactoring* existing complex systems, the most powerful context is not authored by humans or retrieved from documents—it is **captured from reality itself**. By recording the exact execution paths and inter-thread synchronization dependencies of specific scenarios, stripping away all noise, and structuring the result for LLM consumption, RGSC creates what might be called *empirical context*—context grounded in undeniable facts about what the system actually did.

Academic research increasingly validates this direction with growing rigor. TraceCoder's publication at ICSE '26—demonstrating up to 34.43% improvement through runtime trace-driven debugging and showing through ablation that runtime instrumentation accounts for over 10 points of accuracy—represents a landmark validation from the software engineering community's most prestigious venue. Combined with corroborating results from CRANE-LLM, RecovSlicing, InspectCoder, and REval, the evidence suggests that the gap between what LLMs can do with static code and what they can do with runtime-grounded context is not incremental—it is structural.

As systems grow more complex, as concurrency becomes ubiquitous, and as AI becomes more deeply embedded in the software lifecycle, the question of *how to organize runtime reality into reasoning-ready context* will only grow in importance. The RGSC paradigm offers a coherent, principled answer to this question—extending the academically validated direction of runtime-augmented AI reasoning to the full complexity of production-scale multithreaded systems. It deserves to be taken seriously alongside the mainstream paradigms—not as a replacement, but as the missing layer that grounds AI-assisted software understanding in fact.

---

### References

- **TraceCoder**: Huang, J., Ye, W., Sun, W., Zhang, J., Zhang, M., & Liu, Y. (2026). "TraceCoder: A Trace-Driven Multi-Agent Framework for Automated Debugging of LLM-Generated Code." In *Proceedings of the IEEE/ACM 48th International Conference on Software Engineering (ICSE '26)*. arXiv:2602.06875v1.
- **CRANE-LLM**: Wang et al. (2026). "Runtime-Augmented LLMs for Crash Detection and Diagnosis in ML Notebooks." arXiv:2602.18537.
- **RecovSlicing**: Pei et al. (2025). "LLM as an Execution Estimator: Recovering Missing Dependency for Practical Time-travelling Debugging." arXiv:2508.18721.
- **InspectCoder** (2025). "Dynamic Analysis-Enabled Self Repair through Interactive LLM-Debugger Collaboration." arXiv:2510.18327.
- **REval** (2024). "Evaluating Large Language Models with Runtime Behavior of Program Execution." arXiv:2403.16437.
- OpenAI Codex Team (2026). "Harness engineering: leveraging Codex in an agent-first world." openai.com.
- GitHub (2025). "Spec-driven development with AI: Get started with a new open source toolkit." github.blog.
- Thoughtworks (2025). "From vibe coding to context engineering: 2025 in software development." technologyreview.com.
- Hud (2025). "Runtime Code Sensor for Production-Safe AI Code." hud.io. See also: PR Newswire, "Hud Ships First Runtime Code Sensor to Bring Production Reality to Code Generation," December 2025.
