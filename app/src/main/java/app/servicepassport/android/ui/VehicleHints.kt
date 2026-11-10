package app.servicepassport.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.servicepassport.android.R
import app.servicepassport.android.catalog.VehicleCatalog
import app.servicepassport.android.catalog.VinFeedback
import app.servicepassport.android.catalog.plateLooksWrong
import app.servicepassport.vehicleid.VinError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Suggestions under the make, model, VIN and registration fields. They only fill in text the driver could type;
 * nothing here assigns a service schedule. Indices follow the profile editor: 0 make, 1 model.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun VehicleHints(make: String, model: String, vin: String, plate: String, set: (Int, String) -> Unit) {
    val context = LocalContext.current
    val catalog by produceState<VehicleCatalog?>(null) { value = withContext(Dispatchers.IO) { runCatching { VehicleCatalog.fromAssets(context) }.getOrNull() } }
    val list = catalog
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (list != null) {
            val knownMake = list.make(make)
            if (knownMake == null) {
                val makes = list.suggestMakes(make)
                if (makes.isNotEmpty()) {
                    Text(stringResource(R.string.catalog_make_suggestions), style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { makes.forEach { name -> SuggestionChip(onClick = { set(0, name) }, label = { Text(name) }, modifier = Modifier.testTag("make_suggestion")) } }
                }
            } else if (list.model(knownMake, model) == null) {
                val models = list.suggestModels(knownMake, model)
                if (models.isNotEmpty()) {
                    Text(stringResource(R.string.catalog_model_suggestions, knownMake), style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { models.forEach { name -> SuggestionChip(onClick = { set(1, name) }, label = { Text(name) }, modifier = Modifier.testTag("model_suggestion")) } }
                }
            }
            if (make.isNotBlank() || model.isNotBlank()) Text(stringResource(R.string.catalog_note), style = MaterialTheme.typography.bodySmall)
        }
        when (val feedback = VinFeedback.of(vin)) {
            VinFeedback.Empty -> Unit
            is VinFeedback.Invalid -> Text(stringResource(when {
                VinError.FORBIDDEN_LETTER in feedback.errors -> R.string.vin_forbidden_letter
                VinError.INVALID_CHARACTER in feedback.errors -> R.string.vin_invalid_character
                else -> R.string.vin_wrong_length
            }), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("vin_problem"))
            is VinFeedback.WellFormed -> {
                Text(stringResource(R.string.vin_well_formed), style = MaterialTheme.typography.bodySmall)
                val hint = feedback.makeHint
                if (hint != null && make.isBlank()) TextButton(onClick = { set(0, hint) }, modifier = Modifier.testTag("vin_make_hint")) { Text(stringResource(R.string.vin_make_hint, hint)) }
            }
        }
        if (plateLooksWrong(plate)) Text(stringResource(R.string.plate_looks_wrong), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("plate_problem"))
    }
}
