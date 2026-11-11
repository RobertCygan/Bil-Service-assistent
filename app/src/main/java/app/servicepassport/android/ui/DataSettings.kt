package app.servicepassport.android.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.servicepassport.android.R
import app.servicepassport.android.backup.DataFiles
import app.servicepassport.android.backup.DataOutcome
import app.servicepassport.android.backup.PasswordProblem
import kotlinx.coroutines.launch

private fun outcomeMessage(outcome: DataOutcome): Int = when (outcome) {
    DataOutcome.BACKUP_SAVED -> R.string.data_backup_saved
    DataOutcome.BACKUP_FAILED -> R.string.data_backup_failed
    DataOutcome.RESTORED -> R.string.data_restored
    DataOutcome.PASSWORD_NEEDED -> R.string.data_password_needed
    DataOutcome.WRONG_PASSWORD_OR_DAMAGED -> R.string.data_wrong_password
    DataOutcome.NOT_A_BACKUP -> R.string.data_not_a_backup
    DataOutcome.NEWER_VERSION -> R.string.data_newer_version
    DataOutcome.CONTENT_NOT_SUPPORTED -> R.string.data_content_not_supported
    DataOutcome.FILE_UNREADABLE -> R.string.data_file_unreadable
    DataOutcome.DELETED -> R.string.data_deleted
}

/** Backup, restore and deletion. Every destructive step needs an explicit confirmation. */
@Composable internal fun DataSection(files: DataFiles, hasVehicle: Boolean) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<DataOutcome?>(null) }
    var password by rememberSaveable { mutableStateOf("") }
    var repeated by rememberSaveable { mutableStateOf("") }
    var restoreFrom by remember { mutableStateOf<Uri?>(null) }
    var restorePassword by rememberSaveable { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val problem = DataFiles.passwordProblem(password, repeated)

    fun run(work: suspend () -> DataOutcome) { busy = true; outcome = null; scope.launch { outcome = try { work() } finally { busy = false } } }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) { val chosen = password; password = ""; repeated = ""; run { files.save(uri, chosen) } }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { restorePassword = ""; restoreFrom = uri } }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.data_backup_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.data_backup_help), style = MaterialTheme.typography.bodySmall)
        if (hasVehicle) {
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth().testTag("backup_password"), label = { Text(stringResource(R.string.data_password_optional)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            if (password.isNotEmpty() || repeated.isNotEmpty()) {
                OutlinedTextField(repeated, { repeated = it }, Modifier.fillMaxWidth().testTag("backup_password_repeat"), label = { Text(stringResource(R.string.data_password_repeat)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = problem != null, supportingText = { problem?.let { Text(stringResource(if (it == PasswordProblem.TOO_SHORT) R.string.data_password_too_short else R.string.data_password_mismatch, DataFiles.MIN_PASSWORD_LENGTH)) } })
                NoteCard(stringResource(R.string.data_password_warning))
            } else Text(stringResource(R.string.data_unencrypted_note), style = MaterialTheme.typography.bodySmall)
            Button(onClick = { create.launch(files.suggestedName()) }, enabled = !busy && problem == null, modifier = Modifier.fillMaxWidth().testTag("backup_save")) { Text(stringResource(R.string.data_backup_save)) }
        }
        OutlinedButton(onClick = { open.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("backup_restore")) { Text(stringResource(R.string.data_restore)) }
        if (hasVehicle) TextButton(onClick = { confirmDelete = true }, enabled = !busy, modifier = Modifier.testTag("delete_all")) { Text(stringResource(R.string.data_delete_all), color = MaterialTheme.colorScheme.error) }
        if (busy) CircularProgressIndicator()
        outcome?.let { Text(stringResource(outcomeMessage(it)), Modifier.testTag("data_outcome"), color = if (it in setOf(DataOutcome.BACKUP_SAVED, DataOutcome.RESTORED, DataOutcome.DELETED)) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error) }
    }

    restoreFrom?.let { uri ->
        val needsPassword = outcome == DataOutcome.PASSWORD_NEEDED || outcome == DataOutcome.WRONG_PASSWORD_OR_DAMAGED
        AlertDialog(onDismissRequest = { if (!busy) restoreFrom = null }, title = { Text(stringResource(R.string.data_restore_title)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (hasVehicle) R.string.data_restore_replaces else R.string.data_restore_body))
                if (needsPassword) OutlinedTextField(restorePassword, { restorePassword = it }, Modifier.fillMaxWidth().testTag("restore_password"), label = { Text(stringResource(R.string.data_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                outcome?.takeIf { needsPassword }?.let { Text(stringResource(outcomeMessage(it)), Modifier.testTag("restore_problem_${it.name}"), color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(enabled = !busy, modifier = Modifier.testTag("confirm_restore"), onClick = {
                val chosen = restorePassword
                busy = true
                scope.launch {
                    val result = try { files.restore(uri, chosen) } finally { busy = false }
                    outcome = result
                    // Keep the dialog open only to ask for the password again.
                    if (result != DataOutcome.PASSWORD_NEEDED && result != DataOutcome.WRONG_PASSWORD_OR_DAMAGED) { restoreFrom = null; restorePassword = "" }
                }
            }) { Text(stringResource(R.string.data_restore_confirm)) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { restoreFrom = null; restorePassword = "" }) { Text(stringResource(R.string.cancel)) } })
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(stringResource(R.string.data_delete_title)) }, text = { Text(stringResource(R.string.data_delete_body)) },
        confirmButton = { TextButton(modifier = Modifier.testTag("confirm_delete_all"), onClick = { confirmDelete = false; run { files.deleteEverything() } }) { Text(stringResource(R.string.data_delete_confirm), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } })
}
