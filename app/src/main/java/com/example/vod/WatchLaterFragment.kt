package com.example.vod

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vod.utils.ResponsiveUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WatchLaterFragment : Fragment(R.layout.fragment_library) {
    companion object {
        private const val TAG = "WatchLaterFragment"
    }

    // Reusing fragment_library layout (assuming it has a RecyclerView and ProgressBar)

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var txtEmpty: TextView
    private lateinit var adapter: LibraryAdapter // Reusing your existing adapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.rvLibrary)
        progressBar = view.findViewById(R.id.progressBar)
        txtEmpty = view.findViewById(R.id.txtEmpty) // Ensure this exists in XML or remove

        setupRecyclerView()
        loadWatchLater()
    }

    // Refresh list when user returns (e.g. after removing an item in Detail View)
    override fun onResume() {
        super.onResume()
        loadWatchLater()
    }

    private fun setupRecyclerView() {
        val spanCount = ResponsiveUtils.getGridSpanCountFromResources(requireContext())
        recyclerView.layoutManager = GridLayoutManager(context, spanCount)

        // FIX: Change emptyList() to mutableListOf()
        // This prevents the ClassCastException when updateData is called later
        adapter = LibraryAdapter(mutableListOf()) { video ->
            // Open Details
            val intent = Intent(context, DetailsActivity::class.java)
            intent.putExtra("VIDEO_ID", video.id)
            startActivity(intent)
        }
        recyclerView.adapter = adapter
    }

    private fun loadWatchLater() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        txtEmpty.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Call the NEW API Endpoint with profile_id
                val profileId = ProfileManager.getActiveProfileId()
                val response = NetworkClient.api.getWatchList(page = 1, profileId)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE

                    if (response.status == "success" && !response.videos.isNullOrEmpty()) {
                        recyclerView.visibility = View.VISIBLE
                        adapter.updateData(response.videos)
                        // Force focus on first item for TV navigation
                        recyclerView.post {
                            recyclerView.layoutManager?.findViewByPosition(0)?.requestFocus()
                        }
                    } else {
                        // Show "Empty" state
                        txtEmpty.setText(R.string.watch_list_empty)
                        txtEmpty.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading watch later list", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    txtEmpty.setText(R.string.watch_list_error)
                    txtEmpty.visibility = View.VISIBLE
                }
            }
        }
    }
}
