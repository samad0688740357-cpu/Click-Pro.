package id.biz.rrndev.autoclicker.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import id.biz.rrndev.autoclicker.data.model.DEFAULT_SWIPE_DURATION_MS
import id.biz.rrndev.autoclicker.data.model.DEFAULT_TAP_DURATION_MS

/**
 * Room entity representing a single tap point within a click flow.
 * Coordinates are absolute screen pixels (device-independent after placement).
 */
@Entity(
    tableName = "click_points",
    foreignKeys = [
        ForeignKey(
            entity = ClickFlowEntity::class,
            parentColumns = ["id"],
            childColumns = ["flowId"],
            // Cascade delete all points when the parent flow is deleted
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("flowId")]
)
data class ClickPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** Parent flow this point belongs to. */
    val flowId: Long,
    /** Absolute screen X coordinate in pixels. */
    val x: Float,
    /** Absolute screen Y coordinate in pixels. */
    val y: Float,
    /** Delay before this tap fires in the sequence (milliseconds). */
    val delayMs: Long = 300L,
    /** How long the tap gesture is held for (milliseconds, min 1). */
    val durationMs: Long = DEFAULT_TAP_DURATION_MS,
    /** How many times this single point is tapped before moving to the next. */
    val repeatCount: Int = 1,
    /** Zero-based execution order within the flow. */
    val order: Int = 0,
    /** Type of action: 0 for Click, 1 for Swipe, 2 for Long press. */
    val type: Int = 0,
    /** Absolute screen X coordinate for swipe end. */
    val endX: Float = 0f,
    /** Absolute screen Y coordinate for swipe end. */
    val endY: Float = 0f,
    /** Duration of the swipe gesture in milliseconds. */
    val swipeDurationMs: Long = DEFAULT_SWIPE_DURATION_MS
)
