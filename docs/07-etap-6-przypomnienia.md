# Etap 6 — przypomnienia: logika (bez części androidowej)

Data: **10 listopada 2026**. Pakiet `app.servicepassport.reminders` w module `service-engine`, 14 testów.

## Status

| Element | Stan |
| --- | --- |
| Decyzja „o czym i kiedy przypomnieć”, brak duplikatów, reakcja na zmianę danych | **Zaimplementowane i przetestowane** (moduł `service-engine`, 15 testów) |
| Przypomnienia w aplikacji: zadanie w tle raz na dobę, powiadomienia NO/EN, ustawienia, dobrowolne uprawnienie | **Zaimplementowane**; 12 testów na prawdziwej bazie i pulpicie z atrapą powierzchni powiadomień |
| Jedno źródło prawdy | Przypomnienia czytają **końcowy wynik pulpitu**, nie surowy wynik silnika — powiadomienie nie powie więcej niż pulpit |
| Test na rzeczywistym telefonie (kryterium odbioru Etapu 6) | **Niewykonany** — faktyczne wyświetlenie powiadomienia, działanie w tle po restarcie telefonu i okno uprawnienia nie były sprawdzane na urządzeniu |
| Duże czcionki, kontrast, czytniki ekranu | **Niesprawdzone** |

Etap 6 **nie jest ukończony**, dopóki nie ma testu na telefonie.

### Zachowanie w aplikacji

- Przypomnienia są **domyślnie wyłączone**; włącza je kierowca w Ustawieniach. Wtedy aplikacja prosi o uprawnienie do powiadomień (Android 13+). Odmowa niczego nie blokuje — ustawienia pokazują, że powiadomienia są wyłączone w systemie.
- Ustawienia: włącz/wyłącz, powtórka (nigdy / 7 / 14 / 30 dni, domyślnie 14), osobny przełącznik przypomnień o przebiegu.
- Sprawdzenie w tle mniej więcej raz na dobę; dokładną porę wybiera Android.
- Gdy aplikacja jest otwarta, nieaktualne powiadomienia są usuwane od razu (np. po dodaniu serwisu), a nowe czekają na sprawdzenie w tle.
- Brak wybranego bieżącego przebiegu także daje jedno przypomnienie o przebiegu.
- Ustawienia przypomnień trafiają do kopii danych; zapis „co już pokazano” nie, a po odtworzeniu kopii jest czyszczony.

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

## Zostaje do zrobienia

- Test na fizycznym telefonie: wyświetlenie, tryb oszczędzania baterii, restart urządzenia.
- Przegląd czytelności i dużych czcionek.

## Decyzje właściciela

Domyślna powtórka co 14 dni — zatwierdzona 10 listopada 2026.
