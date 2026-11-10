# Koszty, ryzyka i droga do Google Play

Stan: **10 listopada 2026**. Poniższe widełki to założenia planistyczne, bez zakupu usług i bez zobowiązań finansowych. Nie są wyceną wykonawcy ani gwarancją daty publikacji.

## Model kosztów utrzymania

Założenie porównawcze: 1000 aktywnych użytkowników miesięcznie, 5 dokumentów po 2 strony na użytkownika = **10 000 stron/miesiąc**. To scenariusz obliczeniowy, nie prognoza sprzedaży. Przy średnio 2 MB/stronę trzymanie tych nowych skanów w chmurze dodawałoby około 20 GB miesięcznie przed retencją i kopiami. W proponowanym wariancie dokumenty są lokalne, więc nie tworzą takiego kosztu serwera.

| Pozycja | Wariant lokalny | Jeśli wymagane usługi online |
| --- | --- | --- |
| OCR | 0 opłat za stronę za ML Kit według aktualnej oferty | Przykład samego OCR: ok. **15 USD / 10 000 stron**, licząc konserwatywnie 1,50 USD / 1000, bez ulg i rabatów |
| Ekstrakcja semantyczna | Lokalne reguły/słownik; koszt rozwoju i utrzymania | Osobny koszt LLM/parsera, nieobjęty powyższą ceną OCR |
| Serwer aplikacji | Brak, jeśli licencja pozwala dostarczyć katalog offline | Roboczy bufor **200–1000 NOK/mies.** na mały relay bez archiwum faktur; nie jest ofertą dostawcy i wymaga wyceny konkretnego hostingu |
| Strony prywatności i wsparcia | Planistycznie **0–200 NOK/mies.** za małe statyczne strony; domena osobno | Ta sama funkcja; nie budujemy pełnej strony produktu |
| Dane serwisowe | Nieznana licencja na dystrybucję katalogu | Nieznane minimum i opłaty za API/użytkownika; może to być największy koszt |
| Kopie, monitoring, CI | Zależne od wybranych usług i limitów; do ponownej wyceny w Etapie 2 | Dodatkowo zasoby serwera, transfer i retencja; nie zakładamy bezterminowo darmowych limitów |

Cena referencyjna OCR: [Google Cloud Document AI pricing](https://cloud.google.com/products/document-ai/pricing), sprawdzono 10 listopada 2026. Przykład dotyczy Enterprise Document OCR bez dodatków; nie jest ofertą kompletnego odczytu faktur serwisowych. Rozliczenie stron, procesora i regionu trzeba sprawdzić przed uruchomieniem. Zostawiamy cenę w USD, aby nie wymyślać kursu NOK. Kwoty bez podatków, pracy programistycznej, obsługi klienta i przeglądu danych.

Koszt miesięczny = hosting + utrzymanie + licencja bazowa + liczba płatnych zapytań × stawka + ewentualne przetwarzanie dokumentów. **Bez wyceny licencji nie ma wiarygodnej ceny całkowitej.** Nie możemy obecnie twierdzić, że utrzymanie całego produktu zmieści się w kilkuset NOK.

Jednorazowo lub okresowo: konto Google Play, ewentualny dostęp do dokumentacji producenta, przegląd techniczny harmonogramów, przegląd prywatności/licencji, urządzenie testowe, domena, grafiki i korekta językowa NO/EN. Aktualne kwoty weryfikujemy przed wydatkiem. Sposób monetyzacji pozostaje poza MVP; trzeba do niego wrócić po poznaniu kosztu danych, zanim zaczniemy skalować produkt.

## Główne ryzyka

| Ryzyko | Znaczenie | Ograniczenie i moment decyzji |
| --- | --- | --- |
| Brak licencji B2C lub zbyt drogie minimum | Blokuje produkt z automatycznymi planami | Oferta i prawa przed Etapem 4; brak zgody oznacza zatrzymanie tej integracji i decyzję właściciela |
| Zły wariant auta albo rynku | Nieprawidłowe zalecenia | Jawne dopasowanie wymaganych pól i audyt źródła; nigdy plan „najbardziej podobny” |
| Reguła zależna od czujnika/systemu zmiennego | Fałszywa dokładność | Nie zastępować stałym interwałem; pokazać ograniczenie i potrzebę odczytu danych auta |
| OCR myli datę, przebieg lub zalecenie z usługą | Błędne terminy | Fragment źródłowy, korekta, zatwierdzenie i testy negatywne |
| Stary przebieg albo brak dokumentów | Pozorne zaległości lub zaniżenie terminu | Data odczytu, stany „brak potwierdzenia”, prognozy wyraźnie oznaczone |
| Utrata telefonu bez kopii | Utrata lokalnej historii | Eksport/odtworzenie sprawdzone przed betą; brak obietnicy odzyskania z konta |
| Aktualizacja bazy lub źródła | Utrata danych albo stary plan | Migracje, wersjonowanie, ślad obliczenia, zasady aktualności i unieważnienia |
| Ujawnienie faktur/VIN | Szkoda dla prywatności | Prywatne pliki, brak treści w logach/GitHub, audyt SDK i eksportów od początku |
| Przekroczenie kosztów API | Nieprzewidywalne utrzymanie | Limity i kontrola nadużyć w backendzie; sam alert kosztowy nie jest twardym limitem |
| Wymagania Play i dostępność testerów | Opóźnienie wydania | Sprawdzenie typu konta przed betą, rekrutacja testerów, aktualna lista wymagań w Etapie 9 |

## Plan realizacji i odbiorów

Każdy wiersz to osobny etap. Po wykonaniu przedstawiamy rezultat, testy, ograniczenia i propozycję kolejnego kroku; następny etap dopiero po zgodzie właściciela, zgodnie z briefem. Przegląd bezpieczeństwa zaczyna się przy fundamencie, nie dopiero przy Etapie 7.

| Etap | Zakres i rezultat | Sprawdzenie / kryterium odbioru |
| --- | --- | --- |
| **0: analiza** | Niniejszy pakiet, zakres MVP, rekomendacje i warunkowa ścieżka danych | Akceptacja zakresu i techniki; ryzyka danych jawne, bez kodu |
| **1: UX i dane** | Proste makiety głównego procesu, stany błędów/braku danych; model i relacje danych | Da się przejść scenariusz ręczny i OCR w obu językach; szkic OCR oddzielony od historii; bez implementacji pełnych ekranów |
| **2: fundament** | Projekt Android, nawigacja, języki, trwałe dane, migracje, podstawowe CI i instrukcja budowania | Build i uruchomienie na emulatorze, przełączanie języka, zapis po restarcie; wersje SDK sprawdzone na ten etap |
| **3: historia** | Auto, przebiegi, ręczne wpisy, kilka usług, edycja/usuwanie, koszt/notatka | Historyczny wpis nie cofa aktualnego przebiegu; spójność i trwałość danych; użyteczna lokalna historia |
| **4: harmonogram** | Integracja wyłącznie dopuszczonego źródła, silnik reguł i pulpit z podstawą wyniku | Zamknięta bramka danych; testy czasu/km, pierwszych/kolejnych usług, braków, konfliktów i zmiennych zasad; brak fikcyjnych reguł w produkcji |
| **5: dokumenty/OCR** | Zdjęcia/PDF, ekstrakcja NO/EN, podgląd/korekta, duplikaty, atomowe zatwierdzanie | Raport z prywatnego zestawu testowego; wycena/zakup części nie stają się automatycznie usługą; anulowanie nie zapisuje historii |
| **6: przypomnienia/UX** | Powiadomienia, ustawienia, aktualizacja po zmianach, dostępność | Test na rzeczywistym telefonie, restart i odmowa uprawnienia, brak duplikatów, duże fonty i komunikaty bez polegania na kolorze |
| **7: prywatność/dane** | Audyt przepływów, SDK, kopii, logów; eksport/odtworzenie/usunięcie; polityka NO/EN | Usunięcie pochodnych i zadań, odtworzenie eksportu; dokument zgodny z rzeczywistą implementacją; ewentualne umowy chmurowe |
| **8: beta** | Testy pełnego procesu, migracje, offline, awarie sieci, dwa języki, emulator i telefony | Raport, opinie testerów i brak otwartych krytycznych błędów; wewnętrzna/zamknięta beta odpowiednia dla konta |
| **9: gotowość Play** | AAB, podpisy, store listing NO/EN, grafiki, polityka, Data safety, klasyfikacje i kontakt | Aktualne wymagania sprawdzone; deklaracje zgodne z SDK i funkcjami; komplet materiałów, dane wydawcy i dostęp recenzenta |
| **10: publikacja** | Zgoda właściciela dla konkretnego wydania, wysłanie do oceny, obsługa uwag, wdrożenie | Dostępność w uzgodnionym kraju i monitoring; potwierdzony plan wsparcia, aktualizacji danych i poprawki |

Nie ustalamy daty produkcji przed poznaniem dostępności źródła i wyników bety. Krytyczna ścieżka: dopuszczenie danych → obliczenia → OCR i testy → beta → wymagania konta → ocena Google. Akceptacja przez Google nie jest gwarantowana.

## Google Play: co wiadomo teraz, co później

Publiczna instrukcja Google sprawdzona 10 listopada 2026 opisuje dla kont osobistych utworzonych po 13 listopada 2023 zamknięty test z minimum 12 testerami zapisanymi nieprzerwanie przez co najmniej 14 dni przed wnioskiem o dostęp do produkcji. Nie znamy typu ani daty utworzenia konta właściciela, więc nie przypisujemy tego wymagania automatycznie do projektu. Źródło: [wymagania testów kont osobistych](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en). Sprawdzimy je ponownie przed betą i publikacją; samo upłynięcie czasu nie zastępuje rzeczywistego testowania i decyzji Google.

W Etapie 9 sprawdzamy ponownie wymagany target API, zgodność bibliotek natywnych/urządzeń, wymagania weryfikacji dewelopera, uprawnień, danych i paczki aplikacji. Nie wybieramy docelowych wartości na podstawie pamięci. Nie prosimy obecnie o hasła, konto Google ani klucze podpisujące.

Przed publikacją właściciel podaje tożsamość wydawcy, kontakt i wykonuje wymagane formalności konta. Agent przygotowuje konkretną wersję, dowody testów i materiały; wysłanie/publikacja następuje dopiero po zgodzie dla tego wydania.

Utrzymanie obejmuje przegląd aktualności harmonogramów, błędy aplikacji/ANR, migracje, koszty usług i zgłoszenia użytkowników. Zatrzymanie wdrożenia i poprawka są podstawową ścieżką awaryjną; nie zakładamy, że można dowolnie obniżyć wersję aplikacji na telefonach.

## Stan na koniec Etapu 0

Aktualizacja po odbiorze: właściciel zaakceptował zakres i kierunek D-01–D-03 oraz polecił rozpocząć [Etap 1](stage-1/README.md). Nie uruchomiono usług i nie rozpoczęto implementacji Android. Wybór i budżet źródła harmonogramów pozostają osobną, jawną decyzją przed Etapem 4.
