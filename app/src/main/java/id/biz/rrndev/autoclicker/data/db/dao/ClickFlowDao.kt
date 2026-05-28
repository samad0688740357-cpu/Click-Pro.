package id.biz.rrndev.autoclicker.data.db.dao

import androidx.room.*
import id.biz.rrndev.autoclicker.data.db.relation.FlowWithPoints
import id.biz.rrndev.autoclicker.data.entity.ClickFlowEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClickFlowDao {

    /** Observe all flows ordered by creation time (newest first). */
    @Query("SELECT * FROM click_flows ORDER BY createdAt DESC")
    fun getAllFlows(): Flow<List<ClickFlowEntity>>

    /** Observe all flows together with their points (for UI with chips). */
    @Transaction
    @Query("SELECT * FROM click_flows ORDER BY createdAt DESC")
    fun getAllFlowsWithPoints(): Flow<List<FlowWithPoints>>

    /** Load a single flow together with all its points in one transaction. */
    @Transaction
    @Query("SELECT * FROM click_flows WHERE id = :id LIMIT 1")
    suspend fun getFlowWithPoints(id: Long): FlowWithPoints?

    /** Return the total number of saved flows. */
    @Query("SELECT COUNT(*) FROM click_flows")
    suspend fun getFlowCount(): Int

    @Query("UPDATE click_flows SET folderId = :folderId WHERE id = :flowId")
    suspend fun setFolder(flowId: Long, folderId: Long?)

    @Query("UPDATE click_flows SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolder(folderId: Long)

    @Query("DELETE FROM click_flows WHERE folderId = :folderId")
    suspend fun deleteFlowsInFolder(folderId: Long)

    @Query("UPDATE click_flows SET isPinned = :isPinned WHERE id = :flowId")
    suspend fun setPinned(flowId: Long, isPinned: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(flow: ClickFlowEntity): Long

    @Update
    suspend fun update(flow: ClickFlowEntity)

    @Delete
    suspend fun delete(flow: ClickFlowEntity)
}
