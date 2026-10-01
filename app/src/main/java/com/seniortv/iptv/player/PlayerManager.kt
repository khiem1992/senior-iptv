package com.seniortv.iptv.player

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.PlayerView
import com.seniortv.iptv.model.Channel
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Quản lý DUY NHẤT một ExoPlayer trong toàn bộ vòng đời ứng dụng.
 * Tối ưu hóa chuyên sâu cho các luồng IPTV Việt Nam (FPT Play, VTVPrime, VTVGo):
 * 1. Tự động gắn User-Agent Sony Android TV & Referer tương ứng cho từng CDN.
 * 2. Tắt chunklessPreparation để tải header TS giải mã chính xác (fix triệt để lỗi FPT Play).
 * 3. Cho phép giải mã Non-IDR keyframes và Access Units trong MPEG-TS.
 * 4. Tự động chuyển luồng dự phòng khi kênh chính gặp lỗi.
 */
class PlayerManager(private val context: Context) {

    private val tag = "SeniorPlayerManager"
    var exoPlayer: ExoPlayer? = null
        private set

    private var currentChannel: Channel? = null
    private var currentStreamUrlIndex = 0
    private var retryCount = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRetrying = false
    private var retryRunnable: Runnable? = null

    // Callback báo trạng thái cho Activity hiển thị OSD / Reconnecting
    var onStateChanged: ((state: PlaybackState, message: String?) -> Unit)? = null

    enum class PlaybackState {
        BUFFERING,
        READY,
        RECONNECTING,
        ERROR
    }

    init {
        initPlayer()
    }

    private fun initPlayer() {
        if (exoPlayer != null) return

        // 1. Cấu hình OkHttp với Dynamic Headers Interceptor
        val okHttpBuilder = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val url = originalRequest.url.toString()
                val requestBuilder = originalRequest.newBuilder()

                // User-Agent chuẩn Sony BRAVIA Android TV (Tất cả CDN VN chấp nhận 100%)
                val defaultUa = "Mozilla/5.0 (Linux; Android 11; BRAVIA 4K Build/RP1A.201005.002) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                requestBuilder.header("User-Agent", defaultUa)

                // Tự động gán Referer & Origin phù hợp theo máy chủ stream
                if (url.contains("fptplay")) {
                    requestBuilder.header("Referer", "https://fptplay.vn/")
                    requestBuilder.header("Origin", "https://fptplay.vn")
                } else if (url.contains("vtvprime")) {
                    requestBuilder.header("Referer", "https://vtvprime.vn/")
                } else if (url.contains("vtvdigital") || url.contains("vtvgo")) {
                    requestBuilder.header("Referer", "https://vtvgo.vn/")
                }

                // Gán các header đặc biệt nếu kênh có cấu hình riêng
                currentChannel?.customHeaders?.forEach { (k, v) ->
                    requestBuilder.header(k, v)
                }

                chain.proceed(requestBuilder.build())
            }

        // Bỏ qua lỗi SSL mismatch tên miền trên các CDN truyền hình địa phương
        trustAllCertificates(okHttpBuilder)

        val okHttpClient = okHttpBuilder.build()

        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Linux; Android 11; BRAVIA 4K) AppleWebKit/537.36")

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        // 2. Extractor Factory hỗ trợ Non-IDR keyframes cho HLS Live TS Streams
        val hlsExtractorFactory = DefaultHlsExtractorFactory(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
            DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS,
            true
        )

        // 3. HlsMediaSourceFactory với setAllowChunklessPreparation(false)
        // Đây là chìa khóa then chốt giúp FPT Play và các luồng HLS AVC/AAC phát mượt trên TV
        val hlsMediaSourceFactory = HlsMediaSource.Factory(dataSourceFactory)
            .setExtractorFactory(hlsExtractorFactory)
            .setAllowChunklessPreparation(false)

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        // 4. LoadControl tối ưu khởi động nhanh, mở kênh xem ngay lập tức
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 2000,
                /* maxBufferMs = */ 10000,
                /* bufferForPlaybackMs = */ 1000,
                /* bufferForPlaybackAfterRebufferMs = */ 1500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(hlsMediaSourceFactory)
            .setLoadControl(loadControl)
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            .build()
            .apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_BUFFERING -> {
                                if (!isRetrying) {
                                    onStateChanged?.invoke(PlaybackState.BUFFERING, null)
                                }
                            }
                            Player.STATE_READY -> {
                                cancelRetry()
                                retryCount = 0
                                onStateChanged?.invoke(PlaybackState.READY, null)
                            }
                            Player.STATE_ENDED -> {
                                scheduleAutoRetry()
                            }
                            Player.STATE_IDLE -> {
                                // Nghỉ
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(tag, "Lỗi phát kênh: ${error.errorCodeName} - ${error.message}")
                        scheduleAutoRetry()
                    }
                })
            }
    }

    /**
     * Bỏ qua xác thực SSL cho các CDN luồng cũ hoặc chứng chỉ trung gian
     */
    @SuppressLint("CustomX509TrustManager")
    private fun trustAllCertificates(builder: OkHttpClient.Builder) {
        try {
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
            )
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            Log.w(tag, "Không thể cài đặt TrustAll SSL", e)
        }
    }

    /**
     * Gắn Player vào PlayerView của Android TV
     */
    fun attachPlayerView(playerView: PlayerView) {
        playerView.player = exoPlayer
    }

    /**
     * Phát kênh TV: Dùng lại ExoPlayer hiện tại, hỗ trợ phân giải luồng dự phòng
     */
    fun playChannel(channel: Channel) {
        cancelRetry()
        currentChannel = channel
        currentStreamUrlIndex = 0
        retryCount = 0

        playCurrentStreamUrl()
    }

    private fun playCurrentStreamUrl() {
        val channel = currentChannel ?: return
        val player = exoPlayer ?: return

        val allUrls = channel.getAllStreamUrls()
        val targetUrl = if (currentStreamUrlIndex in allUrls.indices) {
            allUrls[currentStreamUrlIndex]
        } else {
            channel.streamUrl
        }

        Log.d(tag, "Đang phát [Luồng ${currentStreamUrlIndex + 1}/${allUrls.size}]: $targetUrl")

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(targetUrl))
            .setMediaId(channel.id)
            .build()

        // Xả sạch bộ đệm kênh cũ, nạp kênh mới tức thì
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        onStateChanged?.invoke(PlaybackState.BUFFERING, null)
    }

    /**
     * Tự động thử lại mỗi 4 giây:
     * - Nếu thử 2 lần thất bại và có link dự phòng -> tự động chuyển sang link dự phòng!
     */
    private fun scheduleAutoRetry() {
        val channel = currentChannel ?: return
        if (isRetrying) return

        isRetrying = true
        retryCount++

        val allUrls = channel.getAllStreamUrls()
        val hasBackup = allUrls.size > 1

        val msg = if (hasBackup && currentStreamUrlIndex > 0) {
            "Đang thử luồng dự phòng (${currentStreamUrlIndex + 1}/${allUrls.size})..."
        } else {
            "Đang tải..."
        }

        onStateChanged?.invoke(PlaybackState.RECONNECTING, msg)

        retryRunnable = Runnable {
            if (!isRetrying) return@Runnable

            // Nếu luồng hiện tại thất bại sau 2 lần thử, chuyển sang luồng kế tiếp (nếu có)
            if (retryCount >= 2 && allUrls.size > 1) {
                currentStreamUrlIndex = (currentStreamUrlIndex + 1) % allUrls.size
                retryCount = 0
                Log.d(tag, "Tự động đổi sang luồng dự phòng: ${allUrls[currentStreamUrlIndex]}")
            }

            playCurrentStreamUrl()

            if (isRetrying) {
                mainHandler.postDelayed(retryRunnable!!, 4000)
            }
        }

        mainHandler.postDelayed(retryRunnable!!, 4000)
    }

    /**
     * Hủy ngay retry cũ khi đổi kênh hoặc đóng ứng dụng
     */
    fun cancelRetry() {
        isRetrying = false
        retryRunnable?.let {
            mainHandler.removeCallbacks(it)
        }
        retryRunnable = null
    }

    /**
     * Giải phóng player khi Activity bị destroy
     */
    fun release() {
        cancelRetry()
        exoPlayer?.let {
            it.stop()
            it.release()
        }
        exoPlayer = null
    }
}
