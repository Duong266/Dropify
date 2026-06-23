package com.example.simplemusicplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.simplemusicplayer.databinding.FragmentPlaylistsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistsFragment : Fragment() {

    private var _binding: FragmentPlaylistsBinding? = null
    private val binding get() = _binding!!
    private var allSongs: List<Song> = emptyList()
    private var favoriteSongs: List<Song> = emptyList()
    private var userPlaylists: MutableList<Playlist> = mutableListOf()
    
    private var onPlaylistClick: ((List<Song>, String) -> Unit)? = null
    private var playlistAdapter: PlaylistAdapter? = null

    companion object {
        private const val ARG_SONGS = "arg_songs"
        private const val ARG_FAVORITES = "arg_favorites"
        
        fun newInstance(songs: List<Song>, favorites: List<Song>): PlaylistsFragment {
            val fragment = PlaylistsFragment()
            val args = Bundle()
            args.putParcelableArrayList(ARG_SONGS, ArrayList(songs))
            args.putParcelableArrayList(ARG_FAVORITES, ArrayList(favorites))
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelableArrayList(ARG_SONGS, Song::class.java)?.let {
                allSongs = it
            }
            arguments?.getParcelableArrayList(ARG_FAVORITES, Song::class.java)?.let {
                favoriteSongs = it
            }
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelableArrayList<Song>(ARG_SONGS)?.let {
                allSongs = it
            }
            @Suppress("DEPRECATION")
            arguments?.getParcelableArrayList<Song>(ARG_FAVORITES)?.let {
                favoriteSongs = it
            }
        }
    }

    fun setOnPlaylistClickListener(listener: (List<Song>, String) -> Unit) {
        onPlaylistClick = listener
    }

    fun updateData(songs: List<Song>) {
        this.allSongs = songs
        if (_binding != null) {
            updateUI()
        }
    }

    fun updateFavorites(favoriteSongs: List<Song>) {
        this.favoriteSongs = favoriteSongs
        _binding?.let { b ->
            b.tvFavoritesCount.text = "${favoriteSongs.size} Bài hát"
            b.cardFavorites.setOnClickListener {
                if (favoriteSongs.isNotEmpty()) {
                    (activity as? MainActivity)?.playPlaylistFromFragment(favoriteSongs, "Yêu thích")
                } else {
                    android.widget.Toast.makeText(context, "Chưa có bài hát yêu thích", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateUI() {
        val b = _binding ?: return
        
        // 1. Được thêm sau cùng (Sắp xếp theo dateAdded giảm dần)
        val lastAddedSongs = allSongs.sortedByDescending { it.dateAdded }.take(20)
        b.tvLastAddedCount.text = "${lastAddedSongs.size} Bài hát"
        
        b.cardLastAdded.setOnClickListener {
            (activity as? MainActivity)?.playPlaylistFromFragment(lastAddedSongs, "Được thêm sau cùng")
        }

        // Cập nhật số lượng bài hát yêu thích từ danh sách đã có
        b.tvFavoritesCount.text = "${favoriteSongs.size} Bài hát"

        // Các mục khác
        b.cardMostPlayed.setOnClickListener {
            (activity as? MainActivity)?.playPlaylistFromFragment(emptyList(), "Được phát nhiều nhất")
        }
        
        b.cardRecentlyPlayed.setOnClickListener {
            (activity as? MainActivity)?.playPlaylistFromFragment(emptyList(), "Phát gần đây")
        }
        
        b.cardFavorites.setOnClickListener {
            (activity as? MainActivity)?.playPlaylistFromFragment(favoriteSongs, "Yêu thích")
        }

        loadUserPlaylists()
    }

    private fun loadUserPlaylists() {
        val context = context ?: return
        val lifecycleScope = viewLifecycleOwner.lifecycleScope
        
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val sharedPrefs = context.getSharedPreferences("MusicPlayerPrefs", android.content.Context.MODE_PRIVATE)
            val playlistNames = sharedPrefs.getStringSet("playlist_names", emptySet()) ?: emptySet()
            
            // Tối ưu: Tạo Map để tra cứu ID bài hát trong O(1) thay vì O(N)
            val songMap = allSongs.associateBy { it.id }
            
            val updatedPlaylists = mutableListOf<Playlist>()
            for (name in playlistNames) {
                val songIdsStr = sharedPrefs.getString("playlist_$name", "") ?: ""
                val songIds = songIdsStr.split(",").filter { it.isNotEmpty() }
                
                val playlistSongs = mutableListOf<Song>()
                for (idStr in songIds) {
                    idStr.toLongOrNull()?.let { id ->
                        songMap[id]?.let { playlistSongs.add(it) }
                    }
                }
                updatedPlaylists.add(Playlist(name.hashCode().toLong(), name, playlistSongs.size, playlistSongs))
            }

            withContext(kotlinx.coroutines.Dispatchers.Main) {
                _binding?.let { b ->
                    userPlaylists.clear()
                    userPlaylists.addAll(updatedPlaylists)
                    
                    if (userPlaylists.isEmpty()) {
                        b.layoutEmptyPlaylist.visibility = View.VISIBLE
                        b.rvPlaylists.visibility = View.GONE
                    } else {
                        b.layoutEmptyPlaylist.visibility = View.GONE
                        b.rvPlaylists.visibility = View.VISIBLE
                        playlistAdapter?.updateList(userPlaylists)
                    }
                }
            }
        }
    }

    private fun showCreatePlaylistDialog() {
        val builder = android.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Tạo danh sách mới")
        
        val input = android.widget.EditText(requireContext())
        input.hint = "Tên danh sách nhạc"
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(requireContext())
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(padding, padding / 2, padding, padding / 2)
        input.layoutParams = params
        container.addView(input)
        builder.setView(container)

        builder.setPositiveButton("Tạo") { _, _ ->
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) {
                savePlaylist(name, "")
                loadUserPlaylists()
            }
        }
        builder.setNegativeButton("Hủy", null)
        builder.show()
    }

    private fun savePlaylist(name: String, songIds: String) {
        val sharedPrefs = requireContext().getSharedPreferences("MusicPlayerPrefs", android.content.Context.MODE_PRIVATE)
        val playlistNames = sharedPrefs.getStringSet("playlist_names", emptySet())?.toMutableSet() ?: mutableSetOf()
        playlistNames.add(name)
        sharedPrefs.edit().apply {
            putStringSet("playlist_names", playlistNames)
            putString("playlist_$name", songIds)
            apply()
        }
        loadUserPlaylists()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlaylistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val adapter = PlaylistAdapter(userPlaylists, { playlist ->
            (activity as? MainActivity)?.playPlaylistFromFragment(playlist.songs, playlist.name)
        }, { playlist ->
            showPlaylistOptions(playlist)
        })
        playlistAdapter = adapter
        
        binding.rvPlaylists.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        binding.rvPlaylists.adapter = adapter

        binding.btnAddPlaylist.setOnClickListener { showCreatePlaylistDialog() }
        binding.btnCreatePlaylist.setOnClickListener { showCreatePlaylistDialog() }

        updateUI()
    }

    private fun showPlaylistOptions(playlist: Playlist) {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.layout_playlist_options, null)
        
        val tvTitle = view.findViewById<android.widget.TextView>(R.id.tvPlaylistOptionTitle)
        tvTitle.text = playlist.name
        
        view.findViewById<android.widget.LinearLayout>(R.id.btnRenamePlaylist).setOnClickListener {
            bottomSheetDialog.dismiss()
            showRenamePlaylistDialog(playlist)
        }
        
        view.findViewById<android.widget.LinearLayout>(R.id.btnDeletePlaylist).setOnClickListener {
            bottomSheetDialog.dismiss()
            showDeletePlaylistDialog(playlist)
        }

        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
    }

    private fun showRenamePlaylistDialog(playlist: Playlist) {
        val builder = android.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Đổi tên danh sách")
        
        val input = android.widget.EditText(requireContext())
        input.setText(playlist.name)
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(requireContext())
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(padding, padding / 2, padding, padding / 2)
        input.layoutParams = params
        container.addView(input)
        builder.setView(container)

        builder.setPositiveButton("Lưu") { _, _ ->
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty() && newName != playlist.name) {
                renamePlaylist(playlist.name, newName)
                loadUserPlaylists()
            }
        }
        builder.setNegativeButton("Hủy", null)
        builder.show()
    }

    private fun showDeletePlaylistDialog(playlist: Playlist) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Xóa danh sách")
            .setMessage("Bạn có chắc chắn muốn xóa danh sách '${playlist.name}' không?")
            .setPositiveButton("Xóa") { _, _ ->
                deletePlaylist(playlist.name)
                loadUserPlaylists()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun renamePlaylist(oldName: String, newName: String) {
        val sharedPrefs = requireContext().getSharedPreferences("MusicPlayerPrefs", android.content.Context.MODE_PRIVATE)
        val playlistNames = sharedPrefs.getStringSet("playlist_names", emptySet())?.toMutableSet() ?: mutableSetOf()
        
        if (playlistNames.contains(oldName)) {
            val songIds = sharedPrefs.getString("playlist_$oldName", "") ?: ""
            playlistNames.remove(oldName)
            playlistNames.add(newName)
            
            sharedPrefs.edit().apply {
                putStringSet("playlist_names", playlistNames)
                remove("playlist_$oldName")
                putString("playlist_$newName", songIds)
                apply()
            }
            loadUserPlaylists()
        }
    }

    private fun deletePlaylist(name: String) {
        val sharedPrefs = requireContext().getSharedPreferences("MusicPlayerPrefs", android.content.Context.MODE_PRIVATE)
        val playlistNames = sharedPrefs.getStringSet("playlist_names", emptySet())?.toMutableSet() ?: mutableSetOf()
        
        playlistNames.remove(name)
        sharedPrefs.edit().apply {
            putStringSet("playlist_names", playlistNames)
            remove("playlist_$name")
            apply()
        }
        loadUserPlaylists()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}