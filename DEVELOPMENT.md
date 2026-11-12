# DEVELOPMENT

## Architecture

`ui → view models → ItemRepository → Room, codecs, SAF, test engine`

| Layer | Owns | Does not |
|-------|------|----------|
| `ui.shell`, `ui.activity` | the single Compose root, navigation and chrome | data logic |
| `ui.screen`, `ui.viewmodel` | screen state and row actions | database access |
| `ItemRepository` | all data access and invariants | UI state |
| `data.db` | schema, DAOs, graph operations, counters | UI, codecs |
| `data.transfer` | SAF picking, import and export workers | codec formats |
| `data.json`, `data.wordlist` | pure parse and render of item streams | DB, SAF, UI |
| `testing` | test run, grading, selection | persistence |

- View models emit `Request` and `UiNotice`. The shell resolves the strings.
- Screen state is synchronous. The repository blocks on IO and callers see a blocking facade.

Component behavior and design live in `NOTES.md`.

## Build and release

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
./gradlew :app:testDebugUnitTest
```

- Tests need JDK 21. Robolectric cannot read JDK 27 class files.
- Add `-Pksp.incremental=false` only after package moves, otherwise KSP crashes.
- Add `--rerun-tasks` only when cached results cannot be trusted.

Build the release APK:

```bash
./gradlew :app:assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## CI

- GitLab runs only the tests and caches `app/build`, with only the junit report as an artifact.
- The test image ships the JDK and compileSdk platform only.
- Robolectric resolves its SDK jar outside Gradle, so its Maven repository is cached.
- A kaniko job rebuilds the image when `ci/Dockerfile` changes.
- GitHub tags the tested main sha after a green Test run.
- Tags come only from the Tag workflow, never from a local command.

## Conventions

- Comments give the why, not the what. Two sentences or 1.5-line bullets at most.
- Comment sentences use no parentheses, dashes or semicolons.
- Code wraps at about 100 columns.
- Prevent invalid states at the source. Never backstop downstream.
- Three instances of work run in parallel. Do not end a turn with fewer.
- Ask before anything irreversible: reverts, deletions, resets, amends.

## Parallel work discipline

- Small agent scopes: one directory or one big file per agent.
- Never hide, stash or delete the user's changes.
- Findings arrive while fixing continues, with a queue of next segments.
- Work continuously between dispatches.
- No separate work left means dispatch, not stop.
- The irreversible-action rule outranks every other rule.
- Dead code from a change is removed without asking.

## Testing standard

Three test packages, one per layer:

| Package | Runs | Covers |
|---------|------|--------|
| `unit` | JVM, Robolectric only where Android APIs are read | pure logic: codecs, engines, detectors, state machines |
| `integration` | Robolectric with a real in-memory Room | repository and view model flows |
| `e2e` | a Compose rule over the real screen | what only the composed screen shows: taps, focus, keyboard, scroll, visibility |

What gets a test:

- A pin belongs where behavior lives: state changes, flows, codecs,
  engines, bug fixes. A component that only wires a callback to Compose
  needs no pin, the framework guarantees the wiring.
- Every bug fix lands red first, the pin fails on the old code.

What does not:

- No visual styling pins for colors, padding, dimensions or rendered pixels.
- Pure calculations may keep unit tests even when their result drives presentation.
- No test for a state that cannot exist. One pin per prevention.
- No second test for one prevention. An e2e pin only where no unit or
  integration test can see the behavior, it is the slowest kind.

## Open items

| Item | Status |
|------|--------|
| UI chapter and note creator dedup | Open decision, the creator inserts freely today |
| On-device SAF import re-verification | Not done after the perf round |
| Scroll stutter on the physical phone | Emulator clean. Recipe: `adb shell dumpsys gfxinfo cz.litoj.schlmgr reset`, fling `\W/` results, `adb shell dumpsys gfxinfo cz.litoj.schlmgr` |
| Main-path pins missing against the testing standard | Governed by the meaningfulness rule, no gap list kept |
| Sibling slide, New button and row flip against the phone animation setting | Open decision, only the list reveal obeys it |

## Code review mode

OpenCode holds a `review` agent mode. It reviews changes and does not fix them.

- The mode is read-only on the codebase. The report is the only write and stays under `/tmp`.
- Review scope:
  - consistency with the codebase style and practices
  - test thoroughness and coverage of edge cases
  - readability
  - documentation where the code is not self-explanatory
- Comment and doc format follows Conventions. Findings also flag doc about params, return values or fields that lives far from those values.
- Process:
  - determine the branch under review and the main branch
  - subagents review the diff between them and may split divisible changes
  - the subagents cross-check the findings and look for further issues
- The joined report orders issues by importance for correctness and maintainability. It lists only remaining issues, never who found or disproved what.
- A merge request branch gets its report in a new markdown file under `/tmp`. Any other review replies directly.
- Every file reference in a report file is a valid online source link to the reviewed branch.

