package com.marcogn.thepatientgamerhelper.domain.howlongtobeat

private val NON_ALPHANUMERIC_REGEX = Regex("[^\\p{L}\\p{N}]")
private val WHITESPACE_REGEX = Regex("\\s+")

/**
 * Pure title-matching logic for HowLongToBeat search results, ported from GameNative's
 * `HltbService` (https://github.com/utkarshdalal/GameNative/blob/master/app/src/main/java/app/gamenative/utils/HltbService.kt),
 * which the user pointed at as a known-working reference after our own bundle-scraping approach
 * kept breaking. See `data/howlongtobeat/HowLongToBeatApiClient.kt` for the HTTP side of the port.
 * No Android/network dependency, unit-tested in plain JVM — same pattern as `domain/filter`/`domain/stats`.
 */
object HltbMatcher {

    /** Lowercases, collapses every run of non letter/digit characters to a single space, trims. */
    fun normalize(input: String): String =
        input.lowercase().replace(NON_ALPHANUMERIC_REGEX, " ").replace(WHITESPACE_REGEX, " ").trim()

    fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        val dp = Array(left.length + 1) { IntArray(right.length + 1) }
        for (leftIndex in 0..left.length) dp[leftIndex][0] = leftIndex
        for (rightIndex in 0..right.length) dp[0][rightIndex] = rightIndex
        for (leftIndex in 1..left.length) {
            for (rightIndex in 1..right.length) {
                dp[leftIndex][rightIndex] = minOf(
                    dp[leftIndex - 1][rightIndex] + 1,
                    dp[leftIndex][rightIndex - 1] + 1,
                    dp[leftIndex - 1][rightIndex - 1] + (if (left[leftIndex - 1] == right[rightIndex - 1]) 0 else 1),
                )
            }
        }
        return dp[left.length][right.length]
    }

    /**
     * True when [candidate] (already normalized) is close enough to [normalizedQuery] to trust as
     * a real match: identical, a "query" + separator prefix/whole-word hit, or within a
     * length-scaled Levenshtein distance threshold. Guards against [findBestMatch] always
     * returning *some* result even when every candidate is unrelated to the query.
     */
    fun isAcceptableMatch(normalizedQuery: String, candidate: String, distance: Int): Boolean {
        if (normalizedQuery.isBlank() || candidate.isBlank()) return false
        if (normalizedQuery == candidate) return true
        if (candidate.startsWith("$normalizedQuery ") || candidate.startsWith("$normalizedQuery:")) return true
        if (candidate.contains(" $normalizedQuery ")) return true
        val distanceThreshold = maxOf(3, normalizedQuery.length / 2)
        return distance <= distanceThreshold
    }

    /**
     * Picks the candidate whose normalized title is closest to [query] by Levenshtein distance
     * (ties go to the earliest candidate), or null if even the closest one fails
     * [isAcceptableMatch]. Generalized over the candidate type so the HTTP layer supplies its own
     * DTO instead of this pure module knowing about HTTP/JSON at all.
     */
    fun <T> findBestMatch(query: String, candidates: List<T>, nameOf: (T) -> String): T? {
        if (candidates.isEmpty()) return null
        val normalizedQuery = normalize(query)
        var best = candidates.first()
        var bestNormalizedName = normalize(nameOf(best))
        var bestDistance = levenshtein(normalizedQuery, bestNormalizedName)
        for (candidate in candidates) {
            val normalizedName = normalize(nameOf(candidate))
            val distance = levenshtein(normalizedQuery, normalizedName)
            if (distance < bestDistance) {
                bestDistance = distance
                best = candidate
                bestNormalizedName = normalizedName
            }
            if (distance == 0) break
        }
        return best.takeIf { isAcceptableMatch(normalizedQuery, bestNormalizedName, bestDistance) }
    }
}
