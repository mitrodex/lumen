package de.tb.lumen

import java.text.Normalizer
import java.util.Locale

/*
 * Reine Text-Logik rund um Titel, Folgen und Seriennamen – ohne Android, damit sie sich mit
 * einfachen Unit-Tests prüfen lässt (app/src/test).
 */

/** Vergleich ohne Groß/klein, Akzente und Satzzeichen („Der Feuer-Drache“ = „der feuer drache“). */
fun normTitle(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}"), "").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

/** Staffel, Folge und Folgentitel aus einem Untertitel wie „S8:F9 Titel“ ([title] leer, wenn keiner dasteht). */
data class NumberedEpisode(val season: Int, val episode: Int, val title: String)

/** „S8:F9 Titel“, „S8 E9 – Titel“, „Staffel 8, Folge 9: Titel“ (Disney+ & Co.); sonst null. */
fun parseNumberedEpisode(subtitle: String): NumberedEpisode? {
    val m = EPISODE.find(subtitle) ?: return null
    val (s, e, rest) = m.destructured
    return NumberedEpisode(s.toInt(), e.toInt(), rest.trim())
}

private val EPISODE = Regex(
    """^\s*(?:S|Staffel\s*|Season\s*)(\d+)\s*[:.,·]?\s*(?:F|E|Folge\s*|Episode\s*)(\d+)\s*[:.\-–·]*\s*(.*)$""",
    RegexOption.IGNORE_CASE,
)

/** Serienname und Staffel aus dem Seitentitel von Prime Video. */
data class PrimeTitle(val name: String, val season: Int?)

/** „Prime Video: Die Küstenwache - Staffel 3 [OV]“ → („Die Küstenwache“, 3); leer → null. */
fun parsePrimeTitle(raw: String): PrimeTitle? {
    val clean = raw.removePrefix("Prime Video:").replace(Regex("\\s*\\[[^]]*]\\s*$"), "").trim()
    if (clean.isEmpty()) return null
    val season = Regex("\\s+[-–]\\s+Staffel\\s+(\\d+)\\s*$").find(clean)
    val name = season?.let { clean.substring(0, it.range.first).trim() } ?: clean
    return PrimeTitle(name, season?.groupValues?.get(1)?.toIntOrNull())
}

/**
 * Überschrift und Unterzeile einer „Weiterschauen“-Empfehlung. Netflix schreibt in den Titel nur
 * „Mit dem Profil von … weiterschauen“, der Inhalt steht im Text („Nachtzug, Abfahrt (2)“ → „Nachtzug“,
 * „Abfahrt (2)“). Andere Apps: Titel + kurzer Text.
 */
fun splitRecommendation(title: String, text: String): Pair<String, String?> {
    if (BOILERPLATE.containsMatchIn(title) && text.isNotEmpty()) {
        val i = text.indexOf(", ")
        return if (i > 0) text.substring(0, i) to text.substring(i + 2) else text to null
    }
    return title to text.takeIf { it.isNotEmpty() && it.length <= 60 }
}

private val BOILERPLATE = Regex("weiterschauen|continue watching|keep watching", RegexOption.IGNORE_CASE)
