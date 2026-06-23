package com.example.simplemusicplayer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String? = null,
    val path: String,
    val duration: Int,
    val albumId: Long,
    val dateAdded: Long = 0
) : Parcelable