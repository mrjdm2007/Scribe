package com.mrjdm.scribe.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "scribe_items", indices = [Index("parentId"), Index("kind"), Index("updatedAt"), Index("deletedAt")])
data class ScribeItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long? = null,
    val kind: String,
    val title: String,
    val body: String = "",
    val uri: String = "",
    val tags: String = "",
    val bookmarked: Boolean = false,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)

@Entity(tableName = "snapshots", indices = [Index("itemId")])
data class NoteSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val title: String,
    val body: String,
    val savedAt: Long = System.currentTimeMillis()
)

@Dao
interface ScribeDao {
    @Query("SELECT * FROM scribe_items WHERE parentId IS :parentId AND deletedAt IS NULL ORDER BY position ASC, createdAt ASC")
    fun children(parentId: Long?): Flow<List<ScribeItem>>
    @Query("SELECT * FROM scribe_items WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): ScribeItem?
    @Query("SELECT * FROM scribe_items WHERE kind = :kind AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun byKind(kind: String): Flow<List<ScribeItem>>
    @Query("SELECT * FROM scribe_items WHERE bookmarked = 1 AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun bookmarks(): Flow<List<ScribeItem>>
    @Query("SELECT * FROM scribe_items WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun trash(): Flow<List<ScribeItem>>
    @Query("SELECT * FROM scribe_items WHERE deletedAt IS NULL AND (title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%') ORDER BY updatedAt DESC")
    fun search(query: String): Flow<List<ScribeItem>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ScribeItem): Long
    @Update
    suspend fun update(item: ScribeItem)
    @Query("DELETE FROM scribe_items WHERE id = :id")
    suspend fun deleteId(id: Long)
    @Insert
    suspend fun snapshot(snapshot: NoteSnapshot)
    @Query("SELECT * FROM snapshots WHERE itemId = :itemId ORDER BY savedAt DESC")
    fun snapshots(itemId: Long): Flow<List<NoteSnapshot>>
    @Query("SELECT * FROM snapshots ORDER BY savedAt DESC")
    suspend fun allSnapshots(): List<NoteSnapshot>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshots(snapshots: List<NoteSnapshot>)
    @Query("SELECT * FROM scribe_items ORDER BY id")
    suspend fun allItems(): List<ScribeItem>
    @Query("DELETE FROM scribe_items")
    suspend fun clearItems()
    @Query("DELETE FROM snapshots WHERE itemId = :itemId")
    suspend fun deleteSnapshotsForItem(itemId: Long)
    @Query("DELETE FROM snapshots")
    suspend fun clearSnapshots()
}

@Database(entities = [ScribeItem::class, NoteSnapshot::class], version = 1, exportSchema = true)
abstract class ScribeDatabase : RoomDatabase() {
    abstract fun dao(): ScribeDao
    companion object {
        @Volatile private var INSTANCE: ScribeDatabase? = null
        fun get(context: Context): ScribeDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, ScribeDatabase::class.java, "scribe.db").build().also { INSTANCE = it }
        }
    }
}
