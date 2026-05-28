package id.biz.rrndev.autoclicker.data.repository

import com.google.gson.Gson
import id.biz.rrndev.autoclicker.data.db.dao.ClickFlowDao
import id.biz.rrndev.autoclicker.data.db.dao.ClickPointDao
import id.biz.rrndev.autoclicker.data.db.dao.HistoryFolderDao
import id.biz.rrndev.autoclicker.data.db.relation.FlowWithPoints
import id.biz.rrndev.autoclicker.data.entity.ClickFlowEntity
import id.biz.rrndev.autoclicker.data.entity.ClickPointEntity
import id.biz.rrndev.autoclicker.data.entity.HistoryFolderEntity
import id.biz.rrndev.autoclicker.data.model.ClickActionType
import id.biz.rrndev.autoclicker.data.model.DEFAULT_LONG_PRESS_DURATION_MS
import id.biz.rrndev.autoclicker.data.model.DEFAULT_SWIPE_DURATION_MS
import id.biz.rrndev.autoclicker.data.model.DEFAULT_TAP_DURATION_MS
import id.biz.rrndev.autoclicker.data.model.MIN_LONG_PRESS_DURATION_MS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class FlowRepositoryImpl(
    private val flowDao: ClickFlowDao,
    private val pointDao: ClickPointDao,
    private val folderDao: HistoryFolderDao
) : FlowRepository {

    private val gson = Gson()

    override fun getAllFlows(): Flow<List<ClickFlowEntity>> = flowDao.getAllFlows()

    override fun getAllFlowsWithPoints(): Flow<List<FlowWithPoints>> = flowDao.getAllFlowsWithPoints()

    override suspend fun getFlowWithPoints(id: Long): FlowWithPoints? =
        flowDao.getFlowWithPoints(id)

    override suspend fun createFlow(flow: ClickFlowEntity): Long = flowDao.insert(flow)

    override suspend fun saveFlowWithPoints(
        flow: ClickFlowEntity,
        points: List<ClickPointEntity>
    ) {
        // Upsert the flow first to obtain a valid id
        val flowId = if (flow.id == 0L) flowDao.insert(flow) else {
            flowDao.update(flow)
            flow.id
        }
        // Atomically replace all points so ordering is always consistent
        pointDao.deleteAllForFlow(flowId)
        val reordered = points.mapIndexed { index, p ->
            normalizePoint(p.copy(flowId = flowId, order = index))
        }
        pointDao.insertAll(reordered)
    }

    override suspend fun updateFlow(flow: ClickFlowEntity) = flowDao.update(flow)

    override suspend fun deleteFlow(flow: ClickFlowEntity) = flowDao.delete(flow)

    override fun getAllFolders(): Flow<List<HistoryFolderEntity>> = folderDao.getAllFolders()

    override suspend fun createFolder(name: String): Long =
        folderDao.insert(
            HistoryFolderEntity(
                name = name.trim(),
                sortOrder = folderDao.getNextSortOrder()
            )
        )

    override suspend fun updateFolder(folder: HistoryFolderEntity) = folderDao.update(folder)

    override suspend fun deleteFolder(folder: HistoryFolderEntity) {
        flowDao.deleteFlowsInFolder(folder.id)
        folderDao.delete(folder)
    }

    override suspend fun setFolderPinned(folderId: Long, isPinned: Boolean) =
        folderDao.setPinned(folderId, isPinned)

    override suspend fun updateFolderOrder(folderIds: List<Long>) {
        folderIds.forEachIndexed { index, folderId ->
            folderDao.setSortOrder(folderId, index)
        }
    }

    override suspend fun setFlowPinned(flowId: Long, isPinned: Boolean) =
        flowDao.setPinned(flowId, isPinned)

    override suspend fun moveFlowToFolder(flowId: Long, folderId: Long?) =
        flowDao.setFolder(flowId, folderId)

    override suspend fun cloneFlow(flowId: Long): Long? {
        val source = flowDao.getFlowWithPoints(flowId) ?: return null
        val newFlowId = flowDao.insert(
            source.flow.copy(
                id = 0L,
                name = "${source.flow.name} Copy",
                createdAt = System.currentTimeMillis(),
                isPinned = false
            )
        )
        val clonedPoints = source.points
            .sortedBy { it.order }
            .mapIndexed { index, point ->
                normalizePoint(point.copy(id = 0L, flowId = newFlowId, order = index))
            }
        pointDao.insertAll(clonedPoints)
        return newFlowId
    }

    // ── JSON import / export ───────────────────────────────────────────────

    /** Serialization wrapper used for export/import. Version 3 adds folder ordering. */
    private data class ExportPayload(
        val version: Int? = 3,
        val folders: List<HistoryFolderEntity>? = emptyList(),
        val flows: List<ClickFlowEntity>? = emptyList(),
        val points: List<ClickPointEntity>? = emptyList()
    )

    override suspend fun exportToJson(): String {
        val allFlows = flowDao.getAllFlows().first()
        val allFolders = folderDao.getAllFoldersSnapshot()
        val allPoints = mutableListOf<ClickPointEntity>()
        allFlows.forEach { f ->
            flowDao.getFlowWithPoints(f.id)?.points?.let { allPoints.addAll(it) }
        }
        return gson.toJson(ExportPayload(folders = allFolders, flows = allFlows, points = allPoints))
    }

    override suspend fun importFromJson(json: String) {
        val payload: ExportPayload = gson.fromJson(json, ExportPayload::class.java) ?: return
        val folderIdMap = mutableMapOf<Long, Long>()
        payload.folders.orEmpty().forEach { importedFolder ->
            val newFolderId = folderDao.insert(importedFolder.copy(id = 0L))
            folderIdMap[importedFolder.id] = newFolderId
        }
        payload.flows.orEmpty().forEach { importedFlow ->
            val mappedFolderId = importedFlow.folderId?.let { folderIdMap[it] }
            val newId = createFlow(importedFlow.copy(id = 0L, folderId = mappedFolderId))
            if (newId > 0) {
                val mappedPoints = payload.points.orEmpty()
                    .filter { it.flowId == importedFlow.id }
                    .mapIndexed { idx, p ->
                        normalizePoint(p.copy(id = 0L, flowId = newId, order = idx))
                    }
                pointDao.insertAll(mappedPoints)
            }
        }
    }

    private fun normalizePoint(point: ClickPointEntity): ClickPointEntity {
        val type = ClickActionType.normalize(point.type)
        val durationMs = when (type) {
            ClickActionType.LONG_PRESS -> point.durationMs
                .takeIf { it >= MIN_LONG_PRESS_DURATION_MS }
                ?: DEFAULT_LONG_PRESS_DURATION_MS
            else -> point.durationMs.takeIf { it > 0L } ?: DEFAULT_TAP_DURATION_MS
        }
        val swipeDurationMs = point.swipeDurationMs.takeIf { it > 0L } ?: DEFAULT_SWIPE_DURATION_MS
        return point.copy(
            type = type,
            durationMs = durationMs,
            swipeDurationMs = swipeDurationMs
        )
    }
}
