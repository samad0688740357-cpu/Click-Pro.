package id.biz.rrndev.autoclicker.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.biz.rrndev.autoclicker.R
import id.biz.rrndev.autoclicker.data.entity.ClickPointEntity
import id.biz.rrndev.autoclicker.data.model.ClickActionType
import id.biz.rrndev.autoclicker.databinding.ItemPointBinding

class PointAdapter(
    private val onEdit: (ClickPointEntity, Int) -> Unit,
    private val onDelete: (Int) -> Unit
) : ListAdapter<ClickPointEntity, PointAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(val binding: ItemPointBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemPointBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val point = getItem(position)
        val ctx = holder.binding.root.context

        holder.binding.tvPointOrder.text = (position + 1).toString()

        val typeName = when (ClickActionType.normalize(point.type)) {
            ClickActionType.SWIPE -> ctx.getString(R.string.action_swipe)
            ClickActionType.LONG_PRESS -> ctx.getString(R.string.action_long_press)
            else -> ctx.getString(R.string.action_click)
        }
        holder.binding.tvPointType.text = typeName

        holder.binding.tvPointDetail.text = when (ClickActionType.normalize(point.type)) {
            ClickActionType.SWIPE -> ctx.getString(
                R.string.point_detail_swipe,
                point.swipeDurationMs,
                point.delayMs
            )
            ClickActionType.LONG_PRESS -> ctx.getString(
                R.string.point_detail_long_press,
                point.durationMs,
                point.delayMs
            )
            else -> ctx.getString(
                R.string.point_detail_click,
                point.delayMs,
                point.repeatCount
            )
        }

        holder.binding.btnEditPoint.setOnClickListener { onEdit(point, holder.bindingAdapterPosition) }
        holder.binding.btnDeletePoint.setOnClickListener { onDelete(holder.bindingAdapterPosition) }
    }

    object DiffCallback : DiffUtil.ItemCallback<ClickPointEntity>() {
        override fun areItemsTheSame(old: ClickPointEntity, new: ClickPointEntity) =
            old.id == new.id && old.order == new.order
        override fun areContentsTheSame(old: ClickPointEntity, new: ClickPointEntity) =
            old == new
    }
}
