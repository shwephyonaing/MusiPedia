package com.musium.app

import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class YouTubeRepository {
    suspend fun searchMusic(query: String): Result<List<YouTubeVideo>> = withContext(Dispatchers.IO) {
        runCatching {
        require(BuildConfig.YOUTUBE_API_KEY.isNotBlank()) {
            "Add YOUTUBE_API_KEY to local.properties before searching."
        }

        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val requestUrl = URI(
            "https://www.googleapis.com/youtube/v3/search" +
                "?part=snippet&type=video&videoCategoryId=10&maxResults=20" +
                "&q=$encodedQuery&key=${BuildConfig.YOUTUBE_API_KEY}"
        ).toURL()
        val connection = requestUrl.openConnection() as HttpURLConnection

        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.requestMethod = "GET"
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val items = JSONObject(responseText).getJSONArray("items")

            buildList {
                for (index in 0 until items.length()) {
                    val item = items.getJSONObject(index)
                    val videoId = item.optJSONObject("id")?.optString("videoId").orEmpty()
                    val snippet = item.optJSONObject("snippet") ?: continue
                    val thumbnails = snippet.optJSONObject("thumbnails") ?: continue
                    val thumbnailUrl = thumbnails.optJSONObject("high")?.optString("url")
                        ?: thumbnails.optJSONObject("medium")?.optString("url")
                        ?: thumbnails.optJSONObject("default")?.optString("url")
                        ?: continue

                    if (videoId.isNotBlank()) {
                        add(
                            YouTubeVideo(
                                videoId = videoId,
                                title = snippet.optString("title"),
                                channelName = snippet.optString("channelTitle"),
                                thumbnailUrl = thumbnailUrl,
                            )
                        )
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        }
    }
}
