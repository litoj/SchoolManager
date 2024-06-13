# SchoolManager

## Features

- export/import settings
- 

### Planned features

- sorting (probably on dao level) - display items from oldest to latest modified…
- words `m×n` translations; responsive global translation toggle in config
- display success rate by coloring a part/entire item background
- display position (config setting)
- in-app configurable tests
- export/import all data locally in SPA-JSON
- possibly picture import - would get uploaded to some server and referred by link, like usual
- sync data with configurable server
  - deleted items checked against per-user _last-server-sync_ timestamps
    - → server: `u1:ts4;u2:ts2` → `u1` immediate × `u2` must sync changes during `ts2-ts4`
  - server as `m×n` with subjects - server for more subjects, but sync with personal + my class
- configure app looks - padding, roundness, colour…

### TODOs

- try to avoid using `.type`, at least passing it to domain types
