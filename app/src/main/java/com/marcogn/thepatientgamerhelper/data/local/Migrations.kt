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
