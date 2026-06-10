package com.guruyu.tracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.guruyu.tracker.MainActivity
import com.guruyu.tracker.R
import com.guruyu.tracker.data.DeviceIdProvider
import com.guruyu.tracker.data.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.time.ZoneOffset

class LocationTrackingService : Service() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Default)
    private lateinit var repository: LocationRepository
    private lateinit var deviceId: String
    private var tickJob: Job? = null
    private var screenInteractive = true
    private var wakeLock: PowerManager.WakeLock? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> screenInteractive = true
                Intent.ACTION_SCREEN_OFF -> screenInteractive = false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = LocationRepository(this)
        deviceId = DeviceIdProvider(this).getOrCreateDeviceId()
        createNotificationChannel()

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "guruyu:tracking_wake_lock",
        ).apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L)
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
        screenInteractive = powerManager.isInteractive
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        markActive(this)
        startMinuteLoop()
        return START_STICKY
    }

    private fun startMinuteLoop() {
        tickJob?.cancel()
        tickJob = scope.launch {
            collectAndSend()
            while (isActive) {
                delay(MINUTE_MS)
                collectAndSend()
            }
        }
    }

    private suspend fun collectAndSend() {
        repository.flushQueue(deviceId)

        val location = fetchCurrentLocation() ?: return

        val result = repository.sendImmediate(
            deviceUuid = deviceId,
            latitude = location.latitude,
            longitude = location.longitude,
            reportedAt = LocalDateTime.now(ZoneOffset.UTC).withNano(0),
        )

        sendBroadcast(
            Intent(ACTION_STATUS_UPDATE).apply {
                setPackage(packageName)
                putExtra(EXTRA_ENABLED, result.enabled)
                putExtra(EXTRA_MESSAGE, result.message)
                putExtra(EXTRA_QUEUE_COUNT, result.queueCount)
                putExtra(EXTRA_LATITUDE, location.latitude)
                putExtra(EXTRA_LONGITUDE, location.longitude)
            },
        )
    }

    private suspend fun fetchCurrentLocation(): Location? {
        val fused = LocationServices.getFusedLocationProviderClient(this)

        // Red/Wi‑Fi/celdas — sin activar GPS.
        requestLocation(fused, Priority.PRIORITY_LOW_POWER)?.let { return it }

        // Red con algo más de precisión si hace falta.
        requestLocation(fused, Priority.PRIORITY_BALANCED_POWER_ACCURACY)?.let { return it }

        // GPS solo si la pantalla está encendida y lo anterior falló.
        if (screenInteractive) {
            requestLocation(fused, Priority.PRIORITY_HIGH_ACCURACY)?.let { return it }
        }

        // Última ubicación conocida si sigue siendo reciente.
        return try {
            fused.lastLocation.await()?.takeIf { isRecentEnough(it) }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun requestLocation(
        fused: FusedLocationProviderClient,
        priority: Int,
    ): Location? {
        return try {
            val cancellation = CancellationTokenSource()
            fused.getCurrentLocation(priority, cancellation.token).await()
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun isRecentEnough(location: Location): Boolean {
        val ageMs = System.currentTimeMillis() - location.time
        return ageMs in 0..MAX_CACHED_LOCATION_AGE_MS
    }

    override fun onDestroy() {
        markInactive(this)
        tickJob?.cancel()
        scope.cancel()
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        unregisterReceiver(screenReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.tracking_notification_title),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(getString(R.string.tracking_notification_text))
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.guruyu.tracker.action.STOP_TRACKING"
        const val ACTION_STATUS_UPDATE = "com.guruyu.tracker.action.STATUS_UPDATE"
        const val EXTRA_ENABLED = "extra_enabled"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_QUEUE_COUNT = "extra_queue_count"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"

        private const val CHANNEL_ID = "guruyu_tracking"
        private const val NOTIFICATION_ID = 1001
        private const val MINUTE_MS = 60_000L
        private const val MAX_CACHED_LOCATION_AGE_MS = 5 * 60 * 1000L

        fun start(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            markInactive(context)
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun isActive(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ACTIVE, false)
        }

        private fun markActive(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ACTIVE, true)
                .apply()
        }

        private fun markInactive(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ACTIVE, false)
                .apply()
        }

        private const val PREFS_NAME = "guruyu_tracking_service"
        private const val KEY_ACTIVE = "active"
    }
}
