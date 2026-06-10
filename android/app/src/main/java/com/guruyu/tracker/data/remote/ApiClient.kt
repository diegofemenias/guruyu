package com.guruyu.tracker.data.remote

import com.guruyu.tracker.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ApiClient {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val reportAdapter = moshi.adapter(ReportRequest::class.java)
    private val reportResponseAdapter = moshi.adapter(ReportResponse::class.java)
    private val devicesResponseAdapter = moshi.adapter(DevicesResponse::class.java)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun sendReport(request: ReportRequest): ReportResponse {
        val body = reportAdapter.toJson(request).toRequestBody(jsonMediaType)
        val httpRequest = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}api/report.php")
            .addHeader("X-API-Key", BuildConfig.API_KEY)
            .post(body)
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val parsed = reportResponseAdapter.fromJson(raw)
            return parsed ?: ReportResponse(success = false, error = "Respuesta inválida")
        }
    }

    fun fetchDevices(deviceUuid: String): DevicesResponse {
        val httpRequest = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}api/devices.php?device_uuid=$deviceUuid")
            .addHeader("X-API-Key", BuildConfig.API_KEY)
            .get()
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val parsed = devicesResponseAdapter.fromJson(raw)
            return parsed ?: DevicesResponse(success = false, error = "Respuesta inválida")
        }
    }
}
