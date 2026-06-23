package com.example.simplemusicplayer

import android.content.ContentUris
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.simplemusicplayer.databinding.ItemAlbumBinding

class AlbumAdapter(
    private var albumList: List<Album>,
    private val onItemClick: (Album) -> Unit
) : RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder>() {

    class AlbumViewHolder(val binding: ItemAlbumBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val binding = ItemAlbumBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlbumViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        val album = albumList[position]
        holder.binding.tvAlbumName.text = album.name
        holder.binding.tvAlbumDetails.text = "${album.artist} • ${album.songCount} Bài hát"

        val sArtworkUri = Uri.parse("content://media/external/audio/albumart")
        val uri = ContentUris.withAppendedId(sArtworkUri, album.id)

        Glide.with(holder.itemView.context)
            .load(uri)
            .placeholder(R.drawable.ic_default_cd)
            .error(R.drawable.ic_default_cd)
            .into(holder.binding.imgAlbumArt)

        holder.itemView.setOnClickListener {
            onItemClick(album)
        }
    }

    override fun getItemCount(): Int = albumList.size

    fun updateList(newList: List<Album>) {
        this.albumList = newList
        notifyDataSetChanged()
    }
}