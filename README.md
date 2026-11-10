# Bil Service Assistent

Robocza nazwa produktu: **AI Service Passport**. Planowana aplikacja Android dla kierowców w Norwegii, w językach norweskim Bokmål (`nb-NO`) i angielskim (`en`).

Cel: pokazać, co i kiedy serwisować na podstawie potwierdzonej historii pojazdu oraz wiarygodnego harmonogramu.

## Status

**Etapy 0 i 1 zaakceptowane. Etap 2 — fundament Android w weryfikacji.** Jest aplikacja Kotlin/Compose z NO/EN i lokalnym profilem auta. Pełna historia, OCR i harmonogramy nie są jeszcze podłączone.

Rekomendacja: Kotlin + Jetpack Compose, dane i OCR lokalnie, bez kont użytkowników w MVP. Wybór dostawcy harmonogramów pozostaje otwarty; jego warunki mogą wymagać małego backendu.

## Aplikacja Android — Etap 2

- [Uruchomienie, zakres i weryfikacja](docs/stage-2/README.md)
- [Wspólny kontrakt z silnikiem i OCR](docs/INTEGRATION.md)
- Build: `./gradlew :service-contract:test :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

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
