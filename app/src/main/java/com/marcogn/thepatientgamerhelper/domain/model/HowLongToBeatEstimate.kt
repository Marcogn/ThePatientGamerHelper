package com.marcogn.thepatientgamerhelper.domain.model

/**
 * Best-effort HowLongToBeat estimate for a single game, in hours. Every field is nullable
 * independently: HowLongToBeat itself leaves a category at 0/unknown when too few submissions
 * exist for it. Only ever attached to [BacklogItem] — see CLAUDE.md, Fase 8, for why this stays
 * out of [Review] the same way `releaseYear`/`developer` do.
 */
data class HowLongToBeatEstimate(
    val mainStoryHours: Double?,
    val mainExtraHours: Double?,
    val completionistHours: Double?,
) {
    val isEmpty: Boolean
        get() = mainStoryHours == null && mainExtraHours == null && completionistHours == null
}
