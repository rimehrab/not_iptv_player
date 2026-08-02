package dev.rimehrab.iptvplayer

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

data class Channel(
    val name: String,
    val url: String,
    val logo: String? = null,
    val category: String? = null,
    val headers: Map<String, String> = emptyMap()
)

object PlaylistLoader {

    // Hardcoded playlist, per spec
    const val PLAYLIST_URL = "https://iptv.rimehrab.qd.je"

    fun fetch(): List<Channel> {
        val conn = URL(PLAYLIST_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.requestMethod = "GET"
        conn.useCaches = false
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (IPTVPlayer)")
        conn.setRequestProperty("Cache-Control", "no-cache, no-store")
        conn.setRequestProperty("Pragma", "no-cache")

        val text = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        return parse(text)
    }

    private val logoRegex = Regex("tvg-logo=\"(.*?)\"")
    private val groupRegex = Regex("group-title=\"(.*?)\"")
    private val vlcOptRegex = Regex("#EXTVLCOPT:(\\S+?)=(.+)")

    // Some entries carry a User-Agent / Referer as an EXTVLCOPT key rather than the
    // header name itself — map the common variants to real HTTP header names.
    private fun headerNameFor(vlcKey: String): String? = when (vlcKey.lowercase()) {
        "http-user-agent" -> "User-Agent"
        "http-referrer", "http-referer" -> "Referer"
        "http-origin" -> "Origin"
        "http-cookie" -> "Cookie"
        else -> null
    }

    // Some playlists append headers straight onto the stream URL as
    // "http://host/stream.m3u8|Referer=https://x.com/&User-Agent=Mozilla/5.0".
    // VLC/Televizo understand that convention; strip it out into real headers here.
    private fun splitUrlAndPipeHeaders(rawUrl: String): Pair<String, Map<String, String>> {
        val pipeIndex = rawUrl.indexOf('|')
        if (pipeIndex == -1) return rawUrl to emptyMap()

        val url = rawUrl.substring(0, pipeIndex)
        val headerPart = rawUrl.substring(pipeIndex + 1)
        val headers = mutableMapOf<String, String>()

        for (pair in headerPart.split('&')) {
            val eq = pair.indexOf('=')
            if (eq == -1) continue
            val key = pair.substring(0, eq).trim()
            val value = try {
                URLDecoder.decode(pair.substring(eq + 1).trim(), "UTF-8")
            } catch (e: Exception) {
                pair.substring(eq + 1).trim()
            }
            if (key.isNotEmpty() && value.isNotEmpty()) {
                headers[key] = value
            }
        }
        return url to headers
    }

    fun parse(m3u: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        var pendingName = ""
        var pendingLogo: String? = null
        var pendingCategory: String? = null
        val pendingHeaders = mutableMapOf<String, String>()

        for (rawLine in m3u.lineSequence()) {
            val line = rawLine.trim()
            when {
                line.startsWith("#EXTINF") -> {
                    pendingName = line.substringAfterLast(",").trim()
                    pendingLogo = logoRegex.find(line)?.groupValues?.get(1)
                    pendingCategory = groupRegex.find(line)?.groupValues?.get(1)
                }
                line.startsWith("#EXTVLCOPT") -> {
                    val match = vlcOptRegex.find(line)
                    if (match != null) {
                        val (vlcKey, value) = match.destructured
                        headerNameFor(vlcKey)?.let { pendingHeaders[it] = value.trim() }
                    }
                }
                line.startsWith("http://") || line.startsWith("https://") -> {
                    val (url, pipeHeaders) = splitUrlAndPipeHeaders(line)
                    val name = if (pendingName.isNotBlank()) pendingName else "Channel ${channels.size + 1}"
                    val headers = pendingHeaders + pipeHeaders // pipe headers win if both present
                    channels.add(Channel(name, url, pendingLogo, pendingCategory, headers))
                    pendingName = ""
                    pendingLogo = null
                    pendingCategory = null
                    pendingHeaders.clear()
                }
            }
        }
        return channels
    }
}
