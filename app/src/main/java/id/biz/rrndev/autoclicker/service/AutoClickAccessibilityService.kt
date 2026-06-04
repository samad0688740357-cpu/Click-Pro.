package id.biz.rrndev.autoclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.*
import android.content.Context
import android.content.Intent
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import id.biz.rrndev.autoclicker.AutoClickerApp
import id.biz.rrndev.autoclicker.R
import id.biz.rrndev.autoclicker.data.entity.ClickFlowEntity
import id.biz.rrndev.autoclicker.data.entity.ClickPointEntity
import id.biz.rrndev.autoclicker.data.model.ClickActionType
import id.biz.rrndev.autoclicker.data.model.DEFAULT_LONG_PRESS_DURATION_MS
import id.biz.rrndev.autoclicker.data.model.DEFAULT_SWIPE_DURATION_MS
import id.biz.rrndev.autoclicker.data.model.DEFAULT_TAP_DURATION_MS
import id.biz.rrndev.autoclicker.data.model.MIN_LONG_PRESS_DURATION_MS
import id.biz.rrndev.autoclicker.data.repository.FlowRepository
import id.biz.rrndev.autoclicker.ui.activity.MainActivity
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

class AutoClickAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: WeakReference<AutoClickAccessibilityService>? = null

        /**
         * True only between onServiceConnected() and the accessibility service unbinding.
         * This is the authoritative live-running flag.
         */
        @Volatile
        private var isConnected: Boolean = false

        /**
         * Set to true the first time onServiceConnected() fires in this process lifetime.
         * Once true, we know the service has bound at least once — so if isConnected is
         * false, the service was genuinely stopped (e.g. via shortcut), not just not-yet-bound.
         * Used by PermissionUtils to suppress the Settings.Secure fallback after the first bind.
         */
        @Volatile
        var hasEverConnected: Boolean = false
            private set

        fun isServiceEnabled(): Boolean = isConnected && instance?.get() != null
        fun isRunning(): Boolean = instance?.get()?.executionJob?.isActive == true

        fun stopClicking() {
            instance?.get()?.stopExecution()
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var repository: FlowRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var executionJob: Job? = null

    // Settings
    private var maxClicks = 0
    private var clickIntervalMs = 1000L

    // Overlay Views
    private var controlView: View? = null
    private val targetReticles = mutableListOf<ReticleInfo>()
    private var isMultiMode = false
    private var isReticlesVisible = true
    private var singleActionType = ClickActionType.CLICK
    private var areOverlayControlsExpanded = true

    // Reticle Data Class
    data class ReticleInfo(
        val view: View, val params: WindowManager.LayoutParams, var x: Int, var y: Int,
        var actionType: Int = ClickActionType.CLICK,
        var pressDurationMs: Long = DEFAULT_TAP_DURATION_MS,
        var endView: View? = null,
        var endParams: WindowManager.LayoutParams? = null,
        var endX: Int = 0,
        var endY: Int = 0,
        var swipeDurationMs: Long = DEFAULT_SWIPE_DURATION_MS
    )

    override fun onServiceConnected() {
        isConnected = true
        hasEverConnected = true
        instance = WeakReference(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        repository = (application as AutoClickerApp).repository
        loadSettings()

        val prefs = getSharedPreferences("autoclicker_prefs", MODE_PRIVATE)
        isMultiMode = prefs.getBoolean("is_multi_target_mode", false)
        areOverlayControlsExpanded = prefs.getBoolean("are_overlay_controls_expanded", true)
        singleActionType = ClickActionType.normalize(
            prefs.getInt("single_action_type", ClickActionType.CLICK)
        )
        val isOverlayActive = prefs.getBoolean("is_overlay_active", false)

        if (isOverlayActive) {
            showControlOverlay()
            if (targetReticles.isEmpty()) {
                addTargetReticle(actionType = if (isMultiMode) ClickActionType.CLICK else singleActionType)
            }
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("autoclicker_prefs", MODE_PRIVATE)
        maxClicks = prefs.getInt("max_clicks", 0)
        clickIntervalMs = prefs.getLong("click_interval", 1000L).coerceAtLeast(100L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_SHOW_OVERLAY" -> {
                val prefs = getSharedPreferences("autoclicker_prefs", MODE_PRIVATE)
                prefs.edit().putBoolean("is_overlay_active", true).apply()
                areOverlayControlsExpanded = prefs.getBoolean("are_overlay_controls_expanded", true)
                val newMode = prefs.getBoolean("is_multi_target_mode", false)
                if (newMode != isMultiMode || controlView == null) {
                    isMultiMode = newMode
                    stopExecution()
                    hideControlOverlay()
                    removeAllReticles()
                    showControlOverlay()
                    addTargetReticle(
                        actionType = if (isMultiMode) ClickActionType.CLICK else singleActionType
                    )
                }
            }
            "ACTION_UPDATE_SETTINGS" -> {
                loadSettings()
            }
            "ACTION_LOAD_FLOW" -> {
                val flowId = intent.getLongExtra("flow_id", -1L)
                if (flowId != -1L) {
                    serviceScope.launch {
                        val flowWithPoints = repository.getFlowWithPoints(flowId)
                        if (flowWithPoints != null) {
                            val prefs = getSharedPreferences("autoclicker_prefs", MODE_PRIVATE)
                            prefs.edit().putBoolean("is_overlay_active", true).apply()

                            stopExecution()
                            removeAllReticles()
                            isMultiMode = flowWithPoints.points.size > 1
                            val loadedSingleActionType = flowWithPoints.points.firstOrNull()
                                ?.let { ClickActionType.normalize(it.type) }
                                ?: ClickActionType.CLICK
                            if (!isMultiMode) {
                                singleActionType = loadedSingleActionType
                            }
                            showControlOverlay()
                            flowWithPoints.points.forEach { point ->
                                when (ClickActionType.normalize(point.type)) {
                                    ClickActionType.SWIPE -> {
                                        addTargetReticle(
                                            actionType = ClickActionType.SWIPE,
                                            initialX = point.x.toInt(),
                                            initialY = point.y.toInt(),
                                            initialEndX = point.endX.toInt(),
                                            initialEndY = point.endY.toInt(),
                                            swipeDurationMs = point.swipeDurationMs
                                        )
                                    }
                                    ClickActionType.LONG_PRESS -> {
                                        addTargetReticle(
                                            actionType = ClickActionType.LONG_PRESS,
                                            initialX = point.x.toInt(),
                                            initialY = point.y.toInt(),
                                            pressDurationMs = point.durationMs
                                        )
                                    }
                                    else -> {
                                        addTargetReticle(
                                            actionType = ClickActionType.CLICK,
                                            initialX = point.x.toInt(),
                                            initialY = point.y.toInt(),
                                            pressDurationMs = point.durationMs
                                        )
                                    }
                                }
                            }
                            getSharedPreferences("autoclicker_prefs", MODE_PRIVATE).edit()
                                .putBoolean("is_multi_target_mode", isMultiMode)
                                .putInt("single_action_type", singleActionType)
                                .apply()
                        }
                    }
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* not needed */ }
    override fun onInterrupt() { stopExecution() }

    override fun onUnbind(intent: Intent?): Boolean {
        handleServiceDisconnected()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handleServiceDisconnected()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleServiceDisconnected() {
        serviceScope.coroutineContext.cancelChildren()
        stopExecution()
        hideControlOverlay()
        removeAllReticles()
        isConnected = false
        instance = null
    }

    // ── Execution Control ──────────────────────────────────────────────────

    private fun toggleExecution() {
        if (isRunning()) {
            stopExecution()
        } else {
            startExecution()
        }
    }

    private fun startExecution() {
        if (targetReticles.isEmpty()) return

        executionJob = serviceScope.launch {
            postStatusNotification(getString(R.string.notification_running_text))
            updatePlayPauseButton(true)

            val reticleSize = (48 * resources.displayMetrics.density).toInt()
            var flowCount = 0

            while (isActive) {
                for (reticle in targetReticles) {
                    ensureActive()
                    val clickX = reticle.x + reticleSize / 2f
                    val clickY = reticle.y + reticleSize / 2f

                    when (ClickActionType.normalize(reticle.actionType)) {
                        ClickActionType.SWIPE -> {
                            val endX = reticle.endX + reticleSize / 2f
                            val endY = reticle.endY + reticleSize / 2f
                            showClickMarker(clickX, clickY)
                            showClickMarker(endX, endY)
                            performSwipe(clickX, clickY, endX, endY, reticle.swipeDurationMs)
                        }
                        ClickActionType.LONG_PRESS -> {
                            showClickMarker(clickX, clickY)
                            performTap(clickX, clickY, reticle.pressDurationMs)
                        }
                        else -> {
                            showClickMarker(clickX, clickY)
                            performTap(clickX, clickY, reticle.pressDurationMs)
                        }
                    }

                    delay(clickIntervalMs)
                }

                flowCount++
                if (maxClicks > 0 && flowCount >= maxClicks) {
                    stopExecution()
                    return@launch
                }
            }
        }
    }

    private fun stopExecution() {
        executionJob?.cancel()
        executionJob = null
        updatePlayPauseButton(false)
        cancelNotification()
    }

    private suspend fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long) {
        suspendCancellableCoroutine<Unit> { cont ->
            val path = Path().apply { moveTo(startX, startY); lineTo(endX, endY) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(1L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(Unit)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(Unit)
                }
            }

            if (!dispatchGesture(gesture, callback, null)) {
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    private suspend fun performTap(x: Float, y: Float, durationMs: Long) {
        suspendCancellableCoroutine<Unit> { cont ->
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(1L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(Unit)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(Unit)
                }
            }

            if (!dispatchGesture(gesture, callback, null)) {
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    // ── Overlay UI Management ──────────────────────────────────────────────

    private fun showControlOverlay() {
        if (controlView != null) {
            hideControlOverlay()
        }

        val layoutRes = if (isMultiMode) R.layout.overlay_control_multi else R.layout.overlay_control_single
        val themedContext = ContextThemeWrapper(this, R.style.Theme_AutoClicker)
        controlView = LayoutInflater.from(themedContext).inflate(layoutRes, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            x = 0
            y = 0
        }

        controlView?.setOnTouchListener(
            DragTouchListener(
                params = params,
                wm = windowManager,
                target = controlView!!,
                onDragEnd = {}
            )
        )

        controlView?.findViewById<ImageButton>(R.id.btnPlayPause)?.apply {
            setOnClickListener { toggleExecution() }
            setOnLongClickListener {
                if (!areOverlayControlsExpanded) setOverlayControlsExpanded(true)
                true
            }
        }
        controlView?.findViewById<ImageButton>(R.id.btnClose)?.setOnClickListener {
            val prefs = getSharedPreferences("autoclicker_prefs", MODE_PRIVATE)
            prefs.edit().putBoolean("is_overlay_active", false).apply()
            stopExecution()
            hideControlOverlay()
            removeAllReticles()
        }
        controlView?.findViewById<ImageButton>(R.id.btnShowMore)?.setOnClickListener {
            setOverlayControlsExpanded(true)
        }
        controlView?.findViewById<ImageButton>(R.id.btnShowLess)?.setOnClickListener {
            setOverlayControlsExpanded(false)
        }

        controlView?.findViewById<ImageButton>(R.id.btnSettings)?.setOnClickListener {
            showSettingsInOverlay()
        }

        controlView?.findViewById<ImageButton>(R.id.btnSave)?.setOnClickListener {
            showSaveDialog()
        }

        controlView?.findViewById<ImageButton>(R.id.btnVisibility)?.setOnClickListener { toggleReticlesVisibility() }

        if (isMultiMode) {
            controlView?.findViewById<ImageButton>(R.id.btnAdd)?.setOnClickListener {
                addTargetReticle(actionType = ClickActionType.CLICK)
            }
            controlView?.findViewById<ImageButton>(R.id.btnAddSwipe)?.setOnClickListener {
                addTargetReticle(actionType = ClickActionType.SWIPE)
            }
            controlView?.findViewById<ImageButton>(R.id.btnAddLongPress)?.setOnClickListener {
                showLongPressDurationDialog(DEFAULT_LONG_PRESS_DURATION_MS) { duration ->
                    addTargetReticle(
                        actionType = ClickActionType.LONG_PRESS,
                        pressDurationMs = duration
                    )
                }
            }
            controlView?.findViewById<ImageButton>(R.id.btnRemove)?.setOnClickListener { removeLastReticle() }
        } else {
            val btnToggle = controlView?.findViewById<ImageButton>(R.id.btnToggleMode)
            btnToggle?.setOnClickListener {
                targetReticles.firstOrNull()?.let { info ->
                    showReticleActionDialog(info, allowRemove = false)
                }
            }
            updateSingleActionButton()
        }

        setOverlayControlsExpanded(areOverlayControlsExpanded, persist = false)
        windowManager.addView(controlView, params)
    }

    private fun setOverlayControlsExpanded(expanded: Boolean, persist: Boolean = true) {
        areOverlayControlsExpanded = expanded
        controlView?.findViewById<View>(R.id.layoutExpandedControls)?.visibility =
            if (expanded) View.VISIBLE else View.GONE
        controlView?.findViewById<ImageButton>(R.id.btnShowMore)?.visibility =
            if (expanded) View.GONE else View.VISIBLE
        if (persist) {
            getSharedPreferences("autoclicker_prefs", MODE_PRIVATE).edit()
                .putBoolean("are_overlay_controls_expanded", expanded)
                .apply()
        }
    }

    private fun showSaveDialog() {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_AutoClicker)
        val dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_save_config, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etConfigName)

        val dialog = MaterialAlertDialogBuilder(themedContext)
            .setView(dialogView)
            .create()

        dialogView.findViewById<MaterialButton>(R.id.btnCancelConfig).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<MaterialButton>(R.id.btnSaveConfig).setOnClickListener {
            val name = etName.text.toString().ifBlank { getString(R.string.untitled_configuration) }
            saveCurrentFlow(name)
            dialog.dismiss()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        } else {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
        dialog.show()
    }

    private fun saveCurrentFlow(name: String) {
        serviceScope.launch {
            val flow = ClickFlowEntity(name = name, loopCount = maxClicks)
            val flowId = repository.createFlow(flow)
            val points = targetReticles.mapIndexed { index, info ->
                val actionType = ClickActionType.normalize(info.actionType)
                ClickPointEntity(
                    flowId = flowId,
                    x = info.x.toFloat(),
                    y = info.y.toFloat(),
                    order = index,
                    durationMs = if (actionType == ClickActionType.LONG_PRESS) {
                        info.pressDurationMs
                    } else {
                        DEFAULT_TAP_DURATION_MS
                    },
                    type = actionType,
                    endX = if (actionType == ClickActionType.SWIPE) info.endX.toFloat() else 0f,
                    endY = if (actionType == ClickActionType.SWIPE) info.endY.toFloat() else 0f,
                    swipeDurationMs = info.swipeDurationMs
                )
            }
            repository.saveFlowWithPoints(flow.copy(id = flowId), points)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@AutoClickAccessibilityService, R.string.configuration_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSettingsInOverlay() {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_AutoClicker)
        val dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_settings, null)
        val etMaxClicks = dialogView.findViewById<TextInputEditText>(R.id.etMaxClicks)
        val etClickInterval = dialogView.findViewById<TextInputEditText>(R.id.etClickInterval)

        etMaxClicks.setText(maxClicks.toString())
        etClickInterval.setText(clickIntervalMs.toString())

        val dialog = MaterialAlertDialogBuilder(themedContext)
            .setView(dialogView)
            .create()

        dialogView.findViewById<MaterialButton>(R.id.btnCancelSettings).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<MaterialButton>(R.id.btnSaveSettings).setOnClickListener {
            val newMax = etMaxClicks.text.toString().toIntOrNull() ?: 0
            val newInterval = etClickInterval.text.toString().toLongOrNull() ?: 1000L

            if (newInterval < 100) {
                Toast.makeText(this, R.string.interval_too_low, Toast.LENGTH_SHORT).show()
            } else {
                maxClicks = newMax
                clickIntervalMs = newInterval
                val prefs = getSharedPreferences("autoclicker_prefs", MODE_PRIVATE)
                prefs.edit().apply {
                    putInt("max_clicks", maxClicks)
                    putLong("click_interval", clickIntervalMs)
                    apply()
                }
                dialog.dismiss()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        } else {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
        dialog.show()
    }

    private fun hideControlOverlay() {
        controlView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            controlView = null
        }
    }

    private fun updatePlayPauseButton(isRunning: Boolean) {
        val btn = controlView?.findViewById<ImageButton>(R.id.btnPlayPause)
        btn?.setImageResource(if (isRunning) R.drawable.ic_stop else R.drawable.ic_play)
    }

    // ── Reticle Management ─────────────────────────────────────────────────

    private fun addTargetReticle(
        actionType: Int = ClickActionType.CLICK,
        initialX: Int = -1,
        initialY: Int = -1,
        initialEndX: Int = -1,
        initialEndY: Int = -1,
        pressDurationMs: Long = DEFAULT_TAP_DURATION_MS,
        swipeDurationMs: Long = DEFAULT_SWIPE_DURATION_MS
    ) {
        if (!isMultiMode && targetReticles.isNotEmpty()) return

        val themedContext = ContextThemeWrapper(this, R.style.Theme_AutoClicker)
        val reticleView = LayoutInflater.from(themedContext).inflate(R.layout.overlay_target_reticle, null)

        val sizePx = (48 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (initialX == -1) {
                x = resources.displayMetrics.widthPixels / 2 - sizePx / 2
                y = resources.displayMetrics.heightPixels / 2 - sizePx / 2
            } else {
                x = initialX
                y = initialY
            }
        }

        val normalizedActionType = ClickActionType.normalize(actionType)
        val info = ReticleInfo(
            view = reticleView,
            params = params,
            x = params.x,
            y = params.y,
            actionType = normalizedActionType,
            pressDurationMs = if (normalizedActionType == ClickActionType.LONG_PRESS) {
                pressDurationMs.coerceAtLeast(MIN_LONG_PRESS_DURATION_MS)
            } else {
                DEFAULT_TAP_DURATION_MS
            },
            swipeDurationMs = swipeDurationMs.takeIf { it > 0L } ?: DEFAULT_SWIPE_DURATION_MS
        )

        reticleView.setOnTouchListener(
            DragTouchListener(
                params = params,
                wm = windowManager,
                target = reticleView,
                onDragEnd = {
                    info.x = params.x
                    info.y = params.y
                },
                onTap = {
                    showReticleActionDialog(
                        info = info,
                        allowRemove = isMultiMode && targetReticles.size > 1
                    )
                }
            )
        )

        windowManager.addView(reticleView, params)
        targetReticles.add(info)

        if (normalizedActionType == ClickActionType.SWIPE) {
            addEndReticleFor(info, initialEndX, initialEndY)
        }
        if (!isMultiMode) {
            singleActionType = normalizedActionType
            persistSingleActionType()
        }
        refreshReticleBadges()
    }

    private fun addEndReticleFor(info: ReticleInfo, initialEndX: Int = -1, initialEndY: Int = -1) {
        removeEndReticleFor(info)

        val themedContext = ContextThemeWrapper(this, R.style.Theme_AutoClicker)
        val endView = LayoutInflater.from(themedContext).inflate(R.layout.overlay_target_reticle, null)
        val sizePx = (48 * resources.displayMetrics.density).toInt()
        val endParams = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (initialEndX == -1) {
                x = info.x
                y = info.y - (100 * resources.displayMetrics.density).toInt()
            } else {
                x = initialEndX
                y = initialEndY
            }
        }

        info.actionType = ClickActionType.SWIPE
        info.endView = endView
        info.endParams = endParams
        info.endX = endParams.x
        info.endY = endParams.y

        endView.setOnTouchListener(
            DragTouchListener(
                params = endParams,
                wm = windowManager,
                target = endView,
                onDragEnd = {
                    info.endX = endParams.x
                    info.endY = endParams.y
                }
            )
        )

        windowManager.addView(endView, endParams)
        refreshReticleBadges()
    }

    private fun removeEndReticleFor(info: ReticleInfo) {
        info.endView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        info.endView = null
        info.endParams = null
        info.endX = 0
        info.endY = 0
    }

    private fun removeLastReticle() {
        if (targetReticles.size > 1) {
            removeReticle(targetReticles.last())
        }
    }

    private fun removeAllReticles() {
        targetReticles.forEach {
            try { windowManager.removeView(it.view) } catch (_: Exception) {}
            it.endView?.let { end -> try { windowManager.removeView(end) } catch (_: Exception) {} }
        }
        targetReticles.clear()
    }

    private fun removeReticle(info: ReticleInfo) {
        if (!isMultiMode || targetReticles.size <= 1) return

        try { windowManager.removeView(info.view) } catch (_: Exception) {}
        info.endView?.let { end -> try { windowManager.removeView(end) } catch (_: Exception) {} }
        targetReticles.remove(info)
        refreshReticleBadges()
    }

    private fun showReticleActionDialog(info: ReticleInfo, allowRemove: Boolean) {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_AutoClicker)
        val dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_edit_target, null)
        val cardClick = dialogView.findViewById<MaterialCardView>(R.id.cardClick)
        val cardSwipe = dialogView.findViewById<MaterialCardView>(R.id.cardSwipe)
        val cardLongPress = dialogView.findViewById<MaterialCardView>(R.id.cardLongPress)
        val ivClickSelected = dialogView.findViewById<ImageView>(R.id.ivClickSelected)
        val ivSwipeSelected = dialogView.findViewById<ImageView>(R.id.ivSwipeSelected)
        val ivLongPressSelected = dialogView.findViewById<ImageView>(R.id.ivLongPressSelected)
        val tvTargetSummary = dialogView.findViewById<TextView>(R.id.tvTargetSummary)
        val tvLongPressDesc = dialogView.findViewById<TextView>(R.id.tvLongPressDesc)
        val btnRemoveTarget = dialogView.findViewById<MaterialButton>(R.id.btnRemoveTarget)
        val btnCancelTarget = dialogView.findViewById<MaterialButton>(R.id.btnCancelTarget)
        val btnApplyTarget = dialogView.findViewById<MaterialButton>(R.id.btnApplyTarget)
        var pendingActionType = ClickActionType.normalize(info.actionType)
        var pendingLongPressDuration = info.pressDurationMs.coerceAtLeast(MIN_LONG_PRESS_DURATION_MS)

        fun refreshSelection() {
            tvTargetSummary.text = getString(R.string.target_summary, actionLabel(pendingActionType))
            tvLongPressDesc.text = getString(R.string.action_long_press_desc, pendingLongPressDuration)
            highlightSelectedAction(
                selectedActionType = pendingActionType,
                cardClick = cardClick,
                cardSwipe = cardSwipe,
                cardLongPress = cardLongPress,
                ivClickSelected = ivClickSelected,
                ivSwipeSelected = ivSwipeSelected,
                ivLongPressSelected = ivLongPressSelected
            )
        }
        btnRemoveTarget.visibility = if (allowRemove) View.VISIBLE else View.GONE
        refreshSelection()

        val dialog = MaterialAlertDialogBuilder(themedContext)
            .setView(dialogView)
            .create()

        cardClick.setOnClickListener {
            pendingActionType = ClickActionType.CLICK
            refreshSelection()
        }
        cardSwipe.setOnClickListener {
            pendingActionType = ClickActionType.SWIPE
            refreshSelection()
        }
        cardLongPress.setOnClickListener {
            pendingActionType = ClickActionType.LONG_PRESS
            refreshSelection()
        }
        btnCancelTarget.setOnClickListener { dialog.dismiss() }
        btnApplyTarget.setOnClickListener {
            if (pendingActionType == ClickActionType.LONG_PRESS) {
                dialog.dismiss()
                showLongPressDurationDialog(pendingLongPressDuration) { duration ->
                    pendingLongPressDuration = duration
                    applyActionToReticle(info, ClickActionType.LONG_PRESS, pendingLongPressDuration)
                }
            } else {
                applyActionToReticle(info, pendingActionType)
                dialog.dismiss()
            }
        }
        btnRemoveTarget.setOnClickListener {
            if (allowRemove) {
                removeReticle(info)
                dialog.dismiss()
            }
        }
        tvLongPressDesc.setOnClickListener {
            showLongPressDurationDialog(
                initialDurationMs = pendingLongPressDuration
            ) { duration ->
                pendingActionType = ClickActionType.LONG_PRESS
                pendingLongPressDuration = duration
                refreshSelection()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        } else {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
        dialog.show()
    }

    private fun showLongPressDurationDialog(
        initialDurationMs: Long,
        onConfirm: (Long) -> Unit
    ) {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_AutoClicker)
        val dialogView = LayoutInflater.from(themedContext)
            .inflate(R.layout.dialog_long_press_duration, null)
        val etDuration = dialogView.findViewById<TextInputEditText>(R.id.etLongPressDuration)
        etDuration.setText(initialDurationMs.toString())
        dialogView.findViewById<MaterialButton>(R.id.btnPreset500).setOnClickListener {
            etDuration.setText("500")
            etDuration.setSelection(etDuration.text?.length ?: 0)
        }
        dialogView.findViewById<MaterialButton>(R.id.btnPreset1000).setOnClickListener {
            etDuration.setText("1000")
            etDuration.setSelection(etDuration.text?.length ?: 0)
        }
        dialogView.findViewById<MaterialButton>(R.id.btnPreset3000).setOnClickListener {
            etDuration.setText("3000")
            etDuration.setSelection(etDuration.text?.length ?: 0)
        }
        val btnCancelDuration = dialogView.findViewById<MaterialButton>(R.id.btnCancelDuration)
        val btnSaveDuration = dialogView.findViewById<MaterialButton>(R.id.btnSaveDuration)

        val dialog = MaterialAlertDialogBuilder(themedContext)
            .setView(dialogView)
            .create()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        } else {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }

        btnCancelDuration.setOnClickListener { dialog.dismiss() }
        btnSaveDuration.setOnClickListener {
            val duration = etDuration.text?.toString()?.toLongOrNull()
            if (duration == null || duration < MIN_LONG_PRESS_DURATION_MS) {
                Toast.makeText(
                    this,
                    R.string.long_press_duration_invalid,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                onConfirm(duration)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun highlightSelectedAction(
        selectedActionType: Int,
        cardClick: MaterialCardView,
        cardSwipe: MaterialCardView,
        cardLongPress: MaterialCardView,
        ivClickSelected: ImageView,
        ivSwipeSelected: ImageView,
        ivLongPressSelected: ImageView
    ) {
        val selectedColor = ContextCompat.getColor(this, R.color.color_accent)
        val defaultColor = ContextCompat.getColor(this, R.color.color_outline)
        listOf(
            Triple(ClickActionType.CLICK, cardClick, ivClickSelected),
            Triple(ClickActionType.SWIPE, cardSwipe, ivSwipeSelected),
            Triple(ClickActionType.LONG_PRESS, cardLongPress, ivLongPressSelected)
        ).forEach { (actionType, card, checkIcon) ->
            val isSelected = actionType == selectedActionType
            card.strokeColor = if (isSelected) selectedColor else defaultColor
            card.strokeWidth = if (isSelected) {
                (2 * resources.displayMetrics.density).toInt()
            } else {
                (1 * resources.displayMetrics.density).toInt()
            }
            checkIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
        }
    }

    private fun actionLabel(actionType: Int): String = when (ClickActionType.normalize(actionType)) {
        ClickActionType.SWIPE -> getString(R.string.action_swipe)
        ClickActionType.LONG_PRESS -> getString(R.string.action_long_press)
        else -> getString(R.string.action_click)
    }

    private fun applyActionToReticle(
        info: ReticleInfo,
        actionType: Int,
        pressDurationMs: Long = DEFAULT_LONG_PRESS_DURATION_MS
    ) {
        when (ClickActionType.normalize(actionType)) {
            ClickActionType.SWIPE -> {
                info.pressDurationMs = DEFAULT_TAP_DURATION_MS
                addEndReticleFor(info)
            }
            ClickActionType.LONG_PRESS -> {
                removeEndReticleFor(info)
                info.actionType = ClickActionType.LONG_PRESS
                info.pressDurationMs = pressDurationMs.coerceAtLeast(MIN_LONG_PRESS_DURATION_MS)
            }
            else -> {
                removeEndReticleFor(info)
                info.actionType = ClickActionType.CLICK
                info.pressDurationMs = DEFAULT_TAP_DURATION_MS
            }
        }
        if (!isMultiMode) {
            singleActionType = ClickActionType.normalize(actionType)
            persistSingleActionType()
        }
        refreshReticleBadges()
    }

    private fun refreshReticleBadges() {
        targetReticles.forEachIndexed { index, info ->
            val startTv = info.view.findViewById<TextView>(R.id.tvTargetNumber)
            when {
                isMultiMode && ClickActionType.normalize(info.actionType) == ClickActionType.SWIPE -> {
                    startTv.visibility = View.VISIBLE
                    startTv.text = getString(R.string.indexed_swipe_start_marker, index + 1)
                    startTv.setBackgroundResource(R.drawable.bg_badge_green)
                }
                isMultiMode && ClickActionType.normalize(info.actionType) == ClickActionType.LONG_PRESS -> {
                    startTv.visibility = View.VISIBLE
                    startTv.text = getString(R.string.indexed_long_press_marker, index + 1)
                    startTv.setBackgroundResource(R.drawable.bg_badge_orange)
                }
                isMultiMode -> {
                    startTv.visibility = View.VISIBLE
                    startTv.text = (index + 1).toString()
                    startTv.setBackgroundResource(R.drawable.bg_circle_accent)
                }
                ClickActionType.normalize(info.actionType) == ClickActionType.SWIPE -> {
                    startTv.visibility = View.VISIBLE
                    startTv.text = getString(R.string.swipe_start_marker)
                    startTv.setBackgroundResource(R.drawable.bg_badge_green)
                }
                ClickActionType.normalize(info.actionType) == ClickActionType.LONG_PRESS -> {
                    startTv.visibility = View.VISIBLE
                    startTv.text = getString(R.string.long_press_marker)
                    startTv.setBackgroundResource(R.drawable.bg_badge_orange)
                }
                else -> {
                    startTv.visibility = View.GONE
                }
            }

            info.endView?.findViewById<TextView>(R.id.tvTargetNumber)?.apply {
                visibility = View.VISIBLE
                text = if (isMultiMode) {
                    getString(R.string.indexed_swipe_end_marker, index + 1)
                } else {
                    getString(R.string.swipe_end_marker)
                }
                setBackgroundResource(R.drawable.bg_badge_purple)
            }
        }
        updateSingleActionButton()
    }

    private fun updateSingleActionButton() {
        val btn = controlView?.findViewById<ImageButton>(R.id.btnToggleMode) ?: return
        val currentAction = targetReticles.firstOrNull()?.let { ClickActionType.normalize(it.actionType) }
            ?: singleActionType
        btn.setImageResource(
            when (currentAction) {
                ClickActionType.SWIPE -> R.drawable.ic_swipe
                ClickActionType.LONG_PRESS -> R.drawable.ic_long_press
                else -> R.drawable.ic_touch
            }
        )
    }

    private fun persistSingleActionType() {
        getSharedPreferences("autoclicker_prefs", MODE_PRIVATE).edit()
            .putInt("single_action_type", singleActionType)
            .apply()
    }

    private fun toggleReticlesVisibility() {
        isReticlesVisible = !isReticlesVisible
        val visibility = if (isReticlesVisible) View.VISIBLE else View.GONE
        targetReticles.forEach { 
            it.view.visibility = visibility 
            it.endView?.visibility = visibility
        }
        val btn = controlView?.findViewById<ImageButton>(R.id.btnVisibility)
        btn?.setImageResource(if (isReticlesVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off)
    }

    // ── Click Marker ────────────────────────────────────────────────────────

    private fun showClickMarker(centerX: Float, centerY: Float) {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_AutoClicker)
        val markerView = LayoutInflater.from(themedContext).inflate(R.layout.overlay_click_marker, null)

        val sizePx = (48 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (centerX - sizePx / 2f).toInt()
            y = (centerY - sizePx / 2f).toInt()
        }

        markerView.scaleX = 0.3f
        markerView.scaleY = 0.3f
        markerView.alpha = 0f

        try {
            windowManager.addView(markerView, params)
        } catch (_: Exception) {
            return
        }

        val scaleX = ObjectAnimator.ofFloat(markerView, View.SCALE_X, 0.3f, 1.4f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(markerView, View.SCALE_Y, 0.3f, 1.4f, 1.0f)
        val fadeIn = ObjectAnimator.ofFloat(markerView, View.ALPHA, 0f, 0.85f)
        val fadeOut = ObjectAnimator.ofFloat(markerView, View.ALPHA, 0.85f, 0f)
        fadeOut.startDelay = 150

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, fadeIn, fadeOut)
            duration = 300
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    try { windowManager.removeView(markerView) } catch (_: Exception) {}
                }
            })
            start()
        }
    }

    // ── Notification ───────────────────────────────────────────────────────

    private fun postStatusNotification(text: String) {
        val stopIntent = Intent(this, MainActivity::class.java).setAction("ACTION_STOP_FROM_NOTIFICATION")
        val pendingStop = PendingIntent.getActivity(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, AutoClickerApp.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle(getString(R.string.notification_running_title))
            .setContentText(text)
            .setOngoing(true)
            .addAction(R.drawable.ic_stop, getString(R.string.stop), pendingStop)
            .build()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(AutoClickerApp.NOTIFICATION_ID, notification)
    }

    private fun cancelNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(AutoClickerApp.NOTIFICATION_ID)
    }

    // ── Drag Listener ──────────────────────────────────────────────────────

    private inner class DragTouchListener(
        private val params: WindowManager.LayoutParams,
        private val wm: WindowManager,
        private val target: View,
        private val onDragEnd: () -> Unit,
        private val onTap: () -> Unit = {}
    ) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var hasMoved = false
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    hasMoved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - touchX
                    val deltaY = event.rawY - touchY
                    val touchSlop = ViewConfiguration.get(target.context).scaledTouchSlop
                    hasMoved = hasMoved || kotlin.math.abs(deltaX) > touchSlop || kotlin.math.abs(deltaY) > touchSlop
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    wm.updateViewLayout(target, params)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (hasMoved) {
                        onDragEnd()
                    } else {
                        onTap()
                    }
                    return true
                }
            }
            return false
        }
    }
}
