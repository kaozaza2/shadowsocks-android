package com.shadowsocks.android.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.shadowsocks.android.model.Profile
import com.shadowsocks.android.model.ConnectionStats

@Database(
    entities = [Profile::class, ConnectionStats::class],
    version = 2,
    exportSchema = false
)
abstract class ShadowsocksDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun connectionStatsDao(): ConnectionStatsDao
}