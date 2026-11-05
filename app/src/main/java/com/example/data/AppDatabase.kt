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

@Database(
    entities = [
        SavedPathEntity::class,
        OperationHistoryEntity::class,
        ErrorLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedPathDao(): SavedPathDao
    abstract fun operationHistoryDao(): OperationHistoryDao
    abstract fun errorLogDao(): ErrorLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "worm_temp_copier.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
