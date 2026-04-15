#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import sys

# ==========================================
# 1. Define Prompt Templates
# ==========================================
# Full template with concurrency analysis
FULL_PROMPT_TEMPLATE = """# Code Bug Localization and Root Cause Analysis Task

You are a senior software architect and debugging expert. Based on the provided zero-noise runtime trace data and synchronization dependencies, please help me perform deterministic factual backtracking to locate the root cause of a bug.

---

## 📋 Bug Symptom & Context

**🐞 Observable Symptom / Anomaly**: 
{bug_symptom}

**🛠️ Tech Stack Context**: 
{tech_stack}

**💬 Additional Notes (Suspected variables, specific thread IDs, etc.)**: 
{additional_info}

---

## 🔍 Zero-Noise Scenario Runtime Data

The following data comes from real system runtime trace logs. It is a "zero-noise" factual record of the specific execution scenario that triggered the bug. It contains:
1. **Call Tree**: The exact sequence of executed basic blocks, pruned source code, and method signatures. Unexecuted branches are entirely removed.
2. **Happens-Before & Data Races (If applicable)**: Explicit synchronization edges and unsynchronized concurrent accesses between threads.
3. **Important Premise**: Please reason entirely based on this factual data. **Do not guess or fabricate** execution paths. If a piece of code is not in the data, it did not execute.

### ✅ [Runtime Evidence] Complete Execution Data
=========================================
{trace_data}
=========================================

---

## 🎯 Diagnostic Requirements

Please act as a factual detective. **Adhere strictly to the following analysis priority**:

1. **Call‑Tree‑First Principle**: Always begin your backtracking using the **Call Tree** evidence.
   - Trace the exact sequence of executed basic blocks backward from the symptom anchor.
   - Only if the call tree alone fails to explain the observed anomaly (e.g., the executed path appears logically correct but still produces wrong output), **then** consult the `Happens‑Before` and `Data Races` sections.
   - **Never assume a concurrency issue unless the trace data explicitly shows a missing synchronization edge or a stale read from a data race.**

2. **Symptom Anchor**: 
   - Locate the exact block or method in the trace data where the symptom manifested (e.g., the exception point or the final incorrect read).

3. **Factual Backtracking**: 
   - Trace the data flow and execution path backward from the symptom anchor.
   - If multithreading is involved, strictly check the `Happens-Before` and `Data Races` sections. Did a thread read stale data because a synchronization edge was missing? Was there an unexpected interleaving?

4. **Root Cause Identification**:
   - Pinpoint the exact file, function, and logical flaw that caused the execution state to diverge from expectations.

---

## ⚠️ Important Constraints

- **Fact-based only**: Your analysis must be strictly bounded by the provided runtime trace and synchronization data.
- **Complete code**: When providing the fix, you **must provide the complete class or complete method code**. Using `...` to omit original logic is strictly forbidden, ensuring the code can be copied and run directly.
- **Code precision**: Clearly specify the **file name** and **function name** where the fix is applied.

---

## 📋 Output Format Requirements

Please strictly follow the template below when providing your diagnostic report:

# Bug Localization and Fix Plan

## 1. Factual Backtracking Path
[Step-by-step trace from the symptom backward to the root cause, citing specific Thread IDs, Block IDs, or Synchronization Edges from the data]

## 2. Root Cause Analysis
- **File**: [specific file name]
- **Function**: [specific function name]
- **The Flaw**: [Explain exactly what went wrong based on the runtime facts, e.g., missing lock, incorrect branch condition, data race]

## 3. Code Fix Implementation
[Provide the complete modified code using Markdown code blocks. Add prominent comments such as `// 🐛 [Bug Fix]` at the changed parts]

## 4. Verification Logic
[Briefly explain why this fix resolves the issue and how it corrects the execution flow or synchronization graph]
"""

# Simplified template (Call-Tree only, concurrency sections removed)
SIMPLE_PROMPT_TEMPLATE = """# Code Bug Localization and Root Cause Analysis Task

You are a senior software architect and debugging expert. Based on the provided zero-noise runtime trace data, please help me perform deterministic factual backtracking to locate the root cause of a bug.

---

## 📋 Bug Symptom & Context

**🐞 Observable Symptom / Anomaly**: 
{bug_symptom}

**🛠️ Tech Stack Context**: 
{tech_stack}

**💬 Additional Notes**: 
{additional_info}

---

## 🔍 Zero-Noise Scenario Runtime Data

The following data comes from real system runtime trace logs. It is a "zero-noise" factual record of the specific execution scenario that triggered the bug. It contains:
- **Call Tree**: The exact sequence of executed basic blocks, pruned source code, and method signatures. Unexecuted branches are entirely removed.
- **Important Premise**: Please reason entirely based on this factual data. **Do not guess or fabricate** execution paths. If a piece of code is not in the data, it did not execute.

### ✅ [Runtime Evidence] Complete Execution Data
=========================================
{trace_data}
=========================================

---

## 🎯 Diagnostic Requirements

Please act as a factual detective. **Focus exclusively on the Call Tree evidence**:

1. **Symptom Anchor**: 
   - Locate the exact block or method in the call tree where the symptom manifested (e.g., the exception point or the final incorrect read).

2. **Factual Backtracking**: 
   - Trace the data flow and execution path backward along the call tree from the symptom anchor.
   - Identify where the execution state diverged from expected behavior.

3. **Root Cause Identification**:
   - Pinpoint the exact file, function, and logical flaw that caused the issue.

---

## ⚠️ Important Constraints

- **Fact-based only**: Your analysis must be strictly bounded by the provided call tree.
- **Complete code**: When providing the fix, you **must provide the complete class or complete method code**. Using `...` to omit original logic is strictly forbidden.
- **Code precision**: Clearly specify the **file name** and **function name** where the fix is applied.

---

## 📋 Output Format Requirements

# Bug Localization and Fix Plan

## 1. Factual Backtracking Path
[Step-by-step trace from the symptom backward to the root cause, citing specific Block IDs from the call tree]

## 2. Root Cause Analysis
- **File**: [specific file name]
- **Function**: [specific function name]
- **The Flaw**: [Explain exactly what went wrong based on the call tree execution]

## 3. Code Fix Implementation
[Provide the complete modified code using Markdown code blocks. Add prominent comments such as `// 🐛 [Bug Fix]` at the changed parts]

## 4. Verification Logic
[Briefly explain why this fix resolves the issue]
"""

# ==========================================
# 2. Interactive Guidance Logic
# ==========================================


def main():
    print("="*50)
    print("🕵️  AI Bug Localization Prompt Auto Generator")
    print("="*50)
    print("Please enter the required information as prompted (press Enter directly to skip optional items)\n")

    # 0. Select analysis mode
    print("⚙️  Select Analysis Mode:")
    print(
        "   [1] Call-Tree First (default, suitable for most single-threaded logic errors)")
    print("   [2] Include Concurrency Analysis (use when symptoms clearly indicate threading issues)")
    mode = input("Enter choice (1 or 2) [default: 1]: ").strip()
    if mode == "2":
        selected_template = FULL_PROMPT_TEMPLATE
        print("✅ Mode: Full analysis with concurrency support.\n")
    else:
        selected_template = SIMPLE_PROMPT_TEMPLATE
        mode = "1"  # ensure mode is set for file hint logic
        print("✅ Mode: Call-Tree only (concurrency sections omitted).\n")

    # 1. Collect bug symptom
    bug_symptom = input(
        "🐞 1. Please describe the [Observable Symptom] (e.g., The event-driven aggregation test incorrectly outputs an array of zeros instead of the expected computed values because the program retrieves the results before the background tasks have finished processing them.):\n> ").strip()
    if not bug_symptom:
        bug_symptom = "[No specific symptom provided. Please analyze the trace data for obvious logic errors or exceptions.]"

    # 2. Collect tech stack
    tech_stack = input(
        "\n🛠️ 2. Please enter the [Tech Stack Context] (default: Java):\n> ").strip()
    if not tech_stack:
        tech_stack = "Java"

    # 3. Collect additional notes
    additional_info = input(
        "\n💬 3. Please enter [Additional Notes] (optional, e.g., suspect SyncTest.sharedData is missing volatile):\n> ").strip()
    if not additional_info:
        additional_info = "No special additional notes. Please follow the factual trace."

    # 4. Read trace data file
    trace_data = ""
    while True:
        # Mode-specific file hint
        if mode == "2":
            file_hint = "Call Tree File With Concurrency (e.g., ../../final-output-combined.md)"
        else:
            file_hint = "Call Tree File (e.g., ../../final-output-calltree.md)"

        file_path = input(
            f"\n📁 4. Please enter the path to the [{file_hint}]:\n> ").strip()
        # Remove possible quotes (common when dragging a file into the terminal)
        file_path = file_path.strip('\'"')

        if not file_path:
            print("❌ File path cannot be empty. Please enter it again!")
            continue

        if not os.path.exists(file_path):
            print(
                f"❌ File not found: {file_path}. Please check whether the path is correct!")
            continue

        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                trace_data = f.read()
            print("✅ Successfully loaded the runtime trace data!")
            break
        except Exception as e:
            print(f"❌ Failed to read file: {e}")
            continue

    # 5. Assemble the final prompt
    final_prompt = selected_template.format(
        bug_symptom=bug_symptom,
        tech_stack=tech_stack,
        additional_info=additional_info,
        trace_data=trace_data
    )

    # 6. Write to file
    output_filename = "AI_Bug_Localization_Prompt.md"
    try:
        with open(output_filename, 'w', encoding='utf-8') as f:
            f.write(final_prompt)
        print("\n" + "="*50)
        print(
            f"🎉 Success! The complete prompt has been generated and saved in the current directory as: {output_filename}")
        print("👉 You can now open this file directly, copy all its contents, and send them to the AI!")
        print("="*50)
    except Exception as e:
        print(f"\n❌ Failed to save file: {e}")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n🛑 Operation cancelled by user.")
        sys.exit(0)
