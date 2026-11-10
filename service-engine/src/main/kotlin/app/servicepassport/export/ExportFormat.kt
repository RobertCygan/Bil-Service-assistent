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

@Serializable
public data class ExportData(
    val vehicles: List<ExportVehicle> = emptyList(),
    val odometerReadings: List<ExportOdometerReading> = emptyList(),
    val serviceEntries: List<ExportServiceEntry> = emptyList(),
    val documents: List<ExportDocument> = emptyList(),
    /** Wyłącznie interwały ustawione przez użytkownika. */
    val userRules: List<ExportUserRule> = emptyList(),
    val indicatorReadings: List<ExportIndicatorReading> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
)

@Serializable
public data class ExportVehicle(
    val id: String,
    val make: String,
    val model: String,
    val vin: String? = null,
    val plate: String? = null,
    val inServiceDate: String? = null,
    /** Cechy konfiguracji (nazwy jak w ConfigAttribute silnika). */
    val attributes: Map<String, String> = emptyMap(),
)

@Serializable
public data class ExportOdometerReading(val vehicleId: String, val date: String, val km: Int)

@Serializable
public data class ExportServiceEntry(
    val id: String,
    val vehicleId: String,
    val date: String,
    val odometerKm: Int? = null,
    val tasks: List<ExportTask>,
    val costMinor: Long? = null,
    val currency: String? = null,
    val note: String? = null,
    val documentIds: List<String> = emptyList(),
)

@Serializable
public data class ExportTask(val taskCode: String, val originalText: String? = null)

@Serializable
public data class ExportDocument(
    val id: String,
    val vehicleId: String,
    /** Ścieżka w archiwum, zawsze pod `documents/`. */
    val path: String,
    val mimeType: String,
    val addedOn: String,
)

@Serializable
public data class ExportUserRule(
    val id: String,
    val vehicleId: String,
    val taskCode: String,
    val km: Int? = null,
    val months: Int? = null,
)

@Serializable
public data class ExportIndicatorReading(
    val vehicleId: String,
    val taskCode: String,
    val date: String,
    val odometerKm: Int,
    val remainingKm: Int? = null,
    val remainingDays: Int? = null,
)
