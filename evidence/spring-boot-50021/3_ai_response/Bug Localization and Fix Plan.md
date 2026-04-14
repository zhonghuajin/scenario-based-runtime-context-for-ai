# Bug Localization and Fix Plan

## 1. Factual Backtracking Path

**Step 1 – Symptom Anchor:** The `DistributionSummary` for "example" returns an empty `histogramCounts()` array. This means the `DistributionStatisticConfig` applied to the meter had no `serviceLevelObjectives`.

**Step 2 – `PropertiesMeterFilter.configure()`:** In Thread-1, the `PropertiesMeterFilter.configure()` method calls `convertServiceLevelObjectives(id.getType(), lookup(distribution.getSlo(), id))`. The `lookup` retrieves `ServiceLevelObjectiveBoundary[]` for key "example" from the bound `MetricsProperties.Distribution.slo` map.

**Step 3 – `convertServiceLevelObjectives()`:** This method calls `candidate.getValue(meterType)` on each `ServiceLevelObjectiveBoundary`. Since the meter is a `DistributionSummary`, `meterType` is `Type.DISTRIBUTION_SUMMARY`. From the trace, `getValue` delegates to `getDistributionSummaryValue()`, which checks `this.value instanceof Double`. The trace shows the `if` block body is **not entered** (pruned away), so the value is **not** a `Double`. It returns `null`. The `filter(Objects::nonNull)` removes all entries, producing an empty array, so `convertServiceLevelObjectives` returns `null`.

**Step 4 – Why is `this.value` not a `Double`?:** Tracing back to `ServiceLevelObjectiveBoundary.valueOf("1")` → `MeterValue.valueOf("1")`. The trace shows `safeParseDuration("1")` is called, which calls `DurationStyle.detectAndParse("1")`. The `DurationStyle.SIMPLE` pattern matches bare integers (it allows an empty suffix group). The trace confirms `DurationStyle.parse` executes: `Unit.fromChronoUnit(null)` returns `Unit.MILLIS`, and it returns `Duration.ofMillis(1)`. Since `safeParseDuration` returns a non-null `Duration`, `MeterValue.valueOf` creates `new MeterValue(Duration.ofMillis(1))` — storing a `Duration`, not a `Double`.

**Step 5 – Root Cause:** `MeterValue.safeParseDuration` successfully parses the plain integer string `"1"` as a `Duration` (1 millisecond). This causes `MeterValue.valueOf` to store a `Duration` object instead of `Double`. When `getDistributionSummaryValue()` is later called, it only handles `Double` values, so it returns `null`, effectively discarding the SLO boundary.

## 2. Root Cause Analysis

- **File**: `org/springframework/boot/actuate/autoconfigure/metrics/MeterValue.java`
- **Function**: `safeParseDuration(String value)`
- **The Flaw**: The method unconditionally attempts to parse the input as a `Duration` via `DurationStyle.detectAndParse`. The `DurationStyle.SIMPLE` pattern matches bare numeric strings (e.g., `"1"`, `"2"`, `"3"`) because its regex allows an empty unit suffix, defaulting to milliseconds. This means plain integer configuration values intended as numeric SLO boundaries are misinterpreted as `Duration` objects. Downstream, `getDistributionSummaryValue()` only handles `Double` instances, so the `Duration` values are silently discarded as `null`.

## 3. Code Fix Implementation

**File**: `org/springframework/boot/actuate/autoconfigure/metrics/MeterValue.java`  
**Method**: `safeParseDuration`

```java
private static Duration safeParseDuration(String value) {
    // 🐛 [Bug Fix] Plain numeric values (e.g. "1", "2.5") must not be parsed
    // as Duration. DurationStyle.SIMPLE matches bare numbers and interprets them
    // as milliseconds, which causes integer SLO boundaries for DistributionSummary
    // to be silently ignored (Duration is not handled by getDistributionSummaryValue).
    // By checking if the value is a plain number first, we ensure it falls through
    // to the Double.parseDouble path in MeterValue.valueOf.
    try {
        Double.parseDouble(value);
        return null;
    } catch (NumberFormatException ex) {
        // Not a plain number, continue to try parsing as Duration
    }
    try {
        return DurationStyle.detectAndParse(value);
    } catch (IllegalArgumentException ex) {
    }
    return null;
}
```

## 4. Verification Logic

**Before the fix**, the call chain for value `"1"` is:
1. `safeParseDuration("1")` → `DurationStyle.detectAndParse("1")` → `Duration.ofMillis(1)` (non-null)
2. `MeterValue.valueOf` stores a `Duration` object
3. `getDistributionSummaryValue()` → `value instanceof Double` is false → returns `null`
4. All SLO boundaries are null → empty histogram buckets

**After the fix**, the call chain for value `"1"` becomes:
1. `safeParseDuration("1")` → `Double.parseDouble("1")` succeeds → returns `null`
2. `MeterValue.valueOf` falls through to `new MeterValue(Double.parseDouble("1"))` → stores `Double(1.0)`
3. `getDistributionSummaryValue()` → `value instanceof Double` is true → returns `1.0`
4. SLO boundaries are `[1.0, 2.0, 3.0]` → histogram buckets are correctly created

Duration-suffixed values (e.g., `"100ms"`, `"PT1S"`) remain unaffected because `Double.parseDouble("100ms")` throws `NumberFormatException`, allowing the method to proceed to `DurationStyle.detectAndParse` as before.