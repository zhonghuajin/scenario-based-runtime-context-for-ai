# Happens-Before
> **Format:** `- [Sync_Object] Releasing_Thread (Time) -> Acquiring_Thread (Time)`
> Represents synchronization edges where the left side happens-before the right side.

- [O3] T39 (10313200) -> T40 (61287200)
- [O5] T42 (116463400) -> T41 (116579100)
- [O6] T45 (135712300) -> T46 (135860000)
- [O2] T47 (141456100) -> T3 (141562600)

# Data Races
> **Format:** `- variable: `VarName` | W: Thread1 (Time) -> R/W: Thread2 (Time)`
> Represents unsynchronized concurrent access to shared variables (Write-Write or Write-Read conflicts).

- variable: `SyncTest.sharedData` | W: T3 (0) -> R: T36 (1665300)
- variable: `SyncTest.sharedData` | W: T3 (0) -> W: T36 (4411300)
- variable: `SyncTest.sharedData` | W: T36 (4411300) -> R: T3 (4465700)
- variable: `SyncTest.sharedData` | W: T36 (4411300) -> W: T37 (6966100)
- variable: `SyncTest.sharedData` | W: T37 (6966100) -> R: T38 (6999900)
- variable: `SyncTest.sharedData` | W: T37 (6966100) -> W: T39 (10310200)
- variable: `SyncTest.sharedData` | W: T39 (10310200) -> W: T42 (116458600)
- variable: `SyncTest.sharedData` | W: T42 (116458600) -> W: T45 (135789400)
- variable: `SyncTest.sharedData` | W: T45 (135789400) -> R: T46 (135890500)
- variable: `SyncTest.sharedData` | W: T45 (135789400) -> W: T47 (141399900)

# Possible Taint Flows 
> **Legend:**
> - `[Inter]`: Cross-thread data flow via shared variables.
> - `[Intra]`: Within-thread data flow (a write operation potentially tainted by previous reads in the same thread).

- [Inter] `SyncTest.sharedData` (Item: I1): T3 (0) -> T36 (1665300)
- [Intra] T36 (4411300): Wrote to `SyncTest.sharedData`, tainted by ["SyncTest.sharedData"]
- [Inter] `SyncTest.sharedData` (Item: I2): T36 (4411300) -> T3 (4465700)
- [Inter] `SyncTest.sharedData` (Item: I6): T37 (6966100) -> T38 (6999900)
- [Inter] `SyncTest.sharedData` (Item: I8): T39 (10310200) -> T40 (61360900)
- [Inter] `SyncTest.sharedData` (Item: I9): T42 (116458600) -> T41 (116592700)
- [Inter] `SyncTest.sharedData` (Item: I10): T45 (135789400) -> T46 (135890500)
- [Inter] `SyncTest.sharedData` (Item: I5): T47 (141399900) -> T3 (141570500)

