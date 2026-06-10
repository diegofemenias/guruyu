package com.guruyu.tracker

import android.app.Application
import com.guruyu.tracker.data.local.AppDatabase
import org.osmdroid.config.Configuration

class GuruyuApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue = packageName
    }
}
