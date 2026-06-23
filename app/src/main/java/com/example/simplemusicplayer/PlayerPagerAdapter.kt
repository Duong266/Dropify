package com.example.simplemusicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class PlayerPagerAdapter(
    private val controlView: View,
    private val lyricsView: View
) : RecyclerView.Adapter<PlayerPagerAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return if (viewType == 0) {
            ViewHolder(controlView)
        } else {
            ViewHolder(lyricsView)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Views are already provided and managed by MainActivity
    }

    override fun getItemCount(): Int = 2

    override fun getItemViewType(position: Int): Int = position
}