# Quick Start Guide

This guide will walk you through the complete process of using runtime evidence for AI-assisted development. The workflow consists of two main phases: generating noise-free runtime evidence, and using that data to construct highly contextualized AI prompts.

## Phase 1: Generate Noise-Free Runtime Evidence

Follow this standard execution sequence to collect and process the runtime context data:

> **💡 Concept Link:** This phase embodies the philosophy of understanding systems through actual runtime behavior rather than static analysis, as detailed in our essay: [*Understanding Complex Systems from Scenarios*](./essays/Understanding%20Complex%20Systems%20from%20Scenarios：%20A%20Technical%20Position%20on%20Code%20Context%20in%20the%20AI%20Era.md). By capturing real execution evidence, we strip away irrelevant noise and create a highly focused, reasoning-ready context for AI.

### Step 1: Run the Instrumentation Script

Execute the following command in your terminal to compile, instrument the code, and start the test application:

**Windows (PowerShell):**
```powershell
.\run-instrumentation-demo.ps1
```

**Linux / macOS (Bash):**
```bash
# Make sure the script is executable first: chmod +x run-instrumentation-demo.sh
./run-instrumentation-demo.sh
```

### Step 2: Flush the Runtime Logs

Open your web browser and request the following endpoint to export the logs from memory:

👉 `http://localhost:19898/flush`

- **Locate the Timestamp:** The response will provide the path to the generated log file, e.g., `.\instrumentor-log-20260329_172418.txt`
- **Record:** Take note of the timestamp in the filename (e.g., `20260329_172418`)
- **Note:** Once you have obtained the timestamp, you can safely close or terminate the running `run-instrumentation-demo.ps1` (or `run-instrumentation-demo.sh`) script.

### Step 3: Process the Logs

Pass the extracted timestamp to the processing script using the `-t` parameter. This script will deduplicate logs, prune irrelevant code blocks, structure the data, and restore the source code:

**Windows (PowerShell):**
```powershell
.\process-logs-demo.ps1 -t "20260329_172418"
```

**Linux / macOS (Bash):**
```bash
# Make sure the script is executable first: chmod +x process-logs-demo.sh
./process-logs-demo.sh -t "20260329_172418"
```

### Phase 1 Output

After the processing script completes, it produces noise-free, structured data files specifically optimized for AI consumption:

| Filename | Description |
|----------|-------------|
| `final-output-calltree.md` | Contains internal function call relationships within each thread and the execution timing sequence of code blocks triggered by the specific scenario. |
| `final-output-happensbefore.md` | Contains the "happens-before" relationships among the different threads involved in the scenario. |
| `final-output-combined.md` | A comprehensive file that combines both the call tree and the happens-before relationship data into a single context document. |

> **💡 Concept Link:** The `final-output-happensbefore.md` file embodies the principles discussed in our essay: [*Understanding Multithreaded Behavior: From Inter-Thread Synchronization Dependencies to Single-Thread Problem Reduction*](./essays/Understanding%20Multithreaded%20Behavior%EF%BC%9A%20From%20Inter-Thread%20Synchronization%20Dependencies%20to%20Single-Thread%20Problem%20Reduction.md). By accurately identifying the actual synchronization dependencies (the "happens-before" relationships), this data structurally transforms complex multithreaded analysis into traceable, single-threaded problems, making it significantly easier for AI models to reason about concurrent behaviors.

---

## Phase 2: Generate AI Prompts

Once you have the noise-free data, the next step is to combine it with your feature requirements to create a structured, runtime-grounded prompt for your AI assistant. 

> **💡 Concept Link:** This step is the practical implementation of the progressive construction paradigm discussed in our essay: [*From Core Scenarios to Scenario Expansion*](./essays/From%20Core%20Scenarios%20to%20Scenario%20Expansion：%20A%20Progressive%20Construction%20Paradigm%20Grounded%20in%20Runtime%20Facts.md). By leveraging the clean runtime context of an existing core scenario, you can incrementally and reliably build new features with AI, grounded entirely in actual runtime facts.

### Step 4: Run the Prompt Generator

Run the following interactive Python script in your terminal:

**Windows:**
```powershell
python poc\denoised-data-ai-app\generate_prompt.py
```

**Linux / macOS:**
```bash
python poc/denoised-data-ai-app/generate_prompt.py
```

Follow the on-screen instructions to:
1. Input your new feature requirements.
2. Add any optional constraints.
3. Provide the path to one of the generated output files from Phase 1 (e.g., `final-output-calltree.md` or `final-output-combined.md`).

### Phase 2 Output

The script will output a ready-to-use **`AI_Dev_Prompt.md`**. 

You can now provide this tailored document to your preferred AI assistant (e.g., ChatGPT, Claude) to start the development process with a clean, highly relevant codebase context.