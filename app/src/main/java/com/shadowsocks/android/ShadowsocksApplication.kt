package com.shadowsocks.android

import android.app.Application
import androidx.room.Room
import com.shadowsocks.android.database.ShadowsocksDatabase

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