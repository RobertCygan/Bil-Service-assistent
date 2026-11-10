package app.servicepassport.engine

public sealed interface PlanResolution {
    public data class Assigned(val plan: ServicePlan) : PlanResolution

    /**
     * „Brak potwierdzonego harmonogramu”. [missingAttributes] wskazuje dane, których uzupełnienie
     * mogłoby dać dopasowanie; pusty zbiór = konfiguracja nie jest obsługiwana.
     */
    public data class NoVerifiedSchedule(val missingAttributes: Set<ConfigAttribute>) : PlanResolution

    /** Kilka planów pasuje jednocześnie — błąd katalogu; nie wybieramy „najbardziej podobnego”. */
    public data class Ambiguous(val planIds: List<String>) : PlanResolution
}

public object PlanResolver {

    /** Przypisuje plan wyłącznie przy pełnym dopasowaniu wszystkich cech z zakresu planu. */
    public fun resolve(config: Map<ConfigAttribute, String>, catalog: List<ServicePlan>): PlanResolution {
        val matches = mutableListOf<ServicePlan>()
        val missing = sortedSetOf<ConfigAttribute>()
        for (plan in catalog) {
            val planMissing = mutableSetOf<ConfigAttribute>()
            var mismatch = false
            for ((attribute, allowed) in plan.scope) {
                val value = config[attribute]?.norm()
                when {
                    value.isNullOrEmpty() -> planMissing += attribute
                    allowed.none { it.norm() == value } -> mismatch = true
                }
            }
            if (mismatch) continue
            if (planMissing.isEmpty()) matches += plan else missing += planMissing
        }
        return when (matches.size) {
            1 -> PlanResolution.Assigned(matches.single())
            0 -> PlanResolution.NoVerifiedSchedule(missing)
            else -> PlanResolution.Ambiguous(matches.map { it.id })
        }
    }

    private fun String.norm() = trim().uppercase()
}
