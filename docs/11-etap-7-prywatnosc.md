# Etap 7 — prywatność: przegląd bibliotek i projekt polityki

Data: **10 listopada 2026**. Stan kodu: Room v5, dokumenty przy wpisach.

## Status

| Element | Stan |
| --- | --- |
| Przegląd uprawnień, bibliotek i logów | **Wykonany** na scalonym manifeście wersji debug i na kodzie źródłowym |
| Brak dostępu do sieci | **Wymuszony w manifeście i pilnowany testami** (test jednostkowy i test na emulatorze) |
| Projekt polityki prywatności (EN) | **Projekt** — wymaga uzupełnienia danych administratora i przeglądu prawnego |
| Wersja norweska polityki | **Projekt** — tłumaczenie poniżej, wymaga sprawdzenia przez osobę znającą norweski |
| Formularz „Data safety” w Google Play | **Nie wypełniony** — propozycja odpowiedzi poniżej |
| Przegląd prawny (RODO / norweska personopplysningsloven) | **Niewykonany** — nie jestem prawnikiem; tekst jest opisem działania, nie opinią prawną |

## Wynik przeglądu

### Uprawnienia w gotowym pakiecie

| Uprawnienie | Skąd | Po co |
| --- | --- | --- |
| `POST_NOTIFICATIONS` | aplikacja | Przypomnienia; pytanie pojawia się dopiero po włączeniu przypomnień |
| `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `ACCESS_NETWORK_STATE` | WorkManager | Codzienne sprawdzenie przypomnień po restarcie telefonu. Aplikacja nie używa warunków sieciowych ani usług pierwszoplanowych |
| `INTERNET` | **usunięte** | Dokładała je biblioteka rozpoznawania tekstu (ML Kit), która wysyła do Google statystyki użycia. Aplikacja usuwa to uprawnienie, więc system odmawia każdego połączenia |

Aplikacja nie prosi o aparat (zdjęcie robi aplikacja aparatu telefonu), lokalizację, kontakty ani dostęp do plików (pliki wskazuje użytkownik w oknie systemowym).

### Znalezisko: statystyki ML Kit

Biblioteka `com.google.mlkit:text-recognition` zawiera moduł wysyłania danych diagnostycznych (`datatransport`). Rozpoznawanie tekstu działa na telefonie, ale bez usunięcia uprawnienia biblioteka mogłaby wysyłać do Google informacje o użyciu (m.in. dane o urządzeniu i wydajności). Dokumentów nie wysyła, lecz obietnica „nic nie opuszcza telefonu” nie byłaby wtedy prawdziwa.

Rozwiązanie: uprawnienie `INTERNET` jest usunięte w manifeście. Skutki:

- żadna część aplikacji ani biblioteki nie może nawiązać połączenia;
- rozpoznawanie tekstu nadal działa, bo model jest w pakiecie — potwierdza to test na emulatorze z prawdziwym ML Kit;
- **odczyt danych auta z rejestru (vegvesen) będzie wymagał przywrócenia uprawnienia.** Trzeba wtedy ponownie ocenić statystyki ML Kit (wyłączyć moduł wysyłki albo opisać go w polityce) i zmienić tekst polityki.

Czego nie sprawdzono: zachowania na fizycznym telefonie przez dłuższy czas (np. czy moduł statystyk nie zapełnia pamięci nieudanymi próbami wysyłki).

### Dane i miejsce przechowywania

| Dane | Gdzie | Kopia systemowa Androida |
| --- | --- | --- |
| Auto (nazwa, marka, model, VIN, numer rejestracyjny), przebiegi, historia serwisowa, własne interwały, odczyty wskaźnika | Baza w prywatnym katalogu aplikacji | Wyłączona |
| Kopie dokumentów (zdjęcia, PDF) | Prywatny katalog aplikacji | Wyłączona |
| Ustawienia przypomnień, zapis dostarczonych przypomnień | Prywatne preferencje | Wyłączona |
| Kopia zapasowa | Plik w miejscu wybranym przez użytkownika; opcjonalnie szyfrowany hasłem | Poza kontrolą aplikacji |

Baza i pliki **nie są dodatkowo szyfrowane** przez aplikację; chroni je szyfrowanie telefonu i izolacja aplikacji Androida.

### Logi i analityka

- Kod aplikacji nie zapisuje niczego do logów systemowych (brak wywołań `Log`, `println`, `printStackTrace`).
- Brak analityki, raportowania awarii, reklam, kont użytkowników i identyfikatorów reklamowych.
- Treść powiadomień zawiera nazwę czynności i auta — jest widoczna na ekranie blokady zgodnie z ustawieniami telefonu.

### Udostępnianie na żądanie użytkownika

- Otwarcie zapisanego dokumentu przekazuje wybranej aplikacji jednorazowy dostęp tylko do odczytu do tego jednego pliku.
- Zdjęcie z poziomu aplikacji wykonuje aplikacja aparatu telefonu; podlega ona własnym zasadom (np. może zapisać lokalizację w pliku).

## Projekt polityki prywatności (EN)

> **Privacy policy — CarServiceAssistent** (draft)
>
> **Who is responsible.** [Name and contact address of the publisher — to be filled in.]
>
> **Summary.** The app works without an account and without an internet connection. Everything you enter stays on your phone. We receive nothing.
>
> **What the app stores on your phone.** The details of your car that you enter (name, make, model, year, and — if you choose — VIN and registration number), mileage readings, service entries with costs and notes, your own service intervals, readings of the car's service display, reminder settings, and copies of invoices or service book pages you decide to keep with an entry.
>
> **Reading documents.** When you choose or photograph a document, its text is recognised on your phone. The document is not uploaded. A copy is kept only if you save the entry and leave "keep a copy" switched on.
>
> **No network access.** The app does not request internet access, so neither the app nor the libraries inside it can send data anywhere.
>
> **Backups.** The app's data is excluded from Android's cloud backup and device transfer. You can create a backup file yourself and choose where to save it; you can protect it with a password. Without a password the file is readable by anyone who obtains it. A forgotten password cannot be recovered.
>
> **Notifications.** If you turn reminders on, the app shows notifications that name the car and the service task. They may be visible on the lock screen, depending on your phone's settings.
>
> **Other apps.** When you open a kept document, the app you pick receives read-only access to that one file. Photos are taken by your phone's camera app, which follows its own rules.
>
> **Deleting your data.** "Delete all data" in the settings removes the car, history, documents and settings from the phone. Uninstalling the app does the same. Backup files you saved elsewhere are yours to delete.
>
> **Not a manufacturer's schedule.** The app does not contain manufacturer service schedules. Dates and distances it shows come from what you entered.
>
> **Changes.** If a later version adds an online feature (for example looking up a car by registration number), this policy will change before that feature is released, and the feature will be optional.

## Projekt polityki prywatności (NO)

Tłumaczenie projektu angielskiego. **Niesprawdzone przez osobę znającą norweski.**

> **Personvernerklæring – CarServiceAssistent** (utkast)
>
> **Hvem som er ansvarlig.** [Utgiverens navn og kontaktadresse – fylles ut.]
>
> **Kort fortalt.** Appen fungerer uten konto og uten internettforbindelse. Alt du legger inn, blir på telefonen din. Vi mottar ingenting.
>
> **Hva appen lagrer på telefonen din.** Opplysningene om bilen som du selv legger inn (navn, merke, modell, årsmodell og – hvis du vil – understellsnummer og registreringsnummer), kilometerstander, serviceoppføringer med kostnader og notater, dine egne serviceintervaller, avlesninger av bilens serviceindikator, innstillinger for påminnelser og kopier av fakturaer eller sider fra serviceheftet som du velger å ta vare på sammen med en oppføring.
>
> **Lesing av dokumenter.** Når du velger eller fotograferer et dokument, gjenkjennes teksten på telefonen din. Dokumentet lastes ikke opp. En kopi beholdes bare hvis du lagrer oppføringen og lar «lagre en kopi» stå på.
>
> **Ingen nettverkstilgang.** Appen ber ikke om internettilgang. Derfor kan verken appen eller bibliotekene i den sende data noe sted.
>
> **Sikkerhetskopier.** Appens data er unntatt fra Androids skysikkerhetskopi og overføring mellom enheter. Du kan selv lage en sikkerhetskopifil og velge hvor den lagres; du kan beskytte den med passord. Uten passord kan filen leses av alle som får tak i den. Et glemt passord kan ikke gjenopprettes.
>
> **Varsler.** Hvis du slår på påminnelser, viser appen varsler som nevner bilen og serviceoppgaven. De kan være synlige på låseskjermen, avhengig av innstillingene på telefonen.
>
> **Andre apper.** Når du åpner et lagret dokument, får appen du velger, lesetilgang til denne ene filen. Bilder tas av telefonens kameraapp, som følger sine egne regler.
>
> **Sletting av data.** «Slett alle data» i innstillingene fjerner bilen, historikken, dokumentene og innstillingene fra telefonen. Det samme skjer når du avinstallerer appen. Sikkerhetskopifiler du har lagret andre steder, må du slette selv.
>
> **Ikke produsentens serviceprogram.** Appen inneholder ikke produsentenes serviceprogrammer. Datoer og kilometerstander den viser, kommer fra det du selv har lagt inn.
>
> **Endringer.** Hvis en senere versjon får en nettbasert funksjon (for eksempel oppslag av bil på registreringsnummer), blir denne erklæringen endret før funksjonen lanseres, og funksjonen blir valgfri.

## Propozycja odpowiedzi w „Data safety” (Google Play)

- Zbieranie danych: **nie**. Udostępnianie danych stronom trzecim: **nie**.
- Szyfrowanie w transmisji: nie dotyczy (brak transmisji).
- Usuwanie danych: użytkownik może usunąć wszystko w aplikacji.

Odpowiedzi są prawdziwe tylko dopóty, dopóki aplikacja nie ma uprawnienia `INTERNET`.

## Do decyzji właściciela

1. Dane administratora do polityki (osoba lub firma, adres kontaktowy) i miejsce publikacji polityki (Google Play wymaga adresu URL).
2. Czy baza i dokumenty mają być dodatkowo szyfrowane w aplikacji. Rekomendacja: **nie w MVP** — szyfrowanie telefonu i izolacja aplikacji wystarczają dla tego rodzaju danych, a własne szyfrowanie dodaje ryzyko utraty danych.
