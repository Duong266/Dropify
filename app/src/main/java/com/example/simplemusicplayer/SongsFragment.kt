package com.example.simplemusicplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.simplemusicplayer.databinding.FragmentSongsBinding

class SongsFragment : Fragment() {

    private var _binding: FragmentSongsBinding? = null
    private val binding get() = _binding!!
    private var songs: List<Song> = emptyList()
    private var onSongClick: ((Song) -> Unit)? = null
    private var onMoreClick: ((Song) -> Unit)? = null

    companion object {
        private const val ARG_SONGS = "arg_songs"
        fun newInstance(songs: List<Song>): SongsFragment {
            val fragment = SongsFragment()
            val args = Bundle()
            args.putParcelableArrayList(ARG_SONGS, ArrayList(songs))
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelableArrayList(ARG_SONGS, Song::class.java)?.let {
                songs = it
            }
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelableArrayList<Song>(ARG_SONGS)?.let {
                songs = it
            }
        }
    }

    fun setOnSongClickListener(listener: (Song) -> Unit) {
        onSongClick = listener
    }

    fun setOnMoreClickListener(listener: (Song) -> Unit) {
        onMoreClick = listener
    }

    fun updateSongs(newSongs: List<Song>) {
        this.songs = newSongs
        if (_binding != null) {
            (binding.rvSongs.adapter as? SongAdapter)?.updateList(newSongs)
            binding.tvEmpty.visibility = if (newSongs.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    fun updatePlayingSong(id: Long) {
        if (_binding != null) {
            (binding.rvSongs.adapter as? SongAdapter)?.updatePlayingSong(id)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvSongs.layoutManager = LinearLayoutManager(context)
        binding.rvSongs.adapter = SongAdapter(songs, { song ->
            (activity as? MainActivity)?.let { mainActivity ->
                // Gọi xử lý trực tiếp từ MainActivity thay vì callback
                mainActivity.playSongFromFragment(song)
            }
        }, { song ->
            (activity as? MainActivity)?.showSongOptions(song)
        })
        binding.tvEmpty.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}