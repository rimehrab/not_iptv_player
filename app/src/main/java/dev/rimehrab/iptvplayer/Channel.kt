package dev.rimehrab.iptvplayer

import java.net.HttpURLConnection
import java.net.URL

data class Channel(
    val name: String,
    val url: String,
    val logo: String? = null
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

    fun parse(m3u: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        var pendingName = ""
        var pendingLogo: String? = null

        for (rawLine in m3u.lineSequence()) {
            val line = rawLine.trim()
            when {
                line.startsWith("#EXTINF") -> {
                    pendingName = line.substringAfterLast(",").trim()
                    pendingLogo = logoRegex.find(line)?.groupValues?.get(1)
                }
                line.startsWith("http://") || line.startsWith("https://") -> {
                    val name = if (pendingName.isNotBlank()) pendingName else "Channel ${channels.size + 1}"
                    channels.add(Channel(name, line, pendingLogo))
                    pendingName = ""
                    pendingLogo = null
                }
            }
        }
        return channels
    }
}
