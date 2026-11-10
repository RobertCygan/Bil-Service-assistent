package app.servicepassport.vehicleid

import java.text.Normalizer

/** Dopasowanie nazwy marki lub modelu (z VIN, rejestru albo wpisanej ręcznie) do pozycji listy. */
public object CatalogMatcher {

    /** Zwraca pozycję listy równą [query] z pominięciem wielkości liter, akcentów, spacji i interpunkcji. */
    public fun exact(query: String, names: Collection<String>): String? {
        val key = key(query)
        if (key.isEmpty()) return null
        return names.firstOrNull { key(it) == key }
    }

    /**
     * Wyszukiwarka do płaskiej listy: najpierw nazwy zaczynające się od zapytania, potem zawierające je.
     * Kolejność wejściowa (np. wg popularności) jest zachowana w obrębie każdej grupy.
     */
    public fun search(query: String, names: List<String>, limit: Int = 20): List<String> {
        val key = key(query)
        if (key.isEmpty()) return names.take(limit)
        val keyed = names.map { it to key(it) }
        val starts = keyed.filter { it.second.startsWith(key) }
        val contains = keyed.filter { !it.second.startsWith(key) && key in it.second }
        return (starts + contains).map { it.first }.take(limit)
    }

    private val MARKS = Regex("\\p{M}+")

    private fun key(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(MARKS, "")
            .uppercase().replace("Ø", "O").replace("Æ", "AE")
            .filter { it.isLetterOrDigit() }
}
