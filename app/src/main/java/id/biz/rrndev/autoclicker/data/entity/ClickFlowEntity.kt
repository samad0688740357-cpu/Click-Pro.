package id.biz.rrndev.autoclicker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a named click flow (a sequence of tap points).
 */
@Entity(
    tableName = "click_flows",
    indices = [Index("folderId"), Index("isPinned")]
)
data class ClickFlowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /** Number of times the full flow repeats. 0 = infinite. */
    val loopCount: Int = 1,
    /** Delay in milliseconds between each full-flow cycle. */
    val loopDelayMs: Long = 500L,
    val createdAt: Long = System.currentTimeMillis(),
    /** App-only premium history folder/category. This is not a file-system folder. */
    val folderId: Long? = null,
    /** Premium favorite/pin marker for history ordering. */
    val isPinned: Boolean = false
)
