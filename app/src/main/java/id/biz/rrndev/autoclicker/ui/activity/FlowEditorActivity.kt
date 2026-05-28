package id.biz.rrndev.autoclicker.ui.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.biz.rrndev.autoclicker.AutoClickerApp
import id.biz.rrndev.autoclicker.R
import id.biz.rrndev.autoclicker.data.entity.ClickFlowEntity
import id.biz.rrndev.autoclicker.data.entity.ClickPointEntity
import id.biz.rrndev.autoclicker.data.model.ClickActionType
import id.biz.rrndev.autoclicker.data.model.DEFAULT_LONG_PRESS_DURATION_MS
import id.biz.rrndev.autoclicker.data.model.DEFAULT_SWIPE_DURATION_MS
import id.biz.rrndev.autoclicker.data.model.DEFAULT_TAP_DURATION_MS
import id.biz.rrndev.autoclicker.data.model.MIN_LONG_PRESS_DURATION_MS
import id.biz.rrndev.autoclicker.databinding.ActivityFlowEditorBinding
import id.biz.rrndev.autoclicker.databinding.DialogEditPointBinding
import id.biz.rrndev.autoclicker.ui.adapter.PointAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FlowEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FLOW_ID = "flow_id"
    }

    private lateinit var binding: ActivityFlowEditorBinding
    private lateinit var pointAdapter: PointAdapter

    private var flowId: Long = 0L
    private val points = mutableListOf<ClickPointEntity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFlowEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        flowId = intent.getLongExtra(EXTRA_FLOW_ID, 0L)

        setupToolbar()
        setupRecycler()
        setupButtons()

        if (flowId != 0L) {
            loadFlow(flowId)
        } else {
            binding.toolbar.title = getString(R.string.new_flow)
            binding.etFlowName.setText(getString(R.string.default_configuration_name))
            binding.etLoopCount.setText("1")
            binding.etLoopDelay.setText("500")
            updatePointCountLabel()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecycler() {
        pointAdapter = PointAdapter(
            onEdit = { point, position -> showEditPointDialog(point, position) },
            onDelete = { position ->
                points.removeAt(position)
                submitPoints()
            }
        )
        binding.recyclerPoints.adapter = pointAdapter
    }

    private fun setupButtons() {
        binding.btnAddPoint.setOnClickListener {
            showEditPointDialog(null, -1)
        }
        binding.btnSave.setOnClickListener {
            saveFlow()
        }
        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun loadFlow(id: Long) {
        lifecycleScope.launch {
            val flowWithPoints = withContext(Dispatchers.IO) {
                (application as AutoClickerApp).repository.getFlowWithPoints(id)
            }
            if (flowWithPoints == null) {
                finish()
                return@launch
            }
            binding.toolbar.title = getString(R.string.edit_flow)
            binding.etFlowName.setText(flowWithPoints.flow.name)
            binding.etLoopCount.setText(flowWithPoints.flow.loopCount.toString())
            binding.etLoopDelay.setText(flowWithPoints.flow.loopDelayMs.toString())
            points.clear()
            points.addAll(flowWithPoints.points.sortedBy { it.order })
            submitPoints()
        }
    }

    private fun saveFlow() {
        val name = binding.etFlowName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.flow_name_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val loopCount = binding.etLoopCount.text.toString().toIntOrNull() ?: 1
        val loopDelay = binding.etLoopDelay.text.toString().toLongOrNull() ?: 500L

        val flow = ClickFlowEntity(
            id = flowId,
            name = name,
            loopCount = loopCount,
            loopDelayMs = loopDelay
        )

        lifecycleScope.launch {
            val repository = (application as AutoClickerApp).repository
            withContext(Dispatchers.IO) {
                if (flowId == 0L) {
                    val newId = repository.createFlow(flow)
                    repository.saveFlowWithPoints(flow.copy(id = newId), points)
                } else {
                    repository.saveFlowWithPoints(flow, points)
                }
            }
            Toast.makeText(this@FlowEditorActivity, R.string.flow_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showEditPointDialog(existing: ClickPointEntity?, position: Int) {
        val dialogBinding = DialogEditPointBinding.inflate(LayoutInflater.from(this))
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        // Default values
        var selectedType = existing?.type ?: ClickActionType.CLICK
        val defaultDelay = existing?.delayMs ?: 300L
        val defaultRepeat = existing?.repeatCount ?: 1
        val defaultDuration = existing?.durationMs ?: DEFAULT_LONG_PRESS_DURATION_MS
        val defaultSwipeDuration = existing?.swipeDurationMs ?: DEFAULT_SWIPE_DURATION_MS

        dialogBinding.etDelay.setText(defaultDelay.toString())
        dialogBinding.etRepeat.setText(defaultRepeat.toString())
        dialogBinding.etDuration.setText(defaultDuration.toString())
        dialogBinding.etSwipeDuration.setText(defaultSwipeDuration.toString())

        if (existing != null) {
            dialogBinding.tvDialogTitle.text = getString(R.string.edit_target)
        }

        fun refreshTypeSelection(type: Int) {
            selectedType = type
            val accentColor = ContextCompat.getColor(this, R.color.color_accent)
            val outlineColor = ContextCompat.getColor(this, R.color.color_outline)
            val strokePx2 = (2 * resources.displayMetrics.density).toInt()
            val strokePx1 = (1 * resources.displayMetrics.density).toInt()

            fun applyCard(card: MaterialCardView, selected: Boolean) {
                card.strokeColor = if (selected) accentColor else outlineColor
                card.strokeWidth = if (selected) strokePx2 else strokePx1
            }
            applyCard(dialogBinding.cardClick, type == ClickActionType.CLICK)
            applyCard(dialogBinding.cardSwipe, type == ClickActionType.SWIPE)
            applyCard(dialogBinding.cardLongPress, type == ClickActionType.LONG_PRESS)

            dialogBinding.ivClickSelected.visibility =
                if (type == ClickActionType.CLICK) View.VISIBLE else View.GONE
            dialogBinding.ivSwipeSelected.visibility =
                if (type == ClickActionType.SWIPE) View.VISIBLE else View.GONE
            dialogBinding.ivLongPressSelected.visibility =
                if (type == ClickActionType.LONG_PRESS) View.VISIBLE else View.GONE

            dialogBinding.tilDuration.visibility =
                if (type == ClickActionType.LONG_PRESS) View.VISIBLE else View.GONE
            dialogBinding.tilSwipeDuration.visibility =
                if (type == ClickActionType.SWIPE) View.VISIBLE else View.GONE

            val typeName = when (type) {
                ClickActionType.SWIPE -> getString(R.string.action_swipe)
                ClickActionType.LONG_PRESS -> getString(R.string.action_long_press)
                else -> getString(R.string.action_click)
            }
            dialogBinding.tvPointSummary.text = getString(R.string.action_summary, typeName)

            val headerIcon = when (type) {
                ClickActionType.SWIPE -> R.drawable.ic_swipe
                ClickActionType.LONG_PRESS -> R.drawable.ic_long_press
                else -> R.drawable.ic_touch
            }
            dialogBinding.ivPointHeader.setImageResource(headerIcon)

            val longPressDurationMs = dialogBinding.etDuration.text.toString().toLongOrNull()
                ?: DEFAULT_LONG_PRESS_DURATION_MS
            dialogBinding.tvLongPressDesc.text =
                getString(R.string.action_long_press_desc, longPressDurationMs)
        }

        refreshTypeSelection(selectedType)

        dialogBinding.cardClick.setOnClickListener { refreshTypeSelection(ClickActionType.CLICK) }
        dialogBinding.cardSwipe.setOnClickListener { refreshTypeSelection(ClickActionType.SWIPE) }
        dialogBinding.cardLongPress.setOnClickListener {
            refreshTypeSelection(ClickActionType.LONG_PRESS)
        }

        dialogBinding.btnCancelPoint.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnApplyPoint.setOnClickListener {
            val delay = dialogBinding.etDelay.text.toString().toLongOrNull() ?: 300L
            val repeat = dialogBinding.etRepeat.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1

            val durationMs: Long = when (selectedType) {
                ClickActionType.LONG_PRESS -> {
                    val d = dialogBinding.etDuration.text.toString().toLongOrNull()
                        ?: DEFAULT_LONG_PRESS_DURATION_MS
                    if (d < MIN_LONG_PRESS_DURATION_MS) {
                        Toast.makeText(this, R.string.long_press_duration_invalid, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    d
                }
                else -> DEFAULT_TAP_DURATION_MS
            }

            val swipeDurationMs: Long = if (selectedType == ClickActionType.SWIPE) {
                dialogBinding.etSwipeDuration.text.toString().toLongOrNull()
                    ?: DEFAULT_SWIPE_DURATION_MS
            } else DEFAULT_SWIPE_DURATION_MS

            val newPoint = if (existing != null) {
                existing.copy(
                    type = selectedType,
                    delayMs = delay,
                    repeatCount = repeat,
                    durationMs = durationMs,
                    swipeDurationMs = swipeDurationMs
                )
            } else {
                ClickPointEntity(
                    flowId = flowId,
                    x = 0f,
                    y = 0f,
                    type = selectedType,
                    delayMs = delay,
                    repeatCount = repeat,
                    durationMs = durationMs,
                    swipeDurationMs = swipeDurationMs,
                    order = points.size
                )
            }

            if (existing != null && position >= 0) {
                points[position] = newPoint
            } else {
                points.add(newPoint)
            }
            submitPoints()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun submitPoints() {
        pointAdapter.submitList(points.toList())
        updatePointCountLabel()
        binding.viewPointsEmpty.visibility = if (points.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updatePointCountLabel() {
        binding.tvPointCount.text = getString(R.string.point_count, points.size)
    }
}
