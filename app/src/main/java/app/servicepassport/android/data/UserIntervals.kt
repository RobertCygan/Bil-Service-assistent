package app.servicepassport.android.data

import androidx.room.*
import app.servicepassport.contract.ServiceTask

@Entity(
    tableName = "user_intervals",
    foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicle_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["vehicle_id", "action_code", "operation_code"], unique = true)],
)
data class UserIntervalEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "vehicle_id") val vehicleId: String,
    @ColumnInfo(name = "action_code") val actionCode: String,
    @ColumnInfo(name = "operation_code") val operationCode: String,
    @ColumnInfo(name = "distance_km") val distanceKm: Int?,
    val months: Int?,
    val revision: Long,
    @ColumnInfo(name = "created_at_utc") val createdAtUtc: Long,
    @ColumnInfo(name = "updated_at_utc") val updatedAtUtc: Long,
)

data class UserIntervalInput(
    val id: String,
    val vehicleId: String,
    val expectedRevision: Long?,
    val actionCode: String?,
    val operationCode: String?,
    val distanceKm: Int?,
    val months: Int?,
)

object UserIntervalValidation {
    const val MAX_MONTHS = 1200
    fun validate(input: UserIntervalInput) {
        if (input.actionCode == null || input.operationCode == null || ServiceTask.fromTaskCode("${input.actionCode}:${input.operationCode}") == null) throw InputRejected(InputProblem.INVALID_ACTION)
        if (input.distanceKm == null && input.months == null || input.distanceKm != null && input.distanceKm <= 0 || input.months != null && input.months !in 1..MAX_MONTHS) throw InputRejected(InputProblem.INVALID_INTERVAL)
    }
}
