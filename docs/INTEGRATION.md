# Wspólny kontrakt aplikacji i silnika — v1

Stan: **10 listopada 2026**, Europe/Berlin. Aktualny przyrost: [pulpit i własne interwały](stage-4/README.md), [PR #7](https://github.com/RobertCygan/Bil-Service-assistent/pull/7). Etap 3 i przygotowanie integracji zostały scalone do `main` w PR #5 i #6. PR #7 importuje kod drugiego modelu z `525c223a9b4377a8490ddb464010fa2128a99ca0` jako drugiego rodzica commita; gałąź PR #2 pozostaje niezmieniona. Nie oznacza to ukończenia interfejsów OCR, kopii ani powiadomień.

## 1. Własność i wspólny build

| Obszar | Zasada współpracy |
| --- | --- |
| `app/`, Room, nawigacja, pulpit, adapter i root Gradle | Integracja po stronie aplikacji; zmiany w gałęzi `codex/stage-4-dashboard` |
| Algorytmy `service-engine/`, ekstrakcja, eksport i przypomnienia | Import z PR #2 bez zmian w kodzie algorytmów; dalsze zmiany uzgadniać z autorem tego modułu |
| `service-contract/` i ten dokument | Wspólne identyfikatory i znaczenie danych; zmiany wymagają oceny obu stron |
| Katalog producentów i prawa użycia | Osobna bramka, niezależna od kompilacji i testów |

Kotlin **2.2.21**, Gradle **8.13**, AGP **8.11.1**, JDK/JVM **17**, minSdk **26**, compile/targetSdk **36**. Kotlin serialization i JSON **1.8.1** korzystają już z `gradle/libs.versions.toml`. `settings.gradle.kts` włącza `service-contract` i `service-engine`; aplikacja ma produkcyjne `implementation(project(":service-engine"))`. Samodzielny build silnika nadal działa.

Plik `.github/engine-integration.ref` przechowuje historyczną rewizję importu. CI nie pobiera osobnej kopii silnika: sprawdza kod aplikacji i modułu z tego samego commita. Dodatkowe zestawy `engineIntegrationTest` i `engineIntegrationAndroidTest` są zawsze częścią testów aplikacji. Workflow Androida uruchamia testy aplikacji, kontraktu i silnika; drugi sprawdza minimum API 26.

## 2. Kody czynności i operacji

Źródło prawdy: [ServiceTask.kt](../service-contract/src/main/kotlin/app/servicepassport/contract/ServiceTask.kt). W bazie są oddzielne `action_code` i `operation_code`; do reguł i historii silnika trafia pełna para, np. **`engine_oil:replace` ≠ `engine_oil:inspect`**.

| Czynność | Dozwolone operacje |
| --- | --- |
| `engine_oil`, `oil_filter`, `gearbox_oil` | `replace`, `inspect` |
| `brake_fluid`, `coolant`, `spark_plugs`, `timing_belt` | `replace`, `inspect` |
| `air_filter`, `cabin_filter`, `fuel_filter` | `replace`, `inspect` |
| `brake_pads`, `brake_discs` | `replace`, `inspect` |
| `inspection_service`, `brakes` | `inspect` |
| `battery_12v` | `test`, `replace` |

To 15 identyfikatorów czynności, nie zalecenia wykonania. Własny opis ma oba kody `null` i pozostaje w historii, lecz nie resetuje reguły po podobieństwie słów. Słownik ekstrakcji PR #2 używa już pełnych par kontraktu; test modułu kontroluje ich zgodność. Szkic nadal wymaga osobnej walidacji i potwierdzenia w aplikacji.

## 3. Decyzje i granice przyrostu

- `AppPolicy`: **1500 km / 30 dni** dla „zbliża się”, **90 dni** dla nieaktualnego odczytu. Są to zatwierdzone ustawienia aplikacji, przekazywane jawnie do `EngineSettings`; nie zmieniają reguł producenta.
- Własne interwały są ustawieniami użytkownika. Początkowe pola km/miesięcy są puste. Jedna reguła dotyczy jednej pary czynność/operacja jednego auta. Wymagany jest co najmniej jeden dodatni próg; miesiące 1–1200. Nie ma predefiniowanych liczb ani etykiety zalecenia producenta.
- Własny interwał jest **kolejnym** terminem od ostatniej potwierdzonej zgodnej czynności. Bez niej wynik to `NEEDS_DATA`, bez fikcyjnego pierwszego terminu od rejestracji/0 km.
- `IndicatorReading` pozostaje w API modułu. Ten przyrost nie dodaje ekranu ani tabeli wskaźnika; aplikacja nie przekazuje fikcyjnych odczytów. Plan zmienny bez wskaźnika wymaga danych; ewentualna górna granica ma odrębną etykietę. Sprzeczne wcześniejsze zapisy zakresu wskaźnika pozostają tematem osobnej decyzji przed jego UI.
- UI obsługuje jedno auto. Dane konfiguracji są opcjonalne, ręczne i nie są uznawane za zweryfikowane. Katalog marek/modeli, VIN i serwer rejestru nie są podłączone do aplikacji.

## 4. Baza, jednostki i zapis

**Room v3** dodaje do v2 tabelę `user_intervals` z ID, właścicielem, parą kodów, opcjonalnym km/miesiącami, rewizją i metadanymi UTC. Unikalny indeks obejmuje `(vehicle_id, action_code, operation_code)`. Migracja **2→3** jest wyłącznie dodająca; pozostaje ścieżka **1→2→3**. Archiwalne `1.json` i `2.json` nie zostały zmienione. Test urządzenia buduje bazę ze schematu v2 i sprawdza zachowanie całego profilu, konfiguracji, kosztu, kolejności prac, własnego opisu, powiązania/uwag odczytu i świadomie starszej selekcji; dopiero potem dodaje interwał i ponownie otwiera bazę.

Dotychczasowe tabele: `vehicles`, `service_events`, uporządkowane `service_actions`, `odometer_readings`, jawny `current_odometer`. `GarageSnapshot` jest transakcyjnym odczytem relacji wraz z interwałami. Tabele usług zawierają **wyłącznie potwierdzone wykonanie**; szkice OCR muszą mieć osobny magazyn.

`performed_day`, `observed_day`, `first_registration_day`: `Long` epoch-day → `LocalDate`. Metadane to milisekundy UTC. Km są `Long` w zakresie 0..Int.MAX_VALUE; konwersja sprawdza zakres. Brak km to `null`, nie 0. Koszt to opcjonalne całkowite øre i NOK, bez `Double`. Rok modelowy nie jest datą pierwszej rejestracji.

Zapis/edycja/usuwanie są transakcyjne. UUID chroni ponowienie zapisu; oczekiwana rewizja zapobiega nadpisaniu nieaktualnej wersji. Własność odczytów i selekcji jest wymuszana także kluczami obcymi. Usunięcie interwału nie usuwa historii. Dane pozostają lokalne, bez kont, sieci, analityki i kopii chmurowej.

## 5. Jawny bieżący przebieg

`EngineFactsMapper` przygotowuje fakty, a produkcyjny `EngineAdapter` zawsze ustawia **`CurrentOdometer.Selected` albo `NotSelected`**. `LatestKnown` jest poza ścieżką aplikacji. Historia służy do wykrywania niespójności i prognozy, a nie do zastępowania brakującej selekcji.

Historyczna usługa nie zmienia wyboru bez jawnego zaznaczenia. Usunięcie wybranego odczytu lub usługi czyści selekcję; zmiana jego wartości/dnia bez ponownego potwierdzenia także ją czyści. Edycja samej notatki zachowuje wybór. `selectCurrent(CurrentReadingInput)` sprawdza istniejący odczyt w transakcji: ostrzeżenie, wyjaśnienie i świeże potwierdzenie są wymagane także przy wyborze z listy. Zmiana wyjaśnienia odczytu usługowego podnosi rewizję odczytu i usługi.

## 6. Kategoria silnika a wiarygodność na pulpicie

`DashboardEvaluator` nie zmienia algorytmów importowanego modułu. Nakłada zasady prezentacji aplikacji, a UI tłumaczy kategorie, powody, źródło, kotwicę i rodzaj interwału na NO/EN.

- Brak potwierdzenia nie staje się zaległością, także przy równoczesnym braku bieżącego przebiegu i minionej dacie.
- Spadek przebiegu, różne wartości jednego dnia, wyjaśniona anomalia bieżącego odczytu lub nowszy wyższy odczyt w historii blokują pewny wynik zależny od km. Nie pokazujemy wtedy pozornie pewnego progu/pozostałych km ani szacowanego miesiąca.
- Sama adnotacja przy dawnym odczycie, np. świadomy wcześniejszy wybór, nie zatruwa na zawsze poprawnego późniejszego odczytu; rzeczywista niespójność całej historii nadal ma znaczenie.
- Odczyt starszy niż 90 dni poniżej progu nie wystarcza do „bez pilnej potrzeby”; potrzebna aktualizacja. Już udokumentowane przekroczenie km pozostaje zaległością z ostrzeżeniem.
- Niezależnie potwierdzony miniony/zbliżający się termin czasowy od ostatniej czynności może pozostać pilny mimo niepewnego przebiegu. Podstawa wskazuje czas. Brak historii tego nie dopuszcza.
- Interwał wyłącznie czasowy nie zależy od aktualności km. Szacowany miesiąc przebiegu jest osobnym polem, a nie dokładną datą serwisu.
- Suma km kotwicy i interwału jest sprawdzana w `Long` przed arytmetyką `Int` silnika. Przekroczenie zakresu daje potrzebę sprawdzenia danych.

Snapshot i jego wynik trafiają do jednego stanu UI. Zmiany bazy przeliczają pulpit; data odświeża się po wznowieniu i co minutę na aktywnym ekranie. Błąd obliczeń ma komunikat i ponowienie, bez zastępowania go pustą „bezpieczną” listą.

## 7. Bramka producenta

Produkcyjny `InstalledManufacturerCatalog` jest pusty. Żadne liczby z testów ani `testFixtures/demo` nie trafiają do danych aplikacji.

Przed pokazaniem jakiejkolwiek reguły producenta wymagane są łącznie:

1. `CatalogValidator.productionIssues` bez błędów, pochodzenie `VERIFIED_SOURCE` i pełne metadane.
2. Jedno pełne dopasowanie `PlanResolver` do konfiguracji. Brak pola i niejednoznaczność nie upoważniają do wyboru podobnego planu.
3. Aktualne, niewycofane `SourceAuthorization` dla **konkretnego ID i wersji** każdego źródła planu/reguł, z niepustym odnośnikiem do dowodu uprawnień oraz okresem ważności.
4. Aktualność źródła oraz kod czynności zgodny z kontraktem.

Niepusty tekst `usageRights` nie jest dowodem licencji. Uprawnienia dostarcza sprawdzona integracja katalogu, nie formularz kierowcy lub OCR. Źródło wygasłe/wycofane/niedopuszczone powoduje usunięcie reguł producenta z wyników i wyjaśnienie braku planu. Własne interwały pozostają osobno, z etykietą „Your interval” / „Ditt intervall”. Ten mechanizm nie dostarcza ani nie weryfikuje samych praw użycia.

## 8. OCR — następny osobny przyrost

Biblioteka ekstrakcji jest zaimportowana; aplikacja nie odczytuje jeszcze faktur. `OcrRun`/`ServiceDraft` nie mają drogi do historii silnika. Integracja musi dodać oryginał dokumentu i trwały szkic, weryfikację pojazdu, rodzaju dokumentu, daty wykonania, waluty, pełnych kodów i wybranych prac oraz atomowe zatwierdzenie bez duplikatów. Data wystawienia faktury nie staje się automatycznie datą usługi. Wycena, zalecenie i zakup części nie resetują interwału. EUR/GBP lub brak waluty nie mogą zostać zapisane jako NOK. Ogólne „full service” nie potwierdza wymiany każdej części.

## 9. Eksport i przypomnienia — granice aktualnej zgodności

`ExportData` z importu zachowuje pola dawnego schematu Room v2 i dodaje `userRules`, ale **`ExportUserRule` nie ma rewizji i znaczników czasu nowych `user_intervals`**. Format archiwum ma `formatVersion=1`; nie mylić tego z wersją schematu Room. Przed udostępnieniem kopii potrzebne są zgodna ewolucja DTO/walidatora i test **Room v3 → archiwum → nowa baza**, obejmujący także selekcję, własne opisy i metadane interwałów. Nie podłączamy niepełnej kopii do UI.

Test biblioteki archiwum na API 26 potwierdza jej wykonanie oraz zapis/odczyt nagłówka na starszym Androidzie; nie dowodzi bezstratności kopii bazy. `ReminderPlanner` ma własne testy tłumienia niepewnych zaleceń; powiadomienia Androida nie są włączone. Przyszły planer musi uwzględniać także bramkę źródła i wiarygodność wyniku aplikacji, zamiast samodzielnie wywoływać silnik z pominięciem tych zasad.

## 10. Weryfikacja i dalsza współpraca

[Raport bieżącego przyrostu](stage-4/README.md) przypina kod, wyniki JVM, Androida i restartu. Testy aplikacji obejmują rzeczywisty zapis Room → mapowanie → silnik, edycję interwałów i politykę kategorii. Test UI wpisuje własne interwały przez produkcyjne formularze, więc zrzuty nie wymagają ukrytego katalogu demonstracyjnego.

Starsze raporty [Etapu 3](stage-3/README.md), [przygotowania](integration/README.md) i [autora modułów](09-uzgodnienie-z-aplikacja.md) opisują wcześniejsze rewizje. Bieżący zakres rozstrzyga ten dokument. Zmiana kodu, typu pola, źródła albo znaczenia statusu wymaga aktualizacji kontraktu i sprawdzenia przepływu między modułami.

## Aktualizacja po scaleniu PR #7 (10 listopada 2026)

Od tego miejsca całość prowadzi jeden autor (decyzja właściciela). Zmiany w pierwszym przyroście po scaleniu:

- **Eksport dostosowany do Room v3:** `ExportUserRule` ma parę kodów, rewizję i znaczniki czasu; test obiegu baza → archiwum → nowa baza porównuje wszystkie wiersze sześciu tabel. Szczegóły: [08-etap-7-eksport-danych.md](08-etap-7-eksport-danych.md).
- **Silnik nie zawija sum km:** przekroczenie zakresu daje `CALCULATION_LIMIT` i „wymaga danych”, także dla odczytu wskaźnika.
- **Mapowanie przyczyn silnika na pulpit jest wyczerpujące** (`when` bez `else`): nowa przyczyna w silniku nie skompiluje się bez obsługi w aplikacji.
- **Zaległość przy starszym wybranym odczycie:** jeśli próg km minął już przy wybranym odczycie, a jedyną wątpliwością jest nowszy, wyższy odczyt w historii, pozycja pozostaje „zaległa” z widocznymi km. Przy cofniętym liczniku, różnych km tego samego dnia lub zapisanym wyjątku nadal „wymaga danych”.
- **Ekran wskaźnika serwisowego jest w MVP** (decyzja właściciela) — do zrobienia w osobnym przyroście.

Rozbieżność kategorii między pulpitem a przypomnieniami jest usunięta: `ReminderPlanner` przyjmuje `ReminderSubject`, który aplikacja buduje z końcowego wyniku pulpitu (`ReminderCoordinator`). Szczegóły: [07-etap-6-przypomnienia.md](07-etap-6-przypomnienia.md).

## Wskaźnik serwisowy z auta (Room v4)

Decyzja właściciela: ekran jest w MVP. Stan po tym przyroście:

- Tabela `indicator_readings` (migracja 3→4): dzień odczytu, przebieg w tej chwili, pozostałe km i/lub dni tak, jak pokazuje auto; wartości ujemne oznaczają, że auto samo pokazuje zaległość. Odczyt jest dodawany lub usuwany, nie edytowany.
- Odczyt jest **własnym źródłem** na pulpicie (`TaskOrigin.VEHICLE_DISPLAY`, czynność `inspection_service:inspect`): termin to dokładnie wpisane wartości, bez żadnego założonego interwału. Liczy się tylko najnowszy odczyt.
- Potwierdzony późniejszy przegląd serwisowy unieważnia starszy odczyt: pozycja przechodzi do „wymaga danych” i prosi o nowy odczyt.
- Gdy kiedyś pojawi się zweryfikowany plan zmienny producenta dla tej samej czynności, te same odczyty zasilą jego regułę, a osobna pozycja z wyświetlacza nie jest dublowana.
- Odczyty są w kopii danych (`ExportIndicatorReading` z identyfikatorem, rewizją i znacznikami czasu) i w teście obiegu baza → kopia → nowa baza.
- Przypomnienia działają dla tej pozycji tak samo jak dla pozostałych, bo czytają wynik pulpitu.

Niesprawdzone: ekran na urządzeniu; migracja 3→4 na fizycznym telefonie (jest test na archiwalnym schemacie v3 w środowisku testowym i sprawdzenie aktualizacji v1/v2 w CI).

## Dokumenty przy wpisach (Room v5)

- Nowa tabela `documents` (migracja 4→5) i pliki w prywatnym katalogu aplikacji; opis w `docs/10-etap-5-import-dokumentu.md`.
- Format kopii pozostaje w wersji 1: `ExportDocument` dostał opcjonalne pole `createdAtUtc`, a rozmiar i skrót pliku pochodzą z manifestu. Starsze wersje aplikacji odmawiają kopii z dokumentami (jak dotąd), nowsza czyta starsze kopie bez zmian.
- Dokument nie wpływa na obliczenia silnika: źródłem historii pozostaje wpis zatwierdzony przez użytkownika.
