package com.sidekick.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "commitments")
data class CommitmentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val evidence: String,
    val capturedAt: Long,
    val zoneId: String,
    val reviewNote: String,
    val state: String = "PROPOSED",
    val dueAt: Long? = null,
    val deliveredAt: Long? = null,
    val activityStartAt: Long? = null,
    val activityEndAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val informational: Boolean = false,
    val placeName: String? = null,
    val placeAddress: String? = null,
    val sourceKey: String? = null,
)

@Dao
interface CommitmentDao {
    @Query("SELECT * FROM commitments ORDER BY capturedAt DESC, id ASC")
    fun observe(): Flow<List<CommitmentEntity>>
    @Insert suspend fun insertAll(items: List<CommitmentEntity>)
    @Query("SELECT * FROM commitments WHERE id = :id") suspend fun get(id: String): CommitmentEntity?
    @Query("SELECT * FROM commitments") suspend fun all(): List<CommitmentEntity>
    @Update suspend fun update(item: CommitmentEntity)
    @Query("UPDATE commitments SET state = :state WHERE id = :id AND state = 'PROPOSED'")
    suspend fun review(id: String, state: String)
    @Query("DELETE FROM commitments WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM commitments") suspend fun deleteAll()
}

@Database(entities = [CommitmentEntity::class], version = 4, exportSchema = true)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun commitments(): CommitmentDao
    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE commitments ADD COLUMN informational INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE commitments ADD COLUMN placeName TEXT")
                db.execSQL("ALTER TABLE commitments ADD COLUMN placeAddress TEXT")
                db.execSQL("ALTER TABLE commitments ADD COLUMN sourceKey TEXT")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE commitments ADD COLUMN activityStartAt INTEGER")
                db.execSQL("ALTER TABLE commitments ADD COLUMN activityEndAt INTEGER")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE commitments ADD COLUMN dueAt INTEGER")
                db.execSQL("ALTER TABLE commitments ADD COLUMN deliveredAt INTEGER")
            }
        }
    }
}
