package app.servicepassport.android.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "model_year") val modelYear: Int?,
    @ColumnInfo(name = "created_at_utc") val createdAtUtc: Long,
    @ColumnInfo(name = "updated_at_utc") val updatedAtUtc: Long,
)

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY created_at_utc, id LIMIT 1")
    fun observeProfile(): Flow<VehicleEntity?>

    @Query("SELECT * FROM vehicles ORDER BY created_at_utc, id LIMIT 1")
    suspend fun getProfile(): VehicleEntity?

    @Upsert
    suspend fun save(profile: VehicleEntity)
}

/** Initial schema. Add a tested Migration for each actual future schema change. */
object DatabaseMigrations {
    val ALL: Array<Migration> = emptyArray()
}

@Database(entities = [VehicleEntity::class], version = 1, exportSchema = true)
abstract class GarageDatabase : RoomDatabase() {
    abstract fun vehicles(): VehicleDao

    companion object {
        fun open(context: Context, name: String = "garage.db"): GarageDatabase =
            Room.databaseBuilder(context.applicationContext, GarageDatabase::class.java, name)
                .addMigrations(*DatabaseMigrations.ALL)
                // No destructive fallback: an unsupported version must never erase the garage.
                .build()
    }
}
