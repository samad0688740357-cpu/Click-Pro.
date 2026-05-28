package id.biz.rrndev.autoclicker.data.db.dao

import androidx.room.*
import id.biz.rrndev.autoclicker.data.entity.ClickPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClickPointDao {

    /** Observe all points for a flow in execution order. */
    @Query("SELECT * FROM click_points WHERE flowId = :flowId ORDER BY `order` ASC")
    fun getPointsForFlow(flowId: Long): Flow<List<ClickPointEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: ClickPointEntity): Long

    /** Batch insert/replace all points for a flow (used when saving an edited flow). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<ClickPointEntity>)

    @Update
    suspend fun update(point: ClickPointEntity)

    @Delete
    suspend fun delete(point: ClickPointEntity)

    /** Delete all points belonging to a flow (used before re-inserting on save). */
    @Query("DELETE FROM click_points WHERE flowId = :flowId")
    suspend fun deleteAllForFlow(flowId: Long)
}
