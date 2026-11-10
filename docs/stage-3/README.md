# Etap 3 — przebiegi i ręczna historia serwisowa

Data: **10 listopada 2026**, Europe/Oslo. Etap 2 zaakceptowany przez właściciela i scalony w PR #4. Etap 3 autoryzowany w tej samej rozmowie. **Status: gotowy do odbioru w PR #5; jeszcze niescalony.**

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
- Poprawka po audycie: wybór istniejącego odczytu stosuje tę samą walidację co formularz. Starszy/niespójny odczyt wymaga wyjaśnienia i świeżego potwierdzenia; anulowanie lub odrzucenie pozostawia poprzedni wybór. Zmiana wyjaśnienia odczytu usługowego chroni także rewizję usługi.

## 3. Jak uruchomić i sprawdzić

- [PR #5 — cały kod Etapu 3](https://github.com/RobertCygan/Bil-Service-assistent/pull/5).
- [APK 0.2.0-dev i raporty](https://github.com/RobertCygan/Bil-Service-assistent/actions/runs/42859784413/artifacts/13784722889): rozpakuj ZIP; APK jest w `app/build/outputs/apk/debug/app-debug.apk`. Wymagane logowanie na GitHubie, retencja 7 dni (do 17 listopada 2026).
- Rzeczywiste ekrany Androida: [historia NO](android-no.png), [historia EN](android-en.png), [bieżący przebieg NO](overview-no.png). Dane „Foundation test car” są fikcyjnym scenariuszem testu, nie danymi startowymi aplikacji.
- [Raport weryfikacji](verification.json) i [wynik pełnego restartu procesu](process-restart.json).

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

| Kontrola | Wynik |
| --- | --- |
| Root wrapper, APK debug i APK testowe | Przeszło lokalnie i w CI, JDK 17 / Gradle 8.13 |
| Testy aplikacji: walidacja, dokładne kwoty, daty NO/EN, plikowy Room, konflikty, selekcja i migracja | 29 testów, 0 błędów |
| Kontrakt kodów czynności i operacji | 3 testy, 0 błędów |
| Android Lint | 0 błędów, 23 ostrzeżenia: 20 o aktualizacjach narzędzi/zależności, 1 localeConfig od API 33, 2 nieużywane zasoby |
| Test Compose na Androidzie 15 / API 35 | Przeszło: aktualizacja przebiegu, starszy serwis z dwiema pracami i kosztem, edycja, usunięcie wpisu bez km, odtworzenie Activity, porzucenie formularza, NO/EN oraz anulowanie/odrzucenie/potwierdzenie wyboru istniejącego starszego odczytu |
| Migracja 1→2 na Androidzie | Przeszło: rzeczywisty archiwalny schemat v1, zachowane ID/nazwa/rok/metadane, nowe dane pozostają puste |
| Pełne zatrzymanie procesu i ponowny start | Przeszło: profil, bieżące 59 000 km, starszy serwis po edycji 49 000 km, dwie prace i nb-NO |
| GitHub CI dla kodu `e1c44e0521d503a6bb91a87e700a503842939e06` | Oba zadania `build` i `device` zielone w obu przebiegach: [PR](https://github.com/RobertCygan/Bil-Service-assistent/actions/runs/42859784413) i [push](https://github.com/RobertCygan/Bil-Service-assistent/actions/runs/42859770643) |

Po audycie dodano 5 testów wyboru istniejącego odczytu oraz rozszerzono test urządzenia. Poprawka jest w kodzie `e1c44e0…`; powyższe przebiegi potwierdzają ją na Androidzie. Wcześniej pierwszy przebieg wykrył zbyt ścisłą asercję tekstu w teście NO; poprawiony test oraz rozszerzony scenariusz usuwania przeszły w powyższych przebiegach. Wynik końcowy dotyczy wskazanej rewizji kodu. Następny commit dodaje tylko dokumentację i rzeczywiste zrzuty. APK pobrany z CI sprawdzono po SHA-256; oba archiwa i suma APK są zapisane w raporcie. Nie dodano uprawnień sieci, aparatu ani powiadomień. Testów na fizycznym telefonie ani na wszystkich wersjach Androida jeszcze nie wykonano.

## 5. Ograniczenia

Obsługujemy jedno auto. Brak OCR, importu faktur, załączników, harmonogramów, przypomnień i eksportu; są w kolejnych etapach. Nie ma logowania, sieci, analityki ani zweryfikowanych interwałów producenta. Cała baza pozostaje w prywatnym katalogu aplikacji, bez szyfrowania przez Room i bez kopii chmurowej. Pełny audyt, eksport/usunięcie wszystkich danych i beta na telefonie należą do dalszych etapów.

Potwierdzenie ręczne oznacza deklarację użytkownika, nie weryfikację przez warsztat. Wyjaśnienie anomalii nie daje aplikacji podstawy do odtworzenia faktycznego całkowitego przebiegu. Robocza nazwa i wygląd pozostają jak w Etapie 2; nie wdrożono nowej wersji logo bez wyboru właściciela.

## 6. Koordynacja

[INTEGRATION.md](../INTEGRATION.md) opisuje fizyczny model Room, jednostki, selekcję przebiegu i mapowanie czynności. Sprawdzono PR #2 do `7e405ff43ab83d3527745310b558a7d291f2e686`. Odnotowano też nowsze dodatki PR #2 przy `7b966d61a9565ad39c2c7c70bcf40de4e0fa904f`: przypomnienia, eksport i serwer rejestru. Nie zmieniono jego gałęzi ani nie dodano zależności od silnika; rozbieżności formatu eksportu zapisano we wspólnym kontrakcie. Integracja musi rozwiązać jawny bieżący odczyt, anomalie licznika, wygasłe źródła i tymczasowe kody ekstraktora.

Progi zaakceptowane przez właściciela to 1500 km / 30 dni oraz 90 dni. W Etapie 3 używane jest wyłącznie oznaczenie nieaktualnego odczytu po 90 dniach; pozostałe progi czekają na reguły. Ekran ręcznego wskaźnika serwisowego pozostaje poza MVP. Do właściciela pozostaje odbiór Etapu 3; nie ma nowych decyzji funkcjonalnych blokujących ten przyrost.

## 7. Odbiór

- [x] Zapis/edycja/usuwanie przebiegów i usług, kilka czynności, koszt i notatka.
- [x] Starsze wpisy zachowują jawny bieżący odczyt.
- [x] Ostrzeżenia i brakujące dane mają jednoznaczne znaczenie.
- [x] Migracja zachowuje profil z Etapu 2 w teście plikowej bazy.
- [x] Testy Androida, NO/EN i pełny restart procesu potwierdzone w CI.
- [ ] Właściciel odebrał Etap 3.

## 8. Następny krok

Po odbiorze Etapu 3: ocena i integracja równoległego Etapu 4, adapter Room → silnik oraz pulpit. Licencjonowany i zweryfikowany katalog producenta pozostaje osobną bramką; sam silnik i jego testy jej nie zamykają. OCR z Etapu 5 wymaga osobnego podłączenia i potwierdzania proponowanych wpisów.
