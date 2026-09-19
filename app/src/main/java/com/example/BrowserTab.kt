package com.example

import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Data model representing a browser tab with its URL, title, scroll state, and saved state bundle.
 */
data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    var url: String = "about:blank",
    var title: String = "New Tab",
    var scrollX: Int = 0,
    var scrollY: Int = 0,
    var isDesktopMode: Boolean = false,
    var savedState: Bundle? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("url", url)
            put("title", title)
            put("scrollX", scrollX)
            put("scrollY", scrollY)
            put("isDesktopMode", isDesktopMode)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): BrowserTab {
            return BrowserTab(
                id = json.optString("id", UUID.randomUUID().toString()),
                url = json.optString("url", "about:blank"),
                title = json.optString("title", "New Tab"),
                scrollX = json.optInt("scrollX", 0),
                scrollY = json.optInt("scrollY", 0),
                isDesktopMode = json.optBoolean("isDesktopMode", false)
            )
        }
    }
}
