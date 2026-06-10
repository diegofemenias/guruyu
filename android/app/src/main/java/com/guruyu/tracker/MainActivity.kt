package com.guruyu.tracker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import com.guruyu.tracker.data.DeviceIdProvider
import com.guruyu.tracker.data.LocationRepository
import com.guruyu.tracker.data.remote.ApiClient
import com.guruyu.tracker.data.remote.RemoteDevice
import com.guruyu.tracker.databinding.ActivityMainBinding
import com.guruyu.tracker.service.LocationTrackingService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var deviceId: String
    private val apiClient = ApiClient()
    private val repository by lazy { LocationRepository(this) }
    private var trackingActive = false
    private var selfEnabled: Boolean? = null
    private var mapRefreshJob: Job? = null
    private val deviceMarkers = mutableMapOf<String, Marker>()

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != LocationTrackingService.ACTION_STATUS_UPDATE) return
            val enabled = intent.getBooleanExtra(LocationTrackingService.EXTRA_ENABLED, false)
            val queue = intent.getIntExtra(LocationTrackingService.EXTRA_QUEUE_COUNT, 0)
            val lat = intent.getDoubleExtra(LocationTrackingService.EXTRA_LATITUDE, 0.0)
            val lng = intent.getDoubleExtra(LocationTrackingService.EXTRA_LONGITUDE, 0.0)
            selfEnabled = enabled
            lifecycleScope.launch { updateStatusUi(enabled, queue, lat, lng) }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            maybeAutoStartTracking()
        } else {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        deviceId = DeviceIdProvider(this).getOrCreateDeviceId()
        binding.deviceIdText.text = "${getString(R.string.device_id_label)}: $deviceId"

        setupMap()
        setupButtons()
        lifecycleScope.launch { updateStatusUi(false, 0, null, null) }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(LocationTrackingService.ACTION_STATUS_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(statusReceiver, filter)
        }
        refreshMapDevices()
        startMapRefreshLoop()
    }

    override fun onStop() {
        mapRefreshJob?.cancel()
        unregisterReceiver(statusReceiver)
        super.onStop()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        stopTracking()
    }

    private fun setupMap() {
        val map = binding.mapView
        map.setMultiTouchControls(true)
        map.controller.setZoom(12.0)
        map.controller.setCenter(GeoPoint(19.4326, -99.1332))
    }

    private fun setupButtons() {
        binding.copyIdButton.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("device_id", deviceId))
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }

        binding.trackingButton.setOnClickListener {
            if (trackingActive) {
                stopTracking()
            } else {
                requestPermissionsAndStart()
            }
        }
    }

    private fun requestPermissionsAndStart() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }

        if (needed.isEmpty()) {
            startTracking()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun maybeAutoStartTracking() {
        if (!trackingActive) {
            startTracking()
        }
    }

    private fun startTracking() {
        trackingActive = true
        LocationTrackingService.start(this)
        binding.trackingButton.text = getString(R.string.stop_tracking)
        lifecycleScope.launch {
            val count = repository.pendingCount()
            binding.queueText.text = "${getString(R.string.queued_reports)}: $count"
        }
    }

    private fun stopTracking() {
        if (!trackingActive) return
        trackingActive = false
        LocationTrackingService.stop(this)
        binding.trackingButton.text = getString(R.string.start_tracking)
        lifecycleScope.launch {
            updateStatusUi(selfEnabled == true, repository.pendingCount(), null, null)
        }
    }

    private fun startMapRefreshLoop() {
        mapRefreshJob?.cancel()
        mapRefreshJob = lifecycleScope.launch {
            while (isActive) {
                refreshMapDevices()
                delay(60_000)
            }
        }
    }

    private fun refreshMapDevices() {
        lifecycleScope.launch {
            try {
                val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    apiClient.fetchDevices(deviceId)
                }
                if (response.success) {
                    selfEnabled = response.selfEnabled
                    renderDevices(response.devices)
                    if (!trackingActive) {
                        updateStatusUi(response.selfEnabled == true, repository.pendingCount(), null, null)
                    }
                }
            } catch (_: Exception) {
                // Se reintenta en el próximo ciclo.
            }
        }
    }

    private suspend fun updateStatusUi(
        enabled: Boolean?,
        queueCount: Int,
        lat: Double?,
        lng: Double?,
    ) {
        val queue = if (queueCount == 0) repository.pendingCount() else queueCount
        val statusText = when {
            trackingActive && enabled == true -> getString(R.string.status_enabled)
            trackingActive && enabled == false -> getString(R.string.status_waiting)
            !trackingActive -> getString(R.string.status_not_tracking)
            else -> getString(R.string.status_disabled_tracking)
        }

        binding.statusText.text = "${getString(R.string.status_label)}: $statusText"
        binding.queueText.text = "${getString(R.string.queued_reports)}: $queue"

        if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
            binding.lastLocationText.text = "${getString(R.string.last_location)}: $lat, $lng"
        }
    }

    private fun renderDevices(devices: List<RemoteDevice>) {
        val map = binding.mapView
        deviceMarkers.values.forEach { map.overlays.remove(it) }
        deviceMarkers.clear()

        var firstPoint: GeoPoint? = null

        devices.forEach { device ->
            val point = GeoPoint(device.latitude, device.longitude)
            if (firstPoint == null) {
                firstPoint = point
            }

            val marker = Marker(map).apply {
                position = point
                title = device.displayName
                snippet = if (device.isStale) {
                    "${device.reportedAt}\n${getString(R.string.stale_marker)}"
                } else {
                    device.reportedAt
                }
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                val color = when {
                    device.uuid == deviceId -> ContextCompat.getColor(this@MainActivity, R.color.marker_self)
                    device.isStale -> ContextCompat.getColor(this@MainActivity, R.color.marker_stale)
                    else -> ContextCompat.getColor(this@MainActivity, R.color.marker_active)
                }
                icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_launcher)?.apply {
                    setTint(color)
                    setTintMode(PorterDuff.Mode.SRC_IN)
                }?.toDrawable(intrinsicWidth, intrinsicHeight)
            }

            map.overlays.add(marker)
            deviceMarkers[device.uuid] = marker
        }

        firstPoint?.let { map.controller.animateTo(it) }
        map.invalidate()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        binding.mapView.onPause()
        super.onPause()
    }
}
