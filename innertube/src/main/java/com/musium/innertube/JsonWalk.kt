package com.musium.innertube

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.obj(key: String): JSONObject? = optJSONObject(key)

internal fun JSONObject.arr(key: String): JSONArray? = optJSONArray(key)

internal fun JSONObject.str(key: String): String? = optString(key).takeIf { it.isNotBlank() }

internal fun JSONObject.child(vararg keys: String): JSONObject? {
    var current: JSONObject? = this
    for (key in keys) current = current?.obj(key) ?: return null
    return current
}

internal fun JSONObject.runsText(key: String = "title"): String? {
    val node = obj(key) ?: return null
    node.str("simpleText")?.let { return it }
    val runs = node.arr("runs") ?: return null
    return buildString {
        for (index in 0 until runs.length()) {
            append(runs.optJSONObject(index)?.optString("text").orEmpty())
        }
    }.takeIf { it.isNotBlank() }
}

internal fun JSONArray.objects(): Sequence<JSONObject> = sequence {
    for (index in 0 until length()) {
        optJSONObject(index)?.let { yield(it) }
    }
}

internal fun JSONObject.walkObjects(visit: (String, JSONObject) -> Unit) {
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        when (val value = opt(key)) {
            is JSONObject -> {
                visit(key, value)
                value.walkObjects(visit)
            }
            is JSONArray -> value.objects().forEach { it.walkObjects(visit) }
        }
    }
}

internal fun JSONObject.bestThumbnail(): String? {
    var bestUrl: String? = null
    var bestWidth = -1
    fun consider(url: String?, width: Int) {
        if (url.isNullOrBlank()) return
        if (width >= bestWidth) {
            bestWidth = width
            bestUrl = url.normalizeThumb()
        }
    }
    arr("thumbnails")?.objects()?.forEach { consider(it.str("url"), it.optInt("width")) }
    walkObjects { _, obj ->
        obj.arr("thumbnails")?.objects()?.forEach { consider(it.str("url"), it.optInt("width")) }
        if (obj.has("url") && obj.has("width")) consider(obj.str("url"), obj.optInt("width"))
    }
    return bestUrl
}

private fun String.normalizeThumb(): String = if (startsWith("//")) "https:$this" else this

internal fun JSONObject.firstVideoId(): String? {
    str("videoId")?.let { return it }
    child("playlistItemData")?.str("videoId")?.let { return it }
    child("navigationEndpoint", "watchEndpoint")?.str("videoId")?.let { return it }
    child("onTap", "watchEndpoint")?.str("videoId")?.let { return it }
    var found: String? = null
    walkObjects { _, obj ->
        if (found != null) return@walkObjects
        obj.child("watchEndpoint")?.str("videoId")?.let { found = it }
    }
    return found
}

internal fun JSONObject.browseEndpoint(): JSONObject? {
    child("navigationEndpoint", "browseEndpoint")?.let { return it }
    var found: JSONObject? = null
    walkObjects { key, obj ->
        if (found == null && key == "browseEndpoint" && obj.str("browseId") != null) {
            found = obj
        }
    }
    return found
}

internal fun JSONObject.pageType(): String? =
    child("browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig")?.str("pageType")
        ?: child("navigationEndpoint", "browseEndpoint")?.pageType()

internal fun JSONObject.musicVideoType(): String? {
    str("musicVideoType")?.let { return it }
    child("watchEndpointMusicSupportedConfigs", "watchEndpointMusicConfig")?.str("musicVideoType")?.let { return it }
    var found: String? = null
    walkObjects { _, obj ->
        if (found == null) found = obj.str("musicVideoType")
    }
    return found
}

internal fun JSONObject.clockSeconds(key: String = "lengthText"): Int? {
    val text = runsText(key) ?: child(key)?.str("simpleText") ?: return null
    val parts = text.split(':').mapNotNull { it.trim().toIntOrNull() }
    return when (parts.size) {
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> null
    }
}
