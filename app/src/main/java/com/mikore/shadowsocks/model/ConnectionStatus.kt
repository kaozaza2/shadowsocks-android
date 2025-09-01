package com.mikore.shadowsocks.model

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

data class ConnectionStatus(
    val state: ConnectionState,
    val profile: Profile? = null,
    val connectedTime: Long? = null,
    val bytesReceived: Long = 0,
    val bytesSent: Long = 0,
    val error: String? = null
)