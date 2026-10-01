package com.seniortv.iptv.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.seniortv.iptv.R
import com.seniortv.iptv.model.Channel

/**
 * Adapter danh sách kênh hiển thị trong Drawer bên trái
 * Thiết kế chữ to, viền focus sáng rõ ràng cho người cao tuổi
 */
class ChannelAdapter(
    private val onChannelClick: (channel: Channel, position: Int) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder>() {

    var onFocusBottomSettings: (() -> Unit)? = null
    private var channels: List<Channel> = emptyList()
    private var selectedIndex = 0

    fun submitList(newList: List<Channel>, currentPlayingIndex: Int) {
        this.channels = newList
        this.selectedIndex = currentPlayingIndex
        notifyDataSetChanged()
    }

    fun setSelected(position: Int) {
        val old = selectedIndex
        selectedIndex = position
        if (old in channels.indices) notifyItemChanged(old)
        if (selectedIndex in channels.indices) notifyItemChanged(selectedIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false)
        return ChannelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val channel = channels[position]
        holder.bind(channel, position == selectedIndex)
    }

    override fun getItemCount(): Int = channels.size

    inner class ChannelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvNumber: TextView = itemView.findViewById(R.id.tvChannelNumber)
        private val tvName: TextView = itemView.findViewById(R.id.tvChannelName)
        private val ivLogo: ImageView = itemView.findViewById(R.id.ivChannelLogo)
        private val activeDot: View = itemView.findViewById(R.id.viewActiveDot)

        init {
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION && pos in channels.indices) {
                    onChannelClick(channels[pos], pos)
                }
            }

            // Hiệu ứng phóng to nhẹ khi Remote focus tới (rất dễ nhìn trên TV)
            itemView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    itemView.animate().scaleX(1.04f).scaleY(1.04f).setDuration(120).start()
                    itemView.setBackgroundResource(R.drawable.bg_channel_focused)
                } else {
                    itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                    itemView.setBackgroundResource(R.drawable.bg_channel_normal)
                }
            }

            // Khi ở kênh cuối cùng và bấm XUỐNG -> Tự động chuyển focus xuống mục Cài đặt ở đáy
            itemView.setOnKeyListener { _, keyCode, keyEvent ->
                if (keyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    val pos = bindingAdapterPosition
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN && pos == channels.size - 1) {
                        onFocusBottomSettings?.invoke()
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }

        fun bind(channel: Channel, isPlaying: Boolean) {
            tvNumber.text = channel.formattedNumber()
            tvName.text = channel.name

            // Đang phát thì hiện chấm xanh nổi bật
            activeDot.visibility = if (isPlaying) View.VISIBLE else View.GONE
            if (isPlaying) {
                tvName.setTextColor(Color.parseColor("#38BDF8"))
            } else {
                tvName.setTextColor(Color.WHITE)
            }

            // Tải logo an toàn tuyệt đối với Glide:
            // Nếu URL hỏng / 404 / mất kết nối -> hiển thị icon TV mặc định, KHÔNG crash app
            if (!channel.logoUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(channel.logoUrl)
                    .placeholder(R.drawable.ic_tv_placeholder)
                    .error(R.drawable.ic_tv_placeholder)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(ivLogo)
            } else {
                ivLogo.setImageResource(R.drawable.ic_tv_placeholder)
            }
        }
    }
}
