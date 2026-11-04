package com.mytube.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Duration

data class YouTubeVideo(
    val id: String,
    val title: String,
    val channel: String,
    val thumbnail: String,
    val description: String,
    val publishedAt: String,
    val duration: String,
    val views: String
)

object YouTubeDataApi {
    private const val BASE = "https://www.googleapis.com/youtube/v3/"

    suspend fun trending(apiKey: String): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "YouTube API key দিন Settings-এ।" }
        val url = BASE + "videos?part=snippet,contentDetails,statistics&chart=mostPopular&regionCode=BD&maxResults=20&key=" + enc(apiKey)
        parseVideoList(request(url))
    }

    suspend fun search(apiKey: String, query: String, shortOnly: Boolean = false): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "YouTube API key দিন Settings-এ।" }
        require(query.isNotBlank()) { "কী খুঁজতে চান লিখুন।" }
        val shortFilter = if (shortOnly) "&videoDuration=short" else ""
        val searchUrl = BASE + "search?part=snippet&type=video&regionCode=BD&maxResults=20" +
            shortFilter + "&q=" + enc(query) + "&key=" + enc(apiKey)
        val searchJson = request(searchUrl)
        val ids = mutableListOf<String>()
        val snippets = mutableMapOf<String, JSONObject>()
        val items = searchJson.optJSONArray("items") ?: return@withContext emptyList()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val id = item.optJSONObject("id")?.optString("videoId").orEmpty()
            if (id.isNotBlank()) {
                ids.add(id)
                item.optJSONObject("snippet")?.let { snippets[id] = it }
            }
        }
        if (ids.isEmpty()) return@withContext emptyList()
        val detailsUrl = BASE + "videos?part=snippet,contentDetails,statistics&id=" +
            enc(ids.joinToString(",")) + "&key=" + enc(apiKey)
        val details = parseVideoList(request(detailsUrl)).associateBy { it.id }
        ids.mapNotNull { details[it] ?: snippets[it]?.let { s -> fromSnippet(it, s) } }
    }

    private fun request(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (code !in 200..299) {
                val message = json.optJSONObject("error")?.optString("message")
                throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "YouTube API error: HTTP $code")
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    private fun parseVideoList(json: JSONObject): List<YouTubeVideo> {
        val items = json.optJSONArray("items") ?: return emptyList()
        return buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val snippet = item.optJSONObject("snippet") ?: continue
                if (id.isNotBlank()) add(fromSnippet(id, snippet, item))
            }
        }
    }

    private fun fromSnippet(id: String, snippet: JSONObject, root: JSONObject? = null): YouTubeVideo {
        val thumbnails = snippet.optJSONObject("thumbnails")
        val thumb = thumbnails?.optJSONObject("high")?.optString("url")
            ?: thumbnails?.optJSONObject("medium")?.optString("url")
            ?: thumbnails?.optJSONObject("default")?.optString("url").orEmpty()
        val durationRaw = root?.optJSONObject("contentDetails")?.optString("duration").orEmpty()
        val duration = try {
            val seconds = Duration.parse(durationRaw).seconds
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        } catch (_: Exception) { "" }
        val views = root?.optJSONObject("statistics")?.optString("viewCount").orEmpty()
        return YouTubeVideo(
            id = id,
            title = snippet.optString("title", "Untitled"),
            channel = snippet.optString("channelTitle", "Unknown channel"),
            thumbnail = thumb,
            description = snippet.optString("description"),
            publishedAt = snippet.optString("publishedAt").take(10),
            duration = duration,
            views = views
        )
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
