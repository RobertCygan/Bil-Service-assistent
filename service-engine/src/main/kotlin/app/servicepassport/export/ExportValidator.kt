package app.servicepassport.export

import java.time.LocalDate
import java.time.format.DateTimeParseException

public enum class ExportIssueCode {
    DUPLICATE_ID, UNKNOWN_VEHICLE, UNKNOWN_DOCUMENT, INVALID_DATE, DATE_IN_FUTURE, NEGATIVE_VALUE,
    EMPTY_FIELD, ENTRY_WITHOUT_TASKS, RULE_WITHOUT_INTERVAL, INDICATOR_WITHOUT_VALUE,
    DOCUMENT_PATH_INVALID, DOCUMENT_FILE_MISSING, DUPLICATE_DOCUMENT_PATH,
}

public data class ExportIssue(val code: ExportIssueCode, val where: String)

/** Spójność treści eksportu. Uruchamiana przed zapisem archiwum i przed odtworzeniem danych. */
public object ExportValidator {

    public fun validate(data: ExportData, today: LocalDate, archivePaths: Set<String>): List<ExportIssue> {
        val issues = mutableListOf<ExportIssue>()
        fun add(code: ExportIssueCode, where: String) { issues += ExportIssue(code, where) }
        fun unique(ids: List<String>, what: String) =
            ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { add(ExportIssueCode.DUPLICATE_ID, "$what:$it") }
        fun date(value: String?, where: String) {
            if (value == null) return
            val parsed = try { LocalDate.parse(value) } catch (_: DateTimeParseException) { null }
            when {
                parsed == null -> add(ExportIssueCode.INVALID_DATE, where)
                parsed.isAfter(today) -> add(ExportIssueCode.DATE_IN_FUTURE, where)
            }
        }
        fun blank(value: String, where: String) { if (value.isBlank()) add(ExportIssueCode.EMPTY_FIELD, where) }
        fun negative(value: Number?, where: String) { if (value != null && value.toLong() < 0) add(ExportIssueCode.NEGATIVE_VALUE, where) }

        val vehicles = data.vehicles.map { it.id }.toSet()
        val documents = data.documents.map { it.id }.toSet()
        fun vehicle(id: String, where: String) { if (id !in vehicles) add(ExportIssueCode.UNKNOWN_VEHICLE, where) }

        unique(data.vehicles.map { it.id }, "vehicle")
        unique(data.serviceEntries.map { it.id }, "entry")
        unique(data.documents.map { it.id }, "document")
        unique(data.userRules.map { it.id }, "rule")

        for (v in data.vehicles) {
            blank(v.id, "vehicle.id"); blank(v.make, "vehicle:${v.id}.make"); blank(v.model, "vehicle:${v.id}.model")
            date(v.inServiceDate, "vehicle:${v.id}.inServiceDate")
        }
        data.odometerReadings.forEachIndexed { i, r ->
            vehicle(r.vehicleId, "odometer[$i]"); date(r.date, "odometer[$i].date"); negative(r.km, "odometer[$i].km")
        }
        for (e in data.serviceEntries) {
            val where = "entry:${e.id}"
            blank(e.id, "entry.id"); vehicle(e.vehicleId, where); date(e.date, "$where.date")
            negative(e.odometerKm, "$where.odometerKm"); negative(e.costMinor, "$where.costMinor")
            if (e.tasks.isEmpty()) add(ExportIssueCode.ENTRY_WITHOUT_TASKS, where)
            e.tasks.forEach { blank(it.taskCode, "$where.taskCode") }
            e.documentIds.filter { it !in documents }.forEach { add(ExportIssueCode.UNKNOWN_DOCUMENT, "$where:$it") }
        }
        data.documents.groupingBy { it.path }.eachCount().filterValues { it > 1 }.keys
            .forEach { add(ExportIssueCode.DUPLICATE_DOCUMENT_PATH, it) }
        for (d in data.documents) {
            val where = "document:${d.id}"
            blank(d.id, "document.id"); vehicle(d.vehicleId, where); date(d.addedOn, "$where.addedOn"); blank(d.mimeType, "$where.mimeType")
            when {
                !ExportArchive.isSafeDocumentPath(d.path) -> add(ExportIssueCode.DOCUMENT_PATH_INVALID, where)
                d.path !in archivePaths -> add(ExportIssueCode.DOCUMENT_FILE_MISSING, where)
            }
        }
        for (r in data.userRules) {
            val where = "rule:${r.id}"
            blank(r.id, "rule.id"); vehicle(r.vehicleId, where); blank(r.taskCode, "$where.taskCode")
            if (r.km == null && r.months == null) add(ExportIssueCode.RULE_WITHOUT_INTERVAL, where)
            if ((r.km ?: 1) <= 0 || (r.months ?: 1) <= 0) add(ExportIssueCode.NEGATIVE_VALUE, where)
        }
        data.indicatorReadings.forEachIndexed { i, r ->
            val where = "indicator[$i]"
            vehicle(r.vehicleId, where); date(r.date, "$where.date"); blank(r.taskCode, "$where.taskCode"); negative(r.odometerKm, "$where.odometerKm")
            if (r.remainingKm == null && r.remainingDays == null) add(ExportIssueCode.INDICATOR_WITHOUT_VALUE, where)
        }
        return issues
    }
}
