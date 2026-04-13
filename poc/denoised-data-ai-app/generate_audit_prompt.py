#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import sys

# ==========================================
# 1. Define Prompt Template (for Code Audit)
# ==========================================
PROMPT_TEMPLATE = """# Zero-Noise Execution Trace-Driven Code Audit and Vulnerability Mining Task

You are a senior software security audit expert and Java concurrency programming master. Based on the zero-noise code execution trace data from the real system runtime provided to me, please help me conduct an in-depth code audit to uncover potential concurrency vulnerabilities, logic flaws, or security issues.

---

## 📋 Audit Task Definition

**🎯 Key Audit Focus**: 
{audit_focus}

**🛠️ Technology Stack Context**: 
{tech_stack}

**💬 Additional Notes (Optional)**: 
{additional_info}

---

## 🔍 Scenario Call Chain and Runtime State Data

The following data comes from real system runtime trace logs, containing the **absolutely authentic and noise-free** execution context for this scenario:
1. **Trace Sequence**: Linear sequence of basic code blocks (Basic Block) executed by threads.
2. **Call Tree**: Contains method signatures, source files, executed Block IDs, and **pruned (only actually executed parts) source code**.
3. **Happens-Before**: Synchronization edges between threads (memory visibility of left operation to right operation).
4. **Data Races**: Unsynchronized concurrent shared variable access conflicts (read-write or write-write conflicts).
5. **Taint Flows**: Data/taint propagation paths across or within threads.

**⚠️ Important Premise**: The data only contains **actually executed** code. If a piece of code does not appear, it means it was absolutely not executed in this scenario. Please reason entirely based on these factual data, **never fabricate** non-existent code logic.

### ✅ [Audit Target Data] Complete Execution Trace and Concurrency State
=========================================
{trace_data}
=========================================

---

## 🎯 In-Depth Audit and Analysis Requirements

Please deeply analyze the complete execution chain of the above scenario, especially the `Data Races` and `Taint Flows` sections, and complete the following audit tasks:

1. **Vulnerability/Defect Identification**: 
   - Combined with Data Races data, identify which shared variables lack proper synchronization mechanisms for concurrent access (e.g., no locking, incorrect use of volatile, etc.).
   - Combined with Happens-Before relationships, analyze whether there are memory visibility issues or instruction reordering leading to potential bugs.
2. **Root Cause Analysis**: 
   - Trace Taint Flows to explain how the problem propagates along the call chain and across threads.
   - Precisely identify the source file, class name, and specific function causing the issue.
3. **Remediation Design**:
   - Based on the existing code architecture, provide best practice code to fix the vulnerability.
   - The fix must ensure concurrency safety (no deadlocks, guarantee visibility, resolve data races) and minimize performance overhead.

---

## 📋 Audit Report Output Format Requirements

Please strictly follow the template below to output your code audit report:

# 🛡️ Code Audit and Remediation Report

## 1. Vulnerability/Defect Summary
[Briefly describe the core issue discovered in one or two sentences, e.g., "Discovered multi-threaded unsynchronized read/write of `sharedData` in the `SyncTest` class, with severe data race and memory visibility vulnerabilities"]

## 2. Detailed Defect Analysis (Root Cause)
- **Risk Level**: [High/Medium/Low]
- **Defect Type**: [e.g., Data Race / Memory Visibility Failure / Deadlock Risk]
- **Trigger Path**: [Combined with Trace data and Taint Flows, describe in detail how the vulnerability is triggered across multiple threads]
- **Affected Location**: [Specific file name and function name]

## 3. Remediation Code Implementation
[Provide the complete fixed code. **Must provide the complete class or complete method code**, never use `...` to omit existing logic, ensure the code can be directly copied and executed. Add prominent comments to modified or added parts, such as `// 🛠️ [Fixed: Added synchronization lock to resolve data race]`]

## 4. Fix Principle Analysis and Regression Recommendations
[Explain why this fix is effective (e.g., which Happens-Before rules are introduced), and what to watch out for in regression testing]
"""

# ==========================================
# 2. Interactive Guidance Logic
# ==========================================
def main():
    print("="*50)
    print("🛡️  AI Zero-Noise Code Audit Prompt Generator")
    print("="*50)
    print("Please enter audit task information as prompted (press Enter to skip optional fields and use default values)\n")

    # 1. Collect audit focus
    audit_focus = input("🎯 1. Please enter [Key Audit Focus] (e.g., Focus on identifying data races, deadlocks, or cross-thread taint propagation):\n> ").strip()
    if not audit_focus:
        audit_focus = "Comprehensive排查 concurrency security vulnerabilities (Data Races), memory visibility issues (missing Happens-Before), and potential business logic defects."

    # 2. Collect tech stack
    tech_stack = input("\n🛠️ 2. Please enter [Technology Stack Context] (default: Java, multi-threaded concurrency JMM):\n> ").strip()
    if not tech_stack:
        tech_stack = "Java, multi-threaded concurrency (JMM, Happens-Before, locking mechanisms)"

    # 3. Collect additional notes
    additional_info = input("\n💬 3. Please enter [Additional Notes] (optional, e.g., Only JDK native libraries can be used for fixes, original method signatures cannot be changed):\n> ").strip()
    if not additional_info:
        additional_info = "No special additional restrictions. Please follow best practices for Java concurrency programming (e.g., prioritize using tools from the java.util.concurrent package)."

    # 4. Read trace data file
    trace_data = ""
    while True:
        file_path = input("\n📁 4. Please enter the path to the [Execution Trace Data File] (e.g., final-output-combined.md):\n> ").strip()
        # Remove possible quotes (common when dragging files from terminal)
        file_path = file_path.strip('\'"')
        
        if not file_path:
            print("❌ File path cannot be empty, please re-enter!")
            continue
            
        if not os.path.exists(file_path):
            print(f"❌ File not found: {file_path}. Please check if the path is correct!")
            continue
            
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                trace_data = f.read()
            print("✅ Successfully loaded execution trace and concurrency state data!")
            break
        except Exception as e:
            print(f"❌ Failed to read file: {e}")
            continue

    # 5. Assemble final prompt
    final_prompt = PROMPT_TEMPLATE.format(
        audit_focus=audit_focus,
        tech_stack=tech_stack,
        additional_info=additional_info,
        trace_data=trace_data
    )

    # 6. Write to file
    output_filename = "AI_Code_Audit_Prompt.md"
    try:
        with open(output_filename, 'w', encoding='utf-8') as f:
            f.write(final_prompt)
        print("\n" + "="*50)
        print(f"🎉 Success! The complete code audit prompt has been generated and saved in the current directory: {output_filename}")
        print("👉 You can now open this file directly, copy all content and send it to a large language model (e.g., Claude 3.5 Sonnet / GPT-4o) for in-depth audit!")
        print("="*50)
    except Exception as e:
        print(f"\n❌ Failed to save file: {e}")

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n🛑 User cancelled the operation.")
        sys.exit(0)