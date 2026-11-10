# Uzgodnienie modułów z aplikacją (po audycie obu gałęzi)

Data: **10 listopada 2026**. Punktem odniesienia jest [INTEGRATION.md](INTEGRATION.md) prowadzony przez autora etapów 1–3 oraz wspólny kontrakt `service-contract/`. Ten dokument opisuje, co zmieniono po stronie modułów `service-engine`, `lookup-server` i `vehicle-catalog`. Gałąź zawiera teraz `main` (etapy 0–2); Etap 3 nie jest jeszcze w `main`, więc porównanie ze schematem bazy v2 opiera się na odczycie gałęzi `codex/etap-3-manual-history`.

## Rozbieżności i ich stan

| # | Rozbieżność | Co zrobiono | Stan |
| --- | --- | --- | --- |
| 1 | Aplikacja ma jawnie wybrany bieżący przebieg, silnik brał najnowszy z historii | `EvaluationInput.currentOdometer`: `Selected(odczyt)`, `NotSelected` albo `LatestKnown` (tylko dla testów i narzędzi). Silnik nie zastępuje wyboru; nowszy, wyższy odczyt w historii daje zastrzeżenie `NEWER_ODOMETER_IN_HISTORY` | Zrobione po stronie silnika; adapter musi zawsze przekazać `Selected`/`NotSelected` |
| 2 | Odczyt faktur używał tymczasowych kodów bez operacji | Słownik ma 15 czynności kontraktu i ich dozwolone operacje; pozycja ma `action`, `operation` i kod `czynność:operacja`. Kontrola i test nie są wymianą. Test porównuje słownik z plikiem kontraktu | Zrobione |
| 3 | Eksport nie był bezstratny; wymagał marki i modelu | Format odwzorowuje tabele bazy v2: identyfikatory odczytów, wybór bieżącego odczytu, pary kodów lub własny opis w kolejności, wyjaśnienia anomalii, rewizje, znaczniki czasu. Auto z samą nazwą jest poprawne | Zrobione po stronie formatu; **test baza → kopia → nowa baza nadal nie istnieje** |
| 4 | `InputStream.readNBytes` wymaga Androida 13 | Usunięte; to było jedyne takie wywołanie w kodzie modułu | Zrobione; **nie sprawdzono na urządzeniu z Androidem 8–12** |
| 5 | Zatwierdzanie szkicu bez bramek | `DraftConfirmation.confirm` odrzuca: kod bez postaci `czynność:operacja`, niepotwierdzone osobno ostrzeżenia blokujące (inny pojazd, wycena), koszt w walucie innej niż NOK lub bez waluty | Zrobione w module; zgodność kodu z kontraktem i atomowy zapis pozostają w aplikacji |
| 6 | Przypomnienia ignorowały zastrzeżenia silnika | Wygasłe źródło i wynik zależny od km przy niespójnym liczniku nie dają przypomnienia; trafiają do `ReminderPlan.suppressed`. Wątpliwości co do licznika dają jedno przypomnienie o przebiegu | Zrobione; prezentacja na pulpicie należy do aplikacji |
| 7 | Dwa powtórzenia nazw w katalogu aut | Akcenty nie tworzą już osobnych pozycji; lista ma 1746 modeli | Zrobione |
| 8 | Różne wersje narzędzi | Oba moduły biorą wersje z `gradle/libs.versions.toml` i używają tego samego wrappera (Kotlin 2.2.21, Gradle 8.13) | Zrobione dla samodzielnych buildów; **moduły nie są jeszcze włączone do głównego builda** |

## Otwarte

- **Ekran wskaźnika serwisowego.** Po stronie modułów zapisano decyzję właściciela „tak”; INTEGRATION.md odnotowuje ekran jako poza MVP. Typ `IndicatorReading` w silniku jest zgodny z obiema wersjami. **Rozstrzyga właściciel.**
- **Wtyczka serializacji** nie ma wpisu we wspólnym katalogu wersji; moduł bierze dla niej wersję Kotlina z katalogu, a wersję biblioteki JSON (1.8.1) trzyma lokalnie. Przy włączaniu do głównego builda warto dodać oba wpisy do katalogu — to plik aplikacji, więc zmiana po uzgodnieniu.
- **Brak testów wspólnego przepływu:** baza → silnik → pulpit oraz baza → kopia → odtworzona baza. Wymagają adaptera w aplikacji.
- **Wygasłe źródło:** silnik nadal liczy termin i dodaje `SOURCE_EXPIRED`; aplikacja ma własną bramkę prezentacji. Przypomnienia już tego nie wysyłają.
- **Niedopatrzenie w Etapie 3** zgłoszone przez jego autora (`selectCurrent()` omija ostrzeżenie) jest po stronie aplikacji i nie było tu ruszane.
- **Nazwa:** aplikacja to „CarServiceAssistent”; starsze dokumenty modułów używają roboczej nazwy „AI Service Passport”.

## Wskazówki dla adaptera

```kotlin
EvaluationInput(
    today = ...,
    inServiceDate = vehicle.firstRegistrationDay?.let(LocalDate::ofEpochDay),
    rules = ...,                       // taskCode = "engine_oil:replace"
    history = potwierdzone prace z kodami; własne opisy pomijamy,
    odometer = wszystkie odczyty,      // do wykrywania niespójności i szacunku
    currentOdometer = snapshot.currentReading?.let { CurrentOdometer.Selected(...) } ?: CurrentOdometer.NotSelected,
)
```

Progi `AppPolicy` (1500 km, 30 dni, 90 dni) przekazywać jawnie do `EngineSettings`, żeby wartości nie żyły w dwóch miejscach.
