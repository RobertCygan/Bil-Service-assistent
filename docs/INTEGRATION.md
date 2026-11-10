# Wspólny kontrakt aplikacji i silnika — v1

Stan: **10 listopada 2026**. Odczytano silnik PR #2 przy commicie `ff102f4d1c50d0974e8c6792270effc26894e4a3`, a następnie aktualizację do `adaf5fd2c74974da14d92e9c0cc59fffd04f37f6`, obejmującą rdzeń ekstrakcji i katalog pojazdów, oraz `7e405ff43ab83d3527745310b558a7d291f2e686` (pomocnicze VIN/tablice i katalog; API reguł/OCR bez zmian). Ten dokument i `service-contract/` są punktem odniesienia dla etapów 1–3 oraz 4–5. Nie oznaczają, że PR #2 został scalony lub że adapter i OCR na Androidzie już działają.

## Podział pracy

| Obszar | Właściciel aktualnej zmiany | Zasada |
| --- | --- | --- |
| `app/`, root Gradle, wersje narzędzi, model Room, nawigacja | Etapy 1–3 | Ta gałąź; bez kopiowania implementacji silnika |
| `service-engine/`, jego testy i projekt OCR | Autor PR #2 | Nie zmieniamy jego gałęzi ani plików w tym etapie |
| `service-contract/`, ten dokument | Wspólny kontrakt integracyjny | Zmiana identyfikatorów wymaga jawnej wersji i oceny obu stron |
| Licencja, katalog producenta, release Play | Osobna bramka | Nie zamykamy ich samym buildem lub testami |

Pracujemy na osobnych gałęziach. Akceptowane dokumenty Etapu 1 są już w `main`. PR #2 pozostaje osobną propozycją; nie włączamy jego katalogu demo do aplikacji, także w debug, bez odrębnej potrzeby testowej.

## 1. Narzędzia i granice modułów

Przyjęto dla fundamentu: **Kotlin 2.2.21, Gradle 8.13, AGP 8.11.1, JDK/JVM 17, minSdk 26, compile/targetSdk 36**. Źródłem wersji jest `gradle/libs.versions.toml`. Nie wybierano AGP/Kotlin „na ślepo”: sprawdzono [tabelę Kotlin/Gradle/AGP](https://kotlinlang.org/docs/gradle-configure-project.html), [wymagania AGP 8.11](https://developer.android.com/build/releases/agp-8-11-0-release-notes) oraz [plugin kompilatora Compose](https://developer.android.com/jetpack/androidx/releases/compose-kotlin). Przed wydaniem wymagania Play i aktualizacje zależności wracają do weryfikacji.

`service-contract` to mały moduł Kotlin/JVM, bez Androida, Room, OCR, sieci i interwałów. Aplikacja nie zależy jeszcze od `service-engine` — fundament nie wymaga przedwczesnego scalania Etapu 4.

Przy integracji PR #2:

1. W `service-engine/build.gradle.kts` użyć `alias(libs.plugins.kotlin.jvm)` zamiast osobnej wersji 2.1.21.
2. Dla zachowania samodzielnego builda silnika jego `settings.gradle.kts` może importować katalog `../gradle/libs.versions.toml`; w buildzie głównym katalog jest już dostępny.
3. Wyrównać wrapper samodzielnego silnika do 8.13 i sumy dystrybucji z root wrappera albo jednoznacznie pozostawić osobny, przetestowany build. Nie nadpisywać wrappera aplikacji plikami ze starszej gałęzi.
4. Dopiero wtedy dodać `include(":service-engine")` i adapter, a następnie uruchomić dotychczasowe testy silnika oraz testy adaptera w jednym buildzie. Obecne 39 testów nie dowodzi jeszcze poprawności mapowania z Room.

## 2. Kody czynności i operacji

Źródło prawdy: [ServiceTask.kt](../service-contract/src/main/kotlin/app/servicepassport/contract/ServiceTask.kt).

W bazie zachowujemy oddzielnie czynność i operację. Do `ServiceRule.taskCode`, `PerformedTask.taskCode` i mapowania OCR trafia **ta sama pełna para** w postaci `czynność:operacja`. Przykład: `engine_oil:replace` ≠ `engine_oil:inspect`. Sama kontrola poziomu oleju nie resetuje reguły wymiany.

| Kod czynności | Dozwolone operacje |
| --- | --- |
| `engine_oil` | `replace`, `inspect` |
| `oil_filter` | `replace`, `inspect` |
| `gearbox_oil` | `replace`, `inspect` |
| `brake_fluid` | `replace`, `inspect` |
| `coolant` | `replace`, `inspect` |
| `spark_plugs` | `replace`, `inspect` |
| `timing_belt` | `replace`, `inspect` |
| `air_filter` | `replace`, `inspect` |
| `cabin_filter` | `replace`, `inspect` |
| `fuel_filter` | `replace`, `inspect` |
| `brake_pads` | `replace`, `inspect` |
| `brake_discs` | `replace`, `inspect` |
| `inspection_service` | `inspect` |
| `brakes` | `inspect` |
| `battery_12v` | `test`, `replace` |

To identyfikatory, **nie zalecenia wykonania ani harmonogram**. Nie utożsamiamy kodu z językiem dokumentu. Opis nieznanej czynności może zostać w historii, ale bez arbitralnego dopasowania do reguł. `fromTaskCode` odrzuca nieznane i niepełne pary; nie robi rozmytego dopasowania. Nie używać obecnych krótkich kodów z fikcyjnych testów silnika jako alternatywnego słownika produkcyjnego.

### Tymczasowe kody ekstraktora z aktualizacji PR #2

`DefaultTaskDictionary` ma 13 kodów wielkimi literami. Przy integracji zastępujemy je pełnymi parami z kontraktu: `ENGINE_OIL` → `engine_oil:replace`, `OIL_FILTER` → `oil_filter:replace`, analogicznie pozostałe nazwy części **tylko po potwierdzeniu wymiany**. `INSPECTION_SERVICE` → `inspection_service:inspect`; ogólne „full service” nie potwierdza wymiany każdej części. Kontrole i testy wymagają własnej operacji, zamiast mapowania dowolnej wzmianki o części na `replace`.

Zalecany punkt integracji to jawnie wstrzyknięty słownik `DocumentExtractor(...)` oparty na kontrakcie. Nazwy tymczasowe nie stają się kodami historii produkcyjnej. `brakes:inspect`, `brake_pads:replace` i `brake_discs:replace` pozostają trzema różnymi pozycjami. Zmiana słownika nie zastępuje walidacji klasyfikacji PERFORMED, dopasowania pojazdu i potwierdzenia przez użytkownika.

## 3. Decyzje właściciela

- **Zatwierdzone:** zbliżający się termin = **1500 km lub 30 dni**, nieaktualny przebieg = **90 dni**. To progi prezentacji/przypomnień aplikacji; nie zmieniają terminu serwisu wynikającego ze źródła. Stała `AppPolicy` przechowuje te wartości. W Etapie 3 tylko próg 90 dni oznacza nieaktualny odczyt na pulpicie; 1500 km / 30 dni czekają na podłączenie reguł.
- **Decyzja w ramach pozostawionego wyboru:** opcjonalny `IndicatorReading` zostaje w API silnika, ale ekran i tabela ręcznego odczytu wskaźnika są **poza MVP**. Bez potrzebnego odczytu plan zmienny pokazuje „wymaga danych”. Nie dodajemy fikcyjnego odczytu ani stałego interwału zastępczego.
- Data pierwszej rejestracji jest osobna od roku modelowego; Etap 3 dodaje ją z migracją 1→2. Żaden rok nie jest automatycznie zamieniany na datę.

**Do synchronizacji opisu PR #2:** jego aktualizacja odnotowuje ekran wskaźnika jako przewidziany w aplikacji. W bieżącej rozmowie właściciel pozostawił wybór i przyjęto odłożenie tego ekranu poza MVP. Opcjonalny typ w silniku pozostaje zgodny; opis zakresu i przyszłe zadania UI wymagają dostosowania do tej decyzji.

Katalog `vehicle-catalog/` i pomocnicze VIN/tablice z PR #2 pozostają do osobnej integracji. Etap 3 zapisuje dane ręcznie, bez deklaracji ich weryfikacji lub dekodowania VIN. Dane `firstYear`/`lastYear` są latami pierwszych rejestracji w źródle, a nie rokiem modelowym i nie mogą go automatycznie uzupełniać. Ręczny wpis pozostaje dostępny dla aut spoza listy. Lista marek/modeli nie przypisuje harmonogramu ani nie zamyka bramki źródła.

## 4. Różnice wymagające rozwiązania przed adapterem

### A. Bieżący przebieg

Model logiczny aplikacji zakłada jawne `currentReadingId`. W schemacie Room v2 realizuje to tabela `current_odometer(vehicle_id, reading_id)`, z kluczem obcym sprawdzającym przynależność odczytu do auta. Odczyt starszej faktury nie ustala bieżącego przebiegu. API silnika z PR #2 przyjmuje listę odczytów i sam dołącza/przegląda odczyty z historii; nie ma jeszcze osobnego parametru autorytatywnego odczytu.

Przed integracją potrzebne jest rozszerzenie silnika lub jednoznacznie przetestowana polityka adaptera, która zachowa wybór aplikacji także wtedy, gdy historia zawiera późniejszy odczyt. Preferowany kierunek: opcjonalny jawny odczyt bieżący plus oddzielne odczyty do szacowania; historia nie może go po cichu zastępować. Nie naprawiamy tego przez usunięcie prawdziwych wpisów z historii przekazywanej do obliczeń.

### B. Wygaśnięcie źródła i licencja

Silnik może zwrócić wyliczony termin z `SOURCE_EXPIRED`. Aplikacja wymaga odrębnej bramki uprawnień/aktualności przed prezentacją zalecenia. Wygasły/wycofany plan nie może wyglądać jak nadal zweryfikowany tylko dlatego, że da się policzyć datę. Niepusty tekst `usageRights` nie jest dowodem faktycznej licencji.

### C. Punkty startowe i niewspierane reguły

PR #2 wspiera stałe pierwsze/kolejne interwały od startu eksploatacji oraz ostatniej czynności. Model aplikacji dopuszcza szersze rodzaje reguł, ale adapter może przekazać wyłącznie te, które silnik rzeczywiście obsługuje i które mają potwierdzoną podstawę. Czynności jednorazowe, wiek auta, inne kotwice i złożone warunki pozostają „brak obsługi/danych”, nie przybliżonym planem.

### D. Typy i jednostki

`LocalDate` dla dnia wykonania; UTC dla metadanych technicznych. Km są nieujemne. Przed konwersją do `Int` silnika sprawdzić zakres, bez obcinania i ograniczania po cichu. Brak przebiegu to `null`, nie 0. Data wystawienia faktury nie staje się automatycznie datą usługi.

### E. Zatwierdzanie OCR

Surowy `OcrRun` i `ServiceDraft` nie mają ścieżki do `PerformedTask`. Dopiero atomowe potwierdzenie tworzy wpis i wykonane, wybrane pozycje. Wycena, zalecenie i zakup części nie resetują interwału. Użytkownik może poprawić klasyfikację, lecz musi jawnie potwierdzić wykonanie; samo zaznaczenie pozycji nie jest obejściem blokady dokumentu obcego auta.

## 5. Trwały kontrakt Etapu 3

- Room v2: `vehicles`, `service_events`, uporządkowane `service_actions`, `odometer_readings` i jawna selekcja `current_odometer`. `GarageSnapshot` jest spójnym odczytem relacji w transakcji; źródłem integracji są `snapshot.currentReading`, `snapshot.readings` i `snapshot.history`. Nie wybierać `MAX(km)` ani najnowszej daty jako zamiennika selekcji.
- Wpis ręczny to potwierdzone przez użytkownika wykonane prace, bez OCR i szkiców. Para `action_code`/`operation_code` musi przejść `ServiceTask.fromTaskCode`; własny opis ma oba kody `null` i nie resetuje żadnej reguły. Stan potwierdzenia jest znaczeniem tej tabeli w v2; przyszłe szkice muszą mieć odrębny magazyn i atomowe zatwierdzenie.
- `performed_day`, `observed_day`, `first_registration_day`: `Long` epoch-day → `LocalDate.ofEpochDay`. Metadane utworzenia/zmiany: milisekundy UTC. Kilometry `Long` w zakresie 0..Int.MAX_VALUE; `null` przebiegu oznacza brak odczytu powiązanego z usługą. Koszt: opcjonalne całkowite øre i waluta NOK, bez `Double`.
- Jedna usługa ma 0 lub 1 powiązany odczyt, wiele prac i własne `revision`. Odczyt usługowy zmieniamy przez usługę. UUID formularza chroni ponowienie zapisu, a oczekiwana rewizja chroni przed nadpisaniem nieaktualnej wersji. Cały zapis/edycja/usunięcie odbywa się w transakcji.
- Dodanie historycznej usługi/odczytu nie zmienia selekcji bez jawnego wyboru. Usunięcie wybranego odczytu lub jego usługi usuwa selekcję; zmiana jego wartości/dnia bez ponownego potwierdzenia także ją czyści. Edycja samej notatki zachowuje selekcję. UI oznacza odczyt starszy niż 90 dni.
- Niespójne wartości, różne km tego samego dnia i wybór odczytu starszego od bieżącego wymagają ostrzeżenia, wyjaśnienia i potwierdzenia aktualnego zestawu danych. `exception_reason` nie oznacza przeliczenia skali licznika. Przy anomalii adapter nie może automatycznie prognozować średniego przebiegu ani uznać stanu za bezpieczny; potrzebna osobna obsługa w Etapie 4.
- Konfiguracja auta jest opcjonalna i ręcznie zadeklarowana. `configuration_revision` rośnie przy zmianie danych dopasowania, w tym roku/rejestracji/VIN/silnika/skrzyni/trybu, a nie przy zmianie samej nazwy. Brak pola i nieznane wartości nie są pełnym dopasowaniem planu. Kodów enum aplikacji nie rzutować bez jawnego mapowania na typy silnika.
- UI nadal obsługuje jedno auto, zgodnie z MVP. Własność odczytów/usług i selekcji jest wymuszana także kluczami obcymi, co zapobiega podpięciu odczytu innego auta.

## 6. Weryfikacja przy integracji — jeszcze nie wykonana

### Dodatki PR #2 odnotowane podczas Etapu 3

Przy `7b966d61a9565ad39c2c7c70bcf40de4e0fa904f` PR #2 dodaje planowanie przypomnień, propozycję formatu eksportu z opcjonalnym hasłem i szkielet serwera rejestru. Nie zmienia dotychczasowych plików API reguł/OCR, ale dodaje plugin Kotlin serialization 2.1.21 i bibliotekę JSON. Przy przyszłym scalaniu także plugin serializacji musi korzystać ze wspólnych wersji. Te dodatki nie są podłączone, przetestowane z Room ani przyjęte jako zakończone etapy aplikacji. Nie zmieniają zakresu ani decyzji tego Etapu 3.

Eksport trzeba uzgodnić z rzeczywistym schematem v2: zachować identyfikatory i jawny wybór bieżącego odczytu, pełne pary czynność/operacja, własne opisy, powiązania i uwagi odczytów, nieznany przebieg, pełny zakres kosztu oraz opcjonalną konfigurację auta. Format, który odtwarza tylko dzień i km, nie wystarcza do bezstratnej kopii tej bazy. Test archiwum w izolacji nie zastępuje testu Room → archiwum → nowa baza.

### Testy granicy aplikacja/silnik

- `engine_oil:inspect` nie resetuje `engine_oil:replace`;
- potwierdzone pozycje trafiają do silnika, szkice/wyceny nie;
- dwa kliknięcia/ponowienie po awarii tworzą jedną usługę;
- jawny bieżący odczyt pozostaje podstawą także po dodaniu starszej/nowszej faktury;
- nieaktualne źródło traci status zweryfikowany, a dane demo nie trafiają do aplikacji;
- brak historii nie jest przedstawiany jako dowód zaniedbania;
- kategorie i kody przyczyn tłumaczone z zasobów NO/EN, szacowany miesiąc oddzielony od daty źródłowej;
- nieobsługiwany typ reguły daje stan braku obsługi, bez fałszywego terminu.

Zmiana kodu, typu pola, znaczenia statusu albo pochodzenia wymaga aktualizacji tego kontraktu i testów na granicy modułów. Pozostawienie osobnych gałęzi nie zastępuje tej weryfikacji.
