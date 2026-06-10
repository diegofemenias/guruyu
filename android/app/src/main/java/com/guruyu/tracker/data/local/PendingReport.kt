package com.guruyu.tracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_reports")
data class PendingReport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceUuid: String,
    val latitude: Double,
    val longitude: Double,
    val reportedAt: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)
