package id.biz.rrndev.autoclicker.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import id.biz.rrndev.autoclicker.data.entity.HistoryFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryFolderDao {

    @Query("SELECT * FROM history_folders ORDER BY isPinned DESC, sortOrder ASC, name COLLATE NOCASE ASC")
    fun getAllFolders(): Flow<List<HistoryFolderEntity>>

    @Query("SELECT * FROM history_folders ORDER BY isPinned DESC, sortOrder ASC, name COLLATE NOCASE ASC")
    suspend fun getAllFoldersSnapshot(): List<HistoryFolderEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM history_folders")
    suspend fun getNextSortOrder(): Int

    @Insert
    suspend fun insert(folder: HistoryFolderEntity): Long

    @Update
    suspend fun update(folder: HistoryFolderEntity)

    @Delete
    suspend fun delete(folder: HistoryFolderEntity)

    @Query("UPDATE history_folders SET isPinned = :isPinned WHERE id = :folderId")
    suspend fun setPinned(folderId: Long, isPinned: Boolean)

    @Query("UPDATE history_folders SET sortOrder = :sortOrder WHERE id = :folderId")
    suspend fun setSortOrder(folderId: Long, sortOrder: Int)
}
