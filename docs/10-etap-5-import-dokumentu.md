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
| Wznowienie po zamknięciu aplikacji w trakcie sprawdzania | **Częściowo**: kopia dokumentu zostaje i ekran importu proponuje odczytanie jej ponownie albo odrzucenie. Wybory z ekranu sprawdzenia **nie są** zachowywane. Niesprawdzone na telefonie |

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
- Ograniczenie: gdy aplikacja zostanie przerwana dokładnie między zapisem bazy a przeniesieniem plików przy przywracaniu, wpisy mogą wskazywać brakujące pliki; trzeba wtedy przywrócić kopię ponownie.

## Koszt

Model rozpoznawania tekstu jest dołączony do aplikacji, więc pakiet testowy urósł do ok. 57 MB (wersja debug ze wszystkimi architekturami). Rozmiar wydania będzie mniejszy i trzeba go zmierzyć przy przygotowaniu do Google Play.
