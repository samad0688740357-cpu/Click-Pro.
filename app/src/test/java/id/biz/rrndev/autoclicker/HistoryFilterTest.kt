package id.biz.rrndev.autoclicker

import id.biz.rrndev.autoclicker.data.db.relation.FlowWithPoints
import id.biz.rrndev.autoclicker.data.entity.ClickFlowEntity
import id.biz.rrndev.autoclicker.data.entity.HistoryFolderEntity
import id.biz.rrndev.autoclicker.ui.history.HistoryFilter
import id.biz.rrndev.autoclicker.ui.history.HistoryQuery
import id.biz.rrndev.autoclicker.ui.history.HistorySortMode
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryFilterTest {

    @Test
    fun freeHistoryStaysFlatAndDateDescending() {
        val flows = listOf(
            flow(id = 1, name = "Older", createdAt = 10L),
            flow(id = 2, name = "Newer", createdAt = 20L, folderId = 7L, isPinned = true)
        )

        val result = HistoryFilter.apply(
            flows = flows,
            folders = listOf(HistoryFolderEntity(id = 7L, name = "Work")),
            query = HistoryQuery(
                isPremium = false,
                search = "missing",
                folderFilter = 7L,
                sortMode = HistorySortMode.NAME,
                ascending = true
            )
        )

        assertEquals(listOf("Newer", "Older"), result.map { it.flow.name })
    }

    @Test
    fun premiumHistorySearchesFolderNameAndKeepsPinnedFirst() {
        val flows = listOf(
            flow(id = 1, name = "alpha", createdAt = 30L, folderId = 7L),
            flow(id = 2, name = "Beta", createdAt = 20L, folderId = 7L, isPinned = true),
            flow(id = 3, name = "Gamma", createdAt = 10L)
        )

        val result = HistoryFilter.apply(
            flows = flows,
            folders = listOf(HistoryFolderEntity(id = 7L, name = "Client Work")),
            query = HistoryQuery(
                isPremium = true,
                search = "client",
                sortMode = HistorySortMode.NAME,
                ascending = true
            )
        )

        assertEquals(listOf("Beta", "alpha"), result.map { it.flow.name })
    }

    @Test
    fun premiumHistoryCanFilterUncategorizedAndSortNameCaseInsensitive() {
        val flows = listOf(
            flow(id = 1, name = "zeta", createdAt = 10L),
            flow(id = 2, name = "Alpha", createdAt = 20L),
            flow(id = 3, name = "middle", createdAt = 30L, folderId = 7L)
        )

        val result = HistoryFilter.apply(
            flows = flows,
            folders = emptyList(),
            query = HistoryQuery(
                isPremium = true,
                folderFilter = HistoryFilter.UNCATEGORIZED_FOLDER_ID,
                sortMode = HistorySortMode.NAME,
                ascending = true
            )
        )

        assertEquals(listOf("Alpha", "zeta"), result.map { it.flow.name })
    }

    private fun flow(
        id: Long,
        name: String,
        createdAt: Long,
        folderId: Long? = null,
        isPinned: Boolean = false
    ): FlowWithPoints = FlowWithPoints(
        flow = ClickFlowEntity(
            id = id,
            name = name,
            createdAt = createdAt,
            folderId = folderId,
            isPinned = isPinned
        ),
        points = emptyList()
    )
}
