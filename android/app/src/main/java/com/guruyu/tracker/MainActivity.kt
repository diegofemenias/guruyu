package com.guruyu.tracker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.guruyu.tracker.data.DeviceIdProvider
import com.guruyu.tracker.data.remote.ApiClient
import com.guruyu.tracker.data.remote.RemoteDevice
import com.guruyu.tracker.databinding.ActivityMainBinding
import com.guruyu.tracker.service.LocationTrackingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var deviceId: String
    private val apiClient = ApiClient()
    private var trackingActive = false
    private var selfEnabled: Boolean? = null
    private val deviceMarkers = mutableMapOf<String, Marker>()

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != LocationTrackingService.ACTION_STATUS_UPDATE) return
            val enabled = intent.getBooleanExtra(LocationTrackingService.EXTRA_ENABLED, false)
            selfEnabled = enabled
            updateStatusUi(enabled)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            requestBackgroundLocationIfNeeded()
        } else {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
            updateStatusUi(null)
        }
    }

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, R.string.background_permission_recommended, Toast.LENGTH_LONG).show()
        }
        startTracking()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        deviceId = DeviceIdProvider(this).getOrCreateDeviceId()
        binding.deviceIdText.text = "${getString(R.string.device_id_label)}: $deviceId"

        setupMap()
        setupButtons()
        setupAutoRefresh()
        syncTrackingUi()
        requestPermissionsAndStart()
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
        syncTrackingUi()
        lifecycleScope.launch { fetchAndRenderDevices(moveCamera = false) }
    }

    override fun onStop() {
        unregisterReceiver(statusReceiver)
        super.onStop()
    }

    private fun setupMap() {
        val map = binding.mapView
        map.setMultiTouchControls(true)
        map.controller.setZoom(14.0)
        map.controller.setCenter(GeoPoint(-34.830005, -55.953078))
    }

    private fun setupButtons() {
        binding.refreshButton.setOnClickListener {
            lifecycleScope.launch {
                val ok = fetchAndRenderDevices(moveCamera = true, showFeedback = true)
                if (!ok) {
                    Toast.makeText(this@MainActivity, R.string.map_update_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.trackingButton.setOnClickListener {
            if (trackingActive) {
                stopTracking()
            } else {
                requestPermissionsAndStart()
            }
        }
    }

    private fun setupAutoRefresh() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    fetchAndRenderDevices(moveCamera = false)
                    delay(60_000)
                }
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
            requestBackgroundLocationIfNeeded()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            return
        }
        startTracking()
    }

    private fun syncTrackingUi() {
        trackingActive = LocationTrackingService.isActive(this)
        binding.trackingButton.text = if (trackingActive) {
            getString(R.string.stop_tracking)
        } else {
            getString(R.string.start_tracking)
        }
        updateStatusUi(selfEnabled)
    }

    private fun startTracking() {
        if (trackingActive) {
            return
        }
        trackingActive = true
        LocationTrackingService.start(this)
        binding.trackingButton.text = getString(R.string.stop_tracking)
        updateStatusUi(selfEnabled)
    }

    private fun stopTracking() {
        if (!trackingActive) return
        trackingActive = false
        LocationTrackingService.stop(this)
        binding.trackingButton.text = getString(R.string.start_tracking)
        updateStatusUi(selfEnabled)
    }

    private suspend fun fetchAndRenderDevices(
        moveCamera: Boolean,
        showFeedback: Boolean = false,
    ): Boolean {
        return try {
            val response = withContext(Dispatchers.IO) {
                apiClient.fetchDevices(deviceId)
            }
            if (response.success) {
                selfEnabled = response.selfEnabled
                renderDevices(response.devices, moveCamera)
                if (!trackingActive) {
                    updateStatusUi(response.selfEnabled)
                }
                if (showFeedback) {
                    Toast.makeText(this, R.string.map_updated, Toast.LENGTH_SHORT).show()
                }
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun updateStatusUi(enabled: Boolean?) {
        val statusText = when {
            trackingActive && enabled == true -> getString(R.string.status_enabled)
            trackingActive && enabled == false -> getString(R.string.status_waiting)
            !trackingActive -> getString(R.string.status_stopped)
            else -> getString(R.string.status_stopped)
        }
        binding.statusText.text = "${getString(R.string.status_label)}: $statusText"
    }

    private fun renderDevices(devices: List<RemoteDevice>, moveCamera: Boolean) {
        val map = binding.mapView
        deviceMarkers.values.forEach { map.overlays.remove(it) }
        deviceMarkers.clear()

        var firstPoint: GeoPoint? = null

        devices.forEach { device ->
            val point = GeoPoint(device.latitude, device.longitude)
            if (firstPoint == null) {
                firstPoint = point
            }

            val color = when {
                device.uuid == deviceId -> ContextCompat.getColor(this, R.color.marker_self)
                device.isStale -> ContextCompat.getColor(this, R.color.marker_stale)
                else -> ContextCompat.getColor(this, R.color.marker_active)
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
                icon = createMarkerIcon(color)
            }

            map.overlays.add(marker)
            deviceMarkers[device.uuid] = marker
        }

        if (moveCamera) {
            firstPoint?.let { map.controller.animateTo(it) }
        }
        map.invalidate()
    }

    private fun createMarkerIcon(color: Int): Drawable {
        val size = (40 * resources.displayMetrics.density).toInt()
        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_map_marker)!!.mutate()
        DrawableCompat.setTint(drawable, color)
        DrawableCompat.setTintMode(drawable, PorterDuff.Mode.SRC_IN)
        drawable.setBounds(0, 0, size, size)
        return drawable
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
