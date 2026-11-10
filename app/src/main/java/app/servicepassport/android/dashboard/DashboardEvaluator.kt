package app.servicepassport.android.dashboard

import app.servicepassport.android.data.GarageSnapshot
import app.servicepassport.android.integration.*
import app.servicepassport.contract.ServiceTask
import app.servicepassport.engine.*
import java.time.LocalDate
import java.time.YearMonth

/** Supplied by a reviewed catalogue integration, never by a driver form or OCR. */
data class SourceAuthorization(
    val sourceId: String,
    val sourceVersion: String,
    val evidenceReference: String,
    val validFrom: LocalDate,
    val validThrough: LocalDate? = null,
    val withdrawn: Boolean = false,
)

data class ManufacturerCatalog(
    val plans: List<ServicePlan> = emptyList(),
    val authorizations: List<SourceAuthorization> = emptyList(),
)

/** No licensed manufacturer data has been installed. Driver intervals are stored separately. */
object InstalledManufacturerCatalog { val value = ManufacturerCatalog() }

enum class CatalogState { NOT_AVAILABLE, NO_MATCH, CONFIGURATION_MISSING, AMBIGUOUS, SOURCE_UNAVAILABLE, INVALID_CATALOG, MATCHED }
enum class TaskOrigin { USER, MANUFACTURER }
enum class DashboardReason {
    CONFIRMED_ACTION_MISSING, ODOMETER_MISSING, LAST_SERVICE_ODOMETER_MISSING, IN_SERVICE_DATE_MISSING,
    VARIABLE_SCHEDULE, ODOMETER_STALE, ODOMETER_UPDATE_REQUIRED, ODOMETER_INCONSISTENT,
    NEWER_ODOMETER_IN_HISTORY, SOURCE_EXPIRED, FUTURE_ENTRIES_IGNORED, CALCULATION_LIMIT,
}

data class DashboardTask(
    val id: String,
    val task: ServiceTask,
    val origin: TaskOrigin,
    val category: Category,
    val dueKm: Int?,
    val dueDate: LocalDate?,
    val remainingKm: Int?,
    val remainingDays: Long?,
    val estimatedMonth: YearMonth?,
    val basis: Basis?,
    val source: SourceRef,
    val reasons: Set<DashboardReason>,
)

data class DashboardState(
    val evaluatedOn: LocalDate,
    val tasks: List<DashboardTask> = emptyList(),
    val catalogState: CatalogState = CatalogState.NOT_AVAILABLE,
    val missingConfiguration: Set<ConfigAttribute> = emptySet(),
    val calculationUnavailable: Boolean = false,
)

class DashboardEvaluator(private val catalog: ManufacturerCatalog = InstalledManufacturerCatalog.value) {
    fun evaluate(snapshot: GarageSnapshot, today: LocalDate): DashboardState {
        val facts = EngineFactsMapper.prepare(snapshot)
        val (catalogState, plan, missing) = resolveCatalog(facts, today)
        val tasks = mutableListOf<DashboardTask>()
        plan?.rules?.forEach { rule ->
            tasks += evaluateRule("manufacturer:${plan.id}:${rule.id}", rule, TaskOrigin.MANUFACTURER, facts, today)
        }
        snapshot.intervals.forEach { interval ->
            require(interval.vehicleId == facts.vehicleId)
            val task = requireNotNull(ServiceTask.fromTaskCode("${interval.actionCode}:${interval.operationCode}"))
            val source = SourceRef("user:${interval.id}", "User interval", interval.revision.toString(), today, null, DataOrigin.USER_DEFINED, "Entered by the vehicle owner")
            val rule = ServiceRule("user:${interval.id}", task.taskCode, Schedule.Fixed(Interval(interval.distanceKm, interval.months)), source)
            tasks += evaluateRule(rule.id, rule, TaskOrigin.USER, facts, today)
        }
        return DashboardState(today, tasks.sortedWith(compareBy<DashboardTask> { it.category.ordinal }.thenBy { it.dueDate ?: LocalDate.MAX }.thenBy { it.remainingKm ?: Int.MAX_VALUE }.thenBy { it.task.taskCode }.thenBy { it.id }), catalogState, missing)
    }

    private data class CatalogResolution(val state: CatalogState, val plan: ServicePlan? = null, val missing: Set<ConfigAttribute> = emptySet())

    private fun resolveCatalog(facts: PreparedEngineFacts, today: LocalDate): CatalogResolution {
        if (catalog.plans.isEmpty()) return CatalogResolution(CatalogState.NOT_AVAILABLE)
        if (CatalogValidator.productionIssues(catalog.plans, today).isNotEmpty()) return CatalogResolution(CatalogState.INVALID_CATALOG)
        return when (val resolution = PlanResolver.resolve(EngineAdapter.configuration(facts), catalog.plans)) {
            is PlanResolution.NoVerifiedSchedule -> CatalogResolution(if (resolution.missingAttributes.isEmpty()) CatalogState.NO_MATCH else CatalogState.CONFIGURATION_MISSING, missing = resolution.missingAttributes)
            is PlanResolution.Ambiguous -> CatalogResolution(CatalogState.AMBIGUOUS)
            is PlanResolution.Assigned -> {
                val plan = resolution.plan
                val sources = listOf(plan.source) + plan.rules.map { it.source }
                if (sources.any { !authorized(it, today) } || plan.rules.any { ServiceTask.fromTaskCode(it.taskCode) == null }) CatalogResolution(CatalogState.SOURCE_UNAVAILABLE)
                else CatalogResolution(CatalogState.MATCHED, plan)
            }
        }
    }

    private fun authorized(source: SourceRef, today: LocalDate): Boolean =
        source.origin == DataOrigin.VERIFIED_SOURCE && !source.verifiedOn.isAfter(today) && source.validUntil?.isBefore(today) != true &&
            catalog.authorizations.any { it.sourceId == source.id && it.sourceVersion == source.version && it.evidenceReference.isNotBlank() && !it.withdrawn && !it.validFrom.isAfter(today) && it.validThrough?.isBefore(today) != true }

    private fun evaluateRule(id: String, rule: ServiceRule, origin: TaskOrigin, facts: PreparedEngineFacts, today: LocalDate): DashboardTask {
        val task = requireNotNull(ServiceTask.fromTaskCode(rule.taskCode))
        val last = facts.performedActions.filter { it.task == task && !it.performedOn.isAfter(today) }.maxWithOrNull(compareBy<PreparedPerformedAction> { it.performedOn }.thenBy { it.odometerKm ?: -1 })
        fun unavailable(reason: DashboardReason) = DashboardTask(id, task, origin, Category.NEEDS_DATA, null, null, null, null, null, null, rule.source, setOf(reason))
        // A driver's repeat interval does not authorize a fictional first service at vehicle registration/0 km.
        if (origin == TaskOrigin.USER && last == null) return unavailable(DashboardReason.CONFIRMED_ACTION_MISSING)
        if (last == null && facts.inServiceDate?.isAfter(today) == true) return unavailable(DashboardReason.IN_SERVICE_DATE_MISSING)
        val interval = when (val schedule = rule.schedule) { is Schedule.Fixed -> if (last == null) schedule.first ?: schedule.repeat else schedule.repeat; is Schedule.Variable -> schedule.upperLimit }
        val anchorKm = last?.odometerKm ?: if (last == null) 0 else null
        val intervalKm = interval?.km
        if (intervalKm != null && anchorKm != null && anchorKm.toLong() + intervalKm.toLong() > Int.MAX_VALUE) return unavailable(DashboardReason.CALCULATION_LIMIT)
        val raw = ServiceEngine.evaluate(EngineAdapter.input(facts, today, listOf(rule))).single()
        // Exhaustive on purpose: a new engine reason must fail compilation here, not crash the dashboard at runtime.
        val reasons = raw.reasons.mapTo(linkedSetOf()) { reason -> when (reason) {
            Reason.NO_CONFIRMATION_IN_DOCUMENTS -> DashboardReason.CONFIRMED_ACTION_MISSING
            Reason.ODOMETER_MISSING -> DashboardReason.ODOMETER_MISSING
            Reason.LAST_SERVICE_ODOMETER_MISSING -> DashboardReason.LAST_SERVICE_ODOMETER_MISSING
            Reason.IN_SERVICE_DATE_MISSING -> DashboardReason.IN_SERVICE_DATE_MISSING
            Reason.VARIABLE_SCHEDULE -> DashboardReason.VARIABLE_SCHEDULE
            Reason.ODOMETER_STALE -> DashboardReason.ODOMETER_STALE
            Reason.ODOMETER_UPDATE_REQUIRED -> DashboardReason.ODOMETER_UPDATE_REQUIRED
            Reason.ODOMETER_INCONSISTENT -> DashboardReason.ODOMETER_INCONSISTENT
            Reason.NEWER_ODOMETER_IN_HISTORY -> DashboardReason.NEWER_ODOMETER_IN_HISTORY
            Reason.SOURCE_EXPIRED -> DashboardReason.SOURCE_EXPIRED
            Reason.FUTURE_ENTRIES_IGNORED -> DashboardReason.FUTURE_ENTRIES_IGNORED
            Reason.CALCULATION_LIMIT -> DashboardReason.CALCULATION_LIMIT
        } }
        val distanceBased = interval?.km != null || rule.schedule is Schedule.Variable && raw.dueKm != null
        val unreliableCounter = facts.currentOdometer?.exceptionReason?.isNotBlank() == true ||
            OdometerCaution.DECREASING_VALUES in facts.odometerCautions || OdometerCaution.DIFFERENT_VALUES_ON_SAME_DAY in facts.odometerCautions ||
            Reason.ODOMETER_INCONSISTENT in raw.reasons
        // Mileage only grows: a limit already passed at the selected reading stays passed when a later, higher
        // reading exists. Hiding it would understate a certain overdue item.
        val provenOverdueByDistance = !unreliableCounter && (raw.remainingKm ?: 0) < 0
        val uncertainDistance = distanceBased && (unreliableCounter || Reason.NEWER_ODOMETER_IN_HISTORY in raw.reasons && !provenOverdueByDistance)
        if (uncertainDistance) reasons += DashboardReason.ODOMETER_INCONSISTENT
        val missingDistance = distanceBased && (raw.basis.odometerUsed == null || raw.dueKm == null)
        val staleBelowThreshold = distanceBased && Reason.ODOMETER_STALE in raw.reasons && (raw.remainingKm ?: Int.MAX_VALUE) >= 0
        val remainingDays = raw.remainingDays
        val timeProvesUrgency = raw.basis.anchorKind != AnchorKind.VEHICLE_START && remainingDays != null && remainingDays <= facts.policy.dueSoonDays
        val distanceNeedsData = uncertainDistance || missingDistance || staleBelowThreshold || Reason.ODOMETER_UPDATE_REQUIRED in raw.reasons
        val category = when {
            Reason.SOURCE_EXPIRED in raw.reasons -> Category.NEEDS_DATA
            distanceNeedsData && timeProvesUrgency -> if (requireNotNull(raw.remainingDays) < 0) Category.OVERDUE else Category.DUE_SOON
            distanceNeedsData -> Category.NEEDS_DATA
            else -> raw.category
        }
        val hideDistance = uncertainDistance || missingDistance
        val suppressEstimate = distanceNeedsData || category == Category.NEEDS_DATA || Reason.SOURCE_EXPIRED in raw.reasons
        return DashboardTask(id, task, origin, category,
            if (hideDistance) null else raw.dueKm, raw.dueDate,
            if (hideDistance) null else raw.remainingKm, raw.remainingDays,
            if (suppressEstimate) null else raw.estimatedKmDueMonth,
            raw.basis.let { if (distanceNeedsData && timeProvesUrgency) it.copy(decidedBy = Dimension.TIME) else it },
            rule.source, reasons)
    }
}
