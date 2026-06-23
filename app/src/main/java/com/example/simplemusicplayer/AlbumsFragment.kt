package com.example.simplemusicplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.simplemusicplayer.databinding.FragmentAlbumsBinding

class AlbumsFragment : Fragment() {

    private var _binding: FragmentAlbumsBinding? = null
    private val binding get() = _binding!!
    private var albums: List<Album> = emptyList()
    private var onAlbumClick: ((Album) -> Unit)? = null

    companion object {
        private const val ARG_ALBUMS = "arg_albums"
        fun newInstance(albums: List<Album>): AlbumsFragment {
            val fragment = AlbumsFragment()
            val args = Bundle()
            args.putParcelableArrayList(ARG_ALBUMS, ArrayList(albums))
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelableArrayList(ARG_ALBUMS, Album::class.java)?.let {
                albums = it
            }
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelableArrayList<Album>(ARG_ALBUMS)?.let {
                albums = it
            }
        }
    }

    fun setOnAlbumClickListener(listener: (Album) -> Unit) {
        onAlbumClick = listener
    }

    fun updateAlbums(newAlbums: List<Album>) {
        this.albums = newAlbums
        if (_binding != null) {
            (binding.rvAlbums.adapter as? AlbumAdapter)?.updateList(newAlbums)
            binding.tvEmpty.visibility = if (newAlbums.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAlbumsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvAlbums.layoutManager = GridLayoutManager(context, 2)
        binding.rvAlbums.adapter = AlbumAdapter(albums) { album ->
            (activity as? MainActivity)?.showAlbumDetailPublic(album)
        }
        binding.tvEmpty.visibility = if (albums.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}