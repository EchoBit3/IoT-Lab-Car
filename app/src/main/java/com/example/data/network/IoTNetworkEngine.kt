package com.example.data.network

import android.util.Log
import com.example.domain.model.ConnectionMetrics
import com.example.domain.model.IoTNetworkDefaults
import com.example.domain.model.UltrasonicTelemetry
import com.example.domain.model.VehicleCommand
import com.example.domain.model.VehicleCommandType
import com.example.domain.model.VehicleTelemetry
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * IoT Network & Realistic 2D/3D Vehicle Physics Engine.
 *
 * - Connects strictly to local RFC 1918 ESP32 IPv4 addresses over TCP socket on [Dispatchers.IO].
 * - Implements a Safety Watchdog / Dead-Man Switch that automatically zeroes motor PWM
 *   if command/heartbeat intervals exceed [IoTNetworkDefaults.WATCHDOG_TIMEOUT_MS]
 *   or if the TCP socket disconnects unexpectedly.
 * - Uses bounded line reading and input validation to protect against malformed or oversized payloads.
 */
class IoTNetworkEngine(
    private val scope: CoroutineScope,
    private val rssiProvider: () -> Int = { 0 }
) {
    private val _telemetry = MutableStateFlow(VehicleTelemetry())
    val telemetry: StateFlow<VehicleTelemetry> = _telemetry.asStateFlow()

    private val _metrics = MutableStateFlow(ConnectionMetrics())
    val metrics: StateFlow<ConnectionMetrics> = _metrics.asStateFlow()

    private var activeJob: Job? = null
    private var heartbeatJob: Job? = null
    private var simulatorJob: Job? = null

    private var clientSocket: Socket? = null
    private var outputWriter: PrintWriter? = null

    private var seqCounter = 1000L

    // Real-time 2D Kinematic & Differential-Drive Simulation State
    @Volatile private var simThrottlePct = 0
    @Volatile private var simSteeringPct = 0
    @Volatile private var simSpeedPct = 0
    @Volatile private var simState = IoTNetworkDefaults.STATE_PARK
    @Volatile private var simEmergencyBraked = false
    @Volatile private var simWatchdogTriggered = false
    @Volatile private var lastCommandTimestampMs = System.currentTimeMillis()
    @Volatile private var simVelocityMps = 0.0f
    @Volatile private var simHeadingDeg = 0.0f
    @Volatile private var simLateralOffsetCm = 0.0f
    @Volatile private var simDistanceTraveledM = 0.0f
    @Volatile private var simLeftPwm = 0
    @Volatile private var simRightPwm = 0

    // Dynamic obstacle state in laboratory track
    @Volatile private var simObstacleDistCm = IoTNetworkDefaults.DEFAULT_OBSTACLE_DIST_CM
    @Volatile private var simObstacleLateralCm = 0.0f
    @Volatile private var simObstaclesDodged = 0
    @Volatile private var simBattery = 96

    init {
        _metrics.value = ConnectionMetrics(
            isConnected = false,
            isSimulated = false,
            latencyMs = 0L,
            lastError = null
        )
        _telemetry.value = VehicleTelemetry(
            state = IoTNetworkDefaults.STATE_DISCONNECTED,
            ultrasonic = UltrasonicTelemetry(0f, 0f, false, false),
            batteryPct = 0,
            wifiRssi = 0
        )
    }

    /**
     * Connect as Control Station to a physical ESP32 vehicle on local Wi-Fi.
     * Enforces RFC 1918 private LAN IPv4 validation before opening a socket.
     */
    fun connectAsControlStation(ip: String, port: Int, streamUrl: String) {
        stopAll()

        val safeIp = IoTNetworkDefaults.sanitizeLocalIp(ip)
        val safePort = IoTNetworkDefaults.sanitizePort(port)
        val safeStreamUrl = IoTNetworkDefaults.sanitizeStreamUrl(streamUrl, safeIp)

        _metrics.update {
            it.copy(
                ipAddress = safeIp,
                port = safePort,
                streamUrl = safeStreamUrl,
                isConnected = false,
                isSimulated = false,
                latencyMs = 0L,
                lastError = null
            )
        }

        if (!IoTNetworkDefaults.isValidPrivateLanIpv4(safeIp)) {
            _metrics.update {
                it.copy(
                    isConnected = false,
                    lastError = "Invalid private LAN IPv4 address"
                )
            }
            return
        }

        activeJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    Log.i(TAG, "Connecting to local ESP32 socket at $safeIp:$safePort...")
                    val connectStartNs = System.nanoTime()
                    val socket = Socket()
                    socket.soTimeout = IoTNetworkDefaults.SOCKET_READ_TIMEOUT_MS
                    clientSocket = socket
                    socket.connect(
                        InetSocketAddress(safeIp, safePort),
                        IoTNetworkDefaults.SOCKET_CONNECT_TIMEOUT_MS
                    )
                    val connectLatencyMs = ((System.nanoTime() - connectStartNs) / 1_000_000L).coerceAtLeast(1L)

                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    outputWriter = writer
                    lastCommandTimestampMs = System.currentTimeMillis()
                    simWatchdogTriggered = false

                    _metrics.update {
                        it.copy(
                            isConnected = true,
                            lastError = null,
                            latencyMs = connectLatencyMs
                        )
                    }

                    startHardwareWatchdogJob(writer)

                    var lastPacketTimeNs = System.nanoTime()
                    while (isActive && socket.isConnected && !socket.isClosed) {
                        val line = readBoundedLine(reader, IoTNetworkDefaults.MAX_TELEMETRY_LINE_LENGTH) ?: break
                        val nowNs = System.nanoTime()
                        val frameIntervalMs = ((nowNs - lastPacketTimeNs) / 1_000_000L).coerceIn(1L, 999L)
                        lastPacketTimeNs = nowNs

                        parseIncomingTelemetry(line)
                        _metrics.update { m ->
                            m.copy(
                                packetsReceived = m.packetsReceived + 1,
                                latencyMs = frameIntervalMs
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "ESP32 socket connection lost or failed")
                    triggerWatchdogStop()
                    _metrics.update {
                        it.copy(
                            isConnected = false,
                            latencyMs = 0L,
                            lastError = e.localizedMessage
                        )
                    }
                    delay(IoTNetworkDefaults.RECONNECT_DELAY_MS)
                } finally {
                    heartbeatJob?.cancel()
                    heartbeatJob = null
                    safeCloseClient()
                }
            }
        }
    }

    /**
     * Reads a line with a strict character limit to prevent OOM DoS from untrusted TCP peers.
     */
    private fun readBoundedLine(reader: BufferedReader, maxChars: Int): String? {
        val sb = StringBuilder()
        while (true) {
            val ch = reader.read()
            if (ch == -1) {
                return if (sb.isEmpty()) null else sb.toString()
            }
            if (ch == '\n'.code) {
                break
            }
            if (ch != '\r'.code && sb.length < maxChars) {
                sb.append(ch.toChar())
            }
        }
        return sb.toString()
    }

    private fun startHardwareWatchdogJob(writer: PrintWriter) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(IoTNetworkDefaults.HEARTBEAT_INTERVAL_MS)
                val now = System.currentTimeMillis()
                val isMoving = abs(simThrottlePct) > 0 || abs(simSteeringPct) > 0
                if (isMoving && (now - lastCommandTimestampMs) > IoTNetworkDefaults.WATCHDOG_TIMEOUT_MS) {
                    triggerWatchdogStop()
                    val stopCmd = VehicleCommand(
                        seq = ++seqCounter,
                        command = VehicleCommandType.STOP,
                        speedPct = 0,
                        throttlePct = 0,
                        steeringPct = 0,
                        leftPwm = 0,
                        rightPwm = 0
                    )
                    try {
                        writer.println(stopCmd.toJsonString())
                    } catch (_: Exception) {}
                } else {
                    val hbCmd = VehicleCommand(
                        seq = ++seqCounter,
                        command = VehicleCommandType.HEARTBEAT,
                        speedPct = simSpeedPct,
                        throttlePct = simThrottlePct,
                        steeringPct = simSteeringPct,
                        leftPwm = simLeftPwm,
                        rightPwm = simRightPwm
                    )
                    try {
                        writer.println(hbCmd.toJsonString())
                    } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * Evaluates the Dead-Man Switch watchdog condition and immediately zeroes motor outputs
     * if command intervals exceed [IoTNetworkDefaults.WATCHDOG_TIMEOUT_MS] while the car is in motion.
     */
    fun evaluateWatchdog(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val isDriving = abs(simThrottlePct) > 0 || abs(simSteeringPct) > 0 || abs(simVelocityMps) > 0.05f
        if (!simEmergencyBraked && isDriving && (nowMillis - lastCommandTimestampMs) > IoTNetworkDefaults.WATCHDOG_TIMEOUT_MS) {
            triggerWatchdogStop()
            return true
        }
        return false
    }

    fun triggerWatchdogStop() {
        simWatchdogTriggered = true
        simThrottlePct = 0
        simSteeringPct = 0
        simSpeedPct = 0
        simVelocityMps = 0.0f
        simLeftPwm = 0
        simRightPwm = 0
        simState = IoTNetworkDefaults.STATE_WATCHDOG_STOP
        _telemetry.update {
            it.copy(
                state = IoTNetworkDefaults.STATE_WATCHDOG_STOP,
                speedPct = 0,
                throttlePct = 0,
                steeringPct = 0,
                velocityMps = 0f,
                leftMotorPwm = 0,
                rightMotorPwm = 0,
                watchdogTriggered = true
            )
        }
    }

    /**
     * Start realistic 20Hz (50ms) 2D/3D vehicle physics simulation mode.
     */
    fun startSimulationMode() {
        stopAll()

        simObstacleDistCm = IoTNetworkDefaults.DEFAULT_OBSTACLE_DIST_CM
        simObstacleLateralCm = 0.0f
        simObstaclesDodged = 0
        simEmergencyBraked = false
        simWatchdogTriggered = false
        lastCommandTimestampMs = System.currentTimeMillis()
        simVelocityMps = 0.0f
        simHeadingDeg = 0.0f
        simLateralOffsetCm = 0.0f
        simThrottlePct = 0
        simSteeringPct = 0
        simLeftPwm = 0
        simRightPwm = 0
        simState = IoTNetworkDefaults.STATE_PARK

        _metrics.update {
            it.copy(
                isConnected = true,
                isSimulated = true,
                latencyMs = 15L,
                lastError = null
            )
        }

        simulatorJob = scope.launch(Dispatchers.Default) {
            val dt = 0.05f
            var lastTickNs = System.nanoTime()
            while (isActive) {
                val nowNs = System.nanoTime()
                val realLoopMs = ((nowNs - lastTickNs) / 1_000_000L).coerceIn(10L, 120L)
                lastTickNs = nowNs

                evaluateWatchdog(System.currentTimeMillis())

                if (simEmergencyBraked) {
                    simVelocityMps = 0.0f
                    simThrottlePct = 0
                    simSteeringPct = 0
                    simSpeedPct = 0
                    simLeftPwm = 0
                    simRightPwm = 0
                    simState = IoTNetworkDefaults.STATE_ESTOP
                } else {
                    val maxVelocityMps = 2.4f
                    val hasSteeringInput = abs(simSteeringPct) >= 6

                    val turnRateDegPerSec = (simSteeringPct / 100.0f) * 65.0f
                    if (hasSteeringInput || abs(simVelocityMps) > 0.04f) {
                        val dirSign = if (simVelocityMps < -0.1f) -1f else 1f
                        simHeadingDeg = (simHeadingDeg + turnRateDegPerSec * dt * dirSign)
                            .coerceIn(-42.0f, 42.0f)
                    }
                    if (!hasSteeringInput && abs(simVelocityMps) > 0.08f) {
                        simHeadingDeg *= 0.88f
                    }

                    val headingContribution = (simHeadingDeg / 42.0f) * 55.0f * (abs(simVelocityMps) / maxVelocityMps)
                    val directSteerContribution = (simSteeringPct / 100.0f) * 125.0f
                    val lateralSpeedCmPerSec = directSteerContribution + headingContribution

                    if (hasSteeringInput || abs(simVelocityMps) > 0.04f) {
                        simLateralOffsetCm = (simLateralOffsetCm + lateralSpeedCmPerSec * dt)
                            .coerceIn(-95.0f, 95.0f)
                    }

                    val lateralDistToObstacle = abs(simLateralOffsetCm - simObstacleLateralCm)
                    val isAlignedWithObstacle = lateralDistToObstacle < 28.0f

                    val effectiveThrottle = if (abs(simThrottlePct) < 15 && abs(simSteeringPct) > 35 && simObstacleDistCm > 20.0f) {
                        28
                    } else {
                        simThrottlePct
                    }
                    val targetVelocity = (effectiveThrottle / 100.0f) * maxVelocityMps

                    val accelRate = if (abs(targetVelocity) > abs(simVelocityMps)) 3.2f else 5.5f
                    val velocityDiff = targetVelocity - simVelocityMps
                    val maxStep = accelRate * dt
                    simVelocityMps += velocityDiff.coerceIn(-maxStep, maxStep)

                    if (abs(simVelocityMps) < 0.03f && effectiveThrottle == 0) {
                        simVelocityMps = 0.0f
                    }

                    if (simVelocityMps > 0f && isAlignedWithObstacle && simObstacleDistCm in -4.0f..18.0f) {
                        simVelocityMps = 0.0f
                    }

                    val deltaDistM = simVelocityMps * dt
                    simDistanceTraveledM += deltaDistM
                    simObstacleDistCm -= (deltaDistM * 100.0f)

                    if (simObstacleDistCm < -18.0f) {
                        simObstaclesDodged += 1
                        simObstacleDistCm = 165.0f
                        val candidateLanes = listOf(-45.0f, 0.0f, 45.0f).filter { abs(it - simObstacleLateralCm) > 10f }
                        simObstacleLateralCm = candidateLanes.random()
                    } else if (simObstacleDistCm > 220.0f) {
                        simObstacleDistCm = 220.0f
                    }
                }

                val leftSensorX = simLateralOffsetCm - 14.0f
                val rightSensorX = simLateralOffsetCm + 14.0f
                val sonarBeamHalfWidth = 22.0f

                val obstacleInFront = simObstacleDistCm >= 0.0f
                val leftHitsObstacle = obstacleInFront && abs(leftSensorX - simObstacleLateralCm) <= sonarBeamHalfWidth
                val rightHitsObstacle = obstacleInFront && abs(rightSensorX - simObstacleLateralCm) <= sonarBeamHalfWidth

                val jitterL = if (abs(simVelocityMps) > 0.05f) (Random.nextFloat() - 0.5f) * 0.4f else 0.0f
                val jitterR = if (abs(simVelocityMps) > 0.05f) (Random.nextFloat() - 0.5f) * 0.4f else 0.0f

                val distanceLeft = if (leftHitsObstacle) {
                    (simObstacleDistCm + jitterL).coerceIn(8.0f, 195.0f)
                } else {
                    190.0f
                }

                val distanceRight = if (rightHitsObstacle) {
                    (simObstacleDistCm + jitterR).coerceIn(8.0f, 195.0f)
                } else {
                    190.0f
                }

                val minDistance = min(distanceLeft, distanceRight)
                val isWarning = minDistance < IoTNetworkDefaults.SONAR_WARNING_THRESHOLD_CM
                val isCritical = minDistance < IoTNetworkDefaults.SONAR_CRITICAL_THRESHOLD_CM

                if (Random.nextInt(600) == 0 && simBattery > 15) {
                    simBattery--
                }

                val liveRssi = rssiProvider()
                val telemetryData = VehicleTelemetry(
                    protocol = IoTNetworkDefaults.PROTOCOL_VERSION,
                    seq = ++seqCounter,
                    timestamp = System.currentTimeMillis(),
                    ultrasonic = UltrasonicTelemetry(
                        leftCm = distanceLeft,
                        rightCm = distanceRight,
                        warning = isWarning,
                        criticalDanger = isCritical
                    ),
                    state = simState,
                    speedPct = simSpeedPct,
                    throttlePct = simThrottlePct,
                    steeringPct = simSteeringPct,
                    velocityMps = simVelocityMps,
                    headingDeg = simHeadingDeg,
                    lateralOffsetCm = simLateralOffsetCm,
                    obstacleLateralCm = simObstacleLateralCm,
                    obstacleDistanceCm = simObstacleDistCm,
                    obstaclesDodged = simObstaclesDodged,
                    isEmergencyBraked = simEmergencyBraked,
                    watchdogTriggered = simWatchdogTriggered,
                    distanceTraveledM = simDistanceTraveledM,
                    leftMotorPwm = simLeftPwm,
                    rightMotorPwm = simRightPwm,
                    batteryPct = simBattery,
                    wifiRssi = if (liveRssi != 0) liveRssi else DEFAULT_SIMULATED_RSSI
                )

                _telemetry.value = telemetryData

                _metrics.update {
                    it.copy(
                        packetsReceived = it.packetsReceived + 1,
                        latencyMs = realLoopMs / 3L
                    )
                }

                delay(IoTNetworkDefaults.PHYSICS_STEP_DELAY_MS)
            }
        }
    }

    /**
     * Send 2D Analog Joystick Vector (throttlePct: -100..+100, steeringPct: -100..+100).
     */
    fun sendJoystickVector(
        throttlePct: Int,
        steeringPct: Int,
        maxSpeedLimitPct: Int = 100,
        commandTimestampMs: Long = System.currentTimeMillis()
    ) {
        if (simEmergencyBraked) {
            simThrottlePct = 0
            simSteeringPct = 0
            simSpeedPct = 0
            simVelocityMps = 0f
            simLeftPwm = 0
            simRightPwm = 0
            simState = IoTNetworkDefaults.STATE_ESTOP
            return
        }

        lastCommandTimestampMs = commandTimestampMs
        simWatchdogTriggered = false

        val scale = (maxSpeedLimitPct.coerceIn(20, 100)) / 100.0f
        val scaledThrottle = (throttlePct * scale).roundToInt().coerceIn(-100, 100)
        val scaledSteering = (steeringPct * scale).roundToInt().coerceIn(-100, 100)

        val rawLeft = (scaledThrottle + scaledSteering * 0.65f).coerceIn(-100f, 100f)
        val rawRight = (scaledThrottle - scaledSteering * 0.65f).coerceIn(-100f, 100f)
        val pwmL = ((rawLeft / 100f) * 255f).roundToInt()
        val pwmR = ((rawRight / 100f) * 255f).roundToInt()

        val deadzone = IoTNetworkDefaults.JOYSTICK_DEADZONE_PCT
        val cmdType = when {
            abs(scaledThrottle) < deadzone && abs(scaledSteering) < deadzone -> VehicleCommandType.STOP
            abs(scaledThrottle) >= abs(scaledSteering) -> {
                if (scaledThrottle >= 0) VehicleCommandType.FORWARD else VehicleCommandType.REVERSE
            }
            else -> {
                if (scaledSteering < 0) VehicleCommandType.LEFT else VehicleCommandType.RIGHT
            }
        }

        val magnitudePct = max(abs(scaledThrottle), abs(scaledSteering)).coerceIn(0, 100)

        simThrottlePct = scaledThrottle
        simSteeringPct = scaledSteering
        simSpeedPct = magnitudePct
        simLeftPwm = pwmL
        simRightPwm = pwmR
        simState = when (cmdType) {
            VehicleCommandType.STOP -> IoTNetworkDefaults.STATE_PARK
            VehicleCommandType.FORWARD -> IoTNetworkDefaults.STATE_FORWARD
            VehicleCommandType.REVERSE -> IoTNetworkDefaults.STATE_REVERSE
            VehicleCommandType.LEFT -> IoTNetworkDefaults.STATE_LEFT
            VehicleCommandType.RIGHT -> IoTNetworkDefaults.STATE_RIGHT
            VehicleCommandType.EMERGENCY_STOP -> IoTNetworkDefaults.STATE_ESTOP
            VehicleCommandType.HEARTBEAT -> simState
        }

        _telemetry.update {
            it.copy(
                state = simState,
                speedPct = simSpeedPct,
                throttlePct = simThrottlePct,
                steeringPct = simSteeringPct,
                leftMotorPwm = simLeftPwm,
                rightMotorPwm = simRightPwm,
                watchdogTriggered = false
            )
        }

        val cmd = VehicleCommand(
            seq = ++seqCounter,
            timestamp = commandTimestampMs,
            command = cmdType,
            speedPct = magnitudePct,
            throttlePct = scaledThrottle,
            steeringPct = scaledSteering,
            leftPwm = pwmL,
            rightPwm = pwmR
        )

        val json = cmd.toJsonString()
        _metrics.update { it.copy(packetsSent = it.packetsSent + 1) }

        if (!_metrics.value.isSimulated) {
            scope.launch(Dispatchers.IO) {
                try {
                    outputWriter?.println(json)
                } catch (_: Exception) {
                    Log.w(TAG, "Failed to send joystick vector over socket")
                }
            }
        }
    }

    /**
     * Send a discrete driving command to the vehicle.
     */
    fun sendCommand(commandType: VehicleCommandType, speedPct: Int = IoTNetworkDefaults.DEFAULT_SPEED_PCT) {
        when (commandType) {
            VehicleCommandType.FORWARD -> sendJoystickVector(speedPct, 0, 100)
            VehicleCommandType.REVERSE -> sendJoystickVector(-speedPct, 0, 100)
            VehicleCommandType.LEFT -> sendJoystickVector(0, -speedPct, 100)
            VehicleCommandType.RIGHT -> sendJoystickVector(0, speedPct, 100)
            VehicleCommandType.STOP -> sendJoystickVector(0, 0, 100)
            VehicleCommandType.HEARTBEAT -> {
                lastCommandTimestampMs = System.currentTimeMillis()
            }
            VehicleCommandType.EMERGENCY_STOP -> {
                simEmergencyBraked = true
                simWatchdogTriggered = false
                simThrottlePct = 0
                simSteeringPct = 0
                simSpeedPct = 0
                simVelocityMps = 0f
                simLeftPwm = 0
                simRightPwm = 0
                simState = IoTNetworkDefaults.STATE_ESTOP
                _telemetry.update {
                    it.copy(
                        state = IoTNetworkDefaults.STATE_ESTOP,
                        speedPct = 0,
                        throttlePct = 0,
                        steeringPct = 0,
                        velocityMps = 0f,
                        leftMotorPwm = 0,
                        rightMotorPwm = 0,
                        isEmergencyBraked = true,
                        watchdogTriggered = false
                    )
                }
                val cmd = VehicleCommand(
                    seq = ++seqCounter,
                    command = VehicleCommandType.EMERGENCY_STOP,
                    speedPct = 0
                )
                val json = cmd.toJsonString()
                _metrics.update { it.copy(packetsSent = it.packetsSent + 1) }
                if (!_metrics.value.isSimulated) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            outputWriter?.println(json)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    /**
     * Release the Emergency Brake Lock so the operator can drive again.
     */
    fun releaseEmergencyBrake() {
        simEmergencyBraked = false
        simWatchdogTriggered = false
        lastCommandTimestampMs = System.currentTimeMillis()
        simThrottlePct = 0
        simSteeringPct = 0
        simSpeedPct = 0
        simVelocityMps = 0f
        simLeftPwm = 0
        simRightPwm = 0
        simState = IoTNetworkDefaults.STATE_PARK
        _telemetry.update {
            it.copy(
                state = IoTNetworkDefaults.STATE_PARK,
                speedPct = 0,
                throttlePct = 0,
                steeringPct = 0,
                velocityMps = 0f,
                leftMotorPwm = 0,
                rightMotorPwm = 0,
                isEmergencyBraked = false,
                watchdogTriggered = false
            )
        }
    }

    fun resetSimulationObstacle(distanceCm: Float = IoTNetworkDefaults.DEFAULT_OBSTACLE_DIST_CM) {
        simObstacleDistCm = distanceCm
        simObstacleLateralCm = 0.0f
        simLateralOffsetCm = 0.0f
        simHeadingDeg = 0.0f
        simVelocityMps = 0.0f
        simThrottlePct = 0
        simSteeringPct = 0
        simEmergencyBraked = false
        simWatchdogTriggered = false
        simState = IoTNetworkDefaults.STATE_PARK
        simSpeedPct = 0
    }

    private fun parseIncomingTelemetry(jsonStr: String) {
        if (jsonStr.isBlank() || jsonStr.length > IoTNetworkDefaults.MAX_TELEMETRY_LINE_LENGTH) return
        try {
            val root = JSONObject(jsonStr)
            val ultrasonicObj = root.optJSONObject("ultrasonic")
            val vehicleObj = root.optJSONObject("vehicle")

            val left = (ultrasonicObj?.optDouble("left_cm", 0.0)?.toFloat() ?: 0f).coerceIn(0f, 400f)
            val right = (ultrasonicObj?.optDouble("right_cm", 0.0)?.toFloat() ?: 0f).coerceIn(0f, 400f)
            val warnThreshold = IoTNetworkDefaults.SONAR_WARNING_THRESHOLD_CM
            val critThreshold = IoTNetworkDefaults.SONAR_CRITICAL_THRESHOLD_CM
            val warning = ultrasonicObj?.optBoolean("warning", false)
                ?: (left in 0.1f..warnThreshold || right in 0.1f..warnThreshold)
            val critical = (left in 0.1f..critThreshold) || (right in 0.1f..critThreshold)

            val rawState = vehicleObj?.optString("state", IoTNetworkDefaults.STATE_PARK)
                ?.uppercase()
                .orEmpty()
            val safeState = if (rawState in ALLOWED_STATES) rawState else IoTNetworkDefaults.STATE_PARK
            val battery = (vehicleObj?.optInt("battery_pct", 0) ?: 0).coerceIn(0, 100)
            val seq = root.optLong("seq", seqCounter).coerceAtLeast(0L)

            _telemetry.value = _telemetry.value.copy(
                seq = seq,
                ultrasonic = UltrasonicTelemetry(left, right, warning, critical),
                state = safeState,
                batteryPct = battery,
                wifiRssi = rssiProvider()
            )
        } catch (_: Exception) {
            Log.w(TAG, "Ignored malformed telemetry packet")
        }
    }

    private fun safeCloseClient() {
        try {
            outputWriter?.flush()
            outputWriter?.close()
        } catch (_: Exception) {}
        try {
            clientSocket?.close()
        } catch (_: Exception) {}
        outputWriter = null
        clientSocket = null
    }

    fun stopAll() {
        activeJob?.cancel()
        activeJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        simulatorJob?.cancel()
        simulatorJob = null
        safeCloseClient()
        _metrics.update { it.copy(isConnected = false) }
    }

    fun disconnect() {
        stopAll()
        simThrottlePct = 0
        simSteeringPct = 0
        simSpeedPct = 0
        simVelocityMps = 0f
        simLeftPwm = 0
        simRightPwm = 0
        _metrics.update {
            it.copy(
                isConnected = false,
                isSimulated = false,
                latencyMs = 0L,
                lastError = null
            )
        }
        _telemetry.value = VehicleTelemetry(
            state = IoTNetworkDefaults.STATE_DISCONNECTED,
            ultrasonic = UltrasonicTelemetry(0f, 0f, false, false),
            batteryPct = 0,
            wifiRssi = 0
        )
    }

    companion object {
        private const val TAG = "IoTNetworkEngine"
        private const val DEFAULT_SIMULATED_RSSI = -52
        private val ALLOWED_STATES = setOf(
            IoTNetworkDefaults.STATE_PARK,
            IoTNetworkDefaults.STATE_FORWARD,
            IoTNetworkDefaults.STATE_REVERSE,
            IoTNetworkDefaults.STATE_LEFT,
            IoTNetworkDefaults.STATE_RIGHT,
            IoTNetworkDefaults.STATE_ESTOP,
            IoTNetworkDefaults.STATE_WATCHDOG_STOP,
            IoTNetworkDefaults.STATE_DISCONNECTED
        )
    }
}
