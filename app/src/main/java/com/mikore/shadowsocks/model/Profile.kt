package com.mikore.shadowsocks.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val server: String,
    val serverPort: Int,
    val password: String,
    val method: String, // AES-256-GCM, ChaCha20-Poly1305, etc.
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastConnectedAt: Long? = null
) : Parcelable