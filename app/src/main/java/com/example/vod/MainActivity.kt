package com.example.vod

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil // Added for efficient updates
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive

class MainActivity : AppCompatActivity() {

    // Main Views
    private lateinit var sideMenu: LinearLayout
    private lateinit var sideMenuContainer: LinearLayout
    private lateinit var sideMenuScroll: ScrollView

    // Navigation Buttons
    private lateinit var btnMenuHome: LinearLayout
    private lateinit var btnMenuWatchLater: LinearLayout
    private lateinit var btnMenuSearch: LinearLayout
    private var fetchJob: kotlinx.coroutines.Job? = null

    // Search Overlay
    private lateinit var layoutSearchOverlay: LinearLayout
    private lateinit var editSearch: EditText

    // Container Switching
    private lateinit var dashboardView: View
    private lateinit var layoutGridContainer: LinearLayout
    private lateinit var containerAlphabet: LinearLayout
    private lateinit var libraryGridView: RecyclerView

    // Hero Section Components
    private lateinit var layoutHero: View
    private lateinit var imgHeroBackdrop: ImageView
    private lateinit var txtHeroTitle: TextView
    private lateinit var txtHeroMetadata: TextView
    private lateinit var txtHeroDescription: TextView

    // List Components
    private lateinit var rvContinue: RecyclerView
    private lateinit var rvRecentMovies: RecyclerView
    private lateinit var rvRecentShows: RecyclerView
    private lateinit var lblContinue: TextView

    // State Variables
    private var currentLibId: Int = -1
    private var isLastPage = false
    private var isLoading = false
    private val videoList = mutableListOf<VideoItem>()
    private lateinit var gridAdapter: LibraryAdapter

    // --- Grid restore state ---
    private var lastGridFocusedPos: Int = RecyclerView.NO_POSITION
    private var lastGridFirstVisiblePos: Int = 0
    private var lastGridFirstVisibleOffset: Int = 0

    // --- Menu Wrap State ---
    private var firstLibraryButton: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Initialize Views
        sideMenu = findViewById(R.id.sideMenu)
        sideMenuContainer = findViewById(R.id.sideMenuContainer)
        sideMenuScroll = findViewById(R.id.sideMenuScroll)

        btnMenuHome = findViewById(R.id.btnMenuHome)
        btnMenuWatchLater = findViewById(R.id.btnMenuWatchLater)
        btnMenuSearch = findViewById(R.id.btnMenuSearch)

        layoutSearchOverlay = findViewById(R.id.layoutSearchOverlay)
        editSearch = findViewById(R.id.editSearch)

        dashboardView = findViewById(R.id.viewDashboard)

        layoutGridContainer = findViewById(R.id.layoutGridContainer)
        containerAlphabet = findViewById(R.id.containerAlphabet)
        libraryGridView = findViewById(R.id.recycler_view)

        // Hero Section
        layoutHero = findViewById(R.id.layoutHero)
        imgHeroBackdrop = findViewById(R.id.imgHeroBackdrop)
        txtHeroTitle = findViewById(R.id.txtHeroTitle)
        txtHeroMetadata = findViewById(R.id.txtHeroMetadata)
        txtHeroDescription = findViewById(R.id.txtHeroDescription)

        // Recyclers
        rvContinue = findViewById(R.id.rvContinueWatching)
        rvRecentMovies = findViewById(R.id.rvRecentMovies)
        rvRecentShows = findViewById(R.id.rvRecentShows)
        lblContinue = findViewById(R.id.lblContinue)

        // 2. Setup Adapters and Logic
        setupGridAdapter()
        setupDashboardRecyclers()
        setupSideMenuToggle()
        setupAlphabetBar()

        // Keep scroll snapshot up-to-date while scrolling
        libraryGridView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateGridScrollSnapshot()
            }
        })

        // 3. Setup Navigation Clicks
        btnMenuHome.setOnClickListener { loadDashboard() }
        btnMenuWatchLater.setOnClickListener { loadWatchLater() }
        btnMenuSearch.setOnClickListener { toggleSearch() }

        // 4. Load Initial Data
        setupMenuLogic()
        loadDashboard()
        setupSearchLogic()
    }

    private fun focusFirstItemInRow(rv: RecyclerView): Boolean {
        val count = rv.adapter?.itemCount ?: 0
        if (count <= 0 || rv.visibility != View.VISIBLE) return false

        rv.stopScroll()
        rv.scrollToPosition(0)

        rv.post {
            rv.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                ?: run {
                    rv.post {
                        rv.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                    }
                }
        }
        return true
    }

    private fun focusFirstDashboardRowItem(): Boolean {
        return focusFirstItemInRow(rvContinue)
                || focusFirstItemInRow(rvRecentMovies)
                || focusFirstItemInRow(rvRecentShows)
    }

    // --- Grid helpers: capture/restore grid state ---
    private fun updateGridScrollSnapshot() {
        val lm = libraryGridView.layoutManager as? GridLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        if (first != RecyclerView.NO_POSITION) {
            lastGridFirstVisiblePos = first
            lastGridFirstVisibleOffset = lm.findViewByPosition(first)?.top ?: 0
        }
    }

    private fun captureGridStateFromCurrentFocus() {
        updateGridScrollSnapshot()

        val focusedView = currentFocus ?: return
        val itemView = libraryGridView.findContainingItemView(focusedView) ?: return
        val pos = libraryGridView.getChildAdapterPosition(itemView)
        if (pos != RecyclerView.NO_POSITION) lastGridFocusedPos = pos
    }

    private fun restoreGridStateAndFocus() {
        libraryGridView.post {
            val lm = libraryGridView.layoutManager as? GridLayoutManager ?: return@post
            lm.scrollToPositionWithOffset(lastGridFirstVisiblePos, lastGridFirstVisibleOffset)

            libraryGridView.post {
                val target = if (lastGridFocusedPos != RecyclerView.NO_POSITION) {
                    lastGridFocusedPos
                } else {
                    lastGridFirstVisiblePos
                }
                libraryGridView.findViewHolderForAdapterPosition(target)?.itemView?.requestFocus()
                    ?: libraryGridView.requestFocus()
            }
        }
    }

    // --- Apply wrap: DPAD_DOWN on last library button goes to first library button ---
    private fun applyLibraryDownWrap() {
        if (sideMenuContainer.childCount <= 0) return

        val first = sideMenuContainer.getChildAt(0)
        val last = sideMenuContainer.getChildAt(sideMenuContainer.childCount - 1)
        firstLibraryButton = first

        if (first.id == View.NO_ID) first.id = View.generateViewId()
        if (last.id == View.NO_ID) last.id = View.generateViewId()

        btnMenuWatchLater.nextFocusDownId = first.id
        last.nextFocusDownId = first.id
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (sideMenu.isGone) {
                val focusedView = currentFocus
                val containing = focusedView?.let { libraryGridView.findContainingItemView(it) }
                if (containing != null) {
                    val position = libraryGridView.getChildAdapterPosition(containing)

                    if (position != RecyclerView.NO_POSITION && position % 6 == 0) {
                        captureGridStateFromCurrentFocus()
                        libraryGridView.suppressLayout(true)
                        libraryGridView.stopScroll()

                        sideMenu.visibility = View.VISIBLE
                        sideMenu.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

                        sideMenu.post {
                            libraryGridView.suppressLayout(false)
                            val lm = libraryGridView.layoutManager as? GridLayoutManager
                            lm?.scrollToPositionWithOffset(lastGridFirstVisiblePos, lastGridFirstVisibleOffset)

                            when (currentLibId) {
                                -1 -> btnMenuHome.requestFocus()
                                -2 -> btnMenuWatchLater.requestFocus()
                                else -> {
                                    var foundFocus = false
                                    for (i in 0 until sideMenuContainer.childCount) {
                                        val child = sideMenuContainer.getChildAt(i)
                                        if (child.tag == currentLibId) {
                                            child.requestFocus()
                                            foundFocus = true
                                            break
                                        }
                                    }
                                    if (!foundFocus) btnMenuHome.requestFocus()
                                }
                            }
                        }
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun setupSideMenuToggle() {
        val hideMenuOnRight = View.OnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                if (layoutGridContainer.isVisible) {
                    libraryGridView.suppressLayout(true)
                    sideMenu.visibility = View.GONE
                    sideMenu.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS

                    libraryGridView.post {
                        libraryGridView.suppressLayout(false)
                        restoreGridStateAndFocus()
                    }
                    return@OnKeyListener true
                }
            }
            false
        }

        btnMenuHome.setOnKeyListener(hideMenuOnRight)
        btnMenuWatchLater.setOnKeyListener(hideMenuOnRight)
        btnMenuSearch.setOnKeyListener(hideMenuOnRight)
    }

    private fun setupAlphabetBar() {
        val alphabet = listOf("#") + ('A'..'Z').map { it.toString() }
        containerAlphabet.removeAllViews()

        for (letter in alphabet) {
            val txt = TextView(this).apply {
                text = letter
                textSize = 14f
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(60.dpToPx(), 50.dpToPx())
                isFocusable = true
                isFocusableInTouchMode = true
                setBackgroundResource(R.drawable.sel_menu_item_pill)
                setOnClickListener { jumpToLetter(letter) }

                setOnFocusChangeListener { _, hasFocus ->
                    setTextColor(if (hasFocus) Color.BLACK else Color.GRAY)
                    if (hasFocus) sideMenu.visibility = View.GONE
                }

                setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        libraryGridView.requestFocus()
                        return@setOnKeyListener true
                    }
                    false
                }
            }
            containerAlphabet.addView(txt)
        }
    }

    private fun jumpToLetter(letter: String) {
        if (videoList.isEmpty()) return

        val index = if (letter == "#") {
            0
        } else {
            videoList.indexOfFirst {
                (it.seriesTitle ?: it.title).uppercase().startsWith(letter)
            }
        }

        if (index != -1) {
            libraryGridView.stopScroll()
            (libraryGridView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(index, 0)
            libraryGridView.post {
                val viewHolder = libraryGridView.findViewHolderForAdapterPosition(index)
                viewHolder?.itemView?.requestFocus()
            }
        }
    }

    private fun toggleSearch() {
        if (layoutSearchOverlay.isVisible) {
            layoutSearchOverlay.visibility = View.GONE
            dashboardView.requestFocus()
        } else {
            layoutSearchOverlay.isVisible = true
            sideMenu.visibility = View.VISIBLE
            sideMenu.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            editSearch.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editSearch, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun loadWatchLater() {
        currentLibId = -2
        dashboardView.visibility = View.GONE
        layoutGridContainer.visibility = View.GONE
        layoutSearchOverlay.visibility = View.GONE
        sideMenu.visibility = View.VISIBLE
        sideMenu.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        highlightMenu(btnMenuWatchLater)
        loadFragment(WatchLaterFragment())
    }

    private fun setupGridAdapter() {
        val layoutManager = GridLayoutManager(this, 6)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (gridAdapter.getItemViewType(position) == LibraryAdapter.VIEW_TYPE_LOADING) 6 else 1
            }
        }

        libraryGridView.layoutManager = layoutManager
        gridAdapter = LibraryAdapter(videoList, isHorizontal = false) { video -> openDetails(video) }
        libraryGridView.adapter = gridAdapter
    }

    private fun setupDashboardRecyclers() {
        rvContinue.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvRecentMovies.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvRecentShows.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    }

    private fun openDetails(video: VideoItem) {
        val intent = Intent(this, DetailsActivity::class.java)
        intent.putExtra("VIDEO_ID", video.id)
        intent.putExtra("RESUME_TIME", video.resume_time)
        startActivity(intent)
    }

    private fun setupSearchLogic() {
        editSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                val query = editSearch.text.toString().trim()
                if (query.isNotEmpty()) {
                    performSearch(query)
                }

                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(editSearch.windowToken, 0)
                layoutSearchOverlay.visibility = View.GONE
                return@setOnEditorActionListener true
            }
            false
        }
    }

    private fun performSearch(query: String) {
        currentLibId = -999
        highlightMenu(null)

        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (fragment != null) {
            supportFragmentManager.beginTransaction().remove(fragment).commit()
        }

        dashboardView.visibility = View.GONE
        layoutGridContainer.visibility = View.VISIBLE
        sideMenu.visibility = View.VISIBLE
        sideMenu.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

        isLoading = true

        // Efficient clearing not required immediately if using DiffUtil,
        // but visuals might look cleaner if we show loading state
        val previousSize = videoList.size
        videoList.clear()
        gridAdapter.notifyItemRangeRemoved(0, previousSize)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = NetworkClient.api.search(query)
                withContext(Dispatchers.Main) {
                    if (response.status == "success") {
                        val newData = response.data ?: emptyList()

                        if (newData.isEmpty()) {
                            Toast.makeText(this@MainActivity, "No results found", Toast.LENGTH_SHORT).show()
                        } else {
                            // OPTIMIZED: Use DiffUtil for results
                            gridAdapter.updateData(newData)
                            libraryGridView.requestFocus()
                        }
                    }
                    isLoading = false
                    isLastPage = true
                }
            } catch (e: Exception) {
                isLoading = false
                e.printStackTrace()
            }
        }
    }

    private fun setupMenuLogic() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = NetworkClient.api.getLibraries()
                withContext(Dispatchers.Main) {
                    if (response.status == "success") {
                        sideMenuContainer.removeAllViews()
                        firstLibraryButton = null

                        response.data?.forEach { lib ->
                            if (lib.id != 0) {
                                addButtonToSidebar(lib.name, lib.id)
                            }
                        }
                        applyLibraryDownWrap()
                        if (currentLibId == -1) highlightMenu(btnMenuHome)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun addButtonToSidebar(title: String, id: Int) {
        val btnLayout = LinearLayout(this@MainActivity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 8) }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10, 10, 10, 10)
            setBackgroundResource(R.drawable.sel_menu_item_pill)
            isFocusable = true
            isFocusableInTouchMode = true
            tag = id
            if (this.id == View.NO_ID) this.id = View.generateViewId()
        }

        val textView = TextView(this@MainActivity).apply {
            text = title
            textSize = 12f
            setTextColor(ContextCompat.getColorStateList(context, R.color.sel_menu_text_color))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isDuplicateParentStateEnabled = true
        }

        btnLayout.addView(textView)

        btnLayout.setOnClickListener {
            if (currentLibId == id) {
                libraryGridView.suppressLayout(true)
                sideMenu.visibility = View.GONE
                sideMenu.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS

                libraryGridView.post {
                    libraryGridView.suppressLayout(false)
                    restoreGridStateAndFocus()
                }
            } else {
                fetchLibrary(id, title)
                highlightMenu(btnLayout)
            }
        }

        btnLayout.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false

            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                val isLast = sideMenuContainer.indexOfChild(btnLayout) == sideMenuContainer.childCount - 1
                if (isLast) {
                    sideMenuScroll.smoothScrollTo(0, 0)
                    firstLibraryButton?.requestFocus()
                    return@setOnKeyListener true
                }
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (layoutGridContainer.isVisible) {
                    libraryGridView.suppressLayout(true)
                    sideMenu.visibility = View.GONE
                    sideMenu.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS

                    libraryGridView.post {
                        libraryGridView.suppressLayout(false)
                        restoreGridStateAndFocus()
                    }
                    return@setOnKeyListener true
                }
            }
            false
        }

        sideMenuContainer.addView(btnLayout)
        if (firstLibraryButton == null) firstLibraryButton = btnLayout
    }

    private fun highlightMenu(selectedView: View?) {
        btnMenuHome.isSelected = (selectedView == btnMenuHome)
        btnMenuWatchLater.isSelected = (selectedView == btnMenuWatchLater)
        btnMenuSearch.isSelected = false
        for (view in sideMenuContainer.children) {
            view.isSelected = (view == selectedView)
        }
    }

    private fun updateHeroSection(video: VideoItem) {
        txtHeroTitle.text = if (!video.seriesTitle.isNullOrEmpty()) video.seriesTitle else video.title
        txtHeroDescription.text = video.plot
        val metaBuilder = StringBuilder()
        if (video.year > 0) metaBuilder.append("${video.year} • ")
        metaBuilder.append(video.quality ?: "HD")
        txtHeroMetadata.text = metaBuilder.toString()
        val backdropUrl = video.getBackdropImage()
        imgHeroBackdrop.load(backdropUrl) { crossfade(true) }

        layoutHero.setOnClickListener { openDetails(video) }
        layoutHero.isFocusable = true
        layoutHero.isFocusableInTouchMode = true
        layoutHero.setOnFocusChangeListener { v, hasFocus ->
            v.animate()
                .scaleX(if (hasFocus) 1.01f else 1.0f)
                .scaleY(if (hasFocus) 1.01f else 1.0f)
                .setDuration(120).start()
            v.elevation = if (hasFocus) 10f else 0f
        }
        layoutHero.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (focusFirstDashboardRowItem()) return@setOnKeyListener true
            }
            false
        }
    }

    private fun loadDashboard() {
        currentLibId = -1
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (fragment != null) {
            supportFragmentManager.beginTransaction().remove(fragment).commit()
        }
        highlightMenu(btnMenuHome)

        dashboardView.visibility = View.VISIBLE
        layoutGridContainer.visibility = View.GONE
        layoutSearchOverlay.visibility = View.GONE
        sideMenu.visibility = View.VISIBLE
        sideMenu.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

        isLoading = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = NetworkClient.api.getDashboard()
                withContext(Dispatchers.Main) {
                    if (response.status == "success") {
                        val heroItem = response.recentMovies?.randomOrNull()
                            ?: response.continueWatching?.randomOrNull()

                        if (heroItem != null) {
                            updateHeroSection(heroItem)
                            layoutHero.visibility = View.VISIBLE
                        } else {
                            layoutHero.visibility = View.GONE
                        }

                        if (!response.continueWatching.isNullOrEmpty()) {
                            rvContinue.visibility = View.VISIBLE
                            lblContinue.visibility = View.VISIBLE
                            rvContinue.adapter = LibraryAdapter(
                                response.continueWatching.toMutableList(),
                                isHorizontal = true
                            ) { openDetails(it) }
                        } else {
                            rvContinue.visibility = View.GONE
                            lblContinue.visibility = View.GONE
                        }

                        rvRecentMovies.adapter = LibraryAdapter(
                            response.recentMovies?.toMutableList() ?: mutableListOf(),
                            isHorizontal = true
                        ) { openDetails(it) }

                        rvRecentShows.adapter = LibraryAdapter(
                            response.recentShows?.toMutableList() ?: mutableListOf(),
                            isHorizontal = true
                        ) { openDetails(it) }
                    }
                    isLoading = false
                }
            } catch (e: Exception) {
                isLoading = false
                e.printStackTrace()
            }
        }
    }

    private fun fetchLibrary(libId: Int, title: String) {
        fetchJob?.cancel()
        currentLibId = libId

        dashboardView.isGone = true
        layoutGridContainer.isVisible = true
        layoutSearchOverlay.isGone = true
        sideMenu.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

        // Clear existing data visual reset
        val previousSize = videoList.size
        videoList.clear()
        gridAdapter.notifyItemRangeRemoved(0, previousSize)
        gridAdapter.setLoadingState(true)

        isLoading = true

        fetchJob = lifecycleScope.launch {
            var pageToLoad = 1
            var hasMorePages = true

            try {
                while (hasMorePages && isActive) {
                    val response = withContext(Dispatchers.IO) {
                        NetworkClient.api.getLibrary(libId, pageToLoad)
                    }

                    if (response.status == "success") {
                        val newData = response.data ?: emptyList()

                        if (newData.isEmpty()) {
                            hasMorePages = false
                        } else {
                            val sortedData = newData.sortedBy { it.title }

                            if (pageToLoad == 1) {
                                gridAdapter.setLoadingState(false)

                                // OPTIMIZED: Use DiffUtil for the initial load of the library
                                gridAdapter.updateData(sortedData)

                                libraryGridView.scrollToPosition(0)
                                lastGridFocusedPos = RecyclerView.NO_POSITION
                                lastGridFirstVisiblePos = 0
                                lastGridFirstVisibleOffset = 0
                            } else {
                                // Keep efficient range insertion for pagination
                                val startPos = videoList.size
                                videoList.addAll(sortedData)
                                gridAdapter.notifyItemRangeInserted(startPos, sortedData.size)
                            }
                            pageToLoad++
                        }
                    } else {
                        hasMorePages = false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error loading library", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isLoading = false
                gridAdapter.setLoadingState(false)
            }
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (layoutSearchOverlay.isVisible) {
            layoutSearchOverlay.visibility = View.GONE
            return
        }

        if (sideMenu.isGone) {
            captureGridStateFromCurrentFocus()
            libraryGridView.suppressLayout(true)
            sideMenu.visibility = View.VISIBLE
            sideMenu.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

            sideMenu.post {
                libraryGridView.suppressLayout(false)
                val lm = libraryGridView.layoutManager as? GridLayoutManager
                lm?.scrollToPositionWithOffset(lastGridFirstVisiblePos, lastGridFirstVisibleOffset)

                var foundFocus = false
                for (i in 0 until sideMenuContainer.childCount) {
                    val child = sideMenuContainer.getChildAt(i)
                    if (child.tag == currentLibId) {
                        child.requestFocus()
                        foundFocus = true
                        break
                    }
                }
                if (!foundFocus) btnMenuHome.requestFocus()
            }
            return
        }

        if (currentLibId != -1) {
            loadDashboard()
        } else {
            super.onBackPressed()
        }
    }
}
class LibraryAdapter(
    private val videos: MutableList<VideoItem>,
    private val isHorizontal: Boolean = false,
    private val onItemClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var isLoadingFooter = false

    companion object {
        const val VIEW_TYPE_ITEM = 0
        const val VIEW_TYPE_LOADING = 1
    }

    // --- DIFF CALLBACK CLASS ---
    class VideoDiffCallback(
        private val oldList: List<VideoItem>,
        private val newList: List<VideoItem>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val old = oldList[oldItemPosition]
            val new = newList[newItemPosition]
            return old.title == new.title &&
                    old.rating == new.rating &&
                    old.resume_time == new.resume_time
        }
    }

    // --- OPTIMIZED UPDATE METHOD ---
    fun updateData(newVideos: List<VideoItem>) {
        val diffCallback = VideoDiffCallback(this.videos, newVideos)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        this.videos.clear()
        this.videos.addAll(newVideos)

        // Dispatch specific updates automatically
        diffResult.dispatchUpdatesTo(this)

        isLoadingFooter = false
    }

    fun setLoadingState(loading: Boolean) {
        if (isLoadingFooter == loading) return
        isLoadingFooter = loading
        if (loading) {
            notifyItemInserted(videos.size)
        } else {
            notifyItemRemoved(videos.size)
        }
    }

    override fun getItemCount(): Int {
        return if (isLoadingFooter) videos.size + 1 else videos.size
    }

    override fun getItemViewType(position: Int): Int {
        return if (isLoadingFooter && position == videos.size) VIEW_TYPE_LOADING else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == VIEW_TYPE_LOADING) {
            val progressBar = ProgressBar(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    150
                )
                isIndeterminate = true
                indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
                isFocusable = false
                isFocusableInTouchMode = false
            }
            return LoadingViewHolder(progressBar)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video_card, parent, false)
            if (isHorizontal) {
                val params = view.layoutParams
                params.width = (120 * parent.context.resources.displayMetrics.density).toInt()
                view.layoutParams = params
            } else {
                val params = view.layoutParams
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                view.layoutParams = params
            }
            return VideoViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is VideoViewHolder) {
            val video = videos[position]
            holder.bind(video, onItemClick)
        }
    }

    class LoadingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgPoster: ImageView = itemView.findViewById(R.id.imgPoster)
        private val txtTitle: TextView = itemView.findViewById(R.id.txtTitle)
        private val txtYear: TextView = itemView.findViewById(R.id.txtYear)
        private val txtQuality: TextView = itemView.findViewById(R.id.txtQualityBadge)
        private val txtRating: TextView = itemView.findViewById(R.id.txtRatingBadge)
        private val txtRuntime: TextView = itemView.findViewById(R.id.txtRuntime)
        private val playbackProgress: ProgressBar = itemView.findViewById(R.id.playbackProgress)

        fun bind(video: VideoItem, onClick: (VideoItem) -> Unit) {
            txtTitle.text = if (!video.seriesTitle.isNullOrEmpty()) video.seriesTitle else video.title
            txtYear.text = video.year.toString()
            txtQuality.text = video.quality ?: "HD"
            txtRating.text = "★ ${video.rating ?: "5.0"}"

            if (video.resume_time > 0 && video.total_duration > 0) {
                val progressPercent = (video.resume_time.toDouble() / video.total_duration.toDouble() * 100).toInt()
                playbackProgress.visibility = View.VISIBLE
                playbackProgress.progress = progressPercent
            } else {
                playbackProgress.visibility = View.GONE
            }

            if (video.runtime > 0) {
                txtRuntime.visibility = View.VISIBLE
                val hours = video.runtime / 60
                val mins = video.runtime % 60
                txtRuntime.text = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
            } else {
                txtRuntime.visibility = View.GONE
            }

            imgPoster.load(video.getDisplayImage()) { crossfade(true) }

            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = true
            itemView.setOnClickListener { onClick(video) }

            itemView.setOnKeyListener { v, keyCode, event ->
                if (!isHorizontal) return@setOnKeyListener false
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false

                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    val pos = bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@setOnKeyListener false

                    val rv = v.parent as? RecyclerView ?: return@setOnKeyListener false
                    val last = (rv.adapter?.itemCount ?: 0) - 1

                    if (last >= 0 && pos == last) {
                        rv.stopScroll()
                        rv.scrollToPosition(0)
                        rv.post {
                            rv.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                        }
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }
    }
}