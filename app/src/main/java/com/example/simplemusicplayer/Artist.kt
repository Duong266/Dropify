package com.example.simplemusicplayer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Artist(
    val name: String,
    val albumCount: Int,
    val songCount: Int,
    val songs: List<Song>
) : Parcelable