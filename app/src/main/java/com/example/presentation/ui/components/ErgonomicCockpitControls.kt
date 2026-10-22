package com.example.presentation.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.presentation.ui.theme.BentoCharcoal
import com.example.presentation.ui.theme.BentoSubtleText
import com.example.presentation.ui.theme.CockpitDirectionLabel
import com.example.presentation.ui.theme.CockpitGimbalBaseDark
import com.example.presentation.ui.theme.CockpitGimbalBaseLight
import com.example.presentation.ui.theme.CockpitGimbalBorderIdle
import com.example.presentation.ui.theme.CockpitLockedOverlayBg
import com.example.presentation.ui.theme.CockpitLockedText
import com.example.presentation.ui.theme.CockpitThumbCore
import com.example.presentation.ui.theme.CockpitThumbInner
import com.example.presentation.ui.theme.CockpitThumbMid
import com.example.presentation.ui.theme.CockpitThumbOuter
import com.example.presentation.ui.theme.GoogleBlue
import com.example.presentation.ui.theme.GoogleGreen
import com.example.presentation.ui.theme.GoogleRed
import com.example.presentation.ui.theme.GoogleYellow
import com.example.presentation.ui.theme.WarningAmberButton
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * Real 360° 2D Circular Analog Joystick & Differential Telemetry Cockpit.
 *
 * - Provides a genuine circular 2D analog gimbal joystick (X: Steering -100..+100%, Y: Throttle -100..+100%)
 *   constrained within a circular radius with spring return to center.
 * - Displays real-time differential motor PWM (Left/Right L298N), physical speed (km/h), and vector telemetry.
 * - Adapts responsively to any screen width via [BoxWithConstraints].
 */
@Composable
fun ErgonomicCockpitControls(
    isConnected: Boolean,
    selectedSpeedPct: Int,
    activeGear: String,
    throttlePct: Int = 0,
    steeringPct: Int = 0,
    velocityMps: Float = 0f,
    leftMotorPwm: Int = 0,
    rightMotorPwm: Int = 0,
    onJoystickMoved: (throttlePct: Int, steeringPct: Int) -> Unit,
    onSpeedSelected: (Int) -> Unit,
    onEmergencyStop: () -> Unit,
    modifier: Modifier = Modifier,
    isWifiConnected: Boolean = true
) {
    val effectiveConnected = isConnected && isWifiConnected
    val isEmergencyLocked = activeGear == "E_STOP"
    val coroutineScope = rememberCoroutineScope()

    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    // Immediately snap stick to (0,0) whenever Emergency Brake is engaged or connection drops
    LaunchedEffect(isEmergencyLocked, effectiveConnected) {
        if (isEmergencyLocked || !effectiveConnected) {
            offsetX.snapTo(0f)
            offsetY.snapTo(0f)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ergonomic_cockpit_card"),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val isCompact = maxWidth < 360.dp
            val joystickDiameterDp = if (isCompact) 168.dp else 196.dp
            val knobDiameterDp = if (isCompact) 54.dp else 62.dp
            val maxRadiusPx = with(LocalDensity.current) { ((joystickDiameterDp - knobDiameterDp) / 2f).toPx() }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row: Title & Live Speedometer Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.cockpit_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                !isWifiConnected -> stringResource(id = R.string.cockpit_sub_no_wifi)
                                !isConnected -> stringResource(id = R.string.cockpit_sub_disconnected)
                                isEmergencyLocked -> stringResource(id = R.string.cockpit_sub_estop)
                                else -> stringResource(id = R.string.cockpit_sub_ready)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (effectiveConnected && !isEmergencyLocked) MaterialTheme.colorScheme.onSurfaceVariant else GoogleRed
                        )
                    }

                    // Digital Speedometer Badge
                    val speedKmh = if (isEmergencyLocked) 0.0f else abs(velocityMps) * 3.6f
                    val displayVelMps = if (isEmergencyLocked) 0.0f else velocityMps
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = BentoCharcoal
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.1f km/h", speedKmh),
                                color = when {
                                    isEmergencyLocked -> GoogleRed
                                    speedKmh > 0.2f -> GoogleGreen
                                    else -> Color.White
                                },
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                            Text(
                                text = String.format(Locale.US, "%.2f m/s", displayVelMps),
                                color = BentoSubtleText,
                                fontSize = 9.5.sp
                            )
                        }
                    }
                }

                // Speed Limit Selector Row (Full width, responsive)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        30 to stringResource(id = R.string.cockpit_speed_30),
                        60 to stringResource(id = R.string.cockpit_speed_60),
                        100 to stringResource(id = R.string.cockpit_speed_100)
                    ).forEach { (speed, label) ->
                        val isSelected = selectedSpeedPct == speed
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = effectiveConnected && !isEmergencyLocked) { onSpeedSelected(speed) },
                            shape = RoundedCornerShape(12.dp),
                            color = when {
                                !effectiveConnected || isEmergencyLocked -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                isSelected -> GoogleBlue
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            },
                            border = if (isSelected && effectiveConnected && !isEmergencyLocked) null else BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = when {
                                        !effectiveConnected || isEmergencyLocked -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        isSelected -> Color.White
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }

                // Center Interactive Area: Circular 360° 2D Analog Joystick + Live Vector & Motor Telemetry
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: Live Vector & Differential PWM Telemetry
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Throttle & Steering Vector Box
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.cockpit_vector_header),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.cockpit_throttle_label),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${if (throttlePct > 0) "+" else ""}$throttlePct%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            throttlePct > 5 -> GoogleGreen
                                            throttlePct < -5 -> GoogleBlue
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.cockpit_steering_label),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = when {
                                            steeringPct < -5 -> "IZQ ${abs(steeringPct)}%"
                                            steeringPct > 5 -> "DER $steeringPct%"
                                            else -> stringResource(id = R.string.cockpit_steering_center)
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (abs(steeringPct) > 5) WarningAmberButton else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Differential L298N Motor PWM Box
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.cockpit_motors_header),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.cockpit_motor_left),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${if (isEmergencyLocked) 0 else leftMotorPwm} PWM",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.cockpit_motor_right),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${if (isEmergencyLocked) 0 else rightMotorPwm} PWM",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Active Gear Status Pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when {
                                !effectiveConnected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                isEmergencyLocked -> GoogleRed.copy(alpha = 0.18f)
                                activeGear == "FORWARD" -> GoogleGreen.copy(alpha = 0.15f)
                                activeGear == "REVERSE" -> GoogleBlue.copy(alpha = 0.15f)
                                activeGear == "LEFT" || activeGear == "RIGHT" -> GoogleYellow.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when {
                                        !isWifiConnected -> stringResource(id = R.string.cockpit_gear_no_wifi)
                                        !isConnected -> stringResource(id = R.string.cockpit_gear_disconnected)
                                        isEmergencyLocked -> stringResource(id = R.string.cockpit_gear_estop)
                                        activeGear == "FORWARD" -> stringResource(id = R.string.cockpit_gear_forward)
                                        activeGear == "REVERSE" -> stringResource(id = R.string.cockpit_gear_reverse)
                                        activeGear == "LEFT" -> stringResource(id = R.string.cockpit_gear_left)
                                        activeGear == "RIGHT" -> stringResource(id = R.string.cockpit_gear_right)
                                        else -> stringResource(id = R.string.cockpit_gear_neutral)
                                    },
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    color = when {
                                        !effectiveConnected -> MaterialTheme.colorScheme.onSurfaceVariant
                                        isEmergencyLocked -> GoogleRed
                                        activeGear == "FORWARD" -> GoogleGreen
                                        activeGear == "REVERSE" -> GoogleBlue
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }

                    // Right Column: True Circular 360° 2D Analog Gimbal Joystick
                    Box(
                        modifier = Modifier
                            .size(joystickDiameterDp)
                            .testTag("circular_analog_joystick"),
                        contentAlignment = Alignment.Center
                    ) {
                        // Circular Joystick Base & Radar Grid Canvas
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val outerRadius = size.minDimension / 2f - 4f

                            // Dark metallic gimbal base
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(CockpitGimbalBaseLight, CockpitGimbalBaseDark),
                                    center = center,
                                    radius = outerRadius
                                ),
                                radius = outerRadius,
                                center = center
                            )

                            // Outer bezel ring
                            drawCircle(
                                color = when {
                                    isEmergencyLocked -> GoogleRed.copy(alpha = 0.8f)
                                    effectiveConnected -> GoogleBlue.copy(alpha = 0.5f)
                                    else -> CockpitGimbalBorderIdle
                                },
                                radius = outerRadius,
                                center = center,
                                style = Stroke(width = 3f)
                            )

                            // Concentric power zone rings (33%, 66%, 90%)
                            listOf(0.33f, 0.66f, 0.90f).forEach { ratio ->
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.10f),
                                    radius = outerRadius * ratio,
                                    center = center,
                                    style = Stroke(width = 1.2f)
                                )
                            }

                            // 360° Degree Tick Marks every 30°
                            for (deg in 0 until 360 step 30) {
                                val rad = deg * (PI / 180.0)
                                val isCardinal = deg % 90 == 0
                                val innerR = outerRadius * (if (isCardinal) 0.84f else 0.90f)
                                val outerR = outerRadius * 0.97f
                                val start = Offset(
                                    x = center.x + (innerR * cos(rad)).toFloat(),
                                    y = center.y + (innerR * sin(rad)).toFloat()
                                )
                                val end = Offset(
                                    x = center.x + (outerR * cos(rad)).toFloat(),
                                    y = center.y + (outerR * sin(rad)).toFloat()
                                )
                                drawLine(
                                    color = if (isCardinal) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.2f),
                                    start = start,
                                    end = end,
                                    strokeWidth = if (isCardinal) 2.5f else 1.2f
                                )
                            }

                            // X & Y Crosshair Axes
                            drawLine(
                                color = Color.White.copy(alpha = 0.14f),
                                start = Offset(center.x, center.y - outerRadius * 0.82f),
                                end = Offset(center.x, center.y + outerRadius * 0.82f),
                                strokeWidth = 1.5f
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.14f),
                                start = Offset(center.x - outerRadius * 0.82f, center.y),
                                end = Offset(center.x + outerRadius * 0.82f, center.y),
                                strokeWidth = 1.5f
                            )

                            // Active Vector Line from Center (0,0) to Stick Position (offsetX, offsetY)
                            if (effectiveConnected && !isEmergencyLocked && (abs(offsetX.value) > 2f || abs(offsetY.value) > 2f)) {
                                val stickPos = Offset(center.x + offsetX.value, center.y + offsetY.value)
                                val vectorColor = when {
                                    offsetY.value < -10f -> GoogleGreen
                                    offsetY.value > 10f -> GoogleBlue
                                    else -> GoogleYellow
                                }
                                drawLine(
                                    color = vectorColor.copy(alpha = 0.85f),
                                    start = center,
                                    end = stickPos,
                                    strokeWidth = 6f,
                                    cap = StrokeCap.Round
                                )
                                drawCircle(
                                    color = vectorColor.copy(alpha = 0.25f),
                                    radius = 18f,
                                    center = stickPos
                                )
                            }
                        }

                        // Cardinal Direction Labels on Base
                        Text(
                            text = stringResource(id = R.string.cockpit_dir_up),
                            color = CockpitDirectionLabel,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp)
                        )
                        Text(
                            text = stringResource(id = R.string.cockpit_dir_down),
                            color = CockpitDirectionLabel,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                        )
                        Text(
                            text = stringResource(id = R.string.cockpit_dir_left),
                            color = CockpitDirectionLabel,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 8.dp)
                        )
                        Text(
                            text = stringResource(id = R.string.cockpit_dir_right),
                            color = CockpitDirectionLabel,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp)
                        )

                        if (!effectiveConnected || isEmergencyLocked) {
                            // Locked Overlay when disconnected, Wi-Fi off, or Emergency Brake engaged
                            Box(
                                modifier = Modifier
                                    .size(knobDiameterDp + 20.dp)
                                    .clip(CircleShape)
                                    .background(CockpitLockedOverlayBg)
                                    .border(2.dp, GoogleRed.copy(alpha = 0.85f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (isEmergencyLocked) Icons.Default.PanTool else Icons.Default.Lock,
                                        contentDescription = stringResource(id = R.string.cockpit_joystick_locked_cd),
                                        tint = if (isEmergencyLocked) GoogleRed else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = when {
                                            !isWifiConnected -> stringResource(id = R.string.cockpit_locked_no_wifi)
                                            !isConnected -> stringResource(id = R.string.cockpit_locked_offline)
                                            else -> stringResource(id = R.string.cockpit_locked_braked)
                                        },
                                        color = if (isEmergencyLocked) GoogleRed else CockpitLockedText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        } else {
                            // 3D-Styled Draggable Analog Thumbstick Cap (Moves in 360° 2D space)
                            val activeKnobRingColor = when {
                                offsetY.value < -10f -> GoogleGreen
                                offsetY.value > 10f -> GoogleBlue
                                abs(offsetX.value) > 10f -> GoogleYellow
                                else -> GoogleBlue
                            }

                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            offsetX.value.roundToInt(),
                                            offsetY.value.roundToInt()
                                        )
                                    }
                                    .size(knobDiameterDp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                CockpitThumbOuter,
                                                CockpitThumbMid,
                                                CockpitThumbInner
                                            )
                                        )
                                    )
                                    .border(3.dp, activeKnobRingColor, CircleShape)
                                    .pointerInput(effectiveConnected, isEmergencyLocked, maxRadiusPx) {
                                        detectDragGestures(
                                            onDragEnd = {
                                                coroutineScope.launch {
                                                    launch { offsetX.animateTo(0f, spring()) }
                                                    launch { offsetY.animateTo(0f, spring()) }
                                                }
                                                onJoystickMoved(0, 0)
                                            },
                                            onDragCancel = {
                                                coroutineScope.launch {
                                                    launch { offsetX.animateTo(0f, spring()) }
                                                    launch { offsetY.animateTo(0f, spring()) }
                                                }
                                                onJoystickMoved(0, 0)
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                if (isEmergencyLocked) return@detectDragGestures
                                                val rawX = offsetX.value + dragAmount.x
                                                val rawY = offsetY.value + dragAmount.y
                                                val distance = hypot(rawX, rawY)

                                                // Constrain smoothly within circular 360° radius
                                                val clampedX: Float
                                                val clampedY: Float
                                                if (distance <= maxRadiusPx) {
                                                    clampedX = rawX
                                                    clampedY = rawY
                                                } else {
                                                    val angle = atan2(rawY, rawX)
                                                    clampedX = maxRadiusPx * cos(angle)
                                                    clampedY = maxRadiusPx * sin(angle)
                                                }

                                                coroutineScope.launch {
                                                    offsetX.snapTo(clampedX)
                                                    offsetY.snapTo(clampedY)
                                                }

                                                // Normalize to -100..+100%
                                                // Up (-Y) is positive throttle (+100%), Down (+Y) is reverse (-100%)
                                                val normThrottle = ((-clampedY / maxRadiusPx) * 100f)
                                                    .roundToInt()
                                                    .coerceIn(-100, 100)
                                                val normSteering = ((clampedX / maxRadiusPx) * 100f)
                                                    .roundToInt()
                                                    .coerceIn(-100, 100)

                                                onJoystickMoved(normThrottle, normSteering)
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                // Inner tactile thumb grip ring
                                Box(
                                    modifier = Modifier
                                        .size(knobDiameterDp * 0.58f)
                                        .clip(CircleShape)
                                        .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                        .background(CockpitThumbCore),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(activeKnobRingColor)
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Full-Width Emergency Stop / Release Brake Button
                Button(
                    onClick = {
                        coroutineScope.launch {
                            launch { offsetX.snapTo(0f) }
                            launch { offsetY.snapTo(0f) }
                        }
                        onEmergencyStop()
                    },
                    enabled = effectiveConnected,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isEmergencyLocked) GoogleGreen else GoogleRed,
                        disabledContainerColor = GoogleRed.copy(alpha = 0.22f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("cockpit_estop_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PanTool,
                            contentDescription = stringResource(id = R.string.cockpit_estop_cd),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEmergencyLocked) {
                                stringResource(id = R.string.cockpit_estop_active_btn)
                            } else {
                                stringResource(id = R.string.cockpit_estop_engage_btn)
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
