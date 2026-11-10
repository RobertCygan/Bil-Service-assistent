package app.servicepassport.vehicleid

public enum class PlateKind {
    /** Dwie litery i pięć cyfr — zwykły samochód. */
    STANDARD_CAR,
    /** Dwie litery i cztery cyfry — m.in. motocykle i przyczepy. */
    STANDARD_SHORT,
    /** Tablica indywidualna lub inny format (2–7 znaków); istnienie potwierdzi dopiero rejestr. */
    OTHER,
}

public sealed interface PlateResult {
    /** [plate] bez spacji i myślników, wielkimi literami — w tej postaci wysyłamy do rejestru. */
    public data class Accepted(val plate: String, val kind: PlateKind) : PlateResult
    public data object Invalid : PlateResult
}

/**
 * Wstępne sprawdzenie norweskiego numeru rejestracyjnego przed zapytaniem do Statens vegvesen.
 * Sprawdza tylko postać zapisu — nie to, czy taki pojazd istnieje.
 */
public object NorwegianPlate {
    private val CAR = Regex("[A-ZÆØÅ]{2}\\d{5}")
    private val SHORT = Regex("[A-ZÆØÅ]{2}\\d{4}")
    private val OTHER = Regex("[A-ZÆØÅ0-9]{2,7}")

    public fun parse(input: String): PlateResult {
        val plate = input.uppercase().filterNot { it.isWhitespace() || it == '-' }
        val kind = when {
            CAR.matches(plate) -> PlateKind.STANDARD_CAR
            SHORT.matches(plate) -> PlateKind.STANDARD_SHORT
            OTHER.matches(plate) -> PlateKind.OTHER
            else -> return PlateResult.Invalid
        }
        return PlateResult.Accepted(plate, kind)
    }
}
