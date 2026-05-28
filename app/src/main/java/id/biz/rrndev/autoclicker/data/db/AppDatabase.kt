package id.biz.rrndev.autoclicker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import id.biz.rrndev.autoclicker.data.db.dao.ClickFlowDao
import id.biz.rrndev.autoclicker.data.db.dao.ClickPointDao
import id.biz.rrndev.autoclicker.data.db.dao.HistoryFolderDao
import id.biz.rrndev.autoclicker.data.entity.ClickFlowEntity
import id.biz.rrndev.autoclicker.data.entity.ClickPointEntity
import id.biz.rrndev.autoclicker.data.entity.HistoryFolderEntity

@Database(
    entities = [ClickFlowEntity::class, ClickPointEntity::class, HistoryFolderEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun clickFlowDao(): ClickFlowDao
    abstract fun clickPointDao(): ClickPointDao
    abstract fun historyFolderDao(): HistoryFolderDao

    companion object {
        private const val DB_NAME = "auto_clicker.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS history_folders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        isPinned INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE click_flows ADD COLUMN folderId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE click_flows ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_click_flows_folderId ON click_flows(folderId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_click_flows_isPinned ON click_flows(isPinned)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history_folders ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
    }
}
