package app.servicepassport.export

import kotlinx.serialization.Serializable

/**
 * Format eksportu, wersja 1. Archiwum ZIP:
 *
 *     manifest.json            — wersja formatu i lista plików ze skrótami SHA-256
 *     data.json                — [ExportData]
 *     documents/<nazwa>        — oryginalne zdjęcia i PDF dodane przez użytkownika
 *
 * Do eksportu NIE trafiają: reguły z licencjonowanego katalogu, surowy wynik OCR, szkice
 * i miniatury (da się je odtworzyć albo nie wolno ich rozpowszechniać). Daty to ISO-8601 (RRRR-MM-DD).
 * Plik zawiera dane prywatne i nie jest szyfrowany.
 */
public object ExportFormat {
    public const val VERSION: Int = 1
    public const val MANIFEST: String = "manifest.json"
    public const val DATA: String = "data.json"
    public const val DOCUMENTS_DIR: String = "documents/"
}

@Serializable
public data class ExportManifest(
    val formatVersion: Int,
    /** Chwila utworzenia, ISO-8601 z czasem. */
    val createdAt: String,
    val appVersion: String,
    val files: List<ExportedFile>,
)

@Serializable
public data class ExportedFile(val path: String, val sha256: String, val bytes: Long)

/**
 * Treść kopii. Pola odpowiadają schematowi bazy aplikacji w wersji 4 (tabele `vehicles`, `service_events`,
 * `service_actions`, `odometer_readings`, `current_odometer`, `user_intervals`, `indicator_readings`), tak aby kopia była bezstratna:
 * identyfikatory, rewizje, znaczniki czasu, jawny wybór bieżącego odczytu i wyjaśnienia anomalii są zachowane.
 * Dni (`...Date`) to ISO-8601; znaczniki `...AtUtc` to milisekundy UTC, jak w bazie.
 */
@Serializable
public data class ExportData(
    val vehicles: List<ExportVehicle> = emptyList(),
    val odometerReadings: List<ExportOdometerReading> = emptyList(),
    val serviceEvents: List<ExportServiceEvent> = emptyList(),
    /** Tabela, której aplikacja jeszcze nie ma. */
    val documents: List<ExportDocument> = emptyList(),
    val userRules: List<ExportUserRule> = emptyList(),
    val indicatorReadings: List<ExportIndicatorReading> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
)

@Serializable
public data class ExportVehicle(
    val id: String,
    /** Jedyne pole opisowe wymagane przez aplikację; marka i model są opcjonalne. */
    val displayName: String,
    val modelYear: Int? = null,
    val firstRegistrationDate: String? = null,
    val make: String? = null,
    val model: String? = null,
    val generation: String? = null,
    val vin: String? = null,
    val registrationNumber: String? = null,
    val engineCode: String? = null,
    val gearboxType: String? = null,
    val gearboxCode: String? = null,
    val powertrain: String? = null,
    val market: String? = null,
    val serviceRegime: String? = null,
    val usageProfile: String? = null,
    val configurationRevision: Long = 1,
    val createdAtUtc: Long = 0,
    val updatedAtUtc: Long = 0,
    /** Jawnie wybrany bieżący odczyt (`current_odometer`); null = brak wyboru. Nigdy nie wyliczany. */
    val currentReadingId: String? = null,
)

@Serializable
public data class ExportOdometerReading(
    val id: String,
    val vehicleId: String,
    /** Wpis serwisowy, z którym odczyt jest związany; null = samodzielny odczyt. */
    val eventId: String? = null,
    val date: String,
    val km: Long,
    /** Wyjaśnienie użytkownika przy niespójnym odczycie; pusty tekst = brak. */
    val exceptionReason: String = "",
    val revision: Long = 1,
    val createdAtUtc: Long = 0,
    val updatedAtUtc: Long = 0,
)

@Serializable
public data class ExportServiceEvent(
    val id: String,
    val vehicleId: String,
    val date: String,
    val costMinor: Long? = null,
    val currency: String? = null,
    val note: String = "",
    val revision: Long = 1,
    val createdAtUtc: Long = 0,
    val updatedAtUtc: Long = 0,
    /** Kolejność na liście = kolumna `position`. */
    val actions: List<ExportAction>,
    val documentIds: List<String> = emptyList(),
)

/** Albo para kodów ze wspólnego kontraktu, albo własny opis użytkownika — nigdy oba naraz. */
@Serializable
public data class ExportAction(
    val id: String,
    val actionCode: String? = null,
    val operationCode: String? = null,
    val customLabel: String? = null,
)

@Serializable
public data class ExportDocument(
    val id: String,
    val vehicleId: String,
    /** Ścieżka w archiwum, zawsze pod `documents/`. */
    val path: String,
    val mimeType: String,
    val addedOn: String,
)

/** Własny interwał użytkownika (tabela `user_intervals`). */
@Serializable
public data class ExportUserRule(
    val id: String,
    val vehicleId: String,
    val actionCode: String,
    val operationCode: String,
    val km: Int? = null,
    val months: Int? = null,
    val revision: Long = 1,
    val createdAtUtc: Long = 0,
    val updatedAtUtc: Long = 0,
)

/** Odczyt wskaźnika serwisowego z wyświetlacza auta (tabela `indicator_readings`). */
@Serializable
public data class ExportIndicatorReading(
    val id: String,
    val vehicleId: String,
    val taskCode: String,
    val date: String,
    val odometerKm: Int,
    val remainingKm: Int? = null,
    val remainingDays: Int? = null,
    val revision: Long = 1,
    val createdAtUtc: Long = 0,
    val updatedAtUtc: Long = 0,
)
