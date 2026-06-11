package com.guruyu.tracker.data.remote

import com.squareup.moshi.Json

data class ReportRequest(
    @Json(name = "device_uuid") val deviceUuid: String,
    val latitude: Double,
    val longitude: Double,
    @Json(name = "reported_at") val reportedAt: String,
)

data class ReportResponse(
    val success: Boolean = false,
    val enabled: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

data class RemoteDevice(
    val uuid: String,
    @Json(name = "display_name") val displayName: String,
    val latitude: Double,
    val longitude: Double,
    @Json(name = "reported_at") val reportedAt: String,
    @Json(name = "is_stale") val isStale: Boolean,
)

data class DevicesResponse(
    val success: Boolean = false,
    @Json(name = "self_enabled") val selfEnabled: Boolean? = null,
    @Json(name = "stale_minutes") val staleMinutes: Int = 2,
    val devices: List<RemoteDevice> = emptyList(),
    val error: String? = null,
)

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    @Json(name = "reported_at") val reportedAt: String,
)

data class DeviceTrack(
    val uuid: String,
    @Json(name = "display_name") val displayName: String,
    val points: List<TrackPoint> = emptyList(),
)

data class TracksResponse(
    val success: Boolean = false,
    @Json(name = "track_points") val trackPoints: Int = 50,
    val tracks: List<DeviceTrack> = emptyList(),
    val error: String? = null,
)
