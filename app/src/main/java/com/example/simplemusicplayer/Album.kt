package com.example.simplemusicplayer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val songCount: Int,
    val songs: List<Song>
) : Parcelable