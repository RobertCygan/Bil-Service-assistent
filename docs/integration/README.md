# Przyrost integracyjny 1 — Room i rzeczywisty silnik

Data: **10 listopada 2026**, Europe/Berlin. Zakres zatwierdzony przez właściciela: poprawka Etapu 3 oraz przygotowanie mapowania i testów połączenia. Pulpit pozostaje następnym przyrostem.

## Cel i zakres

`EngineFactsMapper.prepare(GarageSnapshot)` przygotowuje dane z rzeczywistej bazy: konfigurację, datę pierwszej rejestracji, jawny bieżący odczyt, historię odczytów i pełne kody wykonanych czynności. Nie wybiera największego/najnowszego przebiegu i nie wyznacza harmonogramu.

Mapowanie jest kodem aplikacji. `EngineBinding` i reguły demonstracyjne istnieją wyłącznie w dodatkowym zestawie testowym. Zwykły APK nadal nie zależy od silnika, nie importuje dokumentów i nie pokazuje zaleceń producenta. Nie scalono PR #2 ani nie zmieniono jego gałęzi.

## Co można sprawdzić

- Brak bieżącego wyboru pozostaje `null`, nawet gdy historia zawiera późniejszy przebieg; testowy binding przekazuje **zawsze** `CurrentOdometer.Selected` lub `NotSelected`, nigdy domyślne `LatestKnown`.
- Kontrola i wymiana zachowują odrębne pary `ServiceTask`; własne opisy są przekazywane osobno i nie dopasowują się do reguł po słowach z opisu.
- Brak przebiegu serwisu pozostaje nieznany. Konwersja `Long` → `Int` sprawdza zakres i odrzuca nieprawidłową wartość; bez obcinania lub zamiany na 0.
- Rok modelowy nie uzupełnia daty pierwszej rejestracji. Nieznane wybory konfiguracji (`OTHER`/nieobsługiwany kod) są pomijane; brak danych nie staje się pełnym dopasowaniem.
- Odczyty zachowują ID, powiązanie z usługą i wyjaśnienie. Spadek przebiegu, różne wartości tego samego dnia i zapisane wyjątki są odrębnymi zastrzeżeniami; nie przeliczamy wymiany licznika.
- Progi do silnika pochodzą z `AppPolicy`, zaakceptowanego przez właściciela: 1500 km, 30 dni i 90 dni.
- Nieznany kod w potwierdzonej historii zatrzymuje przygotowanie zamiast zmieniać znaczenie czynności. Szkice OCR muszą pozostać poza tabelą potwierdzonych usług.

## Rzeczywisty drugi moduł

Rewizja silnika jest przypięta w [`.github/engine-integration.ref`](../../.github/engine-integration.ref): `525c223a9b4377a8490ddb464010fa2128a99ca0`. Zawiera poprawki drugiego modelu dotyczące bieżącego odczytu, ekstrakcji, eksportu, przypomnień i wspólnych narzędzi. Zmiana pinu wymaga przeczytania różnicy i ponownego wykonania testów.

W CI drugi checkout umieszcza **całą tę rewizję repozytorium** w `.integration/engine-source`. Gradle uruchamia `service-engine` jako osobny composite build z jego katalogiem wersji. Zależność trafia wyłącznie do testów aplikacji. Bez właściwości `engineIntegrationRoot` nie ma pobierania ani zależności od PR #2.

```sh
# Zwykły build aplikacji — bez silnika.
./gradlew :service-contract:test :app:assembleDebug :app:testDebugUnitTest :app:lintDebug

# Osobny checkout PR #2, zatrzymany na commicie z pliku .ref.
# Podaj ścieżkę do katalogu repozytorium, nie do samego service-engine.
./gradlew :service-contract:test :app:testDebugUnitTest :service-engine:check \
  -PengineIntegrationRoot=/absolute/path/to/engine-checkout

# Urządzenie z API 26: prawdziwa biblioteka silnika i odczyt archiwum.
./gradlew :app:connectedDebugAndroidTest \
  -PengineIntegrationRoot=/absolute/path/to/engine-checkout \
  -Pandroid.testInstrumentationRunnerArguments.class=app.servicepassport.android.integration.EngineAndroidSmokeTest
```

Wspólny workflow: [engine-integration.yml](../../.github/workflows/engine-integration.yml). Testy JVM wykonują zapis do plikowego Room, pobranie relacji, mapowanie, wywołanie prawdziwego `ServiceEngine` i sprawdzenie wyników/przypomnień. Osobny test urządzenia uruchamia bibliotekę i odczyt archiwum na API 26 — hostowy JDK nie udowadnia zgodności starszego Androida.

## Weryfikacja

**Wynik: gotowe do odbioru w [PR #6](https://github.com/RobertCygan/Bil-Service-assistent/pull/6)**, opartym na PR #5. Kod aplikacji sprawdzony przy `11e4f82c918e9425334d1d868d5c4cc8c8e12d65`, silnik przy `525c223a9b4377a8490ddb464010fa2128a99ca0`. Późniejszy commit aktualizuje wyłącznie dokumentację.

| Sprawdzenie | Wynik |
| --- | --- |
| Aplikacja z dodatkowym zestawem integracyjnym | 42 testy: 29 Etapu 3 po poprawce, 7 mapowania i 6 rzeczywistego połączenia z silnikiem; 0 błędów |
| Wspólny kontrakt | 3 testy; 0 błędów |
| Silnik z przypiętej rewizji | 111 testów; 0 błędów |
| Android Lint i kompilacja APK testowego | Przeszło lokalnie i w CI |
| Natywny test na minimum API 26 / Android 8.0 | 1 test: jawny brak odczytu oraz zapis/odczyt prostego archiwum; przeszło |
| Regresja aplikacji API 35 / Android 15 | 2 testy i osobny force-stop/start; przeszło, także wybór istniejącego starszego odczytu |
| Zwykły APK | Sprawdzono DEX: brak klas `app.servicepassport.engine`; mapowanie danych jest obecne |

CI integracji: [PR](https://github.com/RobertCygan/Bil-Service-assistent/actions/runs/42862574387) i [push](https://github.com/RobertCygan/Bil-Service-assistent/actions/runs/42862549500). Regresja Androida: [PR](https://github.com/RobertCygan/Bil-Service-assistent/actions/runs/42862574335) i [push](https://github.com/RobertCygan/Bil-Service-assistent/actions/runs/42862549312). Wszystkie przebiegi zakończone sukcesem.

[Raport maszynowy](verification.json). [Archiwum raportów integracyjnych](https://github.com/RobertCygan/Bil-Service-assistent/actions/runs/42862574387/artifacts/13784744384) pobrano, sprawdzono SHA-256, liczbę testów w XML oraz obie rewizje w `revisions.json`. Pobranie wymaga logowania na GitHubie; retencja 7 dni. Nie uruchamiano testów na fizycznym telefonie.

Scenariusze testowe obejmują: usunięty bieżący odczyt przy nowszym przebiegu w historii, kontrolę bez resetu wymiany, własną czynność, brak km usługi, niepełną konfigurację, odrzucenie DEMO do produkcji, wygasłe źródło oraz wyjaśnioną wymianę licznika. Przy anomalii dopuszczalne jest przypomnienie o sprawdzeniu przebiegu; nie jest to zwykłe zalecenie wykonania usługi.

## Ograniczenia i odbiór

To przygotowanie integracji, nie zakończony Etap 4. Nie ma pulpitu z kategoriami silnika, ekranów OCR/wskaźnika, powiadomień Androida ani eksportu/odtwarzania bazy. Test nagłówka archiwum na API 26 sprawdza zgodność wykonania biblioteki; nie zastępuje pełnego obiegu Room → kopia → nowa baza z Etapu 7.

Przed podłączeniem pulpitu potrzebne są prezentacja NO/EN wszystkich powodów, bramka ważności/uprawnień źródła i ostrożna obsługa zastrzeżeń licznika. Sama kategoria `NO_URGENCY` lub `OVERDUE` nie wystarcza do uznania zalecenia za zweryfikowane. Nie ma licencjonowanych reguł producenta. Dane i interwały testowe są fikcyjne i leżą poza źródłami produkcyjnymi APK.

Po odbiorze tego przyrostu: przenieść przetestowany binding do właściwej integracji aplikacji, podłączyć pulpit i zweryfikować zachowanie na Androidzie; zakres wskaźnika uzgodnić przed jego ekranem. Do tego czasu API drugiego modelu pozostaje odrębną propozycją PR #2, a testy korzystają z jawnie przypiętej rewizji.
