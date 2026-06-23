package com.example.simplemusicplayer

import android.Manifest
import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.view.animation.LinearInterpolator
import android.view.View
import android.view.MotionEvent
import android.view.animation.AnimationUtils
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.bumptech.glide.Glide
import com.example.simplemusicplayer.databinding.ActivityMainBinding
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var fullSongList = mutableListOf<Song>()
    private var albumList = mutableListOf<Album>()
    private var musicService: MusicService? = null
    private var isBound = false
    private val favoriteSongIds = mutableSetOf<Long>()

    private var artistList = mutableListOf<Artist>()

    private var currentAlbumSongs = mutableListOf<Song>()
    private val albumSongAdapter by lazy {
        SongAdapter(currentAlbumSongs, onItemClick = { song ->
            val index = currentAlbumSongs.indexOf(song)
            if (isBound && index != -1) {
                musicService?.setPlaylist(currentAlbumSongs, index)
                musicService?.playMusic(index)
                showPlayer()
            }
        }, onMoreClick = { song ->
            showSongOptions(song)
        })
    }

    private val handler = Handler(Looper.getMainLooper())
    private var rotationAnimator: ObjectAnimator? = null

    private val updateUIReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_UPDATE_UI") {
                updateUI()
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            
            // Nếu đã có danh sách bài hát thì thử khôi phục trạng thái ngay khi kết nối Service
            if (fullSongList.isNotEmpty()) {
                restorePlayerState()
            }
            updateUI()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    private lateinit var playerMainView: View
    private lateinit var playerLyricsView: View
    private lateinit var playerPagerAdapter: PlayerPagerAdapter
    private lateinit var lyricAdapter: LyricAdapter
    private var currentLyricLines: List<LyricLine> = emptyList()
    private var lastFetchedSongId: Long = -1
    private var lyricsFetchJob: Job? = null
    private var forceScrollLyrics = false

    private val lrcLibService by lazy {
        Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LrcLibService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        bindService(intent, connection, BIND_AUTO_CREATE)

        setupPlayerViewPager()
        setupViewPager()
        loadFavorites()
        checkPermission()
        setupSwipeToDismiss()

        // Các listener cho Mini Player
        binding.btnMiniPlayPause.setOnClickListener { togglePlayPause() }
        binding.layoutMiniPlayer.setOnClickListener { showPlayer() }

        // Các listener cho Album Detail
        binding.includeAlbumDetail.toolbarAlbumDetail.setOnClickListener { hideAlbumDetail() }
        binding.includeAlbumDetail.rvAlbumSongs.layoutManager = LinearLayoutManager(this)
        binding.includeAlbumDetail.rvAlbumSongs.adapter = albumSongAdapter

        binding.btnCollapse.setOnClickListener { hidePlayer() }
        binding.btnPlayerOptions.setOnClickListener {
            val currentSong = musicService?.getCurrentSong()
            if (currentSong != null) {
                showSongOptions(currentSong)
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.layoutPlayer.visibility == View.VISIBLE) {
                    hidePlayer()
                } else if (binding.includeAlbumDetail.layoutAlbumDetail.visibility == View.VISIBLE) {
                    hideAlbumDetail()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun setupPlayerViewPager() {
        val layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )

        playerMainView = layoutInflater.inflate(R.layout.layout_player_main_content, binding.playerViewPager, false)
        playerMainView.layoutParams = layoutParams

        playerLyricsView = layoutInflater.inflate(R.layout.layout_player_lyrics, binding.playerViewPager, false)
        playerLyricsView.layoutParams = layoutParams

        val rvLyrics = playerLyricsView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvLyrics)
        lyricAdapter = LyricAdapter()
        rvLyrics.layoutManager = LinearLayoutManager(this)
        rvLyrics.adapter = lyricAdapter
        rvLyrics.clipToPadding = false // Cho phép lời nhạc cuộn qua vùng đệm (padding)
        // Thêm padding dưới để đảm bảo dòng cuối cùng có thể cuộn lên đầu màn hình
        rvLyrics.post {
            rvLyrics.setPadding(0, 0, 0, rvLyrics.height)
        }

        playerPagerAdapter = PlayerPagerAdapter(playerMainView, playerLyricsView)
        binding.playerViewPager.adapter = playerPagerAdapter

        // Thiết lập listener cho các nút điều khiển cố định (đã chuyển ra layoutPlayer)
        binding.btnPlayPausePlayer.setOnClickListener { togglePlayPause() }
        binding.btnNextPlayer.setOnClickListener { musicService?.playNext() }
        binding.btnPrevPlayer.setOnClickListener { musicService?.playPrevious() }
        
        binding.btnShufflePlayer.setOnClickListener {
            val isShuffle = musicService?.toggleShuffle() ?: false
            (it as android.widget.ImageButton).setImageResource(if (isShuffle) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_off)
        }

        binding.btnRepeatPlayer.setOnClickListener {
            val newMode = musicService?.changeRepeatMode()
            val icon = when (newMode) {
                MusicService.RepeatMode.ALL -> R.drawable.ic_repeat_all
                MusicService.RepeatMode.ONE -> R.drawable.ic_repeat_one
                else -> R.drawable.ic_repeat_none
            }
            (it as android.widget.ImageButton).setImageResource(icon)
        }

        binding.btnFavoritePlayer.setOnClickListener {
            toggleFavorite()
        }

        binding.btnAddToPlaylistPlayer.setOnClickListener {
            val currentSong = musicService?.getCurrentSong()
            if (currentSong != null) {
                showAddToPlaylistDialog(currentSong)
            }
        }

        binding.seekBarPlayer.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicService?.seekTo(progress)
                    binding.tvCurrentTimePlayer.text = formatTime(progress)
                    forceScrollLyrics = true // Cập nhật vị trí lời ngay khi người dùng kéo
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
    }

    private fun showAddToPlaylistDialog(song: Song) {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_add_to_playlist, null)
        
        val rvPlaylists = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvPlaylistChoices)
        val btnCreateNew = view.findViewById<android.widget.LinearLayout>(R.id.btnCreateNewPlaylist)
        val btnCancel = view.findViewById<android.widget.Button>(R.id.btnCancel)
        val btnConfirm = view.findViewById<android.widget.Button>(R.id.btnConfirm)
        
        btnCancel.setOnClickListener { bottomSheetDialog.dismiss() }

        val sharedPrefs = getSharedPreferences("MusicPlayerPrefs", Context.MODE_PRIVATE)
        val playlistNames = sharedPrefs.getStringSet("playlist_names", emptySet())?.toList()?.sorted() ?: emptyList()
        
        val userPlaylists = playlistNames.map { name ->
            val songIds = sharedPrefs.getString("playlist_$name", "") ?: ""
            val songs = fullSongList.filter { songIds.split(",").contains(it.id.toString()) }
            Playlist(System.currentTimeMillis(), name, songs.size, songs)
        }

        val tempCheckedPlaylists = userPlaylists.filter { playlist ->
            val songIds = sharedPrefs.getString("playlist_${playlist.name}", "") ?: ""
            songIds.split(",").contains(song.id.toString())
        }.map { it.name }.toMutableSet()

        val adapter = PlaylistChoiceAdapter(userPlaylists, tempCheckedPlaylists) { playlist, isChecked ->
            if (isChecked) tempCheckedPlaylists.add(playlist.name) else tempCheckedPlaylists.remove(playlist.name)
        }

        btnConfirm.setOnClickListener {
            for (playlist in userPlaylists) {
                val songIdsStr = sharedPrefs.getString("playlist_${playlist.name}", "") ?: ""
                val songIds = songIdsStr.split(",").filter { it.isNotEmpty() }.toMutableList()
                
                if (tempCheckedPlaylists.contains(playlist.name)) {
                    if (!songIds.contains(song.id.toString())) {
                        songIds.add(song.id.toString())
                    }
                } else {
                    songIds.remove(song.id.toString())
                }
                sharedPrefs.edit().putString("playlist_${playlist.name}", songIds.joinToString(",")).apply()
            }
            updatePlaylistsFragment()
            bottomSheetDialog.dismiss()
            Toast.makeText(this, "Đã cập nhật danh sách", Toast.LENGTH_SHORT).show()
        }

        btnCreateNew.setOnClickListener {
            bottomSheetDialog.dismiss()
            showCreatePlaylistDialogWithSong(song)
        }

        rvPlaylists.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        rvPlaylists.adapter = adapter

        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
    }

    private fun showCreatePlaylistDialogWithSong(song: Song) {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Tạo playlist mới")
        
        val input = android.widget.EditText(this)
        input.hint = "Tên danh sách"
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this)
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
                createNewPlaylistWithSong(name, song)
            }
        }
        builder.setNegativeButton("Hủy", null)
        builder.show()
    }

    private fun createNewPlaylistWithSong(name: String, song: Song) {
        val sharedPrefs = getSharedPreferences("MusicPlayerPrefs", Context.MODE_PRIVATE)
        val playlistNames = sharedPrefs.getStringSet("playlist_names", emptySet())?.toMutableSet() ?: mutableSetOf()
        
        if (playlistNames.contains(name)) {
            Toast.makeText(this, "Danh sách đã tồn tại", Toast.LENGTH_SHORT).show()
            return
        }

        playlistNames.add(name)
        sharedPrefs.edit().apply {
            putStringSet("playlist_names", playlistNames)
            putString("playlist_$name", song.id.toString())
            apply()
        }
        
        updatePlaylistsFragment()
        Toast.makeText(this, "Đã tạo và thêm vào '$name'", Toast.LENGTH_SHORT).show()
    }

    fun playSongFromFragment(song: Song) {
        val index = fullSongList.indexOf(song)
        if (isBound && index != -1) {
            musicService?.setPlaylist(fullSongList, index)
            musicService?.playMusic(index)
            showPlayer()
        }
    }

    fun showAlbumDetailPublic(album: Album) {
        showAlbumDetail(album)
    }

    fun showArtistDetailPublic(artist: Artist) {
        showArtistDetail(artist)
    }

    fun playPlaylistFromFragment(songs: List<Song>, title: String) {
        if (songs.isNotEmpty()) {
            currentAlbumSongs.clear()
            currentAlbumSongs.addAll(songs)
            albumSongAdapter.notifyDataSetChanged()

            binding.includeAlbumDetail.collapsingToolbar.title = title
            binding.includeAlbumDetail.imgAlbumDetailArt.setImageResource(R.drawable.ic_default_cd)

            binding.includeAlbumDetail.layoutAlbumDetail.visibility = View.VISIBLE
            val animation = AnimationUtils.loadAnimation(this, R.anim.slide_up)
            binding.includeAlbumDetail.layoutAlbumDetail.startAnimation(animation)
        } else {
            Toast.makeText(this, "Danh sách này chưa có bài hát", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupViewPager() {
        val adapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Bài hát"
                1 -> "Album"
                2 -> "Nghệ sỹ"
                3 -> "Danh sách nhạc"
                else -> null
            }
        }.attach()
    }

    private fun fetchLyrics(song: Song) {
        // 1. Nếu cùng một bài hát và đã có dữ liệu lời bài hát, không làm gì cả
        if (lastFetchedSongId == song.id && currentLyricLines.isNotEmpty()) return

        // 2. Nếu chuyển sang bài hát mới hoàn toàn
        if (lastFetchedSongId != song.id) {
            lyricsFetchJob?.cancel() // Hủy tiến trình tải của bài cũ
            lastFetchedSongId = song.id
            currentLyricLines = emptyList()
            lyricAdapter.updateLyrics(listOf(LyricLine(0, "Đang tìm lời bài hát...")))
        } else {
            // Nếu cùng bài hát nhưng chưa có lời, và đang có một tiến trình tải đang chạy thì đợi nó
            if (lyricsFetchJob?.isActive == true) return
        }

        lyricsFetchJob = lifecycleScope.launch {
            try {
                // 3. Kiểm tra Cache cục bộ trước (File và SharedPreferences)
                val cacheFile = File(cacheDir, "lyrics_${song.id}.lrc")
                val sharedPrefs = getSharedPreferences("MusicLyricsCache", Context.MODE_PRIVATE)
                
                val cachedContent = if (cacheFile.exists()) {
                    withContext(Dispatchers.IO) { cacheFile.readText() }
                } else {
                    sharedPrefs.getString("lyrics_${song.id}", null)
                }

                if (cachedContent != null) {
                    currentLyricLines = if (cachedContent.contains("[")) parseLyrics(cachedContent)
                                       else cachedContent.lines().filter { it.isNotBlank() }.map { LyricLine(0, it) }
                    lyricAdapter.updateLyrics(currentLyricLines)
                    return@launch
                }

                // 4. Nếu không có cache, tiến hành gọi API
                val cleanTitle = song.title
                    .replace(Regex("(?i)\\s*[\\(\\[](?:official|video|lyric|audio|music|mv|hd|full|karaoke|prod|ft|feat).*?[\\)\\]]", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("(?i)\\s*-\\s*(?:official|video|lyric|audio|music|mv|hd|full|karaoke).*$", RegexOption.IGNORE_CASE), "")
                    .trim()
                
                val cleanArtist = if (song.artist == "<unknown>") "" else song.artist
                    .replace(Regex("(?i)\\s*ft\\.?.*|\\s*feat\\.?.*", RegexOption.IGNORE_CASE), "")
                    .trim()

                val response = withContext(Dispatchers.IO) {
                    try {
                        lrcLibService.getLyrics(cleanArtist, cleanTitle, song.duration / 1000)
                    } catch (e: Exception) { null }
                }
                
                var syncedLyrics = response?.syncedLyrics
                var plainLyrics = response?.plainLyrics

                if (syncedLyrics.isNullOrBlank() && plainLyrics.isNullOrBlank()) {
                    val searchResponse = withContext(Dispatchers.IO) {
                        try {
                            lrcLibService.searchLyrics("$cleanTitle $cleanArtist")
                        } catch (e: Exception) { emptyList() }
                    }
                    val bestMatch = searchResponse.firstOrNull { !it.syncedLyrics.isNullOrBlank() }
                        ?: searchResponse.firstOrNull { !it.plainLyrics.isNullOrBlank() }
                    syncedLyrics = bestMatch?.syncedLyrics
                    plainLyrics = bestMatch?.plainLyrics
                }

                val finalLyricsRaw = syncedLyrics ?: plainLyrics
                if (!finalLyricsRaw.isNullOrBlank()) {
                    // Lưu vào cả 2 nơi để đảm bảo tính nhất quán
                    withContext(Dispatchers.IO) {
                        try { cacheFile.writeText(finalLyricsRaw) } catch (e: Exception) {}
                    }
                    sharedPrefs.edit().putString("lyrics_${song.id}", finalLyricsRaw).apply()

                    currentLyricLines = if (!syncedLyrics.isNullOrBlank()) {
                        parseLyrics(syncedLyrics)
                    } else {
                        finalLyricsRaw.lines().filter { it.isNotBlank() }.map { LyricLine(0, it) }
                    }
                    lyricAdapter.updateLyrics(currentLyricLines)
                } else {
                    lyricAdapter.updateLyrics(listOf(LyricLine(0, "Không tìm thấy lời bài hát")))
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    lyricAdapter.updateLyrics(listOf(LyricLine(0, "Không tìm thấy lời bài hát")))
                }
            }
        }
    }

    private fun parseLyrics(rawLyrics: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val regex = Regex("\\[(\\d{2}):(\\d{2})[\\.:](\\d{2,3})\\](.*)")
        rawLyrics.lines().forEach { line ->
            val match = regex.find(line)
            if (match != null) {
                val min = match.groupValues[1].toLong()
                val sec = match.groupValues[2].toLong()
                val msStr = match.groupValues[3]
                val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
                val time = (min * 60 + sec) * 1000 + ms
                val text = match.groupValues[4].trim()
                if (text.isNotEmpty() || lines.isNotEmpty()) {
                    lines.add(LyricLine(time, text))
                }
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    private fun setupSwipeToDismiss() {
        var startY = 0f
        binding.layoutPlayer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - startY
                    if (deltaY > 0) {
                        binding.layoutPlayer.translationY = deltaY
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaY = event.rawY - startY
                    if (deltaY > 200) {
                        hidePlayer()
                    } else {
                        binding.layoutPlayer.animate().translationY(0f).setDuration(200).start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun showPlayer() {
        if (binding.layoutPlayer.visibility == View.GONE) {
            // Không ẩn album detail để khi đóng player sẽ quay lại được trang đó
            binding.layoutPlayer.translationY = 0f
            val animation = AnimationUtils.loadAnimation(this, R.anim.slide_up)
            binding.layoutPlayer.startAnimation(animation)
            binding.layoutPlayer.visibility = View.VISIBLE
            binding.layoutMiniPlayer.visibility = View.GONE
        }
    }

    private fun showAlbumDetail(album: Album) {
        currentAlbumSongs.clear()
        currentAlbumSongs.addAll(album.songs)
        albumSongAdapter.notifyDataSetChanged()

        binding.includeAlbumDetail.collapsingToolbar.title = album.name
        val sArtworkUri = Uri.parse("content://media/external/audio/albumart")
        val uri = ContentUris.withAppendedId(sArtworkUri, album.id)
        
        // Use custom request listener or error placeholder to verify URI
        Glide.with(this)
            .load(uri)
            .placeholder(R.drawable.ic_default_cd)
            .error(R.drawable.ic_default_cd)
            .centerCrop()
            .into(binding.includeAlbumDetail.imgAlbumDetailArt)

        binding.includeAlbumDetail.layoutAlbumDetail.visibility = View.VISIBLE
        val animation = AnimationUtils.loadAnimation(this, R.anim.slide_up)
        binding.includeAlbumDetail.layoutAlbumDetail.startAnimation(animation)
    }

    private fun showArtistDetail(artist: Artist) {
        currentAlbumSongs.clear()
        currentAlbumSongs.addAll(artist.songs)
        albumSongAdapter.notifyDataSetChanged()

        binding.includeAlbumDetail.collapsingToolbar.title = artist.name
        
        // Use first song's album art as artist art
        if (artist.songs.isNotEmpty()) {
            val sArtworkUri = Uri.parse("content://media/external/audio/albumart")
            val uri = ContentUris.withAppendedId(sArtworkUri, artist.songs[0].albumId)
            Glide.with(this)
                .load(uri)
                .placeholder(R.drawable.ic_default_cd)
                .error(R.drawable.ic_default_cd)
                .centerCrop()
                .into(binding.includeAlbumDetail.imgAlbumDetailArt)
        }

        binding.includeAlbumDetail.layoutAlbumDetail.visibility = View.VISIBLE
        val animation = AnimationUtils.loadAnimation(this, R.anim.slide_up)
        binding.includeAlbumDetail.layoutAlbumDetail.startAnimation(animation)
    }

    private fun hideAlbumDetail() {
        if (binding.includeAlbumDetail.layoutAlbumDetail.visibility == View.VISIBLE) {
            val animation = AnimationUtils.loadAnimation(this, R.anim.slide_down)
            binding.includeAlbumDetail.layoutAlbumDetail.startAnimation(animation)
            binding.includeAlbumDetail.layoutAlbumDetail.visibility = View.GONE
        }
    }

    private fun hidePlayer() {
        if (binding.layoutPlayer.visibility == View.VISIBLE) {
            val animation = AnimationUtils.loadAnimation(this, R.anim.slide_down)
            binding.layoutPlayer.startAnimation(animation)
            binding.layoutPlayer.visibility = View.GONE
            binding.layoutMiniPlayer.visibility = View.VISIBLE
            binding.layoutPlayer.postDelayed({ binding.layoutPlayer.translationY = 0f }, 500)
        }
    }

    fun showSongOptions(song: Song) {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_song_options, null)
        
        val tvTitle = view.findViewById<android.widget.TextView>(R.id.tvOptionTitle)
        val tvArtist = view.findViewById<android.widget.TextView>(R.id.tvOptionArtist)
        val ivFavTitle = view.findViewById<android.widget.ImageView>(R.id.ivOptionFavoriteTitle)
        
        tvTitle.text = song.title
        tvTitle.isSelected = true // Kích hoạt hiệu ứng marquee
        tvArtist.text = song.artist
        
        var isFav = favoriteSongIds.contains(song.id)
        ivFavTitle.setImageResource(if (isFav) R.drawable.ic_heart_on else R.drawable.ic_heart_off)

        ivFavTitle.setOnClickListener {
            if (isFav) {
                favoriteSongIds.remove(song.id)
                isFav = false
                ivFavTitle.setImageResource(R.drawable.ic_heart_off)
                Toast.makeText(this, "Đã xoá khỏi yêu thích", Toast.LENGTH_SHORT).show()
            } else {
                favoriteSongIds.add(song.id)
                isFav = true
                ivFavTitle.setImageResource(R.drawable.ic_heart_on)
                Toast.makeText(this, "Đã thêm vào yêu thích", Toast.LENGTH_SHORT).show()
            }
            saveFavorites()
            updatePlaylistsFragment()
            (binding.viewPager.adapter as? ViewPagerAdapter)?.getFragment(0)?.let {
                (it as? SongsFragment)?.updateSongs(fullSongList)
            }
            
            // Cập nhật UI nếu là bài đang phát
            if (musicService?.getCurrentSong()?.id == song.id) {
                binding.btnFavoritePlayer.setImageResource(if (isFav) R.drawable.ic_heart_on else R.drawable.ic_heart_off)
            }
        }
        
        view.findViewById<android.widget.LinearLayout>(R.id.btnOptionEditTags).setOnClickListener {
            bottomSheetDialog.dismiss()
            showEditTagsDialog(song)
        }

        view.findViewById<android.widget.LinearLayout>(R.id.btnOptionAudioFilter).setOnClickListener {
            bottomSheetDialog.dismiss()
            showAudioFilterDialog()
        }

        view.findViewById<android.widget.LinearLayout>(R.id.btnOptionPlaybackSpeed).setOnClickListener {
            bottomSheetDialog.dismiss()
            showPlaybackSpeedDialog()
        }

        view.findViewById<android.widget.LinearLayout>(R.id.btnOptionTrimMusic).setOnClickListener {
            bottomSheetDialog.dismiss()
            showTrimMusicDialog(song)
        }

        view.findViewById<android.widget.LinearLayout>(R.id.btnOptionSetRingtone).setOnClickListener {
            bottomSheetDialog.dismiss()
            setRingtone(song)
        }

        view.findViewById<android.widget.LinearLayout>(R.id.btnOptionDelete).setOnClickListener {
            bottomSheetDialog.dismiss()
            confirmDeleteSong(song)
        }

        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
    }

    private fun confirmDeleteSong(song: Song) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Xoá bài hát")
            .setMessage("Bạn có chắc chắn muốn xoá bài hát '${song.title}' khỏi thiết bị không? Hành động này không thể hoàn tác.")
            .setPositiveButton("Xoá") { _, _ ->
                deleteSong(song)
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    private fun deleteSong(song: Song) {
        try {
            val file = java.io.File(song.path)
            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    // Xoá khỏi MediaStore để các app khác cũng cập nhật
                    contentResolver.delete(
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        "${android.provider.MediaStore.Audio.Media._ID} = ?",
                        arrayOf(song.id.toString())
                    )
                    
                    Toast.makeText(this, " Đã xoá bài hát", Toast.LENGTH_SHORT).show()
                    
                    // Nếu đang phát bài này thì dừng lại
                    if (musicService?.getCurrentSong()?.id == song.id) {
                        musicService?.stopMusic()
                    }
                    
                    fetchSongs() // Cập nhật lại danh sách bài hát
                } else {
                    Toast.makeText(this, "Không thể xoá tệp tin. Có thể tệp đang được sử dụng hoặc không có quyền.", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Tệp không tồn tại trên đĩa, nhưng vẫn xoá khỏi MediaStore nếu có
                contentResolver.delete(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    "${android.provider.MediaStore.Audio.Media._ID} = ?",
                    arrayOf(song.id.toString())
                )
                fetchSongs()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Lỗi khi xoá: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setRingtone(song: Song) {
        if (!android.provider.Settings.System.canWrite(this)) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
            Toast.makeText(this, "Vui lòng cấp quyền hệ thống để cài nhạc chuông", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
            
            // Cập nhật thuộc tính IS_RINGTONE trong MediaStore để hệ thống nhận diện là nhạc chuông
            val values = android.content.ContentValues()
            values.put(MediaStore.Audio.Media.IS_RINGTONE, true)
            // Có thể bỏ qua việc update nếu không có quyền ghi MediaStore trực tiếp trên Android 10+ 
            // nhưng thường vẫn nên thử hoặc dùng RingtoneManager trực tiếp
            try {
                contentResolver.update(uri, values, null, null)
            } catch (e: Exception) {
                // Ignore if update fails, try to set ringtone anyway
            }

            android.media.RingtoneManager.setActualDefaultRingtoneUri(
                this,
                android.media.RingtoneManager.TYPE_RINGTONE,
                uri
            )
            Toast.makeText(this, "Đã đặt làm nhạc chuông: ${song.title}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Lỗi khi cài nhạc chuông: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPlaybackSpeedDialog() {
        val service = musicService ?: return
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_dialog_playback_speed, null)

        val tvSpeedLabel = view.findViewById<android.widget.TextView>(R.id.tvSpeedLabel)
        val sbSpeed = view.findViewById<android.widget.SeekBar>(R.id.sbSpeed)
        val tvPitchLabel = view.findViewById<android.widget.TextView>(R.id.tvPitchLabel)
        val sbPitch = view.findViewById<android.widget.SeekBar>(R.id.sbPitch)
        val btnReset = view.findViewById<android.widget.Button>(R.id.btnResetSpeed)

        val currentSpeed = service.getPlaybackSpeed()
        tvSpeedLabel.text = "Tốc độ: ${String.format("%.2f", currentSpeed)}x"
        sbSpeed.progress = ((currentSpeed.coerceIn(0.5f, 2.0f) - 0.5f) / 0.01f).toInt()

        sbSpeed.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    val speed = 0.5f + (p * 0.01f)
                    service.setPlaybackSpeed(speed)
                    tvSpeedLabel.text = "Tốc độ: ${String.format("%.2f", speed)}x"
                }
            }
            override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(s: android.widget.SeekBar?) {}
        })

        val currentPitch = service.getPlaybackPitch()
        tvPitchLabel.text = "Cao độ: ${String.format("%.2f", currentPitch)}"
        sbPitch.progress = ((currentPitch.coerceIn(0.5f, 2.0f) - 0.5f) / 0.01f).toInt()

        sbPitch.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    val pitch = 0.5f + (p * 0.01f)
                    service.setPlaybackPitch(pitch)
                    tvPitchLabel.text = "Cao độ: ${String.format("%.2f", pitch)}"
                }
            }
            override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(s: android.widget.SeekBar?) {}
        })

        btnReset.setOnClickListener {
            service.setPlaybackSpeed(1.0f)
            service.setPlaybackPitch(1.0f)
            dialog.dismiss()
            showPlaybackSpeedDialog()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun showTrimMusicDialog(song: Song) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_dialog_trim_music, null)

        val tvTitle = view.findViewById<android.widget.TextView>(R.id.tvTrimTitle)
        val tvRange = view.findViewById<android.widget.TextView>(R.id.tvTrimRange)
        val rangeSlider = view.findViewById<com.google.android.material.slider.RangeSlider>(R.id.trimRangeSlider)
        val btnTrim = view.findViewById<android.widget.Button>(R.id.btnStartTrim)

        tvTitle.text = "Cắt nhạc: ${song.title}"
        var startTimeMs = 0
        var endTimeMs = song.duration

        tvRange.text = "Đoạn cắt: ${formatTime(startTimeMs)} - ${formatTime(endTimeMs)}"

        rangeSlider.valueFrom = 0f
        rangeSlider.valueTo = song.duration.toFloat()
        rangeSlider.values = listOf(0f, song.duration.toFloat())
        
        rangeSlider.setLabelFormatter { value -> formatTime(value.toInt()) }

        rangeSlider.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            startTimeMs = values[0].toInt()
            endTimeMs = values[1].toInt()
            
            if (endTimeMs - startTimeMs < 1000) {
                if (slider.activeThumbIndex == 0) {
                    startTimeMs = (endTimeMs - 1000).coerceAtLeast(0)
                    slider.values = listOf(startTimeMs.toFloat(), endTimeMs.toFloat())
                } else {
                    endTimeMs = (startTimeMs + 1000).coerceAtMost(song.duration)
                    slider.values = listOf(startTimeMs.toFloat(), endTimeMs.toFloat())
                }
            }
            tvRange.text = "Đoạn cắt: ${formatTime(startTimeMs)} - ${formatTime(endTimeMs)} (Tổng: ${formatTime(endTimeMs - startTimeMs)})"
        }

        btnTrim.setOnClickListener {
            if (endTimeMs - startTimeMs < 1000) {
                Toast.makeText(this, "Đoạn cắt phải dài ít nhất 1 giây", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            trimAudio(song, startTimeMs, endTimeMs)
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun trimAudio(song: Song, startTimeMs: Int, endTimeMs: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val startTimeUs = startTimeMs * 1000L
            val endTimeUs = endTimeMs * 1000L
            
            val startStr = formatTime(startTimeMs).replace(":", ".")
            val endStr = formatTime(endTimeMs).replace(":", ".")
            val sanitizedTitle = song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            
            val extractor = MediaExtractor()
            var muxer: MediaMuxer? = null
            var fos: java.io.FileOutputStream? = null
            var success = false
            var outputFile: java.io.File? = null
            
            try {
                extractor.setDataSource(song.path)
                var audioTrackIndex = -1
                var format: MediaFormat? = null
                var mime: String? = null
                
                for (i in 0 until extractor.trackCount) {
                    val f = extractor.getTrackFormat(i)
                    val m = f.getString(MediaFormat.KEY_MIME)
                    if (m?.startsWith("audio/") == true) {
                        audioTrackIndex = i
                        format = f
                        mime = m
                        extractor.selectTrack(i)
                        break
                    }
                }

                if (audioTrackIndex != -1 && mime != null && format != null) {
                    // Ưu tiên dùng .mp3 cho file mpeg, các loại khác dùng .m4a
                    val isMp3 = mime.contains("mpeg", ignoreCase = true) || mime.contains("mp3", ignoreCase = true)
                    val extension = if (isMp3) ".mp3" else ".m4a"
                    val newFileName = "(${startStr})-(${endStr})$sanitizedTitle$extension"
                    
                    val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
                    val trimmedFolder = java.io.File(musicDir, "SimpleMusicPlayer_Trimmed")
                    if (!trimmedFolder.exists()) trimmedFolder.mkdirs()
                    
                    val outTarget = java.io.File(trimmedFolder, newFileName)
                    outputFile = outTarget

                    // Để tránh lỗi "Failed to add track", chúng ta sẽ sử dụng FileOutputStream (ghi byte thô)
                    // cho MP3 và làm fallback cho các định dạng khác nếu MediaMuxer thất bại.
                    var muxerTrackIndex = -1
                    if (isMp3) {
                        fos = java.io.FileOutputStream(outTarget)
                    } else {
                        try {
                            if (outTarget.exists()) outTarget.delete()
                            muxer = MediaMuxer(outTarget.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                            muxerTrackIndex = muxer.addTrack(format)
                            muxer.start()
                        } catch (e: Exception) {
                            // Nếu MediaMuxer không hỗ trợ track này, dùng FileOutputStream ghi đè
                            if (muxer != null) {
                                try { muxer.release() } catch (ex: Exception) {}
                                muxer = null
                            }
                            fos = java.io.FileOutputStream(outTarget)
                        }
                    }

                    val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
                    val bufferInfo = android.media.MediaCodec.BufferInfo()
                    var sampleCount = 0
                    
                    extractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    var firstSampleTimeUs = -1L

                    while (true) {
                        bufferInfo.offset = 0
                        bufferInfo.size = extractor.readSampleData(buffer, 0)
                        if (bufferInfo.size < 0) break
                        
                        val sampleTime = extractor.sampleTime
                        if (sampleTime < startTimeUs) {
                            extractor.advance()
                            continue
                        }
                        if (sampleTime > endTimeUs) break
                        
                        if (firstSampleTimeUs == -1L) firstSampleTimeUs = sampleTime
                        
                        bufferInfo.presentationTimeUs = sampleTime - firstSampleTimeUs
                        
                        // Chuyển đổi sample flags sang buffer flags để tránh lỗi lint
                        var flags = 0
                        if ((extractor.sampleFlags and android.media.MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                            flags = flags or android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME
                        }
                        bufferInfo.flags = flags
                        
                        if (fos != null) {
                            val chunk = ByteArray(bufferInfo.size)
                            buffer.position(0)
                            buffer.get(chunk)
                            fos.write(chunk)
                            sampleCount++
                        } else if (muxer != null && muxerTrackIndex != -1) {
                            muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                            sampleCount++
                        }
                        extractor.advance()
                    }

                    try { muxer?.stop() } catch (e: Exception) {}
                    try { fos?.close() } catch (e: Exception) {}
                    fos = null
                    
                    if (sampleCount > 0) {
                        success = true
                        android.media.MediaScannerConnection.scanFile(this@MainActivity, arrayOf(outTarget.absolutePath), null) { _, _ ->
                            lifecycleScope.launch(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "Đã lưu: ${outTarget.name}", Toast.LENGTH_LONG).show()
                                fetchSongs()
                            }
                        }
                    } else {
                        throw Exception("Không tìm thấy dữ liệu âm thanh trong đoạn được chọn")
                    }
                } else {
                    throw Exception("Định dạng nhạc không được hỗ trợ")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Lỗi khi cắt nhạc: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                try { muxer?.release() } catch (e: Exception) {}
                try { fos?.close() } catch (e: Exception) {}
                try { extractor.release() } catch (e: Exception) {}
                if (!success && outputFile?.exists() == true) {
                    outputFile.delete()
                }
            }
        }
    }

    private fun showAudioFilterDialog() {
        val equalizer = musicService?.getEqualizer()
        if (equalizer == null) {
            Toast.makeText(this, "Trình phát nhạc chưa sẵn sàng", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_dialog_audio_filter, null)
        val containerBands = view.findViewById<android.widget.LinearLayout>(R.id.containerBands)
        val btnReset = view.findViewById<android.widget.Button>(R.id.btnResetEqualizer)

        val bands = equalizer.numberOfBands
        val range = equalizer.bandLevelRange
        val minLevel = range[0]
        val maxLevel = range[1]

        val paddingPx = (16 * resources.displayMetrics.density).toInt()

        for (i in 0 until bands) {
            val bandLayout = android.widget.LinearLayout(this)
            bandLayout.orientation = android.widget.LinearLayout.VERTICAL
            bandLayout.setPadding(0, 0, 0, paddingPx)

            val freq = equalizer.getCenterFreq(i.toShort()) / 1000
            val bandDescription = when {
                freq <= 100 -> "Trầm thấp (Sub-Bass)"
                freq <= 300 -> "Âm trầm (Bass)"
                freq <= 1000 -> "Âm trung (Mid)"
                freq <= 4000 -> "Trung cao (High-Mid)"
                else -> "Âm bổng (Treble)"
            }

            val bandHeader = android.widget.TextView(this)
            bandHeader.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            bandHeader.textSize = 14f
            
            fun updateBandHeader(levelMillibels: Int) {
                val db = levelMillibels / 100
                val sign = if (db > 0) "+" else ""
                bandHeader.text = "$bandDescription\n$freq Hz: $sign$db dB"
            }

            updateBandHeader(equalizer.getBandLevel(i.toShort()).toInt())
            bandLayout.addView(bandHeader)

            val seekBar = android.widget.SeekBar(this)
            seekBar.max = maxLevel - minLevel
            seekBar.progress = equalizer.getBandLevel(i.toShort()) - minLevel
            seekBar.progressTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_primary))
            seekBar.thumbTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_primary))
            
            seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val newLevel = (progress + minLevel).toShort()
                        equalizer.setBandLevel(i.toShort(), newLevel)
                        updateBandHeader(newLevel.toInt())
                        musicService?.saveEqualizerSettings()
                    }
                }
                override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(s: android.widget.SeekBar?) {}
            })
            
            bandLayout.addView(seekBar)
            containerBands.addView(bandLayout)
        }

        btnReset.setOnClickListener {
            for (i in 0 until bands) {
                equalizer.setBandLevel(i.toShort(), 0.toShort())
            }
            dialog.dismiss()
            showAudioFilterDialog()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun showEditTagsDialog(song: Song) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_edit_tags, null)

        val ivAlbumArt = view.findViewById<android.widget.ImageView>(R.id.ivEditAlbumArt)
        val etTitle = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditTitle)
        val etArtist = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditArtist)
        val etAlbum = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditAlbum)
        val etGenre = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditGenre)
        val btnSave = view.findViewById<android.widget.Button>(R.id.btnSaveEdit)
        val btnCancel = view.findViewById<android.widget.Button>(R.id.btnCancelEdit)

        // Load current data
        etTitle.setText(song.title)
        etArtist.setText(song.artist)
        etAlbum.setText(song.album)
        
        // Try to get genre and album art
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(song.path)
            etGenre.setText(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE))
            val art = retriever.embeddedPicture
            if (art != null) {
                val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                ivAlbumArt.setImageBitmap(bitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            retriever.release()
        }

        val originalTitle = etTitle.text.toString()
        val originalArtist = etArtist.text.toString()
        val originalAlbum = etAlbum.text.toString()
        val originalGenre = etGenre.text.toString()

        val checkChanges = {
            val hasChanges = etTitle.text.toString() != originalTitle ||
                    etArtist.text.toString() != originalArtist ||
                    etAlbum.text.toString() != originalAlbum ||
                    etGenre.text.toString() != originalGenre
            btnSave.isEnabled = hasChanges
        }

        val textWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { checkChanges() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        }

        etTitle.addTextChangedListener(textWatcher)
        etArtist.addTextChangedListener(textWatcher)
        etAlbum.addTextChangedListener(textWatcher)
        etGenre.addTextChangedListener(textWatcher)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val newTitle = etTitle.text.toString()
            val newArtist = etArtist.text.toString()
            val newAlbum = etAlbum.text.toString()
            val newGenre = etGenre.text.toString()

            // Save to SharedPreferences for persistence
            val sharedPrefs = getSharedPreferences("MusicPlayerPrefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().apply {
                putString("song_${song.id}_title", newTitle)
                putString("song_${song.id}_artist", newArtist)
                putString("song_${song.id}_album", newAlbum)
                putString("song_${song.id}_genre", newGenre)
                apply()
            }

            // Update local object
            val updatedSong = song.copy(
                title = newTitle,
                artist = newArtist,
                album = newAlbum,
                genre = newGenre
            )
            
            // Refresh the entire list from MediaStore but apply our overrides
            fetchSongs()
            
            Toast.makeText(this, "Đã cập nhật thông tin", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun toggleFavorite() {
        val currentSong = musicService?.getCurrentSong() ?: return
        val isFav = favoriteSongIds.contains(currentSong.id)
        if (isFav) {
            favoriteSongIds.remove(currentSong.id)
        } else {
            favoriteSongIds.add(currentSong.id)
        }
        
        val icon = if (!isFav) R.drawable.ic_heart_on else R.drawable.ic_heart_off
        binding.btnFavoritePlayer.setImageResource(icon)
        
        saveFavorites()
        updatePlaylistsFragment()
    }

    private fun saveFavorites() {
        val sharedPreferences = getSharedPreferences("MusicPlayerPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val favSet = favoriteSongIds.map { it.toString() }.toSet()
        editor.putStringSet("favoriteSongs", favSet)
        editor.apply()
    }

    private fun loadFavorites() {
        val sharedPreferences = getSharedPreferences("MusicPlayerPrefs", Context.MODE_PRIVATE)
        val favSet = sharedPreferences.getStringSet("favoriteSongs", emptySet()) ?: emptySet()
        favoriteSongIds.clear()
        favoriteSongIds.addAll(favSet.map { it.toLong() })
    }

    private fun updatePlaylistsFragment() {
        val favoriteSongs = fullSongList.filter { favoriteSongIds.contains(it.id) }
        
        // Nếu đang mở xem chi tiết danh sách "Yêu thích", cập nhật danh sách bài hát đang hiển thị
        if (binding.includeAlbumDetail.layoutAlbumDetail.visibility == View.VISIBLE && 
            binding.includeAlbumDetail.collapsingToolbar.title == "Yêu thích") {
            currentAlbumSongs.clear()
            currentAlbumSongs.addAll(favoriteSongs)
            albumSongAdapter.notifyDataSetChanged()
        }

        val viewPagerAdapter = binding.viewPager.adapter as? ViewPagerAdapter
        viewPagerAdapter?.getFragment(3)?.let { fragment ->
            if (fragment is PlaylistsFragment) {
                fragment.updateFavorites(favoriteSongs)
                fragment.updateData(fullSongList)
            }
        }
    }

    private fun updateUI() {
        musicService?.getCurrentSong()?.let { song ->
            binding.tvCurrentSongPlayer.apply {
                text = song.title
                isSelected = true
            }
            binding.tvCurrentArtistPlayer.text = song.artist
            
            binding.tvMiniSong.text = song.title
            binding.tvMiniSong.isSelected = true
            binding.tvMiniArtist.text = song.artist
            
            updateAlbumArt(song.path)
            
            val isPlaying = musicService?.isPlaying() == true
            val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            
            binding.btnPlayPausePlayer.setImageResource(playPauseIcon)
            binding.btnMiniPlayPause.setImageResource(playPauseIcon)

            if (isPlaying) {
                startRotation()
            } else {
                rotationAnimator?.pause()
            }
            
            if (binding.layoutPlayer.visibility == View.GONE) {
                binding.layoutMiniPlayer.visibility = View.VISIBLE
            }
            
            val isShuffle = musicService?.isShuffleMode() ?: false
            binding.btnShufflePlayer.setImageResource(
                if (isShuffle) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_off
            )
            
            // Cập nhật trạng thái tim
            val isFav = favoriteSongIds.contains(song.id)
            binding.btnFavoritePlayer.setImageResource(
                if (isFav) R.drawable.ic_heart_on else R.drawable.ic_heart_off
            )
            
            val repeatIcon = when (musicService?.getRepeatMode()) {
                MusicService.RepeatMode.ALL -> R.drawable.ic_repeat_all
                MusicService.RepeatMode.ONE -> R.drawable.ic_repeat_one
                else -> R.drawable.ic_repeat_none
            }
            binding.btnRepeatPlayer.setImageResource(repeatIcon)

            // Tự động tải lời bài hát từ API
            fetchLyrics(song)

            // Cập nhật highlight bài hát đang phát trong danh sách
            (binding.viewPager.adapter as? ViewPagerAdapter)?.updatePlayingSong(song.id)
            albumSongAdapter.updatePlayingSong(song.id)
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("ACTION_UPDATE_UI")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateUIReceiver, filter, RECEIVER_EXPORTED)
        } else {
            ContextCompat.registerReceiver(this, updateUIReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        
        // Khởi động lại luồng cập nhật UI và ép buộc cuộn lại lời nhạc
        handler.removeCallbacks(updateSeekBarRunnable)
        handler.post(updateSeekBarRunnable)
        forceScrollLyrics = true
        
        updateUI()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(updateUIReceiver)
        handler.removeCallbacks(updateSeekBarRunnable) // Dừng cập nhật khi ở nền
        savePlayerState()
    }

    private fun savePlayerState() {
        val song = musicService?.getCurrentSong()
        if (song != null) {
            val sharedPrefs = getSharedPreferences("MusicPlayerPrefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().apply {
                putLong("last_song_id", song.id)
                putInt("last_position", musicService?.getCurrentPosition() ?: 0)
                putBoolean("is_playing", musicService?.isPlaying() ?: false)
                apply()
            }
        }
    }

    private fun restorePlayerState() {
        if (!isBound || fullSongList.isEmpty()) return

        val sharedPrefs = getSharedPreferences("MusicPlayerPrefs", Context.MODE_PRIVATE)
        val lastSongId = sharedPrefs.getLong("last_song_id", -1L)
        val lastPosition = sharedPrefs.getInt("last_position", 0)
        val wasPlaying = sharedPrefs.getBoolean("is_playing", false)

        if (lastSongId != -1L) {
            val songIndex = fullSongList.indexOfFirst { it.id == lastSongId }
            if (songIndex != -1) {
                musicService?.setPlaylist(fullSongList, songIndex)
                musicService?.prepareMusic(songIndex, lastPosition, playImmediately = wasPlaying)
                updateUI()
            }
        }
    }

    private fun togglePlayPause() {
        musicService?.let {
            if (it.isPlaying()) {
                it.pauseMusic()
                rotationAnimator?.pause()
            } else {
                it.resumeMusic()
                rotationAnimator?.resume()
                if (rotationAnimator == null) startRotation()
            }
        }
    }

    private fun updateAlbumArt(path: String) {
        val currentSong = musicService?.getCurrentSong() ?: return
        val sArtworkUri = Uri.parse("content://media/external/audio/albumart")
        val uri = ContentUris.withAppendedId(sArtworkUri, currentSong.albumId)

        Glide.with(this)
            .load(uri)
            .placeholder(R.drawable.ic_default_cd)
            .error(R.drawable.ic_default_cd)
            .centerCrop()
            .into(playerMainView.findViewById<android.widget.ImageView>(R.id.imgAlbumArtPlayer))

        Glide.with(this)
            .load(uri)
            .placeholder(R.drawable.ic_default_cd)
            .error(R.drawable.ic_default_cd)
            .centerCrop()
            .into(binding.imgMiniAlbumArt)
    }

    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            musicService?.let {
                val currentPos = it.getCurrentPosition()
                val duration = it.getDuration()
                binding.seekBarPlayer.max = duration
                binding.seekBarPlayer.progress = currentPos
                binding.tvCurrentTimePlayer.text = formatTime(currentPos)
                binding.tvTotalTimePlayer.text = formatTime(duration)

                // Đồng bộ lời bài hát
                if (currentLyricLines.isNotEmpty()) {
                    val lyricDelayMs = 500 // Độ trễ 500ms giúp khớp với giọng hát hơn
                    var activeIndex = currentLyricLines.indexOfLast { line -> line.timeMs <= (currentPos - lyricDelayMs) }
                    
                    // Nếu đang ở đoạn dạo đầu (trước câu đầu tiên), mặc định chọn câu đầu
                    if (activeIndex == -1) activeIndex = 0

                    val isChanged = lyricAdapter.updateActiveLine(activeIndex)
                    if (isChanged || forceScrollLyrics) {
                        val rvLyrics = playerLyricsView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvLyrics)
                        val layoutManager = rvLyrics.layoutManager as? LinearLayoutManager
                        
                        // Cuộn dòng hiện tại lên vị trí trên cùng (offset = 0)
                        layoutManager?.scrollToPositionWithOffset(activeIndex, 0)
                        forceScrollLyrics = false
                    }
                }
            }
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                handler.postDelayed(this, 100) // Tăng tần suất cập nhật để lời nhạc mượt hơn
            }
        }
    }

    private fun formatTime(ms: Int): String {
        val minutes = (ms / 1000) / 60
        val seconds = (ms / 1000) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun startRotation() {
        val imgAlbumArt = playerMainView.findViewById<android.widget.ImageView>(R.id.imgAlbumArtPlayer)
        if (rotationAnimator == null) {
            rotationAnimator = ObjectAnimator.ofFloat(imgAlbumArt, "rotation", 0f, 360f).apply {
                duration = 10000
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        } else {
            rotationAnimator?.setTarget(imgAlbumArt)
            rotationAnimator?.resume()
        }
    }

    private fun checkPermission() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            fetchSongs()
        } else {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 100)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchSongs()
            } else {
                Toast.makeText(this, "Cần cấp quyền để ứng dụng hoạt động tốt nhất", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchSongs() {
        lifecycleScope.launch(Dispatchers.IO) {
            val newList = mutableListOf<Song>()
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DATE_ADDED
            )
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                // Cache SharedPreferences values to avoid repeated disk access in the loop
                val sharedPrefs = getSharedPreferences("MusicPlayerPrefs", Context.MODE_PRIVATE)
                val allPrefs = sharedPrefs.all

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown"
                    val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                    val album = cursor.getString(albumCol) ?: "Unknown Album"
                    val path = cursor.getString(dataCol)
                    val duration = cursor.getInt(durationCol)
                    val albumId = cursor.getLong(albumIdCol)
                    val dateAdded = cursor.getLong(dateAddedCol)

                    // Bỏ qua các file bị lỗi hoặc trống (0:00 duration hoặc 0 byte)
                    if (duration <= 0) continue
                    if (path != null && java.io.File(path).length() == 0L) continue

                    // Check if we have modified this song using the cached map
                    val modifiedTitle = allPrefs["song_${id}_title"] as? String
                    val modifiedArtist = allPrefs["song_${id}_artist"] as? String
                    val modifiedAlbum = allPrefs["song_${id}_album"] as? String
                    val modifiedGenre = allPrefs["song_${id}_genre"] as? String

                    newList.add(Song(
                        id,
                        modifiedTitle ?: title,
                        modifiedArtist ?: artist,
                        modifiedAlbum ?: album,
                        modifiedGenre,
                        path,
                        duration,
                        albumId,
                        dateAdded
                    ))
                }
            }

            // Group songs by album based on name and artist to reflect tag edits
            val groupedAlbums = newList.groupBy { it.album to it.artist }.map { (_, songs) ->
                Album(
                    songs[0].albumId, // Use the first song's albumId for the artwork
                    songs[0].album,
                    songs[0].artist,
                    songs.size,
                    songs
                )
            }.sortedBy { it.name }

            // Group songs by artist
            val groupedArtists = newList.groupBy { it.artist }.map { (artistName, songs) ->
                // Count distinct album names instead of albumIds to reflect tag edits
                val albumsCount = songs.map { it.album }.distinct().size
                Artist(
                    artistName,
                    albumsCount,
                    songs.size,
                    songs
                )
            }.sortedBy { it.name }

            // Move this sorting/filtering logic into the background as well
            val sortedList = newList.toMutableList()

            withContext(Dispatchers.Main) {
                fullSongList.clear()
                fullSongList.addAll(sortedList)
                albumList.clear()
                albumList.addAll(groupedAlbums)
                artistList.clear()
                artistList.addAll(groupedArtists)
                
                // Cập nhật các fragment thông qua adapter
                val favoriteSongs = fullSongList.filter { favoriteSongIds.contains(it.id) }
                (binding.viewPager.adapter as? ViewPagerAdapter)?.updateData(fullSongList, albumList, artistList, favoriteSongs)
                updatePlaylistsFragment()
                
                if (musicService?.getCurrentSong() == null) {
                    restorePlayerState()
                } else {
                    updateUI()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) unbindService(connection)
        handler.removeCallbacks(updateSeekBarRunnable)
    }
}