# Etap 1 — UX i model danych

Data: **10 listopada 2026**. Etap 0 oraz rozpoczęcie Etapu 1 zostały zaakceptowane przez właściciela w rozmowie („TAk Zaczynaj”). PR #1 po tej akceptacji włączono do `main`. Nie oznacza to zakupu danych ani zamknięcia bramki licencyjnej.

**Status Etapu 1: zaakceptowany przez właściciela 10 listopada 2026.** Zakres: makiety, teksty, stany i model danych. To nie jest implementacja aplikacji Android. Właściciel zaakceptował rozpoczęcie [Etapu 2](../stage-2/README.md).

## 1. Cel

Uzgodnić sposób korzystania z aplikacji i relacje danych przed rozpoczęciem kodowania. Użytkownik ma rozumieć pochodzenie każdego terminu i sam potwierdzać odczyt dokumentu.

## 2. Zakres

- rozpoczęcie pracy i język;
- dodanie auta i przebiegu;
- pulpit i szczegóły terminu/źródła;
- historia i wpis ręczny;
- dodanie dokumentu i sprawdzenie propozycji OCR;
- ustawienia, przypomnienia i zarządzanie danymi;
- stany puste, offline, brak danych i błędy;
- model danych, relacje i zasady spójności.

## 3. Rezultat do sprawdzenia

1. [Klikalne makiety](wireframes.html) — pobierz plik i otwórz w przeglądarce. Jeden plik, bez instalacji, internetu i zewnętrznych zasobów. Przełącznik NO/EN i scenariusze są nad telefonem. Dane istnieją tylko w pamięci i znikają po odświeżeniu.
2. [Plansza NO](preview-no.png) i [plansza EN](preview-en.png) — osiem głównych ekranów; łatwe do obejrzenia bez uruchamiania prototypu.
3. [Specyfikacja ekranów i przepływów](ux-spec.md).
4. [Stany, błędy i scenariusze odbioru](states-and-review.md).
5. [Model danych i diagram relacji](data-model.md).
6. [Słownik tekstów NO/EN](copy.json) — materiał projektowy, jeszcze nie zasoby Android.

W makiecie użyto fikcyjnego „Example car / Eksempelbil” oraz przykładowego własnego interwału. Żadne wyświetlane wartości nie są zaleceniem producenta. Część „zweryfikowanego planu” ma osobny scenariusz **symulacji**, bez prawdziwych danych OEM. Niczego nie przesyłamy ani nie zapisujemy na dysku; wybór dokumentu, OCR, powiadomienia, eksport i usuwanie są demonstracjami stanów.

### Przegląd w 5 minut

1. Zacznij od „Start”. Wybierz język i dodaj fikcyjne auto z odczytem przebiegu.
2. Na pulpicie otwórz termin; sprawdź rozróżnienie własnego interwału i źródła producenta.
3. Przejdź przez „Wpis ręczny”, potem przez „Dodaj dokument” i „Sprawdź odczyt”. Zauważ, że bez zaznaczenia sprawdzenia danych zapis jest zablokowany.
4. Zmień scenariusz na „Brak harmonogramu”, „Brak historii”, „Stary przebieg” oraz „Offline”. Historia i lokalny odczyt pozostają dostępne.
5. Sprawdź błędy dokumentu i przełącz całość na drugi język.

## 4. Weryfikacja i wyniki

Wyniki wykonanych kontroli są zapisane w [protokole odbioru](states-and-review.md#wykonana-weryfikacja) oraz [raporcie maszynowym](verification.json). Nie utożsamiamy przejścia klikalnej makiety z testem OCR, silnika reguł lub aplikacji Android.

## 5. Ograniczenia i zależności

Nie ma jeszcze aplikacji, bazy Room, OCR, notyfikacji systemowych, eksportu ani integracji źródła. Makieta pokazuje przewidywane zachowanie. Jakość tłumaczeń Bokmål wymaga później przeglądu językowego przez użytkownika/natywnego recenzenta. Licencja i pokrycie danych pozostają otwarte przed Etapem 4.

## 6. Decyzje do akceptacji

| ID | Propozycja |
| --- | --- |
| UX-01 | Nawigacja: Przegląd, Historia, Ustawienia; dodawanie dokumentu dostępne z pulpitu i historii |
| UX-02 | Bez konta i logowania; język dostępny na początku i w ustawieniach |
| UX-03 | Odczyt dokumentu zawsze jako szkic, z korektą i osobnym potwierdzeniem |
| UX-04 | Brak potwierdzenia historii jest osobnym stanem, nie automatyczną zaległością |
| UX-05 | Własne interwały i zalecenia ze zweryfikowanego źródła są wyraźnie oznaczone |
| DATA-01 | Bieżący odczyt przebiegu wybierany jawnie; stary wpis nie cofa go automatycznie |
| DATA-02 | Nie przechowujemy wyniku obliczenia jako prawdy; wyprowadzamy go z wersjonowanych reguł i potwierdzonych wpisów |

## 7. Kryteria odbioru

- [x] Właściciel akceptuje główną nawigację i proces ręczny/OCR.
- [x] Właściciel akceptuje stany braku danych oraz sposób prezentowania źródła.
- [x] Właściciel akceptuje model danych i zasady potwierdzania.
- [x] Właściciel zgadza się rozpocząć Etap 2.

## 8. Następny krok

Po odbiorze: utworzenie minimalnego projektu Android w Kotlin/Compose, nawigacji, zasobów NO/EN, ustawień języka i podstaw lokalnego przechowywania. Pełne funkcje historii, harmonogramów i OCR pozostają w odpowiednich późniejszych etapach.
