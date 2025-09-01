package com.shadowsocks.android.service

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
import com.shadowsocks.android.R
import com.shadowsocks.android.model.ConnectionState
import com.shadowsocks.android.model.ConnectionStatus
import com.shadowsocks.android.model.Profile
import com.shadowsocks.android.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ShadowsocksVpnService : VpnService() {
    
    private val binder = ShadowsocksVpnBinder()
    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    
    private val _connectionStatus = MutableStateFlow(
        ConnectionStatus(ConnectionState.DISCONNECTED)
    )
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "shadowsocks_vpn"
        const val ACTION_CONNECT = "com.shadowsocks.android.CONNECT"
        const val ACTION_DISCONNECT = "com.shadowsocks.android.DISCONNECT"
        const val EXTRA_PROFILE = "profile"
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
        }
        return START_STICKY
    }
    
    fun connect(profile: Profile) {
        serviceScope.launch {
            try {
                _connectionStatus.value = ConnectionStatus(ConnectionState.CONNECTING, profile)
                
                // Prepare VPN
                val builder = Builder()
                    .setSession("Shadowsocks")
                    .addAddress("10.0.0.2", 24)
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("8.8.8.8")
                    .addRoute("0.0.0.0", 0)
                
                vpnInterface = builder.establish()
                
                if (vpnInterface != null) {
                    startForeground(NOTIFICATION_ID, createNotification(profile))
                    _connectionStatus.value = ConnectionStatus(
                        ConnectionState.CONNECTED, 
                        profile,
                        System.currentTimeMillis()
                    )
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
            }
        }
    }
    
    fun disconnect() {
        serviceScope.launch {
            _connectionStatus.value = ConnectionStatus(ConnectionState.DISCONNECTING)
            
            vpnInterface?.close()
            vpnInterface = null
            
            stopForeground(STOP_FOREGROUND_REMOVE)
            _connectionStatus.value = ConnectionStatus(ConnectionState.DISCONNECTED)
        }
    }
    
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
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Shadowsocks Connected")
            .setContentText("Connected to ${profile.name}")
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