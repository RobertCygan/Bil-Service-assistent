# Etap 2 — fundament Android

Data: **10 listopada 2026**, Europe/Oslo. Etap 1 zaakceptowany przez właściciela; PR #3 włączono do `main`. To osobny przyrost fundamentu, bez rozpoczęcia Etapu 3 i bez scalania PR #2.

## 1. Cel

Uruchamialna aplikacja Android, podstawowa nawigacja, oba języki, trwały zapis podstawowych danych oraz powtarzalny build. Jednocześnie ustalenie wspólnego kontraktu z równoległym silnikiem.

## 2. Zakres i rezultat

- `app/`: Kotlin/Compose, trzy zakładki, start bez logowania, formularz nazwy auta i opcjonalnego roku modelowego.
- Zapis do rzeczywistego pliku Room/SQLite; ponowny start korzysta z tych samych danych. Rok modelowy może pozostać nieznany.
- NO (`nb-NO`) i EN, zmiana w aplikacji oraz integracja z językami aplikacji Android; English jako zasób domyślny.
- Język przechowuje mechanizm Android/AppCompat. Nie dublujemy jego wartości w drugim magazynie preferencji; DataStore zostanie użyty, gdy pojawią się pozostałe ustawienia.
- Schemat bazy v1 eksportowany do `app/schemas/`. Brak destrukcyjnego fallbacku i opisany proces przyszłych migracji.
- Osobne identyfikatory debug/release i konfiguracja środowiska w BuildConfig; bez sekretów i płatnych usług.
- Jawne wyłączenie automatycznego backupu/transferu wrażliwych danych w konfiguracji Androida. Rzeczywiste zachowanie transferów OEM pozostaje do audytu przed wydaniem.
- Komunikaty walidacji, zapisu i odczytu z zasobów; brak surowych wyjątków, SQL i danych auta w logach aplikacji.
- Root Gradle wrapper z sumą SHA-256, katalog wersji, CI budowania/testów/lint i osobny test urządzenia.
- `service-contract/` oraz [wspólny kontrakt integracyjny](../INTEGRATION.md) dla drugiego AI.

Nie ma danych demonstracyjnych ani harmonogramu zaszytego w aplikacji. Pusta historia jest rzeczywiście pusta. Ekran po zapisaniu auta informuje o braku zweryfikowanego planu. Formularz zapisuje tylko nazwę i opcjonalny rok — komplet konfiguracji, przebiegi i wpisy serwisowe to Etap 3.

## 3. Jak sprawdzić

### Wymagania

JDK 17, Android SDK Platform 36, Build Tools 35.0.0, zaakceptowane warunki SDK. Otwórz główny katalog w Android Studio. Ustaw lokalną ścieżkę SDK przez IDE/`local.properties` lub `ANDROID_HOME`; plik lokalny nie jest commitowany.

```sh
./gradlew :service-contract:test :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Windows: `gradlew.bat` z tymi samymi argumentami. Wersje bibliotek są przypięte w `gradle/libs.versions.toml`; żadne lokalne ustawienia proxy lub ścieżki z wykonania chmurowego nie są częścią projektu.

APK debug: `app/build/outputs/apk/debug/app-debug.apk`, pakiet `app.servicepassport.android.debug`, Android 8/API 26 lub nowszy. Pakiet release jest roboczy do potwierdzenia przed Play; nie utworzono materiałów podpisujących release.

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:connectedDebugAndroidTest
```

Test ręczny: wybierz język → zapisz nazwę/rok → przejdź między zakładkami → zmień język → całkowicie zamknij proces i uruchom aplikację ponownie. Sprawdź zachowanie nazwy/roku i języka. Nie używaj prawdziwych prywatnych dokumentów w tym etapie — ich import nie jest jeszcze zaimplementowany.

CI `Android foundation`: zadanie `build` publikuje APK i raporty jako artefakt `android-foundation`; zadanie `device` wykonuje test Compose na emulatorze API 35. Artefakty są przechowywane 7 dni. To testowe pliki do pobrania w Actions, bez publikacji w Google Play.

## 4. Weryfikacja

| Kontrola | Stan na przygotowanie PR |
| --- | --- |
| Root wrapper, kompilacja APK debug i APK testowego | Przeszło lokalnie, JDK 17 |
| Testy kontraktu | 3 testy, 0 błędów |
| Walidacja formularza | 2 testy, 0 błędów |
| Room: zapis, zamknięcie, ponowne otwarcie; aktualizacja zachowuje ID | Przeszło lokalnie, test z plikową bazą przez Robolectric |
| Nieznana wersja bazy | Test potwierdza odmowę otwarcia bez kasowania istniejącego rekordu |
| Android Lint | Przeszło lokalnie |
| Uruchomienie i języki na emulatorze | Weryfikacja w toku; wynik zostanie uzupełniony przed odbiorem |
| CI GitHub | Oczekuje na uruchomienie dla tego PR |

Testy bazy i kontraktu nie są testami prawdziwego OCR, reguł producenta ani adaptera do silnika. Schemat v1 jest pierwszym schematem: nie tworzymy fikcyjnej migracji tylko po to, aby wykazać liczbę migracji.

## 5. Migracje i bezpieczeństwo danych

Room eksportuje wersję schematu do `app/schemas/app.servicepassport.android.data.GarageDatabase/1.json`. Przy **rzeczywistej** kolejnej zmianie: podnieś wersję, dodaj `Migration`, zachowaj poprzedni schemat, wykonaj test zachowania danych i porównaj nowy eksport. Nie dodawaj `fallbackToDestructiveMigration()`.

Obecny plik bazy jest w prywatnym katalogu aplikacji; Room samo nie szyfruje bazy. Nie dodano uprawnień sieci, aparatu ani powiadomień, nie ma analityki i SDK OCR. Klucze usług nie trafiają do APK. Jeśli integracja dostawcy ich wymaga, powstanie osobny backend zgodnie z bramką źródła.

## 6. Koordynacja i decyzje

- Przyjęte przez właściciela: 1500 km / 30 dni i 90 dni jako progi aplikacji. Nie są jeszcze używane przez fundament bez podłączonego silnika.
- Po pozostawieniu wyboru zdecydowano: opcjonalny wskaźnik w API silnika zostaje, nowy ekran poza MVP.
- Wspólne kody obejmują operację, np. `engine_oil:replace`. Słownik w `service-contract/` nie zawiera żadnych interwałów.
- Przed adapterem trzeba rozwiązać jawny wybór bieżącego odczytu, wygasłe źródła i ograniczenia rodzajów reguł. Szczegóły: [INTEGRATION.md](../INTEGRATION.md).
- Autor PR #2 nadal prowadzi własne pliki. Nie zmieniono jego gałęzi ani nie scalono modułu bez integracji/testów.

## 7. Kryteria odbioru

- [x] Projekt kompiluje się z root wrappera i ma opis uruchomienia.
- [x] Podstawowa nawigacja i zasoby NO/EN są w aplikacji.
- [x] Podstawowy rekord ma trwały zapis; testy plikowej bazy przechodzą.
- [x] Schemat eksportowany, brak kasującego fallbacku, proces migracji określony.
- [x] Środowiska debug/release i podstawowa obsługa błędów gotowe.
- [ ] Wynik uruchomienia aplikacji i zmiany języka na Androidzie potwierdzony.
- [ ] Właściciel odebrał Etap 2.

## 8. Następny krok

Po odbiorze: Etap 3 — pełniejsze dane auta, jawny bieżący przebieg i ręczne wpisy z wieloma czynnościami, edycją i usuwaniem. Adapter i pulpit reguł pozostają częścią integracji Etapu 4; OCR pozostaje osobnym Etapem 5. Licencja i zweryfikowane dane producenta nadal niepozyskane.
