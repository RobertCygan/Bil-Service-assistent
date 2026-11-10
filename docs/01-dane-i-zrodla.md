# Dane serwisowe, źródła i pilot

Stan weryfikacji publicznej dokumentacji: **10 listopada 2026**. Nie wykonano uwierzytelnionych zapytań do dostawców. „Zweryfikowane” poniżej oznacza treść publicznej strony, nie działającą integrację ani umowę.

## Dwa osobne problemy

Identyfikacja pojazdu ustala, jakie auto opisujemy. Harmonogram ustala, jakie reguły dotyczą dokładnie tej konfiguracji. Źródło VIN/tablic nie zastępuje źródła zaleceń. Nie zakładamy automatycznego dopasowania kodu skrzyni ani rynku po samej nazwie handlowej.

## Rejestr źródeł

| ID i źródło pierwotne | Potwierdzone publicznie | Niepotwierdzone / ograniczenie | Koszt i prawa dla tego projektu |
| --- | --- | --- | --- |
| S-01 [Statens vegvesen — techniczne dane pojazdu](https://www.vegvesen.no/fag/teknologi/apne-data/et-utvalg-apne-data/api-for-tekniske-kjoretoyopplysninger/) | Lookup po VIN lub numerze rejestracyjnym, dostęp z kluczem, bez danych właściciela; opis usługi wyklucza przebieg | Nie jest bazą harmonogramów. Brak potwierdzenia kompletu kodów napędu; klucz wymaga norweskiej e-ID | Cena, limity, caching i użycie w aplikacji do sprawdzenia w warunkach dostępu; nie przyjmujemy automatycznie dowolnej redystrybucji |
| S-02 [TecRMI — REST, sekcja Maintenance](https://tecrmi-services.tecalliance.net/docs/ServiceRest.html) | Dokumentacja ma sprawdzanie dostępności planu dla pojazdu, wybór wariantu oraz pobieranie danych planu; języki i kraje mają własne listy | Konkretny Passat, rynek NO, interwały zmienne i komplet potrzebnych czynności niezweryfikowane | Wycena i umowa integracyjna do uzyskania; publiczna dokumentacja nie nadaje praw do B2C ani offline |
| S-03 [Autodata API](https://developer.autodata-group.com/) i [opis techniczny](https://developer.autodata-group.com/docs) | Dostawca wymienia service schedules w API; część pełnej dokumentacji wymaga logowania | Niepotwierdzone pokrycie pilota, pola wyników, ograniczenia NO/EN i offline | Wycena komercyjnej integracji do uzyskania. Abonament warsztatowy nie jest automatycznie licencją naszej aplikacji |
| S-04 [HaynesPro / Infopro Digital Automotive](https://www.infopro-digital-automotive.com/haynespro/) | Dostawca opisuje OEM-based maintenance data i integracje w produktach klientów | Marketingowe pokrycie nie potwierdza konkretnej konfiguracji i rynku; nie audytowano API | Trzecia opcja do wyceny, jeśli pierwsze dwie nie spełnią warunków |
| S-05 [VW erWin — opis danych](https://volkswagen.erwin-store.com/erwin/showInfoTour.do), [warunki dotychczasowego sklepu](https://volkswagen.erwin-store.com/erwin/showTermsOfBusiness.do), [nowy portal](https://erwin.vwgroup-datahub.com/) | Oficjalny opis obejmuje dane pojazdu i tabele serwisowe; strona wskazuje nowy portal także dla Norwegii | Aktualne uprawnienia i warunki nowego portalu trzeba sprawdzić przy zakupie. Nie potwierdzono praw do ponownej publikacji w naszym produkcie | Dostęp do weryfikacji referencyjnej wymaga sprawdzenia oferty; nie kopiujemy tabel do publicznego repo |

**Wniosek projektowy:** zaczynamy ocenę od S-02, z S-03 jako alternatywą. To kolejność analizy, a nie zakup ani wybór końcowy. S-01 może później uprościć dodawanie auta. S-05 służy do zgodnej z uprawnieniami weryfikacji pilota; samo posiadanie instrukcji nie rozstrzyga prawa do budowy bazy dla innych użytkowników.

Nie ustalono rzetelnej ceny licencji S-02/S-03/S-04 dla naszego scenariusza. Nie używamy cen pojedynczego stanowiska warsztatowego jako szacunku ceny redystrybucji.

## Mała grupa startowa

Pierwszy kandydat: **Volkswagen Passat B8 GTE, rok modelowy 2022, konfiguracja właściwa dla rynku norweskiego**. Wybrany z uwagi na scenariusz z rozmowy, nie na potwierdzony udział w rynku.

Przed dopuszczeniem ustalamy z legalnych źródeł dokładne kody silnika i skrzyni, datę produkcji/rok modelowy, rynek pierwotnej sprzedaży, system serwisowy i wymagane dodatkowe parametry. Nie przenosimy kodów i interwałów z poprzedniej rozmowy do produkcji.

Start od **jednej** konfiguracji. Drugą i trzecią można wybrać z tej samej rodziny dopiero po wykazaniu odrębnego pokrycia oraz zdobyciu dokumentów testowych. Nie deklarujemy obsługi „wszystkich Passatów” ani całej marki. Samochód spoza listy może mieć historię i własne interwały użytkownika, z jasnym oznaczeniem ich pochodzenia.

## Plan uzyskania danych

| Krok | Działanie i odpowiedzialność | Dowód zakończenia |
| --- | --- | --- |
| 1 | Właściciel potwierdza pojazd pilota i dostępność dokumentów; agent przygotowuje listę wymaganych parametrów | Prywatna karta konfiguracji, bez VIN w GitHub |
| 2 | Agent przygotowuje zapytanie do TecRMI i Autodata; właściciel wysyła lub wyraźnie zleca wysłanie | Odpowiedź o pokryciu, cena i warunki B2C |
| 3 | Właściciel zatwierdza budżet i umowę; dostęp przekazuje przez bezpieczne ustawienia środowiska | Uprawniony dostęp testowy/produkcyjny, bez sekretów w czacie i repo |
| 4 | Agent sprawdza próbkę planu, warunki i dopasowanie; osoba kompetentna w serwisie weryfikuje interpretację techniczną | Protokół porównania z referencją właściwą dla auta |
| 5 | Agent zapisuje pochodzenie, wersję i reguły, w zakresie dozwolonym umową | Dopuszczony pakiet i scenariusze obliczeń; materiały źródłowe prywatne, jeśli tego wymagają prawa |

Na 10 listopada: krok 1 niepotwierdzony, kroki 2–5 niewykonane. Nie wysłano wiadomości do dostawców.

### Zakres zapytania ofertowego — gotowy do wykorzystania

Potrzebujemy danych harmonogramów do konsumenckiej aplikacji Android na rynek norweski, interfejs Bokmål/English, pilot 1–3 dokładnych konfiguracji. Prosimy potwierdzić:

- pokrycie wskazanej konfiguracji i reguł specyficznych dla rynku, w tym stałe/zmienne oraz pierwsze/kolejne terminy;
- możliwość użycia wyników w aplikacji dla kierowców, także w zamkniętej becie;
- prawo przechowywania reguł, obliczania dat, tłumaczenia etykiet, powiadamiania i pokazywania źródła;
- dopuszczalność offline, czas cache, aktualizacje, unieważnienie danych i działanie po zakończeniu umowy;
- zakres eksportu wyników i ograniczenia udostępniania danych dalej;
- sposób identyfikacji pojazdu, ograniczenia VIN oraz obsługiwane kraje i języki;
- wymaganie serwera, sposób zabezpieczenia kluczy, identyfikowanie użytkowników i rate limits;
- opłatę uruchomieniową, minimum miesięczne/roczne, koszt per pojazd/użytkownik/zapytanie, limity i ceny przekroczeń;
- warunki przetwarzania danych, dostępne regiony i podwykonawców, jeżeli przekazywane są dane identyfikujące pojazd;
- dostęp testowy i format próbki pozwalającej sprawdzić dopasowanie.

## Bramka dopuszczenia harmonogramu przed Etapem 4

Wymagamy łącznie:

1. Praw do docelowego użycia, z określonymi ograniczeniami offline i eksportu.
2. Pełnego dopasowania wymaganych pól konfiguracji; brakujące pole blokuje automatyczne przypisanie.
3. Źródła/ID, wersji, daty sprawdzenia, rynku, warunków i zakresu czynności.
4. Przeglądu interpretacji reguł, uwzględniającego pierwszą i kolejne czynności oraz punkty początkowe liczenia.
5. Testów oczekiwanych wyników wyprowadzonych ze źródła, oddzielonych od fikcyjnych przykładów.
6. Zasad wygaśnięcia i aktualizacji danych; aplikacja nie może bezterminowo oznaczać starego planu jako aktualnego.

Nie zastępujemy brakujących reguł odpowiedzią LLM, forum ani uśrednionym interwałem. Jeśli bramka nie przejdzie, Etap 4 pozostaje zablokowany. Dalsza praca nad historią/UX nie oznacza zgody na wydanie niespełniające celu produktu.

## Informacje, które wynik musi zachować

Rozróżniamy: regułę z dopuszczonego źródła, własny interwał użytkownika, potwierdzony przez użytkownika wpis, niepotwierdzony szkic OCR i prognozę na podstawie sposobu użytkowania. Potwierdzenie wpisu przez kierowcę nie jest niezależnym audytem warsztatowym.

Przy braku historii: „Brak potwierdzenia w dodanej dokumentacji”. Przy braku danych konfiguracji: „Brak potwierdzonego harmonogramu”. Odczyt przebiegu zawsze ma datę. Płatne treści, dokumenty i prawdziwe identyfikatory nie trafiają do publicznego repozytorium.
