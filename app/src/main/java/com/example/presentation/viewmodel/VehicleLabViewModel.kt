package com.example.presentation.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.ConnectionMetrics
import com.example.domain.model.IoTNetworkDefaults
import com.example.domain.model.OperatorSession
import com.example.domain.model.PersonalDataSummary
import com.example.domain.model.VehicleCommandType
import com.example.domain.model.VehicleTelemetry
import com.example.data.network.IoTNetworkEngine
import com.example.data.network.WifiNetworkMonitor
import com.example.data.local.EncryptedSessionStore
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VehicleLabViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionStore = EncryptedSessionStore(application)
    private val wifiMonitor = WifiNetworkMonitor(application)
    private val networkEngine = IoTNetworkEngine(
        scope = viewModelScope,
        rssiProvider = { wifiMonitor.getCurrentRssiDbm() }
    )

    private val _operatorSession = MutableStateFlow<OperatorSession?>(null)
    val operatorSession: StateFlow<OperatorSession?> = _operatorSession.asStateFlow()

    val isWifiConnected: StateFlow<Boolean> = wifiMonitor.isWifiConnected
    val detectedGatewayIp: StateFlow<String> = wifiMonitor.detectedGatewayIp

    val telemetry: StateFlow<VehicleTelemetry> = networkEngine.telemetry
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VehicleTelemetry())

    val metrics: StateFlow<ConnectionMetrics> = networkEngine.metrics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionMetrics())

    private val _showPermissionsDialog = MutableStateFlow(true)
    val showPermissionsDialog: StateFlow<Boolean> = _showPermissionsDialog.asStateFlow()

    private val _arePermissionsGranted = MutableStateFlow(false)
    val arePermissionsGranted: StateFlow<Boolean> = _arePermissionsGranted.asStateFlow()

    private val _showComplianceDialog = MutableStateFlow(false)
    val showComplianceDialog: StateFlow<Boolean> = _showComplianceDialog.asStateFlow()

    private val _selectedSpeedPct = MutableStateFlow(IoTNetworkDefaults.DEFAULT_SPEED_PCT)
    val selectedSpeedPct: StateFlow<Int> = _selectedSpeedPct.asStateFlow()

    private val _activeGear = MutableStateFlow(IoTNetworkDefaults.STATE_PARK)
    val activeGear: StateFlow<String> = _activeGear.asStateFlow()

    private val _joystickThrottlePct = MutableStateFlow(0)
    val joystickThrottlePct: StateFlow<Int> = _joystickThrottlePct.asStateFlow()

    private val _joystickSteeringPct = MutableStateFlow(0)
    val joystickSteeringPct: StateFlow<Int> = _joystickSteeringPct.asStateFlow()

    private var lastHapticWarningTime = 0L

    init {
        sessionStore.clearSession()
        _operatorSession.value = null

        checkPermissionsState(promptIfMissing = false)
        _showPermissionsDialog.value = !_arePermissionsGranted.value

        viewModelScope.launch {
            isWifiConnected.collect { connected ->
                if (!connected) {
                    _joystickThrottlePct.value = 0
                    _joystickSteeringPct.value = 0
                    _activeGear.value = IoTNetworkDefaults.STATE_PARK
                    networkEngine.disconnect()
                }
            }
        }

        viewModelScope.launch {
            telemetry.collect { t ->
                if (t.ultrasonic.criticalDanger) {
                    val now = System.currentTimeMillis()
                    if (now - lastHapticWarningTime > IoTNetworkDefaults.HAPTIC_COOLDOWN_MS) {
                        lastHapticWarningTime = now
                        triggerHapticWarning()
                    }
                }
            }
        }
    }

    /**
     * Returns the list of runtime permissions strictly required by the app on this Android version.
     */
    fun getRequiredRuntimePermissions(): List<String> {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return perms
    }

    /**
     * Strictly verifies actual OS permission state via [ContextCompat.checkSelfPermission].
     */
    fun checkPermissionsState(promptIfMissing: Boolean = false) {
        val context = getApplication<Application>()
        val allGrantedInOs = getRequiredRuntimePermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
        _arePermissionsGranted.value = allGrantedInOs
        if (!allGrantedInOs && promptIfMissing) {
            _showPermissionsDialog.value = true
        }
    }

    /**
     * Called after the system runtime permission dialog returns.
     * Evaluates the real grant result per permission instead of assuming approval.
     */
    fun onPermissionsResult(grantResults: Map<String, Boolean> = emptyMap()) {
        val context = getApplication<Application>()
        val allGrantedInOs = if (grantResults.isNotEmpty()) {
            getRequiredRuntimePermissions().all { perm ->
                grantResults[perm] == true ||
                    ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            }
        } else {
            getRequiredRuntimePermissions().all { perm ->
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            }
        }
        _arePermissionsGranted.value = allGrantedInOs
        _showPermissionsDialog.value = false
        wifiMonitor.refreshStatus()
    }

    fun requestPermissionsDialog() {
        _showPermissionsDialog.value = true
    }

    fun dismissPermissionsDialog() {
        _showPermissionsDialog.value = false
    }

    fun openComplianceDialog() {
        _showComplianceDialog.value = true
    }

    fun dismissComplianceDialog() {
        _showComplianceDialog.value = false
    }

    fun loginWithGoogle(displayName: String, email: String) {
        val safeEmail = email.trim()
        val safeName = displayName.trim().ifBlank { safeEmail.substringBefore("@") }
        if (safeEmail.isBlank()) return

        val now = System.currentTimeMillis()
        val uid = sessionStore.generateSecureOperatorUid()
        val expiresAt = now + IoTNetworkDefaults.SESSION_MAX_AGE_MS
        val signedToken = sessionStore.generateSignedSessionToken(
            uid = uid,
            email = safeEmail,
            loginTimestamp = now,
            expiresAtTimestamp = expiresAt
        )
        val session = OperatorSession(
            uid = uid,
            displayName = safeName,
            email = safeEmail,
            token = signedToken,
            loginTimestamp = now,
            expiresAtTimestamp = expiresAt
        )
        sessionStore.saveSession(session)
        _operatorSession.value = sessionStore.getSession(now)
        checkPermissionsState(promptIfMissing = false)
    }

    fun logout() {
        disconnectFromVehicle()
        sessionStore.clearSession()
        _operatorSession.value = null
    }

    fun deleteOperatorAccountAndData() {
        disconnectFromVehicle()
        sessionStore.eraseAllPersonalData()
        _operatorSession.value = null
        _showComplianceDialog.value = false
    }

    fun getPersonalDataSummary(): PersonalDataSummary {
        return sessionStore.getPersonalDataSummary()
    }

    fun setSpeed(speedPct: Int) {
        _selectedSpeedPct.value = speedPct.coerceIn(20, 100)
    }

    /**
     * Real-time 2D Analog Joystick vector handler (-100..+100 throttle, -100..+100 steering).
     */
    fun onJoystickVectorChanged(throttlePct: Int, steeringPct: Int) {
        if (telemetry.value.isEmergencyBraked || _activeGear.value == IoTNetworkDefaults.STATE_ESTOP) {
            _joystickThrottlePct.value = 0
            _joystickSteeringPct.value = 0
            return
        }

        _joystickThrottlePct.value = throttlePct
        _joystickSteeringPct.value = steeringPct

        val deadzone = IoTNetworkDefaults.JOYSTICK_DEADZONE_PCT
        _activeGear.value = when {
            abs(throttlePct) < deadzone && abs(steeringPct) < deadzone -> IoTNetworkDefaults.STATE_PARK
            abs(throttlePct) >= abs(steeringPct) -> {
                if (throttlePct > 0) IoTNetworkDefaults.STATE_FORWARD else IoTNetworkDefaults.STATE_REVERSE
            }
            else -> {
                if (steeringPct < 0) IoTNetworkDefaults.STATE_LEFT else IoTNetworkDefaults.STATE_RIGHT
            }
        }

        networkEngine.sendJoystickVector(
            throttlePct = throttlePct,
            steeringPct = steeringPct,
            maxSpeedLimitPct = _selectedSpeedPct.value
        )
    }

    fun emergencyStop() {
        if (telemetry.value.isEmergencyBraked || _activeGear.value == IoTNetworkDefaults.STATE_ESTOP) {
            _joystickThrottlePct.value = 0
            _joystickSteeringPct.value = 0
            _activeGear.value = IoTNetworkDefaults.STATE_PARK
            networkEngine.releaseEmergencyBrake()
            triggerHapticWarning(longVibe = false)
        } else {
            _joystickThrottlePct.value = 0
            _joystickSteeringPct.value = 0
            _activeGear.value = IoTNetworkDefaults.STATE_ESTOP
            networkEngine.sendCommand(VehicleCommandType.EMERGENCY_STOP)
            triggerHapticWarning(longVibe = true)
        }
    }

    fun switchToSimulation() {
        _joystickThrottlePct.value = 0
        _joystickSteeringPct.value = 0
        _activeGear.value = IoTNetworkDefaults.STATE_PARK
        networkEngine.startSimulationMode()
    }

    fun saveVehicleSettings(ip: String, port: Int, streamUrl: String) {
        val safeIp = IoTNetworkDefaults.sanitizeLocalIp(ip, wifiMonitor.detectedGatewayIp.value)
        val safePort = IoTNetworkDefaults.sanitizePort(port)
        val safeStream = IoTNetworkDefaults.sanitizeStreamUrl(streamUrl, safeIp)
        sessionStore.saveVehicleConfig(safeIp, safePort, safeStream)
        networkEngine.connectAsControlStation(safeIp, safePort, safeStream)
    }

    fun disconnectFromVehicle() {
        _activeGear.value = IoTNetworkDefaults.STATE_PARK
        _joystickThrottlePct.value = 0
        _joystickSteeringPct.value = 0
        networkEngine.disconnect()
    }

    fun getSavedVehicleConfig(): Triple<String, Int, String> {
        return sessionStore.getVehicleConfig(defaultIp = wifiMonitor.detectedGatewayIp.value)
    }

    fun resetSimulationObstacle() {
        _activeGear.value = IoTNetworkDefaults.STATE_PARK
        _joystickThrottlePct.value = 0
        _joystickSteeringPct.value = 0
        networkEngine.resetSimulationObstacle(IoTNetworkDefaults.DEFAULT_OBSTACLE_DIST_CM)
    }

    fun refreshWifiStatus() {
        wifiMonitor.refreshStatus()
    }

    private fun triggerHapticWarning(longVibe: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val context = getApplication<Application>()
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                ContextCompat.getSystemService(context, Vibrator::class.java)
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                val duration = if (longVibe) {
                    IoTNetworkDefaults.HAPTIC_LONG_MS
                } else {
                    IoTNetworkDefaults.HAPTIC_SHORT_MS
                }
                val effect = VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(effect)
            }
        } catch (_: Exception) {
            // Ignored gracefully in headless test environments
        }
    }

    override fun onCleared() {
        super.onCleared()
        wifiMonitor.unregister()
        networkEngine.stopAll()
    }
}
