package com.guruyu.tracker.data

import android.content.Context

class LastReportedLocationStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): Pair<Double, Double>? {
        if (!prefs.contains(KEY_LATITUDE)) {
            return null
        }
        return prefs.getFloat(KEY_LATITUDE, 0f).toDouble() to
            prefs.getFloat(KEY_LONGITUDE, 0f).toDouble()
    }

    fun save(latitude: Double, longitude: Double) {
        prefs.edit()
            .putFloat(KEY_LATITUDE, latitude.toFloat())
            .putFloat(KEY_LONGITUDE, longitude.toFloat())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "guruyu_last_report"
        private const val KEY_LATITUDE = "latitude"
        private const val KEY_LONGITUDE = "longitude"
    }
}
