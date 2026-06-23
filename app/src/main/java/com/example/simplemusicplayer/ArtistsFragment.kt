package com.example.simplemusicplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.simplemusicplayer.databinding.FragmentArtistsBinding

class ArtistsFragment : Fragment() {

    private var _binding: FragmentArtistsBinding? = null
    private val binding get() = _binding!!
    private var artists: List<Artist> = emptyList()
    private var onArtistClick: ((Artist) -> Unit)? = null

    companion object {
        private const val ARG_ARTISTS = "arg_artists"
        fun newInstance(artists: List<Artist>): ArtistsFragment {
            val fragment = ArtistsFragment()
            val args = Bundle()
            args.putParcelableArrayList(ARG_ARTISTS, ArrayList(artists))
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelableArrayList(ARG_ARTISTS, Artist::class.java)?.let {
                artists = it
            }
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelableArrayList<Artist>(ARG_ARTISTS)?.let {
                artists = it
            }
        }
    }

    fun setOnArtistClickListener(listener: (Artist) -> Unit) {
        onArtistClick = listener
    }

    fun updateArtists(newArtists: List<Artist>) {
        this.artists = newArtists
        if (_binding != null) {
            (binding.rvArtists.adapter as? ArtistAdapter)?.updateList(newArtists)
            binding.tvEmpty.visibility = if (newArtists.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentArtistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvArtists.layoutManager = LinearLayoutManager(context)
        binding.rvArtists.adapter = ArtistAdapter(artists) { artist ->
            (activity as? MainActivity)?.showArtistDetailPublic(artist)
        }
        binding.tvEmpty.visibility = if (artists.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}