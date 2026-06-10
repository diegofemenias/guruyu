package com.guruyu.tracker.data

import android.content.Context
import com.guruyu.tracker.GuruyuApp
import com.guruyu.tracker.data.local.PendingReport
import com.guruyu.tracker.data.remote.ApiClient
import com.guruyu.tracker.data.remote.ReportRequest
import com.guruyu.tracker.data.remote.ReportResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class LocationRepository(context: Context) {
    private val app = context.applicationContext as GuruyuApp
    private val dao = app.database.pendingReportDao()
    private val apiClient = ApiClient()
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    suspend fun enqueueReport(
        deviceUuid: String,
        latitude: Double,
        longitude: Double,
        reportedAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),
    ): Long = withContext(Dispatchers.IO) {
        dao.insert(
            PendingReport(
                deviceUuid = deviceUuid,
                latitude = latitude,
                longitude = longitude,
                reportedAt = reportedAt.format(formatter),
            ),
        )
    }

    suspend fun pendingCount(): Int = withContext(Dispatchers.IO) {
        dao.count()
    }

    suspend fun flushQueue(deviceUuid: String): FlushResult = withContext(Dispatchers.IO) {
        val pending = dao.getAll()
        var enabled = true
        var lastSuccess: String? = null
        var lastError: String? = null

        for (report in pending) {
            if (report.deviceUuid != deviceUuid) {
                continue
            }

            try {
                val response = apiClient.sendReport(
                    ReportRequest(
                        deviceUuid = report.deviceUuid,
                        latitude = report.latitude,
                        longitude = report.longitude,
                        reportedAt = report.reportedAt,
                    ),
                )

                if (response.enabled) {
                    enabled = true
                    if (response.success) {
                        dao.deleteById(report.id)
                        lastSuccess = "${report.latitude}, ${report.longitude} @ ${report.reportedAt}"
                    } else {
                        lastError = response.error ?: response.message
                    }
                } else {
                    enabled = false
                    lastError = response.message ?: "Dispositivo no habilitado"
                    break
                }
            } catch (e: Exception) {
                lastError = e.message
                break
            }
        }

        FlushResult(
            enabled = enabled,
            lastSuccess = lastSuccess,
            lastError = lastError,
            remaining = dao.count(),
        )
    }

    suspend fun sendImmediate(
        deviceUuid: String,
        latitude: Double,
        longitude: Double,
        reportedAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),
    ): ReportResult = withContext(Dispatchers.IO) {
        val formatted = reportedAt.format(formatter)
        try {
            val response = apiClient.sendReport(
                ReportRequest(deviceUuid, latitude, longitude, formatted),
            )

            if (!response.enabled) {
                return@withContext ReportResult(false, response.enabled, response.message, dao.count())
            }

            if (response.success) {
                ReportResult(true, true, response.message, dao.count())
            } else {
                enqueueReport(deviceUuid, latitude, longitude, reportedAt)
                ReportResult(false, true, response.error ?: response.message, dao.count())
            }
        } catch (e: Exception) {
            enqueueReport(deviceUuid, latitude, longitude, reportedAt)
            ReportResult(false, true, e.message, dao.count())
        }
    }

    data class FlushResult(
        val enabled: Boolean,
        val lastSuccess: String?,
        val lastError: String?,
        val remaining: Int,
    )

    data class ReportResult(
        val success: Boolean,
        val enabled: Boolean,
        val message: String?,
        val queueCount: Int,
    )
}
