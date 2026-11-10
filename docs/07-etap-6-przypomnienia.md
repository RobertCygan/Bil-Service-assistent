# Etap 6 — przypomnienia: logika (bez części androidowej)

Data: **10 listopada 2026**. Pakiet `app.servicepassport.reminders` w module `service-engine`, 14 testów.

## Status

| Element | Stan |
| --- | --- |
| Decyzja „o czym i kiedy przypomnieć”, brak duplikatów, reakcja na zmianę danych | **Zaimplementowane i przetestowane** na wynikach prawdziwego silnika terminów |
| Powiadomienia Androida, WorkManager, prośba o uprawnienie, ekran ustawień, teksty NO/EN | **Nie ma** — wymaga kodu etapów 1–3 |
| Test na rzeczywistym telefonie (kryterium odbioru Etapu 6) | **Niewykonany** |
| Czytelność, duże czcionki, kontrast | **Nie dotyczy tej części** — to praca nad ekranami |

Etap 6 **nie jest ukończony**.

## Jak działa

`ReminderPlanner.plan(pojazd, wyniki silnika, dziś, ustawienia, już wysłane)` zwraca:

- `notifyNow` — co pokazać teraz;
- `cancel` — które powiadomienia usunąć, bo przestały być aktualne;
- `nextCheckOn` — najbliższy dzień, w którym coś zmieni się samo z upływem czasu (wskazówka dla zadania w tle).

Funkcja jest bezstanowa: aplikacja przechowuje listę wysłanych przypomnień i podaje ją przy każdym wywołaniu.

| Sytuacja | Zachowanie |
| --- | --- |
| Czynność „zaległa” lub „zbliża się” | Jedno przypomnienie na regułę; zaległe przed zbliżającymi się |
| „Wymaga danych”, „bez pilnej potrzeby” | Bez powiadomienia — pokazuje to pulpit |
| Ten sam stan następnego dnia | Nic; powtórka dopiero po ustawionej liczbie dni (domyślnie 14, można wyłączyć) |
| „Zbliża się” przechodzi w „zaległe” | Nowe przypomnienie od razu, stare do usunięcia |
| Użytkownik dodał serwis | Przypomnienie do usunięcia, bez nowego |
| Poprawka wpisu przesuwa termin | Nowe przypomnienie mimo niedawnego (zmienił się stan) |
| Nieaktualny przebieg | **Jedno** przypomnienie na pojazd, niezależnie od liczby reguł; znika po nowym odczycie |
| Wygasłe źródło reguły, albo wynik zależny od km przy niespójnym liczniku | Bez przypomnienia o czynności; reguła trafia do listy `suppressed` z przyczyną |
| Niespójny licznik lub nowszy odczyt w historii niż wybrany | Jedno przypomnienie o sprawdzeniu przebiegu |
| Przypomnienia wyłączone | Nic do pokazania, wszystko z tego pojazdu do usunięcia |

## Do zrobienia w aplikacji

- Dobrowolna prośba o uprawnienie do powiadomień; odmowa nie blokuje aplikacji.
- Zadanie w tle uruchamiane codziennie (Android nie gwarantuje dokładnej godziny) — `nextCheckOn` tylko pomaga, nie zastępuje codziennego sprawdzenia.
- Tabela wysłanych przypomnień (`klucz`, `odcisk stanu`, `data`).
- Treści w `nb-NO` i `en`, w tym jasny komunikat, że aplikacja nie zna bieżącego przebiegu bez wpisu.

## Decyzja dla właściciela

Domyślna powtórka co 14 dni to moja propozycja (ustawienie aplikacji, nie zalecenie producenta). Zostaje?
