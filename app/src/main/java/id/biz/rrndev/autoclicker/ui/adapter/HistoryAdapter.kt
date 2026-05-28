package id.biz.rrndev.autoclicker.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.biz.rrndev.autoclicker.R
import id.biz.rrndev.autoclicker.data.db.relation.FlowWithPoints
import id.biz.rrndev.autoclicker.data.entity.ClickFlowEntity
import id.biz.rrndev.autoclicker.data.model.ClickActionType
import id.biz.rrndev.autoclicker.databinding.ItemHistoryBinding

class HistoryAdapter(
    private val onLoad: (ClickFlowEntity) -> Unit,
    private val onEdit: (ClickFlowEntity) -> Unit,
    private val onDelete: (ClickFlowEntity) -> Unit,
    private val onPin: (ClickFlowEntity) -> Unit,
    private val onClone: (ClickFlowEntity) -> Unit,
    private val onMoveFolder: (ClickFlowEntity) -> Unit
) : ListAdapter<FlowWithPoints, HistoryAdapter.ViewHolder>(DiffCallback) {

    private var isPremium = false
    private var folderNames: Map<Long, String> = emptyMap()

    class ViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    fun setPremiumUi(isPremium: Boolean, folderNames: Map<Long, String>) {
        this.isPremium = isPremium
        this.folderNames = folderNames
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val flow = item.flow
        val context = holder.binding.root.context
        val actionTypes = item.points.map { ClickActionType.normalize(it.type) }.distinct()
        holder.binding.tvFlowName.text = flow.name
        holder.binding.tvChipLoops.text =
            if (flow.loopCount == 0) context.getString(R.string.infinite_loop)
            else context.getString(R.string.loop_count, flow.loopCount)
        holder.binding.tvChipMode.text =
            if (item.points.size > 1) context.getString(R.string.chip_multi)
            else context.getString(R.string.chip_single)
        holder.binding.tvFlowActions.text = actionTypes.joinToString(", ") { actionType ->
            when (actionType) {
                ClickActionType.SWIPE -> context.getString(R.string.action_swipe)
                ClickActionType.LONG_PRESS -> context.getString(R.string.action_long_press)
                else -> context.getString(R.string.action_click)
            }
        }
        holder.binding.ivFlowIcon.setImageResource(
            when {
                actionTypes.size == 1 && actionTypes.first() == ClickActionType.SWIPE -> R.drawable.ic_swipe
                actionTypes.size == 1 && actionTypes.first() == ClickActionType.LONG_PRESS -> R.drawable.ic_long_press
                else -> R.drawable.ic_touch
            }
        )
        holder.binding.btnLoad.setOnClickListener { onLoad(flow) }
        holder.binding.btnEdit.setOnClickListener { onEdit(flow) }

        val folderName = flow.folderId?.let { folderNames[it] }
        holder.binding.tvFolderName.visibility =
            if (isPremium && folderName != null) View.VISIBLE else View.GONE
        holder.binding.tvFolderName.text = folderName?.let { context.getString(R.string.folder_name_chip, it) }
        holder.binding.btnPin.visibility = if (isPremium) View.VISIBLE else View.GONE
        holder.binding.btnPin.contentDescription =
            if (flow.isPinned) context.getString(R.string.unpin) else context.getString(R.string.pin)
        holder.binding.btnPin.setColorFilter(
            context.getColor(if (flow.isPinned) R.color.color_accent else R.color.text_muted)
        )
        holder.binding.btnPin.setOnClickListener { onPin(flow) }
        holder.binding.btnOverflow.setOnClickListener { anchor ->
            PopupMenu(context, anchor).apply {
                menuInflater.inflate(R.menu.menu_history_item, menu)
                menu.findItem(R.id.action_folder).isVisible = isPremium
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_clone -> {
                            onClone(flow)
                            true
                        }
                        R.id.action_folder -> {
                            onMoveFolder(flow)
                            true
                        }
                        R.id.action_delete -> {
                            onDelete(flow)
                            true
                        }
                        else -> false
                    }
                }
                show()
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<FlowWithPoints>() {
        override fun areItemsTheSame(oldItem: FlowWithPoints, newItem: FlowWithPoints) =
            oldItem.flow.id == newItem.flow.id
        override fun areContentsTheSame(oldItem: FlowWithPoints, newItem: FlowWithPoints) =
            oldItem == newItem
    }
}
