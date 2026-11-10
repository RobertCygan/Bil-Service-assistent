package app.servicepassport.export

import java.time.LocalDate
import java.time.format.DateTimeParseException

public enum class ExportIssueCode {
    DUPLICATE_ID, UNKNOWN_VEHICLE, UNKNOWN_DOCUMENT, UNKNOWN_EVENT, UNKNOWN_READING,
    INVALID_DATE, DATE_IN_FUTURE, VALUE_OUT_OF_RANGE, EMPTY_FIELD,
    EVENT_WITHOUT_ACTIONS, ACTION_INVALID, EVENT_WITH_SEVERAL_READINGS,
    RULE_WITHOUT_INTERVAL, INDICATOR_WITHOUT_VALUE,
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

        fun range(value: Long?, where: String) { if (value != null && value !in 0..Int.MAX_VALUE.toLong()) add(ExportIssueCode.VALUE_OUT_OF_RANGE, where) }

        val vehicles = data.vehicles.map { it.id }.toSet()
        val documents = data.documents.map { it.id }.toSet()
        val eventOwner = data.serviceEvents.associate { it.id to it.vehicleId }
        val readingOwner = data.odometerReadings.associate { it.id to it.vehicleId }
        fun vehicle(id: String, where: String) { if (id !in vehicles) add(ExportIssueCode.UNKNOWN_VEHICLE, where) }

        unique(data.vehicles.map { it.id }, "vehicle")
        unique(data.odometerReadings.map { it.id }, "reading")
        unique(data.serviceEvents.map { it.id }, "event")
        unique(data.serviceEvents.flatMap { e -> e.actions.map { it.id } }, "action")
        unique(data.documents.map { it.id }, "document")
        unique(data.userRules.map { it.id }, "rule")

        for (v in data.vehicles) {
            val where = "vehicle:${v.id}"
            blank(v.id, "vehicle.id"); blank(v.displayName, "$where.displayName")
            date(v.firstRegistrationDate, "$where.firstRegistrationDate")
            if (v.modelYear != null && v.modelYear !in 1900..today.year + 1) add(ExportIssueCode.VALUE_OUT_OF_RANGE, "$where.modelYear")
            // Wybór bieżącego odczytu musi wskazywać odczyt tego samego auta (jak klucz obcy w bazie).
            if (v.currentReadingId != null && readingOwner[v.currentReadingId] != v.id) add(ExportIssueCode.UNKNOWN_READING, "$where.currentReadingId")
        }
        for (r in data.odometerReadings) {
            val where = "reading:${r.id}"
            blank(r.id, "reading.id"); vehicle(r.vehicleId, where); date(r.date, "$where.date"); range(r.km, "$where.km")
            if (r.eventId != null && eventOwner[r.eventId] != r.vehicleId) add(ExportIssueCode.UNKNOWN_EVENT, where)
        }
        data.odometerReadings.mapNotNull { it.eventId }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            .forEach { add(ExportIssueCode.EVENT_WITH_SEVERAL_READINGS, "event:$it") }
        for (e in data.serviceEvents) {
            val where = "event:${e.id}"
            blank(e.id, "event.id"); vehicle(e.vehicleId, where); date(e.date, "$where.date")
            if (e.costMinor != null && e.costMinor < 0) add(ExportIssueCode.VALUE_OUT_OF_RANGE, "$where.costMinor")
            if (e.actions.isEmpty()) add(ExportIssueCode.EVENT_WITHOUT_ACTIONS, where)
            for (a in e.actions) {
                blank(a.id, "$where.action.id")
                val coded = !a.actionCode.isNullOrBlank() && !a.operationCode.isNullOrBlank() && a.customLabel == null
                val custom = a.actionCode == null && a.operationCode == null && !a.customLabel.isNullOrBlank()
                if (!coded && !custom) add(ExportIssueCode.ACTION_INVALID, "$where.action:${a.id}")
            }
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
            blank(r.id, "rule.id"); vehicle(r.vehicleId, where); blank(r.actionCode, "$where.actionCode"); blank(r.operationCode, "$where.operationCode")
            if (r.km == null && r.months == null) add(ExportIssueCode.RULE_WITHOUT_INTERVAL, where)
            if ((r.km ?: 1) <= 0 || (r.months ?: 1) <= 0) add(ExportIssueCode.VALUE_OUT_OF_RANGE, where)
        }
        data.indicatorReadings.forEachIndexed { i, r ->
            val where = "indicator[$i]"
            vehicle(r.vehicleId, where); date(r.date, "$where.date"); blank(r.taskCode, "$where.taskCode"); range(r.odometerKm.toLong(), "$where.odometerKm")
            if (r.remainingKm == null && r.remainingDays == null) add(ExportIssueCode.INDICATOR_WITHOUT_VALUE, where)
        }
        return issues
    }
}
