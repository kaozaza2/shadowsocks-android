package com.shadowsocks.android.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Connection statistics for monitoring data usage and performance
 */
@Entity(tableName = "connection_stats")
data class ConnectionStats(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: Long,
    val sessionStartTime: Long = System.currentTimeMillis(),
    val sessionEndTime: Long? = null,
    val bytesUploaded: Long = 0,
    val bytesDownloaded: Long = 0,
    val packetsUploaded: Long = 0,
    val packetsDownloaded: Long = 0,
    val avgLatency: Double = 0.0,
    val peakLatency: Double = 0.0,
    val connectionErrors: Int = 0,
    val reconnectAttempts: Int = 0
) {
    val totalBytes: Long get() = bytesUploaded + bytesDownloaded
    val totalPackets: Long get() = packetsUploaded + packetsDownloaded
    val sessionDuration: Long get() = (sessionEndTime ?: System.currentTimeMillis()) - sessionStartTime
    
    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> String.format("%.2f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> String.format("%.2f MB", bytes / 1_048_576.0)
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
    
    fun formatDuration(): String {
        val duration = sessionDuration / 1000
        val hours = duration / 3600
        val minutes = (duration % 3600) / 60
        val seconds = duration % 60
        
        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
            minutes > 0 -> String.format("%d:%02d", minutes, seconds)
            else -> "${seconds}s"
        }
    }
}

/**
 * Real-time connection monitoring data
 */
data class ConnectionMonitor(
    val isConnected: Boolean = false,
    val currentProfile: Profile? = null,
    val connectionTime: Long = 0,
    val currentUploadSpeed: Double = 0.0, // bytes per second
    val currentDownloadSpeed: Double = 0.0, // bytes per second
    val latency: Double = 0.0, // milliseconds
    val totalBytesUploaded: Long = 0,
    val totalBytesDownloaded: Long = 0,
    val lastUpdateTime: Long = System.currentTimeMillis()
) {
    fun formatSpeed(bytesPerSecond: Double): String {
        return when {
            bytesPerSecond >= 1_048_576 -> String.format("%.2f MB/s", bytesPerSecond / 1_048_576)
            bytesPerSecond >= 1024 -> String.format("%.2f KB/s", bytesPerSecond / 1024)
            else -> String.format("%.0f B/s", bytesPerSecond)
        }
    }
    
    val uploadSpeedFormatted: String get() = formatSpeed(currentUploadSpeed)
    val downloadSpeedFormatted: String get() = formatSpeed(currentDownloadSpeed)
    
    val latencyFormatted: String get() = when {
        latency >= 1000 -> String.format("%.2f s", latency / 1000)
        else -> String.format("%.0f ms", latency)
    }
}

/**
 * Network quality assessment
 */
enum class NetworkQuality(val displayName: String, val color: androidx.compose.ui.graphics.Color) {
    EXCELLENT("Excellent", androidx.compose.ui.graphics.Color.Green),
    GOOD("Good", androidx.compose.ui.graphics.Color(0xFF8BC34A)),
    FAIR("Fair", androidx.compose.ui.graphics.Color(0xFFFF9800)),
    POOR("Poor", androidx.compose.ui.graphics.Color(0xFFFF5722)),
    UNKNOWN("Unknown", androidx.compose.ui.graphics.Color.Gray);
    
    companion object {
        fun fromLatency(latency: Double): NetworkQuality = when {
            latency <= 50 -> EXCELLENT
            latency <= 100 -> GOOD
            latency <= 200 -> FAIR
            latency <= 500 -> POOR
            else -> UNKNOWN
        }
    }
}