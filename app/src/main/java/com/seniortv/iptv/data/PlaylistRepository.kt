package com.seniortv.iptv.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.seniortv.iptv.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class PlaylistRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    companion object {
        private const val PREFS_NAME = "senior_iptv_prefs"
        private const val KEY_CACHED_CHANNELS = "cached_channels_json"
        private const val KEY_LAST_CHANNEL_INDEX = "last_channel_index"
        private const val KEY_M3U_URL = "m3u_playlist_url"
        
        // Link M3U mặc định theo yêu cầu của bạn
        const val DEFAULT_M3U_URL = "https://raw.githubusercontent.com/khiem1992/kdtvm/refs/heads/main/iptv86.m3u"
    }

    /**
     * Lấy URL M3U đã lưu hoặc URL mặc định
     */
    fun getM3uUrl(): String {
        return prefs.getString(KEY_M3U_URL, DEFAULT_M3U_URL) ?: DEFAULT_M3U_URL
    }

    /**
     * Lưu URL M3U mới
     */
    fun saveM3uUrl(url: String) {
        prefs.edit().putString(KEY_M3U_URL, url.trim()).apply()
    }

    /**
     * Lấy danh sách kênh từ cache SharedPreferences (đọc tức thì khi mở app)
     */
    fun getCachedChannels(): List<Channel> {
        val json = prefs.getString(KEY_CACHED_CHANNELS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Channel>>() {}.type
            gson.fromJson<List<Channel>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Lưu danh sách kênh vào cache local
     */
    fun saveChannelsToCache(channels: List<Channel>) {
        val json = gson.toJson(channels)
        prefs.edit().putString(KEY_CACHED_CHANNELS, json).apply()
    }

    /**
     * Lấy index kênh đã xem lần trước (mặc định 0 - kênh đầu tiên)
     */
    fun getLastPlayedIndex(): Int {
        return prefs.getInt(KEY_LAST_CHANNEL_INDEX, 0)
    }

    /**
     * Lưu index kênh vừa xem để lần sau mở lên phát ngay
     */
    fun saveLastPlayedIndex(index: Int) {
        prefs.edit().putInt(KEY_LAST_CHANNEL_INDEX, index).apply()
    }

    /**
     * Cập nhật Playlist từ mạng:
     * - Nếu thành công và có kênh -> lưu vào cache
     * - Nếu thất bại hoặc không hợp lệ -> giữ nguyên cache cũ và trả về lỗi
     */
    suspend fun fetchAndCachePlaylist(url: String): Result<List<Channel>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (SmartTV; Linux; Tizen) AppleWebKit/537.36")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Lỗi máy chủ: HTTP ${response.code}"))
            }

            val body = response.body?.string() ?: ""
            if (body.isBlank()) {
                return@withContext Result.failure(Exception("Danh sách phát rỗng"))
            }

            val channels = M3uParser.parse(body)
            if (channels.isEmpty()) {
                // Giữ nguyên cache cũ
                return@withContext Result.failure(Exception("Không tìm thấy kênh hợp lệ trong file M3U"))
            }

            // Thành công: Cập nhật cache và lưu URL
            saveChannelsToCache(channels)
            saveM3uUrl(url)
            Result.success(channels)
        } catch (e: Exception) {
            // Giữ nguyên cache cũ khi mất mạng hoặc lỗi parse
            Result.failure(e)
        }
    }
}
