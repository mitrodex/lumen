package de.tb.lumen

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Disney+ meldet beim Abspielen keine Inhalts-ID, nur Serie/Film als Text. Die Disney+-Adresse
 * („…/browse/entity-<id>“) steht bei JustWatch – dort schlagen wir den Namen einmal nach.
 * Netzwerk: nur aus einem Hintergrund-Thread aufrufen.
 */
object DisneyPlus {
    const val PACKAGE = "com.disney.disneyplus"
    private const val API = "https://apis.justwatch.com/graphql"
    private const val QUERY = "query(\$q:String!){popularTitles(country:DE,first:8,filter:{searchQuery:\$q})" +
        "{edges{node{objectType content(country:DE,language:\"de\"){title originalTitle}" +
        " offers(country:DE,platform:WEB){standardWebURL package{technicalName}}}}}}"
    private val ENTITY = Regex("/browse/entity-([0-9a-f-]{36})")

    /** Entity-ID der Serie bzw. des Films [name] auf Disney+, oder null. */
    fun lookup(name: String, isSeries: Boolean): String? = runCatching {
        val body = JSONObject().put("query", QUERY).put("variables", JSONObject().put("q", name)).toString()
        val conn = URL(API).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        val text = try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        pickEntity(text, name, isSeries)
    }.getOrNull()

    /** Aus der JustWatch-Antwort die Disney+-Entity zum exakt passenden Namen; Serie/Film wie gewünscht bevorzugt. */
    fun pickEntity(json: String, name: String, isSeries: Boolean): String? = runCatching {
        val edges = JSONObject(json).getJSONObject("data").getJSONObject("popularTitles").getJSONArray("edges")
        val want = normTitle(name)
        val hits = (0 until edges.length()).mapNotNull { i ->
            val node = edges.getJSONObject(i).getJSONObject("node")
            val c = node.optJSONObject("content") ?: return@mapNotNull null
            // Nur Treffer mit genau diesem Namen – sonst lieber kein Link als ein falscher.
            if (normTitle(c.optString("title")) != want && normTitle(c.optString("originalTitle")) != want) return@mapNotNull null
            val offers = node.optJSONArray("offers") ?: return@mapNotNull null
            val id = (0 until offers.length()).firstNotNullOfOrNull { j ->
                val o = offers.getJSONObject(j)
                if (!o.optJSONObject("package")?.optString("technicalName").orEmpty().contains("disney")) null
                else ENTITY.find(o.optString("standardWebURL"))?.groupValues?.get(1)
            } ?: return@mapNotNull null
            (node.optString("objectType") == "SHOW") to id
        }
        (hits.firstOrNull { it.first == isSeries } ?: hits.firstOrNull())?.second
    }.getOrNull()
}
