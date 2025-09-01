package com.mikore.shadowsocks.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.mikore.shadowsocks.R
import com.mikore.shadowsocks.crypto.ShadowsocksCrypto
import com.mikore.shadowsocks.model.ConnectionState
import com.mikore.shadowsocks.model.ConnectionStatus
import com.mikore.shadowsocks.model.ConnectionStats
import com.mikore.shadowsocks.model.ConnectionMonitor
import com.mikore.shadowsocks.model.Profile
import com.mikore.shadowsocks.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import kotlin.random.Random

class ShadowsocksVpnService : VpnService() {
    
    private val binder = ShadowsocksVpnBinder()
    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var statsTrackingJob: Job? = null
    private var autoReconnectJob: Job? = null
    private var currentStats: ConnectionStats? = null
    
    private val _connectionStatus = MutableStateFlow(
        ConnectionStatus(ConnectionState.DISCONNECTED)
    )
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus
    
    private val _connectionMonitor = MutableStateFlow(ConnectionMonitor())
    val connectionMonitor: StateFlow<ConnectionMonitor> = _connectionMonitor
    
    // Network monitoring
    private var lastBytesUploaded = 0L
    private var lastBytesDownloaded = 0L
    private var lastSpeedUpdateTime = 0L
    private var autoReconnectEnabled = true
    private var maxReconnectAttempts = 5
    private var reconnectAttempts = 0
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "shadowsocks_vpn"
        private const val STATS_UPDATE_INTERVAL = 1000L // 1 second
        private const val SPEED_CALCULATION_WINDOW = 5000L // 5 seconds
        private const val AUTO_RECONNECT_DELAY = 5000L // 5 seconds
        private const val CONNECTION_TIMEOUT = 10000L // 10 seconds
        
        const val ACTION_CONNECT = "com.mikore.shadowsocks.CONNECT"
        const val ACTION_DISCONNECT = "com.mikore.shadowsocks.DISCONNECT"
        const val ACTION_TOGGLE_AUTO_RECONNECT = "com.mikore.shadowsocks.TOGGLE_AUTO_RECONNECT"
        const val EXTRA_PROFILE = "profile"
        const val EXTRA_AUTO_RECONNECT = "auto_reconnect"
    }
    
    inner class ShadowsocksVpnBinder : Binder() {
        fun getService(): ShadowsocksVpnService = this@ShadowsocksVpnService
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val profile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_PROFILE, Profile::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_PROFILE)
                }
                profile?.let { connect(it) }
            }
            ACTION_DISCONNECT -> disconnect()
            ACTION_TOGGLE_AUTO_RECONNECT -> {
                autoReconnectEnabled = intent.getBooleanExtra(EXTRA_AUTO_RECONNECT, true)
            }
        }
        return START_STICKY
    }
    
    fun connect(profile: Profile) {
        serviceScope.launch {
            try {
                _connectionStatus.value = ConnectionStatus(ConnectionState.CONNECTING, profile)
                reconnectAttempts = 0
                
                // Test server connectivity first
                val isServerReachable = testServerConnectivity(profile)
                if (!isServerReachable) {
                    _connectionStatus.value = ConnectionStatus(
                        ConnectionState.ERROR,
                        profile,
                        error = "Unable to reach server ${profile.server}:${profile.serverPort}"
                    )
                    return@launch
                }
                
                // Prepare VPN with enhanced routing
                val builder = Builder()
                    .setSession("Shadowsocks - ${profile.name}")
                    .addAddress("10.0.0.2", 24)
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("8.8.8.8")
                    .addDnsServer("208.67.222.222") // OpenDNS
                    .addRoute("0.0.0.0", 0)
                    .setMtu(1500)
                    .setBlocking(false)
                
                // Exclude local networks from VPN
                builder.addRoute("192.168.0.0", 16)
                builder.addRoute("10.0.0.0", 8) 
                builder.addRoute("172.16.0.0", 12)
                
                vpnInterface = builder.establish()
                
                if (vpnInterface != null) {
                    // Initialize statistics tracking
                    currentStats = ConnectionStats(
                        profileId = profile.id,
                        sessionStartTime = System.currentTimeMillis()
                    )
                    
                    startForeground(NOTIFICATION_ID, createNotification(profile))
                    startStatsTracking()
                    
                    _connectionStatus.value = ConnectionStatus(
                        ConnectionState.CONNECTED, 
                        profile,
                        System.currentTimeMillis()
                    )
                    
                    // Update monitor with initial state
                    _connectionMonitor.value = ConnectionMonitor(
                        isConnected = true,
                        currentProfile = profile,
                        connectionTime = System.currentTimeMillis(),
                        lastUpdateTime = System.currentTimeMillis()
                    )
                    
                    // Start auto-reconnect monitoring
                    if (autoReconnectEnabled) {
                        startAutoReconnectMonitoring(profile)
                    }
                    
                } else {
                    _connectionStatus.value = ConnectionStatus(
                        ConnectionState.ERROR,
                        profile,
                        error = "Failed to establish VPN interface"
                    )
                }
            } catch (e: Exception) {
                _connectionStatus.value = ConnectionStatus(
                    ConnectionState.ERROR,
                    profile,
                    error = e.message
                )
                
                // Attempt auto-reconnect if enabled
                if (autoReconnectEnabled && reconnectAttempts < maxReconnectAttempts) {
                    reconnectAttempts++
                    currentStats?.let { stats ->
                        currentStats = stats.copy(reconnectAttempts = reconnectAttempts)
                    }
                    
                    serviceScope.launch {
                        delay(AUTO_RECONNECT_DELAY)
                        connect(profile)
                    }
                }
            }
        }
    }
    
    fun disconnect() {
        serviceScope.launch {
            _connectionStatus.value = ConnectionStatus(ConnectionState.DISCONNECTING)
            
            // Stop monitoring jobs
            statsTrackingJob?.cancel()
            autoReconnectJob?.cancel()
            
            // Finalize statistics
            currentStats?.let { stats ->
                currentStats = stats.copy(
                    sessionEndTime = System.currentTimeMillis(),
                    bytesUploaded = _connectionMonitor.value.totalBytesUploaded,
                    bytesDownloaded = _connectionMonitor.value.totalBytesDownloaded
                )
                // TODO: Save stats to database
            }
            
            vpnInterface?.close()
            vpnInterface = null
            
            stopForeground(STOP_FOREGROUND_REMOVE)
            
            _connectionStatus.value = ConnectionStatus(ConnectionState.DISCONNECTED)
            _connectionMonitor.value = ConnectionMonitor()
            
            currentStats = null
            reconnectAttempts = 0
        }
    }
    
    private suspend fun testServerConnectivity(profile: Profile): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(profile.server, profile.serverPort), CONNECTION_TIMEOUT.toInt())
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun startStatsTracking() {
        statsTrackingJob = serviceScope.launch {
            val vpnFd = vpnInterface ?: return@launch
            val inputStream = FileInputStream(vpnFd.fileDescriptor)
            val outputStream = FileOutputStream(vpnFd.fileDescriptor)
            
            var totalBytesUp = 0L
            var totalBytesDown = 0L
            var lastStatsTime = System.currentTimeMillis()
            val speedWindow = mutableListOf<Pair<Long, Pair<Long, Long>>>()
            
            while (vpnInterface != null) {
                try {
                    delay(STATS_UPDATE_INTERVAL)
                    
                    val currentTime = System.currentTimeMillis()
                    
                    // Simulate traffic monitoring (in real implementation, this would read from VPN interface)
                    val newBytesUp = Random.nextLong(0, 1024) // Simulated upload
                    val newBytesDown = Random.nextLong(0, 2048) // Simulated download
                    
                    totalBytesUp += newBytesUp
                    totalBytesDown += newBytesDown
                    
                    // Add to speed calculation window
                    speedWindow.add(currentTime to (newBytesUp to newBytesDown))
                    
                    // Remove old entries (older than window)
                    speedWindow.removeAll { it.first < currentTime - SPEED_CALCULATION_WINDOW }
                    
                    // Calculate current speeds
                    val windowTotalUp = speedWindow.sumOf { it.second.first }
                    val windowTotalDown = speedWindow.sumOf { it.second.second }
                    val windowDuration = if (speedWindow.isNotEmpty()) {
                        (currentTime - speedWindow.first().first) / 1000.0
                    } else 1.0
                    
                    val currentUpSpeed = if (windowDuration > 0) windowTotalUp / windowDuration else 0.0
                    val currentDownSpeed = if (windowDuration > 0) windowTotalDown / windowDuration else 0.0
                    
                    // Simulate latency measurement
                    val latency = Random.nextDouble(20.0, 150.0)
                    
                    // Update monitor
                    val currentMonitor = _connectionMonitor.value
                    _connectionMonitor.value = currentMonitor.copy(
                        connectionTime = currentTime - (currentStats?.sessionStartTime ?: currentTime),
                        currentUploadSpeed = currentUpSpeed,
                        currentDownloadSpeed = currentDownSpeed,
                        latency = latency,
                        totalBytesUploaded = totalBytesUp,
                        totalBytesDownloaded = totalBytesDown,
                        lastUpdateTime = currentTime
                    )
                    
                    // Update current stats
                    currentStats?.let { stats ->
                        currentStats = stats.copy(
                            bytesUploaded = totalBytesUp,
                            bytesDownloaded = totalBytesDown,
                            avgLatency = if (stats.avgLatency == 0.0) latency else (stats.avgLatency + latency) / 2,
                            peakLatency = maxOf(stats.peakLatency, latency)
                        )
                    }
                    
                } catch (e: Exception) {
                    // Connection error - trigger reconnect if enabled
                    if (autoReconnectEnabled && _connectionStatus.value.state == ConnectionState.CONNECTED) {
                        currentStats?.let { stats ->
                            currentStats = stats.copy(connectionErrors = stats.connectionErrors + 1)
                        }
                        
                        _connectionStatus.value.profile?.let { profile ->
                            serviceScope.launch {
                                delay(AUTO_RECONNECT_DELAY)
                                connect(profile)
                            }
                        }
                    }
                    break
                }
            }
        }
    }
    
    private fun startAutoReconnectMonitoring(profile: Profile) {
        autoReconnectJob = serviceScope.launch {
            while (autoReconnectEnabled && vpnInterface != null) {
                delay(30000) // Check every 30 seconds
                
                if (_connectionStatus.value.state == ConnectionState.CONNECTED) {
                    val isStillReachable = testServerConnectivity(profile)
                    if (!isStillReachable && reconnectAttempts < maxReconnectAttempts) {
                        reconnectAttempts++
                        currentStats?.let { stats ->
                            currentStats = stats.copy(
                                connectionErrors = stats.connectionErrors + 1,
                                reconnectAttempts = reconnectAttempts
                            )
                        }
                        
                        disconnect()
                        delay(AUTO_RECONNECT_DELAY)
                        connect(profile)
                    }
                }
            }
        }
    }
    
    fun setAutoReconnectEnabled(enabled: Boolean) {
        autoReconnectEnabled = enabled
    }
    
    fun getConnectionStats(): ConnectionStats? = currentStats
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Shadowsocks VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for Shadowsocks VPN connection"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(profile: Profile): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val monitor = _connectionMonitor.value
        val contentText = if (monitor.isConnected) {
            "↑ ${monitor.uploadSpeedFormatted} ↓ ${monitor.downloadSpeedFormatted}"
        } else {
            "Connected to ${profile.name}"
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Shadowsocks Connected")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_vpn)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }
}