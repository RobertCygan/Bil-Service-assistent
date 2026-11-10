# Etap 3 — przebiegi i ręczna historia serwisowa

Data: **10 listopada 2026**, Europe/Oslo. Etap 2 zaakceptowany przez właściciela i scalony w PR #4. Etap 3 autoryzowany w tej samej rozmowie. **Status: kod gotowy, trwa weryfikacja Androida w CI.**

## 1. Cel

Prowadzenie rzeczywistej historii jednego auta bez OCR: zapis, edycja i usuwanie odczytów oraz usług z wieloma czynnościami, w NO (`nb-NO`) i EN.

## 2. Zakres

- Odczyty licznika z dniem i jawnym wyborem bieżącego odczytu. Historia może zawierać starsze i nowsze wpisy; aplikacja nie zastępuje wyboru użytkownika największą wartością.
- Usługa: dzień wykonania, opcjonalny przebieg, 1–30 prac, opcjonalny koszt NOK i notatka. Brak przebiegu jest odróżniony od 0 km. Własne opisy prac nie są arbitralnie dopasowywane do reguł.
- Oddzielna czynność i operacja, np. kontrola oleju oraz wymiana oleju; 15 stabilnych kodów z `service-contract`.
- Edycja i usuwanie z potwierdzeniem. Usunięcie wybranego odczytu lub jego usługi czyści bieżący wybór; aplikacja prosi o nowy wybór.
- Ostrzeżenia o niespójnych odczytach, różnych wartościach tego samego dnia i cofnięciu wybranego dnia; zapis wymaga wyjaśnienia i potwierdzenia ostrzeżenia. Nie przeliczamy automatycznie wymiany licznika.
- Trwała baza Room v2, migracja 1→2 zachowująca profil z Etapu 2, transakcje, UUID formularzy i rewizje chroniące przed nadpisaniem nieaktualnego wpisu.
- Ręczne, opcjonalne dane konfiguracji auta; data pierwszej rejestracji osobno od roku modelowego. Nieznane pola pozostają puste; nie ma dekodowania VIN ani przypisania planu z samego modelu.
- NO/EN: ścisłe formaty dat, grupowanie kilometrów, koszt bez zaokrąglania ponad dwa miejsca, lokalny kalendarz i komunikaty. Formularze zachowują dane przy odtworzeniu Activity; wyjście z niezapisanymi zmianami wymaga decyzji.

## 3. Jak uruchomić i sprawdzić

JDK 17, SDK Platform 36, Build Tools 35.0.0; konfiguracja jak w [Etapie 2](../stage-2/README.md). W głównym katalogu:

```sh
./gradlew :service-contract:test :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
python3 scripts/check_process_restart.py
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`, pakiet `app.servicepassport.android.debug`, wersja `0.2.0-dev`. Aktualizacja lokalnej instalacji: `adb install -r ...`. Instalacja zachowująca dane wymaga tej samej sygnatury; APK z osobnych maszyn może mieć inny klucz debug. Nie odinstalowuj aplikacji z potrzebną historią tylko w celu obejścia tego ograniczenia. Wydanie z trwałym kluczem należy do etapu publikacji.

Scenariusz ręczny:

1. Dodaj auto i bieżący odczyt 59 000 km.
2. Dodaj starszy serwis: 15.12.2025, 48 000 km, wymiana oleju i kontrola hamulców, koszt 1250,50 NOK, notatka. Na pulpicie nadal jest 59 000 km.
3. Edytuj serwis, zmień przebieg na 49 000 i notatkę. Otwórz szczegóły; sprawdź obie prace i kwotę.
4. Dodaj wpis bez znanego przebiegu, potem go usuń. Brak nie może zamienić się w 0.
5. Spróbuj sprzecznego odczytu, przeczytaj ostrzeżenie; bez wyjaśnienia i potwierdzenia zapis jest odrzucony.
6. Zmień język, całkowicie zatrzymaj proces i uruchom ponownie. Sprawdź profil, historię, bieżący odczyt i język.
7. Usuń odczyt wybrany jako bieżący. Pulpit powinien prosić o nowy, bez samodzielnego wyboru z historii.

## 4. Weryfikacja

Kompilacja i lokalne testy danych przechodzą. Wyniki końcowe CI, raporty i rzeczywiste zrzuty Androida zostaną wpisane po zakończeniu przebiegów. Nie są to makiety.

## 5. Ograniczenia

Obsługujemy jedno auto. Brak OCR, importu faktur, załączników, harmonogramów, przypomnień i eksportu; są w kolejnych etapach. Nie ma logowania, sieci, analityki ani zweryfikowanych interwałów producenta. Cała baza pozostaje w prywatnym katalogu aplikacji, bez szyfrowania przez Room i bez kopii chmurowej. Pełny audyt, eksport/usunięcie wszystkich danych i beta na telefonie należą do dalszych etapów.

Potwierdzenie ręczne oznacza deklarację użytkownika, nie weryfikację przez warsztat. Wyjaśnienie anomalii nie daje aplikacji podstawy do odtworzenia faktycznego całkowitego przebiegu. Robocza nazwa i wygląd pozostają jak w Etapie 2; nie wdrożono nowej wersji logo bez wyboru właściciela.

## 6. Koordynacja

[INTEGRATION.md](../INTEGRATION.md) opisuje fizyczny model Room, jednostki, selekcję przebiegu i mapowanie czynności. Sprawdzono PR #2 do `7e405ff43ab83d3527745310b558a7d291f2e686`. Odnotowano też nowsze dodatki PR #2 przy `7b966d61a9565ad39c2c7c70bcf40de4e0fa904f`: przypomnienia, eksport i serwer rejestru. Nie zmieniono jego gałęzi ani nie dodano zależności od silnika; rozbieżności formatu eksportu zapisano we wspólnym kontrakcie. Integracja musi rozwiązać jawny bieżący odczyt, anomalie licznika, wygasłe źródła i tymczasowe kody ekstraktora.

Progi zaakceptowane przez właściciela to 1500 km / 30 dni oraz 90 dni. W Etapie 3 używane jest wyłącznie oznaczenie nieaktualnego odczytu po 90 dniach; pozostałe progi czekają na reguły. Ekran ręcznego wskaźnika serwisowego pozostaje poza MVP.

## 7. Odbiór

- [x] Zapis/edycja/usuwanie przebiegów i usług, kilka czynności, koszt i notatka.
- [x] Starsze wpisy zachowują jawny bieżący odczyt.
- [x] Ostrzeżenia i brakujące dane mają jednoznaczne znaczenie.
- [x] Migracja zachowuje profil z Etapu 2 w teście plikowej bazy.
- [ ] Testy Androida, NO/EN i pełny restart procesu potwierdzone w CI.
- [ ] Właściciel odebrał Etap 3.

## 8. Następny krok

Po odbiorze Etapu 3: ocena i integracja równoległego Etapu 4, adapter Room → silnik oraz pulpit. Licencjonowany i zweryfikowany katalog producenta pozostaje osobną bramką; sam silnik i jego testy jej nie zamykają. OCR z Etapu 5 wymaga osobnego podłączenia i potwierdzania proponowanych wpisów.
