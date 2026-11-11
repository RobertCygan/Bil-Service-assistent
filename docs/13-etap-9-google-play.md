# Etap 9 — przygotowanie do Google Play

Data: **11 listopada 2026**.

## Status

| Element | Stan |
| --- | --- |
| Pakiet sklepowy (AAB) buduje się przy każdej zmianie | **Zrobione** — zadanie CI, artefakt `android-foundation` (pakiet niepodpisany) |
| Podpisywanie wydania kluczem właściciela | **Przygotowane** — klucz podaje się z zewnątrz, nigdy nie trafia do repozytorium; ścieżka sprawdzona kluczem jednorazowym |
| Wymóg stron pamięci 16 KB dla bibliotek natywnych | **Spełniony** dla bibliotek 64-bitowych; pilnuje tego skrypt w CI |
| Wymagany poziom API (targetSdk 36) | **Spełniony** |
| Teksty do sklepu (NO/EN) | **Projekt** poniżej — norweski wymaga sprawdzenia przez osobę znającą język |
| Polityka prywatności | **Projekt** EN i NO w `docs/11-etap-7-prywatnosc.md`; brak danych wydawcy i adresu URL |
| Konto dewelopera, klucz, grafiki sklepowe, testerzy | **Po stronie właściciela** — lista poniżej |
| Zmniejszanie kodu (R8) | **Wyłączone** — patrz `docs/12-etap-8-beta.md` |

Etap 9 **nie jest ukończony**: nic nie zostało wgrane do Google Play.

## Co musi zrobić właściciel

1. **Konto dewelopera Google Play** — jednorazowa opłata rejestracyjna i weryfikacja tożsamości.
2. **Zamknięte testy przed publikacją.** Konta osobiste założone po 13 listopada 2023 muszą przed wnioskiem o publikację przeprowadzić test zamknięty: **co najmniej 12 testerów zapisanych nieprzerwanie przez 14 dni**. To wymóg Google, którego nie da się skrócić — warto zacząć szukać testerów wcześniej. Konto firmowe tego wymogu nie ma.
3. **Klucz do wgrywania** (upload key) — tworzony raz, przechowywany poza repozytorium, z kopią w bezpiecznym miejscu. Polecenie:

   ```bash
   keytool -genkeypair -v -keystore upload-key.jks -alias upload -keyalg RSA -keysize 4096 -validity 9125
   ```

4. **Adres URL polityki prywatności** i dane wydawcy (nazwa, adres kontaktowy).
5. **Grafiki sklepowe** (od grafika, nie z tego repozytorium): ikona 512×512, grafika główna 1024×500, ikona aplikacji w wersji adaptacyjnej, co najmniej 2 zrzuty ekranu telefonu. Zrzuty prawdziwych ekranów można wygenerować poleceniem z `docs/screens/README.md`, gdy wygląd będzie ostateczny.
6. **Sprawdzenie na telefonie** według `docs/12-etap-8-beta.md`.

## Budowanie podpisanego pakietu

Hasła podaje się jako właściwości Gradle — w wierszu poleceń albo w `~/.gradle/gradle.properties` na własnym komputerze:

```bash
./gradlew :app:bundleRelease -PreleaseStoreFile=/sciezka/upload-key.jks -PreleaseStorePassword=... -PreleaseKeyAlias=upload -PreleaseKeyPassword=...
```

Wynik: `app/build/outputs/bundle/release/app-release.aab`. Bez tych właściwości pakiet powstaje niepodpisany (tak buduje go CI).

Przed każdym wydaniem trzeba podnieść `versionCode` (liczba, zawsze większa niż poprzednia) i `versionName` w `app/build.gradle.kts`.

## Formularze w konsoli — proponowane odpowiedzi

- **Kategoria:** Auto i pojazdy. **Reklamy:** nie. **Zakupy w aplikacji:** nie.
- **Grupa docelowa:** 18+ (kierowcy); aplikacja nie jest kierowana do dzieci.
- **Ocena treści (IARC):** brak przemocy, treści seksualnych, hazardu, substancji, komunikacji między użytkownikami i udostępniania lokalizacji.
- **Bezpieczeństwo danych:** aplikacja nie zbiera i nie udostępnia danych; szczegóły i warunek prawdziwości tych odpowiedzi w `docs/11-etap-7-prywatnosc.md`.
- **Dostęp do aplikacji:** bez logowania, wszystkie funkcje dostępne od razu.

## Teksty do sklepu — projekt

Nazwa (maks. 30 znaków): **CarServiceAssistent**

### English

Krótki opis (79 z 80 znaków):

> Your car's service history and what is due next. No account, all on your phone.

Pełny opis:

> CarServiceAssistent keeps your car's service history in one place and shows what is due next.
>
> WHAT IT DOES
> • Service history — record what was done, when, at what mileage and what it cost.
> • Read invoices — take a photo or pick a PDF. The text is read on your phone, and you check the result before anything is saved.
> • Your own intervals — set distance and time intervals for oil, brake fluid, filters and more. The app shows what is overdue, what is due soon and where information is missing.
> • The car's service display — enter what your car shows ("service in 3,000 km") and see it in the overview.
> • Reminders — optional notifications when something is due.
> • Backup — save everything, including the documents you kept, to a file you control. You can protect it with a password.
>
> PRIVATE BY DESIGN
> • No account and no internet access: nothing leaves your phone.
> • You can delete all data at any time.
>
> GOOD TO KNOW
> • The app does not contain manufacturer service schedules. Due dates come from the intervals you enter or from your car's own display. Always follow your car's handbook and your workshop's advice.
> • Languages: Norwegian (bokmål) and English.

### Norsk bokmål (wymaga sprawdzenia przez osobę znającą język)

Krótki opis (78 z 80 znaków):

> Bilens servicehistorikk og hva som må gjøres nå. Uten konto, alt på telefonen.

Pełny opis:

> CarServiceAssistent samler bilens servicehistorikk på ett sted og viser hva som bør gjøres nå.
>
> DETTE GJØR APPEN
> • Servicehistorikk – registrer hva som ble gjort, når, ved hvilken kilometerstand og hva det kostet.
> • Les fakturaer – ta et bilde eller velg en PDF. Teksten leses på telefonen, og du kontrollerer resultatet før noe lagres.
> • Dine egne intervaller – angi intervaller i kilometer og tid for olje, bremsevæske, filtre og mer. Appen viser hva som er forfalt, hva som nærmer seg, og hvor det mangler opplysninger.
> • Bilens serviceindikator – legg inn det bilen viser («service om 3 000 km») og se det i oversikten.
> • Påminnelser – valgfrie varsler når noe nærmer seg.
> • Sikkerhetskopi – lagre alt, også dokumentene du har tatt vare på, i en fil du selv har kontroll over. Du kan beskytte den med passord.
>
> PERSONVERN
> • Ingen konto og ingen internettilgang: ingenting forlater telefonen.
> • Du kan slette alle data når som helst.
>
> VERDT Å VITE
> • Appen inneholder ikke produsentenes serviceprogrammer. Fristene kommer fra intervallene du selv legger inn, eller fra bilens egen indikator. Følg alltid bilens instruksjonsbok og verkstedets råd.
> • Språk: norsk (bokmål) og engelsk.

## Czego ten etap nie obejmuje

- Odczytu danych auta z rejestru vegvesen (czeka na klucz API i serwer).
- Harmonogramów producentów (czekają na licencjonowane dane).
- Wersji na inne rynki nordyckie poza Norwegią w warstwie językowej (aplikacja ma język norweski i angielski).

## Źródła

- Wymóg testów zamkniętych dla nowych kont osobistych: <https://support.google.com/googleplay/android-developer/answer/14151465>
- Strony pamięci 16 KB: <https://developer.android.com/guide/practices/page-sizes> (według tej strony wymóg dla aktualizacji obowiązuje od 1 lutego 2027; wcześniejsze komunikaty podawały 1 listopada 2025 — aplikacja spełnia go już teraz)
