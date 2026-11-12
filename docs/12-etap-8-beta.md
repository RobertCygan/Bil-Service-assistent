# Etap 8 — gotowość do testów beta

Data: **10 listopada 2026**. Stan kodu: `main` po PR #14.

## Status

| Element | Stan |
| --- | --- |
| Testy automatyczne | **Działają w CI** przy każdej zmianie (liczby poniżej) |
| Test na fizycznym telefonie | **Niewykonany** — lista kontrolna poniżej czeka na właściciela |
| Pomiar rozmiaru wydania | **Wykonany** na niepodpisanym pakiecie wydaniowym |
| Zmniejszanie kodu (R8) w wydaniu | **Wyłączone.** Pakiet z R8 buduje się i jest mniejszy, ale jego działania nie da się obecnie sprawdzić automatycznie |
| Podpisywanie wydania, pakiet AAB, konto Google Play | **Nie ma** (Etap 9) |

Etap 8 **nie jest ukończony**: bez testu na telefonie nie można uznać aplikacji za gotową do bety.

## Testy automatyczne

| Zestaw | Liczba testów | Gdzie działa |
| --- | --- | --- |
| Aplikacja — testy jednostkowe (baza, historia, pulpit, przypomnienia, kopia, dokumenty, katalog, prywatność) oraz testy przepływów przez prawdziwe ekrany z atrapą okien systemowych | 197 | JVM (Robolectric) |
| Aplikacja — testy na emulatorze (start, migracje bazy, pliki dokumentów, prawdziwy ML Kit na pliku PDF, na zdjęciu zapisanym bokiem i na narysowanym zestawie wskaźników, brak sieci, kopia z hasłem przez ekran ustawień) | 12 | Emulator w CI, API 26 i nowsze |
| Silnik (obliczenia, ekstrakcja, przypomnienia, eksport, szyfrowanie, VIN) | 113 | JVM |
| Wspólny kontrakt | 3 | JVM |
| Serwer pośredniczący | 11 | JVM — **bez prawdziwego API vegvesen** |

Czego testy **nie** obejmują:

- wyglądu i czytelności ekranów, dużych czcionek, trybu ciemnego, czytnika ekranu;
- powiadomień wyświetlanych przez system i okna zgody na powiadomienia;
- systemowego okna wyboru pliku, aplikacji aparatu i otwierania dokumentu w innej aplikacji;
- odczytu prawdziwych zdjęć faktur (test używa czystego, wygenerowanego PDF);
- zachowania po wielu dniach (codzienne sprawdzanie przypomnień, oszczędzanie baterii producentów telefonów).

## Rozmiar wydania

Zmierzone na `assembleRelease` (niepodpisany, wszystkie architektury w jednym pliku):

| Składnik | Bez R8 | Z R8 (pomiar próbny) |
| --- | --- | --- |
| Kod aplikacji i bibliotek | 9,0 MB | 2,0 MB |
| Model i silnik rozpoznawania tekstu, arm64 | 11,1 MB | 11,1 MB |
| Lista marek i modeli, zasoby, reszta | 2,4 MB | 2,2 MB |
| **Szacunek pobrania na typowy telefon (arm64, przez Google Play)** | **ok. 22,5 MB** | **ok. 15,3 MB** |
| Jeden plik ze wszystkimi architekturami | 52,6 MB | ok. 45 MB |

Szacunek „na telefon” to suma składników dla jednej architektury; dokładną wartość poda Google Play po wgraniu pakietu AAB.

**Decyzja:** R8 zostaje wyłączone do czasu, gdy zmniejszony pakiet da się uruchomić i sprawdzić na telefonie. Zmniejszanie kodu potrafi usunąć coś potrzebnego bibliotekom (baza, zapis kopii, ML Kit), a taki błąd wychodzi dopiero w działaniu.

**Próba automatycznego sprawdzenia (PR #16) — nieudana.** Testy urządzeniowe uruchomione na zmniejszonym pakiecie nie wystartowały: biblioteki testowe korzystają z klas, które R8 usuwa z aplikacji jako nieużywane (pierwsza brakująca: `androidx.tracing.Trace`). Obejście wymagałoby zachowania w wariancie testowym dużej części bibliotek, a wtedy test sprawdzałby inny pakiet niż ten, który trafia do użytkownika. Zmiana została wycofana. Właściwa droga: zbudować zmniejszone wydanie, zainstalować na telefonie i przejść listę kontrolną poniżej.

## Lista kontrolna do testu na telefonie

Instalacja: plik `app-debug.apk` z artefaktów CI. Wszystkie dane testowe wymyślone albo własne — nic nie opuszcza telefonu.

1. **Start i język** — aplikacja startuje; przełączenie norweski/angielski zmienia wszystkie teksty.
2. **Auto** — podpowiedzi marki i modelu pojawiają się podczas pisania; błędny VIN daje komunikat; zapis działa.
3. **Przebieg i wpis serwisowy** — dodanie odczytu, dodanie wpisu ręcznie, edycja, usunięcie.
4. **Własny interwał i pulpit** — po dodaniu interwału pulpit pokazuje termin; bez interwału pokazuje brak potwierdzonego harmonogramu.
4a. **Zdjęcie licznika** — w formularzu przebiegu „Zrób zdjęcie licznika”: po zdjęciu zegarów liczba pojawia się w polu; sprawdzić na własnym aucie przy świetle dziennym i w nocy, z bliska i z miejsca kierowcy; błędny odczyt da się poprawić ręcznie albo wybrać inną liczbę z tego samego zdjęcia.
5. **Wskaźnik serwisowy** — wpisanie odczytu z wyświetlacza auta zmienia pulpit.
6. **Dokument z pliku** — wybór zdjęcia i PDF; ekran sprawdzenia pokazuje datę, przebieg i pozycje ze źródłem; zapis wpisu.
7. **Zdjęcie aparatem** — przycisk otwiera aparat; po zrobieniu zdjęcia pojawia się ekran sprawdzenia. Sprawdzić też z telefonem obróconym poziomo podczas robienia zdjęcia oraz obrót telefonu na ekranie sprawdzenia i w formularzu wpisu — dokument nie może zniknąć.
8. **Dokument przy wpisie** — w szczegółach wpisu dokument się otwiera; usunięcie dokumentu zostawia wpis.
9. **Duplikat** — ponowne wczytanie tego samego pliku daje ostrzeżenie.
10. **Przypomnienia** — włączenie pyta o zgodę; po odmowie pojawia się wyjaśnienie i przycisk prowadzący do ustawień powiadomień, a po powrocie z włączonymi powiadomieniami wyjaśnienie znika; powiadomienie pojawia się dla zaległej pozycji (może to potrwać do doby); ikona na pasku stanu ma kształt auta (nie jest pełnym kwadratem), a tekst jest w języku wybranym w aplikacji, także gdy telefon ma inny język.
11. **Kopia** — zapis kopii z hasłem i bez; „Usuń wszystko”; przywrócenie kopii przywraca auto, historię i dokumenty; złe hasło daje jasny komunikat.
12. **Duża czcionka i tryb ciemny** — ekrany pozostają czytelne, nic nie jest ucięte.
13. **Tryb samolotowy** — wszystko powyższe działa bez sieci.

Przy każdym punkcie wystarczy: działa / nie działa / uwagi (najlepiej ze zrzutem ekranu).

## Kryteria wejścia do bety

- Lista kontrolna przeszła na co najmniej jednym telefonie.
- Odczyt dokumentów zmierzony na prawdziwych fakturach NO/EN (min. 40) — albo beta wyraźnie oznacza tę funkcję jako próbną.
- Polityka prywatności opublikowana pod adresem URL.
