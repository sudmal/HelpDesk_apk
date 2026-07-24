package com.sputnik.helpdesk.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TicketEntity::class, MaterialEntity::class, PendingCommentEntity::class, ConnectionRequestEntity::class],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ticketDao(): TicketDao
    abstract fun materialDao(): MaterialDao
    abstract fun pendingCommentDao(): PendingCommentDao
    abstract fun connectionRequestDao(): ConnectionRequestDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `pending_comments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `ticketId` INTEGER NOT NULL,
                        `body` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )"""
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tickets` ADD COLUMN `createdAt` TEXT")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tickets` ADD COLUMN `closedBy` TEXT")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `connection_requests` (
                        `id` INTEGER PRIMARY KEY NOT NULL,
                        `name` TEXT NOT NULL,
                        `phone` TEXT,
                        `addressString` TEXT,
                        `description` TEXT,
                        `status` TEXT NOT NULL,
                        `scheduledAt` TEXT,
                        `notes` TEXT,
                        `actNumber` TEXT,
                        `territoryId` INTEGER,
                        `territoryName` TEXT,
                        `creator` TEXT,
                        `createdAt` TEXT,
                        `isNew` INTEGER NOT NULL DEFAULT 0,
                        `cachedAt` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `connection_requests` ADD COLUMN `updatedAt` TEXT")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tickets` ADD COLUMN `actId` INTEGER")
                db.execSQL("ALTER TABLE `tickets` ADD COLUMN `actStatus` TEXT")
                db.execSQL("ALTER TABLE `tickets` ADD COLUMN `actMaterialsChangedAt` TEXT")
                db.execSQL("ALTER TABLE `connection_requests` ADD COLUMN `actId` INTEGER")
                db.execSQL("ALTER TABLE `connection_requests` ADD COLUMN `actStatus` TEXT")
                db.execSQL("ALTER TABLE `connection_requests` ADD COLUMN `actMaterialsChangedAt` TEXT")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `connection_requests` ADD COLUMN `feasibility` TEXT")
                db.execSQL("ALTER TABLE `connection_requests` ADD COLUMN `feasibilityComment` TEXT")
                db.execSQL("ALTER TABLE `connection_requests` ADD COLUMN `feasibilityAt` TEXT")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "helpdesk.db"
            ).addMigrations(
                MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11
            ).build().also { instance = it }
        }
    }
}
