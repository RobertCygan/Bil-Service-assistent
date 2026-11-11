# Zrzuty ekranów

Obrazy prawdziwych ekranów aplikacji z wymyślonymi danymi, wygenerowane na komputerze (bez telefonu):

    ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*' -Pscreenshots

Wynik trafia do `app/build/screenshots/`; wybrane pliki są skopiowane tutaj. Renderowanie odbywa się na emulowanym
Androidzie 9, więc czcionki i drobne szczegóły mogą się różnić od fizycznego telefonu.
