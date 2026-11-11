package app.servicepassport.extraction

/**
 * Słownik NO/EN oparty na wspólnym kontrakcie czynności (`service-contract`, ServiceTask.kt):
 * te same kody czynności i te same dozwolone operacje. Zgodność pilnuje test porównujący ten słownik
 * z plikiem kontraktu. To słownik językowy, nie źródło zaleceń serwisowych.
 */
public object DefaultTaskDictionary {
    private const val REPLACE = "replace"
    private const val INSPECT = "inspect"
    private const val TEST = "test"

    private fun part(action: String, replace: List<String>, parts: List<String>, unless: List<String> = emptyList()) =
        TaskTerm(action, listOf(REPLACE, INSPECT), mapOf(REPLACE to replace), parts, unless)

    /** Inne oleje i opłaty: linia o nich nigdy nie dotyczy oleju silnikowego, cokolwiek jeszcze zawiera. */
    private val notEngineOil = listOf(
        "girkasse", "gir", "automatgir", "automatkasse", "automat", "differensial", "diff", "bakaksel", "foraksel", "aksel", "haldex",
        "fordelerkasse", "vinkelgir", "servo", "servostyring", "hydraulikk", "kompressor", "aircondition", "klimaanlegg",
        "miljøavgift", "miljøgebyr", "avgift", "gebyr",
        "gearbox", "transmission", "differential", "axle", "power steering", "hydraulic", "compressor", "disposal", "environmental",
    )

    public val terms: List<TaskTerm> = listOf(
        part(
            "engine_oil",
            listOf("oljeskift", "olje- og filterskift", "olje og filterskift", "oljeservice", "motoroljeskift", "skift av motorolje", "skift av olje", "oil change", "oil service", "oil and filter change"),
            listOf("motorolje", "olje", "oljenivå", "engine oil", "motor oil", "oil level"),
            notEngineOil,
        ),
        part("oil_filter", listOf("skift av oljefilter", "oil filter change"), listOf("oljefilter", "oil filter")),
        part(
            "gearbox_oil",
            listOf(
                "skift av girolje", "girkasseoljeskift", "giroljeskift", "oljeskift girkasse", "oljeskift på girkasse", "oljeskift automatgir",
                "oljeskift automatkasse", "gearbox oil change", "transmission fluid change",
            ),
            listOf(
                "girolje", "girkasseolje", "automatgirolje", "automatolje", "dsg-olje", "olje girkasse", "olje i girkasse", "olje til girkasse",
                "olje automatgir", "olje i automatgir", "olje automatkasse", "atf", "gearbox oil", "transmission fluid", "transmission oil",
            ),
        ),
        part(
            "brake_fluid",
            listOf("skift av bremsevæske", "bytte av bremsevæske", "bremsevæskeskift", "brake fluid change", "brake fluid replacement", "brake fluid service"),
            listOf("bremsevæske", "brake fluid"),
        ),
        part(
            "coolant",
            listOf("skift av kjølevæske", "coolant change", "coolant replacement"),
            listOf("kjølevæske", "frostvæske", "coolant", "antifreeze"),
        ),
        part("spark_plugs", listOf("skift av tennplugger", "spark plug replacement"), listOf("tennplugg", "tennplugger", "spark plug")),
        part(
            "timing_belt",
            listOf("registerreimskift", "skift av registerreim", "tannreimskift", "skift av tannreim", "timing belt replacement", "cambelt replacement"),
            listOf("registerreim", "registerreimsett", "tannreim", "tannreimsett", "kamreim", "timing belt", "cambelt"),
        ),
        part("air_filter", listOf("skift av luftfilter", "air filter change"), listOf("luftfilter", "air filter")),
        part(
            "cabin_filter",
            listOf("skift av kupefilter", "skift av pollenfilter", "cabin filter change"),
            listOf("kupefilter", "kupéfilter", "kupeluftfilter", "pollenfilter", "friskluftfilter", "kabinfilter", "cabin filter", "pollen filter"),
        ),
        part("fuel_filter", listOf("skift av drivstoffilter", "fuel filter change"), listOf("drivstoffilter", "drivstofffilter", "dieselfilter", "bensinfilter", "brennstoffilter", "fuel filter", "diesel filter")),
        part("brake_pads", listOf("skift av bremseklosser", "brake pad replacement"), listOf("bremsekloss", "bremseklosser", "bremsebelegg", "klosser", "brake pad")),
        part("brake_discs", listOf("skift av bremseskiver", "brake disc replacement"), listOf("bremseskive", "bremseskiver", "brake disc", "brake rotor")),
        TaskTerm(
            "inspection_service", listOf(INSPECT),
            mapOf(INSPECT to listOf(
                "årsservice", "hovedservice", "mellomservice", "inspeksjonsservice", "serviceinspeksjon", "stor service", "liten service", "årlig service",
                "km service", "km-service", "service iht", "service i henhold til",
                "annual service", "full service", "interim service", "major service", "inspection service", "scheduled service",
            )),
        ),
        TaskTerm(
            "brakes", listOf(INSPECT),
            mapOf(INSPECT to listOf(
                "bremsekontroll", "kontroll av bremser", "kontroll bremser", "kontroll av bremseanlegg", "bremsesjekk", "sjekk av bremser", "sjekk bremser",
                "bremsetest", "brake inspection", "brake check", "brake test",
            )),
        ),
        TaskTerm(
            "battery_12v", listOf(TEST, REPLACE),
            mapOf(
                REPLACE to listOf("batteribytte", "skift av batteri", "bytte av batteri", "battery replacement"),
                TEST to listOf("batteritest", "test av batteri", "batterisjekk", "batterikontroll", "battery test"),
            ),
            // Samo „batteri” celowo nie wystarcza: bateria w kluczyku albo akumulator trakcyjny to nie akumulator 12 V.
            listOf("startbatteri", "bilbatteri", "12v batteri", "12 v batteri", "batteri 12v", "batteri 12 v", "12v battery", "starter battery", "car battery"),
        ),
    )
}
