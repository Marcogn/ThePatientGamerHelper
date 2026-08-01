package com.marcogn.gamereviewer.domain.repository

import com.marcogn.gamereviewer.domain.model.Genre
import com.marcogn.gamereviewer.domain.model.Platform
import com.marcogn.gamereviewer.domain.model.Tag
import kotlinx.coroutines.flow.Flow

/** Read access to lookup values, used to power autocomplete in the review form/filters. */
interface LookupRepository {
    fun observePlatforms(): Flow<List<Platform>>
    fun observeGenres(): Flow<List<Genre>>
    fun observeTags(): Flow<List<Tag>>
}
