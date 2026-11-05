package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPathDao {
    @Query("SELECT * FROM saved_paths ORDER BY isDefault DESC, isFavorite DESC, lastUsedAt DESC")
    fun getAllPaths(): Flow<List<SavedPathEntity>>

    @Query("SELECT * FROM saved_paths WHERE isFavorite = 1 OR isDefault = 1 ORDER BY isDefault DESC, lastUsedAt DESC")
    fun getFavoritePaths(): Flow<List<SavedPathEntity>>

    @Query("SELECT * FROM saved_paths WHERE path = :path LIMIT 1")
    suspend fun getPathByValue(path: String): SavedPathEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPath(pathEntity: SavedPathEntity): Long

    @Update
    suspend fun updatePath(pathEntity: SavedPathEntity)

    @Delete
    suspend fun deletePath(pathEntity: SavedPathEntity)

    @Query("SELECT COUNT(*) FROM saved_paths")
    suspend fun getCount(): Int
}

@Dao
interface OperationHistoryDao {
    @Query("SELECT * FROM operation_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<OperationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: OperationHistoryEntity)

    @Query("DELETE FROM operation_history")
    suspend fun clearAllHistory()
}

@Dao
interface ErrorLogDao {
    @Query("SELECT * FROM error_logs ORDER BY timestamp DESC LIMIT 200")
    fun getAllLogs(): Flow<List<ErrorLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ErrorLogEntity)

    @Query("DELETE FROM error_logs")
    suspend fun clearAllLogs()
}

@Dao
interface ScheduledDeletionDao {
    @Query("SELECT * FROM scheduled_deletions WHERE isDeleted = 0 AND cancelled = 0 ORDER BY deleteAtMillis ASC")
    fun getPendingDeletions(): Flow<List<ScheduledDeletionEntity>>

    @Query("SELECT * FROM scheduled_deletions ORDER BY copiedAtMillis DESC LIMIT 100")
    fun getAllScheduledDeletions(): Flow<List<ScheduledDeletionEntity>>

    @Query("SELECT * FROM scheduled_deletions WHERE uniqueCopyId = :id LIMIT 1")
    suspend fun getById(id: String): ScheduledDeletionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ScheduledDeletionEntity)

    @Update
    suspend fun update(entity: ScheduledDeletionEntity)

    @Query("UPDATE scheduled_deletions SET cancelled = 1 WHERE uniqueCopyId = :id")
    suspend fun cancelSchedule(id: String)

    @Query("UPDATE scheduled_deletions SET isDeleted = 1, failureReason = null WHERE uniqueCopyId = :id")
    suspend fun markDeleted(id: String)

    @Query("UPDATE scheduled_deletions SET failureReason = :reason WHERE uniqueCopyId = :id")
    suspend fun markFailed(id: String, reason: String)

    @Delete
    suspend fun delete(entity: ScheduledDeletionEntity)

    @Query("DELETE FROM scheduled_deletions WHERE isDeleted = 1 OR cancelled = 1")
    suspend fun clearFinished()
}

@Database(
    entities = [
        SavedPathEntity::class,
        OperationHistoryEntity::class,
        ErrorLogEntity::class,
        ScheduledDeletionEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedPathDao(): SavedPathDao
    abstract fun operationHistoryDao(): OperationHistoryDao
    abstract fun errorLogDao(): ErrorLogDao
    abstract fun scheduledDeletionDao(): ScheduledDeletionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `scheduled_deletions` (
                        `uniqueCopyId` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `destinationPath` TEXT NOT NULL,
                        `fileSize` INTEGER NOT NULL,
                        `expectedSha256` TEXT,
                        `gameEditionName` TEXT NOT NULL,
                        `durationMinutes` INTEGER NOT NULL,
                        `copiedAtMillis` INTEGER NOT NULL,
                        `deleteAtMillis` INTEGER NOT NULL,
                        `isDeleted` INTEGER NOT NULL,
                        `cancelled` INTEGER NOT NULL,
                        `failureReason` TEXT,
                        PRIMARY KEY(`uniqueCopyId`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `scheduled_deletions` ADD COLUMN `destinationTreeUri` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `scheduled_deletions` ADD COLUMN `targetFileUri` TEXT DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "worm_temp_copier.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
