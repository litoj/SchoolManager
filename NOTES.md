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
- The drawer press runs the same ladder in one go, an armed paste rides along.
- The picker leaves on one back press.
- A container load re-anchors at the top, except an import target or a Go-to hit.
- The frozen pane of a sibling slide starts at the live position, so the swap shows no jump.
- Rows preload flip content at open, and the cost is accepted.
- An unpreloaded row reads on io once.
- A sideways FAB move does not climb.
- A downward move shows the FAB and resets travel.
- Only a load landing shows the FAB again.
- Insert and delete keep the FAB hidden.
- The base fan stays fixed, with the two secondary actions at two-thirds diameter plus 7dp; import lifts clear of the bottom and both shift left by one caption height.
- Both secondary actions take two thirds of a primary button, the size that balances the neighbour gap against their smaller size.
- A hidden New button ends its standing palette.
- The New palette keeps the back key while it shows, so no navigation runs under it.
- An append reveals its row only when the screen does not show it whole.
- The reveal obeys the phone's animation setting, an off setting places the list at once.
- Rows appear and leave without a fade, so a fast scroll shows no shimmer.
- A long-press starts selection, with the row type icon toggling each row.
- A long-press drag on an unselected row selects the range, and a selected row moves where the list has an order.
- A reorder drag moves every selected row in shown order, while a counted proxy alone follows the finger.
- A selection press shows the action strip at once, while a reorder press holds it away until the lift.
- The row type icon toggles a row during a selection or in the picker.
- The row tap keeps its own meaning.
- A picker row tap only toggles the selection.
- Every performed action finishes the selection, a refused one keeps it.
- The strip fits as many configured actions as width allows, and More holds the rest.
- A top-bar action without an icon always goes to the More menu, which shows its text.
- The view model names the menu kind, the search door and the file doors of its own list.
- One menu door serves every host, so no screen picks a menu kind by hand.
- One menu door answers the sort family and the clear filter for every host.
- One row tap flips a word or opens a folder for every host, and the host keeps notes and inert hits.
- One shared search header orders search, the list menu, then optional selection controls.
- The shared picker takes its purpose title from the caller: test sources or a reference.
- One menu host holds a button and its dropdown in one box, so a menu opens at its own button.
- A host that opens no file lists no import row.
- Settings highlights use the same capacity calculation as the shown strips.
- Every explorer pins Select all on the right, with sorting shown directly or collapsed by width.
- The picker shows the same container info line as the explorer, so no hint line replaces it.
- A strip label stays centered and ellipsizes when its slot cannot hold the full text.
- A label with no glyph keeps the glyph slot, so one strip never splits its labels.
- The top More button hides while a selection runs.
- The explorer top bar keeps one action set: search, the list menu, then the pinned Select all.
- Cut applies only to a browse list.
- The settings editor names the strips that show each action at the current width.
- Top actions estimate capacity at 1.2 per slot, while titles and search keep 96 dp of intrinsic minimum space.
- Search stays pinned and never becomes a More entry.
- The top arrow cancels the selection and an armed move.
- An armed move occupies the top line, with cancel and paste in its strip.
- A status edits only the first info line, so the container description below it stays.
- The paste mutes for a folder that would refuse the batch or already holds all of it.
- A batch with one row left to move keeps the paste live, and its tap names the reason.
- A paste read is cancelled on navigation, so only the open folder answers.
- An unavailable action keeps its place and mutes, with the reason in a notice.
- Only a mode takeover may replace a control.
- A reference jump opens its source without a pulse.
- A view keeps its scroll, sort order and flip sides.
- Going back restores the parent view state.
- A sibling swipe restores that sibling's view state.
- Going up forgets the level left behind, so its child reopens at the top.
- An explicit `Original order` clears only the sort memory.
- Every host offers `Original order` on the browse lists it sorts itself.
- The sort menu puts original order first, then descending before ascending, with ▲ and ▼ naming direction.
- A small up button stays fixed at the right of the breadcrumbs, including before the first crumb.
- The breadcrumb presets use fixed heights, with horizontal padding added only in spacious mode.
- Sibling swipes follow the parent view's sort order, not raw database order.
- The view-state store lives with the view model, so rotation keeps it and restart drops it.
- A search round trip restores the same view state.
- The scaffold restores the scroll anchor and reports the live scroll for every host.

### Search

- The index preloads the open container subgraph, so matching runs in memory with no id-list queries.
- Search stays available in every explorer state except the subjects root.- A search started from a selection reads the selected rows and their content only.
- The scope holds until the search ends, so a refined query never widens it.
- The picker marks its sources, so its search stays unlimited.
- A query matches names and translations, and a typed translation finds its words.
- Queries and candidate text are trimmed.
- A new query cancels the pending run, with results published in batches.
- Every non-empty query runs after typing pauses, whatever its length.
- The filter editor builds one criterion at a time: a tag, a comparison and a value.
- The typed text and every criterion of a run must hold.
- The tags are type, name, description, success rate and times tested, each with its own comparisons.
- The dialog captions what a measure counts, and the rate says whether untested rows join.
- An untested rate answers as the lowest one, and the choice keeps those rows out of the run.
- A tag owns one criterion, so applying it again replaces it.
- The field text is literal, the written query syntax is gone.
- The editor holds Apply back, so no run starts from a value that cannot compare.
- An unfinished run reads as searching, an empty finished run names the next step.
- A filter shows as a chip under the header, its body opens the editor, its mark drops it.
- The info strip names the result count and the rows a limited run read.
- The search menu drops every filter and leaves the typed text running.
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
- Every import door opens the picker, which accepts text files in the word format.
- The New palette centre carries the download icon, since it is the only import door.
- Shared files open the import mover.
- The explorer picks the target.
- One top-level container opens directly, otherwise Done opens the target path.
- The import result bar shows only Done.
- The popup title stays generic, the file name belongs to the location line.
- That line labels both values: `File <name>, line <number>`.
- Offered fixes come from a single-edit search the parser validates against the whole file.
- The blanked failing line gives the baseline, so a fix never hides an error it caused.
- Text only the user knows stays a localized placeholder.
- Each fix is an instruction: the edit, then the line it produces.
- A lone diagnosis names the same edit, so neither reads as a question.
- A fix removes nothing but a closer alone on its line, never a line, name or word.
- A fix is the whole message, the diagnosis line appears only when no fix survived.
- One repair kind per edit type, so wording and validated edit can never drift apart.
- A suggestion of one character mends the import buffer itself, spaces and tabs aside.
- The buffer re-parses after every such repair, the auto-repair leaves the picked file alone.
- A repair needing user text, a second character or another line is left to the popup.
- The success report counts the repaired lines and lists each one before and after.
- Bare text is incomplete unless a following indented chapter block gives evidence.
- A separator-led line without a description is a word line whose word got lost.
- A note without a description has no word-list line, so the import refuses its export too.
- No outside editor on the phone takes a foreign content URI for text, so the app edits the file itself.
- The failure of a held file opens an editor on its failing character, the report above the file.
- The editor takes the focus on open, so its caret shows and the keyboard comes.
- The reported line is marked and brought into view, its failing column reached from the left.
- The file keeps one line per source line, a long line scrolls aside instead of wrapping.
- The caret of the editor is the text colour of the theme, the mark of the line reads on both palettes.
- The editor keeps its save at the right edge of the row, where every popup of the app waits for the action.
- Save writes the same document through the write grant of the pick and imports it again.
- A document refusing the write keeps the editor, its note and the typed text.
- A tap aside closes an untouched file only, a typed fix is never thrown away.
- Unclosed chapters, stray description closings and unfinished escapes fail instead of importing silently.
- The source block of a failure no editor can open uses a monospace block on a contrasting surface.
- A `↓` aligns through measured glyph bounds with the exact symbol or missing-character insertion point.
- Long source lines keep their alignment through horizontal scrolling.
- An unfinished multiline description points to its opening line.
- JSON failures retain the original snackbar path.
- The word-list format separates a word and its translation with `=` or `;`, the arrow divides nothing.
- A finished import reloads the target and scrolls to its last row.

### Notifications

- The first start on Android 13 and later explains the progress notifications before Android asks.
- The explanation names long imports, exports and deletions and says the app works without the permission.
- A refusal is accepted, so no screen asks again.
- The prompt appears from both the main start and a shared-file import.

### Export

- Export requires a selection, so an empty writable set opens no picker.
- The menu shows Export only while selecting.
- Every selected row except a translation exports.
- One exported row uses its own name.
- Several rows in a container use the partial suffix, at the root they use `subjects`.
- The JSON tree writes bare word and note roots at the top, and the importer accepts them.
- Export walks pre-order, writes references as paths and keeps path targets outside the export in place.
- The Export row opens one sheet: a format toggle, then Share or Save to file.
- The sheet opens on the format of the last export.
- Share writes a cache copy, which the manifest file provider hands to the system sheet at once.
- The saved file's snackbar still offers Share.

### Test

- A source is a container chain, so a picked chapter covers its words and chains dedup.
- Source word coverage is cached per source, so removal updates counts without walking remaining sources.
- Source doors serialize across the explorer, picker and setup, and a run converts an immutable source snapshot.
- The sources header carries a clear action, which drops the whole list.
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
- Each shown name carries its own description, as many lines as the description setting allows.
- Results round the rate, mark answer edits against the nearest expanded variant and offer a retake.
- The marked answer precedes the complete expected answer, with green matches, gray missing letters and red extra letters.
- A wrong run shows all its typed letters first, then all the missing ones.
- The expected-answer label uses the normal answer color, while its value stays faded.
- The title help icon explains the band and the marks with the report's own rows.
- Every caption of that help is followed by the example it describes.
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
- A message dialog of a known file offers Edit in place of Copy and stays open through it.
- Every dialog title takes one header band, as tall as its button row.
- The band is a floor, so a long title takes its second line instead of losing text.
- A header icon holds one square tap slot at the row end, so its tap is not the glyph size.
- The conflict title info icon opens a stacked `InfoPopup`.
- A paused dialog holds the last keyboard inset, and the creator reopens the keyboard on resume.
- The keyboard leaves with the last dialog, so none stays over a screen with no field.
- New creators focus the name field and show the keyboard, edits focus too.
- The add button scrolls to the bottom and focuses the new row.
- A text field reseeds its parent value in a deferred effect, so recomposition cannot clobber it.
- Creator Enter moves from each name to its description, then empty descriptions advance to the next name or confirm the last field.
- Description keyboard options stay fixed while typing, so a non-empty Enter can insert a newline without restarting the input session.
- OK discards blank translation rows, then outlines missing required names red and restores one translation row when none has actual text.
- A note requires its description, since that field is its body.
- Required creator hints append a red asterisk while their default text is visible.
- A new word swaps with its translations through the button beside its name, exchanging escaped-backslash lists both ways.
- The creator name hint names the created or edited type.
- A passive message snackbar leaves on a tap, an action one answers only through its action.
- Every snackbar leaves after 2 s, whatever its text or action.

### Theme

- Icons use the Material 24dp standard through `IconSize`.
- The Go-to glyph sits on the center of its 24dp viewport.
- Custom visuals derive from their surroundings.
- Disabled icons only tint, with no separate disabled variants.
- The New palette wears that grey wash solid, so the list never mixes into it.
- The success band keeps the red-to-yellow hue ramp at alpha 80 and fades the yellow-to-green side to alpha 40.
- An untested row and an empty container stay untinted, and their separator runs the full line.

### Help

- About covers only what no built-in help of a screen or dialog explains.
- The top bar search filters the tree live while typing, with matches keeping their ancestors and the About block hidden.
- The filter reads the example block of a section too, not only its prose.
- Matches stand open during a search, and closing the field clears the query.
- Header levels differ by band and size, with the deepest carrying depth by indentation.
- Every section header carries a chevron, turned to its open or closed state.
- A one-line topic folds into body text.
- Interaction paths use chevrons and start at the first tap target.
- Each screen explains itself briefly, while the help page keeps only the extras.
- A hidden mechanic gets an inline control or caption before it earns help text.
- The filter dialog and its chips own the filter, so help keeps only the rules they cannot show.
- The success band explains itself in no control, so help names its landmarks and the results help shows the turn.
- Literal examples use monospace code blocks apart from the explanation text.

## Test ownership

- One production decision gets one owner test, no matter how many doors reach it.
- A door list of the same branch becomes one table, the repo idiom of a case map and a loop.
- The lower layer keeps a rule shared with a UI test, the e2e copy goes.
- A UI case is one composition, so a UI run cannot be table-driven across compositions.
- A VisibleForTesting door of production grows its own test beside the real one, so production is driven instead.
- A pure rule needs no database fixture, a Room-backed unit test means the fixture is wrong.

### Coverage gaps the audit found, waiting for their own pass

- Word list: `MISSING_CHAPTER_NAME`, `CHAPTER_OPEN` and `EXTRA_CHAR` as kinds, `DESCRIBED_TEXT`, 9 of 16 fix sentences, both unencodable writer shapes, a lone CR inside a description, `MAX_CANDIDATES` and `MAX_REPAIRS`.
- Import: a nameless translation in a JSON file, the two `resolveTargetRow` fallbacks, the too-deep and load-fail guards, the export failure route.
- Explorer: `pickReferences` and `finishReferencePicker`, `commitForkReferences`, four of the eight sort comparators, `onOtherRowClick` note copy, `armedReferences`, `goToRoot` during a search, the supersede guards of `openSibling`.
- Row: `flipOnto` without a preload, a translation row flipped to its words, `applyPlacement` and `readPlacement`, `applyFresh` of a deleted item, the `searchShowPath` setting.
- Creator: the swap for the three other kinds, `itemNameMissing` of a blank variant part, the note arm of `focusFirstMissing`, the resume without focus, the dirty side of the outside-tap dismissal.

## Open items

- The fix editor of a failed file waits for a phone check of its marked line and its no-wrap view.
- The physical keyboard behaviour waits for an emulator or a device to drive it.
