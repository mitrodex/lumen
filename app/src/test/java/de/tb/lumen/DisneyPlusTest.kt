package de.tb.lumen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisneyPlusTest {
    private fun node(type: String, title: String, url: String?, pkg: String = "disneyplus") = """
        {"node":{"objectType":"$type","content":{"title":"$title","originalTitle":"$title"},
         "offers":[${if (url == null) "" else """{"standardWebURL":"$url","package":{"technicalName":"$pkg"}}"""}]}}
    """

    private fun response(vararg nodes: String) = """{"data":{"popularTitles":{"edges":[${nodes.joinToString(",")}]}}}"""

    private val sternenhafen = "https://www.disneyplus.com/browse/entity-aaaaaaaa-0000-4000-8000-000000000001"

    @Test
    fun exactNameWithDisneyOffer() {
        val json = response(
            node("MOVIE", "Sternenhafen: Der Film", "https://www.disneyplus.com/browse/entity-aaaaaaaa-0000-4000-8000-000000000002"),
            node("SHOW", "Sternenhafen", sternenhafen),
        )
        assertEquals("aaaaaaaa-0000-4000-8000-000000000001", DisneyPlus.pickEntity(json, "Sternenhafen", isSeries = true))
    }

    @Test
    fun similarNamesAreNotGuessed() {
        val json = response(node("SHOW", "Sternenhafen Kids", "https://www.disneyplus.com/browse/entity-aaaaaaaa-0000-4000-8000-000000000003"))
        assertNull(DisneyPlus.pickEntity(json, "Sternenhafen", isSeries = true))
    }

    @Test
    fun otherProvidersAreIgnored() {
        val json = response(node("SHOW", "Sternenhafen", "https://www.netflix.com/title/123", pkg = "netflix"))
        assertNull(DisneyPlus.pickEntity(json, "Sternenhafen", isSeries = true))
    }

    @Test
    fun prefersRequestedType() {
        val movie = "https://www.disneyplus.com/browse/entity-11111111-1111-1111-1111-111111111111"
        val show = "https://www.disneyplus.com/browse/entity-22222222-2222-2222-2222-222222222222"
        val json = response(node("MOVIE", "Wolkenreiter", movie), node("SHOW", "Wolkenreiter", show))
        assertEquals("22222222-2222-2222-2222-222222222222", DisneyPlus.pickEntity(json, "Wolkenreiter", isSeries = true))
        assertEquals("11111111-1111-1111-1111-111111111111", DisneyPlus.pickEntity(json, "Wolkenreiter", isSeries = false))
    }

    @Test
    fun brokenResponse() {
        assertNull(DisneyPlus.pickEntity("not json", "Sternenhafen", isSeries = true))
    }
}
