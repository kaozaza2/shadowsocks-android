package com.shadowsocks.android.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.shadowsocks.android.model.Profile

@Database(
    entities = [Profile::class],
    version = 1,
    exportSchema = false
)
abstract class ShadowsocksDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
}