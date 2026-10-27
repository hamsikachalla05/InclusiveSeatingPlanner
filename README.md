# Inclusive Event Seating Planner

A Java Swing desktop application that builds event-hall seating layouts and assigns attendees automatically while respecting **capacity, accessibility needs, companion seating and group preferences**. When a requirement cannot be met it is never silently violated: the attendee is left unassigned and the problem is recorded as an explained conflict.

Runs fully offline. No external libraries, no database, no internet connection.

---

## 1. Requirements

- **JDK 17 or later** (tested with JDK 21). Check with `java -version` and `javac -version`.
- Visual Studio Code with the **Extension Pack for Java** (optional, for IDE use).

## 2. How to run

| Task | Windows | macOS / Linux |
|---|---|---|
| Build and start the app | `run.bat` | `./run.sh` |
| Build only | `build.bat` | `./run.sh build` |
| Run the self-test suite (21 tests) | `test.bat` | `./run.sh test` |
| Create a runnable JAR | `package.bat` then `java -jar InclusiveSeatingPlanner.jar` | `./run.sh package` |

**In VS Code:** open the `InclusiveSeatingPlanner` folder, wait for the Java extension to load, then press **F5** and choose *Run Inclusive Event Seating Planner* (or *Run self-tests*). `.vscode/settings.json` already points the Java extension at the `src` and `test` folders.

**Quick demo:** start the app, then click **Load demo project** on the dashboard, then **Generate seating**. The demo has 78 attendees in a 9 × 14 hall and deliberately contains problems (7 people need an accessible seat but there are only 6, one companion is not registered, one group is too large for a seating block), so every conflict screen has something to show.

## 3. Features

| Area | What it does |
|---|---|
| **Dashboard** | Capacity, registered, assigned, available seats, unresolved conflicts, plan-validity banner, progress, "needs attention" list, quick actions. |
| **Hall Setup** | Event name, rows (1–52), seats per row (1–60), aisle positions, accessible seats and reserved seats (ranges such as `A1-A4`, reservation holders such as `B7:A003` or `A7-A8:Speakers`). Live seat map; click any seat to change its type. All input is validated and every problem is listed. |
| **Attendees** | Add, edit, search, filter and remove attendees. Fields: ID, name, group, seating accommodation, companion requirement and companion ID, preferred area or row, notes. CSV import is also supported (see `sample-data/attendees-sample.csv`). |
| **Seating Layout** | Manual editing: assign, move, swap and unassign, with a right-click seat menu and double-click shortcuts. Each action is validated; rejected actions explain why and, where possible, offer a fix (e.g. "move companion A067 to A6 as well"). Auto-assign and reset are also here. |
| **Conflicts** | Every conflict has an ID, category, affected attendees, description, severity, suggested resolution and status. You can filter, re-validate, mark resolved (only after validation), acknowledge warnings and clear history. |
| **Final Plan** | Seat map with names and the stage marked, row/seat labels, legend, filters (row, group, status, attendee), attendee lookup table, assignment summary and validity status. |
| **Export & Save** | Assignments CSV, conflict-report CSV, printable HTML seating report (print preview included), save / save as / open. The project is **autosaved after every change**. |

## 4. Constraint model

| Type | Constraint | How it is treated |
|---|---|---|
| **Hard** | Seat capacity: one attendee per seat, one seat per attendee | Enforced by `SeatingAssignment`, so double-booking cannot happen. |
| **Hard** | Reserved seats | Only the attendee named as the holder can use the seat; other reserved seats stay empty. |
| **Hard** | Accessibility need: *accessible seat*, *aisle/row-end seat*, *front-section seat* | The seat must match. Otherwise the attendee is left **unassigned** and an ERROR is raised. |
| **Hard** | Required companion | The pair must sit directly side by side in the same row, **with no aisle between them**. |
| Soft | Group seating | The group is seated together if possible; otherwise a WARNING is raised. |
| Soft | Preferred area (front/middle/back third) or row | Honoured if possible; otherwise a WARNING is raised. |

Only the *accommodation needed for seating* is stored, never medical details. The notes field reminds organisers of this.

## 5. The allocation algorithm (`SeatingAllocator`)

The algorithm is deterministic and greedy, so the same input always produces the same plan:

1. **Clean up:** optionally keep existing assignments, but release any that have become invalid.
2. **Reservations:** seats reserved for a specific attendee ID go to that attendee.
3. **Free misused accessible seats:** if more people need accessible seats than are free, people *without* that need are moved out of them.
4. **Hard constraints first:** companion pairs and attendees with accessibility needs are seated first, scarcest resource first (accessible, then front, then aisle, then companion-only), pairs before singles. A pair is only ever placed on two adjacent seats.
5. **Groups:** largest group first. The algorithm tries one contiguous run of seats in a single row; otherwise it grows a compact cluster around members already seated.
6. **Everyone else:** in attendee-ID order.

Each candidate seat is **scored** (lower is better):

- +1000 for using an accessible seat without needing one
- +100 for missing a preference
- +10 per step away from the nearest group member
- a small bonus for leaving free seats beside a group

Hard constraints are *filters*: an unsuitable seat is never even considered. Soft constraints only affect the score. Anything left unmet is reported by `ConstraintValidator.detect()`.

## 6. Conflicts and their lifecycle

Conflict detection is re-run after **every** change and is based purely on the current state. Reloading a file therefore reproduces exactly the same conflicts.

| Status | Meaning |
|---|---|
| **Open** | The issue is present in the current layout. |
| **Addressed** | Re-validation no longer detects the issue. It is waiting for the organiser to confirm it. |
| **Resolved** | The organiser confirmed it. **Mark resolved** first re-validates and refuses if the issue still exists. |
| **Acknowledged** | A *warning* the organiser has accepted. Errors can never be acknowledged away. |

The categories cover capacity exceeded, insufficient accessible seats, accessibility unmet, no adjacent companion seat, invalid companion reference, group split, preference unmet, reserved seat blocking a request, invalid manual change (blocked actions are logged) and attendee unassigned.

The plan is reported as **valid only when everyone is seated and no open error remains**.

## 7. Architecture

```
src/com/seatingplanner/
├── App.java                     entry point
├── model/                       plain domain objects (no Swing)
│   ├── Seat, EventHall          2D Seat[][] grid, aisles, adjacency, sections
│   ├── Attendee, Project        attendee data; aggregate of hall + attendees + assignment
│   ├── SeatingAssignment        attendee <-> seat mapping (keeps both sides in sync)
│   ├── Conflict                 conflict record
│   └── enums                    SeatType, OccupancyStatus, AccessibilityNeed, SeatingArea,
│                                AssignmentStatus, ConflictCategory, ConflictSeverity, ResolutionStatus
├── service/                     business logic (no Swing)
│   ├── SeatingPlanner           facade: validates, applies, re-validates, notifies the UI
│   ├── SeatingAllocator         automatic assignment algorithm
│   ├── ConstraintValidator      hard/soft rules + conflict detection (single source of truth)
│   ├── ConflictManager          conflict register and status lifecycle
│   ├── HallConfig               parsing/validation of the hall form
│   ├── FileStorageService       JSON save/load (atomic writes), CSV attendee import
│   ├── ExportService            CSV exports + HTML report
│   ├── Json, Csv                dependency-free JSON and RFC-4180 CSV helpers
│   ├── ActionResult, DemoData
│   └── InvalidConfigurationException, StorageException
└── ui/                          Swing presentation only
    ├── MainFrame                window, sidebar navigation, menus, autosave, file dialogs
    ├── DashboardPanel, HallSetupPanel, AttendeePanel, AttendeeDialog,
    │   LayoutEditorPanel, ConflictPanel, FinalPlanPanel, ExportPanel, ReportPreviewDialog
    ├── SeatMapPanel             custom-painted, zoomable seat grid with tooltips
    └── Theme, Icons, UiKit, LegendPanel   design system (colours, vector icons, components)
test/com/seatingplanner/SelfTest.java      21 tests, no JUnit needed
```

The UI never changes model objects directly. Every change goes through `SeatingPlanner`, which:

1. validates the change,
2. applies it (rolling back if the change would create a new hard-constraint violation),
3. re-runs conflict detection,
4. notifies listeners, which refresh the screens and schedule an autosave.

## 8. Data and files

- **Project file:** JSON, by default `~/InclusiveSeatingPlanner/seating-project.json`. The last opened project reopens automatically.
- **Saving:** files are written to a temporary file and then moved into place, so a crash cannot corrupt an existing project. A failed save is reported, and closing the window flushes any pending changes.
- **Loading:** damaged or unknown entries are skipped with warnings, and the whole file is never rejected because of one bad entry. Invalid saved assignments are released and reported. A completely unreadable file is backed up before a new project is started.
- **CSV exports:** use proper escaping (commas, quotes, line breaks), a UTF-8 BOM so Excel shows accents correctly, and protection against spreadsheet formula injection.

## 9. Testing

`test.bat` (or `./run.sh test`) runs `SelfTest`, which covers:

- hall generation and adjacency
- configuration validation
- accessible-first allocation
- accessible-seat shortage
- companion adjacency and impossible pairs
- invalid companion references
- group seating and splits
- reserved seats
- capacity overflow
- manual-change validation, swaps and companion proposals
- preserving assignments on re-run
- determinism
- the conflict lifecycle
- save/load round trip
- malformed files
- CSV escaping and import
- a full demo-project check that no hard constraint is violated

## 10. Known limitations

- The hall is a rectangular grid. Curved or irregular rows can be approximated by marking seats as reserved.
- The allocator is greedy rather than an exhaustive optimiser. It always respects hard constraints, but it may split a group that a perfect search could keep together. Any such split is reported as a warning.
- Companion adjacency means side by side in the same row. Front/back seating does not count as adjacent.
