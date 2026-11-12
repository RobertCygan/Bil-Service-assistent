package app.servicepassport.extraction

/** Liczba, która może być przebiegiem, razem z tekstem, w którym ją znaleziono. */
public data class OdometerCandidate(val km: Int, val text: String, val score: Int)

/**
 * Odczyt przebiegu ze zdjęcia zestawu wskaźników. Na takim zdjęciu jest wiele liczb: skala prędkościomierza, licznik
 * dzienny, zasięg, zegar, temperatura. Tutaj wybierane są te, które mogą być przebiegiem całkowitym, od najbardziej
 * do najmniej prawdopodobnej. Wynik jest tylko propozycją: użytkownik widzi liczbę i sam ją zatwierdza.
 */
public object OdometerPhoto {
    private const val MIN_UNLABELLED_KM = 1_000
    private const val MAX_KM = 2_000_000
    private const val PLAUSIBLE_GROWTH_KM = 150_000

    private val TIME = Regex("""\d{1,2}:\d{2}""")
    private val DATE = Regex("""\d{1,2}[./-]\d{1,2}[./-]\d{2,4}""")
    /** Licznik dzienny, zużycie, temperatura: liczba z częścią dziesiętną. */
    private val DECIMAL = Regex("""\d+[.,]\d{1,2}(?!\d)""")
    private val OTHER_UNIT = Regex("""\d[\d .,]*\s*(?:km\s*/\s*[ht]|kmh|mph|rpm|%|kwh|kw|l\s*/|°|º|bar|psi|v\b)""", RegexOption.IGNORE_CASE)
    private val NUMBER = Regex("""(?<![\d.,])(?:\d{1,3}(?:[ .,  ]\d{3})+|\d{1,7})(?![\d.,]\d)""")
    private val ODO_LABEL = Regex("""\b(?:odo|odometer|total|kilometerstand|km[- .]?stand|totalt)\b""", RegexOption.IGNORE_CASE)
    private val NOT_ODO_LABEL = Regex("""\b(?:trip|tur|range|rekkevidde|rest|avg|snitt|service|temp)\b""", RegexOption.IGNORE_CASE)
    private val KM_AFTER = Regex("""^\s*km\b(?!\s*/)""", RegexOption.IGNORE_CASE)
    private val KM_ALONE = Regex("""^\s*km\s*$""", RegexOption.IGNORE_CASE)

    public fun candidates(lines: List<OcrLine>, lastKnownKm: Int? = null): List<OdometerCandidate> {
        val found = mutableListOf<OdometerCandidate>()
        lines.forEachIndexed { index, line ->
            val original = line.text
            // To, co na pewno nie jest przebiegiem, znika z linii, zanim zaczną być szukane liczby.
            var text = original
            for (noise in listOf(TIME, DATE, OTHER_UNIT, DECIMAL)) text = noise.replace(text) { " ".repeat(it.value.length) }
            val labelled = ODO_LABEL.containsMatchIn(original) || (index > 0 && ODO_LABEL.containsMatchIn(lines[index - 1].text) && original.none(Char::isLetter))
            val otherLabel = NOT_ODO_LABEL.containsMatchIn(original) || (index > 0 && NOT_ODO_LABEL.containsMatchIn(lines[index - 1].text) && original.none(Char::isLetter))
            for (match in NUMBER.findAll(text)) {
                val digits = match.value.filter(Char::isDigit)
                val km = digits.toIntOrNull() ?: continue
                if (km > MAX_KM || digits.length > 7) continue
                val kmFollows = KM_AFTER.containsMatchIn(text.substring(match.range.last + 1)) ||
                    (text.substring(match.range.last + 1).isBlank() && lines.getOrNull(index + 1)?.let { KM_ALONE.matches(it.text) } == true)
                // Krótka liczba bez żadnego opisu to zwykle skala prędkościomierza albo obrotomierza.
                if (km < MIN_UNLABELLED_KM && !labelled && !kmFollows) continue
                if (km == 0) continue
                var score = digits.length * 2
                if (labelled) score += 12
                if (kmFollows) score += 6
                if (otherLabel && !labelled) score -= 14
                if (lastKnownKm != null) score += when {
                    // Im bliżej ostatniego znanego przebiegu, tym lepiej: auto rzadko przejeżdża dziesiątki tysięcy km między odczytami.
                    km in lastKnownKm..(lastKnownKm + PLAUSIBLE_GROWTH_KM) -> 10 - (km - lastKnownKm) / 15_000
                    km < lastKnownKm -> -8
                    else -> -3
                }
                found += OdometerCandidate(km, original.trim(), score)
            }
        }
        // Przy równej ocenie wygrywa liczba najbliższa ostatniemu znanemu przebiegowi; gdy go nie ma — większa.
        val order = compareByDescending<OdometerCandidate> { it.score }
            .thenBy { if (lastKnownKm != null) kotlin.math.abs(it.km - lastKnownKm) else 0 }
            .thenByDescending { it.km }
        return found.filter { it.score > 0 }.sortedWith(order).distinctBy { it.km }.take(3)
    }
}
