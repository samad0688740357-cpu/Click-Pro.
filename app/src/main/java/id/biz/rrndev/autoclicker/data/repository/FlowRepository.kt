package id.biz.rrndev.autoclicker.data.repository

import id.biz.rrndev.autoclicker.data.db.relation.FlowWithPoints
import id.biz.rrndev.autoclicker.data.entity.ClickFlowEntity
import id.biz.rrndev.autoclicker.data.entity.ClickPointEntity
import id.biz.rrndev.autoclicker.data.entity.HistoryFolderEntity
import kotlinx.coroutines.flow.Flow

/** Repository interface – separates data access from business logic. */
interface FlowRepository {

    /** Observe the full list of flows (hot Flow, updates on DB change). */
    fun getAllFlows(): Flow<List<ClickFlowEntity>>

    /** Observe flows with their points – used by history UI to show mode chips. */
    fun getAllFlowsWithPoints(): Flow<List<FlowWithPoints>>

    /** Load a flow plus its ordered points. Returns null if not found. */
    suspend fun getFlowWithPoints(id: Long): FlowWithPoints?

    /** Create a new flow and return its row id. */
    suspend fun createFlow(flow: ClickFlowEntity): Long

    /** Save (upsert) a flow and atomically replace all its points. */
    suspend fun saveFlowWithPoints(flow: ClickFlowEntity, points: List<ClickPointEntity>)

    suspend fun updateFlow(flow: ClickFlowEntity)

    suspend fun deleteFlow(flow: ClickFlowEntity)

    fun getAllFolders(): Flow<List<HistoryFolderEntity>>

    suspend fun createFolder(name: String): Long

    suspend fun updateFolder(folder: HistoryFolderEntity)

    suspend fun deleteFolder(folder: HistoryFolderEntity)

    suspend fun setFolderPinned(folderId: Long, isPinned: Boolean)

    suspend fun updateFolderOrder(folderIds: List<Long>)

    suspend fun setFlowPinned(flowId: Long, isPinned: Boolean)

    suspend fun moveFlowToFolder(flowId: Long, folderId: Long?)

    suspend fun cloneFlow(flowId: Long): Long?

    /** Export all flows + points to a JSON string. */
    suspend fun exportToJson(): String

    /**
     * Import flows + points from a JSON string.
     * Existing flows are kept; duplicates are merged by name.
     */
    suspend fun importFromJson(json: String)
}
