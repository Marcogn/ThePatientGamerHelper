package com.marcogn.thepatientgamerhelper.domain.howlongtobeat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HltbMatcherTest {

    @Test
    fun `normalize lowercases and collapses punctuation to single spaces`() {
        assertEquals("the witcher 3 wild hunt", HltbMatcher.normalize("The Witcher 3: Wild Hunt!!"))
        assertEquals("hollow knight", HltbMatcher.normalize("  Hollow   Knight  "))
    }

    @Test
    fun `levenshtein is zero for identical strings and counts edits otherwise`() {
        assertEquals(0, HltbMatcher.levenshtein("celeste", "celeste"))
        assertEquals(1, HltbMatcher.levenshtein("celeste", "celestx"))
        assertEquals(3, HltbMatcher.levenshtein("kitten", "sitting"))
    }

    @Test
    fun `isAcceptableMatch accepts exact and prefix-separated matches regardless of distance`() {
        assertEquals(true, HltbMatcher.isAcceptableMatch("hades", "hades", 0))
        assertEquals(true, HltbMatcher.isAcceptableMatch("hades", "hades ii", 6))
        assertEquals(true, HltbMatcher.isAcceptableMatch("dark souls", "the dark souls trilogy", 12))
    }

    @Test
    fun `isAcceptableMatch rejects unrelated titles beyond the distance threshold`() {
        assertEquals(false, HltbMatcher.isAcceptableMatch("hades", "stardew valley", 12))
        assertEquals(false, HltbMatcher.isAcceptableMatch("", "hades", 5))
    }

    @Test
    fun `findBestMatch picks the closest normalized title`() {
        val candidates = listOf("Hades II", "Stardew Valley", "Hades")
        val best = HltbMatcher.findBestMatch("Hades", candidates) { it }
        assertEquals("Hades", best)
    }

    @Test
    fun `findBestMatch breaks ties in favor of the earliest candidate`() {
        val candidates = listOf("Celeste", "Celeste")
        val best = HltbMatcher.findBestMatch("Celeste", candidates) { it }
        assertEquals("Celeste", best)
    }

    @Test
    fun `findBestMatch returns null when no candidate is acceptably close`() {
        val candidates = listOf("Stardew Valley", "Terraria")
        assertNull(HltbMatcher.findBestMatch("Hades", candidates) { it })
    }

    @Test
    fun `findBestMatch returns null for an empty candidate list`() {
        assertNull(HltbMatcher.findBestMatch("Hades", emptyList<String>()) { it })
    }
}
