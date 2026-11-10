# vehicle-catalog — lista marek i modeli do wyboru auta

`vehicle-list.json` to lista marek i modeli aut osobowych zbudowana z otwartego rejestru pojazdów RDW (Holandia).

**Do czego służy:** wyłącznie do wygodnego wyboru auta przy dodawaniu pojazdu. **Nie zawiera danych serwisowych** i nie rozpoznaje konfiguracji (silnik, skrzynia, rynek, system serwisowy). Auto wybrane z listy bez dopuszczonego planu dostaje w aplikacji „Brak potwierdzonego harmonogramu”.

## Stan na 10 listopada 2026

| | |
| --- | --- |
| Źródło listy | [RDW Open Data — Gekentekende voertuigen](https://opendata.rdw.nl/Voertuigen/Open-Data-RDW-Gekentekende_voertuigen/m9d7-ebf2), licencja CC0 1.0 |
| Źródło weryfikacji | [EEA — CO2 emissions from new passenger cars](https://www.eea.europa.eu/en/analysis/maps-and-charts/co2-emissions-from-new-passenger-cars) (tabela `CO2Emission.latest.co2cars`), CC BY wg ogólnej polityki EEA — **wymaga podania źródła w aplikacji** |
| Zakres | auta osobowe pierwszy raz zarejestrowane od 2000 r., bez kamperów i dostawczych: 10 073 867 pojazdów |
| Wynik | 67 marek, 1558 modeli, pokrywających ok. 98% pojazdów źródła |
| Weryfikacja | 749 modeli potwierdzonych w Norwegii (w tym 87 dopisanych spoza RDW), 1299 potwierdzonych w innych krajach Europy |
| Rozmiar | ok. 150 kB |

Dokładne liczby i data pobrania są w polu `meta` pliku.

## Format

```json
{"meta": {...},
 "makes": [{"name": "Volkswagen", "vehicles": 1178000,
            "models": [{"name": "GOLF", "firstYear": 2000, "lastYear": 2026, "vehicles": 215000,
                        "norway": true, "europeCountries": 29}]}]}
```

- lista płaska: marki alfabetycznie, modele od najczęstszego — do użycia z wyszukiwarką;
- `vehicles` = liczba aut w rejestrze holenderskim (do sortowania, nie do pokazywania); `0` dla modeli dopisanych z danych norweskich (`"addedFrom": "EEA"`);
- `firstYear`/`lastYear` = lata pierwszej rejestracji — **nie lata produkcji ani rok modelowy**;
- liczby w przykładzie są poglądowe.

## Dwie opcje weryfikacji

| Opcja | Pole | Znaczenie |
| --- | --- | --- |
| **A. Norwegia** | `norway: true` | model był wśród nowych rejestracji w Norwegii w latach 2019–2022 |
| **B. reszta Europy** | `europeCountries: N` | liczba innych krajów Europy (z 29), w których model rejestrowano jako nowy w latach 2010–2022 |

**Brak pola znaczy „nie potwierdzono”, nie „nie było w sprzedaży”.** Dane EEA nie obejmują Norwegii przed 2019 r., żadnego kraju przed 2010 r. ani nikogo po 2022 r. Dlatego modele wycofane przed 2019 r. (np. Lupo) oraz nowości z lat 2023–2026 (np. ID.7) nie mają potwierdzenia norweskiego. Zalecenie: używać pól do **sortowania lub oznaczenia**, nie do ukrywania pozycji.

## Zasady

1. Marki: scalanie wariantów zapisu (`brand_rules.json`); marka musi mieć min. 500 aut w źródle — niszowe i historyczne odpadają.
2. Tylko auta z pierwszą rejestracją od 2000 r. Starsze użytkownik wpisuje ręcznie.
3. Nazwy modeli: usunięcie powtórzonej marki, mocy („150 KW”), kodów typu i emisji; scalanie różnic zapisu (`T-ROC` / `T ROC`).
4. Próg modelu: 250 aut dla dużych marek; dla małych 2% aut marki, minimum 25.
5. Model spoza RDW jest dopisywany, jeśli był rejestrowany w Norwegii w co najmniej 2 różnych latach.
6. Dopasowanie nazw między źródłami: model jest potwierdzony, gdy nazwa w EEA zaczyna się od tych samych słów („GOLF” ~ „GOLF VARIANT”).

## Znane ograniczenia

- **Poziom szczegółowości zależy od producenta.** U VW pozycją jest „GOLF”, u BMW i Mercedesa wariant („320I”, „C 180”), u Audi wersje nadwozia. BMW ma ponad 200 pozycji.
- **Weryfikacja jest częściowa** — patrz zakres lat wyżej. Poprawności dopasowania nazw nie sprawdzałem ręcznie poza wyrywkowym przeglądem kilku marek.
- Bez liczby rejestracji w Norwegii: serwis EEA odrzuca zapytania zliczające, więc wiemy tylko „był / nie był”.
- Marki obecne w Norwegii, ale poniżej progu w źródle, nie są na liście (z przeglądu: Maxus, Hongqi, Seres, Voyah, JAC, Morgan).
- Pozostał szum w nazwach; nazwy są wielkimi literami, jak w rejestrze.
- W aplikacji musi zostać ręczne wpisanie marki i modelu spoza listy.

## Odtworzenie

```bash
python3 build_vehicle_list.py
```

Pobiera dane z RDW i EEA (bez kluczy) i nadpisuje `vehicle-list.json`; trwa kilka minut. Wymaga dostępu sieciowego do `opendata.rdw.nl` i `discodata.eea.europa.eu`. Serwis EEA odrzuca zapytania z `GROUP BY`, `ORDER BY` i filtrem po części krajów, dlatego skrypt pyta rok po roku. Testy: `python3 -m unittest`.
