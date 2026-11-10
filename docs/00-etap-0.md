# Etap 0 — produkt i wykonalność

Data opracowania i weryfikacji źródeł: **10 listopada 2026**, Europe/Oslo.

Status: **zakres i kierunek techniczny zaakceptowane przez właściciela 10 listopada 2026; Etap 1 rozpoczęty na jego polecenie**. Zakres tego etapu to analiza i dokumentacja, bez kodu aplikacji i bez makiet całego produktu.

## 1. Cel

Ustalić mały, możliwy do wykonania produkt, technologię oraz sposób pozyskania danych, zanim zaczniemy implementację. Największą niewiadomą są prawa i pokrycie danych serwisowych dla konkretnych konfiguracji samochodów.

## 2. Zakres i specyfikacja produktu

Główny użytkownik: prywatny właściciel używanego samochodu w Norwegii, który ma faktury i wpisy serwisowe, ale nie wie, które czynności zbliżają się do terminu.

Produkt odpowiada: „Co powinienem zrobić teraz i z czego wynika ten termin?”. Wynik jest zależny od dokumentacji, aktualności przebiegu i poprawnej konfiguracji auta. Aplikacja nie diagnozuje stanu mechanicznego ani nie potwierdza sprawności pojazdu.

**Hipoteza wartości do sprawdzenia w becie:** po dodaniu auta i kilku dokumentów użytkownik lepiej rozumie najbliższy serwis oraz potrafi znaleźć podstawę zalecenia. Nie zakładamy jeszcze potwierdzonego popytu ani przewagi nad konkurencją; twierdzenia z wcześniejszej rozmowy o konkurentach nie są dowodem rynkowym.

### Główny scenariusz

1. Kierowca wybiera język i dodaje samochód ręcznie. VIN jest opcjonalny; odczyt numeru aparatem nie oznacza automatycznie rozpoznania wyposażenia.
2. Potwierdza dane wymagane przez konkretny harmonogram: rok modelowy, napęd, wariant silnika i skrzyni, rynek oraz system serwisowy. Nie utożsamiamy daty rejestracji z rokiem modelowym.
3. Wpisuje przebieg i datę odczytu.
4. Aplikacja przypisuje wyłącznie pasujący, zweryfikowany harmonogram. Przy niepełnym dopasowaniu wyświetla brak danych.
5. Kierowca dodaje zdjęcie lub PDF. Odczyt jest szkicem, a oryginał pozostaje dostępny do porównania.
6. Poprawia i zatwierdza datę, przebieg oraz wykonane czynności. Jedna faktura może zawierać kilka usług.
7. Pulpit pokazuje najbliższe terminy, ich podstawę i brakujące informacje. Zatwierdzony wpis aktualizuje obliczenia i przypomnienia.

### MVP do publikacji

| Obszar | Zakres pierwszego wydania | Warunek odbioru funkcji |
| --- | --- | --- |
| Pojazdy | Ręczne dodawanie i edycja; główny przepływ dla jednego auta, model danych dopuszcza kolejne | Niepełna konfiguracja nie dostaje arbitralnego planu |
| Przebieg | Ręczne odczyty z datą; ostrzeżenia o niespójności i nieaktualności | Starszy wpis nie zastępuje automatycznie aktualnego odczytu |
| Historia | Wpisy ręczne, kilka czynności, edycja, usuwanie; opcjonalny koszt NOK i notatka | Zmiany przeliczają odpowiednie terminy |
| Harmonogram | Jedna dokładnie zweryfikowana konfiguracja na start; maksymalnie trzy po audycie danych | Źródło, wersja, licencja i zakres zgodności są udokumentowane |
| Obliczenia | Czas i km, pierwsze/kolejne czynności, brak historii, zasady stałe i ograniczenia zmiennych | Wynik ma ślad obliczenia i nie ukrywa niepewności |
| Dokumenty | Zdjęcia i wielostronicowe PDF, lokalny OCR, korekta przed zapisem | Odrzucenie szkicu nie zmienia historii |
| Pulpit | Zaległe, zbliżające się, bez pilnego terminu, wymagające danych | Brak historii nie staje się automatycznie „zaległe” |
| Przypomnienia | Lokalne, dobrowolne, bez gwarancji dokładnej godziny | Zmiana/usunięcie wpisu aktualizuje zadania |
| Dane użytkownika | Lokalny eksport/odtworzenie własnej historii i dokumentów; usuwanie | Sprawdzony eksport, odtworzenie i usunięcie plików pochodnych |
| Języki | Bokmål i angielski, ustawienie w aplikacji i lokalne formaty | Pełny główny przepływ w obu językach |

To zakres **docelowego MVP**, nie zakres jednego zadania programistycznego. Ręczna historia z Etapu 3 będzie wcześniejszym, użytecznym przyrostem, ale nie oznacza ukończenia MVP.

### Poza MVP

Konta i synchronizacja między urządzeniami, płatności, subskrypcje, floty, dealerzy, panel warsztatu, QR, OBD, czat AI, oceny „service health”, wyceny napraw, pełna strona marketingowa. Automatyczny lookup VIN/tablic jest opcjonalnym rozszerzeniem po uzyskaniu źródła i praw; ręczne dodawanie pozostaje pełnoprawną ścieżką.

Późniejsze kierunki: import wielu dokumentów i raport braków, przekazanie historii nowemu właścicielowi, B2B. Wymagają osobnej specyfikacji i oceny kosztów.

## 3. Rezultat do sprawdzenia

Powstał pakiet dokumentów w repozytorium:

- ten zakres produktu;
- [rejestr źródeł, propozycja pilota i bramka dopuszczenia danych](01-dane-i-zrodla.md);
- [rekomendacja technologii i porównanie OCR](02-architektura-i-ocr.md);
- [koszty, ryzyka, harmonogram etapów i kryteria odbioru](03-koszty-i-plan.md);
- [oryginalny brief](brief/BUILD_PROMPT_PL.md).

Czytaj w tej kolejności. Przed decyzją sprawdź zwłaszcza granice MVP, brak kont oraz status źródeł danych.

## 4. Weryfikacja i wyniki

| Sprawdzenie | Wynik |
| --- | --- |
| Repozytorium przez GitHub API | W momencie rozpoczęcia puste; brak kodu i instrukcji AGENTS.md w repo |
| Zgodność analizy z załączonym promptem | Obejmuje 10 tematów Etapu 0, NO/EN i etapy 1–10; bez implementacji |
| Publiczna dokumentacja źródeł | Istnieją źródła identyfikacji pojazdów i API harmonogramów; linki w rejestrze |
| Umowa/licencja na nasze użycie | Niepozyskana; publiczna dokumentacja API jej nie zastępuje |
| Pokrycie konkretnego pojazdu | Niezweryfikowane w płatnym źródle; zero konfiguracji dopuszczonych do produkcji |
| OCR na rzeczywistych fakturach NO/EN | Jeszcze niebadany; zaplanowany test w Etapie 5 |
| Kompilacja i testy aplikacji | Nie dotyczy Etapu 0; nie ma kodu aplikacji |

## 5. Ograniczenia i zależności

Można przygotować UX i lokalną historię bez komercyjnego źródła. **Nie można uznać funkcji automatycznych zaleceń za gotową bez dopuszczonych danych.** Nie zmieniamy po cichu celu na zwykły tracker.

Passat GTE z rozmowy jest kandydatem do pilota, nie potwierdzonym samochodem właściciela ani źródłem interwałów. Nie wpisano żadnych rzeczywistych interwałów producenta.

Nie zakupiono usług, nie utworzono kont u dostawców i nie wysłano zapytań do firm. Plan pozyskania danych jest konkretny, ale umowa i ekonomika wymagają dalszej weryfikacji.

## 6. Rekomendacje do akceptacji

| ID | Propozycja | Stan |
| --- | --- | --- |
| D-01 | Android natywnie: Kotlin + Compose | Zaakceptowane |
| D-02 | MVP bez kont i synchronizacji; historia i dokumenty lokalne | Zaakceptowane |
| D-03 | Lokalny OCR; chmura tylko po wykazaniu potrzeby i osobnej decyzji | Zaakceptowane |
| D-04 | Jedna dokładna konfiguracja pilota; Passat B8 GTE 2022 jako pierwszy kandydat | Warunkowa, zależy od danych |
| D-05 | TecRMI jako pierwszy dostawca do oceny, Autodata jako alternatywa | Do uzyskania pokrycia, warunków i ceny |
| D-06 | Etap 1 ograniczony do makiet UX, stanów i modelu danych | Rozpoczęty za zgodą właściciela |

Nie ma pytania blokującego przygotowanie tego raportu. Przed zakupem źródła potrzebny będzie zaakceptowany budżet licencyjny. Przed audytem pilota potrzebne będą potwierdzone parametry pojazdu i dokumenty możliwe do legalnego użycia; nie umieszczamy ich w publicznym repo.

## 7. Kryteria odbioru Etapu 0

- [x] Zakres, scenariusz i granice MVP zapisane.
- [x] Zbadane publiczne źródła i ograniczenia; jest plan pozyskania danych.
- [x] Technologia, OCR i wariant bez kont uzasadnione.
- [x] Koszty mają jawne założenia; niewiadome nie są przedstawione jako cena zero.
- [x] Plan do Google Play ma wyniki i kryteria dla każdego etapu.
- [x] Właściciel zatwierdził MVP i kierunek techniczny w rozmowie („TAk Zaczynaj”).
- [x] Warunkowa ścieżka danych uwzględniona w zaakceptowanym kierunku. Umowa, pokrycie i cena nadal są otwartą bramką przed Etapem 4; nie zatwierdzono zakupu usług.

Etap 0 został odebrany w zakresie produktu i kierunku technicznego. Nie potwierdzamy jeszcze pełnej wykonalności komercyjnej; status licencji i pokrycia danych nie zmienił się.

## 8. Następny krok

Na polecenie właściciela rozpoczęto [Etap 1 — proste makiety i model danych](stage-1/README.md), uwzględniający zarówno zweryfikowany plan, jak i brak planu. Następnie osobny odbiór przed pisaniem fundamentu aplikacji. Równolegle do dalszych etapów należy zamknąć warunki źródła harmonogramów; nie zakładamy ich powodzenia z góry.
