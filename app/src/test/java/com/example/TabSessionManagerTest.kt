package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TabSessionManagerTest {

    private lateinit var context: Context
    private lateinit var manager: TabSessionManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("fast_browser_session", Context.MODE_PRIVATE).edit().clear().commit()
        manager = TabSessionManager(context)
    }

    @Test
    fun testInitialTab() {
        assertEquals(1, manager.tabs.size)
        assertEquals(0, manager.activeTabIndex)
        val activeTab = manager.getActiveTab()
        assertNotNull(activeTab)
        assertEquals("about:blank", activeTab.url)
    }

    @Test
    fun testAddNewTab() {
        manager.addNewTab("https://example.com", "Example")
        assertEquals(2, manager.tabs.size)
        assertEquals(1, manager.activeTabIndex)
        assertEquals("https://example.com", manager.getActiveTab().url)
    }

    @Test
    fun testCloseTabAdjustment() {
        manager.addNewTab("https://first.com", "First")
        manager.addNewTab("https://second.com", "Second")
        assertEquals(3, manager.tabs.size)
        assertEquals(2, manager.activeTabIndex) // active is Second

        // Close index 0 (which is before active index)
        manager.closeTab(0)
        assertEquals(2, manager.tabs.size)
        assertEquals(1, manager.activeTabIndex)
        assertEquals("https://second.com", manager.getActiveTab().url)
    }

    @Test
    fun testBookmarks() {
        manager.addBookmark("Google", "https://google.com")
        val bookmarks = manager.getBookmarks()
        assertEquals(1, bookmarks.size)
        assertEquals("Google", bookmarks[0].first)
        assertEquals("https://google.com", bookmarks[0].second)

        manager.removeBookmark("https://google.com")
        assertTrue(manager.getBookmarks().isEmpty())
    }
}
