package com.example.simplemusicplayer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.simplemusicplayer.databinding.ItemPlaylistBinding

class PlaylistAdapter(
    private var playlistList: List<Playlist>,
    private val onPlaylistClick: (Playlist) -> Unit,
    private val onMoreClick: (Playlist) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>() {

    class PlaylistViewHolder(val binding: ItemPlaylistBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val binding = ItemPlaylistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlaylistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        val playlist = playlistList[position]
        holder.binding.tvPlaylistName.text = playlist.name
        holder.binding.tvPlaylistCount.text = "${playlist.songCount} Bài hát"
        
        holder.itemView.setOnClickListener {
            onPlaylistClick(playlist)
        }
        
        holder.binding.btnMorePlaylist.setOnClickListener {
            onMoreClick(playlist)
        }
    }

    override fun getItemCount(): Int = playlistList.size

    fun updateList(newList: List<Playlist>) {
        this.playlistList = newList
        notifyDataSetChanged()
    }
}