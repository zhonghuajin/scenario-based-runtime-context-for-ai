#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import sys

# ==========================================
# 1. Define Prompt Template
# ==========================================
PROMPT_TEMPLATE = """# Code Bug Localization and Root Cause Analysis Task

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

Please act as a factual detective. Do not guess what *might* have happened; instead, trace backward along the provided execution path to find what *actually* happened:

1. **Symptom Anchor**: 
   - Locate the exact block or method in the trace data where the symptom manifested (e.g., the exception point or the final incorrect read).
2. **Factual Backtracking**: 
   - Trace the data flow and execution path backward from the symptom anchor.
   - If multithreading is involved, strictly check the `Happens-Before` and `Data Races` sections. Did a thread read stale data because a synchronization edge was missing? Was there an unexpected interleaving?
3. **Root Cause Identification**:
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

# ==========================================
# 2. Interactive Guidance Logic
# ==========================================
def main():
    print("="*50)
    print("🕵️  AI Bug Localization Prompt Auto Generator")
    print("="*50)
    print("Please enter the required information as prompted (press Enter directly to skip optional items)\n")

    # 1. Collect bug symptom
    bug_symptom = input("🐞 1. Please describe the [Observable Symptom] (e.g., When the system (or task tracker) reports that \"all tasks are completed,\" retrieving the batch results should return the complete and final computed data for all tasks. But the system prematurely signals that tasks are \"completed\" before the data is actually saved in the background. As a result, when the user fetches the results immediately after receiving the completion signal, they receive empty or incomplete data.):\n> ").strip()
    if not bug_symptom:
        bug_symptom = "[No specific symptom provided. Please analyze the trace data for obvious data races, deadlocks, or exceptions.]"

    # 2. Collect tech stack
    tech_stack = input("\n🛠️ 2. Please enter the [Tech Stack Context] (default: Java, multithreaded concurrency):\n> ").strip()
    if not tech_stack:
        tech_stack = "Java, multithreaded concurrency (JMM, Happens-Before)"

    # 3. Collect additional notes
    additional_info = input("\n💬 3. Please enter [Additional Notes] (optional, e.g., suspect SyncTest.sharedData is missing volatile):\n> ").strip()
    if not additional_info:
        additional_info = "No special additional notes. Please follow the factual trace."

    # 4. Read trace data file
    trace_data = ""
    while True:
        file_path = input("\n📁 4. Please enter the path to the [Call Chain / Combined Data File] (e.g., final-output-calltree.md/final-output-combined.md):\n> ").strip()
        # Remove possible quotes (common when dragging a file into the terminal)
        file_path = file_path.strip('\'"')
        
        if not file_path:
            print("❌ File path cannot be empty. Please enter it again!")
            continue
            
        if not os.path.exists(file_path):
            print(f"❌ File not found: {file_path}. Please check whether the path is correct!")
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
    final_prompt = PROMPT_TEMPLATE.format(
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
        print(f"🎉 Success! The complete prompt has been generated and saved in the current directory as: {output_filename}")
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