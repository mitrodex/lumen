package de.tb.lumen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleParsingTest {

    @Test
    fun normTitleIgnoresCaseAccentsAndPunctuation() {
        assertEquals("der feuer drache", normTitle("Der Feuer-Drache!"))
        assertEquals(normTitle("Pokémon"), normTitle("pokemon"))
    }

    @Test
    fun numberedEpisodeFormats() {
        assertEquals(NumberedEpisode(8, 9, "Ein Titel"), parseNumberedEpisode("S8:F9 Ein Titel"))
        assertEquals(NumberedEpisode(2, 13, "Pilot"), parseNumberedEpisode("S2 E13 – Pilot"))
        assertEquals(NumberedEpisode(1, 4, "Der Anfang"), parseNumberedEpisode("Staffel 1, Folge 4: Der Anfang"))
        assertEquals(NumberedEpisode(3, 1, "Folge 1"), parseNumberedEpisode("Season 3 Episode 1"))
    }

    @Test
    fun plainSubtitleIsNotAnEpisode() {
        assertNull(parseNumberedEpisode("Nachtzug"))
        assertNull(parseNumberedEpisode("Staffelfinale"))
    }

    @Test
    fun primeTitleWithSeasonAndLanguageTag() {
        assertEquals(PrimeTitle("Die Küstenwache", 3), parsePrimeTitle("Prime Video: Die Küstenwache - Staffel 3 [OV]"))
    }

    @Test
    fun primeTitleMovie() {
        assertEquals(PrimeTitle("Sommerregen", null), parsePrimeTitle("Prime Video: Sommerregen"))
        assertNull(parsePrimeTitle("Prime Video: "))
    }

    @Test
    fun netflixRecommendationIsSplitIntoSeriesAndEpisode() {
        assertEquals(
            "Nachtzug" to "Abfahrt (2)",
            splitRecommendation("Mit dem Profil von Alex weiterschauen", "Nachtzug, Abfahrt (2)"),
        )
        assertEquals("Film" to null, splitRecommendation("Continue watching", "Film"))
    }

    @Test
    fun otherRecommendationKeepsTitle() {
        assertEquals("Serie" to "Neue Folge", splitRecommendation("Serie", "Neue Folge"))
        // Lange Texte (Beschreibungen) fallen weg.
        assertEquals("Serie" to null, splitRecommendation("Serie", "x".repeat(80)))
    }

    @Test
    fun calendarUrlList() {
        assertEquals(listOf("a", "b", "c"), Config.parseUrls(" a, b,,c ,"))
        assertEquals(emptyList<String>(), Config.parseUrls(""))
    }
}
