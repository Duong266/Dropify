package com.example.simplemusicplayer

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(
    fragmentActivity: FragmentActivity
) : FragmentStateAdapter(fragmentActivity) {

    private val fragments = mutableMapOf<Int, Fragment>()
    private var songs: List<Song> = emptyList()
    private var albums: List<Album> = emptyList()
    private var artists: List<Artist> = emptyList()
    private var favorites: List<Song> = emptyList()
    private var playingSongId: Long = -1L

    fun updateData(songs: List<Song>, albums: List<Album>, artists: List<Artist>, favorites: List<Song> = emptyList()) {
        this.songs = songs
        this.albums = albums
        this.artists = artists
        this.favorites = favorites
        
        // Cập nhật các fragment đã được khởi tạo
        fragments[0]?.let { (it as? SongsFragment)?.updateSongs(songs) }
        fragments[1]?.let { (it as? AlbumsFragment)?.updateAlbums(albums) }
        fragments[2]?.let { (it as? ArtistsFragment)?.updateArtists(artists) }
        fragments[3]?.let { 
            val fragment = it as? PlaylistsFragment
            fragment?.updateData(songs)
            if (favorites.isNotEmpty()) {
                fragment?.updateFavorites(favorites)
            }
        }
        
        // Luôn cập nhật trạng thái đang phát nếu có
        updatePlayingSong(playingSongId)
    }

    fun updatePlayingSong(id: Long) {
        this.playingSongId = id
        fragments[0]?.let { (it as? SongsFragment)?.updatePlayingSong(id) }
        // Có thể mở rộng cho các fragment khác nếu cần (ví dụ: Album detail)
    }

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        val fragment = when (position) {
            0 -> SongsFragment.newInstance(songs)
            1 -> AlbumsFragment.newInstance(albums)
            2 -> ArtistsFragment.newInstance(artists)
            3 -> PlaylistsFragment.newInstance(songs, favorites)
            else -> throw IllegalStateException("Invalid position")
        }
        fragments[position] = fragment
        return fragment
    }

    fun getFragment(position: Int): Fragment? {
        return fragments[position]
    }
}