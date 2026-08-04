package com.marcogn.thepatientgamerhelper.domain.model

import java.time.Instant

/** A backup archive listed from the Drive appDataFolder, for the restore picker. */
data class BackupFile(
    val id: String,
    val name: String,
    val createdAt: Instant?,
    val sizeBytes: Long?,
)
