package com.seniortv.iptv.model

import com.google.gson.annotations.SerializedName

/**
 * Model đại diện cho một kênh truyền hình IPTV
 */
data class Channel(
    @SerializedName("id")
    val id: String,

    @SerializedName("number")
    val number: Int,

    @SerializedName("name")
    val name: String,

    @SerializedName("streamUrl")
    val streamUrl: String,

    @SerializedName("logoUrl")
    val logoUrl: String? = null,

    @SerializedName("groupTitle")
    val groupTitle: String = "Chung",

    @SerializedName("backupUrls")
    val backupUrls: List<String> = emptyList(),

    @SerializedName("customHeaders")
    val customHeaders: Map<String, String> = emptyMap()
) {
    /**
     * Định dạng số kênh 2 chữ số (VD: 01, 02...) cho OSD chữ lớn
     */
    fun formattedNumber(): String {
        return String.format("%02d", number)
    }

    /**
     * Lấy toàn bộ danh sách URL có thể phát (bao gồm luồng chính và luồng dự phòng)
     */
    fun getAllStreamUrls(): List<String> {
        val list = mutableListOf(streamUrl)
        list.addAll(backupUrls.filter { it.isNotBlank() && it != streamUrl })
        return list
    }
}
