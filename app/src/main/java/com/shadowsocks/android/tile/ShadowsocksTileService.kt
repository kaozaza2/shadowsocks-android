package com.shadowsocks.android.tile

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.shadowsocks.android.R
import com.shadowsocks.android.model.ConnectionState
import com.shadowsocks.android.model.Profile
import com.shadowsocks.android.service.ShadowsocksVpnService
import com.shadowsocks.android.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class ShadowsocksTileService : TileService() {
    
    private var vpnService: ShadowsocksVpnService? = null
    private var bound = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as ShadowsocksVpnService.ShadowsocksVpnBinder
            vpnService = binder.getService()
            bound = true
            observeConnectionStatus()
        }
        
        override fun onServiceDisconnected(arg0: ComponentName) {
            bound = false
            vpnService = null
        }
    }
    
    override fun onStartListening() {
        super.onStartListening()
        bindToVpnService()
        updateTile()
    }
    
    override fun onStopListening() {
        super.onStopListening()
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }
    
    override fun onClick() {
        super.onClick()
        
        val currentState = vpnService?.connectionStatus?.value?.state ?: ConnectionState.DISCONNECTED
        
        when (currentState) {
            ConnectionState.DISCONNECTED -> {
                // Check VPN permission first
                val vpnIntent = VpnService.prepare(this)
                if (vpnIntent != null) {
                    // Need permission, open main activity
                    val intent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivityAndCollapse(intent)
                } else {
                    // Connect with last used profile or show profile selection
                    connectToLastProfile()
                }
            }
            ConnectionState.CONNECTED -> {
                vpnService?.disconnect()
            }
            else -> {
                // Do nothing if connecting/disconnecting
            }
        }
    }
    
    private fun bindToVpnService() {
        val intent = Intent(this, ShadowsocksVpnService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)
    }
    
    private fun observeConnectionStatus() {
        vpnService?.let { service ->
            serviceScope.launch {
                service.connectionStatus.collect { status ->
                    updateTile(status.state, status.profile)
                }
            }
        }
    }
    
    private fun updateTile(state: ConnectionState = ConnectionState.DISCONNECTED, profile: Profile? = null) {
        qsTile?.let { tile ->
            when (state) {
                ConnectionState.DISCONNECTED -> {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = getString(R.string.quick_tile_disconnected)
                    tile.icon = Icon.createWithResource(this, R.drawable.ic_vpn_off)
                }
                ConnectionState.CONNECTING -> {
                    tile.state = Tile.STATE_UNAVAILABLE
                    tile.label = getString(R.string.quick_tile_connecting)
                    tile.icon = Icon.createWithResource(this, R.drawable.ic_vpn_connecting)
                }
                ConnectionState.CONNECTED -> {
                    tile.state = Tile.STATE_ACTIVE
                    tile.label = profile?.name ?: getString(R.string.quick_tile_connected)
                    tile.icon = Icon.createWithResource(this, R.drawable.ic_vpn)
                }
                ConnectionState.DISCONNECTING -> {
                    tile.state = Tile.STATE_UNAVAILABLE
                    tile.label = getString(R.string.quick_tile_disconnecting)
                    tile.icon = Icon.createWithResource(this, R.drawable.ic_vpn_off)
                }
                ConnectionState.ERROR -> {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = getString(R.string.quick_tile_error)
                    tile.icon = Icon.createWithResource(this, R.drawable.ic_vpn_error)
                }
            }
            tile.updateTile()
        }
    }
    
    private fun connectToLastProfile() {
        // TODO: Implement logic to get last used profile from database
        // For now, open main activity to select profile
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivityAndCollapse(intent)
    }
}