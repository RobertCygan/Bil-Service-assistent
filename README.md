# CarServiceAssistent

Aplikacja Android **CarServiceAssistent** dla kierowców w Norwegii, w językach norweskim Bokmål (`nb-NO`) i angielskim (`en`).

Cel: pokazać, co i kiedy serwisować na podstawie potwierdzonej historii pojazdu oraz wiarygodnego harmonogramu.

## Status (11 listopada 2026)

Cała praca jest w `main`; nie ma otwartych gałęzi z niescalonymi zmianami. Aplikacja Kotlin/Compose w NO/EN działa lokalnie, bez konta i bez dostępu do sieci.

**Jest w aplikacji:** auto i przebiegi, ręczna historia serwisowa, pulpit oparty na własnych interwałach kierowcy i na wskaźniku serwisowym z auta, odczyt faktur na telefonie ze sprawdzeniem przed zapisem, dokument przechowywany przy wpisie, przypomnienia, kopia zapasowa (opcjonalnie z hasłem) i usuwanie danych, podpowiedzi marki i modelu.

**Nie ma:** harmonogramów producentów (katalog jest pusty do czasu uzyskania praw i zweryfikowanych danych), odczytu danych auta z rejestru vegvesen, publikacji w Google Play.

**Niesprawdzone:** działanie na fizycznym telefonie i dokładność odczytu prawdziwych faktur.

- [Gotowość do testów beta: spis testów, rozmiar, lista kontrolna na telefon](docs/12-etap-8-beta.md)
- [Przygotowanie do Google Play](docs/13-etap-9-google-play.md)
- [Prywatność: przegląd i projekt polityki](docs/11-etap-7-prywatnosc.md)
- [Odczyt dokumentów](docs/10-etap-5-import-dokumentu.md) · [Przypomnienia](docs/07-etap-6-przypomnienia.md) · [Kopia danych](docs/08-etap-7-eksport-danych.md)
- [Kontrakt między aplikacją a silnikiem](docs/INTEGRATION.md)

Raporty poniżej (Etapy 0–4 i przygotowanie integracji) są zapisem historycznym poszczególnych przyrostów.

Rekomendacja: Kotlin + Jetpack Compose, dane i OCR lokalnie, bez kont użytkowników w MVP. Wybór dostawcy harmonogramów pozostaje otwarty; jego warunki mogą wymagać małego backendu.

## Przebiegi i ręczna historia — Etap 3

- [Zakres, APK do pobrania i weryfikacja](docs/stage-3/README.md)
- Rzeczywista historia Androida: [NO](docs/stage-3/android-no.png) · [EN](docs/stage-3/android-en.png)
- [Kontrakt z równoległym silnikiem i OCR](docs/INTEGRATION.md)

## Przygotowanie integracji — przyrost 1

- [Mapowanie Room, testy rzeczywistego silnika i ograniczenia](docs/integration/README.md)
- Raport historyczny PR #6. W PR #7 silnik jest już zależnością aplikacji, z zachowaniem rewizji importu i historii autora.

## Aplikacja Android — Etap 2

- [Uruchomienie, APK do pobrania, zakres i weryfikacja](docs/stage-2/README.md)
- Rzeczywiste ekrany Androida: [NO](docs/stage-2/android-no.png) · [EN](docs/stage-2/android-en.png)
- [Wspólny kontrakt z silnikiem i OCR](docs/INTEGRATION.md)
- Wspólny build: `./gradlew :service-contract:test :service-engine:check :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

## Makiety i model danych — Etap 1

- [Raport i instrukcja przeglądu](docs/stage-1/README.md)
- [Plansza ekranów NO](docs/stage-1/preview-no.png) · [Plansza EN](docs/stage-1/preview-en.png)
- [Klikalne makiety do pobrania](docs/stage-1/wireframes.html)
- [Model danych i relacje](docs/stage-1/data-model.md)

## Dokumentacja Etapu 0

- [Etap 0: produkt, MVP i decyzje do odbioru](docs/00-etap-0.md)
- [Źródła danych, licencje i plan pilota](docs/01-dane-i-zrodla.md)
- [Architektura, OCR i prywatność](docs/02-architektura-i-ocr.md)
- [Koszty, ryzyka i etapy do Google Play](docs/03-koszty-i-plan.md)
- [Prompt dostarczony przez właściciela](docs/brief/BUILD_PROMPT_PL.md)

Dokumentacja projektu i rozmowa: po polsku. Interfejs i materiały dla użytkowników: NO/EN.

## Zasady produktu

- Nie generujemy zaleceń producenta z AI.
- Każda reguła wymaga źródła, wersji, zakresu zastosowania i prawa użycia.
- OCR proponuje wpis; dopiero potwierdzenie użytkownika zmienia historię.
- Brak dokumentu oznacza brak potwierdzenia, a nie dowód niewykonania usługi.
- Zmiany zakresu i rozpoczęcie kolejnego etapu wymagają odbioru poprzedniego etapu zgodnie z briefem.

Nie dodano licencji projektu. Publiczna widoczność repozytorium nie określa licencji kodu ani praw do danych zewnętrznych. Nie umieszczamy tu prawdziwych VIN, faktur, danych osobowych ani sekretów.
