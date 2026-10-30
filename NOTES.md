# SchoolManager (Kotlin/Compose + Room)

Android app for school vocabulary learning: subjects, chapters, words with translations, tests.
Source: `/home/kepis/Documents/personal/PG/Java/SchoolManager`.
Hardlinked into the repo as `NOTES.md`, a git checkout can break the link.
Architecture, build config, coding rules, deploy commands and open items live in `DEVELOPMENT.md`.
This note holds only what is not there.

Reference project: `~/Documents/school/PG/Android/SchoolManager`.
It is a full Compose and Room rewrite of the same app.
Its UI is not wanted.
Its DB part is the model for this project.

## Session environment (not in DEVELOPMENT.md)

- `adb` is at `/usr/bin/adb`.
- The target is usually `emulator-5554`, verify with `adb devices -l`.
- Only debug deploys via `installDebug`.
- Nothing is on `PATH`.
- The JBR is `/opt/android-studio/jbr` and runs Java 25.
- Run gradle with `/usr/lib/jvm/java-21-openjdk`: the JBR launcher prints
  a restricted-method warning block on every build, java-21 is clean.
  The forked test JVMs are 21 either way, only the daemon differs.
- `JAVA_TOOL_OPTIONS=-Dsun.java2d.uiScale=2` sits in the user environment,
  every local JVM prints `Picked up JAVA_TOOL_OPTIONS`. CI never sees it.
- `/usr/lib/jvm` holds java-21 and java-27.
- `JAVA_HOME` may be the JBR or java-21.
- `GRADLE_USER_HOME` is `~/.cache/gradle`.
- Wrapper 9.8.1 is cached there.
- The SDK lives at `/home/kepis/.cache/Google/android` via `local.properties`,
  its build-tools 36.0.0 holds `apksigner`.
- `--offline` works after an IDE sync.

## Spec decisions (not in DEVELOPMENT.md)

- Import: one import button, accepts directories and text files, no
  "choose source" button.
- Save immediately on every change.
- No save button.
- Keep the import/export word text format.
- The old loader stays a separate file to migrate legacy JSON subjects.
- The `passed_tests` and `failed_tests` columns stay, explicit correction.
- Git discipline: the agent may not hide, stash or delete the user's changes.
- Word tap, 2026-10-24: one click flips one word, a second click on it
  within 300 ms flips every other word and translation row of the shown list
  to the tapped word's state, the tapped word keeps its first flip. Display
  only, no db change. The flip-all settings toggle is gone, the double click
  is the only group flip. Since 2026-10-29 the list itself picks the word
  under the first tap when the double completes, nothing is remembered
  between clicks. A double on the empty space below the rows flips
  everything.
- Flip content, 2026-10-26: rows carry their flip content from the load, so
  every flip is a pure viewmodel state change. The one `childTranslations`
  batch ALWAYS runs at open, user decision, the open cost is accepted.
  Search preloads from the in-memory index. No id-list queries, the param
  cap forbids them. Unpreloaded rows read on io once and cache.
- Export set, 2026-10-26: every selected row exports, chapters, words and
  notes. A translation row has no line of any format, so it never joins.
  The export entry needs a selection since 2026-10-27, the menu shows it
  only while selecting and an empty writable set opens no picker. The JSON
  tree writes bare word and note roots at its top, its importer always
  accepted them. The export name: one row its own name, several inside a
  container `<container> - partial`, several at the root `subjects`.
- New button drag rules, 2026-10-29: a move led sideways counts as no
  climb, a downward move shows the button and resets the travel of the
  gesture. Only a load landing re-shows the button, an insert or delete
  keeps it hidden, user decision over the broader content change rule.
- Text fields, 2026-10-27: the parent value re-seeds a field in a
  `LaunchedEffect(value)`, never during composition. The field state can
  run ahead of the parent value around a window focus change, a
  recomposition between the two must not clobber it.
- Double detection is by tap place, not row, 2026-10-25: the list-level
  `DoubleTapDetector` consumes the second tap's down in the `Initial` pass
  before any row sees it, the first tap passes through to the row untouched.
  The row kind comes from the `LazyListState` layout info, a flip may shrink
  the row and move another word under the finger. The previous tap must have
  hit a word, and a running selection keeps its taps.
- The double window is a setting since 2026-10-25: `ItemRow.doubleTapMs`,
  default 300, persisted as `doubleTapMs`, a settings change rebuilds the
  list detector. Zero disables the double.
- Item icon, 2026-10-25: tapping the type icon of a row selects it while a
  selection runs or the picker shows, the row tap keeps its own meaning.
- Settings are their own activity since 2026-10-25: `SettingsActivity`,
  exported in the manifest with a label, so the system app view opens them
  directly. The cold init matches `MainActivity`. The drawer entry starts
  the activity, `Destination.SETTINGS` is gone, and the screen's hamburger
  became a back arrow.
- Conflict dialog help, 2026-10-25: the title info icon opens a read-only
  `InfoPopup` stacked above the round, not an inline section.
- Dialog keyboard, 2026-10-26: a pause, including a split screen neighbour
  taking the touch, never reflows an open dialog. The scrim holds the last
  keyboard inset while the activity is paused, the creator reopens the
  keyboard on the resume while a field holds focus.
- New-item creator, 2026-10-25: new kinds open with the name field focused
  and the keyboard up, one shot per editor. Edits focus too since
  2026-10-26, user order: every editor opens on the keyboard. The add
  button scrolls to the bottom and focuses the new row's name field.
- Icon sizing, 2026-10-25: the Material 24dp standard, one shared
  `IconSize` in `ui/component/IconSize.kt`, covers every action and
  content icon. Material `Icon` sites keep the default. Custom visuals
  derive from their surroundings: drawer logo matches its title, the FAB
  icon fills the button, the crumb chevron halves its 25dp row and dims
  to 75% of the crumb colour, the palette keeps `PaletteIconSize = FabSize`.
  The disabled state only tints, no disabled icon variants.
- Tag CI, 2026-10-25: the tag job triggers on a completed Test workflow
  run of a main commit and only a green conclusion tags. The checkout and
  the tag point at the tested head sha, never the default branch head.

## Work cursor

- Open items live in `DEVELOPMENT.md`, section Open items.

- Picker selection tap, 2026-10-25: identical to the explorer selection
  mode, a row tap while selecting toggles the row, no click-through. The
  divert lives in `pickerCallbacks` like `mainCallbacks` does it.

## Work log

- 2026-10-29: the yellow success-rate band was almost invisible, commit
  `5d95245`.
  - Cause: red and green each hide one channel the eye sees well, yellow
    over a light ground only dims blue, the channel the eye sees worst.
    One alpha for all three made yellow look near-transparent.
  - Fix: the alpha rises from 40 at the ends to 80 at an equal score, so
    the band reads equally strong everywhere.
  - Pins updated for the new alpha values, plus the alpha ramp itself.
    649 tests and all lint green.

- 2026-10-29: the double-click flip belongs to the explorer, commits
  `686993d`, `308733c`, `f4fa49d`.
  - Before: the view model remembered the last clicked word, the double
    used that memory. The user rejected the design.
  - Now: when the double completes, the list itself picks the word under
    the first tap.
  - Bug in the first version: clicking the word moves the rows, so the
    lookup found the wrong word. Fixed by picking the word at the first
    tap, before its click moves anything.
  - Double-click on the empty space below the rows flips everything.
  - Test: a word with a tall description, tapped at its bottom, proves
    the right word wins. 648 tests and all lint green.

- 2026-10-29: the mover gap pins, commit `dee12cb`.
  - The busy lock has a pin: a shadow stream gates the document read, the
    test drives the mover on real Dispatchers.IO and releases the gate.
    waitUntil never drains the Robolectric main queue, the pin polls
    between idles instead, see drainUntil.
  - The revealed list end has a pin: the last imported row displays.
    A word row shows its translation by default, so the last row reads
    t10, not the word name.
  - ListEndReveal was never broken: every earlier display failure was
    the wrong text in the pin. A frame-wait hardening had no red case
    and went out, the reveal animates on the state change as before.
  - 646 tests and all lint green.

- 2026-10-29: the import and transfer review fixes.
  - `711f349`: MainActivity clears the launch target extras with the read.
  - `c4bf13e`: every activity shares `startAppShell` and `StatusBarBand`.
  - `3a203d1`: `ImporterFactory.createPickedImport` owns the picked-file
    dispatch, the export name computes once through `exportFileName`.
  - `8347413`: the mover resolves the provider name on io, ImportActivity
    is singleTop, the openMain intent carries NEW_TASK.
  - `87bf5ca`: the share snackbar holds Long.
  - The wide manifest mimetypes stay, file managers share text files as
    plain text or octet-stream streams.

- 2026-10-29: the post-2.0.5 review round, three agents over the 2.0.5..HEAD diff.
  - The iterative FAB commits ended in a sound design, the fixes were edge
    cases only.
  - `656fe99`: a sideways swipe keeps the button, a reversal inside one
    gesture resets its travel. Moves inside one performTouchInput block
    merge into one net pointer event, a multi-move pin needs one block per
    move.
  - `b268906`: only a load resets the button, it keys on the content step
    of the screen state.
  - `640e37a`: the resume holds the frozen keyboard inset until the live
    one is non-zero. `c1ed031`: only a growth scrolls the creator rows.
  - `d41d5a7`, `6f84ffc`, `aceb719`: small creator and flip-all cleanups.
  - The user's own commits landed in parallel: `003c912` hands the
    keyboard to the previous row on a delete, `194ef12` jumps a reference
    row home.
  - The import review findings all landed in the commits above the same
    day, see the previous entry.

- 2026-10-29: removing a focused translation hands the keyboard over,
  commit `003c912`.
  - The row disposal dropped the focused field with the keyboard. The row
    now knows it holds the keyboard, its cross requests the previous
    row's name field first, the item name for the first row.
  - The cross carries a content description, `delete`, so the pin finds
    it. Pin: focus the last row, remove it, typing lands in the previous
    row and the keyboard never drops. 633 tests and all lint green.

- 2026-10-29: the New button resets with the content and stays over a
  short list, commit `ff52873`.
  - A content step re-shows the button, so every chapter change brings it
    back.
  - The hide now asks whether the list scrolls at all. A list that fits
    the viewport keeps the button through any gesture.
  - The button image carries a content description, `fab_new` en and cs,
    so the screen pin finds it. Pins: the short-list keep in
    `NewItemFabTest`, the full round in `SubjectsScreenFabTest`. 631 tests
    and all lint green.

- 2026-10-29: a creator append reveals the list end, commit `b3552d7`.
  - The user's `scrollToBottom` counter covered every import reload and
    target, but `insertItem` publishes around `publish`, so creator adds
    never asked. An append at the end now bumps the counter in the same
    update.
  - A mid-list insert still does not scroll. Pinned next to the import
    scroll pins. 630 tests and all lint green.
- Older entries went out in the 2026-10-29 cleanup, the commit messages
  carry their summaries.


## Policy: parallel work discipline (user's standing rules, 2026-10-15)

Hard constraints, not suggestions.
Violating them repeatedly made the user angry.
The three-instances rule and the comment limits are recorded in `DEVELOPMENT.md` §8.
Also binding, recorded only here:

- Small agent scopes: one directory or one big file per agent, not whole packages.
- Findings arrive while fixing continues, with a queue of next segments to delegate.
- Work continuously between dispatches: fix findings, review the remaining segments, write pins.
- "Nothing non-overlapping left" is a signal to dispatch, not to stop.
- The ask-before-irreversible rule in `DEVELOPMENT.md` §8 outranks every other rule, user order 2026-10-16.
- Dead code a change of mine leaves behind is part of that change: remove it
  in the same round, no ask. Only the user's own work needs the ask, user
  order 2026-10-25, "I always tell you to be clean".
