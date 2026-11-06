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

- Names and descriptions are trimmed at the write boundary.
- Save immediately on every change.
- No save button.
- Keep `passed_tests` and `failed_tests`.
- An edit that keeps the row identity updates its row in place, no reload.
- Rows sharing a touched translation re-render.
- A fork or a rename join rebuilds the list, the view state survives it.

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

- The More menu starts a selection with the first row.
- The row type icon toggles a row while a selection runs or in the picker.
- The row tap keeps its own meaning.
- A picker row tap toggles while selecting.
- It never opens the row.
- Every performed action finishes the selection, a refused one keeps it.
- The strip holds four configurable actions, the settings order drives it and the more menu.
- Go-to is a normal action that applies to search only, Cut to browse only.
- The first four applicable actions of an order fill a strip, the rest its more menu.
- The settings editor names the strips that show each action.
- The top arrow cancels the selection and an armed move, like the search field.
- An armed move reads on the top line, its strip holds cancel and paste.

### Settings

- Settings are an exported activity.
- The drawer starts Settings.
- Its screen has a back arrow.
- A field row focuses its field from the label, switch and choice rows act on any tap.

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

### Actions

- An unavailable action keeps its place and mutes.
- A tap explains the reason in a notice.
- Only a mode takeover may replace a control, never unavailability alone.

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
- The view state of a container is its scroll and the flip sides of its rows.
- A load leaving a view captures its state, a returning view restores it.
- The store lives with the view model: a rotation keeps it, a restart drops it.
- A search round trip restores the view through the same store.

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
- A hidden mechanic gets an inline control or caption before it earns help text.
- The search filter owns query building, so the help page keeps no syntax tables.
- Literal examples render as monospace code blocks, apart from the explanation text.

### Test

- The picker starts at the explorer's position and never moves it.
- The setup rows name their units, captions derive the pool and the total time.
- The tested side row flips between its two sides on a tap.
- The default tested side is a setting, the word side by default, and a fresh setup takes it.
- Start waits for a source and says why.
- Identical translation sets merge into one labeled answer block. Partial overlap stays separate.
- A repeated answer inside an exact-match block fails after its first pass.
- Every shown translation must name the answer for a synonym to pass.
- The countdown pauses while the app is away.
- Back confirms before ending a run without grading.
- A run-out time reports itself before the grading.
- The answer hint names the asked side.
- Results round the rate, pair wrong answers with the expected one and offer a retake.

### Search

- Queries and candidate text are trimmed before matching.
- The filter icon opens labeled type, field, condition and value choices.
- The dialog previews the written query, and Apply writes it into the field.
- A written filter reads back into the dialog, while plain text stays plain.
- The info strip names the active filter and the result count, never the walked chapters.
- The search menu clears an active filter.
- An unfinished run reads as searching, a finished empty one names the next step.
- The field stays while it holds text, with or without the keyboard.
- A keyboard drop with an empty query closes the field.
- The nav back arrow cancels the search: the results leave and the query resets.
- System back follows the same rule as the arrow.
- A word hit starts on its translations through the search translate setting.
- A settings change needs a new run, live results keep their state.
- A settings toggle folds diacritics away, the regex pattern stays as written.

### CI

- The tag CI waits for a green Test run of a main commit.
- It tags the tested sha.
- A tag comes only from the Tag workflow, never from a local command.
- GitLab CI runs the tests only, tagging stays on GitHub.
- The GitLab test job caches `app/build`, artifacts hold only the junit report.
- The test image lives in the project registry and ships the JDK and the compileSdk platform only.
- Robolectric resolves its SDK jar outside Gradle, so the CI caches its Maven repository.
- A kaniko job rebuilds the image when `ci/Dockerfile` changes.

## Current

- No active task.
- Open items live in `DEVELOPMENT.md`.
- Walkthrough screenshots live in `docs/scenario-walkthrough`.
- 2026-11-06: exact duplicate test prompts merge into one labeled answer block.
  Repeated answers fail once, synonyms must match every shown translation.
  Item text and search or test comparisons trim at the boundary. 802 tests.
- 2026-11-05: help moved from hidden mechanics into the UI. The search field
  opens a labeled filter dialog and the strip names the active filter. Go-to
  is a search-only action in the normal order. The More menu starts a selection
  and clears a filter. Settings, test setup, picker and search states explain
  themselves. The help page keeps short sections. 799 tests.
- 2026-11-05: main adopts the test run, test report, search and picker fixes.
  The report reads Score x/y (z%) with the prompt, correct and typed answer.
  The info strip names counts per kind, a removed source offers undo.
  A bad or malformed query reports in the strip, the search field opens its help. 771 tests.
- 2026-11-04: unavailable actions keep their place muted and name their
  reason on tap. The test setup, selection strip, menu, export, search and
  busy import follow it. Mode takeovers stay hidden. 750 tests.
- 2026-11-03: preparing a test finishes the selection like every action, a
  refused preparation keeps it. 710 tests.
- 2026-11-03: the selection strip actions became configurable with the
  connectable Go-to, the paste strip states its operation. 708 tests.
- 2026-11-03: diacritic-insensitive search behind a settings toggle. The fold
  covers the plain text finders of the query and the names, the regex pattern
  stays exact.
- 2026-11-03: creating a chapter opens it on commit instead of only appending it,
  the same landing as tapping it; notes still only append.
- 2026-11-03: word search hits start on their translations through the search
  translate toggle. The tap and sort pins run from the word side, the toggle
  carries the default-side pins. 694 tests.
- 2026-11-02: a container load re-anchors the list at the top, the frozen
  pane of the sibling slide starts at the live position. 672 tests.
- 2026-11-02: each container view remembers its scroll and flip sides for
  the session. `ViewStateStore` captures on leave and restores on return,
  search round trips included. 677 tests.
- 2026-11-02: edits update their rows in place. Same-id word and item edits
  re-render through `applyFresh`, shared-translation rows follow, forks and
  rename joins keep the reload. 680 tests.
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
