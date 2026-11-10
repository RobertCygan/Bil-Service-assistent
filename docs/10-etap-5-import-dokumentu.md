# Etap 5 — import dokumentu w aplikacji (pierwszy przyrost)

Data: **10 listopada 2026**.

## Status

| Element | Stan |
| --- | --- |
| Wybór zdjęcia lub PDF, odczyt tekstu **na telefonie** (ML Kit z modelem w aplikacji), PDF do 20 stron | **Zaimplementowane** |
| Ekran sprawdzenia: data, przebieg, pozycje, kwota, ostrzeżenia; przy każdej wartości linia źródłowa i numer strony | **Zaimplementowane** |
| Zatwierdzanie | Wynik sprawdzenia **wypełnia zwykły formularz wpisu serwisowego**; do historii trafia dopiero po zapisaniu tam, tą samą ścieżką walidacji co wpis ręczny |
| Test prawdziwego rozpoznawania | Test na emulatorze generuje dwustronicową fakturę PDF i odczytuje ją ML Kit — **czysty wydruk, nie zdjęcie** |
| Pomiar na prawdziwych fakturach NO/EN (kryterium odbioru Etapu 5) | **Niewykonany** — brak zestawu dokumentów |
| Robienie zdjęcia aparatem z poziomu aplikacji | **Nie ma** — zdjęcie trzeba zrobić aparatem telefonu i wybrać plik |
| Przechowywanie dokumentu przy wpisie, podgląd fragmentu obrazu, wykrywanie duplikatów plików | **Nie ma** — po odczycie plik nie jest zapisywany w aplikacji |
| Trwały szkic (przetrwanie zamknięcia aplikacji w trakcie sprawdzania) | **Nie ma** — szkic żyje tylko w pamięci |

Etap 5 **nie jest ukończony**.

## Zasady

- Wstępnie zaznaczone są tylko pozycje rozpoznane jako wykonane prace. Zalecenia, wyceny i same części są widoczne, ale niezaznaczone.
- Data i przebieg są wstępnie wybrane tylko wtedy, gdy zostały rozpoznane jednoznacznie; w innym razie użytkownik wybiera kandydata albo wpisuje wartość sam. Formularz nigdy nie podstawia dzisiejszej daty za nierozpoznaną.
- Dokument innego auta i wycena wymagają osobnego potwierdzenia, którego nie zastępuje zaznaczenie pozycji.
- Kwota w walucie innej niż NOK (lub bez rozpoznanej waluty) nie jest przenoszona.
- Uszkodzony lub zabezpieczony plik, zły typ i przekroczone limity (20 MB, 20 stron) dają prosty komunikat; nic nie jest zapisywane, a nazwy i treść plików nie trafiają do logów.

## Koszt

Model rozpoznawania tekstu jest dołączony do aplikacji, więc pakiet testowy urósł do ok. 57 MB (wersja debug ze wszystkimi architekturami). Rozmiar wydania będzie mniejszy i trzeba go zmierzyć przy przygotowaniu do Google Play.
