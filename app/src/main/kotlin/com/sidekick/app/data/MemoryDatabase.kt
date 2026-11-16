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

@Entity(tableName = "automatic_changes")
data class AutomaticChange(@PrimaryKey val targetId: String, val token: String, val before: String?, val after: String, val changedAt: Long)

/**
 * What a consent notice covered in one listening session (Washington treats a recorded announcement as consent and
 * SideKick keeps no audio). No audio, no voice data, no position: only the state codes the rules came from.
 */
@Entity(tableName = "notice_records")
data class NoticeRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val noticeAt: Long,
    /** Comma-separated state codes (containing state first, then nearby states that made it stricter). */
    val stateCodes: String,
    val recordingRule: String,
    val biometricRule: String,
    val textVersion: Int,
    val includesVoice: Boolean,
    /** SPOKEN or ATTESTED. */
    val method: String,
)

@Dao
interface NoticeRecordDao {
    @Insert suspend fun put(record: NoticeRecordEntity)
    @Query("SELECT * FROM notice_records ORDER BY noticeAt DESC LIMIT 20") fun observeRecent(): Flow<List<NoticeRecordEntity>>
    @Query("SELECT * FROM notice_records") suspend fun all(): List<NoticeRecordEntity>
    @Query("DELETE FROM notice_records WHERE sessionId = :session") suspend fun deleteSession(session: String)
    @Query("DELETE FROM notice_records WHERE noticeAt < :before") suspend fun deleteBefore(before: Long)
    @Query("DELETE FROM notice_records") suspend fun deleteAll()
}

/** Recovery metadata only: never duplicates event titles, source excerpts or transcripts. */
@Entity(tableName = "calendar_links")
data class CalendarLink(@PrimaryKey val memoryId: String, val calendarId: Long, val eventId: Long?,
    val appliedHash: String?, val pendingHash: String?, val state: String)

@Dao
interface CalendarLinkDao {
    @Query("SELECT * FROM calendar_links") suspend fun all(): List<CalendarLink>
    @Query("SELECT * FROM calendar_links WHERE memoryId = :id") suspend fun get(id: String): CalendarLink?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(link: CalendarLink)
    @Query("DELETE FROM calendar_links WHERE memoryId = :id") suspend fun delete(id: String)
}

@Dao
interface CommitmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putChange(change: AutomaticChange)
    @Query("SELECT * FROM automatic_changes WHERE targetId = :id") suspend fun change(id: String): AutomaticChange?
    @Query("SELECT * FROM automatic_changes ORDER BY changedAt DESC LIMIT 5") suspend fun recentChanges(): List<AutomaticChange>
    @Query("SELECT * FROM automatic_changes ORDER BY changedAt DESC LIMIT 5") fun observeChanges(): Flow<List<AutomaticChange>>
    @Query("DELETE FROM automatic_changes WHERE targetId = :id") suspend fun clearChange(id: String)
    @Query("DELETE FROM automatic_changes WHERE targetId NOT IN (SELECT targetId FROM automatic_changes ORDER BY changedAt DESC LIMIT 100)") suspend fun pruneChanges()
    @Transaction suspend fun write(item: CommitmentEntity, existing: Boolean, change: AutomaticChange?) {
        if (existing) update(item) else insertAll(listOf(item))
        if (change == null) clearChange(item.id) else { putChange(change); pruneChanges() }
    }
    @Transaction suspend fun undoWrite(id: String, previous: CommitmentEntity?) {
        if (previous == null) delete(id) else update(previous)
        clearChange(id)
    }
    @Query("SELECT * FROM commitments ORDER BY capturedAt DESC, id ASC")
    fun observe(): Flow<List<CommitmentEntity>>
    @Insert suspend fun insertAll(items: List<CommitmentEntity>)
    @Query("SELECT * FROM commitments WHERE id = :id") suspend fun get(id: String): CommitmentEntity?
    @Query("SELECT * FROM commitments WHERE state = 'ACCEPTED' AND activityStartAt = :start") suspend fun activitiesAt(start: Long): List<CommitmentEntity>
    @Query("SELECT * FROM commitments") suspend fun all(): List<CommitmentEntity>
    @Update suspend fun update(item: CommitmentEntity)
    @Query("UPDATE commitments SET state = :state WHERE id = :id AND state = 'PROPOSED'")
    suspend fun review(id: String, state: String)
    @Query("DELETE FROM commitments WHERE id = :id") suspend fun deleteRow(id: String)
    @Transaction suspend fun delete(id: String) { deleteRow(id); clearChange(id) }
    @Query("DELETE FROM commitments") suspend fun deleteRows()
    @Query("DELETE FROM automatic_changes") suspend fun clearChanges()
    @Transaction suspend fun deleteAll() { deleteRows(); clearChanges() }
}

@Database(entities = [CommitmentEntity::class, AutomaticChange::class, CalendarLink::class, NoticeRecordEntity::class], version = 7, exportSchema = true)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun commitments(): CommitmentDao
    abstract fun calendarLinks(): CalendarLinkDao
    abstract fun noticeRecords(): NoticeRecordDao
    companion object {
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS notice_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId TEXT NOT NULL, noticeAt INTEGER NOT NULL, stateCodes TEXT NOT NULL, recordingRule TEXT NOT NULL, biometricRule TEXT NOT NULL, textVersion INTEGER NOT NULL, includesVoice INTEGER NOT NULL, method TEXT NOT NULL)")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS calendar_links (memoryId TEXT NOT NULL PRIMARY KEY, calendarId INTEGER NOT NULL, eventId INTEGER, appliedHash TEXT, pendingHash TEXT, state TEXT NOT NULL)")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS automatic_changes (targetId TEXT NOT NULL PRIMARY KEY, token TEXT NOT NULL, `before` TEXT, `after` TEXT NOT NULL, changedAt INTEGER NOT NULL)")
            }
        }
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
