# Etap 5 — import dokumentu w aplikacji (pierwszy i drugi przyrost)

Data: **10 listopada 2026**.

## Status

| Element | Stan |
| --- | --- |
| Wybór zdjęcia lub PDF, odczyt tekstu **na telefonie** (ML Kit z modelem w aplikacji), PDF do 20 stron | **Zaimplementowane** |
| Ekran sprawdzenia: data, przebieg, pozycje, kwota, ostrzeżenia; przy każdej wartości linia źródłowa i numer strony | **Zaimplementowane** |
| Zatwierdzanie | Wynik sprawdzenia **wypełnia zwykły formularz wpisu serwisowego**; do historii trafia dopiero po zapisaniu tam, tą samą ścieżką walidacji co wpis ręczny |
| Test prawdziwego rozpoznawania | Test na emulatorze generuje dwustronicową fakturę PDF i odczytuje ją ML Kit — **czysty wydruk, nie zdjęcie** |
| Pomiar na prawdziwych fakturach NO/EN (kryterium odbioru Etapu 5) | **Niewykonany** — brak zestawu dokumentów |
| Robienie zdjęcia z poziomu aplikacji | **Zaimplementowane** przez aplikację aparatu telefonu (bez uprawnienia do aparatu). **Niesprawdzone na telefonie**; test na emulatorze obejmuje tylko zapis pliku i dalszą ścieżkę, nie sam aparat |
| Przechowywanie dokumentu przy wpisie | **Zaimplementowane** (drugi przyrost): kopia w prywatnym katalogu aplikacji, zapisywana dopiero razem z wpisem; użytkownik może z niej zrezygnować na ekranie sprawdzenia |
| Podgląd dokumentu | Otwarcie zapisanego pliku w innej aplikacji z jednorazowym dostępem tylko do odczytu. **Niesprawdzone na telefonie.** Podświetlenia fragmentu, z którego pochodzi wartość, **nie ma** |
| Wykrywanie duplikatów | **Zaimplementowane** jako ostrzeżenie (nie blokada): ten sam plik (skrót SHA-256) albo wpis z tą samą datą i przebiegiem |
| Dokumenty w kopii zapasowej | **Zaimplementowane**: pliki trafiają do kopii (także szyfrowanej) i wracają przy przywracaniu; test porównuje bajty |
| Wznowienie po zamknięciu aplikacji w trakcie sprawdzania | **Zaimplementowane**: kopia dokumentu zostaje razem z wyborami z ekranu sprawdzenia (data, przebieg, pozycje, kwota, potwierdzenia, „zachowaj kopię”). Po wznowieniu dokument jest odczytywany ponownie, a wybory wracają tylko wtedy, gdy odczyt jest identyczny; inaczej ekran zaczyna od ostrożnych wartości domyślnych. Niesprawdzone na telefonie |

Etap 5 **nie jest ukończony**.

## Zasady

- Wstępnie zaznaczone są tylko pozycje rozpoznane jako wykonane prace. Zalecenia, wyceny i same części są widoczne, ale niezaznaczone.
- Data i przebieg są wstępnie wybrane tylko wtedy, gdy zostały rozpoznane jednoznacznie; w innym razie użytkownik wybiera kandydata albo wpisuje wartość sam. Formularz nigdy nie podstawia dzisiejszej daty za nierozpoznaną.
- Dokument innego auta i wycena wymagają osobnego potwierdzenia, którego nie zastępuje zaznaczenie pozycji.
- Kwota w walucie innej niż NOK (lub bez rozpoznanej waluty) nie jest przenoszona.
- Uszkodzony lub zabezpieczony plik, zły typ i przekroczone limity (20 MB, 20 stron) dają prosty komunikat; nic nie jest zapisywane, a nazwy i treść plików nie trafiają do logów.

## Dokument przy wpisie (Room v5)

- Tabela `documents`: jeden dokument należy do jednego wpisu serwisowego tego samego auta; usunięcie wpisu usuwa wiersz, a plik jest sprzątany zaraz potem.
- Trzy katalogi, żeby niedokończony krok nie udawał historii: `documents` (pliki zapisanych wpisów), `documents-staged` (dokument w trakcie sprawdzania; znika po porzuceniu formularza) i `documents-restore` (pliki kopii, która jest jeszcze weryfikowana).
- Kopia zapasowa pomija dokument, którego pliku nie ma już na telefonie — wpis zostaje, a ekran szczegółów pokazuje „brak pliku”. Kopia nie udaje, że plik istnieje.
- Przywracanie odmawia, gdy dokument nie należy do dokładnie jednego wpisu albo ma nieobsługiwany typ. Przy każdej odmowie baza i pliki zostają bez zmian.
- Pliki dokumentów nie trafiają do kopii systemowych Androida ani transferu między urządzeniami (jak reszta danych).
- Przerwanie aplikacji dokładnie między zapisem bazy a przeniesieniem plików przy przywracaniu jest naprawiane przy następnym uruchomieniu: zweryfikowane pliki, które czekają, trafiają na miejsce tylko wtedy, gdy rozmiar i skrót zgadzają się z wpisem. Pliki, do których nie odwołuje się żaden wpis, są wtedy usuwane.
- Wybór pliku, którego nie da się odczytać, oraz anulowanie aparatu nie kasują dokumentu, którego sprawdzanie przerwano wcześniej.

## Słownik i reguły odczytu — poprawki z 11 listopada 2026

Sprawdzenie typowych norweskich sformułowań (wpisanych z pamięci, **nie z prawdziwych faktur**) wykazało błąd i braki:

- **Błąd:** linie o innym oleju („Skift olje girkasse”, „Skift olje bakaksel”, „Skift olje automatgir”) były proponowane jako wykonana wymiana oleju silnikowego. Teraz słowo wskazujące inny olej, opłatę środowiskową itp. wyklucza olej silnikowy w tej linii; olej skrzyni jest rozpoznawany jako olej skrzyni, a olej osi czy sprzęgła Haldex nie jest przypisywany do niczego. Zasada: pominięcie pozycji jest bezpieczniejsze niż przypisanie jej do złej czynności.
- **Dodane słowa:** oljeservice, bremsebelegg, klosser, tannreim, kamreim, friskluftfilter, kabinfilter, bensinfilter, drivstofffilter, automatgirolje, bremsesjekk, batterisjekk, „stor/liten service”, „Service 60 000 km”, „service iht.” i podobne; „montert/installert” jako wykonanie wymiany.
- **Przebieg:** etykiety „Km.stand”, „Km.st.”, „KM:” oraz etykieta i wartość w osobnych liniach tabeli. „km:” przy stawce lub krótkiej odległości nie jest stanem licznika.
- **Kwota i data:** „Totalsum”, „Totalbeløp”, „Fakturabeløp”, „Sluttsum”; „Ferdig dato”, „ferdigstilt”, „Reparasjonsdato” jako data wykonania.
- **Drugi błąd przypisania:** „Luftfilter kupé” (filtr kabinowy zapisany dwoma słowami) był odczytywany jako filtr powietrza silnika; teraz jest filtrem kabinowym.
- **Celowo nierozpoznawane:** samo „batteri” (bateria w kluczyku, akumulator trakcyjny), „EU-kontroll”, „AC-service”, wymiana opon, samo słowo „Service”.

To nadal nie jest pomiar skuteczności — ten wymaga prawdziwych dokumentów.

## Koszt

Model rozpoznawania tekstu jest dołączony do aplikacji, więc pakiet testowy urósł do ok. 57 MB (wersja debug ze wszystkimi architekturami). Rozmiar wydania będzie mniejszy i trzeba go zmierzyć przy przygotowaniu do Google Play.
