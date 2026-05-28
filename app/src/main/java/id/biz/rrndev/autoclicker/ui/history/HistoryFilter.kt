package id.biz.rrndev.autoclicker.ui.history

import id.biz.rrndev.autoclicker.data.db.relation.FlowWithPoints
import id.biz.rrndev.autoclicker.data.entity.HistoryFolderEntity
import java.util.Locale

enum class HistorySortMode {
    DATE,
    NAME
}

data class HistoryQuery(
    val isPremium: Boolean,
    val search: String = "",
    val folderFilter: Long? = null,
    val sortMode: HistorySortMode = HistorySortMode.DATE,
    val ascending: Boolean = false
)

object HistoryFilter {

    const val UNCATEGORIZED_FOLDER_ID = 0L

    fun apply(
        flows: List<FlowWithPoints>,
        folders: List<HistoryFolderEntity>,
        query: HistoryQuery
    ): List<FlowWithPoints> {
        if (!query.isPremium) {
            return flows.sortedByDescending { it.flow.createdAt }
        }

        val folderNames = folders.associate { it.id to it.name }
        val searchText = query.search.trim().lowercase(Locale.ROOT)

        val filtered = flows.filter { item ->
            val matchesFolder = when (query.folderFilter) {
                null -> true
                UNCATEGORIZED_FOLDER_ID -> item.flow.folderId == null
                else -> item.flow.folderId == query.folderFilter
            }
            val matchesSearch = searchText.isEmpty() ||
                item.flow.name.lowercase(Locale.ROOT).contains(searchText) ||
                item.flow.folderId?.let { folderNames[it]?.lowercase(Locale.ROOT)?.contains(searchText) } == true
            matchesFolder && matchesSearch
        }

        val sorted = when (query.sortMode) {
            HistorySortMode.DATE -> {
                if (query.ascending) filtered.sortedBy { it.flow.createdAt }
                else filtered.sortedByDescending { it.flow.createdAt }
            }
            HistorySortMode.NAME -> {
                if (query.ascending) filtered.sortedBy { it.flow.name.lowercase(Locale.ROOT) }
                else filtered.sortedByDescending { it.flow.name.lowercase(Locale.ROOT) }
            }
        }

        return sorted.sortedByDescending { it.flow.isPinned }
    }
}
