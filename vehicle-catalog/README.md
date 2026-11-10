# vehicle-catalog — lista marek i modeli do wyboru auta

`vehicle-list.json` to lista marek i modeli aut osobowych zbudowana z otwartego rejestru pojazdów RDW (Holandia).

**Do czego służy:** wyłącznie do wygodnego wyboru auta przy dodawaniu pojazdu. **Nie zawiera danych serwisowych** i nie rozpoznaje konfiguracji (silnik, skrzynia, rynek, system serwisowy). Auto wybrane z listy bez dopuszczonego planu dostaje w aplikacji „Brak potwierdzonego harmonogramu”.

## Stan na 10 listopada 2026

| | |
| --- | --- |
| Źródło | [RDW Open Data — Gekentekende voertuigen](https://opendata.rdw.nl/Voertuigen/Open-Data-RDW-Gekentekende_voertuigen/m9d7-ebf2), licencja CC0 1.0 |
| Zakres | auta osobowe (`Personenauto`) bez kamperów: 10 640 982 pojazdy |
| Wynik | 139 marek, ok. 1,97 tys. pozycji modeli, pokrywających ok. 96% pojazdów źródła |
| Rozmiar | ok. 140 kB (JSON bez formatowania) |

Dokładne liczby i data pobrania są w polu `meta` pliku.

## Format

```json
{"meta": {...},
 "makes": [{"name": "Volkswagen", "vehicles": 1221279,
            "models": [{"name": "POLO", "firstYear": 1976, "lastYear": 2026, "vehicles": 288691}]}]}
```

- marki alfabetycznie, modele od najczęstszego;
- `vehicles` = liczba aut w rejestrze holenderskim (do sortowania wg popularności, nie do pokazywania);
- `firstYear`/`lastYear` = lata pierwszej rejestracji widziane w rejestrze — **to nie są lata produkcji ani rok modelowy**.

## Zasady czyszczenia

1. Marki: scalanie oczywistych wariantów zapisu (`brand_rules.json`, np. `VW` → Volkswagen, `TESLA MOTORS` → Tesla); marka musi mieć min. 100 aut.
2. Nazwy modeli: usunięcie powtórzonej marki, mocy („150 KW”), fabrycznych kodów typu i kodów emisji; scalanie różnic w zapisie (`T-ROC` / `T ROC`).
3. Próg modelu: 250 aut dla dużych marek; dla małych 2% aut marki, minimum 25.

## Znane ograniczenia

- **To rynek holenderski, nie norweski.** Modele sprzedawane w Norwegii, a nie w Holandii, mogą nie występować; kolejność popularności jest holenderska. Nie porównałem listy z rejestrem norweskim (jego zbiór danych jest obecnie niedostępny do pobrania).
- **Poziom szczegółowości zależy od producenta.** RDW przechowuje nazwę handlową z dokumentu: u VW to „GOLF”, u BMW i Mercedesa to wariant („320I”, „C 180”), u Audi wersje nadwozia. Lista nie grupuje ich w rodziny modeli.
- **Pozostał szum**, zwłaszcza przy starych autach (np. „GOLF CL”, „240 GL”, kody typu „113”).
- 11 niszowych marek historycznych ma pustą listę modeli (każdy ich model jest poniżej progu).
- Nazwy modeli są wielkimi literami, tak jak w rejestrze.
- W aplikacji musi zostać możliwość ręcznego wpisania marki i modelu spoza listy.

## Odtworzenie

```bash
python3 build_vehicle_list.py
```

Pobiera zagregowane dane z API RDW (bez klucza, 2 zapytania) i nadpisuje `vehicle-list.json`. Wymaga dostępu sieciowego do `opendata.rdw.nl`. Testy: `python3 -m unittest`.
