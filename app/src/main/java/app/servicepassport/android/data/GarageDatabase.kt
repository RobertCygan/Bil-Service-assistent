package app.servicepassport.android.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "model_year") val modelYear: Int?,
    @ColumnInfo(name = "created_at_utc") val createdAtUtc: Long,
    @ColumnInfo(name = "updated_at_utc") val updatedAtUtc: Long,
    @ColumnInfo(name = "first_registration_day") val firstRegistrationDay: Long? = null,
    val make: String? = null, val model: String? = null, val generation: String? = null,
    val vin: String? = null,
    @ColumnInfo(name = "registration_number") val registrationNumber: String? = null,
    @ColumnInfo(name = "engine_code") val engineCode: String? = null,
    @ColumnInfo(name = "gearbox_type") val gearboxType: String? = null,
    @ColumnInfo(name = "gearbox_code") val gearboxCode: String? = null,
    val powertrain: String? = null, val market: String? = null,
    @ColumnInfo(name = "service_regime") val serviceRegime: String? = null,
    @ColumnInfo(name = "usage_profile") val usageProfile: String? = null,
    @ColumnInfo(name = "configuration_revision", defaultValue = "1") val configurationRevision: Long = 1,
)

@Entity(tableName = "service_events", foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicle_id"], onDelete = ForeignKey.CASCADE)], indices = [Index("vehicle_id"), Index(value = ["id", "vehicle_id"], unique = true)])
data class ServiceEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "vehicle_id") val vehicleId: String,
    @ColumnInfo(name = "performed_day") val performedDay: Long,
    @ColumnInfo(name = "cost_minor") val costMinor: Long?,
    val currency: String?, val note: String, val revision: Long,
    @ColumnInfo(name = "created_at_utc") val createdAtUtc: Long,
    @ColumnInfo(name = "updated_at_utc") val updatedAtUtc: Long,
)

@Entity(tableName = "service_actions", foreignKeys = [ForeignKey(entity = ServiceEventEntity::class, parentColumns = ["id"], childColumns = ["event_id"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["event_id", "position"], unique = true)])
data class ServiceActionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "event_id") val eventId: String,
    val position: Int,
    @ColumnInfo(name = "action_code") val actionCode: String?,
    @ColumnInfo(name = "operation_code") val operationCode: String?,
    @ColumnInfo(name = "custom_label") val customLabel: String?,
)

@Entity(
    tableName = "odometer_readings",
    foreignKeys = [
        ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicle_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ServiceEventEntity::class, parentColumns = ["id", "vehicle_id"], childColumns = ["event_id", "vehicle_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("vehicle_id"), Index(value = ["event_id"], unique = true), Index(value = ["event_id", "vehicle_id"]), Index(value = ["id", "vehicle_id"], unique = true)],
)
data class OdometerReadingEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "vehicle_id") val vehicleId: String,
    @ColumnInfo(name = "event_id") val eventId: String?,
    @ColumnInfo(name = "observed_day") val observedDay: Long,
    val km: Long,
    @ColumnInfo(name = "exception_reason") val exceptionReason: String,
    val revision: Long,
    @ColumnInfo(name = "created_at_utc") val createdAtUtc: Long,
    @ColumnInfo(name = "updated_at_utc") val updatedAtUtc: Long,
)

/** Explicit selection; the composite FK also enforces ownership of the chosen reading. */
@Entity(tableName = "current_odometer", foreignKeys = [
    ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicle_id"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = OdometerReadingEntity::class, parentColumns = ["id", "vehicle_id"], childColumns = ["reading_id", "vehicle_id"], onDelete = ForeignKey.CASCADE),
], indices = [Index(value = ["reading_id", "vehicle_id"])])
data class CurrentOdometerEntity(
    @PrimaryKey @ColumnInfo(name = "vehicle_id") val vehicleId: String,
    @ColumnInfo(name = "reading_id") val readingId: String,
)

data class ServiceRecord(
    @Embedded val event: ServiceEventEntity,
    @Relation(parentColumn = "id", entityColumn = "event_id") val actions: List<ServiceActionEntity>,
    @Relation(parentColumn = "id", entityColumn = "event_id") val readings: List<OdometerReadingEntity>,
) {
    val reading: OdometerReadingEntity? get() = readings.singleOrNull()
    val orderedActions: List<ServiceActionEntity> get() = actions.sortedBy { it.position }
}

data class GarageSnapshot(
    @Embedded val vehicle: VehicleEntity,
    @Relation(parentColumn = "id", entityColumn = "vehicle_id") val readings: List<OdometerReadingEntity>,
    @Relation(parentColumn = "id", entityColumn = "vehicle_id") val selections: List<CurrentOdometerEntity>,
    @Relation(entity = ServiceEventEntity::class, parentColumn = "id", entityColumn = "vehicle_id") val events: List<ServiceRecord>,
) {
    val currentReading: OdometerReadingEntity? get() = readings.firstOrNull { it.id == selections.singleOrNull()?.readingId }
    val history: List<ServiceRecord> get() = events.sortedWith(compareByDescending<ServiceRecord> { it.event.performedDay }.thenByDescending { it.event.createdAtUtc }.thenBy { it.event.id })
}

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY created_at_utc, id LIMIT 1") fun observeProfile(): Flow<VehicleEntity?>
    @Query("SELECT * FROM vehicles ORDER BY created_at_utc, id LIMIT 1") suspend fun getProfile(): VehicleEntity?
    @Upsert suspend fun save(profile: VehicleEntity)
    @Transaction @Query("SELECT * FROM vehicles ORDER BY created_at_utc, id LIMIT 1") fun observeGarage(): Flow<GarageSnapshot?>
    @Transaction @Query("SELECT * FROM vehicles WHERE id = :id") suspend fun garage(id: String): GarageSnapshot?
    @Transaction @Query("SELECT * FROM service_events WHERE id = :id") suspend fun event(id: String): ServiceRecord?
    @Query("SELECT * FROM odometer_readings WHERE id = :id") suspend fun reading(id: String): OdometerReadingEntity?
    @Insert suspend fun insertEvent(event: ServiceEventEntity)
    @Update suspend fun updateEvent(event: ServiceEventEntity)
    @Insert suspend fun insertActions(actions: List<ServiceActionEntity>)
    @Query("DELETE FROM service_actions WHERE event_id = :id") suspend fun clearActions(id: String)
    @Upsert suspend fun saveReading(reading: OdometerReadingEntity)
    @Upsert suspend fun selectReading(selection: CurrentOdometerEntity)
    @Query("DELETE FROM current_odometer WHERE vehicle_id = :vehicleId") suspend fun clearSelection(vehicleId: String)
    @Query("DELETE FROM odometer_readings WHERE id = :id") suspend fun deleteReading(id: String)
    @Query("DELETE FROM service_events WHERE id = :id") suspend fun deleteEvent(id: String)
}

object DatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            for (name in listOf("first_registration_day", "make", "model", "generation", "vin", "registration_number", "engine_code", "gearbox_type", "gearbox_code", "powertrain", "market", "service_regime", "usage_profile")) {
                db.execSQL("ALTER TABLE vehicles ADD COLUMN `$name` ${if (name == "first_registration_day") "INTEGER" else "TEXT"}")
            }
            db.execSQL("ALTER TABLE vehicles ADD COLUMN configuration_revision INTEGER NOT NULL DEFAULT 1")
            db.execSQL("CREATE TABLE IF NOT EXISTS service_events (id TEXT NOT NULL, vehicle_id TEXT NOT NULL, performed_day INTEGER NOT NULL, cost_minor INTEGER, currency TEXT, note TEXT NOT NULL, revision INTEGER NOT NULL, created_at_utc INTEGER NOT NULL, updated_at_utc INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(vehicle_id) REFERENCES vehicles(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_service_events_vehicle_id ON service_events(vehicle_id)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_service_events_id_vehicle_id ON service_events(id, vehicle_id)")
            db.execSQL("CREATE TABLE IF NOT EXISTS service_actions (id TEXT NOT NULL, event_id TEXT NOT NULL, position INTEGER NOT NULL, action_code TEXT, operation_code TEXT, custom_label TEXT, PRIMARY KEY(id), FOREIGN KEY(event_id) REFERENCES service_events(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_service_actions_event_id_position ON service_actions(event_id, position)")
            db.execSQL("CREATE TABLE IF NOT EXISTS odometer_readings (id TEXT NOT NULL, vehicle_id TEXT NOT NULL, event_id TEXT, observed_day INTEGER NOT NULL, km INTEGER NOT NULL, exception_reason TEXT NOT NULL, revision INTEGER NOT NULL, created_at_utc INTEGER NOT NULL, updated_at_utc INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(vehicle_id) REFERENCES vehicles(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(event_id, vehicle_id) REFERENCES service_events(id, vehicle_id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_odometer_readings_vehicle_id ON odometer_readings(vehicle_id)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_odometer_readings_event_id ON odometer_readings(event_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_odometer_readings_event_id_vehicle_id ON odometer_readings(event_id, vehicle_id)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_odometer_readings_id_vehicle_id ON odometer_readings(id, vehicle_id)")
            db.execSQL("CREATE TABLE IF NOT EXISTS current_odometer (vehicle_id TEXT NOT NULL, reading_id TEXT NOT NULL, PRIMARY KEY(vehicle_id), FOREIGN KEY(vehicle_id) REFERENCES vehicles(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(reading_id, vehicle_id) REFERENCES odometer_readings(id, vehicle_id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_current_odometer_reading_id_vehicle_id ON current_odometer(reading_id, vehicle_id)")
        }
    }
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}

@Database(entities = [VehicleEntity::class, ServiceEventEntity::class, ServiceActionEntity::class, OdometerReadingEntity::class, CurrentOdometerEntity::class], version = 2, exportSchema = true)
abstract class GarageDatabase : RoomDatabase() {
    abstract fun vehicles(): VehicleDao
    companion object {
        fun open(context: Context, name: String = "garage.db"): GarageDatabase = Room.databaseBuilder(context.applicationContext, GarageDatabase::class.java, name).addMigrations(*DatabaseMigrations.ALL).build()
    }
}
