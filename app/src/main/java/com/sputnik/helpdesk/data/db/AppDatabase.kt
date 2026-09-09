package com.sputnik.helpdesk.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TicketEntity::class, MaterialEntity::class, PendingCommentEntity::class, ConnectionRequestEntity::class, ActEntity::class],
    version = 17,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ticketDao(): TicketDao
    abstract fun materialDao(): MaterialDao
    abstract fun pendingCommentDao(): PendingCommentDao
    abstract fun connectionRequestDao(): ConnectionRequestDao
    abstract fun actDao(): ActDao

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

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `acts` (
                        `id` INTEGER PRIMARY KEY NOT NULL,
                        `number` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `ticketId` INTEGER,
                        `ticketNumber` TEXT,
                        `connectionRequestId` INTEGER,
                        `connectionRequestName` TEXT,
                        `address` TEXT,
                        `creator` TEXT,
                        `createdAt` TEXT NOT NULL,
                        `foremanReviewedAt` TEXT,
                        `materialsChangedAt` TEXT,
                        `canForemanReview` INTEGER NOT NULL,
                        `canEditMaterials` INTEGER NOT NULL,
                        `canAcknowledge` INTEGER NOT NULL,
                        `cachedAt` INTEGER NOT NULL
                    )"""
                )
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `acts` ADD COLUMN `materialsCorrectedAt` TEXT")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tickets` ADD COLUMN `territoryId` INTEGER")
                db.execSQL("ALTER TABLE `tickets` ADD COLUMN `territoryName` TEXT")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tickets` ADD COLUMN `statusSortOrder` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `connection_requests` ADD COLUMN `serviceTypeName` TEXT")
                db.execSQL("ALTER TABLE `connection_requests` ADD COLUMN `serviceTypeColor` TEXT")
            }
        }

        // No schema change: only realigns the stored identity hash with `tickets.statusSortOrder`
        // now declaring @ColumnInfo(defaultValue = "0"), so upgrades don't hit the integrity check.
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {}
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "helpdesk.db"
            ).addMigrations(
                MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17
            )
                // The DB is only a cache of server data — if a migration is missing or a restored
                // file fails validation, rebuild it instead of crash-looping on every launch.
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
