package com.example.simplemusicplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.Toast
import androidx.core.app.NotificationCompat

class MusicService : Service(), AudioManager.OnAudioFocusChangeListener {
    companion object {
        const val ACTION_PLAY = "com.example.simplemusicplayer.PLAY"
        const val ACTION_PAUSE = "com.example.simplemusicplayer.PAUSE"
        const val ACTION_NEXT = "com.example.simplemusicplayer.NEXT"
        const val ACTION_PREVIOUS = "com.example.simplemusicplayer.PREVIOUS"
    }

    enum class RepeatMode { NONE, ALL, ONE }
    private var currentRepeatMode = RepeatMode.NONE
    private var mediaPlayer: MediaPlayer? = null
    private var equalizer: android.media.audiofx.Equalizer? = null
    private val binder = MusicBinder()
    private var isShuffle = false

    private var playbackSpeed = 1.0f
    private var playbackPitch = 1.0f

    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false

    private var songList = mutableListOf<Song>()
    private var currentIndex = -1

    private var mediaSession: MediaSessionCompat? = null
    private val handler = Handler(Looper.getMainLooper())

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        mediaPlayer = MediaPlayer()
        mediaPlayer?.setOnErrorListener { _, what, extra ->
            android.util.Log.e("MusicService", "MediaPlayer error: what=$what, extra=$extra")
            
            // Nếu là lỗi dịch vụ âm thanh bị chết (hệ thống quá tải)
            if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                mediaPlayer?.reset()
                return@setOnErrorListener true
            }

            // Chỉ coi là lỗi hỏng file nếu extra trả về các mã lỗi IO hoặc định dạng không hỗ trợ
            // Các mã lỗi phổ biến: -1004 (IO), -1007 (Malformed), -1010 (Unsupported)
            val isSeriousError = extra == -1004 || extra == -1007 || extra == -1010
            
            if (isSeriousError) {
                Toast.makeText(this, "Không thể phát bài hát này. File có thể bị hỏng.", Toast.LENGTH_SHORT).show()
                playNext()
            } else {
                // Với các lỗi khác, ta thử reset và tiếp tục thay vì chuyển bài ngay
                android.util.Log.d("MusicService", "Ignored non-fatal error")
            }
            true // Trả về true để báo hiệu đã xử lý lỗi, tránh hiện thông báo mặc định của Android
        }
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        mediaSession = MediaSessionCompat(this, "MusicService").apply {
            isActive = true
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { resumeMusic() }
                override fun onPause() { pauseMusic() }
                override fun onSkipToNext() { playNext() }
                override fun onSkipToPrevious() { playPrevious() }
                override fun onSeekTo(pos: Long) { seekTo(pos.toInt()) }
            })
        }

        // Bắt đầu vòng lặp cập nhật SeekBar trên thông báo
        handler.post(updateMetadataRunnable)
        loadEqualizerSettings()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> resumeMusic()
            ACTION_PAUSE -> pauseMusic()
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
        }
        return START_STICKY
    }

    fun setPlaylist(list: List<Song>, index: Int) {
        this.songList = list.toMutableList()
        this.currentIndex = index
    }

    fun prepareMusic(index: Int, position: Int, playImmediately: Boolean = false) {
        if (index !in songList.indices) return
        currentIndex = index
        val song = songList[index]
        val file = java.io.File(song.path)
        if (!file.exists() || file.length() == 0L) {
            return
        }

        mediaPlayer?.apply {
            reset()
            try {
                setDataSource(song.path)
                prepareAsync()
                setOnPreparedListener {
                    seekTo(position)
                    if (playImmediately && requestAudioFocus()) {
                        start()
                        updatePlaybackState(true)
                        showNotification(song, true)
                    } else {
                        updatePlaybackState(false)
                        showNotification(song, false)
                    }
                    updateMediaMetadata(song)
                    sendUpdateBroadcast("ACTION_UPDATE_UI")
                }
                setOnCompletionListener {
                    playNext()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playMusic(index: Int) {
        if (index !in songList.indices) return
        
        val song = songList[index]
        val file = java.io.File(song.path)
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(this, "File không tồn tại hoặc bị lỗi", Toast.LENGTH_SHORT).show()
            playNext()
            return
        }

        if (!requestAudioFocus()) return

        currentIndex = index

        mediaPlayer?.apply {
            reset()
            try {
                setDataSource(song.path)
                prepareAsync()
                setOnPreparedListener {
                    if (equalizer == null) {
                        try {
                            equalizer = android.media.audiofx.Equalizer(0, audioSessionId)
                            equalizer?.enabled = true
                            applyEqualizerSettings()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    applyPlaybackParams() // Áp dụng tốc độ và cao độ
                    start()
                    updateMediaMetadata(song)
                    updatePlaybackState(true)
                    showNotification(song, true)
                    saveState() // Lưu trạng thái khi bắt đầu phát
                    sendUpdateBroadcast("ACTION_UPDATE_UI")
                }
                setOnCompletionListener {
                    playNext()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MusicService, "Lỗi khi mở file nhạc", Toast.LENGTH_SHORT).show()
                playNext()
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        applyPlaybackParams()
    }

    fun getPlaybackSpeed(): Float = playbackSpeed

    fun setPlaybackPitch(pitch: Float) {
        playbackPitch = pitch
        applyPlaybackParams()
    }

    fun getPlaybackPitch(): Float = playbackPitch

    private fun applyPlaybackParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayer?.let { mp ->
                try {
                    val params = mp.playbackParams
                    params.speed = playbackSpeed
                    params.pitch = playbackPitch
                    mp.playbackParams = params
                } catch (e: Exception) {
                    // Đôi khi MediaPlayer chưa sẵn sàng hoặc không hỗ trợ params cụ thể
                    e.printStackTrace()
                }
            }
        }
    }

    fun pauseMusic() {
        mediaPlayer?.pause()
        abandonAudioFocus()
        updatePlaybackState(false)
        getCurrentSong()?.let { 
            showNotification(it, false)
            saveState()
        }
        sendUpdateBroadcast("ACTION_UPDATE_UI")
    }

    fun stopMusic() {
        mediaPlayer?.stop()
        mediaPlayer?.reset()
        abandonAudioFocus()
        updatePlaybackState(false)
        stopForeground(true)
        sendUpdateBroadcast("ACTION_UPDATE_UI")
    }

    fun resumeMusic() {
        if (requestAudioFocus()) {
            mediaPlayer?.start()
            updatePlaybackState(true)
            getCurrentSong()?.let { 
                showNotification(it, true)
                saveState()
            }
            sendUpdateBroadcast("ACTION_UPDATE_UI")
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnFocusGain) {
                    resumeMusic()
                    resumeOnFocusGain = false
                }
                mediaPlayer?.setVolume(1.0f, 1.0f)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                pauseMusic()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (isPlaying()) {
                    pauseMusic()
                    resumeOnFocusGain = true
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (isPlaying()) {
                    mediaPlayer?.setVolume(0.3f, 0.3f)
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()
            audioManager.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }
    }

    private fun updateMediaMetadata(song: Song) {
        val albumArt = getAlbumArt(song.path)
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer?.duration?.toLong() ?: 0L) // Set tổng thời gian
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
            .build()
        mediaSession?.setMetadata(metadata)
    }

    private fun updatePlaybackState(isPlaying: Boolean) {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO // Cho phép kéo SeekBar
            )
            .setState(state, mediaPlayer?.currentPosition?.toLong() ?: 0L, 1.0f) // Gửi vị trí hiện tại
            .build()
        mediaSession?.setPlaybackState(playbackState)
    }

    // Vòng lặp cập nhật trạng thái SeekBar mỗi giây
    private val updateMetadataRunnable = object : Runnable {
        override fun run() {
            if (isPlaying()) {
                updatePlaybackState(true)
            }
            handler.postDelayed(this, 1000)
        }
    }

    fun playNext() {
        if (songList.isEmpty()) return
        if (currentRepeatMode == RepeatMode.ONE) {
            playMusic(currentIndex)
            return
        }
        if (isShuffle) {
            currentIndex = (songList.indices).random()
        } else {
            currentIndex = (currentIndex + 1) % songList.size
        }
        playMusic(currentIndex)
    }

    fun playPrevious() {
        if (songList.isEmpty()) return
        currentIndex = if (currentIndex <= 0) songList.size - 1 else currentIndex - 1
        playMusic(currentIndex)
    }

    private fun sendUpdateBroadcast(action: String) {
        val intent = Intent(action)
        sendBroadcast(intent)
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false
    fun getDuration(): Int = mediaPlayer?.duration ?: 0
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        updatePlaybackState(isPlaying())
        saveState(position) // Lưu vị trí ngay khi seek
    }
    fun toggleShuffle(): Boolean { isShuffle = !isShuffle; return isShuffle }
    fun isShuffleMode(): Boolean = isShuffle
    fun getRepeatMode(): RepeatMode = currentRepeatMode
    fun getCurrentSong(): Song? = if (currentIndex in songList.indices) songList[currentIndex] else null
    private var bandLevels: ShortArray? = null

    fun getEqualizer(): android.media.audiofx.Equalizer? = equalizer

    fun saveEqualizerSettings() {
        equalizer?.let { eq ->
            val bands = eq.numberOfBands
            val levels = ShortArray(bands.toInt())
            for (i in 0 until bands) {
                levels[i] = eq.getBandLevel(i.toShort())
            }
            bandLevels = levels
            
            val prefs = getSharedPreferences("MusicPlayerPrefs", Context.MODE_PRIVATE)
            val levelString = levels.joinToString(",")
            prefs.edit().putString("equalizer_levels", levelString).apply()
        }
    }

    private fun loadEqualizerSettings() {
        val prefs = getSharedPreferences("MusicPlayerPrefs", Context.MODE_PRIVATE)
        val levelString = prefs.getString("equalizer_levels", null)
        if (levelString != null) {
            try {
                val levels = levelString.split(",").map { it.toShort() }.toShortArray()
                bandLevels = levels
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun applyEqualizerSettings() {
        val levels = bandLevels ?: return
        equalizer?.let { eq ->
            val bands = eq.numberOfBands
            for (i in 0 until bands) {
                if (i < levels.size) {
                    eq.setBandLevel(i.toShort(), levels[i])
                }
            }
        }
    }

    fun addSongToNext(song: Song) {
        if (currentIndex == -1) {
            songList.add(song)
            playMusic(0)
        } else {
            songList.add(currentIndex + 1, song)
        }
    }

    fun changeRepeatMode(): RepeatMode {
        currentRepeatMode = when (currentRepeatMode) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
        return currentRepeatMode
    }

    private fun showNotification(song: Song, isPlaying: Boolean) {
        val channelId = "music_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Music Player Control", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val prevIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PREVIOUS }
        val prevPendingIntent = PendingIntent.getService(this, 0, prevIntent, PendingIntent.FLAG_IMMUTABLE)

        val playPauseIntent = Intent(this, MusicService::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(this, 1, playPauseIntent, PendingIntent.FLAG_IMMUTABLE)

        val nextIntent = Intent(this, MusicService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(this, 2, nextIntent, PendingIntent.FLAG_IMMUTABLE)

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val albumArt = getAlbumArt(song.path)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setSmallIcon(R.drawable.ic_music)
            .setLargeIcon(albumArt)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPausePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setMediaSession(mediaSession?.sessionToken))
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, notification)
        }
    }

    private fun getAlbumArt(path: String): android.graphics.Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val art = retriever.embeddedPicture
            if (art != null) BitmapFactory.decodeByteArray(art, 0, art.size) else null
        } catch (e: Exception) { null } finally { retriever.release() }
    }

    private fun saveState(customPosition: Int? = null) {
        getCurrentSong()?.let { song ->
            val sharedPrefs = getSharedPreferences("MusicPlayerPrefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().apply {
                putLong("last_song_id", song.id)
                putInt("last_position", customPosition ?: mediaPlayer?.currentPosition ?: 0)
                putBoolean("is_playing", isPlaying())
                apply()
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveState()
        // Dừng nhạc và giải phóng tài nguyên ngay khi vuốt đóng ứng dụng
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateMetadataRunnable)
        mediaSession?.release()
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }
}