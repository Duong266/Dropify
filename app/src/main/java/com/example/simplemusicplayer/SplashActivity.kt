package com.example.simplemusicplayer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.simplemusicplayer.databinding.ActivitySplashBinding
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

@SuppressLint("CustomSplash")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startLoading()
    }

    private fun startLoading() {
        lifecycleScope.launch(Dispatchers.IO) {
            // 1. Lấy danh sách bài hát từ bộ nhớ
            val songs = fetchSongsSync()
            
            withContext(Dispatchers.Main) {
                binding.progressBar.max = songs.size
                binding.tvLoadingStatus.text = "Đang kiểm tra kết nối internet..."
            }

            // 2. Kiểm tra internet và cập nhật lời bài hát nếu có
            if (isNetworkAvailable()) {
                withContext(Dispatchers.Main) {
                    binding.tvLoadingStatus.text = "Loading..."
                }
                
                songs.forEachIndexed { index, song ->
                    // Chỉ tải nếu chưa có cache
                    val cacheFile = File(cacheDir, "lyrics_${song.id}.lrc")
                    if (!cacheFile.exists()) {
                        fetchAndCacheLyrics(song)
                    }
                    
                    withContext(Dispatchers.Main) {
                        binding.progressBar.progress = index + 1
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    binding.tvLoadingStatus.text = "Không có internet, sử dụng dữ liệu ngoại tuyến."
                }
                delay(1000) // Cho người dùng kịp đọc thông báo
            }

            // 3. Chuyển sang MainActivity
            withContext(Dispatchers.Main) {
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                finish()
            }
        }
    }

    private fun fetchSongsSync(): List<Song> {
        val list = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION)
        
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Unknown"
                val artist = cursor.getString(artistCol) ?: "Unknown"
                val duration = cursor.getInt(durCol)
                if (duration > 0) {
                    list.add(Song(id, title, artist, "", null, "", duration, 0))
                }
            }
        }
        return list
    }

    private fun fetchAndCacheLyrics(song: Song) {
        try {
            val cleanTitle = song.title.replace(Regex("(?i)\\(.*?\\)|\\[.*?\\]"), "").split("-")[0].trim()
            val cleanArtist = song.artist.split(Regex("(?i),|&|\\bft\\.?|\\bfeat\\.?"))[0].trim()
            val url = "https://lrclib.net/api/get?artist_name=${Uri.encode(cleanArtist)}&track_name=${Uri.encode(cleanTitle)}&duration=${song.duration / 1000}"
            
            val request = Request.Builder().url(url).header("User-Agent", "SimpleMusicPlayer/1.0").build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonData = response.body()?.string() ?: return
                    val jsonObject = JSONObject(jsonData)
                    val syncedLyrics = jsonObject.optString("syncedLyrics")
                    val plainLyrics = jsonObject.optString("plainLyrics")
                    
                    val contentToCache = when {
                        !syncedLyrics.isNullOrEmpty() && syncedLyrics != "null" -> syncedLyrics
                        !plainLyrics.isNullOrEmpty() && plainLyrics != "null" -> plainLyrics
                        else -> null
                    }
                    
                    contentToCache?.let {
                        File(cacheDir, "lyrics_${song.id}.lrc").writeText(it)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    }
}
