package com.marcogn.thepatientgamerhelper.data.repository

import com.marcogn.thepatientgamerhelper.data.local.dao.GenreDao
import com.marcogn.thepatientgamerhelper.data.local.dao.PlatformDao
import com.marcogn.thepatientgamerhelper.data.local.dao.TagDao
import com.marcogn.thepatientgamerhelper.domain.model.Genre
import com.marcogn.thepatientgamerhelper.domain.model.Platform
import com.marcogn.thepatientgamerhelper.domain.model.Tag
import com.marcogn.thepatientgamerhelper.domain.repository.LookupRepository
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
