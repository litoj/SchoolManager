# DEVELOPMENT

## 1. Architecture

`ui → view models → ItemRepository → Room, codecs, SAF, test engine`

| Layer | Owns | Does not |
|-------|------|----------|
| `ui.shell`, `ui.activity` | the single Compose root, navigation and chrome | data logic |
| `ui.screen`, `ui.viewmodel` | screen state and row actions | database access |
| `ItemRepository` | all data access and invariants | UI state |
| `data.db` | schema, DAOs, graph operations, counters | UI, codecs |
| `data.transfer` | SAF picking, import and export workers | codec formats |
| `data.json`, `data.wordlist`, `data.legacy` | pure parse and render of item streams | DB, SAF, UI |
| `testing` | test run, grading, selection | persistence |

- View models emit `Request` and `UiNotice`. The shell resolves the strings.
- Dialogs are a `DialogSpec` stack, each in its own window, so they survive rotation.
- Screen state is synchronous. The repository blocks on IO and callers see a blocking facade.
- `ExplorerViewModel` holds the shared explorer core: history, search, selection, list and info strip.
- Back order: clear the selection, go up one level, double-press exit.

## 2. Data model

- One `item` table for chapters, words, translations, notes and the root.
- One `item_link` M:N table for containment, with 0-based position and `is_reference`.
- The invisible root's children are the subjects.
- Counters live on words. A container rate sums the words below plain links only, so referenced content never counts.
- Destructive fallback. No migrations until a release.

## 3. Import and export

- Import runs in one Room transaction with three phases: save and merge by name and type, resolve reference paths, refresh subtree counters.
- Export walks pre-order. A reference writes as a path. Content outside the export writes in place so no path dangles.

## 4. Test engine

- A source is a container chain. A picked chapter covers its words, so chains dedup.
- Clever mode ranks by `TestEngine.urgency`. Otherwise the pick is random.
- Grading matches name variants and linked translations.
- Pass and fail counters go up the plain-parent chain.

## 5. Search

- The index preloads the subgraph of the open container, so a search runs in memory.
- A query matches names and translations. A typed translation finds its words.
- A new query cancels the pending run. Results reach the UI in batches.
- Search is a screen state with three values: off, typing, results. Back restores the cached rows.

## 6. Build and release

- Release always signs from `keystore.properties`, which never enters VCS. A missing file fails the build.
- Lint treats warnings as errors, so CI fails on a new warning.
- Material 2 only. Material 3 is excluded to keep the compact look.
- Locales are `en` and `cs` only. `values-cs` stays region-less, so any Czech locale matches.

Install a debug build on the connected device:

```bash
./gradlew installDebug
adb shell am start -n cz.litoj.schlmgr/.ui.activity.MainActivity
```

Run the unit tests:

```bash
./gradlew :app:testDebugUnitTest -Pksp.incremental=false --rerun-tasks
```

- Tests need JDK 21. Robolectric cannot read JDK 27 class files.
- `-Pksp.incremental=false` is required after package moves, otherwise KSP crashes.
- `--rerun-tasks` when cached results can lie.

Build the release APK:

```bash
./gradlew :app:assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## 7. Conventions

- Comments give the why, not the what. Two sentences or 1.5-line bullets at most.
- Comment sentences use no parentheses, dashes or semicolons.
- Code wraps at about 100 columns.
- Prevent invalid states at the source. Never backstop downstream.
- Three instances of work run in parallel. Do not end a turn with fewer.
- Ask before anything irreversible: reverts, deletions, resets, amends.

## 8. Testing standard

Three test packages, one per layer:

| Package | Runs | Covers |
|---------|------|--------|
| `unit` | plain JVM | pure logic: codecs, engines, detectors, state machines |
| `integration` | Robolectric with a real in-memory Room | repository and view model flows |
| `e2e` | a Compose rule over the real screen | what only the composed screen shows: taps, focus, keyboard, scroll, visibility |

What gets a test:

- A pin belongs where behavior lives: state changes, flows, codecs,
  engines, bug fixes. A component that only wires a callback to Compose
  needs no pin, the framework guarantees the wiring.
- Every bug fix lands red first, the pin fails on the old code.

What does not:

- No test for a state that cannot exist. One pin per prevention.
- No second test for one prevention. An e2e pin only where no unit or
  integration test can see the behavior, it is the slowest kind.

## 9. Open items

| Item | Status |
|------|--------|
| UI chapter and note creator dedup | Open decision, the creator inserts freely today |
| On-device SAF import re-verification | Not done after the perf round |
| Scroll stutter on the physical phone | Emulator clean. Recipe: `adb shell dumpsys gfxinfo cz.litoj.schlmgr reset`, fling `\W/` results, `adb shell dumpsys gfxinfo cz.litoj.schlmgr` |
| Test results success rate | Prints a raw float, rounding undecided |
| `GradleDependency` lint errors on `libs.versions.toml` | Since Kotlin 2.4.0, bump or baseline undecided |
| Main-path pins missing against §8 | Governed by the meaningfulness rule, no gap list kept |

