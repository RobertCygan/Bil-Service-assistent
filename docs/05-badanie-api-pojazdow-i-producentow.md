# Badanie: listy marek/modeli oraz API producentów z danymi serwisowymi

Data: **10 listopada 2026**. Uzupełnia rejestr źródeł z [01-dane-i-zrodla.md](01-dane-i-zrodla.md).

## Metoda i jej ograniczenia

Badanie oparto na wyszukiwaniu w sieci. **Bezpośrednie pobranie stron i wywołanie API było zablokowane przez politykę sieciową sesji** (portale producentów, vegvesen.no, NHTSA, RDW), więc:

- „Potwierdzone” = treść oficjalnej strony widoczna w wynikach wyszukiwania, nie samodzielnie otwarta strona ani wykonane zapytanie do API.
- „Źródło wtórne” = dokumentacja integracji społecznościowych, blogi, katalogi API.
- Żadnego API nie przetestowano. Nie sprawdzono, czy listy zawierają konkretne modele.

## Część A — lista marek i modeli do wyboru

Cel: bogata lista, z której kierowca wybiera auto; brak harmonogramu daje jawne „Brak potwierdzonego harmonogramu”.

| Źródło | Co daje | Licencja / dostęp | Ocena dla nas |
| --- | --- | --- | --- |
| [RDW Open Data (Holandia)](https://data.overheid.nl/dataset/11441-open-data-rdw--gekentekende-voertuigen) | Rejestr wszystkich zarejestrowanych pojazdów; pola `merk` i `handelsbenaming` (źródło wtórne co do nazw pól) | CC0 wg data.overheid.nl; API Socrata bez klucza (źródło wtórne) | **Najlepszy kandydat na listę europejską.** Nazwy handlowe są wpisywane niejednolicie — wymaga czyszczenia |
| [Statens vegvesen — zbiór danych](https://www.vegvesen.no/fag/teknologi/apne-data/et-utvalg-apne-data/api-for-kjoretoyopplysninger/) | Dane techniczne pojazdów w Norwegii, bez VIN i numerów rej. | CC BY 4.0 | Idealne dla rynku NO, ale **pobieranie jest obecnie wyłączone** (zlikwidowany zewnętrzny „datahotell”; brak daty powrotu) |
| [Statens vegvesen — API pojedynczych zapytań](https://dataut.vegvesen.no/dataservice/kjoretoyopplysninger-tekniske-data-enkeltoppslag) | Dane techniczne po numerze rej. lub VIN | Klucz API (wymaga norweskiej e-ID), limit 50 000 zapytań/klucz/dobę; CC BY 4.0 wg wyników | Dobre do „wpisz numer rejestracyjny”. Klucz nie może być w aplikacji → potrzebny mały backend. Obecność kodu silnika/skrzyni **niepotwierdzona**. Kontakt: api-kjoretoy@vegvesen.no |
| [EEA — CO2 nowych aut](https://www.eea.europa.eu/en/analysis/maps-and-charts/co2-emissions-from-new-passenger-cars) | Każda nowa rejestracja w Europie: marka, nazwa handlowa, typ/wariant/wersja, napęd | Ogólna polityka EEA: CC BY; licencja tego konkretnego zbioru niepotwierdzona | Dobre uzupełnienie o warianty i roczniki; duże pliki, dane od ok. 2010 r. |
| [NHTSA vPIC](https://vpic.nhtsa.dot.gov/api/) | Marki, modele, dekoder VIN | Bezpłatne, bez klucza (źródło wtórne); oficjalnie tylko informacja o automatycznym limicie ruchu | Dane z amerykańskich zgłoszeń producentów — **modele europejskie i chińskie mogą być niepełne** |
| Bazy komercyjne (np. [car2db](https://car2db.com/), [vehapi](https://vehapi.com/)) | Marka/model/generacja/wersja | Ceny deklarowane przez sprzedawców: jednorazowo ok. 95 USD lub 19–49 USD/mies. | Opcja zapasowa; prawa do dystrybucji w aplikacji trzeba sprawdzić w licencji |

**Rekomendacja:** lista budowana offline (skrypt przy wydaniu) z RDW, docelowo z norweskiego zbioru vegvesen, gdy wróci. Lista jedzie w aplikacji — bez backendu, bez kluczy, działa offline. W liście wymagana atrybucja źródeł (CC BY).

**Uwaga produktowa:** lista marek i modeli **nie identyfikuje konfiguracji** wymaganej przez harmonogram (kod silnika, skrzynia, rynek, system serwisowy). Wybór z listy rozwiązuje wygodę dodania auta, nie przypisanie planu.

## Część B — czy producenci udostępniają API z danymi serwisowymi?

**Wniosek: nie znaleziono żadnego producenta, który publicznie udostępnia API z planowymi harmonogramami serwisowymi (interwałami) do użycia w aplikacji konsumenckiej.** Istnieją trzy inne rzeczy, które łatwo z tym pomylić.

### 1. Informacje naprawczo-serwisowe (RMI) — obowiązek prawny, ale nie API dla aplikacji

- [Rozporządzenie (UE) 2018/858](https://eur-lex.europa.eu/eli/reg/2018/858/oj/eng) nakazuje producentom udostępniać niezależnym podmiotom informacje o naprawie i obsłudze; wolno pobierać „rozsądne i proporcjonalne” opłaty.
- TSUE, [C-390/21 (ADPA/Peugeot)](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:62021CJ0390): wydawcy informacji technicznych są „niezależnymi podmiotami” z prawem dostępu. TSUE, [C-319/22 (Scania)](https://eur-lex.europa.eu/legal-content/en/TXT/?uri=CELEX:62022CJ0319): dane mają być dostępne jako zbiory nadające się do odczytu maszynowego.
- [Rozporządzenie delegowane (UE) 2026/699](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=OJ%3AL_202600699) (w mocy od 23 czerwca 2026 wg [analizy Taylor Wessing](https://www.taylorwessing.com/en/insights-and-events/insights/2026/07/new-eu-rules-on-access-to-vehicle-data-and-repair-information)) dodaje do załącznika X wymagania dot. API — według znalezionych opisów chodzi o diagnostykę i bezpieczny dostęp OBD; zakres dot. harmonogramów niepotwierdzony.
- W praktyce z tych praw korzystają wydawcy (TecRMI, Autodata, HaynesPro) i warsztaty przez płatne portale producentów. **To potwierdza ścieżkę z Etapu 0: licencja od wydawcy.** Czy aplikacja konsumencka sama mogłaby być „niezależnym podmiotem” — pytanie prawne, nierozstrzygnięte.

### 2. Dane z połączonego auta — „ile zostało do serwisu” wyliczone przez pojazd

To nie harmonogram, tylko bieżący stan wskaźnika serwisowego konkretnego auta. Pasuje do zatwierdzonego wejścia `IndicatorReading` w naszym silniku.

| Producent | Co znaleziono | Dostęp i koszt | Pewność |
| --- | --- | --- | --- |
| BMW / MINI | [BMW CarData](https://www.bmwgroup.com/en/general/regulations/cardata.html): firmy z EOG rejestrują się bezpłatnie, dane za zgodą klienta | [Cennik](https://bmw-cardata.bmwgroup.com/thirdparty/public/car-data/pricing) od grudnia 2025: 0,29 EUR za klucz danych, 4 EUR/mies. za strumień na pojazd | Dostęp i cennik: oficjalne. Konkretne pola serwisowe: **niepotwierdzone** |
| Mercedes-Benz | [Remote Maintenance Support API](https://developer.mercedes-benz.com/products/remote_maintenance_support/docs): przebieg i kod serwisu, który jest należny; do tego produkty XENTRY (czasy napraw, literatura warsztatowa) | Akredytowane aplikacje, zgoda klienta; ceny nieznalezione | Oficjalna strona w wynikach |
| Volvo | Connected Vehicle API: dystans i czas do serwisu, ostrzeżenie serwisowe | Portal deweloperski; warunki publikacji aplikacji nieznalezione | **Źródło wtórne** ([integracja Home Assistant](https://www.home-assistant.io/integrations/volvo/)) |
| Stellantis (Peugeot, Citroën, DS, Opel) | [API B2C](https://developer.groupe-psa.io/webapi/b2c/overview/about/) z polami `daysBeforeMaintenance`, `mileageBeforeMaintenance` ([changelog](https://developer.groupe-psa.io/webapi/b2b/api-reference/changelog/)) | Tylko na wniosek, rejestracja aplikacji przez Mobilisights | Oficjalna dokumentacja w wynikach |
| Grupa VW (VW, Audi, Škoda, SEAT, Cupra) | Portal EU Data Act dla właścicieli; strony trzecie przez VW GIS Data Hub | Warunki nieznalezione; społeczność zgłasza problemy z portalem (niezweryfikowane) | Pola serwisowe: tylko deklaracja pośrednika ([High Mobility](https://www.high-mobility.com/blog/high-mobility-integrates-volkswagen-group-vehicle-data)) |
| Hyundai / Kia / Genesis | [Strona Data Act](https://connected-mobility.hyundai.com/data-rights-en): Vehicle Data API za zgodą właściciela, wymaga wniosku | Wg źródeł społecznościowych przez platformę Pleos, tylko odczyt | Pola serwisowe **niepotwierdzone** |
| Tesla | [Fleet API](https://developer.tesla.com/docs/fleet-api/getting-started/what-is-fleet-api); zakres uprawnień wymienia historię i terminy serwisu | Rozliczenie za użycie | Konkretne pola **niepotwierdzone** |
| Toyota, Renault, Ford | Nie znaleziono publicznego portalu z danymi serwisowymi dla Europy | — | Brak wyniku ≠ dowód braku |

Pośrednicy łączący wielu producentów:

- [High Mobility](https://www.high-mobility.com/pricing): ok. 30 marek, opłata miesięczna za aktywny pojazd, minimum 99 EUR/mies.; w katalogu pozycja „Maintenance — next services”. Które marki ją dostarczają — niepotwierdzone.
- [Smartcar](https://smartcar.com/docs/api-reference/get-service-records): historia serwisowa tylko dla marek w USA; w Europie brak.

Tło prawne: [EU Data Act](https://eur-lex.europa.eu/legal-content/EN/TXT/HTML/?uri=OJ%3AC_202505026) stosuje się od 12 września 2025 — użytkownik może przekazać dane pojazdu wybranej stronie trzeciej; producent sam wybiera sposób udostępnienia; obowiązki projektowe od września 2026.

### 3. Cyfrowe książki serwisowe producentów

Art. 61 ust. 10 rozporządzenia 2018/858 daje niezależnym warsztatom bezpłatny dostęp do centralnej historii obsługi. Nie znaleziono API pozwalającego aplikacji konsumenckiej pobrać tę historię.

## Co z tego wynika dla projektu

1. **Lista aut:** wykonalna z danych otwartych, bez kosztów licencyjnych i bez backendu. Do zrobienia jako osobny, mały krok.
2. **Harmonogramy:** producenci nie dają ich przez API. Droga z Etapu 0 (TecRMI / Autodata / HaynesPro) pozostaje jedyną realną; bramka danych bez zmian.
3. **Dane z połączonego auta:** realna przyszła funkcja („pobierz termin serwisu z auta”), ale wymaga backendu, logowania kontem producenta i umów — to sprzeczne z MVP bez kont. Obejmuje tylko nowsze auta z aktywną usługą online. **Poza MVP**; w MVP odczyt wskaźnika wpisuje użytkownik.

## Do zweryfikowania przed decyzją

- Otworzyć strony i dokumentację bezpośrednio (wymaga odblokowania hostów w środowisku albo sprawdzenia ręcznego).
- Zapytać vegvesen o termin powrotu zbioru danych i warunki użycia API w aplikacji komercyjnej.
- Sprawdzić na próbce RDW, czy lista zawiera modele popularne w Norwegii i jak brudne są nazwy.
