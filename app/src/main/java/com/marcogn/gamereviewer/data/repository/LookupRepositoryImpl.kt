package com.marcogn.gamereviewer.data.repository

import com.marcogn.gamereviewer.data.local.dao.GenreDao
import com.marcogn.gamereviewer.data.local.dao.PlatformDao
import com.marcogn.gamereviewer.data.local.dao.TagDao
import com.marcogn.gamereviewer.domain.model.Genre
import com.marcogn.gamereviewer.domain.model.Platform
import com.marcogn.gamereviewer.domain.model.Tag
import com.marcogn.gamereviewer.domain.repository.LookupRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LookupRepositoryImpl @Inject constructor(
    private val platformDao: PlatformDao,
    private val genreDao: GenreDao,
    private val tagDao: TagDao,
) : LookupRepository {

    override fun observePlatforms(): Flow<List<Platform>> =
        platformDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeGenres(): Flow<List<Genre>> =
        genreDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeTags(): Flow<List<Tag>> =
        tagDao.observeAll().map { list -> list.map { it.toDomain() } }
}
