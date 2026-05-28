package id.biz.rrndev.autoclicker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import id.biz.rrndev.autoclicker.billing.PremiumManager
import id.biz.rrndev.autoclicker.data.DatabaseSeeder
import id.biz.rrndev.autoclicker.data.db.AppDatabase
import id.biz.rrndev.autoclicker.data.repository.FlowRepository
import id.biz.rrndev.autoclicker.data.repository.FlowRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Application class – wires the database and repository singletons. */
class AutoClickerApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    /** Shared repository accessible from both Activities and the Accessibility Service. */
    val repository: FlowRepository by lazy {
        FlowRepositoryImpl(
            database.clickFlowDao(),
            database.clickPointDao(),
            database.historyFolderDao()
        )
    }

    val premiumManager: PremiumManager by lazy { PremiumManager(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "auto_clicker_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        premiumManager.start()
        // Pre-populate with an example flow on first install
        appScope.launch { DatabaseSeeder.seedIfEmpty(this@AutoClickerApp, repository) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
