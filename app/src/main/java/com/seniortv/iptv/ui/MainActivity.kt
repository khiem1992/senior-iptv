package com.seniortv.iptv.ui

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.seniortv.iptv.R
import com.seniortv.iptv.data.PlaylistRepository
import com.seniortv.iptv.model.Channel
import com.seniortv.iptv.player.PlayerManager
import kotlinx.coroutines.launch

/**
 * MainActivity tối ưu tuyệt đối cho người lớn tuổi sử dụng Android TV / Google TV.
 * Xử lý D-Pad LÊN, XUỐNG, OK, BACK chính xác, không giật lag.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var playerManager: PlayerManager
    private lateinit var channelAdapter: ChannelAdapter

    // Views
    private lateinit var playerView: androidx.media3.ui.PlayerView
    private lateinit var drawerLayout: View
    private lateinit var rvChannels: RecyclerView
    private lateinit var btnSettings: View
    private lateinit var osdLayout: View
    private lateinit var tvOsdNumber: TextView
    private lateinit var tvOsdName: TextView
    private lateinit var tvReconnecting: View
    private lateinit var tvReconnectingMessage: TextView
    private lateinit var pbBuffering: ProgressBar

    // Trạng thái kênh
    private var channels: List<Channel> = emptyList()
    private var currentChannelIndex: Int = 0

    // Handler tự ẩn OSD sau 3 giây
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideOsdRunnable = Runnable {
        osdLayout.animate().alpha(0f).setDuration(250).withEndAction {
            osdLayout.visibility = View.GONE
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Giữ màn hình luôn sáng trên TV
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        hideSystemUI()
        initViews()
        initPlayerAndData()
    }

    private fun initViews() {
        playerView = findViewById(R.id.playerView)
        drawerLayout = findViewById(R.id.channelDrawer)
        rvChannels = findViewById(R.id.rvChannels)
        btnSettings = findViewById(R.id.btnSettings)
        osdLayout = findViewById(R.id.osdLayout)
        tvOsdNumber = findViewById(R.id.tvOsdNumber)
        tvOsdName = findViewById(R.id.tvOsdName)
        tvReconnecting = findViewById(R.id.tvReconnecting)
        tvReconnectingMessage = findViewById(R.id.tvReconnectingMessage)
        pbBuffering = findViewById(R.id.pbBuffering)

        playlistRepository = PlaylistRepository(this)
        playerManager = PlayerManager(this)
        playerManager.attachPlayerView(playerView)

        // Cài đặt RecyclerView danh sách kênh
        channelAdapter = ChannelAdapter { channel, position ->
            // Khi nhấn chọn kênh trong Drawer: Đóng Drawer và phát ngay trên Player hiện tại
            currentChannelIndex = position
            playlistRepository.saveLastPlayedIndex(position)
            channelAdapter.setSelected(position)
            closeDrawer()
            playCurrentChannel()
        }
        channelAdapter.onFocusBottomSettings = {
            btnSettings.requestFocus()
        }

        rvChannels.layoutManager = LinearLayoutManager(this)
        rvChannels.adapter = channelAdapter
        rvChannels.setHasFixedSize(true)

        // Nút Cài đặt ở cuối danh sách kênh: Hỗ trợ Remote TV LÊN / XUỐNG / OK mượt mà
        btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        btnSettings.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                btnSettings.animate().scaleX(1.02f).scaleY(1.02f).setDuration(120).start()
                btnSettings.setBackgroundResource(R.drawable.bg_channel_focused)
            } else {
                btnSettings.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                btnSettings.setBackgroundResource(R.drawable.bg_channel_normal)
            }
        }

        btnSettings.setOnKeyListener { _, keyCode, keyEvent ->
            if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        // Bấm LÊN từ nút Cài đặt -> Quay lại kênh cuối cùng của danh sách
                        val lastPos = channels.size - 1
                        if (lastPos >= 0) {
                            rvChannels.scrollToPosition(lastPos)
                            rvChannels.post {
                                rvChannels.findViewHolderForAdapterPosition(lastPos)?.itemView?.requestFocus()
                            }
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Bấm XUỐNG từ nút Cài đặt -> Vòng lên kênh đầu tiên (01)
                        if (channels.isNotEmpty()) {
                            rvChannels.scrollToPosition(0)
                            rvChannels.post {
                                rvChannels.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                            }
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        showSettingsDialog()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        // Lắng nghe trạng thái Player
        playerManager.onStateChanged = { state, message ->
            runOnUiThread {
                when (state) {
                    PlayerManager.PlaybackState.BUFFERING -> {
                        pbBuffering.visibility = View.VISIBLE
                        tvReconnecting.visibility = View.GONE
                    }
                    PlayerManager.PlaybackState.READY -> {
                        pbBuffering.visibility = View.GONE
                        tvReconnecting.visibility = View.GONE
                    }
                    PlayerManager.PlaybackState.RECONNECTING -> {
                        pbBuffering.visibility = View.GONE
                        tvReconnecting.visibility = View.VISIBLE
                        tvReconnectingMessage.text = message ?: "Đang tải..."
                    }
                    PlayerManager.PlaybackState.ERROR -> {
                        pbBuffering.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun initPlayerAndData() {
        // 1. Đọc cache local trước để mở app là phát ngay lập tức
        val cached = playlistRepository.getCachedChannels()
        if (cached.isNotEmpty()) {
            channels = cached
            currentChannelIndex = playlistRepository.getLastPlayedIndex().coerceIn(0, channels.size - 1)
            channelAdapter.submitList(channels, currentChannelIndex)
            playCurrentChannel()
        } else {
            // Lần đầu mở app chưa có cache: tự động tải M3U mặc định
            loadPlaylistFromNetwork(playlistRepository.getM3uUrl())
        }
    }

    /**
     * Tải M3U từ mạng và cập nhật cache
     */
    private fun loadPlaylistFromNetwork(url: String, onFinished: ((Boolean) -> Unit)? = null) {
        pbBuffering.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = playlistRepository.fetchAndCachePlaylist(url)
            pbBuffering.visibility = View.GONE
            if (result.isSuccess) {
                channels = result.getOrNull() ?: emptyList()
                if (channels.isNotEmpty()) {
                    currentChannelIndex = playlistRepository.getLastPlayedIndex().coerceIn(0, channels.size - 1)
                    channelAdapter.submitList(channels, currentChannelIndex)
                    playCurrentChannel()
                    onFinished?.invoke(true)
                }
            } else {
                Toast.makeText(this@MainActivity, "Không tải được danh sách kênh mới. Đang dùng dữ liệu cũ.", Toast.LENGTH_LONG).show()
                onFinished?.invoke(false)
            }
        }
    }

    /**
     * Phát kênh tại vị trí hiện tại: Dùng lại ExoPlayer, không tạo mới
     */
    private fun playCurrentChannel() {
        if (channels.isEmpty()) return
        val channel = channels[currentChannelIndex]
        playlistRepository.saveLastPlayedIndex(currentChannelIndex)
        playerManager.playChannel(channel)
        showOsd(channel)
    }

    /**
     * Hiển thị OSD số kênh + tên kênh chữ lớn, tự tắt sau 3 giây
     */
    private fun showOsd(channel: Channel) {
        mainHandler.removeCallbacks(hideOsdRunnable)
        tvOsdNumber.text = channel.formattedNumber()
        tvOsdName.text = channel.name

        osdLayout.alpha = 1f
        osdLayout.visibility = View.VISIBLE

        // Tự tắt OSD sau 3 giây
        mainHandler.postDelayed(hideOsdRunnable, 3000)
    }

    /**
     * Mở Drawer danh sách kênh bên trái (Focus trực tiếp kênh đang phát)
     */
    private fun openDrawer() {
        if (drawerLayout.isVisible) return
        drawerLayout.visibility = View.VISIBLE
        drawerLayout.translationX = -drawerLayout.width.toFloat()
        drawerLayout.animate().translationX(0f).setDuration(160).start()

        // Đặt ngay vị trí focus trực tiếp vào kênh đang phát, KHÔNG chạy cuộn từ đầu danh sách
        (rvChannels.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(currentChannelIndex, 60)
        channelAdapter.setSelected(currentChannelIndex)
        rvChannels.post {
            val holder = rvChannels.findViewHolderForAdapterPosition(currentChannelIndex)
            holder?.itemView?.requestFocus() ?: rvChannels.requestFocus()
        }
    }

    /**
     * Đóng Drawer danh sách kênh
     */
    private fun closeDrawer() {
        if (!drawerLayout.isVisible) return
        drawerLayout.animate().translationX(-drawerLayout.width.toFloat()).setDuration(180).withEndAction {
            drawerLayout.visibility = View.GONE
        }.start()
    }

    /**
     * XỬ LÝ REMOTE ĐIỀU KHIỂN ANDROID TV
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event)
        }

        val keyCode = event.keyCode

        // Khi Drawer đang mở:
        // LÊN/XUỐNG chỉ di chuyển focus trong Drawer, KHÔNG được đổi kênh
        if (drawerLayout.isVisible) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                closeDrawer()
                return true
            }
            // Cho phép Android focus framework tự điều khiển focus trong danh sách
            return super.dispatchKeyEvent(event)
        }

        // Khi Drawer ĐANG ĐÓNG:
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                // Chuyển kênh trước đó
                if (channels.isNotEmpty()) {
                    currentChannelIndex = if (currentChannelIndex > 0) currentChannelIndex - 1 else channels.size - 1
                    playCurrentChannel()
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                // Chuyển kênh kế tiếp
                if (channels.isNotEmpty()) {
                    currentChannelIndex = if (currentChannelIndex < channels.size - 1) currentChannelIndex + 1 else 0
                    playCurrentChannel()
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                // Nhấn OK khi đang xem -> Mở Drawer danh sách kênh bên trái
                openDrawer()
                return true
            }

            KeyEvent.KEYCODE_BACK -> {
                // Nhấn BACK khi đang xem -> Hiện hộp thoại xác nhận thoát
                showExitDialog()
                return true
            }

            KeyEvent.KEYCODE_MENU -> {
                // Nhấn MENU -> Mở cài đặt
                showSettingsDialog()
                return true
            }
        }

        return super.dispatchKeyEvent(event)
    }

    /**
     * Hộp thoại thoát ứng dụng chữ to, rõ ràng
     */
    private fun showExitDialog() {
        val dialog = Dialog(this, R.style.TvDialogTheme)
        dialog.setContentView(R.layout.dialog_exit)

        val btnYes = dialog.findViewById<Button>(R.id.btnExitYes)
        val btnNo = dialog.findViewById<Button>(R.id.btnExitNo)

        btnYes.setOnClickListener {
            dialog.dismiss()
            finishAffinity()
        }

        btnNo.setOnClickListener {
            dialog.dismiss()
        }

        btnYes.requestFocus()
        dialog.show()
    }

    /**
     * Hộp thoại Cài đặt: URL M3U và nút Cập nhật kênh
     */
    private fun showSettingsDialog() {
        val dialog = Dialog(this, R.style.TvDialogTheme)
        dialog.setContentView(R.layout.dialog_settings)

        val edtUrl = dialog.findViewById<EditText>(R.id.edtM3uUrl)
        val btnUpdate = dialog.findViewById<Button>(R.id.btnUpdatePlaylist)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancelSettings)
        val pbUpdating = dialog.findViewById<ProgressBar>(R.id.pbUpdating)

        edtUrl.setText(playlistRepository.getM3uUrl())

        btnUpdate.setOnClickListener {
            val newUrl = edtUrl.text.toString().trim()
            if (newUrl.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập link M3U", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnUpdate.isEnabled = false
            pbUpdating.visibility = View.VISIBLE

            loadPlaylistFromNetwork(newUrl) { success ->
                pbUpdating.visibility = View.GONE
                btnUpdate.isEnabled = true
                if (success) {
                    Toast.makeText(this, "Đã cập nhật danh sách kênh thành công!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    closeDrawer()
                } else {
                    Toast.makeText(this, "Cập nhật thất bại. Vui lòng kiểm tra lại link.", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnUpdate.requestFocus()
        dialog.show()
    }

    /**
     * Ẩn thanh điều hướng và thanh trạng thái toàn màn hình
     */
    private fun hideSystemUI() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        playerManager.exoPlayer?.play()
    }

    override fun onPause() {
        super.onPause()
        playerManager.exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(hideOsdRunnable)
        playerManager.release()
    }
}
