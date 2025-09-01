package com.shadowsocks.android.database

import androidx.room.*
import androidx.lifecycle.LiveData
import com.shadowsocks.android.model.ConnectionStats
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionStatsDao {
    
    @Query("SELECT * FROM connection_stats ORDER BY sessionStartTime DESC")
    fun getAllStats(): Flow<List<ConnectionStats>>
    
    @Query("SELECT * FROM connection_stats WHERE profileId = :profileId ORDER BY sessionStartTime DESC")
    fun getStatsForProfile(profileId: Long): Flow<List<ConnectionStats>>
    
    @Query("SELECT * FROM connection_stats WHERE id = :id")
    suspend fun getStatsById(id: Long): ConnectionStats?
    
    @Query("SELECT * FROM connection_stats WHERE sessionEndTime IS NULL ORDER BY sessionStartTime DESC LIMIT 1")
    suspend fun getCurrentSession(): ConnectionStats?
    
    @Query("SELECT SUM(bytesUploaded + bytesDownloaded) FROM connection_stats WHERE profileId = :profileId")
    suspend fun getTotalBytesForProfile(profileId: Long): Long?
    
    @Query("SELECT SUM(bytesUploaded + bytesDownloaded) FROM connection_stats")
    suspend fun getTotalBytes(): Long?
    
    @Query("SELECT COUNT(*) FROM connection_stats WHERE profileId = :profileId")
    suspend fun getConnectionCountForProfile(profileId: Long): Int
    
    @Query("SELECT AVG(avgLatency) FROM connection_stats WHERE profileId = :profileId AND avgLatency > 0")
    suspend fun getAverageLatencyForProfile(profileId: Long): Double?
    
    @Query("""
        SELECT * FROM connection_stats 
        WHERE sessionStartTime >= :startTime AND sessionEndTime <= :endTime 
        ORDER BY sessionStartTime DESC
    """)
    fun getStatsInDateRange(startTime: Long, endTime: Long): Flow<List<ConnectionStats>>
    
    @Query("""
        SELECT 
            DATE(sessionStartTime / 1000, 'unixepoch') as date,
            SUM(bytesUploaded + bytesDownloaded) as totalBytes,
            COUNT(*) as connectionCount,
            AVG(avgLatency) as avgLatency
        FROM connection_stats 
        WHERE sessionStartTime >= :startTime
        GROUP BY DATE(sessionStartTime / 1000, 'unixepoch')
        ORDER BY date DESC
    """)
    fun getDailyStats(startTime: Long): Flow<List<DailyStats>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStats(stats: ConnectionStats): Long
    
    @Update
    suspend fun updateStats(stats: ConnectionStats)
    
    @Delete
    suspend fun deleteStats(stats: ConnectionStats)
    
    @Query("DELETE FROM connection_stats WHERE profileId = :profileId")
    suspend fun deleteStatsForProfile(profileId: Long)
    
    @Query("DELETE FROM connection_stats WHERE sessionStartTime < :beforeTime")
    suspend fun deleteStatsOlderThan(beforeTime: Long)
    
    @Query("DELETE FROM connection_stats")
    suspend fun deleteAllStats()
}

/**
 * Data class for aggregated daily statistics
 */
data class DailyStats(
    val date: String,
    val totalBytes: Long,
    val connectionCount: Int,
    val avgLatency: Double
)