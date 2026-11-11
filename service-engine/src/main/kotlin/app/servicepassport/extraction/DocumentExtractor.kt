package app.servicepassport.extraction

import java.time.DateTimeException
import java.time.LocalDate
import java.util.Locale

/**
 * Ekstrakcja regułowa z tekstu OCR. Czysta funkcja: bez I/O, bez modelu AI, bez zegara.
 * Treść dokumentu jest wyłącznie danymi — nic z niej nie jest wykonywane ani interpretowane jako polecenie.
 */
public class DocumentExtractor(private val dictionary: List<TaskTerm> = DefaultTaskDictionary.terms) {

    private class Line(val evidence: Evidence, val norm: String)

    private class Matcher(val term: TaskTerm, val operations: List<Pair<String, List<Regex>>>, val part: List<Regex>, val unless: List<Regex>)

    private val matchers = dictionary.map { term ->
        // „replace” sprawdzamy pierwsze: fraza wymiany wygrywa z frazą kontroli w tej samej linii
        val ordered = term.operationPhrases.entries.sortedBy { if (it.key == "replace") 0 else 1 }
        Matcher(term, ordered.map { it.key to it.value.map(::phrase) }, term.partPhrases.map(::phrase), term.unless.map(::phrase))
    }

    /** Przegląd zapisany liczbą zamiast nazwą („Service 60 000 km”) — tylko gdy słownik zna przegląd serwisowy. */
    private val serviceTerm = dictionary.firstOrNull { it.action == SERVICE_ACTION }

    public fun extract(pages: List<OcrPage>, vehicle: VehicleIdentity, today: LocalDate): DraftProposal {
        val lines = pages.sortedBy { it.index }.flatMap { page ->
            page.lines.mapIndexedNotNull { i, l ->
                val norm = l.text.lowercase(Locale.ROOT).replace(WHITESPACE, " ").trim()
                if (norm.isEmpty()) null else Line(Evidence(page.index, i, l.box, l.text), norm)
            }
        }
        val warnings = sortedSetOf<DraftWarning>()
        if (lines.sumOf { l -> l.norm.count { it.isLetterOrDigit() } } < MIN_READABLE_CHARS) {
            warnings += DraftWarning.NO_READABLE_TEXT
        }

        val head = lines.take(HEAD_LINES)
        val isQuote = head.any { QUOTE_DOC.containsMatchIn(it.norm) } && head.none { INVOICE_DOC.containsMatchIn(it.norm) }
        if (isQuote) warnings += DraftWarning.DOCUMENT_IS_QUOTE

        val vin = identifiers(lines, VIN) { v -> v.count(Char::isDigit) >= 2 && v.count(Char::isLetter) >= 2 }
        val plate = identifiers(lines, PLATE) { v -> v.take(2) !in NOT_PLATE_PREFIX }
        if (mismatch(vehicle, vin, plate)) warnings += DraftWarning.VEHICLE_MISMATCH

        val (odometer, miles) = odometer(lines)
        if (miles) warnings += DraftWarning.MILEAGE_IN_MILES

        return DraftProposal(
            serviceDate = serviceDate(lines, today),
            odometerKm = odometer,
            vin = idField(vin, vehicle.vin),
            plate = idField(plate, vehicle.plate),
            total = total(lines),
            items = items(lines, isQuote),
            warnings = warnings,
        )
    }

    // --- data usługi ---

    private enum class DateKind { SERVICE, INVOICE, OTHER }

    private class DateHit(val date: LocalDate, val kind: DateKind, val ambiguous: Boolean, val evidence: Evidence)

    private fun serviceDate(lines: List<Line>, today: LocalDate): Field<LocalDate> {
        val hits = mutableListOf<DateHit>()
        lines.forEachIndexed { i, line ->
            val found = datesIn(line.norm).filter { (d, _) -> !d.isAfter(today) && d.year >= 1980 }
            if (found.isEmpty()) return@forEachIndexed
            // Etykieta bywa w poprzedniej linii (tabele); sięgamy tam tylko, gdy ta linia nie ma liter.
            val context = if (line.norm.none(Char::isLetter) && i > 0) lines[i - 1].norm else line.norm
            if (DATE_EXCLUDED.containsMatchIn(context)) return@forEachIndexed
            val kind = when {
                DATE_SERVICE.containsMatchIn(context) -> DateKind.SERVICE
                DATE_INVOICE.containsMatchIn(context) -> DateKind.INVOICE
                else -> DateKind.OTHER
            }
            found.forEach { (d, ambiguous) -> hits += DateHit(d, kind, ambiguous, line.evidence) }
        }
        if (hits.isEmpty()) return Field(null, FieldState.NOT_FOUND, emptyList())

        val candidates = hits.sortedBy { it.kind }.map { Candidate(it.date, it.evidence) }.distinctBy { it.value }
        fun unique(kind: DateKind?) = hits.filter { kind == null || it.kind == kind }.map { it.date }.distinct().singleOrNull()
        val service = unique(DateKind.SERVICE)
        val value = service ?: unique(DateKind.INVOICE) ?: unique(null)
        // „Rozpoznano” tylko dla jednoznacznie opisanej daty wykonania. Data faktury to nie data usługi.
        val sure = value != null && value == service && hits.none { it.date == value && it.ambiguous }
        return Field(value, if (sure) FieldState.RECOGNIZED else FieldState.CHECK, candidates)
    }

    /** Zwraca daty z linii; flaga = zapis typu 03/04/2025, w którym kolejność dzień/miesiąc jest niepewna. */
    private fun datesIn(norm: String): List<Pair<LocalDate, Boolean>> {
        val out = mutableListOf<Pair<LocalDate, Boolean>>()
        var rest = norm
        fun take(regex: Regex, build: (MatchResult) -> Pair<LocalDate, Boolean>?) {
            rest = regex.replace(rest) { m ->
                build(m)?.let { out += it }
                " ".repeat(m.value.length)
            }
        }
        take(DATE_ISO) { m -> date(m.groupValues[1], m.groupValues[2], m.groupValues[3])?.let { it to false } }
        take(DATE_NUMERIC) { m ->
            val (d, sep, mo, y) = m.destructured
            val year = if (y.length == 2) "20$y" else y
            date(year, mo, d)?.let { it to (sep == "/" && d.toInt() <= 12 && mo.toInt() <= 12 && d.toInt() != mo.toInt()) }
        }
        take(DATE_DAY_MONTHNAME) { m -> date(m.groupValues[3], MONTHS.getValue(m.groupValues[2]), m.groupValues[1])?.let { it to false } }
        take(DATE_MONTHNAME_DAY) { m -> date(m.groupValues[3], MONTHS.getValue(m.groupValues[1]), m.groupValues[2])?.let { it to false } }
        return out
    }

    private fun date(y: String, m: String, d: String): LocalDate? = try {
        LocalDate.of(y.toInt(), m.toInt(), d.toInt())
    } catch (_: DateTimeException) {
        null
    }

    // --- przebieg ---

    private fun odometer(lines: List<Line>): Pair<Field<Int>, Boolean> {
        val labeled = mutableListOf<Candidate<Int>>()
        val unlabeled = mutableListOf<Candidate<Int>>()
        var miles = false
        for ((i, line) in lines.withIndex()) {
            if (ODO_EXCLUDED.containsMatchIn(line.norm)) continue
            // W tabelach etykieta i wartość bywają w osobnych liniach: sama etykieta, a pod nią sama liczba.
            if (ODO_LABEL_ONLY.matches(line.norm)) {
                lines.getOrNull(i + 1)?.let { next ->
                    ODO_VALUE_ONLY.matchEntire(next.norm)?.groupValues?.get(1)?.filter(Char::isDigit)?.toIntOrNull()
                        ?.takeIf { it in MIN_BARE_KM..MAX_KM }?.let { labeled += Candidate(it, next.evidence) }
                }
                continue
            }
            val m = ODO_LABELED.find(line.norm)
            val bare = if (m == null) ODO_BARE.find(line.norm) else null
            if (m != null) {
                val value = m.groupValues[2].filter(Char::isDigit).toIntOrNull()?.takeIf { it <= MAX_KM } ?: continue
                if (m.groupValues[3].startsWith("mi")) miles = true else labeled += Candidate(value, line.evidence)
            } else if (bare != null) {
                // Samo „km:” jest etykietą tylko dla liczby, która wygląda na stan licznika, a nie na stawkę czy odległość.
                bare.groupValues[1].filter(Char::isDigit).toIntOrNull()?.takeIf { it in MIN_BARE_KM..MAX_KM }
                    ?.let { labeled += Candidate(it, line.evidence) }
            } else {
                ODO_UNLABELED.findAll(line.norm).forEach { u ->
                    u.groupValues[1].filter(Char::isDigit).toIntOrNull()?.takeIf { it in 1..MAX_KM }
                        ?.let { unlabeled += Candidate(it, line.evidence) }
                }
            }
        }
        val candidates = (labeled + unlabeled).distinctBy { it.value }
        // Wartość proponujemy wyłącznie z linii opisanej jako stan licznika.
        val value = labeled.map { it.value }.distinct().singleOrNull()
        val state = when {
            value != null && !miles -> FieldState.RECOGNIZED
            candidates.isEmpty() && !miles -> FieldState.NOT_FOUND
            else -> FieldState.CHECK
        }
        return Field(if (miles) null else value, state, candidates) to miles
    }

    // --- identyfikacja pojazdu ---

    private fun identifiers(lines: List<Line>, regex: Regex, accept: (String) -> Boolean): List<Candidate<String>> =
        lines.flatMap { line ->
            regex.findAll(line.evidence.text.uppercase(Locale.ROOT)).map { it.value.filter(Char::isLetterOrDigit) }
                .filter(accept).map { Candidate(it, line.evidence) }
        }.distinctBy { it.value }

    private fun idField(found: List<Candidate<String>>, expected: String?): Field<String> {
        val match = found.firstOrNull { it.value == expected.id() }
        return when {
            found.isEmpty() -> Field(null, FieldState.NOT_FOUND, found)
            match != null -> Field(match.value, FieldState.RECOGNIZED, found)
            else -> Field(found.singleOrNull()?.value, FieldState.CHECK, found)
        }
    }

    private fun mismatch(vehicle: VehicleIdentity, vin: List<Candidate<String>>, plate: List<Candidate<String>>): Boolean {
        fun verdict(expected: String?, found: List<Candidate<String>>): Boolean? =
            if (expected.id() == null || found.isEmpty()) null else found.any { it.value == expected.id() }
        val verdicts = listOfNotNull(verdict(vehicle.vin, vin), verdict(vehicle.plate, plate))
        return verdicts.isNotEmpty() && verdicts.none { it }
    }

    private fun String?.id(): String? = this?.uppercase(Locale.ROOT)?.filter(Char::isLetterOrDigit)?.takeIf { it.isNotEmpty() }

    // --- pozycje ---

    private fun items(lines: List<Line>, isQuote: Boolean): List<DraftItem> {
        class Match(val term: TaskTerm, val phraseOperation: String?)

        val items = mutableListOf<DraftItem>()
        var recommendedSection = false
        for (line in lines) {
            val matched = matchers.mapNotNull { m ->
                // Słowo wykluczające wygrywa ze wszystkim: lepiej pominąć pozycję niż przypisać ją do złej czynności.
                if (m.unless.any { it.containsMatchIn(line.norm) }) return@mapNotNull null
                val byPhrase = m.operations.firstOrNull { (_, phrases) -> phrases.any { it.containsMatchIn(line.norm) } }?.first
                if (byPhrase != null || m.part.any { it.containsMatchIn(line.norm) }) Match(m.term, byPhrase) else null
            }.toMutableList()
            if (serviceTerm != null && matched.none { it.term.action == SERVICE_ACTION } && SERVICE_AT_KM.containsMatchIn(line.norm)) {
                matched += Match(serviceTerm, serviceTerm.operations.first())
            }
            if (matched.isEmpty()) {
                val heading = line.norm.none(Char::isDigit) && line.norm.split(' ').size <= MAX_HEADING_WORDS
                if (heading && RECOMMENDED_HEADING.containsMatchIn(line.norm)) recommendedSection = true
                if (heading && PERFORMED_HEADING.containsMatchIn(line.norm)) recommendedSection = false
                continue
            }
            // Czasownik w linii wskazuje operację dla samych nazw części; wymiana wygrywa z kontrolą.
            val verbOperation = when {
                REPLACE_VERB.containsMatchIn(line.norm) || matched.any { it.phraseOperation == "replace" } -> "replace"
                TEST_VERB.containsMatchIn(line.norm) -> "test"
                INSPECT_VERB.containsMatchIn(line.norm) -> "inspect"
                else -> null
            }
            for (match in matched) {
                val allowed = match.term.operations
                // Sprawdzenie akumulatora to w kontrakcie „test”, nie „inspect”.
                val fromVerb = when {
                    verbOperation == null || verbOperation in allowed -> verbOperation
                    verbOperation == "inspect" && "test" in allowed -> "test"
                    else -> null
                }
                val operation = match.phraseOperation ?: fromVerb
                val kind = when {
                    isQuote -> ItemKind.QUOTE
                    NOT_DONE.containsMatchIn(line.norm) -> ItemKind.UNCLEAR
                    recommendedSection || RECOMMENDED.containsMatchIn(line.norm) -> ItemKind.RECOMMENDED
                    operation != null -> ItemKind.PERFORMED
                    verbOperation != null -> ItemKind.UNCLEAR // czasownik jest, ale ta czynność nie ma takiej operacji
                    else -> ItemKind.PARTS_ONLY
                }
                items += DraftItem(line.evidence.text, match.term.action, operation ?: allowed.first(), kind, line.evidence)
            }
        }
        return items
    }

    // --- kwota ---

    private fun total(lines: List<Line>): Field<Money> {
        fun hits(label: Regex) = lines.filter { label.containsMatchIn(it.norm) && !TOTAL_EXCLUDED.containsMatchIn(it.norm) }
            .mapNotNull { line -> AMOUNT.findAll(line.norm).lastOrNull()?.let { line to parseAmount(it.value) } }
        val strong = hits(TOTAL_STRONG)
        val found = strong.ifEmpty { hits(TOTAL_WEAK) }
        if (found.isEmpty()) return Field(null, FieldState.NOT_FOUND, emptyList())

        val docCurrencies = lines.flatMap { currencies(it.norm) }.distinct()
        val candidates = found.map { (line, minor) ->
            Candidate(Money(minor, currencies(line.norm).singleOrNull() ?: docCurrencies.singleOrNull()), line.evidence)
        }.distinctBy { it.value }
        val value = candidates.singleOrNull()?.value ?: candidates.last().value
        val sure = candidates.size == 1 && strong.isNotEmpty() && value.currency != null
        return Field(value, if (sure) FieldState.RECOGNIZED else FieldState.CHECK, candidates)
    }

    private fun parseAmount(raw: String): Long {
        val decimals = DECIMALS.find(raw)?.groupValues?.get(1)
        val whole = (if (decimals != null) raw.dropLast(3) else raw).filter(Char::isDigit)
        return whole.toLong() * 100 + (decimals?.toLong() ?: 0)
    }

    private fun currencies(norm: String): List<String> = CURRENCIES.filter { it.second.containsMatchIn(norm) }.map { it.first }

    private companion object {
        const val MIN_READABLE_CHARS = 20
        const val HEAD_LINES = 10
        const val MAX_KM = 2_000_000
        const val MAX_HEADING_WORDS = 5
        const val MIN_BARE_KM = 1000
        const val SERVICE_ACTION = "inspection_service"

        val WHITESPACE = Regex("[\\s\\u00A0]+")
        private const val W = "(?<![\\p{L}\\d])"
        private const val E = "(?![\\p{L}\\d])"

        /** Fraza jako całe słowo, z dopuszczeniem krótkiej końcówki fleksyjnej („bremsevæsken”, „filters”). */
        fun phrase(p: String): Regex =
            Regex(W + Regex.escape(p.lowercase(Locale.ROOT)) + "(?:ene|en|et|er|ne|es|n|e|a|s)?" + E)
        fun words(alternatives: String): Regex = Regex("$W(?:$alternatives)$E")

        val QUOTE_DOC = words("tilbud|pristilbud|prisoverslag|kostnadsoverslag|estimate|quote|quotation")
        val INVOICE_DOC = words("faktura|invoice|kvittering|receipt")

        val DATE_ISO = Regex("(?<!\\d)(\\d{4})-(\\d{2})-(\\d{2})(?!\\d)")
        val DATE_NUMERIC = Regex("(?<!\\d)(\\d{1,2})([./-])(\\d{1,2})\\2(\\d{4}|\\d{2})(?!\\d)")
        private const val MONTH = "(jan|feb|mar|apr|mai|may|jun|jul|aug|sep|okt|oct|nov|des|dec)\\p{L}*\\.?"
        val DATE_DAY_MONTHNAME = Regex("(?<!\\d)(\\d{1,2})\\.?\\s+$MONTH,?\\s+(\\d{4})(?!\\d)")
        val DATE_MONTHNAME_DAY = Regex("$W$MONTH\\s+(\\d{1,2})(?:st|nd|rd|th)?,?\\s+(\\d{4})(?!\\d)")
        val MONTHS = mapOf(
            "jan" to "1", "feb" to "2", "mar" to "3", "apr" to "4", "mai" to "5", "may" to "5", "jun" to "6",
            "jul" to "7", "aug" to "8", "sep" to "9", "okt" to "10", "oct" to "10", "nov" to "11", "des" to "12", "dec" to "12",
        )
        val DATE_EXCLUDED = words(
            "forfall\\p{L}*|betalingsfrist|due date|payment due|neste|next|gyldig til|valid until|frist|" +
                "registrert|registered|reg\\.? ?dato|første gang|first reg\\p{L}*|født|levering\\p{L}*|bestilt|ordered",
        )
        val DATE_SERVICE = words(
            "utført|servicedato|service dato|arbeidsdato|verkstedsdato|reparasjonsdato|ferdigdato|ferdig dato|ferdigstilt|innlevert|" +
                "date of service|service date|" +
                "work completed|completed|date of work|work date|carried out",
        )
        val DATE_INVOICE = words("fakturadato|faktura dato|invoice date|kvitteringsdato|dato|date")

        private const val NUM = "\\d{1,3}(?:[ .,]\\d{3})+(?!\\d)|\\d{1,7}"
        private const val ODO_LABEL = "kilometerstand|km[-. ]?stand|km\\.st\\.?|kjørelengde|tellerstand|kjørte km|kilometer|mileage|odometer(?: reading)?"
        val ODO_LABELED = Regex("$W($ODO_LABEL)\\D{0,12}($NUM)\\s*(km|miles|mi)?")
        val ODO_BARE = Regex("${W}km\\s*[:=]\\s*(\\d{1,3}(?:[ .]\\d{3})+|\\d{4,7})(?![.,]?\\d)")
        val ODO_LABEL_ONLY = Regex("(?:$ODO_LABEL|km)\\s*[:.]?")
        val ODO_VALUE_ONLY = Regex("(\\d{1,3}(?:[ .]\\d{3})+|\\d{3,7})\\s*(?:km)?")
        val ODO_UNLABELED = Regex("(?<![\\d.,])($NUM)\\s*km(?![\\p{L}/])")
        val ODO_EXCLUDED = words("neste|next|hver|every|intervall\\p{L}*|interval\\p{L}*|garanti\\p{L}*|warranty|innen|within|etter|after")

        val VIN = Regex("(?<![A-Z0-9])[A-HJ-NPR-Z0-9]{17}(?![A-Z0-9])")
        val PLATE = Regex("(?<![A-Z0-9])[A-Z]{2}[ -]?\\d{5}(?![A-Z0-9])")
        val NOT_PLATE_PREFIX = setOf("KR", "NO", "NR", "PB", "TL", "ID")

        val INSPECT_VERB = words(
            "kontroll|kontrollert|kontrollere|kontrolleres|sjekk|sjekket|sjekke|inspisert|inspeksjon|check|checked|inspect|inspected|inspection",
        )
        val TEST_VERB = words("test|testet|testing|tested|målt|measured")
        val REPLACE_VERB = words(
            "skift|skifte|skiftet|bytt|bytte|byttet|utskiftet|utskifting|erstattet|fornyet|montert|installert|" +
                "replaced|changed|renewed|replace|change|replacement|renewal|fitted|installed",
        )
        val RECOMMENDED = words(
            "bør|må|anbefal\\p{L}*|should|needs?|recommend\\p{L}*|advis\\p{L}*|neste|next|snart|soon|due",
        )
        val NOT_DONE = words("ikke utført|ikke skiftet|ikke byttet|not performed|not done|not replaced|declined|avslått|utsatt|deferred")
        val SERVICE_AT_KM = Regex("${W}service\\s+(?:ved\\s+|at\\s+)?\\d{1,3}(?:[ .]?\\d{3})\\s*km$E")
        val RECOMMENDED_HEADING = words(
            "anbefal\\p{L}*|bør utføres|recommend\\p{L}*|advisor(?:y|ies)|future work|ved neste service|at next service",
        )
        val PERFORMED_HEADING = words(
            "utført arbeid|arbeid utført|utførte arbeider|work performed|work carried out|work done|work completed|" +
                "deler og arbeid|parts and labou?r|spesifikasjon",
        )

        val TOTAL_STRONG = words(
            "å betale|til betaling|amount due|total due|balance due|sum inkl\\p{L}*\\.?|total inkl\\p{L}*\\.?|" +
                "totalt inkl\\p{L}*\\.?|total incl?\\p{L}*\\.?|grand total|totalsum(?: inkl\\p{L}*\\.?)?|totalbeløp|" +
                "fakturabeløp|fakturasum|sluttsum|sluttbeløp|invoice total|total amount",
        )
        val TOTAL_WEAK = words("totalt|total|sum|beløp")
        val TOTAL_EXCLUDED = words("eks\\p{L}*\\.?|excl\\p{L}*\\.?|ex vat|subtotal|sub total|netto|net|mva-grunnlag|herav mva|vat amount")
        val AMOUNT = Regex("(?<![\\d.,])(?:\\d{1,3}(?:[ .,]\\d{3})+|\\d+)(?:[.,]\\d{2})?(?!\\d)")
        val DECIMALS = Regex("[.,](\\d{2})$")
        val CURRENCIES = listOf(
            "NOK" to Regex("${W}(?:nok|kr)$E|,-"),
            "EUR" to Regex("${W}eur$E|€"),
            "GBP" to Regex("${W}gbp$E|£"),
            "USD" to Regex("${W}usd$E|\\$"),
            "SEK" to Regex("${W}sek$E"),
            "DKK" to Regex("${W}dkk$E"),
        )
    }
}
