package com.marcogn.gamereviewer.domain.model

/**
 * One candidate returned by a TheGamesDB search (Tappa 2). Multiple results are expected for
 * remasters/regional releases/ambiguous titles — the user picks, nothing is auto-selected.
 */
data class GameMetadataSearchResult(
    val externalId: Long,
    val title: String,
    val platformName: String?,
    val releaseYear: Int?,
    val genreNames: List<String>,
    val developerName: String?,
    val coverImageUrl: String?,
)
