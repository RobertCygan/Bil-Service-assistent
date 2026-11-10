# Rekomendacja techniczna i OCR

Data: **10 listopada 2026**. Kierunek techniczny zaakceptowany przez właściciela po Etapie 0, bez implementacji. Szczegółowe ekrany i model danych przedstawia [Etap 1](stage-1/README.md). Warunki integracji źródła nadal otwarte.

## Technologia

| Element | Rekomendacja | Uzasadnienie |
| --- | --- | --- |
| Aplikacja | Kotlin, Jetpack Compose, Material 3 | Docelową platformą jest Android; bez dodatkowego mostu do aparatu, dokumentów i pracy w tle |
| Stan i logika | ViewModel, coroutines/Flow; odseparowany silnik reguł | Obliczenia możliwe do sprawdzenia bez ekranu i bez OCR |
| Dane | Room/SQLite, DataStore dla ustawień, prywatny katalog plików | Historia działa offline; migracje danych są częścią wdrożeń |
| Aparat i import | CameraX, systemowy wybór zdjęć/plików | Dostęp tylko do wybranych plików; uprawnienie aparatu dopiero przy użyciu |
| Dokumenty | Renderowanie stron PDF przez Android PdfRenderer, potem OCR | Wielostronicowy PDF nie jest pojedynczym obrazem; strony przetwarzamy kolejno |
| OCR | ML Kit Text Recognition v2, model Latin dołączony do aplikacji | Rozpoznawanie tekstu na urządzeniu, dostępne bez pobierania modelu przy pierwszym użyciu |
| Przypomnienia | WorkManager i lokalne powiadomienia | Serwis auta nie wymaga alarmu o dokładnej sekundzie |
| Języki | Zasoby Android i per-app language preferences | Bokmål `nb-NO`, angielski `en`, angielski fallback; brak tekstów na sztywno w UI |
| Dystrybucja | Android App Bundle, Play App Signing | Publikacja przez Google Play po testach i zgodzie właściciela |

Na start jeden projekt aplikacji z czytelnymi pakietami; bez mikroserwisów i wielomodułowej struktury na zapas. Wstępne minimum: Android 8 / API 26 jako decyzja produktowa, do potwierdzenia na urządzeniach testerów. Nie jest to wymaganie Google Play. Target/compile SDK i stabilne wersje zależności dobierzemy z aktualnej dokumentacji przy Etapie 2, a wymagania publikacji sprawdzimy ponownie w Etapie 9.

Alternatywy rozważone: Flutter przy priorytecie iOS; React Native przy istniejącym zapleczu React. Przy obecnym celu Android nie uzasadniają dodatkowej warstwy integracji. PWA/strona internetowa nie realizuje przyjętego kierunku aplikacji mobilnej.

Źródła techniczne sprawdzone 10 listopada 2026: [Compose](https://developer.android.com/compose), [Room](https://developer.android.com/training/data-storage/room), [WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent), [języki aplikacji](https://developer.android.com/guide/topics/resources/app-languages), [PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer). Dobór powyżej jest rekomendacją projektową, nie twierdzeniem, że gotowa aplikacja została zbudowana.

## Konta i backend

**Propozycja: bez kont użytkowników i bez wysyłania faktur do serwera w MVP.** Historia, dokumenty i zatwierdzanie pozostają na telefonie. Nie ma synchronizacji ani odzyskania danych przez logowanie.

Możliwe warianty źródła harmonogramu:

1. Licencja pozwala dostarczyć mały, wersjonowany katalog z aplikacją: brak backendu wykonującego lookup. Aktualizacje katalogu przez wydanie aplikacji, z widoczną datą weryfikacji i zasadami wygaśnięcia.
2. Dostawca wymaga tajnego klucza lub zapytań online: potrzebny mały backend pośredniczący. Klucz tylko po stronie serwera, ograniczenie nadużyć i kosztów, minimalne dane żądania, logi bez VIN. Prawo cache i identyfikowanie konsumenta według umowy. Brak kont kierowców nie zwalnia z ochrony endpointu.
3. Dostawca wymaga kont/entitlements lub warunków niezgodnych z MVP: wracamy do decyzji architektonicznej i zakresu; nie dokładamy ich bez uzgodnienia.

O wyborze wariantu przesądzi [bramka danych](01-dane-i-zrodla.md), przed integracją w Etapie 4. Do tego czasu nie obiecujemy kosztu backendu równego zero. Reguły i historia muszą być od siebie oddzielone, aby zmiana dostawcy nie wymagała przepisywania wpisów użytkownika.

## OCR to nie rozumienie serwisu

OCR odczytuje znaki. Osobny mechanizm musi wyłonić datę usługi, przebieg, pojazd i czynności. Data faktury nie musi być datą naprawy. „Oil”, „olje”, nazwa produktu lub zakup części nie dowodzą wymiany. Oddzielamy usługę wykonaną, zalecenie, wycenę i pozycję niejednoznaczną.

Pierwszy wariant ekstrakcji: lokalne reguły, słownik NO/EN i oznaczanie niejednoznaczności. LLM nie jest konieczny do wykonania pierwszego MVP; nazwę „AI” traktujemy roboczo, a opis sklepowy musi odpowiadać faktycznej funkcjonalności.

Przepływ dokumentu: wybór pliku → walidacja formatu/rozmiaru → podgląd → OCR stron → szkic propozycji → porównanie z oryginałem i korekta → świadome zatwierdzenie → zapis historii. Zapis powinien być atomowy i odporny na podwójne zatwierdzenie. Anulowanie, przerwanie procesu albo błąd nie tworzą wykonanej usługi.

Przy każdej propozycji zachowujemy stronę/fragment źródłowy. Nie pokazujemy zmyślonego procentu pewności, jeśli komponent go nie dostarcza i nie został skalibrowany. W pierwszym wariancie jawne oznaczenia „sprawdź” / „nie rozpoznano” wystarczą.

Proponowane ograniczenia początkowe do potwierdzenia w UX: do 20 stron i 20 MB na dokument, przetwarzanie kolejno, anulowanie i czytelny komunikat o zaszyfrowanym/uszkodzonym PDF. Brak obsługi pisma ręcznego jako gwarantowanej funkcji. Wykrywanie identycznych plików przez skrót; inne zdjęcia tej samej faktury wymagają także ostrzeżenia o możliwym powtórzeniu wpisu.

## Lokalnie czy chmura?

| Kryterium | Lokalny ML Kit + ekstrakcja regułami | OCR/ekstrakcja w chmurze |
| --- | --- | --- |
| Jakość | Do zbadania dla zdjęć faktur NO/EN; słabe zdjęcia i układ tabel mogą wymagać korekty | Potencjalnie lepsza analiza układu/semantyki, lecz wymaga tego samego testu; nie zakładamy przewagi bez pomiaru |
| Prywatność | Treść wejściowa i wynik OCR według dokumentacji ML Kit są przetwarzane na urządzeniu | Dokument lub jego tekst przekazywany dostawcy; potrzebna ocena regionu, retencji i warunków przetwarzania |
| Internet | Dołączony model pozwala czytać dokument offline | Potrzebne połączenie, retry i obsługa niedostępności |
| Koszt | Brak opłaty za wywołanie ML Kit; koszt pracy telefonu i większego pakietu | Koszt stron i ewentualnej osobnej ekstrakcji, plus serwer i transfer |
| Utrzymanie | SDK, kompatybilność i lokalny słownik | Integracja API, limity, sekrety, rozliczenia i kontrola nadużyć |

Źródła: [ML Kit Android](https://developers.google.com/ml-kit/vision/text-recognition/v2/android), [oferta ML Kit](https://developers.google.com/ml-kit/guides), [warunki i prywatność](https://developers.google.com/ml-kit/terms). Lokalny OCR **nie oznacza braku całego ruchu sieciowego SDK**: Google opisuje metryki działania/użycia i kontakty związane z utrzymaniem modeli. Należy je uwzględnić w polityce prywatności i Data safety. Nie obiecujemy „żadne dane nigdy nie opuszczają telefonu”.

**Decyzja proponowana:** lokalny OCR jako pierwszy wariant; chmura dopiero po wykazaniu istotnego problemu jakościowego, wycenie i zatwierdzeniu nowego przepływu danych. Nie uruchamiamy płatnego fallbacku automatycznie.

### Plan pomiaru w Etapie 5

Zbudować za zgodą uprawnionych osób prywatny zestaw minimum 40 dokumentów, po 20 NO i EN: dobre zdjęcia, słabe zdjęcia, PDF, dokumenty wielostronicowe, wyceny i zakup części. Część przykładów odłożyć do oceny po przygotowaniu reguł; nie mierzyć jakości wyłącznie na materiale użytym do ich strojenia.

Raportować osobno: dokładne dopasowanie daty i przebiegu, precyzję/kompletność propozycji czynności, czas przetwarzania, poprawki użytkownika i odmowy odczytu, z liczebnością próbek. Robocze cele: ≥90% poprawnych dat/przebiegów w czytelnych dokumentach i ≥95% precyzji proponowanych wykonanych czynności. Mała próba nie potwierdzi jakości dla wszystkich warsztatów.

Niezależnie od wyniku: zero automatycznie zatwierdzonych wpisów; zawsze możliwa korekta i ręczny wpis. Jeżeli cele nie zostaną osiągnięte, poprawiamy odczyt/słownik albo przedstawiamy decyzję o chmurze, bez ogłaszania sukcesu.

## Prywatność od początku

- Dane i oryginały w prywatnym obszarze aplikacji; Room samo w sobie nie dodaje szyfrowania bazy. Dobór dodatkowej ochrony po analizie zagrożeń, bez własnej kryptografii.
- Bez własnej analityki i reklam w MVP; osobno audyt faktycznych zachowań SDK.
- Jawne reguły Android Backup i transferu urządzeń: planujemy wykluczenie wrażliwych dokumentów, OCR i bazy z automatycznej kopii. Trzeba sprawdzić skuteczność na wspieranych wersjach i urządzeniach; samo `allowBackup` nie kończy weryfikacji. Źródło: [Android Auto Backup](https://developer.android.com/identity/data/autobackup).
- Eksport własnej historii i dokumentów oraz odtworzenie na nowym urządzeniu. Użytkownik wybiera miejsce; plik zawiera dane prywatne. Treści licencjonowane tylko w zakresie umowy, bez dołączania całej cudzej bazy.
- Usunięcie obejmuje oryginały, miniatury, OCR, wpisy i przypomnienia. Nie obiecujemy usunięcia kopii wcześniej wyeksportowanych poza aplikację.
- Jeśli pojawi się chmura: osobny przepływ zgody/informacji odpowiedni do podstawy przetwarzania, minimalizacja danych, retencja i umowy przed przesłaniem rzeczywistych dokumentów. Przewodnik [Datatilsynet o umowach powierzenia](https://www.datatilsynet.no/rettigheter-og-plikter/virksomhetenes-plikter/hvordan-lage-en-databehandleravtale/) potwierdza znaczenie ustalenia ról i umowy; nie jest to ukończony audyt prawny aplikacji.

Projekt polityki prywatności dopiero po ustaleniu rzeczywistych przepływów, danych wydawcy i użytych SDK. Nigdy nie deklarujemy zgodności wyłącznie na podstawie wyboru regionu serwera.
