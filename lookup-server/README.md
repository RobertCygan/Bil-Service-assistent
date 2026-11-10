# lookup-server — serwer pośredniczący do odczytu danych auta

Mały serwer na własny VPS. Stoi między aplikacją a rejestrem Statens vegvesen (SVV): trzyma klucz API po stronie serwera, ogranicza nadużycia i nie zapisuje numerów rejestracyjnych ani VIN w logach.

## Status — 10 listopada 2026

| Element | Stan |
| --- | --- |
| Serwer, walidacja zapytań, limity, cache, logi bez numerów | **Zaimplementowane**, 11 testów; proces uruchomiony i sprawdzony lokalnie |
| Połączenie z prawdziwym API SVV | **Nigdy nie wywołane** — brak klucza. Adres i nagłówek autoryzacji są wpisane z wiedzy ogólnej i wymagają potwierdzenia |
| Mapowanie odpowiedzi SVV na pola aplikacji | **Nie ma** — odpowiedź jest przekazywana bez zmian (`"mapped": false`) |
| Obraz Dockera | Plik przygotowany; **budowanie obrazu nie było testowane** |
| Ochrona przed podszywaniem się pod aplikację (Play Integrity) | **Nie ma** — patrz „Bezpieczeństwo” |

Bez `SVV_API_KEY` serwer startuje i odpowiada `503 lookup_not_configured`.

## API

| Zapytanie | Wynik |
| --- | --- |
| `GET /v1/health` | `{"status":"ok","lookupConfigured":true}` |
| `GET /v1/vehicles/no/plate/{numer}` | dane auta po norweskim numerze rejestracyjnym |
| `GET /v1/vehicles/no/vin/{vin}` | dane auta po VIN (tylko auta zarejestrowane w Norwegii) |

Odpowiedź przy powodzeniu: `{"source":"svv","mapped":false,"data":{...}}`.

| Kod | `error` | Znaczenie |
| --- | --- | --- |
| 400 | `invalid_identifier` | zły format numeru lub VIN — rejestr nie jest pytany |
| 401 | `unauthorized` | brak lub zły `X-App-Token` (gdy ustawiono `APP_TOKEN`) |
| 404 | `vehicle_not_found` | rejestr nie zna pojazdu |
| 429 | `rate_limited` | limit na adres IP |
| 502 | `upstream_error` | rejestr nie odpowiedział poprawnie |
| 503 | `lookup_not_configured` / `lookup_unavailable` / `daily_limit_reached` | brak klucza / rejestr odrzucił klucz / wyczerpany dzienny budżet |

## Konfiguracja (zmienne środowiskowe)

| Zmienna | Domyślnie | Opis |
| --- | --- | --- |
| `SVV_API_KEY` | brak | klucz API SVV; **tylko w środowisku serwera**, nigdy w repozytorium |
| `SVV_URL`, `SVV_AUTH_HEADER`, `SVV_AUTH_PREFIX` | wartości niepotwierdzone | do poprawienia po sprawdzeniu dokumentacji SVV |
| `APP_TOKEN` | brak | opcjonalny wspólny token w nagłówku `X-App-Token` |
| `RATE_PER_CLIENT_PER_HOUR` | 30 | limit zapytań na adres IP |
| `UPSTREAM_PER_DAY` | 5000 | dzienny budżet zapytań do SVV (ich limit: 50 000) |
| `CACHE_SECONDS` | 0 (wyłączone) | pamięć odpowiedzi; włączyć po sprawdzeniu warunków SVV |
| `TRUST_PROXY` | false | `true` tylko za własnym reverse proxy (adres klienta z `X-Forwarded-For`) |
| `PORT` | 8080 | port nasłuchu |

## Uruchomienie na VPS

```bash
docker build -f lookup-server/Dockerfile -t lookup-server .
```

```bash
docker run -d --name lookup-server --restart unless-stopped -p 127.0.0.1:8080:8080 --env-file /etc/lookup-server.env lookup-server
```

Plik `/etc/lookup-server.env` (uprawnienia 600) zawiera `SVV_API_KEY=...` i `TRUST_PROXY=true`. HTTPS zapewnia reverse proxy (np. Caddy lub nginx) kierujące ruch na `127.0.0.1:8080`; proxy musi **nadpisywać** nagłówek `X-Forwarded-For`, inaczej limit na adres da się obejść. W logach dostępu proxy trzeba wyłączyć zapisywanie ścieżki zapytania, bo zawiera numer.

Bez Dockera: `./gradlew installDist`, potem `build/install/lookup-server/bin/lookup-server` (wymaga Javy 17+).

## Bezpieczeństwo — co jest, a czego nie ma

- Klucz SVV zostaje na serwerze; aplikacja go nie zna.
- Logi zawierają tylko metodę, szablon trasy, kod odpowiedzi i czas. Bez numerów, VIN i adresów klientów.
- Limit na adres IP i twardy dzienny budżet chronią limit SVV i koszty.
- Stan (limity, cache) jest tylko w pamięci jednego procesu: restart go zeruje, a kilka instancji nie dzieli limitów.
- `APP_TOKEN` to słaba bariera — da się go wyciągnąć z aplikacji. Serwer **nie potwierdza**, że pyta prawdziwa aplikacja; właściwym rozwiązaniem jest Play Integrity, do dodania przed publikacją.
- Numer rejestracyjny i VIN to dane osobowe pośrednio identyfikujące właściciela: serwer i proxy trzeba ująć w polityce prywatności (Etap 7).

## Testy

```bash
./gradlew build
```
