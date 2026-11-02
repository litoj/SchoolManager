# SchoolManager, Kotlin Compose and Room

## Purpose

Android app for school vocabulary: subjects, chapters, words, translations and tests.
The repository is the source.
This note holds only what is not in `DEVELOPMENT.md`.

## Note file

`NOTES.md` is a hardlink to this note. A git checkout can break the link.
Architecture, build config, coding rules, deploy commands and open items live in `DEVELOPMENT.md`.

## Reference project

A separate local Compose and Room rewrite.
Its UI is not wanted.
Its DB part is the model for this project.

## Session environment

### Tools

- `adb` is at `/usr/bin/adb`.
- The emulator is `emulator-5554`.
- Only debug builds go to the emulator.

### Build

- Use `/usr/lib/jvm/java-21-openjdk` for gradle.
- The JBR is `/opt/android-studio/jbr`, Java 25.
- `JAVA_TOOL_OPTIONS` prints a local warning line.
- `GRADLE_USER_HOME` is `~/.cache/gradle`, wrapper 9.8.1.
- The SDK path is in `local.properties`.
- Build-tools 36.0.0 holds `apksigner`.
- `--offline` works after an IDE sync.

## Spec decisions

### Editing and data

- Save immediately on every change.
- No save button.
- Keep `passed_tests` and `failed_tests`.

### Import

- One import button, text files.
- Keep the word text format.
- Shared files open the import mover.
- The explorer picks the import target.
- Done opens the main screen at the path.
- A file with one container at the top opens it.
- The import result bar shows only Done.
- The import error names the picked file.
- A finished import reloads the target and scrolls to its last row.

### Export

- Every selected row exports, except translations.
- The export needs a selection.
- The menu shows Export only while selecting.
- An empty writable set opens no picker.
- One exported row uses its own name.
- Several exported rows in a container use the partial suffix.
- Several exported rows at the root use `subjects`.
- The JSON tree writes bare word and note roots at the top.
- Its importer accepts them.
- The export result offers Share.

### Flip

- A word tap flips one row.
- A second tap within the window flips the shown list to that state.
- The list picks the word under the first tap.
- Nothing is remembered between taps.
- A double tap on empty space below the rows flips all.
- The old settings toggle for flipping all is gone.
- The first tap must hit a word.
- A running selection keeps its taps.

### Double window

- `ItemRow.doubleTapMs` is the window, default 300, stored as `doubleTapMs`.
- Zero disables the double.
- A settings change rebuilds the double detector.

### Preload

- Rows preload flip content at open.
- The open cost is accepted.
- Search preloads from the index.
- An unpreloaded row reads on io once.
- No id-list queries.

### Selection

- The row type icon selects during a selection or in the picker.
- The row tap keeps its own meaning.
- A picker row tap toggles while selecting.
- It never opens the row.

### Settings

- Settings are an exported activity.
- The drawer starts Settings.
- Its screen has a back arrow.

### Dialogs and creators

- The conflict title info icon opens a stacked `InfoPopup`.
- A paused dialog holds the last keyboard inset.
- The creator reopens the keyboard on resume.
- New creators focus the name field and show the keyboard.
- Edits focus too.
- The add button scrolls to the bottom and focuses the new row.
- A text field reseeds its parent value in a deferred effect.
- A recomposition must not clobber that.

### Icons and theme

- Icons use the Material 24dp standard through `IconSize`.
- Custom visuals derive from their surroundings.
- Disabled icons only tint.
- There are no disabled variants.
- The yellow success band uses alpha 40 at the ends and 80 at an equal score.

### FAB and list

- A sideways FAB move does not climb.
- A downward move shows the FAB and resets travel.
- Only a load landing shows the FAB again.
- Insert and delete keep the FAB hidden.
- An append at the list end reveals the end.
- An insert in the middle does not scroll.

### View state

- A container load re-anchors the list at the top of the new view.
- A bottom request of an import wins over the re-anchor.
- The frozen pane of the sibling slide starts at the live position, so the swap shows no jump.

### Help

- The help top bar search filters the tree live while typing.
- A match keeps its ancestors, everything else drops.
- Matches stand open during a search and the About block hides.
- Closing the field clears the query.
- Header levels differ by band and size, the deepest carries depth by indentation.
- A one-line topic folds into body text, never gets its own section.
- An interaction path renders with chevrons, its segments reuse the menu labels.
- A path starts at the first tap target, no screen or container anchor.
- A screen explains itself in one short text, the help page holds only the necessary extras.
- Literal examples render as monospace code blocks, apart from the explanation text.

### Test

- The picker starts at the explorer's position and never moves it.
- The setup rows name their units, captions derive the pool and the total time.
- The direction row names both sides, there is no relative switch.
- Start waits for a source and says why.
- The countdown pauses while the app is away.
- Back confirms before ending a run without grading.
- A run-out time reports itself before the grading.
- The answer hint names the asked side.
- Results round the rate, pair wrong answers with the expected one and offer a retake.

### Search

- The field stays while it holds text, with or without the keyboard.
- A keyboard drop with an empty query closes the field.
- The nav back arrow cancels the search: the results leave and the query resets.
- System back follows the same rule as the arrow.

### CI

- The tag CI waits for a green Test run of a main commit.
- It tags the tested sha.
- GitLab CI runs the tests only, tagging stays on GitHub.
- The GitLab test job caches `app/build`, artifacts hold only the junit report.
- The test image lives in the project registry and ships the JDK and the compileSdk platform only.
- A kaniko job rebuilds the image when `ci/Dockerfile` changes.

## Current

- No active task.
- Open items live in `DEVELOPMENT.md`.
- Walkthrough screenshots live in `docs/scenario-walkthrough`.
- 2026-11-02: a container load re-anchors the list at the top, the frozen
  pane of the sibling slide starts at the live position. 672 tests.
- 2026-11-02: test screens reshaped. The setup states its units, pool and
  total, the run shows progress, pauses away and confirms exits, results
  pair answers with expectations and offer a retake. 668 tests.
- 2026-11-02: legacy subject folder import dropped with the whole `data.legacy`
  package. The import menu is one plain entry everywhere, the root and the
  container share one browse menu. The perf suites seed NJ from `nj.hjson`.
  666 tests.
- 2026-11-02: test setup explains itself on its screen, the help page keeps
  only the extras. Literal examples render as code blocks. 675 tests.
- 2026-11-02: picker starts at the explorer position, search field stays
  through results with a cancel arrow, test help rewritten, help interaction
  paths render with chevrons. 675 tests.
- 2026-10-30: help search, three header levels and folded one-line topics.
  The filter is `HelpFilter` with a unit pin, 668 tests.
- 2026-10-29: the testing standard lives in `DEVELOPMENT.md` §8, commits
  `e103946` and `2ef70a6`. A pin belongs where behavior lives, wiring a
  callback to Compose needs none. The wiring pins of the first round went
  out, the behavior pins stay: ContinuePopup, TestActivity, ImportActivity.
  652 tests.

## Policy: parallel work discipline

Hard constraints.

- Small agent scopes: one directory or one big file per agent.
- Never hide, stash or delete the user's changes.
- Findings arrive while fixing continues, with a queue of next segments.
- Work continuously between dispatches.
- No separate work left means dispatch, not stop.
- The rule to ask before an irreversible action in `DEVELOPMENT.md` §7 outranks every other rule.
- Dead code from a change is part of it. Remove it without asking.
- The rule of three instances and the comment limits live in `DEVELOPMENT.md` §7.
