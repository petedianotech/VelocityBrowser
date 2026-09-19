package com.example

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages active browser tabs, switching, persistent storage across app backgrounding,
 * and zero-crash state restoration.
 */
class TabSessionManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("fast_browser_session", Context.MODE_PRIVATE)

    val tabs: MutableList<BrowserTab> = mutableListOf()
    var activeTabIndex: Int = 0

    init {
        restoreFromDisk()
    }

    fun getActiveTab(): BrowserTab {
        if (tabs.isEmpty()) {
            val defaultTab = BrowserTab(url = "about:blank", title = "New Tab")
            tabs.add(defaultTab)
            activeTabIndex = 0
            return defaultTab
        }
        if (activeTabIndex !in tabs.indices) {
            activeTabIndex = 0
        }
        return tabs[activeTabIndex]
    }

    fun addNewTab(url: String = "about:blank", title: String = "New Tab"): BrowserTab {
        val tab = BrowserTab(url = url, title = title)
        tabs.add(tab)
        activeTabIndex = tabs.size - 1
        saveToDisk()
        return tab
    }

    fun closeTab(index: Int): BrowserTab {
        if (index in tabs.indices) {
            tabs.removeAt(index)
            if (index < activeTabIndex) {
                activeTabIndex--
            } else if (activeTabIndex >= tabs.size) {
                activeTabIndex = tabs.size - 1
            }
        }
        if (tabs.isEmpty()) {
            tabs.add(BrowserTab(url = "about:blank", title = "New Tab"))
            activeTabIndex = 0
        }
        saveToDisk()
        return getActiveTab()
    }

    fun closeAllTabs() {
        tabs.clear()
        tabs.add(BrowserTab(url = "about:blank", title = "New Tab"))
        activeTabIndex = 0
        saveToDisk()
    }

    fun selectTab(index: Int): BrowserTab {
        if (index in tabs.indices) {
            activeTabIndex = index
            saveToDisk()
        }
        return getActiveTab()
    }

    fun saveToDisk() {
        try {
            val jsonArray = JSONArray()
            for (tab in tabs) {
                jsonArray.put(tab.toJson())
            }
            prefs.edit()
                .putString("saved_tabs", jsonArray.toString())
                .putInt("active_tab_index", activeTabIndex)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restoreFromDisk() {
        try {
            val jsonStr = prefs.getString("saved_tabs", null)
            if (!jsonStr.isNullOrEmpty()) {
                val jsonArray = JSONArray(jsonStr)
                tabs.clear()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    tabs.add(BrowserTab.fromJson(obj))
                }
                activeTabIndex = prefs.getInt("active_tab_index", 0)
                if (activeTabIndex !in tabs.indices) {
                    activeTabIndex = 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (tabs.isEmpty()) {
            tabs.add(BrowserTab(url = "about:blank", title = "New Tab"))
            activeTabIndex = 0
        }
    }

    fun saveToBundle(outState: Bundle) {
        val tabIds = ArrayList<String>()
        val tabUrls = ArrayList<String>()
        val tabTitles = ArrayList<String>()
        val tabScrollX = ArrayList<Int>()
        val tabScrollY = ArrayList<Int>()

        for (tab in tabs) {
            tabIds.add(tab.id)
            tabUrls.add(tab.url)
            tabTitles.add(tab.title)
            tabScrollX.add(tab.scrollX)
            tabScrollY.add(tab.scrollY)
        }

        outState.putStringArrayList("tab_ids", tabIds)
        outState.putStringArrayList("tab_urls", tabUrls)
        outState.putStringArrayList("tab_titles", tabTitles)
        outState.putIntegerArrayList("tab_scroll_x", tabScrollX)
        outState.putIntegerArrayList("tab_scroll_y", tabScrollY)
        outState.putInt("active_tab_index", activeTabIndex)

        saveToDisk()
    }

    fun restoreFromBundle(savedInstanceState: Bundle) {
        val tabIds = savedInstanceState.getStringArrayList("tab_ids")
        val tabUrls = savedInstanceState.getStringArrayList("tab_urls")
        val tabTitles = savedInstanceState.getStringArrayList("tab_titles")
        val tabScrollX = savedInstanceState.getIntegerArrayList("tab_scroll_x")
        val tabScrollY = savedInstanceState.getIntegerArrayList("tab_scroll_y")

        if (tabIds != null && tabUrls != null && tabIds.size == tabUrls.size) {
            tabs.clear()
            for (i in tabIds.indices) {
                tabs.add(
                    BrowserTab(
                        id = tabIds[i],
                        url = tabUrls[i],
                        title = tabTitles?.getOrNull(i) ?: "Tab",
                        scrollX = tabScrollX?.getOrNull(i) ?: 0,
                        scrollY = tabScrollY?.getOrNull(i) ?: 0
                    )
                )
            }
            activeTabIndex = savedInstanceState.getInt("active_tab_index", 0)
            if (activeTabIndex !in tabs.indices) {
                activeTabIndex = 0
            }
        }
    }

    // Quick Bookmarks persistence
    fun getBookmarks(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val raw = prefs.getString("bookmarks_list", null) ?: return list
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(Pair(obj.optString("title"), obj.optString("url")))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun addBookmark(title: String, url: String) {
        val current = getBookmarks().toMutableList()
        current.removeAll { it.second == url }
        current.add(0, Pair(title, url))
        val array = JSONArray()
        for (item in current.take(50)) {
            val obj = JSONObject().apply {
                put("title", item.first)
                put("url", item.second)
            }
            array.put(obj)
        }
        prefs.edit().putString("bookmarks_list", array.toString()).apply()
    }

    fun removeBookmark(url: String) {
        val current = getBookmarks().toMutableList()
        current.removeAll { it.second == url }
        val array = JSONArray()
        for (item in current) {
            val obj = JSONObject().apply {
                put("title", item.first)
                put("url", item.second)
            }
            array.put(obj)
        }
        prefs.edit().putString("bookmarks_list", array.toString()).apply()
    }
}
