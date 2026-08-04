package com.marcogn.thepatientgamerhelper.domain.backup

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

/** Timestamped so multiple backups (manual + periodic) never collide in the appDataFolder. */
fun suggestedBackupFileName(now: Instant = Instant.now()): String =
    "the-patient-gamer-helper-backup-${fileNameFormatter.format(now)}.zip"
