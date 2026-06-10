package com.guruyu.tracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingReportDao {
    @Insert
    suspend fun insert(report: PendingReport): Long

    @Query("SELECT * FROM pending_reports ORDER BY id ASC")
    suspend fun getAll(): List<PendingReport>

    @Query("SELECT COUNT(*) FROM pending_reports")
    suspend fun count(): Int

    @Query("DELETE FROM pending_reports WHERE id = :id")
    suspend fun deleteById(id: Long)
}
