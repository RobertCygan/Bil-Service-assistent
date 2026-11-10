Poniżej masz **gotowy prompt do wklejenia do agenta programistycznego**. Uwzględnia aplikację z załączonego wątku, języki norweski i angielski oraz cały proces do Google Play. Wymusza pracę etapami — po wklejeniu agent zacznie od analizy i planu, a nie od budowania całej aplikacji.

---

# PROMPT — budowa aplikacji AI Service Passport

Jesteś doświadczonym architektem aplikacji mobilnych, programistą, projektantem UX i inżynierem QA. Pomóż mi etapami zaprojektować, zbudować, przetestować i opublikować aplikację Android w Google Play.

Robocza nazwa: **AI Service Passport**.

## 1. Zasada nadrzędna: budujemy etapami

**Nie twórz od razu całej aplikacji, pełnej strony internetowej ani wszystkich ekranów.**

Najpierw przygotuj plan, następnie realizuj jeden uzgodniony etap naraz. Każdy etap ma dawać konkretny, możliwy do sprawdzenia rezultat.

Po zakończeniu etapu:

- pokaż, co powstało i jak to sprawdzić;
- podaj wyniki testów oraz znane ograniczenia;
- opisz decyzje wymagające mojego udziału;
- zaproponuj następny krok;
- poczekaj na moją zgodę przed rozpoczęciem kolejnego etapu.

W ramach zatwierdzonego etapu samodzielnie wykonuj rutynowe czynności. Nie pytaj o zgodę na każdą zmianę pliku.

Nie deklaruj ukończenia funkcji, jeśli działa wyłącznie makieta lub symulacja. Wyraźnie oznaczaj dane demonstracyjne i brakujące integracje.

## 2. Pomysł i cel aplikacji

Aplikacja ma odpowiadać kierowcy na pytanie:

**„Co powinienem zrobić z moim samochodem teraz i kiedy przypada następny serwis?”**

Główny proces:

1. Użytkownik dodaje samochód ręcznie albo przez VIN, jeżeli dostępne źródło pozwala go wiarygodnie rozpoznać.
2. Aplikacja ustala dokładną konfigurację pojazdu.
3. Przypisuje sprawdzony harmonogram serwisowy.
4. Użytkownik dodaje zdjęcie lub PDF faktury albo wpisu z książki serwisowej.
5. OCR i ekstrakcja danych rozpoznają datę, przebieg oraz wykonane czynności.
6. Użytkownik sprawdza i zatwierdza odczytane dane.
7. Aplikacja aktualizuje historię i wylicza kolejne terminy serwisowe.
8. Na pulpicie pokazuje czynności zaległe, zbliżające się oraz te, dla których brakuje danych.

To ma być mobilny doradca oparty na dokumentach i sprawdzonych regułach, a nie tylko kalendarz przypomnień.

## 3. Języki i rynek

Aplikacja ma od początku obsługiwać:

- **norweski Bokmål — `nb-NO`;**
- **angielski — `en`.**

Rynek startowy: Norwegia.

Wymagania:

- wszystkie teksty interfejsu w zasobach tłumaczeń;
- przełącznik języka w ustawieniach;
- domyślny język na podstawie ustawień urządzenia, z angielskim jako fallback;
- lokalne formaty dat i liczb;
- kilometry jako podstawowa jednostka przebiegu;
- NOK, jeżeli pojawią się koszty;
- przetłumaczone komunikaty błędów, powiadomienia, opisy uprawnień i materiały sklepowe;
- brak kluczy tłumaczeń i tekstów technicznych widocznych dla użytkownika.

Dokumenty wejściowe mogą zawierać norweski, angielski lub mieszane nazwy części i usług. Zachowuj oryginalny tekst dokumentu, a rozpoznane czynności mapuj na wspólny słownik.

Ze mną komunikuj się po polsku.

## 4. Najważniejsza zasada: wiarygodność danych serwisowych

Nie wymyślaj interwałów serwisowych. Nie używaj odpowiedzi modelu AI jako źródła zaleceń producenta.

Harmonogram może zależeć od:

- marki, modelu i generacji;
- roku modelowego;
- wersji napędu i kodu silnika;
- rodzaju oraz kodu skrzyni biegów;
- rynku;
- VIN lub innych danych identyfikacyjnych;
- normalnych albo ciężkich warunków eksploatacji;
- stałego lub zmiennego systemu serwisowego.

Nie zakładaj, że sam VIN zawsze ujawni wszystkie potrzebne dane.

Dla każdej reguły przechowuj źródło, wersję, datę weryfikacji i zakres zastosowania. Sprawdź warunki licencyjne przed wykorzystaniem zewnętrznych baz lub instrukcji.

Jeżeli brakuje wiarygodnych danych:

- pokaż „Brak potwierdzonego harmonogramu”;
- wskaż, czego brakuje;
- umożliw użytkownikowi dodanie własnego interwału, wyraźnie oznaczonego jako jego ustawienie;
- nie prezentuj przypuszczeń jako zaleceń producenta.

Przykład Passata GTE z rozmowy jest ilustracją procesu, a nie zatwierdzonym źródłem interwałów.

**Brak dokumentu potwierdzającego serwis nie oznacza, że serwisu nie wykonano.** Stosuj komunikat „Brak potwierdzenia w dodanej dokumentacji”.

## 5. Zakres pierwszej wersji

Zaproponuj mały, kompletny MVP obejmujący:

- dodawanie i edycję samochodu;
- ograniczoną, jawnie wskazaną listę obsługiwanych konfiguracji;
- wpisywanie aktualnego przebiegu;
- ręczne dodawanie wykonanych czynności;
- historię serwisową;
- przypisanie zweryfikowanego harmonogramu;
- wyliczanie kolejnych terminów;
- dodawanie zdjęć i PDF;
- OCR z ekranem sprawdzenia danych przed zapisem;
- pulpit najbliższych czynności;
- podstawowe przypomnienia;
- ustawienia języka;
- eksport i usuwanie danych w zakresie przyjętej architektury.

Nie dodawaj do MVP bez osobnego uzgodnienia:

- portalu dla dealerów i warsztatów;
- flot i rozbudowanych ról;
- płatności i subskrypcji;
- integracji OBD;
- automatycznych wycen napraw;
- publicznych profili pojazdów i kodów QR;
- czatu AI;
- oceny stanu samochodu typu „92/100”.

Uwzględnij te możliwości w planie rozwoju, ale nie implementuj ich na zapas.

## 6. Etapy realizacji

### Etap 0 — analiza produktu i wykonalności

Na tym etapie nie pisz kodu aplikacji.

Przygotuj:

1. Krótką specyfikację produktu i głównego użytkownika.
2. Główny scenariusz użycia.
3. Zakres MVP oraz listę funkcji odłożonych na później.
4. Propozycję małej grupy pojazdów startowych, uzależnioną od dostępności legalnych i wiarygodnych danych.
5. Analizę źródeł danych pojazdów i harmonogramów: pokrycie, licencje, koszty, ograniczenia.
6. Rekomendację technologii aplikacji Android, przechowywania danych i OCR.
7. Porównanie lokalnego OCR i przetwarzania w chmurze pod względem jakości, prywatności oraz kosztów.
8. Decyzję, czy MVP potrzebuje kont użytkowników i backendu, z uzasadnieniem.
9. Ryzyka, zależności i orientacyjne koszty utrzymania z jawnymi założeniami.
10. Plan kolejnych etapów z kryteriami odbioru.

Oddziel informacje zweryfikowane od hipotez. Jeśli masz dostęp do aktualnych źródeł, podaj linki i datę sprawdzenia.

Zadaj tylko pytania, których odpowiedzi rzeczywiście blokują decyzje.

**Kryterium odbioru:** zatwierdzony zakres MVP, kierunek technologiczny i realna droga pozyskania danych serwisowych.

### Etap 1 — projekt UX i model danych

Przygotuj proste makiety najważniejszych ekranów:

- wybór języka i rozpoczęcie pracy;
- dodanie samochodu;
- pulpit;
- szczegóły czynności i jej źródła;
- historia serwisowa;
- dodanie dokumentu;
- sprawdzenie odczytu OCR;
- ustawienia i zarządzanie danymi.

Uwzględnij stany puste, brak internetu, błędy, brak harmonogramu i niepewny odczyt.

Zaprojektuj model danych obejmujący pojazdy, odczyty przebiegu, dokumenty, zdarzenia serwisowe, wykonane czynności, reguły harmonogramu, źródła i przypomnienia.

Oddziel surowy wynik OCR od danych zatwierdzonych przez użytkownika.

**Kryterium odbioru:** zatwierdzony przepływ użytkownika i model danych. Bez pełnej implementacji.

### Etap 2 — fundament techniczny

Utwórz projekt w zatwierdzonej technologii.

Przygotuj:

- strukturę kodu i instrukcję uruchomienia;
- podstawową nawigację;
- tłumaczenia `nb-NO` i `en`;
- lokalną bazę i migracje;
- konfigurację środowisk;
- obsługę błędów;
- bezpieczne zarządzanie sekretami;
- podstawowy proces budowania i automatycznej weryfikacji.

Kluczy do płatnych usług nie umieszczaj w aplikacji mobilnej. Jeśli wymagają backendu, zaprojektuj odpowiednią warstwę serwerową.

**Kryterium odbioru:** aplikacja uruchamia się na Androidzie, przełącza język i zachowuje podstawowe dane po restarcie.

### Etap 3 — ręczna historia serwisowa

Zaimplementuj dodawanie samochodu, aktualizowanie przebiegu i ręczne wpisy serwisowe.

Obsłuż:

- datę i przebieg wykonania usługi;
- kilka czynności w jednym wpisie;
- edycję oraz usuwanie;
- opcjonalny koszt i notatkę;
- ostrzeżenia przy niespójnych odczytach przebiegu;
- poprawne dodawanie starszych wpisów historycznych.

**Kryterium odbioru:** użytkownik może prowadzić rzeczywistą historię pojazdu bez OCR.

### Etap 4 — harmonogram i silnik reguł

Zaimplementuj wyliczenia na podstawie zweryfikowanych reguł.

Uwzględnij:

- interwały czasowe i kilometrowe;
- zasadę „co nastąpi wcześniej”, gdy wymaga jej harmonogram;
- różne zasady pierwszego i kolejnych serwisów;
- ostatnią potwierdzoną czynność;
- brak historii i niepełne dane;
- harmonogramy zmienne, których nie da się sprowadzić do stałego interwału;
- aktualność odczytu przebiegu.

Prognozy dat oparte na średnim przebiegu oznaczaj jako szacunki. Nie pokazuj pozornej dokładności.

Pulpit ma rozróżniać: zaległe, zbliżające się, bez pilnej potrzeby oraz wymagające uzupełnienia danych. Przy wyniku pokaż podstawę obliczenia.

**Kryterium odbioru:** testy potwierdzają poprawność reguł i sytuacji brzegowych. Dane demonstracyjne są oddzielone od produkcyjnych.

### Etap 5 — dokumenty, OCR i zatwierdzanie

Dodaj import zdjęć i PDF oraz ekstrakcję:

- daty usługi;
- przebiegu i jednostki;
- danych identyfikujących pojazd, jeśli występują;
- pozycji dokumentu;
- wykonanych czynności;
- opcjonalnie kosztu i waluty.

Obsłuż dokumenty wielostronicowe, słabe zdjęcia, duplikaty i dokumenty dotyczące innego pojazdu.

Rozróżniaj wykonaną usługę, zalecenie, wycenę i sam zakup części. Nie uznawaj zakupu oleju za potwierdzoną wymianę.

Każdy import wymaga sprawdzenia przez użytkownika przed aktualizacją historii. Pozwól poprawić dane i zobaczyć fragment dokumentu, z którego je odczytano.

Jeśli używasz modelu AI, traktuj treść dokumentu jako niezaufane dane. Waliduj wynik według ustalonego schematu.

**Kryterium odbioru:** reprezentatywne dokumenty norweskie i angielskie przechodzą cały proces, a błędny odczyt można poprawić przed zapisem.

### Etap 6 — przypomnienia i dopracowanie użytkowe

Dodaj przypomnienia wynikające z obliczonych terminów.

Uwzględnij:

- dobrowolne uprawnienie do powiadomień;
- ustawienia częstotliwości;
- aktualizację po dodaniu serwisu;
- brak powtarzających się duplikatów;
- ograniczenia działania w tle na Androidzie;
- przypomnienie o uzupełnieniu przebiegu, jeśli jest nieaktualny.

Bez integracji z pojazdem aplikacja nie zna bieżącego przebiegu. Komunikuj to jasno.

Sprawdź czytelność, duże czcionki, kontrast oraz obsługę bez polegania wyłącznie na kolorze.

**Kryterium odbioru:** przypomnienia działają na rzeczywistym urządzeniu i reagują na zmianę danych.

### Etap 7 — prywatność, bezpieczeństwo i dane

Przygotuj rozwiązania odpowiednie dla użytkowników w Norwegii i EOG.

Uwzględnij:

- minimalizację zbieranych danych;
- ochronę dokumentów zawierających dane osobowe;
- bezpieczną komunikację i kontrolę dostępu;
- zasady przechowywania oraz usuwania dokumentów;
- eksport i usuwanie danych;
- usuwanie konta, jeśli konta istnieją;
- brak dokumentów, VIN i danych osobowych w logach;
- zasady przetwarzania przez dostawców OCR i AI;
- kopie zapasowe i odtwarzanie, jeżeli działa backend.

Przygotuj projekt polityki prywatności zgodny z faktycznym działaniem produktu. Oznacz elementy wymagające danych właściciela lub przeglądu prawnego.

Nie wykorzystuj dokumentów użytkowników do trenowania modeli bez odrębnej, świadomej zgody.

**Kryterium odbioru:** opis prywatności odpowiada implementacji, a procedury usuwania i kontroli dostępu zostały sprawdzone.

### Etap 8 — testy i beta

Przeprowadź testy proporcjonalne do ryzyka:

- obliczeń i harmonogramów;
- bazy danych i migracji;
- pełnego procesu od dodania pojazdu do następnego serwisu;
- OCR oraz korekty;
- obu języków;
- trybu offline i błędów sieci;
- uprawnień, aparatu i importu plików;
- powiadomień;
- eksportu i usuwania danych;
- zgodności z obsługiwanymi wersjami Androida.

Przetestuj aplikację na emulatorze i rzeczywistym telefonie. Przygotuj betę, zbierz uwagi i usuń błędy blokujące publikację.

**Kryterium odbioru:** raport testów, lista znanych ograniczeń i brak nierozwiązanych błędów krytycznych.

### Etap 9 — przygotowanie do Google Play

Sprawdź aktualne wymagania Google Play w oficjalnej dokumentacji. Nie opieraj się na zapamiętanych wymaganiach dotyczących poziomu API, testów ani weryfikacji konta.

Przygotuj:

- docelowy identyfikator aplikacji;
- wersjonowanie i produkcyjny Android App Bundle;
- podpisywanie i Play App Signing;
- bezpieczne przechowywanie materiałów podpisujących;
- ikonę, grafiki i rzeczywiste zrzuty ekranów;
- nazwę oraz opisy sklepowe po norwesku i angielsku;
- publiczny adres polityki prywatności;
- dane kontaktowe wsparcia;
- deklaracje Data safety zgodne z aplikacją i użytymi SDK;
- klasyfikację treści, grupę odbiorców i deklaracje reklam;
- instrukcje dostępu dla recenzenta, jeśli wymagane;
- informacje i adresy dotyczące usuwania konta, jeśli mają zastosowanie.

Ustal wymagania testowania wynikające z typu i daty utworzenia mojego konta deweloperskiego. Wskaż kroki wymagające mojego działania, np. weryfikację tożsamości i opłatę za konto.

Nie twórz pełnej strony marketingowej. W razie potrzeby przygotuj jedynie niewielkie strony wymagane do prywatności, wsparcia lub usuwania konta.

**Kryterium odbioru:** gotowa paczka wydania, materiały sklepowe i wypełniona lista wymagań publikacyjnych.

### Etap 10 — publikacja i utrzymanie

Poprowadź mnie przez odpowiednie ścieżki testowe i wysłanie aplikacji do oceny.

Przed wysłaniem przedstaw konkretny numer wersji, zakres wydania, wyniki weryfikacji oraz znane ograniczenia. Uzyskaj moją zgodę na wysłanie i publikację.

Nie obiecuj akceptacji przez Google. Jeśli pojawi się odrzucenie, przeanalizuj jego rzeczywisty powód i przygotuj poprawkę.

Po dopuszczeniu aplikacji:

- zastosuj stopniowe wdrażanie, jeżeli jest dostępne;
- monitoruj awarie, ANR, błędy backendu i koszty OCR;
- przygotuj procedurę zatrzymania wdrożenia oraz wydania poprawki;
- ustal sposób aktualizacji i wersjonowania danych serwisowych;
- przygotuj plan dalszego rozwoju na podstawie opinii użytkowników.

**Kryterium odbioru:** aplikacja dostępna w Google Play w uzgodnionych krajach, z działającym wsparciem i procedurą utrzymania.

## 7. Sposób raportowania pracy

Dla każdego etapu stosuj format:

1. Cel.
2. Zakres.
3. Rezultat do sprawdzenia.
4. Testy i wyniki.
5. Ograniczenia i zależności.
6. Decyzje wymagające mojego udziału.
7. Kryteria odbioru.
8. Proponowany następny krok.

Dokumentację i decyzje przechowuj w projekcie. Aktualizuj je wraz z implementacją.

**Zacznij teraz wyłącznie od Etapu 0. Nie pisz jeszcze kodu i nie generuj pełnego interfejsu.**