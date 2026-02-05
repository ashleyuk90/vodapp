package com.example.vod

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EpisodeAdapter(
    private val allEpisodes: List<EpisodeItem>,
    private val onEpisodeClick: (EpisodeItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    // This list changes dynamically as we expand/collapse
    private val displayList = mutableListOf<Any>()

    // Default: Open the first season
    private val expandedSeasons = mutableSetOf<Int>()

    init {
        val seasons = allEpisodes.map { it.season }.distinct().sorted()
        if (seasons.isNotEmpty()) {
            expandedSeasons.add(seasons[0])
        }
        initialBuild()
    }

    private fun initialBuild() {
        displayList.clear()
        // Group by Season
        val grouped = allEpisodes.groupBy { it.season }.toSortedMap()

        grouped.forEach { (season, episodes) ->
            displayList.add(SeasonHeader(season))
            if (expandedSeasons.contains(season)) {
                displayList.addAll(episodes)
            }
        }
    }

    data class SeasonHeader(val seasonNumber: Int)

    override fun getItemViewType(position: Int): Int {
        return if (displayList[position] is SeasonHeader) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_season_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_episode_row, parent, false)
            EpisodeViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) {
            holder.bind(displayList[position] as SeasonHeader)
        } else if (holder is EpisodeViewHolder) {
            holder.bind(displayList[position] as EpisodeItem)
        }
    }

    override fun getItemCount(): Int = displayList.size

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtSeason: TextView = itemView.findViewById(R.id.txtSeasonName)
        private val imgArrow: ImageView = itemView.findViewById(R.id.imgExpand)

        fun bind(header: SeasonHeader) {
            txtSeason.text = itemView.context.getString(R.string.season_format, header.seasonNumber)

            val isExpanded = expandedSeasons.contains(header.seasonNumber)
            imgArrow.rotation = if (isExpanded) 180f else 0f

            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener

                // Find episodes for this season
                val seasonEpisodes = allEpisodes.filter { it.season == header.seasonNumber }

                if (isExpanded) {
                    // COLLAPSE: Remove items below header
                    expandedSeasons.remove(header.seasonNumber)
                    displayList.removeAll(seasonEpisodes)
                    notifyItemRangeRemoved(pos + 1, seasonEpisodes.size)
                } else {
                    // EXPAND: Add items below header
                    expandedSeasons.add(header.seasonNumber)
                    displayList.addAll(pos + 1, seasonEpisodes)
                    notifyItemRangeInserted(pos + 1, seasonEpisodes.size)
                }
                // Animate arrow rotation without breaking list focus
                notifyItemChanged(pos)
            }
        }
    }

    inner class EpisodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtNum: TextView = itemView.findViewById(R.id.txtEpNumber)
        private val txtTitle: TextView = itemView.findViewById(R.id.txtEpTitle)
        private val txtPlot: TextView = itemView.findViewById(R.id.txtEpPlot)
        private val txtDuration: TextView = itemView.findViewById(R.id.txtEpDuration)

        fun bind(ep: EpisodeItem) {
            txtNum.text = ep.episode.toString()
            txtTitle.text = ep.title
            txtDuration.text = itemView.context.getString(R.string.duration_format, ep.runtime)
            txtPlot.text = ep.plot ?: ""

            itemView.setOnClickListener { onEpisodeClick(ep) }
        }
    }
}