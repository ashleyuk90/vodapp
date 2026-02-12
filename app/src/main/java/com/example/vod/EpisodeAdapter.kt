package com.example.vod

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat

class EpisodeAdapter(
    private val allEpisodes: List<EpisodeItem>,
    private val onEpisodeClick: (EpisodeItem) -> Unit,
    private val onEpisodePlayClick: (EpisodeItem) -> Unit,
    private val onEpisodePlayWithSubtitlesClick: (EpisodeItem) -> Unit
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

    private fun rebuildDisplayListAndDispatchDiff() {
        val oldList = displayList.toList()
        initialBuild()
        val newList = displayList.toList()

        val diff = DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = oldList.size
                override fun getNewListSize(): Int = newList.size

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val oldItem = oldList[oldItemPosition]
                    val newItem = newList[newItemPosition]
                    return when {
                        oldItem is SeasonHeader && newItem is SeasonHeader ->
                            oldItem.seasonNumber == newItem.seasonNumber
                        oldItem is EpisodeItem && newItem is EpisodeItem ->
                            oldItem.id == newItem.id
                        else -> false
                    }
                }

                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return oldList[oldItemPosition] == newList[newItemPosition]
                }
            }
        )
        diff.dispatchUpdatesTo(this)
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

                val currentlyExpanded = expandedSeasons.contains(header.seasonNumber)
                if (currentlyExpanded) {
                    expandedSeasons.remove(header.seasonNumber)
                } else {
                    expandedSeasons.add(header.seasonNumber)
                }

                // Rebuild list atomically to avoid RecyclerView inconsistencies on rapid taps.
                rebuildDisplayListAndDispatchDiff()
            }
        }
    }

    inner class EpisodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtNum: TextView = itemView.findViewById(R.id.txtEpNumber)
        private val txtTitle: TextView = itemView.findViewById(R.id.txtEpTitle)
        private val txtPlot: TextView = itemView.findViewById(R.id.txtEpPlot)
        private val txtDuration: TextView = itemView.findViewById(R.id.txtEpDuration)
        private val episodePlaybackProgress: ProgressBar = itemView.findViewById(R.id.episodePlaybackProgress)
        private val btnEpPlay: ImageView? = itemView.findViewById(R.id.btnEpPlay)
        private val btnEpCc: TextView? = itemView.findViewById(R.id.btnEpCc)

        fun bind(ep: EpisodeItem) {
            txtNum.text = NumberFormat.getIntegerInstance().format(ep.episode)
            txtTitle.text = ep.title
            txtDuration.text = itemView.context.getString(R.string.duration_format, ep.runtime)
            txtPlot.text = ep.plot ?: ""

            val durationSeconds = when {
                ep.total_duration > 0 -> ep.total_duration
                ep.runtime > 0 -> ep.runtime.toLong() * 60L
                else -> 0L
            }
            val computedProgressPercent = if (ep.resume_time > 0 && durationSeconds > 0) {
                ((ep.resume_time.toDouble() / durationSeconds.toDouble()) * 100).toInt().coerceIn(0, 100)
            } else {
                0
            }
            val progressPercent = if (ep.progress_percent > 0) {
                ep.progress_percent.coerceIn(0, 100)
            } else {
                computedProgressPercent
            }

            if (progressPercent > 0) {
                episodePlaybackProgress.visibility = View.VISIBLE
                episodePlaybackProgress.progress = progressPercent
            } else {
                episodePlaybackProgress.visibility = View.GONE
            }

            itemView.setOnClickListener { onEpisodeClick(ep) }
            btnEpPlay?.setOnClickListener { onEpisodePlayClick(ep) }

            val showCcButton = ep.hasSubtitles && !ep.subtitleUrl.isNullOrBlank()
            btnEpCc?.apply {
                visibility = if (showCcButton) View.VISIBLE else View.GONE
                setOnClickListener(
                    if (showCcButton) {
                        View.OnClickListener { onEpisodePlayWithSubtitlesClick(ep) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}
