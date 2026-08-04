package com.marcogn.gamereviewer.data.drive

import com.marcogn.gamereviewer.domain.model.BackupFile
import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class DriveFileMetadataDto(val name: String, val parents: List<String>)

@Serializable
data class DriveFileDto(
    val id: String,
    val name: String,
    val createdTime: String? = null,
    val size: String? = null,
)

@Serializable
data class DriveFileListDto(val files: List<DriveFileDto> = emptyList())

fun DriveFileDto.toDomain(): BackupFile = BackupFile(
    id = id,
    name = name,
    createdAt = createdTime?.let { runCatching { Instant.parse(it) }.getOrNull() },
    sizeBytes = size?.toLongOrNull(),
)
