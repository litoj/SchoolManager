# SchoolManager, Kotlin Compose and Room

## Purpose

Android app for school vocabulary: subjects, chapters, words, translations and tests.
The repository is the source.
This note holds only what is not in `DEVELOPMENT.md`.

## Note file

`NOTES.md` is a hardlink to this note. A git checkout can break the link.
Component behavior and design live here.
Layer boundaries, workflows, build instructions and open items live in `DEVELOPMENT.md`.

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

## Components

### Data model

- One `item` table holds chapters, words, translations, notes and the root.
- One `item_link` M:N table holds containment, 0-based positions and reference flags.
- The invisible root's children are the subjects.
- Counters live on words.
- A container rate sums words below plain links only, so referenced content never counts.
- The schema uses destructive fallback, with no migrations before a release.
- The reference project models the database only, never the UI.

### Explorer

- `ExplorerViewModel` holds the shared core: history, search, selection, list and info strip.
- Back clears the selection, then goes up one level, then asks for a second exit press.
- The picker leaves on one back press.
- A container load re-anchors at the top, except an import target or a Go-to hit.
- The frozen pane of a sibling slide starts at the live position, so the swap shows no jump.
- Rows preload flip content at open, and the cost is accepted.
- An unpreloaded row reads on io once.
- A sideways FAB move does not climb.
- A downward move shows the FAB and resets travel.
- Only a load landing shows the FAB again.
- Insert and delete keep the FAB hidden.
- An append reveals its row only when the screen does not show it whole.
- The reveal obeys the phone's animation setting, an off setting places the list at once.
- Rows appear and leave without a fade, so a fast scroll shows no shimmer.
- A long-press starts selection, with the row type icon toggling each row.
- The row type icon toggles a row during a selection or in the picker.
- The row tap keeps its own meaning.
- A picker row tap only toggles the selection.
- Every performed action finishes the selection, a refused one keeps it.
- The strip fits as many configured actions as width allows, and More holds the rest.
- Settings highlights use the same capacity calculation as the shown strips.
- The picker pins Select all on the right, with sorting shown directly or collapsed by width.
- A strip label stays centered and ellipsizes when its slot cannot hold the full text.
- The top More button hides while a selection runs.
- Cut applies only to a browse list.
- The settings editor names the strips that show each action at the current width.
- Top actions estimate capacity at 1.2 per slot, while titles and search keep 96 dp of intrinsic minimum space.
- Search stays pinned and never becomes a More entry.
- The top arrow cancels the selection and an armed move.
- An armed move occupies the top line, with cancel and paste in its strip.
- An unavailable action keeps its place and mutes, with the reason in a notice.
- Only a mode takeover may replace a control.
- A reference jump opens its source without a pulse.
- A view keeps its scroll, sort order and flip sides.
- Going back restores the parent view state.
- A sibling swipe restores that sibling's view state.
- Going up forgets the level left behind, so its child reopens at the top.
- An explicit `Original order` clears only the sort memory.
- Sibling swipes follow the parent view's sort order, not raw database order.
- The view-state store lives with the view model, so rotation keeps it and restart drops it.
- A search round trip restores the same view state.

### Search

- The index preloads the open container subgraph, so matching runs in memory with no id-list queries.
- A query matches names and translations, and a typed translation finds its words.
- Queries and candidate text are trimmed.
- A new query cancels the pending run, with results published in batches.
- Every non-empty query runs after typing pauses, whatever its length.
- The filter icon builds the query from labeled choices and previews it.
- Apply writes the dialog result into the field, and a written filter reopens the dialog.
- A plain filter stays plain text.
- An operator after the markers means a number query, other text uses the escaped form.
- A malformed number query or bad regex shows red in the info strip and never runs.
- An unfinished run reads as searching, an empty finished run names the next step.
- The info strip names the active filter and result count.
- The search menu clears the active filter.
- The field stays while it holds text, with or without the keyboard.
- A keyboard drop on an empty field closes it.
- The nav arrow and system back cancel the search, clear the query and restore the cached browse rows.
- A word hit starts on its translations through the search translate setting.
- Go-to is search-only, opens the hit's parent and pulses the landed row once.
- Live results keep their state across settings changes, which take effect on a new run.
- Edits never rematch an active query, and a rename join rebinds its hit to the surviving word.
- History restores a search's rows and scroll, while a new query starts at the top.
- Search rows update through the shared list path, with history state captured only on leaving.
- A search hit shows only the container trail below the search start.
- The diacritics toggle folds plain names and queries, while regex patterns stay as written.

### Editing

- Names and descriptions are trimmed at the write boundary.
- Every change saves immediately, with no save button.
- Keep `passed_tests` and `failed_tests`.
- Every edit updates its shown row in place, including a rename join to a surviving word.
- Rows sharing a touched translation re-render.
- An edit never reloads the list or changes its membership.

### Flip

- The Flip action swaps the stored word and its translations.
- A word tap flips one row.
- A second tap within the window flips the shown list to that state.
- The first tap picks the word under the finger and is always a word.
- A double tap on empty space below the rows flips all.
- Nothing is remembered between taps.
- A running selection keeps its taps.
- `ItemRow.doubleTapMs` defaults to 300 and persists as `doubleTapMs`.
- Zero disables the group flip.
- A settings change rebuilds the double detector.
- The old settings toggle for flipping all is gone.

### Import

- Import runs in one Room transaction: save and merge by name and type, resolve references, refresh counters.
- One import button accepts text files in the word format.
- Shared files open the import mover.
- The explorer picks the target.
- One top-level container opens directly, otherwise Done opens the target path.
- The import result bar shows only Done.
- The popup title stays generic, the file name belongs to the location line.
- That line labels both values: `File <name>, line <number>`.
- Offered fixes come from a single-edit search the parser validates against the whole file.
- The blanked failing line gives the baseline, so a fix never hides an error it caused.
- Text only the user knows stays a localized placeholder.
- Each fix reads as a suggestion: the kind of edit, then the line it produces.
- A fix never removes anything: no line, name or word is ever offered as deleted.
- A fix is the whole message, the diagnosis line appears only when no fix survived.
- One repair kind per edit type, so wording and validated edit can never drift apart.
- Bare text is incomplete unless an arrow relation or following indented chapter block gives evidence.
- Unclosed chapters, stray description closings and unfinished escapes fail instead of importing silently.
- The complete source line uses a monospace block on a contrasting surface.
- A `↓` aligns through measured glyph bounds with the exact symbol or missing-character insertion point.
- Long source lines keep their alignment through horizontal scrolling.
- An unfinished multiline description points to its opening line.
- JSON failures retain the original snackbar path.
- The word-list format supports `=`, `;` and `→` separators.
- A finished import reloads the target and scrolls to its last row.

### Export

- Export requires a selection, so an empty writable set opens no picker.
- The menu shows Export only while selecting.
- Every selected row except a translation exports.
- One exported row uses its own name.
- Several rows in a container use the partial suffix, at the root they use `subjects`.
- The JSON tree writes bare word and note roots at the top, and the importer accepts them.
- Export walks pre-order, writes references as paths and keeps path targets outside the export in place.
- The export result offers Share.

### Test

- A source is a container chain, so a picked chapter covers its words and chains dedup.
- Source word coverage is cached per source, so removal updates counts without walking remaining sources.
- Source doors serialize across the explorer, picker and setup, and a run converts an immutable source snapshot.
- The picker starts at the explorer position without moving it.
- Clever mode ranks by urgency, otherwise the pick is random.
- The setup rows name their units, and captions derive the pool and total time.
- The tested side row flips on tap, starting from the setting in a fresh setup, with the word side as default.
- Start waits for a source and says why.
- Unavailable Start fades the whole button once.
- A shared prompt groups by the shown prompt: translation rows normally, the word name when reversed.
- Partial overlap stays separate, and a shared block uses one batched prompt lookup.
- Shared-block answers land on the member that accepts them, whatever the field order.
- A repeated answer fails once in an ordinary shared block.
- Required variant lists may repeat one variant across different words.
- A synonym passes only when every shown translation names its answer.
- The answer hint names the asked side.
- The countdown pauses while the app is away.
- Back confirms before ending an ungraded run, and a run-out time reports itself.
- The red button aborts and drops the answers, only Done grades them.
- Requiring all variants accepts any valid answers that cover every side once, with the widest independent part setting the field count.
- An added required answer multiplies that word's time by 1.5.
- A word's time is 5 seconds plus its answer characters times the seconds per character.
- A tested word scores once regardless of variants, while different words score separately.
- Grading matches name variants and linked translations, then counters go up the plain-parent chain.
- Global grading removes each configured answer literal from both sides.
- Ignorable literals use one settings field per value with a trailing empty field.
- A prompt describes the shown side, never the hidden answer.
- Each shown name carries its own description, only its first line.
- Results round the rate, mark answer edits against the nearest expanded variant and offer a retake.
- The marked answer precedes the complete expected answer, with green matches, gray missing letters and red extra letters.
- The expected-answer label uses the normal answer color, while its value stays faded.
- A correct variant answer still labels the full variant word.
- Result positions follow item order, with lifetime passed/total only at the trailing edge.

### Settings

- Settings are an exported activity.
- The drawer opens Settings, whose screen has a back arrow.
- A field row focuses its field from the label.
- Ignored answer fields move focus to the next field on Enter.
- The settings list ends with a margin, so its last row clears the screen edge.
- Switch and choice rows act on any tap.

### Dialogs and creators

- A `DialogSpec` stack uses one window per dialog, so dialogs survive rotation.
- The conflict title info icon opens a stacked `InfoPopup`.
- A paused dialog holds the last keyboard inset, and the creator reopens the keyboard on resume.
- The keyboard leaves with the last dialog, so none stays over a screen with no field.
- New creators focus the name field and show the keyboard, edits focus too.
- The add button scrolls to the bottom and focuses the new row.
- A text field reseeds its parent value in a deferred effect, so recomposition cannot clobber it.
- Creator Enter moves from each name to its description, then empty descriptions advance to the next name or confirm the last field.
- Description keyboard options stay fixed while typing, so a non-empty Enter can insert a newline without restarting the input session.
- OK discards blank translation rows, then outlines missing required names red and restores one translation row when none has actual text.
- Required creator hints append a red asterisk while their default text is visible.
- A new word swaps with its translations through the button beside its name, exchanging escaped-backslash lists both ways.
- The creator name hint names the created or edited type.

### Theme

- Icons use the Material 24dp standard through `IconSize`.
- The Go-to glyph sits on the center of its 24dp viewport.
- Custom visuals derive from their surroundings.
- Disabled icons only tint, with no separate disabled variants.
- The New palette uses the light grey wash.
- The success band keeps the red-to-yellow hue ramp at alpha 80 and fades the yellow-to-green side to alpha 40.

### Help

- The top bar search filters the tree live while typing, with matches keeping their ancestors and the About block hidden.
- Matches stand open during a search, and closing the field clears the query.
- Header levels differ by band and size, with the deepest carrying depth by indentation.
- A one-line topic folds into body text.
- Interaction paths use chevrons and start at the first tap target.
- Each screen explains itself briefly, while the help page keeps only the extras.
- A hidden mechanic gets an inline control or caption before it earns help text.
- The search filter owns query building, so help keeps no syntax tables.
- Literal examples use monospace code blocks apart from the explanation text.

## Current

- The Delete action dispatches through `MenuId.DELETE` again, with regression tests through the strip action.
- Root container labels are “Sets” in English and “Sady” in Czech.
- A refused creator confirm focuses the first missing required field, with 812 behavior tests and debug lint green.
- Creator descriptions keep their input session during typing, with 814 behavior tests and debug lint green.
- Passive message snackbars dismiss on a tap, while action snackbars remain clickable only through their action.
- The snackbar dismissal behavior passes 816 behavior tests and debug lint.
- Selection strip labels stay centered and the Go-to glyph is centered.
- Browse and search rows update edits in place, including rename joins.
- Search history keeps its scroll through the shared anchor path, and a new query starts at the top.
- Top and bottom actions collapse by width through one shared calculation, with 837 behavior tests and debug lint green.
- No emulator is connected for a physical keyboard check.
- The selection More slot passes focused tests and lint, and is committed.
- The top and selection More dropdowns render below and above their buttons, checked on Small_Phone.
- Validated import suggestions and measured markers pass 871 behavior tests and debug lint.
- The list reveal skips a shown row and obeys the phone, with 874 behavior tests and debug lint green.
- The abort label and the dialog keyboard handoff pass 878 behavior tests and debug lint.
- Repair and wording matrices run as unit tests, the popup keeps only the measured marker.
- Walkthrough screenshots live in `docs/scenario-walkthrough`.
- Colored answer diffs compare only expanded variants and pass 907 behavior tests and debug lint.
- Seconds per character and its stored-format migration pass 907 behavior tests and debug lint.
- Ignorable literal fields chain Enter and pass 907 behavior tests and debug lint.
- Prompts describe the shown side, each name with its own description, with 909 behavior tests and debug lint green.
