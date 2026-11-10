package app.servicepassport.extraction

/**
 * Startowy słownik NO/EN. Kody są TYMCZASOWE — do zastąpienia wspólnym słownikiem czynności
 * z etapów 1–3. To słownik językowy, nie źródło zaleceń serwisowych.
 */
public object DefaultTaskDictionary {
    public val terms: List<TaskTerm> = listOf(
        TaskTerm(
            "ENGINE_OIL",
            listOf("oljeskift", "olje- og filterskift", "olje og filterskift", "skift av motorolje", "skift av olje", "oil change", "oil service", "oil and filter change"),
            listOf("motorolje", "olje", "engine oil", "motor oil"),
        ),
        TaskTerm("OIL_FILTER", listOf("skift av oljefilter", "oil filter change"), listOf("oljefilter", "oil filter")),
        TaskTerm("AIR_FILTER", listOf("skift av luftfilter", "air filter change"), listOf("luftfilter", "air filter")),
        TaskTerm(
            "CABIN_FILTER",
            listOf("skift av kupefilter", "skift av pollenfilter", "cabin filter change"),
            listOf("kupefilter", "kupéfilter", "pollenfilter", "cabin filter", "pollen filter"),
        ),
        TaskTerm(
            "BRAKE_FLUID",
            listOf("skift av bremsevæske", "bytte av bremsevæske", "bremsevæskeskift", "brake fluid change", "brake fluid replacement", "brake fluid service"),
            listOf("bremsevæske", "brake fluid"),
        ),
        TaskTerm("SPARK_PLUGS", listOf("skift av tennplugger", "spark plug replacement"), listOf("tennplugg", "tennplugger", "spark plug")),
        TaskTerm(
            "COOLANT",
            listOf("skift av kjølevæske", "coolant change", "coolant replacement"),
            listOf("kjølevæske", "frostvæske", "coolant", "antifreeze"),
        ),
        TaskTerm(
            "TIMING_BELT",
            listOf("registerreimskift", "skift av registerreim", "timing belt replacement", "cambelt replacement"),
            listOf("registerreim", "registerreimsett", "timing belt", "cambelt"),
        ),
        TaskTerm(
            "GEARBOX_OIL",
            listOf("skift av girolje", "girkasseoljeskift", "gearbox oil change", "transmission fluid change"),
            listOf("girolje", "girkasseolje", "dsg-olje", "gearbox oil", "transmission fluid", "transmission oil"),
        ),
        TaskTerm("FUEL_FILTER", listOf("skift av drivstoffilter", "fuel filter change"), listOf("drivstoffilter", "drivstoffilter", "dieselfilter", "fuel filter")),
        TaskTerm("BRAKE_PADS", listOf("skift av bremseklosser", "brake pad replacement"), listOf("bremsekloss", "bremseklosser", "brake pad")),
        TaskTerm("BRAKE_DISCS", listOf("skift av bremseskiver", "brake disc replacement"), listOf("bremseskive", "bremseskiver", "brake disc", "brake rotor")),
        TaskTerm(
            "INSPECTION_SERVICE",
            listOf("årsservice", "hovedservice", "mellomservice", "inspeksjonsservice", "annual service", "full service", "interim service", "major service", "inspection service"),
        ),
    )
}
