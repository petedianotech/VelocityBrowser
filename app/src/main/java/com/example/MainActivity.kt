package com.example

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity(), FastWebClient.BrowserClientListener, FastChromeClient.ChromeListener {

    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var webViewContainer: FrameLayout
    private lateinit var mainWebView: WebView
    private lateinit var startPageContainer: ScrollView
    private lateinit var gridShortcuts: GridLayout
    private lateinit var containerBookmarks: LinearLayout
    private lateinit var tvBookmarksHeader: TextView
    private lateinit var fullscreenContainer: FrameLayout

    // Find in page
    private lateinit var layoutFindInPage: LinearLayout
    private lateinit var etFindQuery: EditText
    private lateinit var btnFindPrev: ImageButton
    private lateinit var btnFindNext: ImageButton
    private lateinit var btnFindClose: ImageButton

    // Bottom Bar
    private lateinit var btnBack: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var ivSslStatus: ImageView
    private lateinit var etUrlBar: EditText
    private lateinit var btnUrlAction: ImageButton
    private lateinit var btnTabs: TextView
    private lateinit var btnHome: ImageButton
    private lateinit var btnMenu: ImageButton

    private lateinit var tabManager: TabSessionManager
    private lateinit var webClient: FastWebClient
    private lateinit var chromeClient: FastChromeClient

    private var isLoadingPage: Boolean = false
    private var customVideoView: View? = null
    private var customVideoCallback: WebChromeClient.CustomViewCallback? = null

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        fileUploadCallback?.onReceiveValue(uris)
        fileUploadCallback = null
    }

    // Default desktop user agent template
    private val desktopUserAgent =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    private var defaultUserAgent: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tabManager = TabSessionManager(this)

        initViews()
        setupWebView()
        setupBottomBar()
        setupFindInPage()
        setupSpeedDial()
        setupBackNavigation()

        // Zero-Crash State Restoration
        if (savedInstanceState != null) {
            tabManager.restoreFromBundle(savedInstanceState)
            val savedStateBundle = savedInstanceState.getBundle("webview_active_state")
            val activeTab = tabManager.getActiveTab()

            if (savedStateBundle != null) {
                mainWebView.restoreState(savedStateBundle)
                if (activeTab.scrollY > 0 || activeTab.scrollX > 0) {
                    mainWebView.post { mainWebView.scrollTo(activeTab.scrollX, activeTab.scrollY) }
                }
            } else if (activeTab.url != "about:blank") {
                loadUrl(activeTab.url)
            } else {
                showStartPage()
            }
        } else {
            val activeTab = tabManager.getActiveTab()
            if (activeTab.url.isNotBlank() && activeTab.url != "about:blank") {
                loadUrl(activeTab.url)
            } else {
                showStartPage()
            }
        }

        updateTabsBadge()
    }

    private fun initViews() {
        progressBar = findViewById(R.id.progressBar)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        webViewContainer = findViewById(R.id.webViewContainer)
        mainWebView = findViewById(R.id.mainWebView)
        startPageContainer = findViewById(R.id.startPageContainer)
        gridShortcuts = findViewById(R.id.gridShortcuts)
        containerBookmarks = findViewById(R.id.containerBookmarks)
        tvBookmarksHeader = findViewById(R.id.tvBookmarksHeader)
        fullscreenContainer = findViewById(R.id.fullscreenContainer)

        layoutFindInPage = findViewById(R.id.layoutFindInPage)
        etFindQuery = findViewById(R.id.etFindQuery)
        btnFindPrev = findViewById(R.id.btnFindPrev)
        btnFindNext = findViewById(R.id.btnFindNext)
        btnFindClose = findViewById(R.id.btnFindClose)

        btnBack = findViewById(R.id.btnBack)
        btnForward = findViewById(R.id.btnForward)
        ivSslStatus = findViewById(R.id.ivSslStatus)
        etUrlBar = findViewById(R.id.etUrlBar)
        btnUrlAction = findViewById(R.id.btnUrlAction)
        btnTabs = findViewById(R.id.btnTabs)
        btnHome = findViewById(R.id.btnHome)
        btnMenu = findViewById(R.id.btnMenu)

        // Swipe refresh styling
        swipeRefreshLayout.setColorSchemeColors(getColorRes(R.color.browser_accent))
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(getColorRes(R.color.browser_surface))
        swipeRefreshLayout.setOnRefreshListener {
            if (startPageContainer.visibility == View.VISIBLE) {
                swipeRefreshLayout.isRefreshing = false
            } else {
                mainWebView.reload()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        // Hardware acceleration
        mainWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val settings = mainWebView.settings
        defaultUserAgent = settings.userAgentString

        // Ultra-Fast & Battery/Data Saving settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        // Battery & Data Saving: Block auto-play videos
        settings.mediaPlaybackRequiresUserGesture = true

        // File & content access
        settings.allowFileAccess = false
        settings.allowContentAccess = true

        // Mixed content support
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        // Dark Theme Web content if supported
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(settings, if (isNightMode) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF)
        }

        // Enable cookies
        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(mainWebView, true)
        }

        // Native download manager support
        mainWebView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimetype)
                    addRequestHeader("User-Agent", userAgent)
                    setDescription("Downloading file…")
                    val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                    setTitle(fileName)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                }
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(this, "Downloading file…", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (ex: Exception) {
                    Toast.makeText(this, "Unable to download file", Toast.LENGTH_SHORT).show()
                }
            }
        }

        webClient = FastWebClient(this, this)
        mainWebView.webViewClient = webClient

        chromeClient = FastChromeClient(this)
        mainWebView.webChromeClient = chromeClient
    }

    private fun setupBottomBar() {
        btnBack.setOnClickListener {
            if (mainWebView.canGoBack()) {
                mainWebView.goBack()
            } else if (startPageContainer.visibility != View.VISIBLE) {
                showStartPage()
            }
        }

        btnForward.setOnClickListener {
            if (mainWebView.canGoForward()) {
                mainWebView.goForward()
            }
        }

        btnHome.setOnClickListener {
            showStartPage()
        }

        btnTabs.setOnClickListener {
            showTabsBottomSheet()
        }

        btnUrlAction.setOnClickListener {
            if (isLoadingPage) {
                mainWebView.stopLoading()
            } else {
                mainWebView.reload()
            }
        }

        btnMenu.setOnClickListener { view ->
            showPopupMenu(view)
        }

        etUrlBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val input = etUrlBar.text.toString().trim()
                if (input.isNotEmpty()) {
                    loadUrl(normalizeUrl(input))
                    hideKeyboard()
                    etUrlBar.clearFocus()
                }
                true
            } else {
                false
            }
        }

        etUrlBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val currentUrl = mainWebView.url ?: ""
                if (currentUrl.isNotEmpty() && currentUrl != "about:blank") {
                    etUrlBar.setText(currentUrl)
                }
                etUrlBar.selectAll()
            } else {
                updateUrlBarDisplay(mainWebView.url ?: "")
            }
        }
    }

    private fun setupFindInPage() {
        etFindQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = etFindQuery.text.toString().trim()
                if (query.isNotEmpty()) {
                    mainWebView.findAllAsync(query)
                }
                hideKeyboard()
                true
            } else {
                false
            }
        }

        btnFindNext.setOnClickListener {
            mainWebView.findNext(true)
        }

        btnFindPrev.setOnClickListener {
            mainWebView.findNext(false)
        }

        btnFindClose.setOnClickListener {
            mainWebView.clearMatches()
            layoutFindInPage.visibility = View.GONE
            hideKeyboard()
        }
    }

    private fun setupSpeedDial() {
        val shortcuts = listOf(
            Triple("Google", "https://www.google.com", "G"),
            Triple("DuckDuckGo", "https://duckduckgo.com", "D"),
            Triple("Wikipedia", "https://en.wikipedia.org", "W"),
            Triple("GitHub", "https://github.com", "GH"),
            Triple("Reddit", "https://www.reddit.com", "R"),
            Triple("Hacker News", "https://news.ycombinator.com", "HN"),
            Triple("BBC News", "https://www.bbc.com", "B"),
            Triple("Weather", "https://wttr.in", "🌤")
        )

        gridShortcuts.removeAllViews()
        val inflater = LayoutInflater.from(this)

        for (shortcut in shortcuts) {
            val itemView = inflater.inflate(R.layout.item_shortcut, gridShortcuts, false)
            val tvIcon = itemView.findViewById<TextView>(R.id.tvShortcutIconLetter)
            val tvTitle = itemView.findViewById<TextView>(R.id.tvShortcutTitle)

            tvIcon.text = shortcut.third
            tvTitle.text = shortcut.first

            itemView.setOnClickListener {
                loadUrl(shortcut.second)
            }

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
            gridShortcuts.addView(itemView, params)
        }

        refreshBookmarksList()
    }

    private fun refreshBookmarksList() {
        containerBookmarks.removeAllViews()
        val bookmarks = tabManager.getBookmarks()
        if (bookmarks.isEmpty()) {
            tvBookmarksHeader.visibility = View.GONE
            return
        }
        tvBookmarksHeader.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(this)
        for (bm in bookmarks.take(5)) {
            val bmView = inflater.inflate(R.layout.item_bookmark, containerBookmarks, false)
            val tvTitle = bmView.findViewById<TextView>(R.id.tvBookmarkTitle)
            val tvUrl = bmView.findViewById<TextView>(R.id.tvBookmarkUrl)
            val btnDel = bmView.findViewById<ImageButton>(R.id.btnDeleteBookmark)

            tvTitle.text = bm.first
            tvUrl.text = bm.second

            bmView.setOnClickListener {
                loadUrl(bm.second)
            }

            btnDel.setOnClickListener {
                tabManager.removeBookmark(bm.second)
                refreshBookmarksList()
            }
            containerBookmarks.addView(bmView)
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customVideoView != null) {
                    chromeClient.onHideCustomView()
                    return
                }
                if (layoutFindInPage.visibility == View.VISIBLE) {
                    layoutFindInPage.visibility = View.GONE
                    mainWebView.clearMatches()
                    return
                }
                if (mainWebView.canGoBack()) {
                    mainWebView.goBack()
                } else if (startPageContainer.visibility != View.VISIBLE) {
                    showStartPage()
                } else {
                    finish()
                }
            }
        })
    }

    fun loadUrl(url: String) {
        startPageContainer.visibility = View.GONE
        swipeRefreshLayout.visibility = View.VISIBLE
        updateUrlBarDisplay(url)

        val activeTab = tabManager.getActiveTab()
        activeTab.url = url
        tabManager.saveToDisk()

        mainWebView.loadUrl(url)
    }

    private fun showStartPage() {
        val activeTab = tabManager.getActiveTab()
        activeTab.url = "about:blank"
        activeTab.title = "New Tab"
        tabManager.saveToDisk()

        mainWebView.loadUrl("about:blank")
        startPageContainer.visibility = View.VISIBLE
        swipeRefreshLayout.visibility = View.GONE
        etUrlBar.setText("")
        ivSslStatus.setImageResource(R.drawable.ic_lock_secure)
        refreshBookmarksList()
        updateNavButtons()
    }

    private fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("about:") || trimmed.startsWith("file:")) {
            return trimmed
        }
        // If looks like a domain name (e.g. google.com, test.org)
        if (trimmed.contains(".") && !trimmed.contains(" ") && !trimmed.contains("?")) {
            return "https://$trimmed"
        }
        // Fallback to DuckDuckGo privacy search query
        return "https://duckduckgo.com/?q=" + Uri.encode(trimmed)
    }

    private fun updateUrlBarDisplay(url: String) {
        if (url.isEmpty() || url == "about:blank") {
            etUrlBar.setText("")
            ivSslStatus.setImageResource(R.drawable.ic_lock_secure)
            return
        }
        try {
            val uri = Uri.parse(url)
            val host = uri.host ?: url
            etUrlBar.setText(host)
        } catch (e: Exception) {
            etUrlBar.setText(url)
        }
    }

    private fun updateNavButtons() {
        btnBack.isEnabled = mainWebView.canGoBack() || startPageContainer.visibility != View.VISIBLE
        btnBack.alpha = if (btnBack.isEnabled) 1.0f else 0.4f

        btnForward.isEnabled = mainWebView.canGoForward()
        btnForward.alpha = if (btnForward.isEnabled) 1.0f else 0.4f
    }

    private fun updateTabsBadge() {
        btnTabs.text = tabManager.tabs.size.toString()
    }

    private fun showPopupMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        val activeTab = tabManager.getActiveTab()

        popup.menu.add(0, 1, 0, "New Tab")
        popup.menu.add(0, 2, 1, if (webClient.isDataSaverEnabled) "Data Saver: ON (Ad-Block)" else "Data Saver: OFF")
        popup.menu.add(0, 3, 2, if (activeTab.isDesktopMode) "Mobile Site" else "Desktop Site")
        popup.menu.add(0, 4, 3, "Find in Page")
        popup.menu.add(0, 5, 4, "Add to Bookmarks")
        popup.menu.add(0, 6, 5, "Saved Bookmarks")
        popup.menu.add(0, 7, 6, "Share Link")
        popup.menu.add(0, 8, 7, "Clear Cache")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { // New Tab
                    tabManager.addNewTab()
                    updateTabsBadge()
                    showStartPage()
                }
                2 -> { // Data Saver
                    webClient.isDataSaverEnabled = !webClient.isDataSaverEnabled
                    Toast.makeText(
                        this,
                        if (webClient.isDataSaverEnabled) "Data Saver enabled" else "Data Saver disabled",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                3 -> { // Desktop Site
                    activeTab.isDesktopMode = !activeTab.isDesktopMode
                    mainWebView.settings.userAgentString =
                        if (activeTab.isDesktopMode) desktopUserAgent else defaultUserAgent
                    mainWebView.reload()
                }
                4 -> { // Find in Page
                    layoutFindInPage.visibility = View.VISIBLE
                    etFindQuery.requestFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(etFindQuery, InputMethodManager.SHOW_IMPLICIT)
                }
                5 -> { // Add Bookmark
                    val url = mainWebView.url
                    if (!url.isNullOrEmpty() && url != "about:blank") {
                        val title = mainWebView.title ?: url
                        tabManager.addBookmark(title, url)
                        Toast.makeText(this, "Added to Bookmarks", Toast.LENGTH_SHORT).show()
                        refreshBookmarksList()
                    } else {
                        Toast.makeText(this, "Cannot bookmark start page", Toast.LENGTH_SHORT).show()
                    }
                }
                6 -> { // Saved Bookmarks Dialog
                    showBookmarksDialog()
                }
                7 -> { // Share Link
                    val url = mainWebView.url
                    if (!url.isNullOrEmpty() && url != "about:blank") {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                        }
                        startActivity(Intent.createChooser(shareIntent, "Share URL"))
                    }
                }
                8 -> { // Clear Cache
                    mainWebView.clearCache(true)
                    Toast.makeText(this, getString(R.string.cache_cleared), Toast.LENGTH_SHORT).show()
                }
            }
            true
        }
        popup.show()
    }

    private fun showTabsBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_tabs, null)
        dialog.setContentView(dialogView)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvTabsTitle)
        val btnCloseAll = dialogView.findViewById<Button>(R.id.btnCloseAllTabs)
        val btnAddTab = dialogView.findViewById<ImageButton>(R.id.btnAddNewTab)
        val rvTabs = dialogView.findViewById<RecyclerView>(R.id.rvTabsList)

        tvTitle.text = "Tabs (${tabManager.tabs.size})"
        rvTabs.layoutManager = LinearLayoutManager(this)

        var adapter: TabsAdapter? = null

        fun refreshAdapter() {
            tvTitle.text = "Tabs (${tabManager.tabs.size})"
            updateTabsBadge()
            adapter = TabsAdapter(
                tabs = tabManager.tabs,
                activeIndex = tabManager.activeTabIndex,
                onTabClick = { pos ->
                    switchTab(pos)
                    dialog.dismiss()
                },
                onTabClose = { pos ->
                    val wasActive = (pos == tabManager.activeTabIndex)
                    if (wasActive) {
                        val currentTab = tabManager.getActiveTab()
                        currentTab.scrollX = mainWebView.scrollX
                        currentTab.scrollY = mainWebView.scrollY
                        val bundle = Bundle()
                        mainWebView.saveState(bundle)
                        currentTab.savedState = bundle
                    }

                    val newActiveTab = tabManager.closeTab(pos)
                    updateTabsBadge()
                    if (wasActive) {
                        if (tabManager.tabs.isEmpty() || newActiveTab.url == "about:blank") {
                            showStartPage()
                        } else {
                            if (newActiveTab.savedState != null) {
                                startPageContainer.visibility = View.GONE
                                swipeRefreshLayout.visibility = View.VISIBLE
                                mainWebView.restoreState(newActiveTab.savedState!!)
                                updateUrlBarDisplay(newActiveTab.url)
                                mainWebView.post { mainWebView.scrollTo(newActiveTab.scrollX, newActiveTab.scrollY) }
                            } else {
                                loadUrl(newActiveTab.url)
                            }
                        }
                    }
                    refreshAdapter()
                }
            )
            rvTabs.adapter = adapter
        }

        refreshAdapter()

        btnAddTab.setOnClickListener {
            // Save state of current tab
            val currentTab = tabManager.getActiveTab()
            currentTab.scrollX = mainWebView.scrollX
            currentTab.scrollY = mainWebView.scrollY
            val bundle = Bundle()
            mainWebView.saveState(bundle)
            currentTab.savedState = bundle

            tabManager.addNewTab()
            updateTabsBadge()
            showStartPage()
            dialog.dismiss()
        }

        btnCloseAll.setOnClickListener {
            tabManager.closeAllTabs()
            updateTabsBadge()
            showStartPage()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun switchTab(targetIndex: Int) {
        if (targetIndex == tabManager.activeTabIndex) return

        // Save current tab state
        val currentTab = tabManager.getActiveTab()
        currentTab.scrollX = mainWebView.scrollX
        currentTab.scrollY = mainWebView.scrollY
        val bundle = Bundle()
        mainWebView.saveState(bundle)
        currentTab.savedState = bundle

        val targetTab = tabManager.selectTab(targetIndex)
        updateTabsBadge()

        if (targetTab.url == "about:blank") {
            showStartPage()
        } else if (targetTab.savedState != null) {
            startPageContainer.visibility = View.GONE
            swipeRefreshLayout.visibility = View.VISIBLE
            mainWebView.restoreState(targetTab.savedState!!)
            updateUrlBarDisplay(targetTab.url)
            mainWebView.post { mainWebView.scrollTo(targetTab.scrollX, targetTab.scrollY) }
        } else {
            loadUrl(targetTab.url)
        }
    }

    private fun showBookmarksDialog() {
        val dialog = BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_bookmarks, null)
        dialog.setContentView(dialogView)

        val rv = dialogView.findViewById<RecyclerView>(R.id.rvBookmarks)
        rv.layoutManager = LinearLayoutManager(this)

        val bookmarks = tabManager.getBookmarks()
        rv.adapter = BookmarksAdapter(
            bookmarks = bookmarks,
            onBookmarkClick = { url ->
                loadUrl(url)
                dialog.dismiss()
            },
            onBookmarkDelete = { url ->
                tabManager.removeBookmark(url)
                showBookmarksDialog() // reload
                dialog.dismiss()
                refreshBookmarksList()
            }
        )
        dialog.show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun getColorRes(id: Int): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            resources.getColor(id, theme)
        } else {
            @Suppress("DEPRECATION")
            resources.getColor(id)
        }
    }

    // ==========================================
    // FastWebClient.BrowserClientListener
    // ==========================================

    override fun onPageStarted(url: String, favicon: Bitmap?) {
        isLoadingPage = true
        progressBar.visibility = View.VISIBLE
        btnUrlAction.setImageResource(R.drawable.ic_close)
        btnUrlAction.contentDescription = getString(R.string.stop)
        updateNavButtons()
    }

    override fun onPageFinished(url: String, title: String?) {
        isLoadingPage = false
        progressBar.visibility = View.GONE
        swipeRefreshLayout.isRefreshing = false
        btnUrlAction.setImageResource(R.drawable.ic_refresh)
        btnUrlAction.contentDescription = getString(R.string.refresh)

        val activeTab = tabManager.getActiveTab()
        activeTab.url = url
        if (!title.isNullOrEmpty()) {
            activeTab.title = title
        }
        activeTab.scrollX = mainWebView.scrollX
        activeTab.scrollY = mainWebView.scrollY
        tabManager.saveToDisk()

        updateUrlBarDisplay(url)
        updateNavButtons()
    }

    override fun onUrlChanged(url: String, isSecure: Boolean) {
        if (isSecure) {
            ivSslStatus.setImageResource(R.drawable.ic_lock_secure)
            ivSslStatus.contentDescription = getString(R.string.ssl_secure)
        } else {
            ivSslStatus.setImageResource(R.drawable.ic_lock_open)
            ivSslStatus.contentDescription = getString(R.string.ssl_insecure)
        }
        if (!etUrlBar.hasFocus()) {
            updateUrlBarDisplay(url)
        }
    }

    override fun onProgressUpdate(progress: Int) {
        progressBar.progress = progress
    }

    override fun onErrorReceived(errorCode: Int, description: String, failingUrl: String) {
        progressBar.visibility = View.GONE
        swipeRefreshLayout.isRefreshing = false
        isLoadingPage = false
        btnUrlAction.setImageResource(R.drawable.ic_refresh)
    }

    // ==========================================
    // FastChromeClient.ChromeListener
    // ==========================================

    override fun onProgress(progress: Int) {
        progressBar.progress = progress
        if (progress >= 100) {
            progressBar.visibility = View.GONE
        } else {
            progressBar.visibility = View.VISIBLE
        }
    }

    override fun onTitle(title: String?) {
        if (!title.isNullOrEmpty()) {
            val activeTab = tabManager.getActiveTab()
            activeTab.title = title
            tabManager.saveToDisk()
        }
    }

    override fun onFavicon(icon: Bitmap?) {
        // Optional favicon badge
    }

    override fun onShowFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: WebChromeClient.FileChooserParams
    ): Boolean {
        fileUploadCallback?.onReceiveValue(null)
        fileUploadCallback = filePathCallback
        return try {
            val intent = fileChooserParams.createIntent()
            fileChooserLauncher.launch(intent)
            true
        } catch (e: Exception) {
            fileUploadCallback = null
            false
        }
    }

    override fun onJsAlert(message: String, result: JsResult): Boolean {
        MaterialAlertDialogBuilder(this)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                result.confirm()
                dialog.dismiss()
            }
            .setOnCancelListener {
                result.cancel()
            }
            .show()
        return true
    }

    override fun onJsConfirm(message: String, result: JsResult): Boolean {
        MaterialAlertDialogBuilder(this)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                result.confirm()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                result.cancel()
                dialog.dismiss()
            }
            .setOnCancelListener {
                result.cancel()
            }
            .show()
        return true
    }

    override fun onShowFullScreen(customView: View, callback: WebChromeClient.CustomViewCallback) {
        customVideoView = customView
        customVideoCallback = callback

        fullscreenContainer.addView(customView)
        fullscreenContainer.visibility = View.VISIBLE
        swipeRefreshLayout.visibility = View.GONE
        layoutBottomBarVisible(false)
    }

    override fun onHideFullScreen() {
        fullscreenContainer.removeAllViews()
        fullscreenContainer.visibility = View.GONE
        swipeRefreshLayout.visibility = View.VISIBLE
        layoutBottomBarVisible(true)
        customVideoView = null
        customVideoCallback = null
    }

    private fun layoutBottomBarVisible(visible: Boolean) {
        findViewById<View>(R.id.layoutBottomBar).visibility = if (visible) View.VISIBLE else View.GONE
    }

    // ==========================================
    // THREAD-SAFE LIFECYCLE & STATE RESTORATION
    // ==========================================

    override fun onResume() {
        super.onResume()
        mainWebView.onResume()
        mainWebView.resumeTimers()
    }

    override fun onPause() {
        mainWebView.pauseTimers()
        mainWebView.onPause()

        // Capture scroll state and url on pause
        val activeTab = tabManager.getActiveTab()
        activeTab.scrollX = mainWebView.scrollX
        activeTab.scrollY = mainWebView.scrollY
        activeTab.url = mainWebView.url ?: activeTab.url
        tabManager.saveToDisk()

        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // Save active tab scroll & url
        val activeTab = tabManager.getActiveTab()
        activeTab.scrollX = mainWebView.scrollX
        activeTab.scrollY = mainWebView.scrollY
        activeTab.url = mainWebView.url ?: activeTab.url

        // Save active webview back/forward list bundle
        val webState = Bundle()
        mainWebView.saveState(webState)
        outState.putBundle("webview_active_state", webState)

        // Save all tabs state metadata
        tabManager.saveToBundle(outState)
    }

    override fun onDestroy() {
        if (customVideoView != null) {
            chromeClient.onHideCustomView()
        }
        try {
            webViewContainer.removeView(mainWebView)
            mainWebView.stopLoading()
            mainWebView.clearHistory()
            mainWebView.removeAllViews()
            mainWebView.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }
}
