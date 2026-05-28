package id.biz.rrndev.autoclicker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** App-only history folder/category for premium organization. */
@Entity(tableName = "history_folders")
data class HistoryFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val isPinned: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
