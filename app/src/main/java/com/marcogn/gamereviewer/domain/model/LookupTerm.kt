package com.marcogn.gamereviewer.domain.model

/** A named lookup value (Platform, Genre or Tag) with autocomplete-friendly identity. */
data class Platform(val id: Long, val name: String)

data class Genre(val id: Long, val name: String)

data class Tag(val id: Long, val name: String)
