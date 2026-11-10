# Etap 7 (część) — format eksportu i odtwarzania danych

Data: **10 listopada 2026**. Pakiet `app.servicepassport.export` w module `service-engine`, 19 testów.

## Status

| Element | Stan |
| --- | --- |
| Format archiwum, zapis, odczyt z pełną weryfikacją, opcjonalne hasło | **Zaimplementowane i przetestowane** (moduł `service-engine`, 19 testów) |
| Mapowanie bazy aplikacji (Room v3) ↔ format, zapis i odtworzenie całej bazy | **Zaimplementowane i przetestowane**: `app/.../backup/GarageBackup.kt`, 5 testów obiegu baza → kopia → nowa baza |
| Ekran w Ustawieniach: zapis kopii z opcjonalnym hasłem, odtworzenie, usunięcie wszystkich danych | **Zaimplementowane**; logika przetestowana na prawdziwych plikach (5 testów). Sam ekran i systemowe okno wyboru pliku **nie były oglądane ani klikane na urządzeniu** |
| Sprawdzenie na urządzeniu z Androidem 8–12 | **Nie wykonano** dla pełnego odtworzenia bazy (test biblioteki archiwum na API 26 istnieje) |
| Usuwanie danych | **Zaimplementowane**: usuwa auto, historię, odczyty, interwały, ustawienia przypomnień i pokazane powiadomienia; kopii zapisanych poza aplikacją nie dotyka |
| Audyt SDK i logów, polityka prywatności | **Nie ma** — pozostała część Etapu 7 |

### Co potwierdza test obiegu

Baza wypełniona przez zwykłe operacje aplikacji (auto z pełną konfiguracją i jej rewizją, dwa wpisy serwisowe z edycją, prace z kodami i własnym opisem w ustalonej kolejności, koszt, notatki, trzy odczyty, **świadomie wybrany starszy odczyt z wyjaśnieniem**, dwa własne interwały z edycją) jest zapisywana do archiwum i odtwarzana w nowej bazie. Porównywane są wszystkie wiersze wszystkich sześciu tabel — muszą być identyczne, łącznie z rewizjami i znacznikami czasu. Po odtworzeniu baza przyjmuje kolejne zmiany.

Zasady odtwarzania:

- zastępuje wszystkie dane lokalne w jednej transakcji; każda odmowa zostawia bazę nietkniętą;
- odmowa, gdy kopia zawiera coś, czego ta wersja aplikacji nie umie zapisać bez straty: kod czynności spoza kontraktu, walutę inną niż NOK, dokumenty lub odczyty wskaźnika, więcej niż jedno auto.

## Format (wersja 1)

Archiwum ZIP:

- `manifest.json` — wersja formatu, data, wersja aplikacji, lista plików z rozmiarem i skrótem SHA-256;
- `data.json` — odwzorowanie tabel bazy aplikacji w wersji 2: pojazdy z jawnym wyborem bieżącego odczytu, odczyty z wyjaśnieniami anomalii, wpisy serwisowe z uporządkowanymi pracami (para kodów albo własny opis), rewizje i znaczniki czasu; do tego dokumenty, własne interwały, odczyty wskaźnika i ustawienia;
- `documents/` — oryginalne zdjęcia i PDF.

**Nie są eksportowane:** reguły z licencjonowanego katalogu (prawa dostawcy), surowy OCR, szkice i miniatury.

## Gwarancje

- **Zapis:** przed utworzeniem pliku sprawdzana jest spójność (każdy wpis ma pojazd, każdy dokument ma plik itd.). Przy błędzie nic nie jest zapisywane.
- **Odczyt:** archiwum jest przyjmowane tylko w całości. Odrzucane są: zmieniony lub brakujący plik, plik spoza manifestu, ścieżka wychodząca poza `documents/`, zbyt duże archiwum, niespójna treść, format nowszy niż obsługiwany.
- Nieznane pola w tej samej wersji formatu są ignorowane, więc drobne rozszerzenia nie psują starszych plików.

## Opcjonalne hasło (decyzja właściciela z 10 listopada 2026)

Domyślnie kopia jest jawna. Gdy użytkownik poda hasło, cała kopia jest szyfrowana standardowymi mechanizmami platformy: klucz z hasła przez PBKDF2-HMAC-SHA256 (600 000 iteracji), treść AES-256-GCM w kawałkach po 64 KiB.

- Zaszyfrowany plik nie ujawnia nazw plików, VIN ani treści; każda zmiana, obcięcie lub zamiana kolejności fragmentów jest wykrywana.
- Złe hasło i uszkodzony plik dają ten sam komunikat — nie da się ich rozróżnić.
- **Zapomnianego hasła nie da się odzyskać.** Aplikacja musi to powiedzieć przed eksportem.
- Ekran wymaga co najmniej 8 znaków i powtórzenia hasła oraz ostrzega, że zapomnianego hasła nie da się odzyskać; bez hasła informuje, że plik jest czytelny dla każdego.
- Szyfrowanie nie było sprawdzane przez niezależnego specjalistę; warto to zrobić w ramach przeglądu bezpieczeństwa Etapu 7.

## Ograniczenia

- Bezstratność jest potwierdzona testem dla bazy v3 w środowisku testowym (Robolectric), nie na fizycznym telefonie.
- Kopia nie obejmuje języka aplikacji (jest ustawieniem systemowym, nie wierszem bazy).
- Usunięto wywołanie niedostępne przed Androidem 13; działania na Androidzie 8–12 nie sprawdzono na urządzeniu.
- Przy nieudanym imporcie aplikacja musi usunąć pliki dokumentów zapisane w trakcie czytania i nie zmieniać bazy — to obowiązek warstwy aplikacji, opisany w kodzie.
- Nie testowano bardzo dużych archiwów (setki MB) na telefonie.
