# vehicle-catalog — lista marek i modeli do wyboru auta

`vehicle-list.json` to lista marek i modeli aut osobowych zbudowana z otwartego rejestru pojazdów RDW (Holandia).

**Do czego służy:** wyłącznie do wygodnego wyboru auta przy dodawaniu pojazdu. **Nie zawiera danych serwisowych** i nie rozpoznaje konfiguracji (silnik, skrzynia, rynek, system serwisowy). Auto wybrane z listy bez dopuszczonego planu dostaje w aplikacji „Brak potwierdzonego harmonogramu”.

## Stan na 10 listopada 2026

| | |
| --- | --- |
| Źródło listy | [RDW Open Data — Gekentekende voertuigen](https://opendata.rdw.nl/Voertuigen/Open-Data-RDW-Gekentekende_voertuigen/m9d7-ebf2), licencja CC0 1.0 |
| Źródło weryfikacji | [EEA — CO2 emissions from new passenger cars](https://www.eea.europa.eu/en/analysis/maps-and-charts/co2-emissions-from-new-passenger-cars) (tabela `CO2Emission.latest.co2cars`), CC BY wg ogólnej polityki EEA — **wymaga podania źródła w aplikacji** |
| Zakres | auta osobowe pierwszy raz zarejestrowane od 2000 r., bez kamperów i dostawczych: 10 073 867 pojazdów |
| Wynik | 73 marki, 1746 modeli, pokrywających ok. 98% pojazdów źródła |
| Weryfikacja nordycka | 1385 modeli potwierdzonych w co najmniej jednym kraju nordyckim (w tym 259 dopisanych spoza RDW): NO 771, SE 1313, FI 1189, DK 1022, IS 496 |
| Rozmiar | ok. 170 kB |

Dokładne liczby i data pobrania są w polu `meta` pliku.

## Format

```json
{"meta": {...},
 "makes": [{"name": "Volkswagen", "vehicles": 1178000,
            "models": [{"name": "GOLF", "firstYear": 2000, "lastYear": 2026, "vehicles": 215000,
                        "nordic": ["NO", "SE", "FI", "DK", "IS"]}]}]}
```

- lista płaska: marki alfabetycznie, modele od najczęstszego — do użycia z wyszukiwarką;
- `vehicles` = liczba aut w rejestrze holenderskim (do sortowania, nie do pokazywania); `0` dla modeli dopisanych z danych norweskich (`"addedFrom": "EEA"`);
- `firstYear`/`lastYear` = lata pierwszej rejestracji — **nie lata produkcji ani rok modelowy**;
- liczby w przykładzie są poglądowe.

## Weryfikacja nordycka

Zakres ograniczony decyzją właściciela do krajów nordyckich; reszta Europy jest odłożona na późniejszą aktualizację.

Pole `nordic` to lista krajów (`NO`, `SE`, `FI`, `DK`, `IS`), w których model był wśród nowych rejestracji. Zakres lat w danych EEA:

| Kraj | Lata |
| --- | --- |
| Szwecja, Finlandia, Dania | 2010–2022 |
| Islandia | 2018–2022 |
| Norwegia | 2019–2022 |

**Brak kraju w polu znaczy „nie potwierdzono”, nie „nie było w sprzedaży”.** Modele wycofane przed początkiem danych (np. Lupo) i nowości z lat 2023–2026 (np. ID.7) nie mają potwierdzenia; 361 modeli nie ma żadnego kraju. Zalecenie: używać pola do **sortowania lub oznaczenia**, nie do ukrywania pozycji.

## Zasady

1. Marki: scalanie wariantów zapisu (`brand_rules.json`); marka musi mieć min. 500 aut w źródle — niszowe i historyczne odpadają. Wyjątek (`alwaysInclude`): Maxus, Hongqi, Seres, Voyah, JAC i Morgan, obecne w Norwegii.
2. Tylko auta z pierwszą rejestracją od 2000 r. Starsze użytkownik wpisuje ręcznie.
3. Nazwy modeli: usunięcie powtórzonej marki, mocy („150 KW”), kodów typu i emisji; scalanie różnic zapisu (`T-ROC` / `T ROC`, `HURACÁN` / `HURACAN`).
4. Próg modelu: 250 aut dla dużych marek; dla małych 2% aut marki, minimum 25.
5. Model spoza RDW jest dopisywany, jeśli był rejestrowany w co najmniej 2 krajach nordyckich i w 2 różnych latach.
6. Dopasowanie nazw między źródłami: model jest potwierdzony, gdy nazwa w EEA zaczyna się od tych samych słów („GOLF” ~ „GOLF VARIANT”).

## Znane ograniczenia

- **Poziom szczegółowości zależy od producenta.** U VW pozycją jest „GOLF”, u BMW i Mercedesa wariant („320I”, „C 180”), u Audi wersje nadwozia. BMW ma ponad 200 pozycji.
- **Weryfikacja jest częściowa** — patrz zakres lat wyżej. Poprawności dopasowania nazw nie sprawdzałem ręcznie poza wyrywkowym przeglądem kilku marek.
- Bez liczby rejestracji w krajach nordyckich: serwis EEA odrzuca zapytania zliczające, więc wiemy tylko „był / nie był”.
- Sześć marek dopisanych wyjątkiem ma bardzo krótkie listy modeli (np. JAC: jedna pozycja), bo w źródłach jest ich mało.
- Pozostał szum w nazwach; nazwy są wielkimi literami, jak w rejestrze.
- W aplikacji musi zostać ręczne wpisanie marki i modelu spoza listy.

## Użycie w aplikacji

Plik jest dołączany do aplikacji przy budowaniu (`app/build.gradle.kts`, zadanie `vehicleCatalogAssets`). W formularzu auta podpowiada marki i modele; wpisanie dowolnej innej marki lub modelu nadal jest możliwe. Źródła i licencje są wymienione w Ustawieniach aplikacji (wymóg CC BY dla danych EEA).

## Odtworzenie

```bash
python3 build_vehicle_list.py
```

Pobiera dane z RDW i EEA (bez kluczy) i nadpisuje `vehicle-list.json`; trwa kilka minut. Wymaga dostępu sieciowego do `opendata.rdw.nl` i `discodata.eea.europa.eu`. Serwis EEA odrzuca zapytania z `GROUP BY`, `ORDER BY` i filtrem po części krajów, dlatego skrypt pyta rok po roku. Testy: `python3 -m unittest`.
