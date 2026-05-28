package id.biz.rrndev.autoclicker.ui.adapter

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import id.biz.rrndev.autoclicker.data.entity.HistoryFolderEntity
import id.biz.rrndev.autoclicker.databinding.ItemManageFolderBinding
import java.util.Collections

class ManageFoldersAdapter(
    private val onRename: (HistoryFolderEntity) -> Unit,
    private val onDelete: (HistoryFolderEntity) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onOrderChanged: (List<Long>) -> Unit
) : RecyclerView.Adapter<ManageFoldersAdapter.ViewHolder>() {

    private val folders = mutableListOf<HistoryFolderEntity>()

    class ViewHolder(val binding: ItemManageFolderBinding) : RecyclerView.ViewHolder(binding.root)

    fun submitFolders(newFolders: List<HistoryFolderEntity>) {
        folders.clear()
        folders.addAll(newFolders)
        notifyDataSetChanged()
    }

    fun moveFolder(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition !in folders.indices || toPosition !in folders.indices) return false
        if (fromPosition < toPosition) {
            for (index in fromPosition until toPosition) {
                Collections.swap(folders, index, index + 1)
            }
        } else {
            for (index in fromPosition downTo toPosition + 1) {
                Collections.swap(folders, index, index - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    fun dispatchOrderChanged() {
        onOrderChanged(folders.map { it.id })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemManageFolderBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val folder = folders[position]
        holder.binding.tvFolderName.text = folder.name
        holder.binding.btnRenameFolder.setOnClickListener { onRename(folder) }
        holder.binding.btnDeleteFolder.setOnClickListener { onDelete(folder) }
        holder.binding.ivDragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                onStartDrag(holder)
            }
            false
        }
    }

    override fun getItemCount(): Int = folders.size
}
