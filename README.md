# SchoolManager

## Features

- hierarchical structure of chapters, words with translations and titled pictures
- configurable test success rate (tests currently not implemented) color representation
- words/translations can be configured to be toggled by default and/or toggle entire list on tap
- export/import settings
- responsive preview for settings screen, github users used as preview data
- firebase crashlytics integrated

### Planned features

- sorting (probably on dao level) - display items from oldest to latest modified…
- words `m×n` translations
- display position (config setting)
- in-app configurable tests
- export/import all data locally in SPA-JSON
- possibly picture import - would get uploaded to some server and referred by link, like usual
- sync data with configurable server
  - deleted items checked against per-user _last-server-sync_ timestamps
    - → server: `u1:ts4;u2:ts2` → `u1` immediate × `u2` must sync changes during `ts2-ts4`
  - server as `m×n` with subjects - server for more subjects, but sync with personal + my class
- configure app looks - padding, roundness, colour…

## Structure

- **core**: base `item` table/data + base item UI representation
- features:
  - specific implementation differences for each item type as separate features
    - chapter + subjects (as root/default screen) show a list of their contents + create new items
  - **settings**: configuration screen with settings import+export + preview of changes
    - contains theming/looks config because it is planned to be configurable in the future
    - basic wrappers for some composables with the looks settings applied
