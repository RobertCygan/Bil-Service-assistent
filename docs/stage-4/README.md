# Etap 4 — pulpit NO/EN i własne interwały

Data: **10 listopada 2026**, Europe/Berlin. [PR #7](https://github.com/RobertCygan/Bil-Service-assistent/pull/7), gałąź `codex/stage-4-dashboard`. Etap 3 i przygotowanie integracji są scalone do `main` w PR #5 i #6. **Ten przyrost jest oddzielny; nie zamyka bramki danych producentów całego Etapu 4.**

## 1. Cel

Połączyć rzeczywistą bazę i silnik obu modeli w jednej aplikacji oraz pokazać kierowcy wynik, jego podstawę i brakujące dane. Udostępnić przewidziane w briefie własne interwały jako jawne ustawienia użytkownika, kiedy nie ma zweryfikowanego planu producenta.

## 2. Co powstało

- Produkcyjny `EngineAdapter`: konfiguracja, potwierdzone prace z pełną parą czynność/operacja, historia i jawny bieżący odczyt. Brak selekcji nie jest zastępowany najnowszym odczytem.
- Pulpit i podsumowanie w NO/EN: zaległe, zbliżające się, wymagające danych i bez pilnej potrzeby. Szczegóły rozdzielają próg km/datę, zapisany przebieg, kotwicę, pierwszy/kolejny/górny interwał, źródło, zastrzeżenia oraz szacowany miesiąc.
- Dodawanie, edycja i usuwanie własnych interwałów dla 15 czynności kontraktu i ich dozwolonych operacji. Puste pola początkowe, km i/lub miesiące; bez predefiniowanych liczb. Etykieta „Your interval” / „Ditt intervall” odróżnia je od planu producenta.
- Własny termin liczy się od ostatniej potwierdzonej zgodnej czynności. Brak wpisu daje potrzebę danych. Kontrola nie resetuje wymiany. Usunięcie reguły zachowuje historię.
- Room v3 z migracją 2→3, rewizjami i transakcjami; zachowana ścieżka 1→2→3. Zmiana danych przelicza pulpit, a data odświeża się po wznowieniu i co minutę aktywnej aplikacji.
- Bramka źródła: pełne dopasowanie konfiguracji, poprawny katalog i osobne aktualne uprawnienie do konkretnej wersji źródła. Źródła wygasłe, wycofane i demo nie dają zaleceń producenta. Katalog produkcyjny pozostaje pusty.
- Wspólny root build i katalog wersji Kotlin/serializacji/JSON. Silnik importowany z PR #2 przy `525c223a9b4377a8490ddb464010fa2128a99ca0`, z zachowaniem historii jako drugiego rodzica commita. Kod jego algorytmów i gałąź autora nie zostały zmienione.

## 3. Jak sprawdzić

APK debug **0.3.0-dev**, versionCode **3**, pakiet `app.servicepassport.android.debug`, Android **8.0 lub nowszy**. APK i raporty: **[APK i raporty z CI](https://github.com/RobertCygan/Bil-Service-assistent/actions/runs/42883641021/artifacts/13795671151)**. Pobranie artefaktu wymaga logowania na GitHubie; retencja 7 dni. W ZIP szukaj `app/outputs/apk/debug/app-debug.apk` lub `app/build/outputs/apk/debug/app-debug.apk`, zależnie od struktury archiwum.

Rzeczywiste zrzuty Androida: [pulpit NO](dashboard-no.png), [pulpit EN](dashboard-en.png), [podstawa wyniku EN](forecast-en.png), [pulpit NO po restarcie procesu](dashboard-no-restarted.png). „Foundation test car” i wpisane w testach liczby są fikcyjnym scenariuszem, a nie danymi startowymi aplikacji ani zaleceniami producenta.

1. Dodaj auto i aktualny przebieg 59 000 km.
2. Dodaj historyczny wpis z wymianą oleju i kontrolą hamulców przy 49 000 km oraz osobny wpis z wymianą filtra powietrza przy 48 000 km. Zachowaj poprawną kolejność dat/przebiegów.
3. Otwórz **Service overview / Serviceoversikt → Your intervals / Dine intervaller**. Dodaj własne interwały: wymiana oleju 9000 km, kontrola hamulców 10 000 km, wymiana filtra powietrza 30 000 km, wymiana filtra oleju 5000 km.
4. Pulpit powinien pokazać po jednej pozycji w każdej kategorii. Filtr oleju wymaga danych, bo brak potwierdzonej wymiany. Szczegóły oleju pokazują 49 000 + 9000 = 58 000 km i bieżące 59 000 km, z etykietą własnego interwału.
5. Edytuj interwał i sprawdź natychmiastowe przeliczenie. Usuń go i sprawdź, że historia pozostała. Spróbuj pustego/zerowego interwału oraz duplikatu tej samej pary.
6. Usuń wybrany bieżący odczyt. Wynik zależny od km ma poprosić o nowy, bez użycia starszej faktury/usługi. Przy braku km wynik czasowy może nadal mieć niezależną, potwierdzoną podstawę.
7. Zmień język na Bokmål, całkowicie zatrzymaj proces i uruchom ponownie. Sprawdź profil, przebieg, interwały, cztery kategorie oraz język.

Te liczby służą wyłącznie sprawdzeniu zachowania. Do używania aplikacji wpisz własne ustawienia, które chcesz śledzić.

JDK 17, Platform 36, Build Tools 35.0.0:

```sh
./gradlew :service-contract:test :service-engine:check :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
python3 scripts/check_process_restart.py
```

`check_process_restart.py` uruchamiaj po `FoundationSmokeTest` na testowym emulatorze. Aktualizacja zachowująca istniejące dane wymaga podpisu zgodnego z zainstalowanym APK; oddzielne maszyny CI mogą generować różne klucze debug. Nie usuwaj potrzebnej historii w celu obejścia podpisu; trwały klucz należy do etapu wydania.

## 4. Weryfikacja

Sprawdzony kod: **`0143da2c4ba47e04eba65cb433209f1fa1a1019c`**. Import modułów: `525c223a9b4377a8490ddb464010fa2128a99ca0`. [Raport maszynowy](verification.json), [restart procesu](process-restart.json).

| Sprawdzenie | Wynik |
| --- | --- |
| Aplikacja: baza, migracja, walidacja, mapowanie, rzeczywisty silnik i pulpit | **59 testów JVM**, 0 błędów |
| Wspólny kontrakt | **3 testy**, 0 błędów |
| Silnik, ekstrakcja, eksport, przypomnienia i VIN w module | **111 testów**, 0 błędów |
| APK debug i testowy | Zbudowane lokalnie i w CI |
| Android Lint | **0 błędów, 26 ostrzeżeń**: 22 o aktualizacjach zależności/narzędzi, 1 localeConfig od API 33, 3 nieużywane zasoby |
| Android 15 / API 35 | **4 testy, 0 błędów: pełny przepływ historii/interwałów NO/EN, migracje v1/v2 i biblioteka silnika** |
| Android 8.0 / API 26 | **3 testy, 0 błędów: migracje v1/v2 do v3 oraz silnik/archiwum** |
| Pełny restart procesu | **Przeszło: profil, jawny przebieg 59 000 km, 4 interwały, po 1 wyniku w każdej kategorii i nb-NO** |
| Oddzielne buildy importowanych modułów | **`service-engine`, `lookup-server` i `vehicle-catalog`: sukces w CI** |
| Kontrola APK | Klasa rzeczywistego silnika obecna; `DemoCatalog` i testowe reguły demo nieobecne |

Testy pulpitu obejmują brak historii, rozróżnienie kontroli/wymiany, dokładny próg, brak bieżącego odczytu po usunięciu, stary przebieg, niezależny termin czasowy, wymianę licznika, sprzeczne km tego samego dnia, przepełnienie, bramki licencji/ważności/DEMO/konfiguracji oraz plan zmienny bez wskaźnika. Testy repozytorium sprawdzają trwałość, duplikaty, konflikty rewizji i własność, bez usuwania historii razem z regułą.

Migracja urządzenia tworzy bazę z rzeczywistego archiwalnego v2 i porównuje profil, wszystkie pola konfiguracji, koszt, własny opis, kolejność czynności, notatki, rewizje, daty, powiązanie odczytu i świadomie starszą selekcję. Następnie zapisuje interwał i ponownie otwiera v3. Archiwalne schematy v1/v2 są niezmienione. Test biblioteki archiwum na API 26 nie zastępuje pełnego eksportu/odtworzenia bazy.

Wspólny build i Android 15: [PR](https://github.com/RobertCygan/Bil-Service-assistent/actions/runs/42883641021), [push](https://github.com/RobertCygan/Bil-Service-assistent/actions/runs/42883630228). Minimum API 26: [PR](https://github.com/RobertCygan/Bil-Service-assistent/actions/runs/42883640960), [push](https://github.com/RobertCygan/Bil-Service-assistent/actions/runs/42883630402). Wszystkie zakończone sukcesem. Artefakty pobrano, porównano SHA-256, wyniki XML JVM, raporty urządzeń i plik restartu. Pierwszy bieg ujawnił błędy pomocniczego odczytu schematu i etykiety selektora testu; poprawione scenariusze przeszły na obu wersjach Androida. Kolejny commit zawiera wyłącznie dokumentację i rzeczywiste zrzuty.

## 5. Ograniczenia

Brak zweryfikowanego, licencjonowanego katalogu producentów nadal blokuje zamknięcie części Etapu 4 dotyczącej zaleceń producenta. Mechanizm uprawnień jest przygotowany, ale nie dostarcza licencji ani samych danych. Nieobsługiwane są czynności jednorazowe i reguły zależne od wieku auta. Bez odczytu wskaźnika nie zastępujemy zmiennego planu stałym interwałem.

Ten przyrost nie dodaje interfejsów OCR, dokumentów, powiadomień, eksportu/odtwarzania ani połączenia z serwerem. Biblioteki tych funkcji są w zaimportowanym module, lecz nie są udostępnione w aplikacji. **Eksport musi zostać dostosowany do Room v3**, bo `ExportUserRule` z PR #2 nie przechowuje jeszcze rewizji i znaczników czasu własnych interwałów. [Kontrakt](../INTEGRATION.md) wskazuje tę różnicę.

Obsługa jednego auta; dane lokalne bez szyfrowania Room. Nie zmieniano wybranego wcześniej wyglądu ani logo. Nie wykonano beta testów na fizycznych telefonach, audytu przed publikacją ani publikacji w Play.

## 6. Decyzje i koordynacja

Zachowano progi zaakceptowane przez właściciela: 1500 km / 30 dni / 90 dni. Własne interwały i ich jawne odróżnienie od zaleceń są przewidziane w oryginalnym briefie. Import obejmuje zatwierdzone poprawki drugiego modelu; nie modyfikuje jego gałęzi. Dalsze prace przy słowniku, kopii i przypomnieniach opierają się na aktualnym [INTEGRATION.md](../INTEGRATION.md).

Nie rozstrzygano za właściciela sprzecznych wcześniejszych zapisów o ekranie wskaźnika: ekran nie jest częścią tego przyrostu. Jego opcjonalny typ pozostaje w module.

## 7. Odbiór przyrostu

- [x] Wspólny build, rzeczywisty silnik i aplikacja.
- [x] Cztery kategorie, podstawa i zastrzeżenia w NO/EN.
- [x] Trwałe własne interwały, walidacja i rozdzielenie kontroli/wymiany.
- [x] Producent wymaga osobnego dopuszczenia źródła; brak fikcyjnego katalogu.
- [x] Wyniki urządzeń, migracji i restartu potwierdzone w artefaktach CI.
- [ ] Właściciel odebrał ten przyrost.
- [ ] Zweryfikowany katalog producentów i prawa użycia — warunek ukończenia całego Etapu 4.

## 8. Następny krok

Po odbiorze pulpitu: osobny przyrost importu dokumentu i OCR z trwałym szkicem oraz zatwierdzaniem przed historią. Równolegle można wybrać legalne źródło planów producenta. Kopia danych musi otrzymać pełny test obiegu Room v3 → archiwum → odtworzona baza, zanim pojawi się w aplikacji.
