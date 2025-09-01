package com.mikore.shadowsocks.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mikore.shadowsocks.model.Profile
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    
    @Query("SELECT * FROM profiles ORDER BY createdAt DESC")
    fun getAllProfiles(): Flow<List<Profile>>
    
    @Query("SELECT * FROM profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfile(): Profile?
    
    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileById(id: Long): Profile?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: Profile): Long
    
    @Update
    suspend fun updateProfile(profile: Profile)
    
    @Delete
    suspend fun deleteProfile(profile: Profile)
    
    @Query("UPDATE profiles SET isActive = 0")
    suspend fun deactivateAllProfiles()
    
    @Query("UPDATE profiles SET isActive = 1 WHERE id = :id")
    suspend fun activateProfile(id: Long)
    
    @Query("UPDATE profiles SET lastConnectedAt = :timestamp WHERE id = :id")
    suspend fun updateLastConnectedTime(id: Long, timestamp: Long)
}