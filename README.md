# School Manager

Offline Android app for school vocabulary. Subjects, chapters, words, translations and notes form a hierarchy, with tests and saved success rates.

- Android 11 or newer
- Download the APK from [Releases](https://github.com/litoj/SchoolManager/releases)

## Screenshots

<img width="360" alt="subjects" src="https://github.com/user-attachments/assets/60bd06c5-2d81-420b-82db-281376e01f6a" /> <img width="360" alt="chapter" src="https://github.com/user-attachments/assets/80a97a5f-799f-44b2-b066-187d2497d1fc" />

<img width="360" alt="new-item-palette" src="https://github.com/user-attachments/assets/918789a2-de1f-4f42-87db-ace4bf48607c" /> <img width="360" alt="word-creator" src="https://github.com/user-attachments/assets/162a2198-ad90-4a50-8ee7-56d09f61c91b" />

<img width="360" alt="selection" src="https://github.com/user-attachments/assets/0d21c156-5a8a-492a-950a-f03f01856399" /> <img width="360" alt="search" src="https://github.com/user-attachments/assets/6dfa95cb-9012-42a3-8912-188e0d39294f" />

<img width="360" alt="test-setup" src="https://github.com/user-attachments/assets/15b4f339-4833-44c7-b103-d90d8654275c" /> <img width="360" alt="test-run" src="https://github.com/user-attachments/assets/fa59cfc9-a103-431f-8e48-007665c5196e" />
<img width="360" alt="test-results" src="https://github.com/user-attachments/assets/0cf6d9bc-f4dd-48fb-899d-f17088a582f4" />

<img width="360" alt="settings" src="https://github.com/user-attachments/assets/27eb3611-7113-4b3b-a562-35fb618bf80d" /> <img width="360" alt="help" src="https://github.com/user-attachments/assets/f63f900f-5337-483d-af0d-4cb0a492fd63" />

## Beyond the basics

The in-app Help covers the standard workflow. These features are easy to miss.

### Browsing

- Swipe left or right to move between sibling containers.
- Tap a breadcrumb part to jump back to that container.
- Pull down at the top of a list to open the search field.
- Tap the type icon of a row to select it during a selection or in the picker.
- Long-press and drag a row in a browse list to reorder it.

### Display

- Tap a word to switch between the word and its translations.
- A second tap within the window flips the whole list to that word's side.
- The double-tap window is configurable. Zero turns the group flip off.
- The colour band marks success. Blue is untested, red is weak, green is strong.
- A description above the display limit collapses to an info icon. Tap it to read the full text.

### References

- A reference shows one word in another container without a copy.
- The Create reference action prepares the selected rows. Open the target and press Paste.
- Chapters cannot be referenced.
- The link icon opens the original container. With several containers, the app asks for one.
- Editing a shared word asks which references follow the change.

### Stored flip

- The more menu can flip the stored direction of the selected rows.
- The conflict dialog lists rows that reach outside the selection. Keep, copy or drop each one.

### Sorting

- The sort menu orders by alphabet, success rate, name length, manual position or test priority.
- Priority puts weak and rarely tested rows first.

### Keyboard

- Search: Enter runs the query.
- New word: Enter moves from the name to the translations. The last translation saves the word and opens a fresh word popup.
- Note: Enter on the name moves to the description.
- Subject or chapter: Enter in the name saves it.
- Test: Enter moves to the next answer. The last answer hides the keyboard.

### Tests

- Duration is per item. The timer runs for the number of tested items times the duration.
- The Make tests by success rate setting picks the rows that the priority sort puts first.
- The source picker has a select-all button in the top corner.

Development: [DEVELOPMENT.md](DEVELOPMENT.md).
