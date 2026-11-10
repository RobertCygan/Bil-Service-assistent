package app.servicepassport.android.data

import androidx.room.withTransaction
import java.time.Clock
import java.time.Year
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ProfileInput(val name: String, val modelYear: Int?)

enum class ProfileProblem { NAME_REQUIRED, NAME_TOO_LONG, INVALID_YEAR }

object ProfileValidation {
    fun problem(name: String, year: String, currentYear: Int): ProfileProblem? = when {
        name.isBlank() -> ProfileProblem.NAME_REQUIRED
        name.trim().length > 80 -> ProfileProblem.NAME_TOO_LONG
        year.isNotBlank() && (year.toIntOrNull() == null || year.toInt() !in 1900..currentYear + 1) ->
            ProfileProblem.INVALID_YEAR
        else -> null
    }
}

class ProfileRepository(private val database: GarageDatabase, private val clock: Clock = Clock.systemUTC()) {
    fun observeProfile() = database.vehicles().observeProfile()

    suspend fun save(input: ProfileInput) = withContext(Dispatchers.IO) {
        require(ProfileValidation.problem(input.name, input.modelYear?.toString().orEmpty(), Year.now(clock).value) == null)
        database.withTransaction {
            val old = database.vehicles().getProfile()
            val now = clock.millis()
            database.vehicles().save(
                VehicleEntity(
                    id = old?.id ?: UUID.randomUUID().toString(),
                    displayName = input.name.trim(),
                    modelYear = input.modelYear,
                    createdAtUtc = old?.createdAtUtc ?: now,
                    updatedAtUtc = now,
                )
            )
        }
    }
}
