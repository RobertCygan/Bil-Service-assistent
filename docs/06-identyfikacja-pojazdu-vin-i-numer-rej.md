# Identyfikacja auta po VIN i norweskim numerze rejestracyjnym — plan

Data: **10 listopada 2026**. Wymaganie właściciela: aplikacja ma odczytywać dane auta po VIN, a w Norwegii po numerze rejestracyjnym (Statens vegvesen, SVV). **To jest plan, nie implementacja.**

## Co to zmienia względem Etapu 0

Etap 0 zakładał MVP bez backendu, a automatyczny lookup jako rozszerzenie. API SVV wymaga klucza, a klucza nie wolno umieścić w aplikacji mobilnej. **Wymaganie oznacza więc mały serwer pośredniczący.** Konta użytkowników nadal nie są potrzebne.

## Norwegia: numer rejestracyjny i VIN przez SVV

Stan wiedzy (z wyników wyszukiwania, bez otwarcia dokumentacji — patrz [05-badanie](05-badanie-api-pojazdow-i-producentow.md)):

- API pojedynczych zapytań przyjmuje numer rejestracyjny albo VIN i zwraca dane techniczne bez danych właściciela;
- klucz wydawany automatycznie, wymaga norweskiej e-ID; limit 50 000 zapytań na klucz na dobę;
- obejmuje pojazdy zarejestrowane w Norwegii.

Niepotwierdzone, do sprawdzenia przed budową:

1. Czy odpowiedź zawiera kod silnika i typ/kod skrzyni — od tego zależy, czy lookup wystarczy do przypisania harmonogramu, czy tylko wypełni markę, model i rok.
2. Warunki użycia w aplikacji komercyjnej, zasady przechowywania wyników i wymagana atrybucja.
3. Dokładny format odpowiedzi (nie projektuję mapowania na zgadywanym schemacie).

## VIN poza Norwegią

Nie ma bezpłatnego, wiarygodnego dekodera VIN dla całej Europy.

| Wariant | Co daje | Koszt | Ocena |
| --- | --- | --- | --- |
| Lokalnie w aplikacji: kontrola formatu + prefiks producenta (WMI) | Producent i kraj producenta; walidacja 17 znaków | 0, działa offline | Pewne, ale to tylko podpowiedź marki |
| NHTSA vPIC | Marka, model, rok dla aut z rynku USA | 0 | Dla aut europejskich często niepełne; nietestowane |
| Komercyjny dekoder VIN | Marka, model, rok, czasem silnik | Opłata za zapytanie; cen nie badałem | Decyzja po poznaniu potrzeb |

Sam VIN zwykle **nie ujawnia** kodu skrzyni ani systemu serwisowego. Znak roku modelowego (10. pozycja) nie jest w Europie obowiązkowy, więc nie wolno na nim polegać.

## Proponowane kroki

1. **Offline (bez decyzji zewnętrznych):** walidacja VIN i norweskiego numeru rejestracyjnego, podpowiedź marki z WMI, dopasowanie do listy z `vehicle-catalog`. Czysty Kotlin z testami, jak silnik.
2. **SVV:** po uzyskaniu klucza — odczyt prawdziwej odpowiedzi, mapowanie na konfigurację pojazdu, serwer pośredniczący (klucz po stronie serwera, limit zapytań, brak VIN i numerów w logach).
3. **Dekoder komercyjny:** osobna decyzja po kroku 2.

W każdym wariancie wynik lookupu jest **propozycją do potwierdzenia przez użytkownika**, a ręczne dodanie auta pozostaje pełnoprawną ścieżką.

## Potrzebne od właściciela

- Wniosek o klucz API SVV (wymaga Twojej norweskiej e-ID) i przekazanie go przez bezpieczne ustawienia środowiska, nie w czacie ani repozytorium.
- Zgoda na mały serwer i wybór, gdzie ma działać (koszt planistyczny z Etapu 0: 200–1000 NOK/mies.).
- Zgoda na krok 1.
