package com.example.domain.model

import org.json.JSONObject

object IoTNetworkDefaults {
    const val PROTOCOL_VERSION = "IOT_EDU_V1"
    const val DEFAULT_AP_IP = "192.168.4.1"
    const val DEFAULT_TCP_PORT = 8088
    const val DEFAULT_CAMERA_PORT = 81
    const val DEFAULT_SPEED_PCT = 60

    // Safety & Physics Constants
    const val DEFAULT_OBSTACLE_DIST_CM = 140.0f
    const val SONAR_WARNING_THRESHOLD_CM = 60.0f
    const val SONAR_CRITICAL_THRESHOLD_CM = 25.0f
    const val JOYSTICK_DEADZONE_PCT = 8
    const val MAX_TELEMETRY_LINE_LENGTH = 1024

    // Network Timeouts & Intervals
    const val SOCKET_CONNECT_TIMEOUT_MS = 4000
    const val SOCKET_READ_TIMEOUT_MS = 3000
    const val RECONNECT_DELAY_MS = 3000L
    const val HEARTBEAT_INTERVAL_MS = 500L
    const val PHYSICS_STEP_DELAY_MS = 50L

    // Haptic Feedback Timing
    const val HAPTIC_COOLDOWN_MS = 600L
    const val HAPTIC_SHORT_MS = 120L
    const val HAPTIC_LONG_MS = 350L

    // Vehicle States
    const val STATE_PARK = "PARK"
    const val STATE_FORWARD = "FORWARD"
    const val STATE_REVERSE = "REVERSE"
    const val STATE_LEFT = "LEFT"
    const val STATE_RIGHT = "RIGHT"
    const val STATE_ESTOP = "E_STOP"
    const val STATE_WATCHDOG_STOP = "WATCHDOG_STOP"
    const val STATE_DISCONNECTED = "DESCONECTADO"

    // Safety Watchdog timeout (ISO 12100 / ISO 13849-1 Dead-Man Switch best practice)
    const val WATCHDOG_TIMEOUT_MS = 1500L

    // Maximum session retention period: 8 hours (RGPD Art. 5.1.e Storage Limitation)
    const val SESSION_MAX_AGE_MS = 8L * 60L * 60L * 1000L

    fun buildStreamUrl(ip: String): String {
        val safeIp = sanitizeLocalIp(ip)
        return "http://$safeIp:$DEFAULT_CAMERA_PORT/stream"
    }

    /**
     * Validates that an IPv4 address belongs strictly to a private local network (RFC 1918:
     * 10.0.0.0/8, 172.16.0.0/12, or 192.168.0.0/16) to prevent connections to arbitrary external hosts.
     */
    fun isValidPrivateLanIpv4(ip: String): Boolean {
        val trimmed = ip.trim()
        val parts = trimmed.split(".")
        if (parts.size != 4) return false
        val octets = parts.map { part ->
            if (part.isEmpty() || part.length > 3 || !part.all { it.isDigit() }) return false
            val num = part.toIntOrNull() ?: return false
            if (num !in 0..255) return false
            num
        }
        val first = octets[0]
        val second = octets[1]
        return when {
            first == 10 -> true
            first == 172 && second in 16..31 -> true
            first == 192 && second == 168 -> true
            else -> false
        }
    }

    fun sanitizeLocalIp(ip: String, fallback: String = DEFAULT_AP_IP): String {
        val candidate = ip.trim()
        if (isValidPrivateLanIpv4(candidate)) return candidate
        val safeFallback = fallback.trim()
        return if (isValidPrivateLanIpv4(safeFallback)) safeFallback else DEFAULT_AP_IP
    }

    fun sanitizePort(port: Int, fallback: Int = DEFAULT_TCP_PORT): Int {
        return if (port in 80..65535) port else fallback
    }

    fun sanitizeStreamUrl(rawUrl: String, localIp: String): String {
        val safeIp = sanitizeLocalIp(localIp)
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return buildStreamUrl(safeIp)
        val lower = trimmed.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return buildStreamUrl(safeIp)
        }
        val afterScheme = trimmed.substringAfter("://")
        val hostPort = afterScheme.substringBefore("/")
        val host = hostPort.substringBefore(":")
        return if (isValidPrivateLanIpv4(host)) trimmed else buildStreamUrl(safeIp)
    }
}

data class OperatorSession(
    val uid: String,
    val displayName: String,
    val email: String,
    val token: String,
    val authMethod: String = "Google Account",
    val loginTimestamp: Long = System.currentTimeMillis(),
    val expiresAtTimestamp: Long = loginTimestamp + IoTNetworkDefaults.SESSION_MAX_AGE_MS
)

/**
 * RGPD Art. 15 Right of Access summary of personal and configuration data stored on device.
 */
data class PersonalDataSummary(
    val hasActiveOperatorSession: Boolean,
    val operatorUid: String?,
    val operatorName: String?,
    val operatorEmail: String?,
    val encryptionMode: String,
    val tokenIntegrityVerified: Boolean,
    val sessionExpiresAtMillis: Long?,
    val savedVehicleIp: String
)

enum class VehicleCommandType {
    FORWARD,
    REVERSE,
    LEFT,
    RIGHT,
    STOP,
    EMERGENCY_STOP,
    HEARTBEAT
}

data class VehicleCommand(
    val protocol: String = IoTNetworkDefaults.PROTOCOL_VERSION,
    val seq: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val command: VehicleCommandType,
    val speedPct: Int = IoTNetworkDefaults.DEFAULT_SPEED_PCT,
    val throttlePct: Int = 0, // -100 (full reverse) to +100 (full forward)
    val steeringPct: Int = 0, // -100 (full left) to +100 (full right)
    val leftPwm: Int = 0,     // -255 to +255 L298N Left Motor PWM
    val rightPwm: Int = 0     // -255 to +255 L298N Right Motor PWM
) {
    fun toJsonString(): String {
        return JSONObject().apply {
            put("protocol", protocol)
            put("seq", seq)
            put("timestamp", timestamp)
            put("command", command.name)
            put("speed", speedPct.coerceIn(0, 100))
            put("throttle", throttlePct.coerceIn(-100, 100))
            put("steering", steeringPct.coerceIn(-100, 100))
            put("pwmL", leftPwm.coerceIn(-255, 255))
            put("pwmR", rightPwm.coerceIn(-255, 255))
        }.toString()
    }
}

data class UltrasonicTelemetry(
    val leftCm: Float = 0.0f,
    val rightCm: Float = 0.0f,
    val warning: Boolean = false,
    val criticalDanger: Boolean = false
) {
    val minDistance: Float get() = minOf(leftCm, rightCm)
}

data class VehicleTelemetry(
    val protocol: String = IoTNetworkDefaults.PROTOCOL_VERSION,
    val seq: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val ultrasonic: UltrasonicTelemetry = UltrasonicTelemetry(),
    val state: String = IoTNetworkDefaults.STATE_PARK,
    val speedPct: Int = 0,
    val throttlePct: Int = 0,
    val steeringPct: Int = 0,
    val velocityMps: Float = 0.0f,
    val headingDeg: Float = 0.0f,
    val lateralOffsetCm: Float = 0.0f,
    val obstacleLateralCm: Float = 0.0f,
    val obstacleDistanceCm: Float = IoTNetworkDefaults.DEFAULT_OBSTACLE_DIST_CM,
    val obstaclesDodged: Int = 0,
    val isEmergencyBraked: Boolean = false,
    val watchdogTriggered: Boolean = false,
    val distanceTraveledM: Float = 0.0f,
    val leftMotorPwm: Int = 0,
    val rightMotorPwm: Int = 0,
    val batteryPct: Int = 0,
    val wifiRssi: Int = 0
)

data class ConnectionMetrics(
    val isConnected: Boolean = false,
    val ipAddress: String = IoTNetworkDefaults.DEFAULT_AP_IP,
    val port: Int = IoTNetworkDefaults.DEFAULT_TCP_PORT,
    val streamUrl: String = IoTNetworkDefaults.buildStreamUrl(IoTNetworkDefaults.DEFAULT_AP_IP),
    val latencyMs: Long = 0L,
    val packetsReceived: Long = 0L,
    val packetsSent: Long = 0L,
    val lastError: String? = null,
    val isSimulated: Boolean = false
)
