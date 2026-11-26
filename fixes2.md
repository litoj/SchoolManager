# Opravy nálezů z druhého uživatelského testování

Snímky jsou z emulátoru Small_Phone na větvi `usability-fixes`.
Snímek před opravou ukazuje větev těsně před ní.

### Přesun kapitol nejde najít

| Údaj | Obsah |
|---|---|
| Problém | Uživatelské testování, tester nemohl najít možnost přesunutí kapitol. |
| Původní stav | Přesun nesl popisek Vyjmout s ikonou nůžek.<br><img src="fixes2/move-label-before.png" width="220"> |
| Provedená změna | Akce se jmenuje Přesunout a nese ikonu složky se šipkou.<br>Stejný název má v nastavení akcí i v nápovědě.<br>Vybraná kapitola, lišta výběru.<br>Commit `977a33f`.<br><img src="fixes2/move-label-after.png" width="220"> |
| Odpovědnost | |
| Stav a omezení | Realizováno. Tlačítko Vložit na konci přesunu zůstává. |

### Chybný soubor vypadá jako nepoužitelný

| Údaj | Obsah |
|---|---|
| Problém | Uživatelské testování, tester předpokládal, že soubor nejde naimportovat. |
| Původní stav | Klávesnice se otevřela hned a zakryla editor souboru.<br>Zbyl jen nadpis Import se nezdařil a rada, popisek tlačítka byl useknutý.<br><img src="fixes2/import-error-keyboard-before.png" width="220"> |
| Provedená změna | Editor se otevře bez klávesnice, soubor s označeným řádkem zůstane vidět.<br>Červená věta nahoře říká, že soubor obsahuje chybu k opravě.<br>Tlačítko se jmenuje Opravit a importovat a vejde se celé.<br>Import souboru 2. 100.txt s chybou na řádku 102.<br>Commit `94ce404`.<br><img src="fixes2/import-error-keyboard-after.png" width="220"> |
| Odpovědnost | |
| Stav a omezení | Realizováno. Kurzor už nečeká na chybném znaku, klávesnici otevře klepnutí do textu. |

### Slabá slovíčka se vybírala ručně podle barev

| Údaj | Obsah |
|---|---|
| Problém | Uživatelské testování, tester nevyužil filtrování a slovíčka do testu vybíral ručně podle barev.<br>Cílem byl test jen z nezkoušených slovíček a slovíček s úspěšností 0 %. |
| Původní stav | Příprava testu neuměla omezit slovíčka podle úspěšnosti.<br>Filtr existoval jen ve výběru zdroje, kde ho tester nehledal.<br>Počet, čas a řazení zabíraly horní polovinu obrazovky.<br><img src="fixes2/test-rate-limit-before.png" width="220"> |
| Provedená změna | Řádek Úspěšnost nejvýš pustí do testu jen slovíčka do zadaného procenta, nezkoušená počítají jako 0 %.<br>Popisek hned ukáže, kolik slovíček vyhovuje.<br>Počet, sekundy na znak a slabá napřed leží v rozbalovacím Pokročilém nastavení.<br>Šedý pruh nadpisu a odsazení se zelenou čarou odliší rozbalené volby od ostatních.<br>Test, zdroj Deutsch, Úspěšnost nejvýš 0, vlevo sbalené a vpravo rozbalené nastavení.<br><img src="fixes2/test-rate-limit-after.png" width="440"><br>Přidat zdroj pak ukáže jen vyhovující slovíčka, kapitoly zůstávají kvůli cestě k nim.<br>Kapitola 1 vlevo při limitu 100, vpravo při limitu 0 bez slovíček sůl a maso.<br>Commit `e74314a`.<br><img src="fixes2/test-rate-limit-picker.png" width="440"> |
| Odpovědnost | |
| Stav a omezení | Realizováno. Každá nová příprava testu začíná na 100, limit se neukládá.<br>Kapitola bez vyhovujících slovíček se ve výběru dál zobrazuje. |

### Sdílení exportu zůstalo nevyužité

| Údaj | Obsah |
|---|---|
| Problém | Uživatelské testování, tester nevyužil možnost Sdílet. |
| Původní stav | Sdílet stálo vlevo, Uložit do souboru vpravo na místě potvrzení.<br><img src="fixes2/export-share-before.png" width="220"> |
| Provedená změna | Tlačítka si vyměnila místa, Sdílet stojí vpravo, kde každé okno aplikace čeká na hlavní akci.<br>Vybraný předmět, Více, Export.<br>Commit `7347332`.<br><img src="fixes2/export-share-after.png" width="220"> |
| Odpovědnost | |
| Stav a omezení | Realizováno. Ukládání do souboru zůstává o jedno klepnutí vlevo. |

### Test z více kapitol se skládal přes kapitolu zástupců

| Údaj | Obsah |
|---|---|
| Problém | Uživatelské testování, tester nenašel funkcionalitu testování.<br>Místo testu vytvořil kapitolu Test a přidával do ní zástupce slovíček. |
| Původní stav | Tlačítko Test v liště výběru zdroje testu nahradilo.<br>Kapitola 1 a pak Kapitola 2 nechaly ve zdrojích jen Kapitolu 2.<br>Otevření testu z menu zdroje smazalo.<br><img src="fixes2/add-to-test-before.png" width="440"> |
| Provedená změna | Tlačítko se jmenuje Do testu a výběr ke zdrojům přidá.<br>Kapitola 1 a pak Kapitola 2 dají oba zdroje.<br>Zdroje zůstanou i po otevření testu z menu, smaže je jen Vymazat seznam nebo křížek.<br>Deutsch, výběr kapitoly, Do testu, zpět, další kapitola, Do testu.<br>Commit `fe352de`.<br><img src="fixes2/add-to-test-after.png" width="440"> |
| Odpovědnost | |
| Stav a omezení | Realizováno. Po přidání se dál otevře příprava testu, ke sběru další kapitoly je třeba se vrátit. |
