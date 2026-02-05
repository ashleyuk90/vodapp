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
import android.widget.ImageButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.util.TypedValue
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.vod.utils.Constants
import com.example.vod.utils.ErrorHandler
import com.example.vod.utils.NetworkUtils
import com.example.vod.utils.AnimationHelper
import com.example.vod.utils.AccessibilityUtils
import com.example.vod.utils.OrientationUtils
import com.example.vod.utils.RatingUtils
import com.example.vod.utils.ResponsiveUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.io.IOException
import java.lang.ref.WeakReference
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class MainActivity : AppCompatActivity() {

    // Main Views
    private lateinit var sideMenu: LinearLayout
    private lateinit var sideMenuContainer: LinearLayout
    private lateinit var sideMenuScroll: ScrollView

    // Navigation Buttons
    private lateinit var btnMenuHome: LinearLayout
    private lateinit var btnMenuWatchLater: LinearLayout
    private lateinit var btnMenuSearch: LinearLayout
    private lateinit var sideMenuHeader: View
    private lateinit var btnMenuToggle: ImageButton
    private lateinit var txtMenuSearch: TextView
    private lateinit var txtMenuHome: TextView
    private lateinit var txtMenuWatchLater: TextView
    private lateinit var txtLibraryLabel: TextView
    private lateinit var btnMenuProfile: LinearLayout
    private lateinit var txtProfileName: TextView
    private lateinit var txtProfileInitial: TextView
    private lateinit var viewProfileAvatar: View
    private var fetchJob: kotlinx.coroutines.Job? = null

    // Search Overlay
    private lateinit var layoutSearchOverlay: LinearLayout
    private lateinit var editSearch: EditText
    
    // Search Filters
    private lateinit var chipGroupType: ChipGroup
    private lateinit var chipGroupGenre: ChipGroup
    private lateinit var chipMovies: Chip
    private lateinit var chipSeries: Chip
    private lateinit var chipClearFilters: Chip
    private var currentFilters = SearchFilters()

    // Container Switching
    private lateinit var dashboardView: View
    private lateinit var layoutGridContainer: LinearLayout
    private lateinit var libraryContainer: View
    private lateinit var scrollAlphabet: ScrollView
    private lateinit var containerAlphabet: LinearLayout
    private lateinit var libraryGridView: RecyclerView

    // Hero Section Components
    private lateinit var layoutHero: View
    private lateinit var imgHeroBackdrop: ImageView
    private lateinit var txtHeroTitle: TextView
    private lateinit var txtHeroMetadata: TextView
    private lateinit var txtHeroDescription: TextView
    private var btnHeroPlay: ImageButton? = null
    private var currentHeroVideo: VideoItem? = null

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
    private var isSideMenuCollapsed = false
    private var isPhone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OrientationUtils.applyPreferredOrientation(this)
        setContentView(R.layout.activity_main)
        isPhone = ResponsiveUtils.getScreenSize(this) == ResponsiveUtils.ScreenSize.PHONE

        // Initialize ProfileManager
        ProfileManager.init(this)

        // 1. Initialize Views
        sideMenu = findViewById(R.id.sideMenu)
        sideMenuContainer = findViewById(R.id.sideMenuContainer)
        sideMenuScroll = findViewById(R.id.sideMenuScroll)

        btnMenuHome = findViewById(R.id.btnMenuHome)
        btnMenuWatchLater = findViewById(R.id.btnMenuWatchLater)
        btnMenuSearch = findViewById(R.id.btnMenuSearch)
        sideMenuHeader = findViewById(R.id.sideMenuHeader)
        btnMenuToggle = findViewById(R.id.btnMenuToggle)
        txtMenuSearch = findViewById(R.id.txtMenuSearch)
        txtMenuHome = findViewById(R.id.txtMenuHome)
        txtMenuWatchLater = findViewById(R.id.txtMenuWatchLater)
        txtLibraryLabel = findViewById(R.id.txtLibraryLabel)
        
        // Profile button
        btnMenuProfile = findViewById(R.id.btnMenuProfile)
        txtProfileName = findViewById(R.id.txtProfileName)
        txtProfileInitial = findViewById(R.id.txtProfileInitial)
        viewProfileAvatar = findViewById(R.id.viewProfileAvatar)

        layoutSearchOverlay = findViewById(R.id.layoutSearchOverlay)
        editSearch = findViewById(R.id.editSearch)
        
        // Initialize filter chips
        chipGroupType = findViewById(R.id.chipGroupType)
        chipGroupGenre = findViewById(R.id.chipGroupGenre)
        chipMovies = findViewById(R.id.chipMovies)
        chipSeries = findViewById(R.id.chipSeries)
        chipClearFilters = findViewById(R.id.chipClearFilters)
        
        setupFilterChips()

        dashboardView = findViewById(R.id.viewDashboard)

        layoutGridContainer = findViewById(R.id.layoutGridContainer)
        libraryContainer = findViewById(R.id.libraryContainer)
        scrollAlphabet = findViewById(R.id.scrollAlphabet)
        containerAlphabet = findViewById(R.id.containerAlphabet)
        libraryGridView = findViewById(R.id.recycler_view)

        // Hero Section
        layoutHero = findViewById(R.id.layoutHero)
        imgHeroBackdrop = findViewById(R.id.imgHeroBackdrop)
        txtHeroTitle = findViewById(R.id.txtHeroTitle)
        txtHeroMetadata = findViewById(R.id.txtHeroMetadata)
        txtHeroDescription = findViewById(R.id.txtHeroDescription)
        btnHeroPlay = findViewById(R.id.btnHeroPlay)

        // Recyclers
        rvContinue = findViewById(R.id.rvContinueWatching)
        rvRecentMovies = findViewById(R.id.rvRecentMovies)
        rvRecentShows = findViewById(R.id.rvRecentShows)
        lblContinue = findViewById(R.id.lblContinue)

        // 2. Setup Adapters and Logic
        setupGridAdapter()
        setupDashboardRecyclers()
        setupPhoneSideMenu()
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
        btnMenuProfile.setOnClickListener { switchProfile() }
        btnMenuProfile.setOnLongClickListener {
            openPlaybackPreferences()
            true
        }
        
        // Update profile display
        updateProfileDisplay()

        // 4. Load Initial Data
        setupMenuLogic()
        loadDashboard()
        setupSearchLogic()
        setupBackPressedHandler()
    }

    /**
     * Modern back press handling using OnBackPressedDispatcher.
     * This replaces the deprecated onBackPressed() method.
     */
    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    layoutSearchOverlay.isVisible -> {
                        layoutSearchOverlay.visibility = View.GONE
                    }
                    sideMenu.isGone -> {
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
                    }
                    currentLibId != -1 -> {
                        loadDashboard()
                    }
                    else -> {
                        // Allow default back behavior (finish activity)
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
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

    // --- Apply navigation: DPAD_DOWN on last library button goes to profile button ---
    private fun applyLibraryDownWrap() {
        if (sideMenuContainer.childCount <= 0) {
            // No library items - profile navigates up to Watch Later
            btnMenuProfile.nextFocusUpId = R.id.btnMenuWatchLater
            return
        }

        val first = sideMenuContainer.getChildAt(0)
        val last = sideMenuContainer.getChildAt(sideMenuContainer.childCount - 1)
        firstLibraryButton = first

        if (first.id == View.NO_ID) first.id = View.generateViewId()
        if (last.id == View.NO_ID) last.id = View.generateViewId()

        btnMenuWatchLater.nextFocusDownId = first.id
        // Navigate to profile button at bottom instead of wrapping
        last.nextFocusDownId = R.id.btnMenuProfile
        btnMenuProfile.nextFocusUpId = last.id
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (sideMenu.isGone) {
                val focusedView = currentFocus
                val containing = focusedView?.let { libraryGridView.findContainingItemView(it) }
                if (containing != null) {
                    val position = libraryGridView.getChildAdapterPosition(containing)
                    val spanCount = ResponsiveUtils.getGridSpanCountFromResources(this)

                    if (position != RecyclerView.NO_POSITION && position % spanCount == 0) {
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
        if (isPhone) {
            return
        }

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
        
        // Search button key listener - UP wraps to Profile, RIGHT hides menu
        btnMenuSearch.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    // Wrap to profile button at bottom
                    btnMenuProfile.requestFocus()
                    return@setOnKeyListener true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
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
            }
            false
        }
        
        // Profile button key listener with up navigation to last library item
        btnMenuProfile.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    // Go to last library item, or Watch Later if no libraries
                    if (sideMenuContainer.childCount > 0) {
                        val lastLib = sideMenuContainer.getChildAt(sideMenuContainer.childCount - 1)
                        lastLib.requestFocus()
                    } else {
                        btnMenuWatchLater.requestFocus()
                    }
                    return@setOnKeyListener true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    // Same behavior as other menu items - hide menu and focus grid
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
            }
            false
        }
    }

    private fun setupAlphabetBar() {
        if (ResponsiveUtils.getScreenSize(this) == ResponsiveUtils.ScreenSize.PHONE) {
            scrollAlphabet.visibility = View.GONE
            containerAlphabet.removeAllViews()
            return
        }

        scrollAlphabet.visibility = View.VISIBLE
        val alphabet = listOf("#") + ('A'..'Z').map { it.toString() }
        containerAlphabet.removeAllViews()

        // Get dimensions from resources for screen-size-appropriate sizing
        val textSize = resources.getDimension(R.dimen.alphabet_text_size)
        val paddingV = resources.getDimensionPixelSize(R.dimen.alphabet_letter_padding_vertical)
        val paddingH = resources.getDimensionPixelSize(R.dimen.alphabet_letter_padding_horizontal)
        val minWidth = resources.getDimensionPixelSize(R.dimen.alphabet_letter_min_width)

        for (letter in alphabet) {
            val txt = TextView(this).apply {
                text = letter
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSize)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 2.dpToPx()
                }
                minimumWidth = minWidth
                setPadding(paddingH, paddingV, paddingH, paddingV)
                isFocusable = true
                isFocusableInTouchMode = true
                setBackgroundResource(R.drawable.focus_alphabet_letter)
                setOnClickListener { jumpToLetter(letter) }

                setOnFocusChangeListener { _, hasFocus ->
                    setTextColor(if (hasFocus) Color.BLACK else Color.WHITE)
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
        // Use resource-based span count for responsive layout
        val spanCount = ResponsiveUtils.getGridSpanCountFromResources(this)
        val layoutManager = GridLayoutManager(this, spanCount)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (gridAdapter.getItemViewType(position) == LibraryAdapter.VIEW_TYPE_LOADING) spanCount else 1
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
        AnimationHelper.applyOpenTransition(this)
    }

    private fun setupSearchLogic() {
        editSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                val query = editSearch.text.toString().trim()
                if (query.isNotEmpty()) {
                    performSearch(query, currentFilters)
                }

                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(editSearch.windowToken, 0)
                layoutSearchOverlay.visibility = View.GONE
                return@setOnEditorActionListener true
            }
            false
        }
    }
    
    private fun setupFilterChips() {
        // Type filter chips (including "All")
        chipGroupType.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilters = when {
                checkedIds.contains(R.id.chipAll) -> currentFilters.copy(type = null) // "All" clears type filter
                checkedIds.contains(R.id.chipMovies) -> currentFilters.copy(type = "movie")
                checkedIds.contains(R.id.chipSeries) -> currentFilters.copy(type = "series")
                else -> currentFilters.copy(type = null)
            }
            updateClearFiltersVisibility()
        }
        
        // Genre filter chips
        chipGroupGenre.setOnCheckedStateChangeListener { _, checkedIds ->
            val genre = when {
                checkedIds.contains(R.id.chipAction) -> "Action"
                checkedIds.contains(R.id.chipComedy) -> "Comedy"
                checkedIds.contains(R.id.chipDrama) -> "Drama"
                checkedIds.contains(R.id.chipHorror) -> "Horror"
                checkedIds.contains(R.id.chipSciFi) -> "Science Fiction"
                checkedIds.contains(R.id.chipAnimation) -> "Animation"
                else -> null
            }
            currentFilters = currentFilters.copy(genre = genre)
            updateClearFiltersVisibility()
        }
        
        // Clear filters button
        chipClearFilters.setOnClickListener {
            clearAllFilters()
        }
    }
    
    private fun updateClearFiltersVisibility() {
        // Show clear button only if genre filter is active (type "All" is ok)
        val hasGenreFilter = currentFilters.genre != null
        val hasTypeFilter = currentFilters.type != null
        chipClearFilters.visibility = if (hasGenreFilter || hasTypeFilter) View.VISIBLE else View.GONE
    }
    
    private fun clearAllFilters() {
        // Select "All" chip instead of clearing completely
        findViewById<com.google.android.material.chip.Chip>(R.id.chipAll)?.isChecked = true
        chipGroupGenre.clearCheck()
        currentFilters = SearchFilters()
        chipClearFilters.visibility = View.GONE
    }

    private fun performSearch(query: String, filters: SearchFilters = SearchFilters()) {
        // Check network before search
        if (!NetworkUtils.isNetworkAvailable(this)) {
            ErrorHandler.showError(this, "No internet connection")
            return
        }

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

        val previousSize = videoList.size
        videoList.clear()
        gridAdapter.notifyItemRangeRemoved(0, previousSize)

        // Use WeakReference to prevent activity leak
        val weakActivity = WeakReference(this)

        // Use lifecycleScope for automatic cancellation on activity destruction
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val profileId = ProfileManager.getActiveProfileId()
                val response = NetworkClient.api.search(
                    query = query,
                    profileId = profileId,
                    genre = filters.genre,
                    yearMin = filters.yearMin,
                    yearMax = filters.yearMax,
                    minRating = filters.minRating,
                    type = filters.type
                )
                withContext(Dispatchers.Main) {
                    val activity = weakActivity.get() ?: return@withContext
                    
                    if (response.status == "success") {
                        val newData = response.data ?: emptyList()

                        if (newData.isEmpty()) {
                            ErrorHandler.showError(activity, "No results found")
                        } else {
                            activity.gridAdapter.updateData(newData)
                            activity.libraryGridView.requestFocus()
                            
                            // Show filter info if filters are active
                            if (filters.hasActiveFilters()) {
                                Toast.makeText(
                                    activity,
                                    activity.getString(R.string.search_results_filtered, newData.size),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    activity.isLoading = false
                    activity.isLastPage = true
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    weakActivity.get()?.let { activity ->
                        ErrorHandler.handleNetworkError(activity, e)
                        activity.isLoading = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    weakActivity.get()?.let { activity ->
                        ErrorHandler.handleNetworkError(activity, e, "Search failed")
                        activity.isLoading = false
                    }
                }
            }
        }
    }

    private fun setupMenuLogic() {
        // Check network before loading libraries
        if (!NetworkUtils.isNetworkAvailable(this)) {
            ErrorHandler.showError(this, "No internet connection")
            return
        }

        // Use WeakReference to prevent activity leak
        val weakActivity = WeakReference(this)

        // Use lifecycleScope for automatic cancellation on activity destruction
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = NetworkClient.api.getLibraries()
                withContext(Dispatchers.Main) {
                    val activity = weakActivity.get() ?: return@withContext
                    
                    if (response.status == "success") {
                        activity.sideMenuContainer.removeAllViews()
                        activity.firstLibraryButton = null

                        response.data?.forEach { lib ->
                            if (lib.id != 0) {
                                activity.addButtonToSidebar(lib.name, lib.id)
                            }
                        }
                        activity.applyLibraryDownWrap()
                        if (activity.currentLibId == -1) activity.highlightMenu(activity.btnMenuHome)
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    weakActivity.get()?.let { activity ->
                        ErrorHandler.handleNetworkError(activity, e, "Failed to load libraries")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    weakActivity.get()?.let { activity ->
                        ErrorHandler.handleNetworkError(activity, e, "Error loading libraries")
                    }
                }
            }
        }
    }

    private fun addButtonToSidebar(title: String, id: Int) {
        val paddingH = resources.getDimensionPixelSize(R.dimen.side_menu_item_padding_horizontal)
        val paddingV = resources.getDimensionPixelSize(R.dimen.side_menu_item_padding_vertical)
        val marginV = resources.getDimensionPixelSize(R.dimen.side_menu_item_margin_vertical)
        
        val btnLayout = LinearLayout(this@MainActivity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, marginV, 0, marginV) }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(paddingH, paddingV, paddingH, paddingV)
            setBackgroundResource(R.drawable.sel_menu_item_pill)
            isFocusable = true
            isFocusableInTouchMode = true
            tag = id
            if (this.id == View.NO_ID) this.id = View.generateViewId()
        }

        val textView = TextView(this@MainActivity).apply {
            text = title
            setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.side_menu_text_size)
            )
            setTextColor(ContextCompat.getColorStateList(context, R.color.sel_menu_text_color))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isDuplicateParentStateEnabled = true
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            visibility = if (isPhone && isSideMenuCollapsed) View.GONE else View.VISIBLE
        }

        btnLayout.addView(textView)

        btnLayout.setOnClickListener {
            if (currentLibId == id) {
                if (!isPhone) {
                    libraryGridView.suppressLayout(true)
                    sideMenu.visibility = View.GONE
                    sideMenu.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS

                    libraryGridView.post {
                        libraryGridView.suppressLayout(false)
                        restoreGridStateAndFocus()
                    }
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
                    // Navigate to profile button instead of wrapping
                    btnMenuProfile.requestFocus()
                    return@setOnKeyListener true
                }
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (!isPhone && layoutGridContainer.isVisible) {
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

    private fun setupPhoneSideMenu() {
        if (!isPhone) {
            btnMenuToggle.visibility = View.GONE
            sideMenuHeader.visibility = View.GONE
            return
        }

        sideMenuHeader.visibility = View.VISIBLE
        btnMenuToggle.visibility = View.VISIBLE
        btnMenuToggle.setOnClickListener {
            setSideMenuCollapsed(!isSideMenuCollapsed)
        }
        setSideMenuCollapsed(isSideMenuCollapsed)
    }

    private fun setSideMenuCollapsed(collapsed: Boolean) {
        if (!isPhone) return

        isSideMenuCollapsed = collapsed
        val paddingV = resources.getDimensionPixelSize(R.dimen.side_menu_item_padding_vertical)
        val paddingH = resources.getDimensionPixelSize(
            if (collapsed) R.dimen.side_menu_item_padding_horizontal_collapsed
            else R.dimen.side_menu_item_padding_horizontal
        )
        val menuWidth = resources.getDimensionPixelSize(
            if (collapsed) R.dimen.side_menu_width_collapsed else R.dimen.side_menu_width
        )
        sideMenu.layoutParams = sideMenu.layoutParams.apply { width = menuWidth }
        sideMenu.requestLayout()

        val textVisibility = if (collapsed) View.GONE else View.VISIBLE
        txtMenuSearch.visibility = textVisibility
        txtMenuHome.visibility = textVisibility
        txtMenuWatchLater.visibility = textVisibility
        txtLibraryLabel.visibility = textVisibility
        txtProfileName.visibility = textVisibility
        libraryContainer.visibility = textVisibility

        btnMenuSearch.setPadding(paddingH, paddingV, paddingH, paddingV)
        btnMenuHome.setPadding(paddingH, paddingV, paddingH, paddingV)
        btnMenuWatchLater.setPadding(paddingH, paddingV, paddingH, paddingV)
        btnMenuProfile.setPadding(paddingH, paddingV, paddingH, paddingV)

        // Hide/show dynamic library item labels
        for (view in sideMenuContainer.children) {
            val textView = (view as? ViewGroup)?.getChildAt(0)
            textView?.visibility = textVisibility
            view.setPadding(paddingH, paddingV, paddingH, paddingV)
        }

        btnMenuToggle.rotation = if (collapsed) 180f else 0f
        btnMenuToggle.contentDescription = getString(
            if (collapsed) R.string.menu_expand else R.string.menu_collapse
        )
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
        currentHeroVideo = video
        txtHeroTitle.text = if (!video.seriesTitle.isNullOrEmpty()) video.seriesTitle else video.title
        txtHeroDescription.text = video.plot
        val metaBuilder = StringBuilder()
        if (video.year > 0) metaBuilder.append("${video.year} • ")
        metaBuilder.append(video.quality ?: "HD")
        if (!video.rating.isNullOrEmpty()) metaBuilder.append(" • ${video.rating}")
        txtHeroMetadata.text = metaBuilder.toString()
        val backdropUrl = video.getBackdropImage()
        imgHeroBackdrop.load(backdropUrl) { 
            crossfade(true)
            error(R.drawable.ic_movie)
        }

        // Hero content click opens details
        layoutHero.setOnClickListener { openDetails(video) }
        
        // Hero Play button - go directly to player
        btnHeroPlay?.setOnClickListener {
            val button = btnHeroPlay ?: return@setOnClickListener
            AnimationHelper.runWithPressEffect(button) {
                val intent = Intent(this, PlayerActivity::class.java)
                intent.putExtra("VIDEO_ID", video.id)
                intent.putExtra("RESUME_TIME", video.resume_time)
                startActivity(intent)
                AnimationHelper.applyOpenTransition(this)
            }
        }
        
        // Focus handling for hero play button
        btnHeroPlay?.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                AnimationHelper.scaleUp(v, Constants.FOCUS_SCALE_MEDIUM)
            } else {
                AnimationHelper.scaleDown(v)
            }
        }
        
        // Allow D-pad navigation from hero play button
        btnHeroPlay?.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (focusFirstDashboardRowItem()) return@setOnKeyListener true
            }
            false
        }
        
        // Set accessibility description
        layoutHero.contentDescription = getString(R.string.a11y_hero_section, video.title)
    }

    /**
     * Update the profile button display with current profile info.
     */
    private fun updateProfileDisplay() {
        val profile = ProfileManager.getActiveProfile()
        if (profile != null) {
            txtProfileName.text = profile.name
            txtProfileInitial.text = profile.getInitial()
            
            // Set avatar color based on profile ID
            val colors = listOf(
                "#E50914", "#1DB954", "#5865F2", "#FF6B35",
                "#9B59B6", "#3498DB", "#E91E63", "#00BCD4"
            )
            val colorIndex = (profile.id - 1) % colors.size
            viewProfileAvatar.background.setTint(android.graphics.Color.parseColor(colors[colorIndex]))
        } else {
            txtProfileName.text = getString(R.string.profile_switch)
            txtProfileInitial.text = "?"
        }
    }

    /**
     * Navigate to profile selection screen.
     */
    private fun switchProfile() {
        // Clear active profile so user can pick again
        ProfileManager.clearActiveProfile()
        val intent = Intent(this, ProfileSelectionActivity::class.java)
        intent.putExtra(ProfileSelectionActivity.EXTRA_SKIP_AUTO_LOGIN, true)
        startActivity(intent)
        AnimationHelper.applyFadeTransition(this)
        finish()
    }

    private fun openPlaybackPreferences() {
        if (!ProfileManager.hasActiveProfile()) {
            Toast.makeText(this, R.string.profile_not_selected, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, PlaybackPreferencesActivity::class.java)
        startActivity(intent)
        AnimationHelper.applyOpenTransition(this)
    }

    private fun loadDashboard() {
        // Check network before loading dashboard
        if (!NetworkUtils.isNetworkAvailable(this)) {
            ErrorHandler.showError(this, "No internet connection")
            return
        }

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

        // Use WeakReference to prevent activity leak
        val weakActivity = WeakReference(this)

        // Use lifecycleScope for automatic cancellation on activity destruction
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val profileId = ProfileManager.getActiveProfileId()
                val response = NetworkClient.api.getDashboard(profileId)
                withContext(Dispatchers.Main) {
                    val activity = weakActivity.get() ?: return@withContext
                    
                    if (response.status == "success") {
                        val heroItem = response.recentMovies?.randomOrNull()
                            ?: response.continueWatching?.randomOrNull()

                        if (heroItem != null) {
                            activity.updateHeroSection(heroItem)
                            activity.layoutHero.visibility = View.VISIBLE
                        } else {
                            activity.layoutHero.visibility = View.GONE
                        }

                        if (!response.continueWatching.isNullOrEmpty()) {
                            activity.rvContinue.visibility = View.VISIBLE
                            activity.lblContinue.visibility = View.VISIBLE
                            activity.rvContinue.adapter = LibraryAdapter(
                                response.continueWatching.toMutableList(),
                                isHorizontal = true
                            ) { activity.openDetails(it) }
                        } else {
                            activity.rvContinue.visibility = View.GONE
                            activity.lblContinue.visibility = View.GONE
                        }

                        activity.rvRecentMovies.adapter = LibraryAdapter(
                            response.recentMovies?.toMutableList() ?: mutableListOf(),
                            isHorizontal = true
                        ) { activity.openDetails(it) }

                        activity.rvRecentShows.adapter = LibraryAdapter(
                            response.recentShows?.toMutableList() ?: mutableListOf(),
                            isHorizontal = true
                        ) { activity.openDetails(it) }
                    }
                    activity.isLoading = false
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    weakActivity.get()?.let { activity ->
                        ErrorHandler.handleNetworkError(activity, e)
                        activity.isLoading = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    weakActivity.get()?.let { activity ->
                        ErrorHandler.handleNetworkError(activity, e, "Error loading dashboard")
                        activity.isLoading = false
                    }
                }
            }
        }
    }

    private fun fetchLibrary(libId: Int, title: String) {
        // Check network before loading library
        if (!NetworkUtils.isNetworkAvailable(this)) {
            ErrorHandler.showError(this, "No internet connection")
            return
        }

        // Ensure Watch Later fragment is removed so grid can render
        supportFragmentManager.findFragmentById(R.id.fragment_container)?.let { fragment ->
            if (!supportFragmentManager.isStateSaved) {
                supportFragmentManager.beginTransaction().remove(fragment).commitNow()
            } else {
                supportFragmentManager.beginTransaction().remove(fragment).commit()
            }
        }

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

        // Use WeakReference to prevent activity leak
        val weakActivity = WeakReference(this)

        fetchJob = lifecycleScope.launch {
            var pageToLoad = 1
            var hasMorePages = true
            val profileId = ProfileManager.getActiveProfileId()

            try {
                while (hasMorePages && isActive) {
                    val response = withContext(Dispatchers.IO) {
                        NetworkClient.api.getLibrary(libId, pageToLoad, profileId)
                    }

                    val activity = weakActivity.get() ?: return@launch

                    if (response.status == "success") {
                        val newData = response.data ?: emptyList()

                        if (newData.isEmpty()) {
                            hasMorePages = false
                        } else {
                            val sortedData = newData.sortedBy { it.title }

                            if (pageToLoad == 1) {
                                activity.gridAdapter.setLoadingState(false)

                                // OPTIMIZED: Use DiffUtil for the initial load of the library
                                activity.gridAdapter.updateData(sortedData)

                                activity.libraryGridView.scrollToPosition(0)
                                activity.lastGridFocusedPos = RecyclerView.NO_POSITION
                                activity.lastGridFirstVisiblePos = 0
                                activity.lastGridFirstVisibleOffset = 0
                            } else {
                                // Keep efficient range insertion for pagination
                                val startPos = activity.videoList.size
                                activity.videoList.addAll(sortedData)
                                activity.gridAdapter.notifyItemRangeInserted(startPos, sortedData.size)
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
                    weakActivity.get()?.let { activity ->
                        ErrorHandler.handleNetworkError(activity, e, "Error loading library")
                    }
                }
            } finally {
                weakActivity.get()?.let { activity ->
                    activity.isLoading = false
                    activity.gridAdapter.setLoadingState(false)
                }
            }
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
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
                    Constants.LOADING_FOOTER_HEIGHT
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
                params.width = (Constants.HORIZONTAL_CARD_WIDTH_DP * parent.context.resources.displayMetrics.density).toInt()
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
            val displayTitle = if (!video.seriesTitle.isNullOrEmpty()) video.seriesTitle else video.title
            txtTitle.text = displayTitle
            txtYear.text = video.year.toString()
            txtQuality.text = video.quality ?: "HD"
            val formattedRating = RatingUtils.formatImdbRating(video.rating) ?: "5.0"
            txtRating.text = "★ $formattedRating"

            val progressPercent = if (video.resume_time > 0 && video.total_duration > 0) {
                (video.resume_time.toDouble() / video.total_duration.toDouble() * 100).toInt()
            } else 0
            
            if (progressPercent > 0) {
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

            imgPoster.load(video.getDisplayImage()) { 
                crossfade(true)
                placeholder(R.drawable.ic_movie)
                error(R.drawable.ic_movie)
            }
            
            // Accessibility: Set content description for TalkBack
            itemView.contentDescription = AccessibilityUtils.createVideoCardDescription(
                title = displayTitle ?: video.title,
                year = video.year,
                rating = formattedRating,
                quality = video.quality,
                hasProgress = progressPercent > 0,
                progressPercent = progressPercent
            )

            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = true
            itemView.setOnClickListener { onClick(video) }
            
            // Enhanced focus animation
            itemView.setOnFocusChangeListener { v, hasFocus ->
                val scale = if (hasFocus) Constants.FOCUS_SCALE_MEDIUM else 1.0f
                val elevation = if (hasFocus) Constants.FOCUS_ELEVATION else Constants.DEFAULT_ELEVATION
                if (hasFocus) {
                    AnimationHelper.scaleUp(v, scale)
                } else {
                    AnimationHelper.scaleDown(v)
                }
                v.elevation = elevation
            }

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
