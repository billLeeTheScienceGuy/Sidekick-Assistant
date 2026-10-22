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

@Database(entities = [CommitmentEntity::class], version = 2, exportSchema = true)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun commitments(): CommitmentDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE commitments ADD COLUMN dueAt INTEGER")
                db.execSQL("ALTER TABLE commitments ADD COLUMN deliveredAt INTEGER")
            }
        }
    }
}
