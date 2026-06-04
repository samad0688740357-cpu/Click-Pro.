package id.biz.rrndev.autoclicker.ui.activity

import android.Manifest
import android.app.Activity
import android.content.res.ColorStateList
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import id.biz.rrndev.autoclicker.AutoClickerApp
import id.biz.rrndev.autoclicker.R
import id.biz.rrndev.autoclicker.billing.PremiumState
import id.biz.rrndev.autoclicker.data.db.relation.FlowWithPoints
import id.biz.rrndev.autoclicker.data.entity.ClickFlowEntity
import id.biz.rrndev.autoclicker.data.entity.HistoryFolderEntity
import id.biz.rrndev.autoclicker.databinding.ActivityMainBinding
import id.biz.rrndev.autoclicker.databinding.ViewHistoryBinding
import id.biz.rrndev.autoclicker.service.AutoClickAccessibilityService
import id.biz.rrndev.autoclicker.ui.adapter.HistoryAdapter
import id.biz.rrndev.autoclicker.ui.adapter.ManageFoldersAdapter
import id.biz.rrndev.autoclicker.ui.history.HistoryFilter
import id.biz.rrndev.autoclicker.ui.history.HistoryQuery
import id.biz.rrndev.autoclicker.ui.history.HistorySortMode
import id.biz.rrndev.autoclicker.util.JsonUtils
import id.biz.rrndev.autoclicker.util.PermissionUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        const val ACTION_QUICK_START = "id.biz.rrndev.autoclicker.action.QUICK_START"
        const val EXTRA_QUICK_START_MULTI = "extra_quick_start_multi"
        private const val SHORTCUT_SINGLE = "quick_start_single"
        private const val SHORTCUT_MULTI = "quick_start_multi"
        private const val EXPORT_OPTION_SAVE_LOCAL = 0
        private const val EXPORT_OPTION_SHARE = 1
        private const val EXPORT_OPTION_DRIVE = 2
        private const val IMPORT_OPTION_LOCAL = 0
        private const val IMPORT_OPTION_DRIVE = 1
        private const val GOOGLE_DRIVE_PACKAGE = "com.google.android.apps.docs"
        private const val ACCESSIBILITY_STATUS_REFRESH_DELAY_MS = 500L
    }

    private lateinit var binding: ActivityMainBinding
    private var historyBinding: ViewHistoryBinding? = null
    private lateinit var historyAdapter: HistoryAdapter
    private var pendingExportJson: String? = null
    private var pendingQuickStartMode: Boolean? = null
    private var accessibilityDialogShowing = false

    private var isPremium = false
    private var currentFlows: List<FlowWithPoints> = emptyList()
    private var currentFolders: List<HistoryFolderEntity> = emptyList()
    private var selectedFolderFilter: Long? = null
    private var historySearch = ""
    private var historySortMode = HistorySortMode.DATE
    private var historySortAscending = false

    private val repository get() = (application as AutoClickerApp).repository
    private val premiumManager get() = (application as AutoClickerApp).premiumManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled silently */ }

    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { importFromUri(it) }
    }

    private val driveImportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { importFromUri(it) }
        }
    }

    private val exportFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val json = pendingExportJson
        if (uri == null || json == null) {
            pendingExportJson = null
            return@registerForActivityResult
        }
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray())
            }
            Toast.makeText(this, R.string.file_saved_successfully, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.failed_to_save_file, Toast.LENGTH_SHORT).show()
        } finally {
            pendingExportJson = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        findViewById<View>(R.id.layoutHistory)?.let { historyView ->
            historyBinding = ViewHistoryBinding.bind(historyView)
            setupHistory()
        }

        setupButtons()
        setupBottomNavigation()
        requestNotificationPermission()
        handleQuickStartIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleQuickStartIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        premiumManager.restorePurchases()
        updatePermissionBanner()
        binding.root.postDelayed(
            { updatePermissionBanner() },
            ACCESSIBILITY_STATUS_REFRESH_DELAY_MS
        )
        attemptPendingQuickStart()
    }

    private fun setupHistory() {
        historyAdapter = HistoryAdapter(
            onLoad = { loadFlowFromHistory(it) },
            onEdit = { flow ->
                startActivity(Intent(this, FlowEditorActivity::class.java).apply {
                    putExtra(FlowEditorActivity.EXTRA_FLOW_ID, flow.id)
                })
            },
            onDelete = { flow -> confirmDeleteFlow(flow) },
            onPin = { flow -> lifecycleScope.launch { repository.setFlowPinned(flow.id, !flow.isPinned) } },
            onClone = { flow -> lifecycleScope.launch { repository.cloneFlow(flow.id) } },
            onMoveFolder = { flow -> showMoveToFolderDialog(flow) }
        )
        historyBinding?.recyclerHistory?.adapter = historyAdapter

        historyBinding?.btnNewFlow?.setOnClickListener {
            startActivity(Intent(this, FlowEditorActivity::class.java))
        }
        historyBinding?.btnPremiumLifetime?.setOnClickListener {
            if (!premiumManager.state.value.isPremium) showPremiumDialog()
        }
        historyBinding?.btnHistoryMore?.setOnClickListener { anchor ->
            showHistoryActionsMenu(anchor)
        }
        historyBinding?.btnExport?.setOnClickListener { showExportOptionsDialog() }
        historyBinding?.btnImport?.setOnClickListener {
            handleImportClick()
        }
        historyBinding?.btnCreateFolder?.setOnClickListener { showManageFoldersDialog() }
        historyBinding?.btnSortHistory?.setOnClickListener { showSortDialog() }
        historyBinding?.etHistorySearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                historySearch = s?.toString().orEmpty()
                renderHistory()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        lifecycleScope.launch {
            repository.getAllFlowsWithPoints().collectLatest { flows ->
                currentFlows = flows
                renderHistory()
            }
        }
        lifecycleScope.launch {
            repository.getAllFolders().collectLatest { folders ->
                currentFolders = folders
                renderHistory()
            }
        }
        lifecycleScope.launch {
            premiumManager.state.collectLatest { state ->
                isPremium = state.isPremium
                updatePremiumUi(state)
                renderHistory()
                state.lastMessage?.let { message ->
                    if (message.isNotBlank()) Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadFlowFromHistory(flow: ClickFlowEntity) {
        if (!PermissionUtils.isAccessibilityServiceEnabled(this)) {
            showAccessibilityDialog()
            return
        }

        getSharedPreferences("autoclicker_prefs", MODE_PRIVATE).edit()
            .putInt("max_clicks", flow.loopCount)
            .apply()

        startService(Intent(this, AutoClickAccessibilityService::class.java).apply {
            action = "ACTION_LOAD_FLOW"
            putExtra("flow_id", flow.id)
        })

        binding.bottomNavigation.selectedItemId = R.id.nav_main
        Toast.makeText(this, getString(R.string.loaded_flow, flow.name), Toast.LENGTH_SHORT).show()
    }

    private fun renderHistory() {
        if (!::historyAdapter.isInitialized) return
        val folderNames = currentFolders.associate { it.id to it.name }
        historyAdapter.setPremiumUi(isPremium, folderNames)
        val visibleFlows = HistoryFilter.apply(
            flows = currentFlows,
            folders = currentFolders,
            query = HistoryQuery(
                isPremium = isPremium,
                search = historySearch,
                folderFilter = selectedFolderFilter,
                sortMode = historySortMode,
                ascending = historySortAscending
            )
        )
        historyAdapter.submitList(visibleFlows)
        historyBinding?.viewEmptyState?.visibility =
            if (visibleFlows.isEmpty()) View.VISIBLE else View.GONE
        renderFolderChips()
        updateSortButton()
    }

    private fun updatePremiumUi(state: PremiumState) {
        historyBinding?.layoutPremiumHistoryTools?.visibility =
            if (state.isPremium) View.VISIBLE else View.GONE
        historyBinding?.tilHistorySearch?.visibility =
            if (state.isPremium) View.VISIBLE else View.GONE
        historyBinding?.btnSortHistory?.visibility =
            if (state.isPremium) View.VISIBLE else View.GONE
        historyBinding?.tvSavedFlowLabel?.visibility =
            if (state.isPremium) View.GONE else View.VISIBLE
        historyBinding?.btnPremiumLifetime?.apply {
            text = getString(if (state.isPremium) R.string.active_short else R.string.premium_short)
            isClickable = !state.isPremium
            isFocusable = !state.isPremium
            backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (state.isPremium) R.color.color_accent else R.color.color_accent_container
                )
            )
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (state.isPremium) R.color.white else R.color.color_accent
                )
            )
            icon = if (state.isPremium) {
                ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_star)
            } else {
                null
            }
            iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (state.isPremium) R.color.white else R.color.color_accent
                )
            )
            iconPadding = (4 * resources.displayMetrics.density).toInt()
        }
        if (!state.isPremium) selectedFolderFilter = null
    }

    private fun renderFolderChips() {
        val container = historyBinding?.layoutFolderChips ?: return
        container.removeAllViews()
        if (!isPremium) return

        addFolderChip(container, getString(R.string.all_folders), null, null)
        addFolderChip(
            container,
            getString(R.string.uncategorized),
            HistoryFilter.UNCATEGORIZED_FOLDER_ID,
            null
        )
        currentFolders.forEach { folder ->
            val label = if (folder.isPinned) "Pinned: ${folder.name}" else folder.name
            addFolderChip(container, label, folder.id, folder)
        }
    }

    private fun addFolderChip(
        container: LinearLayout,
        label: String,
        filterId: Long?,
        folder: HistoryFolderEntity?
    ) {
        val chip = MaterialButton(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = label
            textSize = 12f
            minHeight = (36 * resources.displayMetrics.density).toInt()
            isCheckable = true
            isChecked = selectedFolderFilter == filterId
            setOnClickListener {
                selectedFolderFilter = filterId
                renderHistory()
            }
            folder?.let { currentFolder ->
                setOnLongClickListener {
                    showFolderMenu(currentFolder)
                    true
                }
            }
        }
        val marginEnd = (8 * resources.displayMetrics.density).toInt()
        container.addView(
            chip,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, marginEnd, 0) }
        )
    }

    private fun showManageFoldersDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manage_folders, null)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recyclerManageFolders)
        val emptyView = dialogView.findViewById<TextView>(R.id.tvManageFoldersEmpty)
        lateinit var touchHelper: ItemTouchHelper

        val adapter = ManageFoldersAdapter(
            onRename = { folder -> showFolderNameDialog(folder) },
            onDelete = { folder -> confirmDeleteFolder(folder) },
            onStartDrag = { viewHolder -> touchHelper.startDrag(viewHolder) },
            onOrderChanged = { folderIds ->
                lifecycleScope.launch { repository.updateFolderOrder(folderIds) }
            }
        )

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        touchHelper = ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                0
            ) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    return adapter.moveFolder(
                        viewHolder.bindingAdapterPosition,
                        target.bindingAdapterPosition
                    )
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

                override fun clearView(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ) {
                    super.clearView(recyclerView, viewHolder)
                    adapter.dispatchOrderChanged()
                }
            }
        )
        touchHelper.attachToRecyclerView(recycler)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()
        val dialogJob = lifecycleScope.launch {
            repository.getAllFolders().collectLatest { folders ->
                adapter.submitFolders(folders)
                emptyView.visibility = if (folders.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        dialogView.findViewById<MaterialButton>(R.id.btnAddManagedFolder).setOnClickListener {
            showFolderNameDialog()
        }
        dialogView.findViewById<MaterialButton>(R.id.btnDoneManageFolders).setOnClickListener {
            dialog.dismiss()
        }
        dialog.setOnDismissListener { dialogJob.cancel() }
        dialog.show()
    }

    private fun showFolderNameDialog(folder: HistoryFolderEntity? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_folder_name, null)
        val title = dialogView.findViewById<TextView>(R.id.tvFolderDialogTitle)
        val input = dialogView.findViewById<TextInputEditText>(R.id.etFolderName)
        title.setText(if (folder == null) R.string.create_folder else R.string.rename_folder)
        input.setText(folder?.name.orEmpty())
        input.setSelection(input.text?.length ?: 0)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<MaterialButton>(R.id.btnCancelFolder).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<MaterialButton>(R.id.btnSaveFolder).setOnClickListener {
            val name = input.text?.toString()?.trim().orEmpty()
            if (name.isNotEmpty()) {
                lifecycleScope.launch {
                    if (folder == null) repository.createFolder(name)
                    else repository.updateFolder(folder.copy(name = name))
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showFolderMenu(folder: HistoryFolderEntity) {
        val pinLabel = getString(if (folder.isPinned) R.string.unpin_folder else R.string.pin_folder)
        val options = arrayOf(pinLabel, getString(R.string.rename_folder), getString(R.string.delete_folder))
        MaterialAlertDialogBuilder(this)
            .setTitle(folder.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> lifecycleScope.launch { repository.setFolderPinned(folder.id, !folder.isPinned) }
                    1 -> showFolderNameDialog(folder)
                    2 -> confirmDeleteFolder(folder)
                }
            }
            .show()
    }

    private fun confirmDeleteFolder(folder: HistoryFolderEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_folder)
            .setMessage(getString(R.string.delete_folder_message, folder.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    if (selectedFolderFilter == folder.id) selectedFolderFilter = null
                    repository.deleteFolder(folder)
                }
            }
            .show()
    }

    private fun confirmDeleteFlow(flow: ClickFlowEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_config)
            .setMessage(getString(R.string.delete_config_message, flow.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch { repository.deleteFlow(flow) }
            }
            .show()
    }

    private fun showMoveToFolderDialog(flow: ClickFlowEntity) {
        val folderOptions = listOf<Pair<Long?, String>>(null to getString(R.string.uncategorized)) +
            currentFolders.map { it.id to it.name }
        val labels = folderOptions.map { it.second }.toTypedArray()
        val checkedIndex = folderOptions.indexOfFirst { it.first == flow.folderId }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.move_to_folder)
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                lifecycleScope.launch {
                    repository.moveFlowToFolder(flow.id, folderOptions[which].first)
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSortDialog() {
        val options = arrayOf(
            getString(R.string.sort_date_desc),
            getString(R.string.sort_date_asc),
            getString(R.string.sort_name_asc),
            getString(R.string.sort_name_desc)
        )
        val checked = when {
            historySortMode == HistorySortMode.DATE && !historySortAscending -> 0
            historySortMode == HistorySortMode.DATE && historySortAscending -> 1
            historySortMode == HistorySortMode.NAME && historySortAscending -> 2
            else -> 3
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sort_history)
            .setSingleChoiceItems(options, checked) { dialog, which ->
                when (which) {
                    0 -> {
                        historySortMode = HistorySortMode.DATE
                        historySortAscending = false
                    }
                    1 -> {
                        historySortMode = HistorySortMode.DATE
                        historySortAscending = true
                    }
                    2 -> {
                        historySortMode = HistorySortMode.NAME
                        historySortAscending = true
                    }
                    3 -> {
                        historySortMode = HistorySortMode.NAME
                        historySortAscending = false
                    }
                }
                renderHistory()
                dialog.dismiss()
            }
            .show()
    }

    private fun showHistoryActionsMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menuInflater.inflate(R.menu.menu_history_actions, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_import -> {
                        handleImportClick()
                        true
                    }
                    R.id.action_export -> {
                        showExportOptionsDialog()
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun handleImportClick() {
        if (isPremium) showImportOptionsDialog()
        else openImportDocument()
    }

    private fun updateSortButton() {
        val textRes = when {
            historySortMode == HistorySortMode.DATE && !historySortAscending -> R.string.sort_date_desc
            historySortMode == HistorySortMode.DATE && historySortAscending -> R.string.sort_date_asc
            historySortMode == HistorySortMode.NAME && historySortAscending -> R.string.sort_name_asc
            else -> R.string.sort_name_desc
        }
        historyBinding?.btnSortHistory?.contentDescription = getString(textRes)
    }

    private fun showImportOptionsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_import_config, null)
        val cardImportLocal = dialogView.findViewById<MaterialCardView>(R.id.cardImportLocal)
        val cardImportDrive = dialogView.findViewById<MaterialCardView>(R.id.cardImportDrive)
        val ivImportLocalSelected = dialogView.findViewById<ImageView>(R.id.ivImportLocalSelected)
        val ivImportDriveSelected = dialogView.findViewById<ImageView>(R.id.ivImportDriveSelected)
        var selectedImportOption = IMPORT_OPTION_LOCAL

        fun refreshCard(card: MaterialCardView, selected: Boolean) {
            card.strokeColor = ContextCompat.getColor(
                this,
                if (selected) R.color.color_accent else R.color.color_outline
            )
            card.strokeWidth = (
                (if (selected) 2 else 1) * resources.displayMetrics.density
                ).toInt()
        }

        fun refreshSelection() {
            refreshCard(cardImportLocal, selectedImportOption == IMPORT_OPTION_LOCAL)
            refreshCard(cardImportDrive, selectedImportOption == IMPORT_OPTION_DRIVE)
            ivImportLocalSelected.visibility =
                if (selectedImportOption == IMPORT_OPTION_LOCAL) View.VISIBLE else View.GONE
            ivImportDriveSelected.visibility =
                if (selectedImportOption == IMPORT_OPTION_DRIVE) View.VISIBLE else View.GONE
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        cardImportLocal.setOnClickListener {
            selectedImportOption = IMPORT_OPTION_LOCAL
            refreshSelection()
        }
        cardImportDrive.setOnClickListener {
            selectedImportOption = IMPORT_OPTION_DRIVE
            refreshSelection()
        }
        dialogView.findViewById<MaterialButton>(R.id.btnCancelImport).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<MaterialButton>(R.id.btnImportConfiguration).setOnClickListener {
            dialog.dismiss()
            if (selectedImportOption == IMPORT_OPTION_DRIVE) openDriveImportDocument()
            else openImportDocument()
        }
        refreshSelection()
        dialog.show()
    }

    private fun importFromUri(uri: Uri) {
        val json = JsonUtils.readJsonFromUri(this, uri)
        if (json != null) {
            lifecycleScope.launch {
                try {
                    repository.importFromJson(json)
                    Toast.makeText(this@MainActivity, R.string.restore_successful, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, R.string.restore_failed_invalid_file, Toast.LENGTH_LONG).show()
                }
            }
        } else {
            Toast.makeText(this, R.string.restore_failed_cannot_read_file, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openImportDocument() {
        importFileLauncher.launch(arrayOf("application/json", "*/*"))
    }

    private fun openDriveImportDocument() {
        val driveIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            setPackage(GOOGLE_DRIVE_PACKAGE)
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("application/json", "text/json", "text/plain", "*/*")
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (driveIntent.resolveActivity(packageManager) != null) {
            driveImportLauncher.launch(driveIntent)
        } else {
            Toast.makeText(this, R.string.google_drive_unavailable, Toast.LENGTH_SHORT).show()
            openImportDocument()
        }
    }

    private fun showExportOptionsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_export_config, null)
        val cardSaveLocal = dialogView.findViewById<MaterialCardView>(R.id.cardSaveLocal)
        val cardShareBackup = dialogView.findViewById<MaterialCardView>(R.id.cardShareBackup)
        val cardDriveBackup = dialogView.findViewById<MaterialCardView>(R.id.cardDriveBackup)
        val ivSaveLocalSelected = dialogView.findViewById<ImageView>(R.id.ivSaveLocalSelected)
        val ivShareBackupSelected = dialogView.findViewById<ImageView>(R.id.ivShareBackupSelected)
        val ivDriveBackupSelected = dialogView.findViewById<ImageView>(R.id.ivDriveBackupSelected)
        var selectedExportOption = EXPORT_OPTION_SAVE_LOCAL

        cardDriveBackup.visibility = if (isPremium) View.VISIBLE else View.GONE

        fun refreshCard(card: MaterialCardView, selected: Boolean) {
            card.strokeColor = ContextCompat.getColor(
                this,
                if (selected) R.color.color_accent else R.color.color_outline
            )
            card.strokeWidth = (
                (if (selected) 2 else 1) * resources.displayMetrics.density
                ).toInt()
        }

        fun refreshSelection() {
            refreshCard(cardSaveLocal, selectedExportOption == EXPORT_OPTION_SAVE_LOCAL)
            refreshCard(cardShareBackup, selectedExportOption == EXPORT_OPTION_SHARE)
            refreshCard(cardDriveBackup, selectedExportOption == EXPORT_OPTION_DRIVE)
            ivSaveLocalSelected.visibility =
                if (selectedExportOption == EXPORT_OPTION_SAVE_LOCAL) View.VISIBLE else View.GONE
            ivShareBackupSelected.visibility =
                if (selectedExportOption == EXPORT_OPTION_SHARE) View.VISIBLE else View.GONE
            ivDriveBackupSelected.visibility =
                if (selectedExportOption == EXPORT_OPTION_DRIVE) View.VISIBLE else View.GONE
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        cardSaveLocal.setOnClickListener {
            selectedExportOption = EXPORT_OPTION_SAVE_LOCAL
            refreshSelection()
        }
        cardShareBackup.setOnClickListener {
            selectedExportOption = EXPORT_OPTION_SHARE
            refreshSelection()
        }
        cardDriveBackup.setOnClickListener {
            selectedExportOption = EXPORT_OPTION_DRIVE
            refreshSelection()
        }
        dialogView.findViewById<MaterialButton>(R.id.btnCancelExport).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<MaterialButton>(R.id.btnExportConfiguration).setOnClickListener {
            dialog.dismiss()
            when (selectedExportOption) {
                EXPORT_OPTION_SHARE -> shareExport()
                EXPORT_OPTION_DRIVE -> shareExportToDrive()
                else -> exportToDocument("autoclicker_backup.json")
            }
        }
        refreshSelection()
        dialog.show()
    }

    private fun exportToDocument(fileName: String) {
        lifecycleScope.launch {
            try {
                pendingExportJson = repository.exportToJson()
                exportFileLauncher.launch(fileName)
            } catch (e: Exception) {
                pendingExportJson = null
                Toast.makeText(this@MainActivity, R.string.export_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareExport() {
        lifecycleScope.launch {
            try {
                val json = repository.exportToJson()
                val shareIntent = JsonUtils.shareJson(this@MainActivity, json)
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_backup)))
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, R.string.export_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareExportToDrive() {
        lifecycleScope.launch {
            try {
                val json = repository.exportToJson()
                val driveIntent = JsonUtils.shareJson(
                    this@MainActivity,
                    json,
                    "autoclicker_drive_backup.json"
                ).apply {
                    setPackage(GOOGLE_DRIVE_PACKAGE)
                }
                if (driveIntent.resolveActivity(packageManager) != null) {
                    startActivity(driveIntent)
                } else {
                    Toast.makeText(this@MainActivity, R.string.google_drive_unavailable, Toast.LENGTH_SHORT).show()
                    exportToDocument("autoclicker_drive_backup.json")
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, R.string.export_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPremiumDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_premium_upgrade, null)
        val state = premiumManager.state.value
        dialogView.findViewById<TextView>(R.id.tvPremiumPrice).text =
            if (state.isPremium) getString(R.string.premium_active)
            else state.priceText ?: getString(R.string.premium_price_loading)
        dialogView.findViewById<TextView>(R.id.tvPremiumDiscount).text =
            state.discountText ?: getString(R.string.premium_discount_default)
        setupCompareRow(dialogView, R.id.rowFolders, R.string.premium_compare_folders, false, true)
        setupCompareRow(dialogView, R.id.rowPinClone, R.string.premium_compare_pin_clone, false, true)
        setupCompareRow(dialogView, R.id.rowSearchSort, R.string.premium_compare_search_sort, false, true)
        setupCompareRow(dialogView, R.id.rowUnlimited, R.string.premium_compare_unlimited, true, true)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<MaterialButton>(R.id.btnCancelPremium).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<MaterialButton>(R.id.btnBuyPremium).apply {
            text = state.priceText?.let { "${getString(R.string.upgrade)} $it" } ?: getString(R.string.upgrade)
            isEnabled = !state.isPremium
            setOnClickListener {
                premiumManager.launchPurchase(this@MainActivity)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun setupCompareRow(
        root: View,
        rowId: Int,
        featureText: Int,
        freeIncluded: Boolean,
        premiumIncluded: Boolean
    ) {
        val row = root.findViewById<View>(rowId)
        row.findViewById<TextView>(R.id.tvCompareFeature).setText(featureText)
        setCompareIcon(row.findViewById(R.id.ivFreeState), freeIncluded)
        setCompareIcon(row.findViewById(R.id.ivPremiumState), premiumIncluded)
    }

    private fun setCompareIcon(icon: ImageView, included: Boolean) {
        icon.setImageResource(if (included) R.drawable.ic_check_circle else R.drawable.ic_close)
        icon.setColorFilter(
            ContextCompat.getColor(
                this,
                if (included) R.color.color_success else R.color.color_error
            )
        )
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_main -> {
                    binding.viewMain.visibility = View.VISIBLE
                    findViewById<View>(R.id.layoutHistory)?.visibility = View.GONE
                    true
                }
                R.id.nav_history -> {
                    binding.viewMain.visibility = View.GONE
                    findViewById<View>(R.id.layoutHistory)?.visibility = View.VISIBLE
                    true
                }
                else -> false
            }
        }
    }

    private fun setupButtons() {
        binding.btnEnableSingle.setOnClickListener { enableMode(isMultiTarget = false) }
        binding.btnEnableMulti.setOnClickListener { enableMode(isMultiTarget = true) }
        binding.btnSettingsSingle.setOnClickListener { showSettingsDialog() }
        binding.btnSettingsMulti.setOnClickListener { showSettingsDialog() }
        binding.btnShortcutSingle.setOnClickListener { addQuickStartShortcut(isMultiTarget = false) }
        binding.btnShortcutMulti.setOnClickListener { addQuickStartShortcut(isMultiTarget = true) }
    }

    private fun addQuickStartShortcut(isMultiTarget: Boolean) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            Toast.makeText(this, R.string.shortcut_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        val label = getString(
            if (isMultiTarget) R.string.multi_quick_start_shortcut else R.string.single_quick_start_shortcut
        )
        val shortcutIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_QUICK_START
            putExtra(EXTRA_QUICK_START_MULTI, isMultiTarget)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val shortcut = ShortcutInfoCompat.Builder(
            this,
            if (isMultiTarget) SHORTCUT_MULTI else SHORTCUT_SINGLE
        )
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(IconCompat.createWithResource(this, if (isMultiTarget) R.drawable.ic_add else R.drawable.ic_touch))
            .setIntent(shortcutIntent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
        Toast.makeText(this, R.string.shortcut_added, Toast.LENGTH_SHORT).show()
    }

    private fun handleQuickStartIntent(intent: Intent?) {
        if (intent?.action != ACTION_QUICK_START) return
        pendingQuickStartMode = intent.getBooleanExtra(EXTRA_QUICK_START_MULTI, false)
        intent.action = null
        attemptPendingQuickStart()
    }

    private fun attemptPendingQuickStart() {
        val isMultiTarget = pendingQuickStartMode ?: return
        if (!PermissionUtils.isAccessibilityServiceEnabled(this)) {
            showAccessibilityDialog(clearPendingQuickStartOnCancel = true)
            return
        }
        pendingQuickStartMode = null
        enableMode(isMultiTarget)
    }

    private fun enableMode(isMultiTarget: Boolean) {
        if (!PermissionUtils.isAccessibilityServiceEnabled(this)) {
            showAccessibilityDialog()
            return
        }

        val prefs = getSharedPreferences("autoclicker_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("is_multi_target_mode", isMultiTarget).apply()

        startService(Intent(this, AutoClickAccessibilityService::class.java).apply {
            action = "ACTION_SHOW_OVERLAY"
        })

        Toast.makeText(
            this,
            if (isMultiTarget) R.string.multi_target_mode else R.string.single_target_mode,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences("autoclicker_prefs", MODE_PRIVATE)
        val currentMaxClicks = prefs.getInt("max_clicks", 0)
        val currentInterval = prefs.getLong("click_interval", 1000L)

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
        val etMaxClicks = dialogView.findViewById<TextInputEditText>(R.id.etMaxClicks)
        val etClickInterval = dialogView.findViewById<TextInputEditText>(R.id.etClickInterval)

        etMaxClicks.setText(currentMaxClicks.toString())
        etClickInterval.setText(currentInterval.toString())

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<MaterialButton>(R.id.btnCancelSettings).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<MaterialButton>(R.id.btnSaveSettings).setOnClickListener {
            val maxClicks = etMaxClicks.text.toString().toIntOrNull() ?: 0
            val interval = etClickInterval.text.toString().toLongOrNull() ?: 1000L

            if (interval < 100) {
                Toast.makeText(this, R.string.interval_too_low, Toast.LENGTH_SHORT).show()
            } else {
                prefs.edit().apply {
                    putInt("max_clicks", maxClicks)
                    putLong("click_interval", interval)
                    apply()
                }
                if (AutoClickAccessibilityService.isServiceEnabled()) {
                    startService(Intent(this, AutoClickAccessibilityService::class.java).apply {
                        action = "ACTION_UPDATE_SETTINGS"
                    })
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun updatePermissionBanner() {
        val hasAccess = PermissionUtils.isAccessibilityServiceEnabled(this)
        binding.cardPermission.visibility = if (hasAccess) View.GONE else View.VISIBLE
        binding.cardAccessibilityEnabled.visibility = if (hasAccess) View.VISIBLE else View.GONE
        binding.btnEnableAccessibility.setOnClickListener {
            openAccessibilitySettingsOrShowHelp()
        }
        binding.btnDisableAccessibility.setOnClickListener {
            openAccessibilitySettingsOrShowHelp()
        }
    }

    private fun openAccessibilitySettingsOrShowHelp(): Boolean {
        val opened = PermissionUtils.openAccessibilitySettings(this)
        if (!opened) showAccessibilityManualHelpDialog()
        return opened
    }

    private fun showAccessibilityDialog(clearPendingQuickStartOnCancel: Boolean = false) {
        if (accessibilityDialogShowing) return
        accessibilityDialogShowing = true
        var openedSettings = false
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.accessibility_required_title)
            .setMessage(R.string.accessibility_required_msg)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                openedSettings = openAccessibilitySettingsOrShowHelp()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                if (clearPendingQuickStartOnCancel) pendingQuickStartMode = null
            }
            .setOnDismissListener {
                accessibilityDialogShowing = false
                if (clearPendingQuickStartOnCancel && !openedSettings) {
                    pendingQuickStartMode = null
                }
            }
            .show()
    }

    private fun showAccessibilityManualHelpDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.accessibility_manual_title)
            .setMessage(R.string.accessibility_manual_msg)
            .setPositiveButton(R.string.done, null)
            .show()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionUtils.hasNotificationPermission(this)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
