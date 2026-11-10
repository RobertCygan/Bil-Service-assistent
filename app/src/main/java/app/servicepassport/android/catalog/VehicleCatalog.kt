package app.servicepassport.android.catalog

import android.content.Context
import app.servicepassport.vehicleid.CatalogMatcher
import app.servicepassport.vehicleid.NorwegianPlate
import app.servicepassport.vehicleid.PlateResult
import app.servicepassport.vehicleid.Vin
import app.servicepassport.vehicleid.VinError
import app.servicepassport.vehicleid.VinResult
import org.json.JSONObject

/**
 * Makes and models offered as suggestions when adding a car. Built from open registration data
 * (see vehicle-catalog/README.md). It carries no service data and does not identify a configuration:
 * picking from it only saves typing. Any make or model can still be typed by hand.
 */
class VehicleCatalog(private val models: Map<String, List<Model>>, val source: String, val licence: String) {
    data class Model(val name: String, val nordic: List<String>)

    /** Alphabetical make names. */
    val makes: List<String> = models.keys.sortedBy { it.lowercase() }

    fun suggestMakes(query: String, limit: Int = 6): List<String> = if (query.isBlank()) emptyList() else CatalogMatcher.search(query, makes, limit)

    /** The catalogue spelling of a typed make, if it is one. */
    fun make(typed: String): String? = CatalogMatcher.exact(typed, makes)

    /** Models of a known make, most common first; an empty query lists the most common ones. */
    fun suggestModels(make: String, query: String, limit: Int = 8): List<String> {
        val known = make(make) ?: return emptyList()
        return CatalogMatcher.search(query, models.getValue(known).map { it.name }, limit)
    }

    fun model(make: String, typed: String): Model? {
        val known = make(make) ?: return null
        val name = CatalogMatcher.exact(typed, models.getValue(known).map { it.name }) ?: return null
        return models.getValue(known).first { it.name == name }
    }

    companion object {
        const val ASSET = "vehicle-list.json"

        fun fromAssets(context: Context): VehicleCatalog = parse(context.assets.open(ASSET).bufferedReader().use { it.readText() })

        fun parse(json: String): VehicleCatalog {
            val root = JSONObject(json)
            val meta = root.getJSONObject("meta")
            val makes = root.getJSONArray("makes")
            val result = LinkedHashMap<String, List<Model>>()
            for (i in 0 until makes.length()) {
                val make = makes.getJSONObject(i)
                val list = make.getJSONArray("models")
                result[make.getString("name")] = (0 until list.length()).map { j ->
                    val model = list.getJSONObject(j)
                    val nordic = model.optJSONArray("nordic")
                    Model(model.getString("name"), if (nordic == null) emptyList() else (0 until nordic.length()).map(nordic::getString))
                }
            }
            val verification = meta.optJSONObject("verification")?.optString("source").orEmpty()
            return VehicleCatalog(result, listOf(meta.getString("source"), verification).filter { it.isNotEmpty() }.joinToString("; "), meta.getString("licence"))
        }
    }
}

/** What to tell the driver about a typed VIN. Format only: it never proves the car or its equipment. */
sealed interface VinFeedback {
    data object Empty : VinFeedback
    data class Invalid(val errors: Set<VinError>) : VinFeedback
    /** [makeHint] is a suggestion from the manufacturer prefix, to be confirmed by the driver. */
    data class WellFormed(val vin: String, val makeHint: String?) : VinFeedback

    companion object {
        fun of(typed: String): VinFeedback {
            if (typed.isBlank()) return Empty
            return when (val result = Vin.parse(typed)) {
                is VinResult.Invalid -> Invalid(result.errors)
                is VinResult.Valid -> WellFormed(result.vin, result.makeHint)
            }
        }
    }
}

/** True when the text cannot be a Norwegian registration number at all. Unusual but possible plates are accepted. */
fun plateLooksWrong(typed: String): Boolean = typed.isNotBlank() && NorwegianPlate.parse(typed) is PlateResult.Invalid
