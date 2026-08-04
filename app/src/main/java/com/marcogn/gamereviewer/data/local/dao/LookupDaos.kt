package com.marcogn.gamereviewer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.marcogn.gamereviewer.data.local.entity.GenreEntity
import com.marcogn.gamereviewer.data.local.entity.PlatformEntity
import com.marcogn.gamereviewer.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlatformDao {
    @Query("SELECT * FROM platforms ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<PlatformEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoring(entity: PlatformEntity): Long

    @Query("SELECT * FROM platforms WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): PlatformEntity?

    @Query("DELETE FROM platforms")
    suspend fun deleteAll()

    @Transaction
    suspend fun getOrCreate(rawName: String): Long {
        val trimmed = rawName.trim()
        val normalized = trimmed.lowercase()
        findByNormalizedName(normalized)?.let { return it.id }
        insertIgnoring(PlatformEntity(name = trimmed, normalizedName = normalized))
        return findByNormalizedName(normalized)!!.id
    }
}

@Dao
interface GenreDao {
    @Query("SELECT * FROM genres ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<GenreEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoring(entity: GenreEntity): Long

    @Query("SELECT * FROM genres WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): GenreEntity?

    @Query("DELETE FROM genres")
    suspend fun deleteAll()

    @Transaction
    suspend fun getOrCreate(rawName: String): Long {
        val trimmed = rawName.trim()
        val normalized = trimmed.lowercase()
        findByNormalizedName(normalized)?.let { return it.id }
        insertIgnoring(GenreEntity(name = trimmed, normalizedName = normalized))
        return findByNormalizedName(normalized)!!.id
    }
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoring(entity: TagEntity): Long

    @Query("SELECT * FROM tags WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): TagEntity?

    @Query("DELETE FROM tags")
    suspend fun deleteAll()

    @Transaction
    suspend fun getOrCreate(rawName: String): Long {
        val trimmed = rawName.trim()
        val normalized = trimmed.lowercase()
        findByNormalizedName(normalized)?.let { return it.id }
        insertIgnoring(TagEntity(name = trimmed, normalizedName = normalized))
        return findByNormalizedName(normalized)!!.id
    }
}
