package com.example.simplemusicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PlaylistChoiceAdapter(
    private val playlists: List<Playlist>,
    private val initialCheckedIds: Set<String>,
    private val onItemClick: (Playlist, Boolean) -> Unit
) : RecyclerView.Adapter<PlaylistChoiceAdapter.ViewHolder>() {

    private val checkedPlaylists = initialCheckedIds.toMutableSet()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumb: ImageView = view.findViewById(R.id.ivPlaylistThumb)
        val tvName: TextView = view.findViewById(R.id.tvPlaylistName)
        val tvCount: TextView = view.findViewById(R.id.tvSongCount)
        val checkBox: CheckBox = view.findViewById(R.id.cbPlaylist)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist_choice, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val playlist = playlists[position]
        holder.tvName.text = playlist.name
        holder.tvCount.text = "${playlist.songs.size} bài hát"
        
        // Handle thumbnails - in a real app you'd get the first song's art
        if (playlist.name == "Yêu Thích") {
            holder.ivThumb.setImageResource(R.drawable.ic_heart_on)
            holder.ivThumb.setPadding(12, 12, 12, 12)
        } else {
            holder.ivThumb.setImageResource(R.drawable.ic_music)
            holder.ivThumb.setPadding(0, 0, 0, 0)
        }

        holder.checkBox.isChecked = checkedPlaylists.contains(playlist.name)

        holder.itemView.setOnClickListener {
            val isChecked = !holder.checkBox.isChecked
            holder.checkBox.isChecked = isChecked
            if (isChecked) checkedPlaylists.add(playlist.name) else checkedPlaylists.remove(playlist.name)
            onItemClick(playlist, isChecked)
        }
    }

    override fun getItemCount() = playlists.size
}