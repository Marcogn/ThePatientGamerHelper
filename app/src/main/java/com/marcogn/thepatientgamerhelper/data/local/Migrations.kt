package com.marcogn.thepatientgamerhelper.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the backlog tables (Tappa 1) without touching existing review data — the app is already
 * in real personal use (see CLAUDE.md), so a destructive migration isn't acceptable here.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `backlog_lists` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `backlog_items` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `listId` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `coverImagePath` TEXT,
                `status` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `addedAt` INTEGER NOT NULL,
                `startDate` INTEGER,
                `completedDate` INTEGER,
                `reviewId` TEXT,
                `abandonNote` TEXT,
                `releaseYear` INTEGER,
                `developer` TEXT,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`listId`) REFERENCES `backlog_lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`reviewId`) REFERENCES `reviews`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_backlog_items_listId` ON `backlog_items` (`listId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_backlog_items_reviewId` ON `backlog_items` (`reviewId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `backlog_comments` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `itemId` TEXT NOT NULL,
                `text` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                FOREIGN KEY(`itemId`) REFERENCES `backlog_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_backlog_comments_itemId` ON `backlog_comments` (`itemId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `backlog_history_entries` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `itemId` TEXT NOT NULL,
                `eventType` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `detail` TEXT,
                FOREIGN KEY(`itemId`) REFERENCES `backlog_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_backlog_history_entries_itemId` ON `backlog_history_entries` (`itemId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `backlog_item_platform_cross_ref` (
                `itemId` TEXT NOT NULL,
                `platformId` INTEGER NOT NULL,
                PRIMARY KEY(`itemId`, `platformId`),
                FOREIGN KEY(`itemId`) REFERENCES `backlog_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`platformId`) REFERENCES `platforms`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_backlog_item_platform_cross_ref_platformId` " +
                "ON `backlog_item_platform_cross_ref` (`platformId`)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `backlog_item_genre_cross_ref` (
                `itemId` TEXT NOT NULL,
                `genreId` INTEGER NOT NULL,
                PRIMARY KEY(`itemId`, `genreId`),
                FOREIGN KEY(`itemId`) REFERENCES `backlog_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`genreId`) REFERENCES `genres`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_backlog_item_genre_cross_ref_genreId` " +
                "ON `backlog_item_genre_cross_ref` (`genreId`)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `backlog_item_tag_cross_ref` (
                `itemId` TEXT NOT NULL,
                `tagId` INTEGER NOT NULL,
                PRIMARY KEY(`itemId`, `tagId`),
                FOREIGN KEY(`itemId`) REFERENCES `backlog_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_backlog_item_tag_cross_ref_tagId` " +
                "ON `backlog_item_tag_cross_ref` (`tagId`)",
        )
    }
}

/**
 * Adds the three HowLongToBeat estimate columns to `backlog_items` (Fase 8) — additive, same
 * non-destructive rationale as MIGRATION_1_2. All three are nullable REAL with no default, so
 * every row that predates this migration simply reads back as "no estimate", exactly like a row
 * created after this migration for a game HowLongToBeat has no data for.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `backlog_items` ADD COLUMN `hltbMainStoryHours` REAL")
        db.execSQL("ALTER TABLE `backlog_items` ADD COLUMN `hltbMainExtraHours` REAL")
        db.execSQL("ALTER TABLE `backlog_items` ADD COLUMN `hltbCompletionistHours` REAL")
    }
}

/**
 * Adds `backlog_lists.systemKind` (nullable, additive) — identifies the two lists the app itself
 * manages ("Completed with review"/"Completed awaiting review") independently of their
 * display name, so a later app-language switch can't fork them into duplicate lists. Every row that
 * predates this migration reads back as `NULL` (a regular, user-created list), same as any list
 * created after this migration that isn't one of the two system ones.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `backlog_lists` ADD COLUMN `systemKind` TEXT")
    }
}

/**
 * Adds six nullable columns to `reviews` (Tappa 1 of the reviews/backlog import-export spec v2):
 * `developer`/`publisher`/`releaseYear`/`metadataSource`/`externalId`/`linkedBacklogItemId`. None
 * of these are ever set by the create/edit form — they only round-trip through the new front-matter
 * Markdown export/import format (see `domain/export/ReviewBackupMarkdown.kt`) and the Drive backup
 * DTO, so every row that predates this migration (and every row created afterward outside of an
 * import) simply reads back as `NULL` in all six columns, additive like every migration before it.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `reviews` ADD COLUMN `developer` TEXT")
        db.execSQL("ALTER TABLE `reviews` ADD COLUMN `publisher` TEXT")
        db.execSQL("ALTER TABLE `reviews` ADD COLUMN `releaseYear` INTEGER")
        db.execSQL("ALTER TABLE `reviews` ADD COLUMN `metadataSource` TEXT")
        db.execSQL("ALTER TABLE `reviews` ADD COLUMN `externalId` TEXT")
        db.execSQL("ALTER TABLE `reviews` ADD COLUMN `linkedBacklogItemId` TEXT")
    }
}
