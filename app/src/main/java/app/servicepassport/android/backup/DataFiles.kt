package app.servicepassport.android.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.servicepassport.android.BuildConfig
import app.servicepassport.android.reminders.ReminderCoordinator
import app.servicepassport.export.ImportFailure
import java.time.LocalDate
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** What the driver is told after a backup, restore or deletion. One message per outcome, no technical detail. */
enum class DataOutcome {
    BACKUP_SAVED, BACKUP_FAILED, RESTORED, PASSWORD_NEEDED, WRONG_PASSWORD_OR_DAMAGED, NOT_A_BACKUP, NEWER_VERSION,
    CONTENT_NOT_SUPPORTED, FILE_UNREADABLE, DELETED,
}

enum class PasswordProblem { TOO_SHORT, MISMATCH }

/** Backup, restore and deletion against files the driver picks in the system file dialog. */
class DataFiles(private val context: Context, private val backup: GarageBackup, private val reminders: ReminderCoordinator?) {

    fun suggestedName(today: LocalDate = LocalDate.now()): String = "car-service-backup-$today.zip"

    // Each operation runs to its end once it has started. The screen that started it can go away at any moment (another
    // tab is opened, the phone is turned); stopping half-way would leave a restore without its document files and
    // reminder settings, or a deletion that kept some of what it was asked to remove.

    /** @param password null or empty for a plain copy */
    suspend fun save(target: Uri, password: String?): DataOutcome = withContext(NonCancellable) { saveNow(target, password) }

    suspend fun restore(source: Uri, password: String?): DataOutcome = withContext(NonCancellable) { restoreNow(source, password) }

    suspend fun deleteEverything(): DataOutcome = withContext(NonCancellable) {
        backup.deleteAll()
        reminders?.sync(mayNotify = false) // no vehicle left: removes shown notifications and the delivery record
        DataOutcome.DELETED
    }

    private suspend fun saveNow(target: Uri, password: String?): DataOutcome {
        val chars = password?.takeIf { it.isNotEmpty() }?.toCharArray()
        return try {
            // "wt" empties an existing file first; a few storage providers only know plain "w", which is enough for a new file.
            val resolver = context.contentResolver
            val out = (try { resolver.openOutputStream(target, "wt") } catch (_: Exception) { null } ?: resolver.openOutputStream(target, "w"))
                ?: return DataOutcome.BACKUP_FAILED
            out.use { backup.write(it, BuildConfig.VERSION_NAME, chars) }
            DataOutcome.BACKUP_SAVED
        } catch (_: Exception) {
            // Never leave a half-written file that looks like a backup.
            try { DocumentsContract.deleteDocument(context.contentResolver, target) } catch (_: Exception) { }
            DataOutcome.BACKUP_FAILED
        } finally {
            chars?.fill('\u0000')
        }
    }

    private suspend fun restoreNow(source: Uri, password: String?): DataOutcome {
        val chars = password?.takeIf { it.isNotEmpty() }?.toCharArray()
        val outcome = try {
            val input = context.contentResolver.openInputStream(source) ?: return DataOutcome.FILE_UNREADABLE
            input.use { backup.restore(it, chars) }
        } catch (_: Exception) {
            return DataOutcome.FILE_UNREADABLE
        } finally {
            chars?.fill('\u0000')
        }
        return when (outcome) {
            RestoreOutcome.Restored -> { reminders?.sync(mayNotify = false); DataOutcome.RESTORED }
            is RestoreOutcome.ContentRefused -> DataOutcome.CONTENT_NOT_SUPPORTED
            is RestoreOutcome.ArchiveRefused -> when (outcome.reason) {
                ImportFailure.PASSWORD_REQUIRED -> DataOutcome.PASSWORD_NEEDED
                ImportFailure.WRONG_PASSWORD_OR_DAMAGED -> DataOutcome.WRONG_PASSWORD_OR_DAMAGED
                ImportFailure.NEWER_FORMAT -> DataOutcome.NEWER_VERSION
                ImportFailure.NOT_AN_EXPORT, ImportFailure.MANIFEST_INVALID, ImportFailure.DATA_INVALID, ImportFailure.UNSAFE_PATH,
                ImportFailure.UNEXPECTED_FILE, ImportFailure.FILE_MISSING, ImportFailure.FILE_CORRUPTED, ImportFailure.TOO_LARGE,
                ImportFailure.CONTENT_INCONSISTENT -> DataOutcome.NOT_A_BACKUP
            }
        }
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 8

        /** An empty password means "no encryption" and is valid. */
        fun passwordProblem(password: String, repeated: String): PasswordProblem? = when {
            password.isEmpty() && repeated.isEmpty() -> null
            password.length < MIN_PASSWORD_LENGTH -> PasswordProblem.TOO_SHORT
            password != repeated -> PasswordProblem.MISMATCH
            else -> null
        }
    }
}
