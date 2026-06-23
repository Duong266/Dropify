package com.example.simplemusicplayer

import android.content.ContentUris
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.simplemusicplayer.databinding.ItemArtistBinding

class ArtistAdapter(
    private var artistList: List<Artist>,
    private val onItemClick: (Artist) -> Unit
) : RecyclerView.Adapter<ArtistAdapter.ArtistViewHolder>() {

    class ArtistViewHolder(val binding: ItemArtistBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistViewHolder {
        val binding = ItemArtistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ArtistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ArtistViewHolder, position: Int) {
        val artist = artistList[position]
        holder.binding.tvArtistName.text = artist.name
        holder.binding.tvArtistDetails.text = "${artist.albumCount} Album • ${artist.songCount} Bài hát"

        // For artist art, we often use the artwork of their first song's album
        if (artist.songs.isNotEmpty()) {
            val sArtworkUri = Uri.parse("content://media/external/audio/albumart")
            val uri = ContentUris.withAppendedId(sArtworkUri, artist.songs[0].albumId)

            Glide.with(holder.itemView.context)
                .load(uri)
                .placeholder(R.drawable.ic_default_cd)
                .error(R.drawable.ic_default_cd)
                .circleCrop()
                .into(holder.binding.imgArtistArt)
        } else {
            holder.binding.imgArtistArt.setImageResource(R.drawable.ic_default_cd)
        }

        holder.itemView.setOnClickListener {
            onItemClick(artist)
        }
    }

    override fun getItemCount(): Int = artistList.size

    fun updateList(newList: List<Artist>) {
        this.artistList = newList
        notifyDataSetChanged()
    }
}