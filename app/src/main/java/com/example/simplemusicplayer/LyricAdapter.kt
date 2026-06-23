package com.example.simplemusicplayer

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LyricAdapter(private var lyrics: List<LyricLine> = emptyList()) :
    RecyclerView.Adapter<LyricAdapter.LyricViewHolder>() {

    private var currentLineIndex = -1

    fun updateLyrics(newLyrics: List<LyricLine>) {
        lyrics = newLyrics
        currentLineIndex = -1
        notifyDataSetChanged()
    }

    fun updateActiveLine(index: Int): Boolean {
        if (currentLineIndex != index) {
            val previousIndex = currentLineIndex
            currentLineIndex = index
            notifyItemChanged(previousIndex)
            notifyItemChanged(currentLineIndex)
            return true
        }
        return false
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LyricViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return LyricViewHolder(view)
    }

    override fun onBindViewHolder(holder: LyricViewHolder, position: Int) {
        val lyric = lyrics[position]
        holder.textView.text = lyric.text
        holder.textView.textAlignment = View.TEXT_ALIGNMENT_CENTER
        
        if (position == currentLineIndex) {
            holder.textView.setTextColor(Color.parseColor("#1DB954")) // Emerald Green
            holder.textView.typeface = Typeface.DEFAULT_BOLD
            holder.textView.textSize = 20f
        } else {
            holder.textView.setTextColor(Color.parseColor("#A7A7A7")) // text_secondary
            holder.textView.typeface = Typeface.DEFAULT
            holder.textView.textSize = 16f
        }
    }

    override fun getItemCount(): Int = lyrics.size

    class LyricViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(android.R.id.text1)
    }
}