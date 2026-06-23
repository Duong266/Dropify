package com.example.simplemusicplayer

import retrofit2.http.GET
import retrofit2.http.Query

interface LrcLibService {
    @GET("api/get")
    suspend fun getLyrics(
        @Query("artist_name") artist: String,
        @Query("track_name") track: String,
        @Query("duration") duration: Int
    ): LrcLibResponse?

    @GET("api/search")
    suspend fun searchLyrics(
        @Query("q") query: String
    ): List<LrcLibResponse>
}