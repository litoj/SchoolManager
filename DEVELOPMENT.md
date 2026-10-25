# DEVELOPMENT — SchoolManager Architecture Overview

Android app for school vocabulary learning (subjects → chapters → words with translations, tests).
Language: Kotlin (100%), Jetpack Compose UI, Room database. Minimum SDK 30, target/compile SDK 37.
App ID: `cz.litoj.schlmgr`. Version 2.0.1 (build 66). Distributed as APK via GitHub releases.

## 1. Source layout

```
app/src/main/java/cz/litoj/schlmgr/
├── app/                    App context, startup, settings, crash handling, messages, SAF text I/O
├── data/                   Repository facade, item-stream types, word-name parsing
├── data/db/                Room entities, import/export service, flip/counter logic
├── data/db/dao/            Room DAOs (ItemDao, WordDao)
├── data/json/              HJSON tree codec (.hjson/.json)
├── data/legacy/            Legacy subject import (old split-file JSON)
├── data/transfer/          SAF document gateway, import/export factory, worker pipeline
├── data/wordlist/          Word-list (.txt) parser and writer
├── testing/                Test engine, test entry, test item model, timer
├── ui/activity/            MainActivity, TestActivity
├── ui/component/           Compose UI components (colors, text field, action bar, scaffold, item row, list, gestures, FAB, settings rows)
├── ui/popup/               Dialog spec/stack, message/continue/crash/test-results/creator/reference/source-pick popups
├── ui/screen/about/        About/help screen + view model
├── ui/screen/picker/       Source picker screen + view model
├── ui/screen/settings/     Settings screen + state
├── ui/screen/subjects/     Main explorer screen: view model, menus, navigation, paste, word edit, transfer
├── ui/screen/test/         Test setup screen + state + view model
├── ui/search/              Search engine + search index
├── ui/shell/               SchlMgrApp shell, top bar, drawer layer, app drawer, more menu, snackbar, back handler
└── ui/viewmodel/           Explorer view model family, screen/list/info states, item/ui models, search flow/input/run, selection, navigation history
```

## 2. Runtime architecture

```
SchlMgrApp (Compose shell)            ← navigation graph: SUBJECTS ↔ TEST ↔
  PICKER_ROUTE ↔ SETTINGS ↔ ABOUT; SnackbarHost (GlobalDependencies.messages)
  + DialogHost + Drawer
→ screen view models                  ← MainExplorer · TestSetup · Picker · Settings
→ ExplorerViewModel family            ← shared core: NavigatorHistory · SearchFlow ·
  SearchInput · SearchRun · Selection · ExplorerList · InfoStrip · ItemRow ·
  ItemListState · NavigationHistory
→ ItemRepository (single data gateway) ← Read API (subjects/children/get/…) ·
  Create/Edit (create*/rename/setDescription) · Import/Export/Flip/Move/
  Remove/Reorder/Counters/Test stats
→ back ends                            ← Room DB (single DB, version 5) ·
  Codecs (HJson, WordList) · SAF transport (FileText/FileGateway) · Test engine
```

### Component responsibilities

| Layer | Owns | Does not |
|-------|------|----------|
| `ui.shell.SchlMgrApp` | Navigation graph, drawer, snackbar, dialogs, status band | Any business data logic |
| `ui.activity.MainActivity` | `Startup.init()`, edge-to-edge, composes `SchlMgrApp` | Screen content |
| `ui.activity.TestActivity` | Countdown timer, test run, grading, results popup | Test engine rules |
| `ui.viewmodel.ExplorerViewModel` family | Navigation history, search state, selection, list state, info strip | Database access |
| `data.ItemRepository` | All data access — the single gateway | UI state, Compose |
| `data.db.ItemDatabase` | Room schema, DAOs, destructive migration | Business rules |
| `data.db.ItemDao` / `WordDao` | SQL queries for graph, links, counters | Transactions, merge rules |
| `data.transfer.*` | SAF document picking, import/export workers | Codec format details |
| `data.json.HJson`, `data.wordlist.*` | Pure codecs — parse/render item streams | DB, SAF, UI |

## 3. Database schema

Single-table design (DB version 5, destructive fallback — no migrations, no official release yet).

### `item` table

| Column | Type | Meaning |
|--------|------|---------|
| `id` | INTEGER PK AUTOINCREMENT | Row id (root = 0) |
| `name` | TEXT | Item name (trimmed at creation) |
| `description` | TEXT | Item description |
| `type` | TEXT | `chapter` \| `word` \| `translation` \| `note` \| `root` |
| `passed_tests` | INTEGER | Pass count |
| `failed_tests` | INTEGER | Fail count |

### `item_link` table

| Column | Type | Meaning |
|--------|------|---------|
| `parent_id` | INTEGER FK → item.id | Parent row |
| `child_id` | INTEGER FK → item.id | Child row |
| `position` | INTEGER | 0-based order (gaps allowed after delete) |
| `is_reference` | INTEGER (0/1) | Reference link — target lives elsewhere |

- PK = (`parent_id`, `child_id`)
- Index on `child_id` (for CASCADE deletes)
- Subjects = chapters with no parent link (root's children)
- References = items with >1 parent link; chapters referencing chapters is forbidden at write time
- `passed_tests`/`failed_tests` summed over plain links only (reference links never count toward a parent's rate)

## 4. Data flow

### Import (word list / HJSON / legacy JSON)

```
picked document (SAF Uri)
→ FileGateway.startsAsJson()           ← peeks first non-whitespace byte:
  '[' or '{' = tree JSON, anything else = word list
→ ImporterFactory.decide()             ← ImportKind: JSON_TREE / WORD_AS_SUBJECT /
  WORD_INTO_CONTAINER
→ FileWorker (named thread)            ← parses on worker, posts results to UI
→ Codec (pure, testable)              ← HJsonImporter / WordListImporter /
  LegacySubjectImporter; produces DataImporter stream (StreamItem pre-order +
  StreamReference links)
→ ItemRepository.import()             ← one Room transaction, ImportService.run()
  3 phases: 1. save stream (merge by name/type, placeholder ids)
  2. resolve references (climb plain-parent paths to exported root)
  3. refresh counters (subtree sums)
→ Room DB tables: item + item_link
```

### Export

```
selection or open container
→ ItemRepository.export()             ← walkExport() pre-order stream: each
  container first, then children; references write as ref: paths;
  content-in-place when target outside export; onStream hands the whole
  stream over — its size targets the bar
→ Codec.render()                      ← HJsonExporter / WordListExporter;
  produces text (HJSON tree or word list)
→ FileText.writeText()                ← SAF output stream, UTF-8, one pass
→ Startup.onExportSuccess()           ← snackbar with stats + path
```

### Delete

```
ItemRepository.remove()               ← deleteGraph() recursively removes subtree;
  reports every deleted row via Progress (rows phase)
→ FileProgress                        ← indeterminate bar with row count text
→ Startup.onLoadFail / onSaveFail     ← failures per file
```

### Test flow

```
TestSetupState (picked source chains)
→ TestActivity.startTest()            ← TestEngine.convertAll(chains), then
  TestEngine.setTested(amount, timeSec, sources); selection heuristics:
  usage count + success-rate belts; answer matching: name variants + linked
  translations
→ TestScreen (Compose)                ← countdown header, answer rows, Done button
→ onSubmit()                          ← grades off IO thread, shows TestResultsPopup
→ ItemRepository.recordPass/recordFail ← bumps counters up the plain-link chain
```

### Search

```
query field (SearchInput)
→ SearchEngine.search()               ← filters preloaded SearchIndex in memory;
  matches word names and translation text (typed translation → its words);
  regex via java.util.regex.Pattern (invalid → BadPattern notice)
→ SearchRun                           ← batches of 32 rows to the UI thread;
  debounce onSubmit, cancel pending run on new text
→ ExplorerScreenState.searchUi        ← OFF / TYPING / RESULTS; back from search →
  restoreSearch() reloads cached rows
```

## 5. UI components

### Shell (`ui.shell`)

- **SchlMgrApp** — the only Compose root. Navigation graph with 5 destinations. Shows crash-report popup (from `CrashReporter.getCrashReport()` once per launch), snackbar host over content, dialog host, drawer layer, status band drawn last so it covers the open drawer too.
- **TopBar** — composable chrome: `TopBarNav` (hamburger), `TopBarTitle`, `TopBarIconButton`, `TopBarMoreButton` (owns its dropdown), `headerShadow()` modifier under content.
- **DrawerLayer** — 250dp panel with full-screen scrim (tap closes), drag open/close, settle to nearest anchor.
- **AppDrawer** — destinations SUBJECTS / TEST / SETTINGS / ABOUT, each with icon + label.
- **MoreMenu** — entry list with id/label/children/selection-action/always/enabled; opens as M2 dropdown.
- **MessageSnackbar** — resolves `UiNotice` → snackbar text/full-text/action label.

### Explorer screens (`ui.screen.subjects`)

- **SubjectsScreen** — top bar (search field or title), breadcrumb strip + info strip via `ExplorerScaffold`, item list, bottom selection/paste strip, `NewItemFab`, dialogs (creator/reference/confirm-delete), back handler, horizontal swipe → sibling switch.
- **MainExplorerViewModel** — content loading, back dispatch, more-menu dispatch (`onMenuItemClick`), row intents (click flips/opens/copies; long click toggles selection), creation, import/export, paste/cut/reference/delete, flip selected, prepare test.
- **SubjectsTransfer** — export word-list/tree-JSON to picked target; dispatches picked files to `ImporterFactory`; directory import.
- **SubjectsPaste** — cut/reference paste with cycle/subject/duplicate guards.
- **SubjectsWordEdit** — word/item editing (translations editor for words, simple editor otherwise), creation, fork handling on shared-word rename.
- **SubjectsNavigation** — reference jump (single source direct, multiple → SourcePickPopup), sibling swipe.
- **MenuId** — integer ids for more-menu leaves.

### Test screens (`ui.screen.test`)

- **TestSetupScreen** — amount/duration rows, reverse switch, picked-source list, add-source tile, Start button.
- **TestSetupViewModel** — owns `TestSetupState`, persists changes immediately.
- **TestSetupState** — picked source chains (dedup, one-subject rule), amount/time/reverse.
- **TestActivity** — separate `ComponentActivity`: countdown header, answer rows (translation → word by default, word → translations when reversed), Done button → `TestResultsPopup`. Double-back-press exits.

### About (`ui.screen.about`)

- **AboutScreen** — top bar with app name + version; collapsible help tree (create/select/search/test/extra sections + regex cheat-sheet tables).
- **AboutViewModel** — expanded-section set (survives rotation), app version loaded once.

### Settings (`ui.screen.settings`)

- **SettingsScreen** — interface section (parse names, default flip, flip-all-on-click, description lines, word splitter) + test section (amount, time, clever).
- **SettingsViewModel / SettingsState** — loads from statics/AppSettings; every edit applies and persists at once.

### Picker (`ui.screen.picker`)

- **PickerScreen** — explorer content; search field in title bar; select-all button in the top corner; bottom bar (cancel/select) only while selecting.
- **PickerViewModel** — shared saved history across entries; `onSelect` commits picked rows into `TestSetupState`.

### Popups (`ui.popup`)

All implement `DialogSpec`, shown through `Dialogs` stack (own `Dialog` window each, survives rotation).

| Popup | Purpose |
|-------|---------|
| `CreatorPopup` | Create/edit item: name + description, word translations editor, `CreatorResult` verdict |
| `FlipConflictPopup` | Flip's conflict round: editable rows (keep/edit/remove), `FlipConflictsResult` verdict |
| `InfoPopup` | Read-only explanation above another dialog: title + scrollable body + OK |
| `ReferencePopup` | Pick which referenced chapters keep the moved word (fork decision) |
| `ContinuePopup` | OK/cancel confirm, OK fires exactly once |
| `TestResultsPopup` | Post-test results: success % + per-item rows with typed answer colored |
| `MessagePopup` | Scrollable body + Copy (clipboard-capped) + OK |
| `CrashReportPopup` | Previous-session crash report + Copy |
| `SourcePickPopup` | Pick source chain for a word living in multiple containers |
| `Dialogs` | Stack: show/dismiss/clear, `isShowing<T>()` |
| `PopupChrome` | Shared card/title/body/button/separator/field chrome |

### Components (`ui.component`)

| Component | Purpose |
|-----------|---------|
| `Item` | Single row: position cell, kind icon, name/flipped translations, answer line, info/reference icons, success-ratio tint band |
| `ItemList` | `LazyColumn` of `Item` rows, per-variant position width, content-type recycling, full callback set |
| `ItemListGestures` | Long-press-and-drag (select range / reorder / edge auto-scroll), top-pull-to-search |
| `ExplorerScaffold` | Breadcrumb bar + animated list content + action bar slot |
| `ExplorerTopBar` | Scrollable breadcrumb strip + expandable info strip |
| `BareTextField` / `WideTextField` | Compact / full-row `BasicTextField` forms with hint, accent cursor, focus-lost |
| `ExplorerActionBar` | Bottom strips: selection (test/cut/edit/delete), paste, picker |
| `NewItemFab` | Floating New button: one kind → click/drag commits; several → circular palette |
| `SettingRows` | `SettingSwitchRow`, `SettingNumberRow` |
| `ExplorerColors` | Theme colors from `colors.xml` |
| `SearchEngine` | Filters `SearchIndex` in memory; matches names + translations |
| `SearchIndex` | Preloaded subgraph + links for search rows |

## 6. View model / state model

### `ExplorerViewModel<R>` (abstract, shared core)

- Owns `NavigationHistory` (path, prevPaths, hopDescents), `SearchRun`, `SearchFlow`, `Selection`, `ExplorerList`, `InfoStrip`.
- `setContent(container)`, `changedPath(skip)`, `requests` flow (screen-specific `Request` sealed interface).
- Back handling returns `BackOutcome` (HANDLED / EXIT_HINT / EXIT).
- `onBackRequested()`: selection → hierarchy level → double-press exit → goBack.

### Screen view models

| View model | Screen | Request type |
|------------|--------|--------------|
| `MainExplorerViewModel` | Subjects | `OpenTest`, `PickImportDir`, `PickImportFile`, `PickWordExport`, `PickJsonExport`, `EditWord`, `EditItem`, `CreateItem`, `ForkReferences`, `ConfirmDelete`, `PickSource` |
| `PickerViewModel` | Picker | `Close` |
| `TestSetupViewModel` | Test setup | `StartTest`, `OpenPicker` |
| `SettingsViewModel` | Settings | — (state applies directly) |
| `AboutViewModel` | About | — (expanded sections + version) |

### State objects

| State object | Holds |
|--------------|-------|
| `ExplorerScreenState` | breadcrumbs, info, isInfoExpanded, reorderable, searchUi, sibling, contentStep |
| `ItemListState` | items, maxPosition, pickerMode, selectionActive, variant, contentStep |
| `ItemRow` | item, parentId, position, flipped, selected, translationLines, path, isReference |
| `ItemUiModel` | key, position, name, description, kind, ratio, isReference, hasSource, selected, payload |
| `TestSetupState` | openedPicker, openedWithSelection, list (source chains), amountText, timeText, reverseTest |
| `SettingsState` | parse, defFlip, flipAllOnClick, descLinesText, wordSplitText, clever, amountText, timeText |

## 7. Build configuration

### `build.gradle.kts` (root)

- Plugins: Android application (aliased), Compose compiler, KSP
- No `versionCode`/`versionName` here — set in app module

### `app/build.gradle.kts`

| Setting | Value |
|---------|-------|
| `namespace` / `applicationId` | `cz.litoj.schlmgr` |
| `compileSdk` / `targetSdk` | 37 |
| `minSdk` | 30 |
| `versionCode` | 66 |
| `versionName` | 2.0.1 |
| Java toolchain | 21 (module), tests forked at 21 |
| Compose | enabled, Material 2, material3 excluded |
| Room | runtime + KSP compiler |
| Locales | `en`, `cs` only (`values-cs`, not `values-cs-rCZ`) |
| Signing | `release` build type always uses `keystore.properties` (not in VCS); missing file fails release |
| Shrink | `isMinifyEnabled = true`, `isShrinkResources = true` on release |
| Play metadata | `dependenciesInfo.includeInApk = false` |
| Lint | `warningsAsErrors = true` (CI gate) |

### Key dependencies

- AndroidX Activity + Compose + Navigation + DocumentFile
- Room (runtime, KTX, KSP compiler)
- kotlinx-coroutines-android
- HJSON
- JUnit + Robolectric (tests)
- Compose UI test (junit4 + manifest)

## 8. Coding rules (project conventions)

- **Comments**: max 2 sentences or 1.5-line bullets, ~100-column width, WHY only — no narration, no bug history, no checks for states that cannot exist.
- **Prevent invalid states at the source** (repository layer owns invariants); never backstop downstream.
- **No tests for impossible states**; one pin per prevention.
- **Fail-first**: every behaviour change ships with a pinning test that fails when re-added.
- **Three instances rule**: two subagents + me, or three subagents; never end a turn with fewer than three active.
- **Ask before anything irreversible**: reverts, deletions, resets, amends require asking first.
- **Screen state is synchronous** — `ItemRepository` blocks on `Dispatchers.IO`; callers see blocking facade.
- **View models hold no resource IDs** — semantic requests (`UiNotice`), the shell resolves strings at display time.
- **Data imports/exports through item streams** (`DataImporter`/`DataExporter`); codecs are pure and testable.
- **No reference chapters** — `ItemRepository.reference()` refuses chapters; `is_reference` stored on `item_link`.

## 9. How to build / test / deploy

### Deploy (debug, to emulator)

```bash
cd /home/kepis/Documents/personal/PG/Java/SchoolManager
JAVA_HOME=/opt/android-studio/jbr ./gradlew installDebug
adb shell am start -n cz.litoj.schlmgr/.ui.activity.MainActivity
```

### Unit tests

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest -Pksp.incremental=false --rerun-tasks
```

- `-Pksp.incremental=false` required after package moves (KSP crash otherwise)
- Tests run on JDK 21 (Robolectric can't read JDK 27 class files)
- Use `--rerun-tasks` when result freshness matters (cached results can lie)

### Release APK

```bash
./gradlew :app:assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

- `keystore.properties` at repo root (not in VCS) must exist; release always signs, missing file = build failure
- Release cert SHA-256: `25e8…a87e`

## 10. Open items / known gaps

| Item | Status |
|------|--------|
| UI chapter/note creator dedup (merge same-named rows like words) | Open decision — today it inserts freely |
| On-device SAF import re-verification | Not done after perf round |
| Scroll stutter on physical phone | Emulator clean (0% jank); recipe: `adb shell dumpsys gfxinfo cz.litoj.schlmgr reset` → fling `\W/` results → `adb shell dumpsys gfxinfo cz.litoj.schlmgr` |
| Reverse tests | Never existed (removed feature) |
| `colorSearchField`/`colorSearchBorder`/`colorSearchText` | Unused palette entries |
