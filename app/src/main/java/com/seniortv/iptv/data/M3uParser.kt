package com.seniortv.iptv.data

import com.seniortv.iptv.model.Channel
import java.io.BufferedReader
import java.io.StringReader

object M3uParser {

    /**
     * Trích xuất giá trị thuộc tính an toàn (hỗ trợ cả dấu ngoặc kép " và đơn ')
     * Tối ưu tốc độ phân tích và tránh 100% lỗi escaping dấu ngoặc kép
     */
    private fun extractAttribute(line: String, key: String): String? {
    val singleQuote = 39.toChar() // ASCII 39: Dấu nháy đơn '
    val doubleQuote = 34.toChar() // ASCII 34: Dấu nháy kép "
    val quotes = charArrayOf(doubleQuote, singleQuote)
    for (q in quotes) {
        val prefix = "$key=$q"
        val startIndex = line.indexOf(prefix, ignoreCase = true)
        if (startIndex != -1) {
            val valueStart = startIndex + prefix.length
            val endIndex = line.indexOf(q, valueStart)
            if (endIndex != -1) {
                return line.substring(valueStart, endIndex).trim()
            }
        }
    }
    return null
}

    /**
     * Phân tích nội dung M3U/M3U8 thành danh sách Channel
     */
    fun parse(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val reader = BufferedReader(StringReader(content))

        var line: String?
        var currentTvgId = ""
        var currentName = ""
        var currentLogo: String? = null
        var currentGroup = "Kênh TV"
        var channelIndex = 1

        while (reader.readLine().also { line = it } != null) {
            val trimmed = line?.trim() ?: continue
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("#EXTINF:")) {
                // Parse tvg-id
                currentTvgId = extractAttribute(trimmed, "tvg-id") ?: ""

                // Parse logo
                currentLogo = extractAttribute(trimmed, "tvg-logo")

                // Parse group
                currentGroup = extractAttribute(trimmed, "group-title") ?: "Kênh TV"

                // Parse channel name sau dấu phẩy cuối cùng
                val commaIndex = trimmed.lastIndexOf(',')
                currentName = if (commaIndex != -1 && commaIndex < trimmed.length - 1) {
                    trimmed.substring(commaIndex + 1).trim()
                } else {
                    extractAttribute(trimmed, "tvg-name") ?: "Kênh $channelIndex"
                }
            } else if (!trimmed.startsWith("#")) {
                // Đây là dòng chứa Stream URL
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("rtmp://")) {
                    var streamUrl = trimmed
                    val customHeaders = mutableMapOf<String, String>()

                    // Xử lý cú pháp Pipe Headers quốc tế: URL|User-Agent=...&Referer=...
                    if (streamUrl.contains("|")) {
                        val parts = streamUrl.split("|", limit = 2)
                        streamUrl = parts[0].trim()
                        val headerParams = parts[1].split("&")
                        for (param in headerParams) {
                            val kv = param.split("=", limit = 2)
                            if (kv.size == 2) {
                                customHeaders[kv[0].trim()] = kv[1].trim()
                            }
                        }
                    }

                    // Tự động gán luồng dự phòng (Backup URLs) cho các kênh thiết yếu
                    val backupList = mutableListOf<String>()
                    val upperName = currentName.uppercase()
                    if (upperName.contains("VTV1")) {
                        backupList.add("https://live-a.fptplay53.net/live/media/vtv1/live247-hls-avc/index.m3u8")
                        backupList.add("https://vips-livecdn.fptplay.net/live/media/vtv1/live247-hls-avc/index.m3u8")
                        backupList.add("https://liveh12.vtvprime.vn/hls/VTV1/index.m3u8")
                        backupList.add("https://vtvgolive-failover.vtvdigital.vn/vtvgo/vtv1-manifest.m3u8")
                    } else if (upperName.contains("VTV2")) {
                        backupList.add("https://live-a.fptplay53.net/live/media/vtv2/live247-hls-avc/index.m3u8")
                        backupList.add("https://vips-livecdn.fptplay.net/live/media/vtv2/live247-hls-avc/index.m3u8")
                        backupList.add("https://liveh12.vtvprime.vn/hls/VTV2/index.m3u8")
                    } else if (upperName.contains("VTV3")) {
                        backupList.add("https://live-a.fptplay53.net/live/media/vtv3/live247-hls-avc/index.m3u8")
                        backupList.add("https://vips-livecdn.fptplay.net/live/media/vtv3/live247-hls-avc/index.m3u8")
                        backupList.add("https://liveh12.vtvprime.vn/hls/VTV3/index.m3u8")
                    } else if (upperName.contains("AN NINH") || upperName.contains("ANTV")) {
                        backupList.add("https://liveh12.vtvprime.vn/hls/ANNINHTV/index.m3u8")
                    }

                    val id = if (currentTvgId.isNotEmpty()) currentTvgId else "channel_$channelIndex"
                    val channel = Channel(
                        id = id,
                        number = channelIndex,
                        name = if (currentName.isNotEmpty()) currentName else "Kênh $channelIndex",
                        streamUrl = streamUrl,
                        logoUrl = currentLogo,
                        groupTitle = currentGroup,
                        backupUrls = backupList.filter { it != streamUrl },
                        customHeaders = customHeaders
                    )
                    channels.add(channel)
                    channelIndex++

                    // Reset tạm thời
                    currentTvgId = ""
                    currentName = ""
                    currentLogo = null
                    currentGroup = "Kênh TV"
                }
            }
        }

        return channels
    }
}
