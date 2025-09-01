package com.shadowsocks.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shadowsocks.android.R
import com.shadowsocks.android.model.ConnectionMonitor
import com.shadowsocks.android.model.ConnectionState
import com.shadowsocks.android.model.ConnectionStatus
import com.shadowsocks.android.model.Profile
import com.shadowsocks.android.ui.theme.ShadowsocksAndroidTheme

@Composable
fun MainScreen(
    connectionStatus: ConnectionStatus = ConnectionStatus(ConnectionState.DISCONNECTED),
    connectionMonitor: ConnectionMonitor = ConnectionMonitor(),
    profiles: List<Profile> = emptyList(),
    onConnect: (Profile) -> Unit = {},
    onDisconnect: () -> Unit = {},
    onNavigateToProfiles: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
        
        // Connection Status Card
        ConnectionStatusCard(
            connectionStatus = connectionStatus,
            connectionMonitor = connectionMonitor,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            profiles = profiles
        )
        
        // Quick Stats Card (when connected)
        if (connectionStatus.state == ConnectionState.CONNECTED && connectionMonitor.isConnected) {
            QuickStatsCard(
                monitor = connectionMonitor,
                onViewDetails = onNavigateToStats
            )
        }
        
        // Navigation Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NavigationCard(
                title = "Profiles",
                subtitle = "${profiles.size} configured",
                icon = Icons.Default.List,
                onClick = onNavigateToProfiles,
                modifier = Modifier.weight(1f)
            )
            
            NavigationCard(
                title = "Statistics",
                subtitle = "View usage data",
                icon = Icons.Default.Analytics,
                onClick = onNavigateToStats,
                modifier = Modifier.weight(1f)
            )
        }
        
        // Welcome Message (when disconnected and no profiles)
        if (connectionStatus.state == ConnectionState.DISCONNECTED && profiles.isEmpty()) {
            WelcomeCard(onGetStarted = onNavigateToProfiles)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionStatusCard(
    connectionStatus: ConnectionStatus,
    connectionMonitor: ConnectionMonitor,
    onConnect: (Profile) -> Unit,
    onDisconnect: () -> Unit,
    profiles: List<Profile>
) {
    var showProfileSelector by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (connectionStatus.state) {
                ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> MaterialTheme.colorScheme.secondaryContainer
                ConnectionState.ERROR -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Icon
            val (icon, iconColor) = when (connectionStatus.state) {
                ConnectionState.CONNECTED -> Icons.Default.CheckCircle to Color.Green
                ConnectionState.CONNECTING -> Icons.Default.HourglassEmpty to MaterialTheme.colorScheme.primary
                ConnectionState.DISCONNECTING -> Icons.Default.HourglassEmpty to MaterialTheme.colorScheme.secondary
                ConnectionState.ERROR -> Icons.Default.Error to Color.Red
                else -> Icons.Default.VpnLock to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            }
            
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = iconColor
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Status Text
            Text(
                text = when (connectionStatus.state) {
                    ConnectionState.CONNECTED -> "Connected"
                    ConnectionState.CONNECTING -> "Connecting..."
                    ConnectionState.DISCONNECTING -> "Disconnecting..."
                    ConnectionState.ERROR -> "Connection Error"
                    else -> "Disconnected"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            // Profile Info (when connected)
            connectionStatus.profile?.let { profile ->
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "${profile.server}:${profile.serverPort}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
            
            // Error Message
            connectionStatus.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Action Button
            when (connectionStatus.state) {
                ConnectionState.CONNECTED -> {
                    Button(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Disconnect")
                    }
                }
                ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                    if (profiles.isNotEmpty()) {
                        Button(
                            onClick = {
                                if (profiles.size == 1) {
                                    onConnect(profiles.first())
                                } else {
                                    showProfileSelector = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (profiles.size == 1) "Connect to ${profiles.first().name}" else "Connect")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { /* Navigate to add profile */ },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Server Profile")
                        }
                    }
                }
                else -> {
                    Button(
                        onClick = { },
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(connectionStatus.state.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }
        }
    }
    
    // Profile Selector Dialog
    if (showProfileSelector) {
        AlertDialog(
            onDismissRequest = { showProfileSelector = false },
            title = { Text("Select Profile") },
            text = {
                Column {
                    profiles.forEach { profile ->
                        TextButton(
                            onClick = {
                                onConnect(profile)
                                showProfileSelector = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "${profile.server}:${profile.serverPort}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProfileSelector = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun QuickStatsCard(
    monitor: ConnectionMonitor,
    onViewDetails: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Connection Info",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                TextButton(onClick = onViewDetails) {
                    Text("View Details")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "Upload",
                    value = monitor.uploadSpeedFormatted,
                    icon = Icons.Default.Upload
                )
                
                StatItem(
                    label = "Download", 
                    value = monitor.downloadSpeedFormatted,
                    icon = Icons.Default.Download
                )
                
                StatItem(
                    label = "Latency",
                    value = monitor.latencyFormatted,
                    icon = Icons.Default.Speed
                )
            }
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun NavigationCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun WelcomeCard(onGetStarted: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.welcome_message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onGetStarted) {
                Text(stringResource(R.string.get_started))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    ShadowsocksAndroidTheme {
        MainScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenConnectedPreview() {
    ShadowsocksAndroidTheme {
        val sampleProfile = Profile(
            id = 1,
            name = "My Server",
            server = "example.com",
            serverPort = 8388,
            password = "password",
            method = "AES-256-GCM"
        )
        
        MainScreen(
            connectionStatus = ConnectionStatus(
                ConnectionState.CONNECTED,
                sampleProfile,
                System.currentTimeMillis()
            ),
            connectionMonitor = ConnectionMonitor(
                isConnected = true,
                currentProfile = sampleProfile,
                connectionTime = 120000,
                currentUploadSpeed = 1024.0 * 25,
                currentDownloadSpeed = 1024.0 * 150,
                latency = 75.0,
                totalBytesUploaded = 1024 * 1024 * 10,
                totalBytesDownloaded = 1024 * 1024 * 50
            ),
            profiles = listOf(sampleProfile)
        )
    }
}