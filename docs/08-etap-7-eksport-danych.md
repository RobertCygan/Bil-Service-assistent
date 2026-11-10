# Etap 7 (część) — format eksportu i odtwarzania danych

Data: **10 listopada 2026**. Pakiet `app.servicepassport.export` w module `service-engine`, 9 testów.

## Status

| Element | Stan |
| --- | --- |
| Format archiwum, zapis, odczyt z pełną weryfikacją, kontrola spójności treści | **Zaimplementowane i przetestowane** |
| Mapowanie bazy aplikacji ↔ format, ekran eksportu/importu, wybór miejsca zapisu | **Nie ma** — wymaga kodu etapów 1–3 |
| Usuwanie danych, audyt SDK i logów, polityka prywatności | **Nie ma** — pozostała część Etapu 7 |

## Format (wersja 1)

Archiwum ZIP:

- `manifest.json` — wersja formatu, data, wersja aplikacji, lista plików z rozmiarem i skrótem SHA-256;
- `data.json` — pojazdy, odczyty przebiegu, wpisy serwisowe z czynnościami, dokumenty, własne interwały użytkownika, odczyty wskaźnika serwisowego, ustawienia;
- `documents/` — oryginalne zdjęcia i PDF.

**Nie są eksportowane:** reguły z licencjonowanego katalogu (prawa dostawcy), surowy OCR, szkice i miniatury.

## Gwarancje

- **Zapis:** przed utworzeniem pliku sprawdzana jest spójność (każdy wpis ma pojazd, każdy dokument ma plik itd.). Przy błędzie nic nie jest zapisywane.
- **Odczyt:** archiwum jest przyjmowane tylko w całości. Odrzucane są: zmieniony lub brakujący plik, plik spoza manifestu, ścieżka wychodząca poza `documents/`, zbyt duże archiwum, niespójna treść, format nowszy niż obsługiwany.
- Nieznane pola w tej samej wersji formatu są ignorowane, więc drobne rozszerzenia nie psują starszych plików.

## Ograniczenia i decyzje do podjęcia

- **Plik nie jest szyfrowany**, a zawiera faktury i VIN. Czy dodać hasło do eksportu, to decyzja właściciela (kosztem: zapomniane hasło = utracona kopia).
- Struktura `data.json` to **moja propozycja kontraktu**; model danych z Etapu 1 może wymagać jej dopasowania.
- Przy nieudanym imporcie aplikacja musi usunąć pliki dokumentów zapisane w trakcie czytania i nie zmieniać bazy — to obowiązek warstwy aplikacji, opisany w kodzie.
- Nie testowano bardzo dużych archiwów (setki MB) na telefonie.
