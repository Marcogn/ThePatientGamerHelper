package com.marcogn.gamereviewer.domain.backup

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

/** Timestamped so multiple backups (manual + periodic) never collide in the appDataFolder. */
fun suggestedBackupFileName(now: Instant = Instant.now()): String =
    "gamereviewer-backup-${fileNameFormatter.format(now)}.zip"
