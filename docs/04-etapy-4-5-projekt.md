# Etapy 4 i 5 — projekt oraz silnik reguł

Data: **10 listopada 2026**. Gałąź: `codex/etap-0-plan`. Dokument i moduł `service-engine/` powstały **bez wglądu w `main` i gałęzie etapów 1–3**; nie zmieniają żadnego istniejącego pliku.

## Status — co jest, a czego nie ma

| Element | Stan |
| --- | --- |
| Silnik terminów, dopasowanie planu, bramka katalogu (`service-engine/`, pakiet `engine`) | **Zaimplementowane i przetestowane** (39 testów, czysty Kotlin/JVM) |
| Rdzeń Etapu 5: ekstrakcja z tekstu OCR, szkic, zatwierdzanie, limity, duplikaty (pakiet `extraction`) | **Zaimplementowane i przetestowane** (22 testy) — wyłącznie na zmyślonych dokumentach |
| Zweryfikowane reguły producenta | **Brak.** Bramka danych z [01-dane-i-zrodla.md](01-dane-i-zrodla.md) nadal otwarta; katalog produkcyjny jest pusty |
| Dane w testach | **Wyłącznie demonstracyjne**, fikcyjny pojazd „Demo Motors Example”, zmyślone interwały |
| Pulpit (ekran Compose), adapter do bazy Room | **Nie ma** — wymaga kodu etapów 1–3 |
| Etap 5 na Androidzie: aparat, wybór pliku, PdfRenderer, ML Kit, ekran sprawdzenia, zapis do bazy | **Nie ma** — wymaga kodu etapów 1–3 |
| Pomiar jakości na prawdziwych dokumentach NO/EN | **Niewykonany** — brak zestawu testowego; kryterium odbioru Etapu 5 niespełnione |

Etapy 4 i 5 **nie są ukończone**: ukończone są ich części niezależne od aplikacji, od dostawcy danych i od prawdziwych dokumentów.

## Etap 4 — jak działa silnik

Jedna czysta funkcja: `ServiceEngine.evaluate(EvaluationInput): List<TaskStatus>`. Bez bazy, sieci, zegara i tekstów; `today` jest parametrem, więc każdy wynik da się odtworzyć w teście.

**Wejście:** reguły (z planu i/lub własne użytkownika), zatwierdzone czynności, odczyty przebiegu z datami, data rozpoczęcia eksploatacji, opcjonalne odczyty wskaźnika serwisowego z auta, progi aplikacji.

**Wynik dla każdej reguły:** grupa pulpitu, termin w km i/lub data, pozostało km/dni, szacowany miesiąc, **podstawa obliczenia** (`Basis`: punkt startu, zastosowany interwał, użyty odczyt przebiegu z datą, wymiar decydujący) i kody zastrzeżeń (`Reason`). Źródło reguły (id, wersja, data weryfikacji, pochodzenie) jedzie razem z wynikiem.

### Zasady rozstrzygania

| Sytuacja | Wynik |
| --- | --- |
| Interwał km i czas | Obowiązuje ten, który nastąpi wcześniej; `decidedBy` mówi który |
| Jest potwierdzona czynność | Liczymy od najnowszej (data, przebieg) interwałem `repeat` |
| Brak czynności, pierwszy termin jeszcze nie minął | Liczymy od startu eksploatacji interwałem `first` (lub `repeat`, gdy źródło nie różnicuje) |
| Brak czynności, pierwszy termin wg planu minął | **„Wymaga danych”** + `NO_CONFIRMATION_IN_DOCUMENTS`. Nigdy „zaległe” |
| Przekroczony termin przy istniejącej historii | „Zaległe” |
| Brak odczytu przebiegu / ostatni wpis bez przebiegu / brak daty startu | „Wymaga danych” ze wskazaniem, czego brakuje. Wyjątek: gdy znany wymiar już jest przekroczony → „Zaległe” |
| Plan zmienny bez odczytu wskaźnika | „Wymaga danych” + `VARIABLE_SCHEDULE`; nigdy „bez pilnej potrzeby”. „Zaległe” tylko po przekroczeniu górnej granicy podanej przez źródło |
| Plan zmienny z odczytem wskaźnika zrobionym po ostatnim serwisie | Termin z odczytu auta; podstawa `VEHICLE_INDICATOR` |
| Odczyt przebiegu starszy niż 90 dni | Zastrzeżenie `ODOMETER_STALE`; jeśli szacunek wskazuje, że próg mógł już minąć → „Wymaga danych” + `ODOMETER_UPDATE_REQUIRED` |
| Termin dokładnie dziś / 0 km | „Zbliża się”, jeszcze nie „zaległe” |
| Wpisy z datą przyszłą | Pomijane, z zastrzeżeniem `FUTURE_ENTRIES_IGNORED` |
| Źródło po dacie ważności | Wynik liczony, z zastrzeżeniem `SOURCE_EXPIRED` |

**Szacunki:** średni przebieg liczymy z odczytów z ostatnich 365 dni (min. 30 dni rozpiętości, min. 2 odczyty, tylko spójne dane). Wynik to **miesiąc**, nie dzień (`estimatedKmDueMonth`), i jest osobnym polem — nie miesza się z terminem wynikającym z reguły. Gdy danych jest za mało, szacunku nie ma.

**Progi aplikacji** (`EngineSettings`: „zbliża się” = 1500 km / 30 dni, nieaktualny przebieg = 90 dni) to ustawienia aplikacji, nie zalecenia producenta. Wartości domyślne są moją propozycją do zatwierdzenia.

### Przypisanie planu

`PlanResolver.resolve(konfiguracja, katalog)` zwraca:

- `Assigned` — tylko przy zgodności **wszystkich** cech z zakresu planu (marka, model, rok modelowy, kod silnika/skrzyni, rynek, system serwisowy, profil eksploatacji — tyle, ile wymaga dany plan);
- `NoVerifiedSchedule(missingAttributes)` — „Brak potwierdzonego harmonogramu” z listą brakujących danych;
- `Ambiguous` — dwa plany pasują jednocześnie; to błąd katalogu, nie wybieramy „podobniejszego”.

Normalne i ciężkie warunki oraz stały/zmienny system to osobne plany rozróżniane cechą zakresu, więc bez tej informacji plan nie zostanie przypisany.

### Dane demonstracyjne a produkcyjne

1. Każde źródło ma `origin`: `VERIFIED_SOURCE`, `USER_DEFINED` albo `DEMO`.
2. Katalog demo leży w `src/testFixtures` — fizycznie nie wchodzi do artefaktu produkcyjnego (sprawdzone: w jar nie ma klas demo).
3. `CatalogValidator.productionIssues()` odrzuca katalog z pochodzeniem innym niż `VERIFIED_SOURCE`, pustymi polami źródła/licencji, datą weryfikacji z przyszłości, pustym zakresem, duplikatami. Build wydania ma się na tym zatrzymywać.

### Integracja z etapami 1–3

Moduł ma własne, minimalne typy wejściowe. Aplikacja potrzebuje **jednego adaptera** (mapowanie encji Room → wejście silnika); silnik nie zna Room, Compose ani Androida.

| Silnik oczekuje | Skąd w aplikacji | Uwaga |
| --- | --- | --- |
| `PerformedTask(taskCode, date, odometerKm?)` | Zatwierdzone czynności z wpisów serwisowych | **Szkice OCR nie mogą tu trafić** — filtruje adapter |
| `OdometerReading(date, km)` | Odczyty przebiegu | Służą do wykrywania niespójności i szacunku |
| `currentOdometer` | Jawnie wybrany bieżący odczyt | `Selected(odczyt)` albo `NotSelected`; silnik nie zastępuje wyboru odczytem z historii |
| `inServiceDate` | Data pierwszej rejestracji pojazdu | Jeśli model z Etapu 1 jej nie ma — potrzebne pole (opcjonalne) |
| `Map<ConfigAttribute, String>` | Pola konfiguracji pojazdu | Brak pola = brak wpisu w mapie |
| `taskCode` | Wspólny kontrakt `service-contract` | Pełna para `czynność:operacja`, np. `engine_oil:replace`. Odczyt faktur zwraca te same kody. Szczegóły: [09-uzgodnienie-z-aplikacja.md](09-uzgodnienie-z-aplikacja.md) |
| `Reason`, `Category` → tekst | Zasoby `nb-NO` / `en` | Silnik nie zawiera tekstów dla użytkownika |

Podłączenie do builda: `include(":service-engine")` w głównym `settings.gradle.kts` + `implementation(project(":service-engine"))`; dane demo tylko w debug: `debugImplementation(testFixtures(project(":service-engine")))`. Wymaga JVM 17 i pluginu Kotlin — przy integracji wersję pluginu trzeba zrównać z aplikacją. `java.time` jest dostępne od API 26 (zgodne z rekomendacją z Etapu 0).

### Ograniczenia i świadome uproszczenia

- Brak czynności jednorazowych (np. „wymień raz przy X km”) i interwałów zależnych od wieku auta (np. „po 10 latach co rok”). Dodam, gdy realne źródło ich wymaga — nie projektuję pod przypuszczenia.
- Format pliku katalogu (JSON/inna dystrybucja) nie jest zdefiniowany: zależy od dostawcy i licencji.
- Odczyt wskaźnika serwisowego (`IndicatorReading`) to propozycja wykraczająca poza model z briefu; jest opcjonalna. Bez niej plany zmienne zawsze trafią do „Wymaga danych”.
- Gdy dla jednej czynności istnieje reguła z planu i własna użytkownika, silnik zwraca obie, każdą z jej pochodzeniem. Sposób prezentacji to decyzja UX.

### Jak sprawdzić

```bash
cd service-engine
./gradlew build
```

Raport: `service-engine/build/reports/tests/test/index.html`. Workflow `.github/workflows/service-engine.yml` uruchamia to samo tylko przy zmianach w `service-engine/`.

## Etap 5 — rdzeń ekstrakcji

Ten sam wzorzec co w Etapie 4: logika w czystym Kotlinie, Android tylko na brzegach.

```
plik → walidacja (typ, ≤20 MB, ≤20 stron, SHA-256) → strony jako obrazy (PdfRenderer)
     → ML Kit OCR (Android)  ──►  OcrPage(linie + ramki)          ← surowy wynik, zapisany bez zmian
     → DocumentExtractor (czysty Kotlin)  ──►  DraftProposal      ← szkic, osobna tabela
     → ekran sprawdzenia (korekta + podgląd fragmentu)
     → zatwierdzenie (jedna transakcja)  ──►  wpis serwisowy + PerformedTask
```

**Granica testowalności:** `DocumentExtractor` przyjmuje tekst z ramkami, a nie obraz. Dzięki temu ekstrakcję testujemy na JVM na zapisanych wynikach OCR, bez telefonu, a jakość samego OCR mierzymy osobno na urządzeniu.

**Zaimplementowane** (pakiet `app.servicepassport.extraction`): `DocumentExtractor.extract(strony, pojazd, dziś)`, `DraftConfirmation.confirm()`, `ImportLimits.check()`, `DuplicateCheck.find()`, startowy słownik NO/EN (13 czynności, **kody tymczasowe**). **Niezaimplementowane:** wszystko po stronie Androida oraz przechowywanie surowego OCR i szkicu w bazie.

**Szkic (`DraftProposal`)** — każde pole ma wartość, stan (rozpoznano / sprawdź / nie znaleziono), listę kandydatów i wskazanie strony/linii/ramki, z której pochodzi:

| Pole | Zasada |
| --- | --- |
| Data usługi | Kandydaci z etykietami (`dato`, `utført`, `fakturadato`, `date`…). Data faktury ≠ data usługi: przy kilku kandydatach pole jest „do sprawdzenia”, bez cichego wyboru |
| Przebieg | Liczba przy `km`, `kilometerstand`, `km-stand`, `mileage`, `odometer`; mile oznaczane i **nie** przeliczane po cichu |
| Pojazd | Numer rejestracyjny / VIN z dokumentu porównywany z pojazdem; niezgodność = ostrzeżenie blokujące domyślne zatwierdzenie |
| Pozycje | Oryginalny tekst linii zachowany zawsze; mapowanie na `taskCode` przez słownik NO/EN |
| Rodzaj pozycji | `PERFORMED` / `RECOMMENDED` / `QUOTE` / `PARTS_ONLY` / `UNCLEAR` |
| Koszt | Opcjonalnie, z walutą z dokumentu |

**Klasyfikacja pozycji** jest regułowa i ostrożna. Typ dokumentu (`tilbud`, `pristilbud`, `estimate`, `quote` → wszystko `QUOTE`), sekcje (`anbefalt`, `bør utføres`, `recommended` → `RECOMMENDED`), sama część bez robocizny/czasownika wykonania (`olje 5W-30 4 l`) → `PARTS_ONLY`. Tylko `PERFORMED` jest domyślnie zaznaczone do zapisu; resztę użytkownik może włączyć ręcznie. Bez procentów pewności — stany „rozpoznano / sprawdź / nie rozpoznano”.

**Duplikaty:** identyczny plik po SHA-256; osobno ostrzeżenie, gdy istnieje wpis z tą samą datą i przebiegiem dla tego pojazdu (inne zdjęcie tej samej faktury).

**Kontrakt z Etapem 4:** silnik widzi wyłącznie to, co wyszło z zatwierdzenia. Szkic, surowy OCR i pozycje inne niż zatwierdzone `PERFORMED` nie mają drogi do `EvaluationInput`.

### Znane ograniczenia ekstrakcji

- Reguły powstały na **zmyślonych** dokumentach napisanych przeze mnie. Nie wiem jeszcze, jak sprawdzą się na prawdziwych fakturach norweskich warsztatów; liczby jakości nie istnieją.
- Wejściem jest tekst linii. Prawdziwy ML Kit może dzielić wiersze tabel inaczej (np. kwota w osobnej linii) — to wyjdzie dopiero na urządzeniu.
- Pozycje spoza słownika nie trafiają do szkicu; użytkownik dodaje je ręcznie.
- Numer rejestracyjny: tylko norweski format samochodowy (2 litery + 5 cyfr). VIN nie jest korygowany przy typowych pomyłkach OCR (0/O, 1/I).
- Data dd/mm zapisana z ukośnikiem jest czytana jako dzień/miesiąc i oznaczana „sprawdź”.
- Tekst typu „odometer … 1” zostanie odczytany jako przebieg — reguły nie odróżniają treści złośliwej od prawdziwej. Zabezpieczeniem jest obowiązkowe sprawdzenie przez użytkownika, nie ekstraktor.

**Bez LLM w pierwszym wariancie**, zgodnie z Etapem 0. Jeśli pomiar na zestawie 40 dokumentów nie osiągnie celów, decyzja o chmurze wraca do właściciela.

**Zależności blokujące start Etapu 5:** model danych dokumentu/szkicu z Etapu 1, słownik czynności, prywatny zestaw prawdziwych dokumentów NO/EN (poza repozytorium).

## Decyzje

Zatwierdzone przez właściciela 10 listopada 2026:

- progi aplikacji zostają: „zbliża się” = 1500 km / 30 dni, nieaktualny przebieg = 90 dni;
- odczyt wskaźnika serwisowego z auta jest danymi wejściowymi dla planów zmiennych (wymaga ekranu i tabeli w aplikacji).

Otwarte:

1. **Słownik czynności:** lista kodów z etapów 1–3, gdy będzie stabilna — zastąpi kody tymczasowe w `DefaultTaskDictionary`.
2. **Prywatny zestaw prawdziwych dokumentów NO/EN** (min. 40 wg Etapu 0, poza repozytorium) — bez niego nie da się zmierzyć jakości ani zamknąć Etapu 5.
3. **Bramka danych** (TecRMI/Autodata) nadal decyduje, czy Etap 4 da się zamknąć produkcyjnie.
