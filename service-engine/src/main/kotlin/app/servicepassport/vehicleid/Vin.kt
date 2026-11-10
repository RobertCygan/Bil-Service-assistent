package app.servicepassport.vehicleid

public enum class VinError {
    TOO_SHORT,
    TOO_LONG,
    /** Litery I, O i Q nie występują w VIN — zwykle to pomylone 1 lub 0. */
    FORBIDDEN_LETTER,
    INVALID_CHARACTER,
}

public enum class VinRegion { AFRICA, ASIA, EUROPE, NORTH_AMERICA, OCEANIA, SOUTH_AMERICA }

public sealed interface VinResult {
    public data class Valid(
        /** VIN wielkimi literami, bez spacji i myślników. */
        val vin: String,
        /** Pierwsze trzy znaki: identyfikator producenta. */
        val wmi: String,
        val region: VinRegion?,
        /**
         * PODPOWIEDŹ marki z prefiksu producenta; null, gdy prefiksu nie ma w tabeli.
         * Zawsze wymaga potwierdzenia przez użytkownika.
         */
        val makeHint: String?,
        /**
         * Zgodność cyfry kontrolnej (9. znak). Obowiązkowa tylko w Ameryce Północnej —
         * w europejskich VIN wynik `false` jest normalny i NIE oznacza błędu.
         */
        val checkDigitMatches: Boolean,
    ) : VinResult

    public data class Invalid(val errors: Set<VinError>, val normalized: String) : VinResult
}

/**
 * Sprawdzenie formatu VIN (ISO 3779) bez sieci. Nie ustala modelu, rocznika, silnika ani skrzyni:
 * znak roku modelowego nie jest w Europie obowiązkowy, więc celowo go nie interpretujemy.
 */
public object Vin {
    public const val LENGTH: Int = 17

    public fun parse(input: String): VinResult {
        val vin = input.uppercase().filterNot { it.isWhitespace() || it == '-' }
        val errors = sortedSetOf<VinError>()
        if (vin.length < LENGTH) errors += VinError.TOO_SHORT
        if (vin.length > LENGTH) errors += VinError.TOO_LONG
        if (vin.any { it in "IOQ" }) errors += VinError.FORBIDDEN_LETTER
        if (vin.any { it !in 'A'..'Z' && it !in '0'..'9' }) errors += VinError.INVALID_CHARACTER
        if (errors.isNotEmpty()) return VinResult.Invalid(errors, vin)

        val wmi = vin.take(3)
        return VinResult.Valid(vin, wmi, region(vin[0]), WmiTable.makeFor(wmi), checkDigit(vin) == vin[8])
    }

    private fun region(first: Char): VinRegion? = when (first) {
        in 'A'..'H' -> VinRegion.AFRICA
        in 'J'..'R' -> VinRegion.ASIA
        in 'S'..'Z' -> VinRegion.EUROPE
        in '1'..'5' -> VinRegion.NORTH_AMERICA
        '6', '7' -> VinRegion.OCEANIA
        '8', '9' -> VinRegion.SOUTH_AMERICA
        else -> null
    }

    private const val LETTERS = "ABCDEFGHJKLMNPRSTUVWXYZ"
    private val LETTER_VALUES = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 1, 2, 3, 4, 5, 7, 9, 2, 3, 4, 5, 6, 7, 8, 9)
    private val WEIGHTS = intArrayOf(8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2)

    private fun checkDigit(vin: String): Char {
        val sum = vin.indices.sumOf { i ->
            val c = vin[i]
            (if (c.isDigit()) c - '0' else LETTER_VALUES[LETTERS.indexOf(c)]) * WEIGHTS[i]
        }
        return (sum % 11).let { if (it == 10) 'X' else '0' + it }
    }
}

/**
 * Prefiksy producentów (WMI) dla marek częstych w krajach nordyckich. Tabela jest celowo krótka,
 * spisana z wiedzy ogólnej i NIE była porównana z oficjalnym rejestrem SAE — dlatego wynik to
 * wyłącznie podpowiedź. Nazwy odpowiadają polu `name` w vehicle-catalog/vehicle-list.json.
 */
internal object WmiTable {
    private val makes: Map<String, String> = buildMap {
        fun add(make: String, vararg prefixes: String) = prefixes.forEach { put(it, make) }
        add("Volkswagen", "WVW", "WVG", "WV1", "WV2")
        add("Audi", "WAU", "WUA", "TRU")
        add("BMW", "WBA", "WBS", "WBY")
        add("Mercedes-Benz", "WDB", "WDD", "WDC", "W1K", "W1N")
        add("Smart", "WME")
        add("Porsche", "WP0", "WP1")
        add("Opel", "W0L", "W0V")
        add("Ford", "WF0")
        add("Škoda", "TMB")
        add("SEAT", "VSS")
        add("Renault", "VF1")
        add("Peugeot", "VF3", "VR3")
        add("Citroën", "VF7", "VR7")
        add("DS", "VR1")
        add("Dacia", "UU1")
        add("Fiat", "ZFA")
        add("Alfa Romeo", "ZAR")
        add("Ferrari", "ZFF")
        add("Volvo", "YV1", "YV4")
        add("Saab", "YS3")
        add("Land Rover", "SAL")
        add("Jaguar", "SAJ")
        add("Toyota", "JTD", "JTE", "SB1", "VNK")
        add("Lexus", "JTH")
        add("Nissan", "JN1", "SJN")
        add("Mazda", "JMZ")
        add("Honda", "JHM")
        add("Subaru", "JF1")
        add("Hyundai", "KMH", "TMA", "NLH")
        add("Kia", "KNA", "KND", "U5Y")
        add("Tesla", "5YJ", "7SA", "LRW", "XP7")
    }

    fun makeFor(wmi: String): String? = makes[wmi]
}
