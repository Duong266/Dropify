package com.example.simplemusicplayer

import android.content.ContentUris
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.simplemusicplayer.databinding.ItemSongBinding
import java.util.Locale

class SongAdapter(
    private var songList: List<Song>,
    private val onItemClick: (Song) -> Unit,
    private val onMoreClick: ((Song) -> Unit)? = null
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    class SongViewHolder(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    private var playingSongId: Long = -1L

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val currentSong = songList[position]
        holder.binding.tvSongTitle.text = currentSong.title
        
        val durationStr = formatDuration(currentSong.duration)
        holder.binding.tvArtist.text = "${currentSong.artist} • $durationStr"

        val sArtworkUri = Uri.parse("content://media/external/audio/albumart")
        val uri = ContentUris.withAppendedId(sArtworkUri, currentSong.albumId)

        Glide.with(holder.itemView.context)
            .load(uri)
            .placeholder(R.drawable.ic_default_cd)
            .error(R.drawable.ic_default_cd)
            .circleCrop()
            .into(holder.binding.imgSongArt)

        // Highlight playing song
        if (currentSong.id == playingSongId) {
            holder.itemView.setBackgroundResource(R.drawable.bg_playing_song)
        } else {
            holder.itemView.setBackgroundResource(android.R.color.transparent)
        }

        holder.itemView.setOnClickListener {
            onItemClick(currentSong)
        }

        holder.binding.btnMore.setOnClickListener {
            onMoreClick?.invoke(currentSong)
        }
    }

    private fun formatDuration(duration: Int): String {
        val minutes = (duration / 1000) / 60
        val seconds = (duration / 1000) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    override fun getItemCount(): Int = songList.size

    fun updateList(newList: List<Song>) {
        this.songList = newList
        notifyDataSetChanged() // Lệnh để danh sách trên màn hình cập nhật lại
    }

    fun updatePlayingSong(id: Long) {
        this.playingSongId = id
        notifyDataSetChanged()
    }
}