package app.servicepassport.android.reminders

import app.servicepassport.android.dashboard.DashboardEvaluator
import app.servicepassport.android.dashboard.DashboardReason
import app.servicepassport.android.dashboard.DashboardTask
import app.servicepassport.android.data.GarageDatabase
import app.servicepassport.contract.AppPolicy
import app.servicepassport.engine.EngineSettings
import app.servicepassport.engine.Reason
import app.servicepassport.reminders.DeliveredReminder
import app.servicepassport.reminders.Reminder
import app.servicepassport.reminders.ReminderPlan
import app.servicepassport.reminders.ReminderPlanner
import app.servicepassport.reminders.ReminderSubject
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shows and removes notifications. Implemented by Android in production and by a fake in tests. */
interface ReminderNotifier {
    /** False when the system or the driver does not allow notifications. Nothing is recorded as shown then. */
    fun canPost(): Boolean
    fun post(reminder: Reminder, task: DashboardTask?, vehicleName: String, odometerAgeDays: Long?)
    fun cancel(key: String)
}

/**
 * Turns the dashboard result into notifications. The dashboard's final category is the only input,
 * so a notification can never say something the dashboard does not.
 */
class ReminderCoordinator(
    private val database: GarageDatabase,
    private val store: ReminderStore,
    private val notifier: ReminderNotifier,
    private val evaluator: DashboardEvaluator = DashboardEvaluator(),
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    /**
     * @param mayNotify false while the app is open: stale notifications are removed, new ones wait for the background run
     * @return the plan, or null when there is no vehicle or the dashboard cannot be calculated
     */
    suspend fun sync(mayNotify: Boolean): ReminderPlan? = withContext(Dispatchers.IO) {
        val dao = database.vehicles()
        val snapshot = dao.vehicleIds().firstOrNull()?.let { dao.garage(it) }
        if (snapshot == null) {
            store.delivered().forEach { notifier.cancel(it.key) }
            store.forgetAll()
            return@withContext null
        }
        val today = LocalDate.now(clock)
        val dashboard = try { evaluator.evaluate(snapshot, today) } catch (_: RuntimeException) { return@withContext null }
        val tasks = dashboard.tasks.associateBy { it.id }
        val subjects = dashboard.tasks.map(::subject)
        val plan = ReminderPlanner.plan(
            snapshot.vehicle.id, subjects, today, store.settings, store.delivered(),
            EngineSettings(AppPolicy.DUE_SOON_KM, AppPolicy.DUE_SOON_DAYS, AppPolicy.ODOMETER_STALE_AFTER_DAYS),
        )
        plan.cancel.forEach(notifier::cancel)
        store.forget(plan.cancel)
        if (mayNotify && notifier.canPost()) {
            val age = ReminderPlanner.odometerAgeDays(subjects.filter { it.odometerNeedsAttention }, today)
            for (reminder in plan.notifyNow) {
                notifier.post(reminder, reminder.subject?.let { tasks[it.id] }, snapshot.vehicle.displayName, age)
                store.record(DeliveredReminder(reminder.key, reminder.fingerprint, today))
            }
        }
        plan
    }

    private fun subject(task: DashboardTask) = ReminderSubject(
        id = task.id,
        category = task.category,
        dueDate = task.dueDate,
        dueKm = task.dueKm,
        odometerUsed = task.basis?.odometerUsed,
        odometerNeedsAttention = ODOMETER_ATTENTION.any { it in task.reasons },
        doubt = Reason.SOURCE_EXPIRED.takeIf { DashboardReason.SOURCE_EXPIRED in task.reasons },
    )

    private companion object {
        val ODOMETER_ATTENTION = setOf(
            DashboardReason.ODOMETER_MISSING, DashboardReason.ODOMETER_STALE, DashboardReason.ODOMETER_UPDATE_REQUIRED,
            DashboardReason.ODOMETER_INCONSISTENT, DashboardReason.NEWER_ODOMETER_IN_HISTORY,
        )
    }
}
