package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.IoTNetworkDefaults
import com.example.domain.model.OperatorSession
import com.example.domain.model.UltrasonicTelemetry
import com.example.domain.model.VehicleCommand
import com.example.domain.model.VehicleCommandType
import com.example.data.network.IoTNetworkEngine
import com.example.data.local.EncryptedSessionStore
import com.example.presentation.viewmodel.VehicleLabViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context and verify auth button labels`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("IoT Lab Car", context.getString(R.string.app_name))
        assertEquals("Iniciar sesión con Google", context.getString(R.string.auth_google_signin))
        assertEquals("Crear cuenta", context.getString(R.string.auth_create_account))
    }

    @Test
    fun `test encrypted session store with HMAC signature and RGPD erasure`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = EncryptedSessionStore(context)

        val now = 1_700_000_000_000L
        val signedToken = store.generateSignedSessionToken(
            uid = "usr_test_123",
            email = "operador.iot@gmail.com",
            loginTimestamp = now
        )

        val session = OperatorSession(
            uid = "usr_test_123",
            displayName = "Operador de Vehículo",
            email = "operador.iot@gmail.com",
            token = signedToken,
            loginTimestamp = now,
            expiresAtTimestamp = now + IoTNetworkDefaults.SESSION_MAX_AGE_MS
        )

        store.saveSession(session)
        assertTrue(store.hasActiveSession(now + 1000L))

        val loaded = store.getSession(now + 1000L)
        assertNotNull(loaded)
        assertEquals("Operador de Vehículo", loaded?.displayName)
        assertEquals("operador.iot@gmail.com", loaded?.email)

        // Verify RGPD Art. 15 Right of Access summary
        val summary = store.getPersonalDataSummary(now + 1000L)
        assertTrue(summary.hasActiveOperatorSession)
        assertTrue(summary.tokenIntegrityVerified)
        assertEquals("operador.iot@gmail.com", summary.operatorEmail)

        // Verify RGPD Art. 5.1.e Storage Limitation (TTL expiration after 8h)
        val afterExpiry = now + IoTNetworkDefaults.SESSION_MAX_AGE_MS + 5000L
        assertNull(store.getSession(afterExpiry))
        assertFalse(store.hasActiveSession(afterExpiry))

        // Save again and test RGPD Art. 17 Right to Erasure
        store.saveSession(session)
        store.saveVehicleConfig("192.168.4.1", 8088, "http://192.168.4.1:81/stream")
        store.eraseAllPersonalData()
        assertFalse(store.hasActiveSession(now + 1000L))
        assertFalse(store.getPersonalDataSummary(now + 1000L).hasActiveOperatorSession)
    }

    @Test
    fun `test HMAC session token tamper and delimiter injection rejection`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = EncryptedSessionStore(context)
        val now = 1_700_000_000_000L
        val validToken = store.generateSignedSessionToken("usr_01", "admin@iot.edu", now)
        assertTrue(store.verifySessionToken(validToken, "usr_01", "admin@iot.edu", now))

        // Tamper with uid or signature
        assertFalse(store.verifySessionToken(validToken, "usr_02", "admin@iot.edu", now))
        val tamperedSigToken = validToken.dropLast(4) + "ffff"
        assertFalse(store.verifySessionToken(tamperedSigToken, "usr_01", "admin@iot.edu", now))

        // Verify Base64 claim encoding prevents pipe delimiter injection
        val pipeToken = store.generateSignedSessionToken("usr|admin", "a|b@iot.edu", now)
        assertTrue(store.verifySessionToken(pipeToken, "usr|admin", "a|b@iot.edu", now))
        assertFalse(store.verifySessionToken(pipeToken, "usr", "admin|a|b@iot.edu", now))
    }

    @Test
    fun `test RFC 1918 private LAN IPv4 validation`() {
        assertTrue(IoTNetworkDefaults.isValidPrivateLanIpv4("192.168.4.1"))
        assertTrue(IoTNetworkDefaults.isValidPrivateLanIpv4("10.0.0.42"))
        assertTrue(IoTNetworkDefaults.isValidPrivateLanIpv4("172.16.5.10"))
        assertFalse(IoTNetworkDefaults.isValidPrivateLanIpv4("8.8.8.8"))
        assertFalse(IoTNetworkDefaults.isValidPrivateLanIpv4("999.999.1.1"))
        assertFalse(IoTNetworkDefaults.isValidPrivateLanIpv4("evil.example.com"))
    }

    @Test
    fun `test safety watchdog dead man switch and emergency stop under ISO 12100 and 13849`() {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engine = IoTNetworkEngine(testScope)

        val t0 = 1_000_000L
        engine.sendJoystickVector(throttlePct = 80, steeringPct = 0, maxSpeedLimitPct = 100, commandTimestampMs = t0)
        assertEquals("FORWARD", engine.telemetry.value.state)
        assertTrue(engine.telemetry.value.leftMotorPwm > 0)

        // Within watchdog window (e.g. +500ms) -> still moving
        assertFalse(engine.evaluateWatchdog(t0 + 500L))
        assertEquals("FORWARD", engine.telemetry.value.state)

        // Past watchdog timeout (>1500ms without command refresh) -> automatic fail-safe cut to 0 PWM
        assertTrue(engine.evaluateWatchdog(t0 + IoTNetworkDefaults.WATCHDOG_TIMEOUT_MS + 200L))
        assertEquals("WATCHDOG_STOP", engine.telemetry.value.state)
        assertEquals(0, engine.telemetry.value.leftMotorPwm)
        assertEquals(0, engine.telemetry.value.rightMotorPwm)
        assertTrue(engine.telemetry.value.watchdogTriggered)

        // Test Emergency Stop lock
        engine.sendCommand(VehicleCommandType.EMERGENCY_STOP)
        assertTrue(engine.telemetry.value.isEmergencyBraked)
        assertEquals("E_STOP", engine.telemetry.value.state)
        assertEquals(0, engine.telemetry.value.leftMotorPwm)

        engine.releaseEmergencyBrake()
        assertFalse(engine.telemetry.value.isEmergencyBraked)
        assertEquals("PARK", engine.telemetry.value.state)
        engine.stopAll()
    }

    @Test
    fun `test honest runtime permission state in ViewModel`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = shadowOf(app)
        val vm = VehicleLabViewModel(app)

        // When user denies permissions in OS dialog, arePermissionsGranted must remain FALSE
        val deniedMap = vm.getRequiredRuntimePermissions().associateWith { false }
        vm.onPermissionsResult(deniedMap)
        assertFalse(vm.arePermissionsGranted.value)

        // When OS grants all required permissions, arePermissionsGranted becomes TRUE
        vm.getRequiredRuntimePermissions().forEach { perm ->
            shadowApp.grantPermissions(perm)
        }
        val grantedMap = vm.getRequiredRuntimePermissions().associateWith { true }
        vm.onPermissionsResult(grantedMap)
        assertTrue(vm.arePermissionsGranted.value)
    }

    @Test
    fun `test vehicle command json formatting`() {
        val cmd = VehicleCommand(
            seq = 101L,
            command = VehicleCommandType.FORWARD,
            speedPct = 65
        )
        val json = cmd.toJsonString()
        assertTrue(json.contains("\"command\":\"FORWARD\""))
        assertTrue(json.contains("\"speed\":65"))
    }

    @Test
    fun `test ultrasonic proximity logic`() {
        val safe = UltrasonicTelemetry(leftCm = 85f, rightCm = 90f)
        assertFalse(safe.warning)
        assertFalse(safe.criticalDanger)
        assertEquals(85f, safe.minDistance)

        val critical = UltrasonicTelemetry(leftCm = 15f, rightCm = 40f, criticalDanger = true)
        assertTrue(critical.criticalDanger)
        assertEquals(15f, critical.minDistance)
    }
}
