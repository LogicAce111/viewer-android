package com.legion.viewer.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "media_progress", primaryKeys = ["category", "uri"])
data class MediaProgressEntity(
    val category: String,
    val uri: String,
    val positionMs: Long = 0,
    val scrollRatio: Float = 0f,
    val pageIndex: Int = 0,
    val pageOffset: Int = 0,
    val completed: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface MediaProgressDao {
    @Query("SELECT * FROM media_progress WHERE category = :category AND uri = :uri LIMIT 1")
    suspend fun get(category: String, uri: String): MediaProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(progress: MediaProgressEntity)

    @Query("DELETE FROM media_progress WHERE category = :category AND uri = :uri")
    suspend fun delete(category: String, uri: String)
}

@Database(entities = [MediaProgressEntity::class], version = 1, exportSchema = false)
abstract class ViewerDatabase : RoomDatabase() {
    abstract fun progressDao(): MediaProgressDao

    companion object {
        fun create(context: Context): ViewerDatabase = Room.databaseBuilder(
            context.applicationContext,
            ViewerDatabase::class.java,
            "viewer.db",
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }
}

class ProgressRepository(private val dao: MediaProgressDao) {
    suspend fun get(category: MediaCategory, uri: Uri): MediaProgressEntity? = dao.get(category.name, uri.toString())

    suspend fun savePlayback(item: MediaItem, positionMs: Long, completed: Boolean = false) {
        dao.save(
            MediaProgressEntity(
                category = item.category.name,
                uri = item.uri.toString(),
                positionMs = positionMs.coerceAtLeast(0),
                completed = completed,
            )
        )
    }

    suspend fun saveText(item: MediaItem, scrollRatio: Float) {
        dao.save(
            MediaProgressEntity(
                category = item.category.name,
                uri = item.uri.toString(),
                scrollRatio = scrollRatio.coerceIn(0f, 1f),
            )
        )
    }

    suspend fun saveComic(item: MediaItem, pageIndex: Int, pageOffset: Int) {
        dao.save(
            MediaProgressEntity(
                category = item.category.name,
                uri = item.uri.toString(),
                pageIndex = pageIndex.coerceAtLeast(0),
                pageOffset = pageOffset.coerceAtLeast(0),
            )
        )
    }
}

private typealias MediaCategory = com.legion.viewer.model.MediaCategory
private typealias MediaItem = com.legion.viewer.model.MediaItem
private typealias Uri = android.net.Uri

