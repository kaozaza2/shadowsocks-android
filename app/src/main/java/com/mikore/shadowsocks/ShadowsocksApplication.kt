package com.mikore.shadowsocks

import android.app.Application
import androidx.room.Room
import com.mikore.shadowsocks.database.ShadowsocksDatabase

class ShadowsocksApplication : Application() {
    
    val database by lazy {
        Room.databaseBuilder(
            this,
            ShadowsocksDatabase::class.java,
            "shadowsocks_database"
        ).build()
    }
    
    override fun onCreate() {
        super.onCreate()
    }
}