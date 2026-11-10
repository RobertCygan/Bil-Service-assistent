# Wspólny kontrakt aplikacji i silnika — v1

Stan: **10 listopada 2026**. Odczytano PR #2 przy commicie `ff102f4d1c50d0974e8c6792270effc26894e4a3`. Ten dokument i `service-contract/` są punktem odniesienia dla etapów 1–3 oraz 4–5. Nie oznaczają, że PR #2 został scalony lub że adapter i OCR już działają.

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
| `gearbox_oil` | `replace`, `inspect` |
| `brake_fluid` | `replace`, `inspect` |
| `coolant` | `replace`, `inspect` |
| `spark_plugs` | `replace`, `inspect` |
| `timing_belt` | `replace`, `inspect` |
| `air_filter` | `replace`, `inspect` |
| `cabin_filter` | `replace`, `inspect` |
| `brakes` | `inspect` |
| `battery_12v` | `test`, `replace` |

To identyfikatory, **nie zalecenia wykonania ani harmonogram**. Nie utożsamiamy kodu z językiem dokumentu. Opis nieznanej czynności może zostać w historii, ale bez arbitralnego dopasowania do reguł. `fromTaskCode` odrzuca nieznane i niepełne pary; nie robi rozmytego dopasowania. Nie używać obecnych krótkich kodów z fikcyjnych testów silnika jako alternatywnego słownika produkcyjnego.

## 3. Decyzje właściciela

- **Zatwierdzone:** zbliżający się termin = **1500 km lub 30 dni**, nieaktualny przebieg = **90 dni**. To progi prezentacji/przypomnień aplikacji; nie zmieniają terminu serwisu wynikającego ze źródła. W Etapie 2 nie są jeszcze stosowane przez pulpit, bo silnik nie jest podłączony.
- **Decyzja w ramach pozostawionego wyboru:** opcjonalny `IndicatorReading` zostaje w API silnika, ale ekran i tabela ręcznego odczytu wskaźnika są **poza MVP**. Bez potrzebnego odczytu plan zmienny pokazuje „wymaga danych”. Nie dodajemy fikcyjnego odczytu ani stałego interwału zastępczego.
- Data pierwszej rejestracji pozostaje osobna od roku modelowego; zostanie dodana do danych pojazdu w Etapie 3 z rzeczywistą migracją.

## 4. Różnice wymagające rozwiązania przed adapterem

### A. Bieżący przebieg

Model aplikacji zakłada jawne `currentReadingId`. Odczyt starszej faktury nie ustala bieżącego przebiegu. API silnika z PR #2 przyjmuje listę odczytów i sam dołącza/przegląda odczyty z historii; nie ma jeszcze osobnego parametru autorytatywnego odczytu.

Przed integracją potrzebne jest rozszerzenie silnika lub jednoznacznie przetestowana polityka adaptera, która zachowa wybór aplikacji także wtedy, gdy historia zawiera późniejszy odczyt. Preferowany kierunek: opcjonalny jawny odczyt bieżący plus oddzielne odczyty do szacowania; historia nie może go po cichu zastępować. Nie naprawiamy tego przez usunięcie prawdziwych wpisów z historii przekazywanej do obliczeń.

### B. Wygaśnięcie źródła i licencja

Silnik może zwrócić wyliczony termin z `SOURCE_EXPIRED`. Aplikacja wymaga odrębnej bramki uprawnień/aktualności przed prezentacją zalecenia. Wygasły/wycofany plan nie może wyglądać jak nadal zweryfikowany tylko dlatego, że da się policzyć datę. Niepusty tekst `usageRights` nie jest dowodem faktycznej licencji.

### C. Punkty startowe i niewspierane reguły

PR #2 wspiera stałe pierwsze/kolejne interwały od startu eksploatacji oraz ostatniej czynności. Model aplikacji dopuszcza szersze rodzaje reguł, ale adapter może przekazać wyłącznie te, które silnik rzeczywiście obsługuje i które mają potwierdzoną podstawę. Czynności jednorazowe, wiek auta, inne kotwice i złożone warunki pozostają „brak obsługi/danych”, nie przybliżonym planem.

### D. Typy i jednostki

`LocalDate` dla dnia wykonania; UTC dla metadanych technicznych. Km są nieujemne. Przed konwersją do `Int` silnika sprawdzić zakres, bez obcinania i ograniczania po cichu. Brak przebiegu to `null`, nie 0. Data wystawienia faktury nie staje się automatycznie datą usługi.

### E. Zatwierdzanie OCR

Surowy `OcrRun` i `ServiceDraft` nie mają ścieżki do `PerformedTask`. Dopiero atomowe potwierdzenie tworzy wpis i wykonane, wybrane pozycje. Wycena, zalecenie i zakup części nie resetują interwału. Użytkownik może poprawić klasyfikację, lecz musi jawnie potwierdzić wykonanie; samo zaznaczenie pozycji nie jest obejściem blokady dokumentu obcego auta.

## 5. Weryfikacja przy integracji — jeszcze nie wykonana

- `engine_oil:inspect` nie resetuje `engine_oil:replace`;
- potwierdzone pozycje trafiają do silnika, szkice/wyceny nie;
- dwa kliknięcia/ponowienie po awarii tworzą jedną usługę;
- jawny bieżący odczyt pozostaje podstawą także po dodaniu starszej/nowszej faktury;
- nieaktualne źródło traci status zweryfikowany, a dane demo nie trafiają do aplikacji;
- brak historii nie jest przedstawiany jako dowód zaniedbania;
- kategorie i kody przyczyn tłumaczone z zasobów NO/EN, szacowany miesiąc oddzielony od daty źródłowej;
- nieobsługiwany typ reguły daje stan braku obsługi, bez fałszywego terminu.

Zmiana kodu, typu pola, znaczenia statusu albo pochodzenia wymaga aktualizacji tego kontraktu i testów na granicy modułów. Pozostawienie osobnych gałęzi nie zastępuje tej weryfikacji.
