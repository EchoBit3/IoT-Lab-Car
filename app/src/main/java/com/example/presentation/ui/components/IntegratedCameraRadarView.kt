package com.example.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.VehicleTelemetry
import com.example.presentation.ui.theme.BentoCharcoal
import com.example.presentation.ui.theme.BentoMint
import com.example.presentation.ui.theme.BentoSubtleText
import com.example.presentation.ui.theme.FpvDangerPulse
import com.example.presentation.ui.theme.FpvGroundBottom
import com.example.presentation.ui.theme.FpvGroundTop
import com.example.presentation.ui.theme.FpvHudOverlayBg
import com.example.presentation.ui.theme.FpvLatencyText
import com.example.presentation.ui.theme.FpvPlaceholderIconBg
import com.example.presentation.ui.theme.FpvPlaceholderIconTint
import com.example.presentation.ui.theme.FpvRoadBottom
import com.example.presentation.ui.theme.FpvRoadTop
import com.example.presentation.ui.theme.FpvSkyBottom
import com.example.presentation.ui.theme.FpvSkyTop
import com.example.presentation.ui.theme.FpvSonarLabel
import com.example.presentation.ui.theme.GoogleBlue
import com.example.presentation.ui.theme.GoogleGreen
import com.example.presentation.ui.theme.GoogleRed
import com.example.presentation.ui.theme.GoogleYellow
import com.example.presentation.ui.theme.WarningAmberBorder
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * Realistic 3D First-Person FPV Camera & Dual Ultrasonic Radar HUD.
 *
 * - Renders a real-time 3D perspective laboratory driving track that scrolls at the exact
 *   physical speed of the vehicle (`distanceTraveledM`), curves with the steering angle
 *   (`steeringPct`, `headingDeg`), shifts laterally as the car changes lanes (`lateralOffsetCm`),
 *   and projects 3D obstacles and dual HC-SR04 sonar wave cones (`leftCm`, `rightCm`).
 */
@Composable
fun IntegratedCameraRadarView(
    isConnected: Boolean,
    telemetry: VehicleTelemetry,
    activeGear: String,
    latencyMs: Long,
    isSimulated: Boolean,
    isWifiConnected: Boolean = true,
    onStartSimulationRequested: () -> Unit = {},
    onResetObstacleRequested: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ultrasonic = telemetry.ultrasonic
    var pulseState by remember { mutableStateOf(false) }

    LaunchedEffect(ultrasonic.criticalDanger && isConnected) {
        if (ultrasonic.criticalDanger && isConnected) {
            while (true) {
                pulseState = !pulseState
                delay(260)
            }
        } else {
            pulseState = false
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(236.dp)
            .testTag("integrated_camera_radar_card"),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCharcoal),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!isConnected) {
                // Offline Placeholder with direct button to launch Realistic 3D Driving Simulator
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(FpvPlaceholderIconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = null,
                            tint = FpvPlaceholderIconTint,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.fpv_offline_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(id = R.string.fpv_offline_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = BentoSubtleText,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onStartSimulationRequested,
                        enabled = isWifiConnected,
                        colors = ButtonDefaults.buttonColors(containerColor = GoogleGreen),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isWifiConnected) {
                                stringResource(id = R.string.fpv_start_simulation)
                            } else {
                                stringResource(id = R.string.fpv_enable_wifi_to_simulate)
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            } else {
                // REAL-TIME 3D PERSPECTIVE DRIVING & DUAL SONAR SIMULATION
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Horizon shifts subtly with acceleration/braking pitch and steering yaw
                    val pitchOffset = (telemetry.throttlePct / 100f) * 8f
                    val horizonY = (h * 0.40f + pitchOffset).coerceIn(h * 0.32f, h * 0.48f)
                    val vanishingX = (w * 0.5f - (telemetry.headingDeg / 45f) * (w * 0.16f))
                        .coerceIn(w * 0.25f, w * 0.75f)

                    // 1. Sky / Lab Ceiling Gradient
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(FpvSkyTop, FpvSkyBottom),
                            startY = 0f,
                            endY = horizonY
                        ),
                        size = Size(w, horizonY)
                    )

                    // 2. Ground / Laboratory Floor Gradient
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(FpvGroundTop, FpvGroundBottom),
                            startY = horizonY,
                            endY = h
                        ),
                        topLeft = Offset(0f, horizonY),
                        size = Size(w, h - horizonY)
                    )

                    // 3. 3D Perspective Asphalt Track Polygon (Shifts laterally with car offset)
                    val lateralShiftBottom = -(telemetry.lateralOffsetCm / 90f) * (w * 0.28f)
                    val roadBottomLeftX = w * 0.06f + lateralShiftBottom
                    val roadBottomRightX = w * 0.94f + lateralShiftBottom
                    val roadTopLeftX = vanishingX - w * 0.08f
                    val roadTopRightX = vanishingX + w * 0.08f

                    val roadPath = Path().apply {
                        moveTo(roadTopLeftX, horizonY)
                        lineTo(roadTopRightX, horizonY)
                        lineTo(roadBottomRightX, h)
                        lineTo(roadBottomLeftX, h)
                        close()
                    }
                    drawPath(
                        path = roadPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(FpvRoadTop, FpvRoadBottom),
                            startY = horizonY,
                            endY = h
                        )
                    )

                    // 4. Scrolling Perspective Horizontal Grid & Dashed Center Lane Lines
                    // Scroll phase driven by physical odometer `distanceTraveledM`
                    val phase = ((telemetry.distanceTraveledM * 2.2f) % 1.0f + 1.0f) % 1.0f
                    val numSlices = 7
                    for (i in 0 until numSlices) {
                        val rawT = ((i + phase) / numSlices.toFloat()).coerceIn(0.02f, 0.99f)
                        // Non-linear perspective depth (t^2)
                        val p = rawT * rawT
                        val y = horizonY + (h - horizonY) * p
                        val leftX = roadTopLeftX + (roadBottomLeftX - roadTopLeftX) * p
                        val rightX = roadTopRightX + (roadBottomRightX - roadTopRightX) * p

                        // Transverse asphalt grid line
                        drawLine(
                            color = Color.White.copy(alpha = 0.06f + 0.08f * p),
                            start = Offset(leftX, y),
                            end = Offset(rightX, y),
                            strokeWidth = 1f + 1.2f * p
                        )

                        // Center dashed yellow/white lane stripe
                        val nextRawT = minOf(1.0f, rawT + 0.07f)
                        val nextP = nextRawT * nextRawT
                        val y2 = horizonY + (h - horizonY) * nextP
                        val centerX1 = (leftX + rightX) * 0.5f
                        val centerLeft2 = roadTopLeftX + (roadBottomLeftX - roadTopLeftX) * nextP
                        val centerRight2 = roadTopRightX + (roadBottomRightX - roadTopRightX) * nextP
                        val centerX2 = (centerLeft2 + centerRight2) * 0.5f

                        drawLine(
                            color = WarningAmberBorder.copy(alpha = 0.25f + 0.55f * p),
                            start = Offset(centerX1, y),
                            end = Offset(centerX2, y2),
                            strokeWidth = 2f + 4f * p,
                            cap = StrokeCap.Round
                        )
                    }

                    // Road boundary curbs (Left & Right)
                    drawLine(
                        color = GoogleBlue.copy(alpha = 0.65f),
                        start = Offset(roadTopLeftX, horizonY),
                        end = Offset(roadBottomLeftX, h),
                        strokeWidth = 3f
                    )
                    drawLine(
                        color = GoogleBlue.copy(alpha = 0.65f),
                        start = Offset(roadTopRightX, horizonY),
                        end = Offset(roadBottomRightX, h),
                        strokeWidth = 3f
                    )

                    // 5. Curved Ackermann Steering Trajectory Ribbons
                    val relLateralCm = telemetry.obstacleLateralCm - telemetry.lateralOffsetCm
                    val isClearOfObstacle = abs(relLateralCm) >= 28.0f
                    val guideColor = when {
                        telemetry.isEmergencyBraked -> GoogleRed
                        isClearOfObstacle -> GoogleGreen
                        ultrasonic.criticalDanger -> GoogleRed
                        ultrasonic.warning -> GoogleYellow
                        else -> BentoMint
                    }
                    val steerCurveX = (telemetry.steeringPct / 100f) * (w * 0.22f)

                    val leftTrackPath = Path().apply {
                        moveTo(w * 0.26f, h * 0.96f)
                        quadraticTo(
                            w * 0.34f + steerCurveX * 0.45f,
                            h * 0.75f,
                            vanishingX - w * 0.05f + steerCurveX,
                            horizonY + (h - horizonY) * 0.28f
                        )
                    }
                    val rightTrackPath = Path().apply {
                        moveTo(w * 0.74f, h * 0.96f)
                        quadraticTo(
                            w * 0.66f + steerCurveX * 0.45f,
                            h * 0.75f,
                            vanishingX + w * 0.05f + steerCurveX,
                            horizonY + (h - horizonY) * 0.28f
                        )
                    }
                    drawPath(
                        path = leftTrackPath,
                        color = guideColor.copy(alpha = 0.85f),
                        style = Stroke(width = 3.2f, cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = rightTrackPath,
                        color = guideColor.copy(alpha = 0.85f),
                        style = Stroke(width = 3.2f, cap = StrokeCap.Round)
                    )

                    // 6. 3D Projected Obstacle in Track with Real Lateral & Longitudinal Position
                    val rawLongitudinalCm = if (isSimulated) telemetry.obstacleDistanceCm else ultrasonic.minDistance
                    if (rawLongitudinalCm in -12f..190f) {
                        val clampedDist = rawLongitudinalCm.coerceIn(0f, 190f)
                        val normDepth = ((190f - clampedDist) / 190f).coerceIn(0.08f, 1.0f)
                        val depthPerspective = normDepth * normDepth
                        val obsWidth = w * (0.07f + 0.23f * depthPerspective)
                        val obsHeight = h * (0.06f + 0.22f * depthPerspective)

                        // Compute road center and width at the obstacle's depth
                        val roadWidthAtDepth = (roadTopRightX - roadTopLeftX) +
                            ((roadBottomRightX - roadBottomLeftX) - (roadTopRightX - roadTopLeftX)) * depthPerspective
                        val roadCenterAtDepth = (roadTopLeftX + roadTopRightX) * 0.5f +
                            (((roadBottomLeftX + roadBottomRightX) * 0.5f) - (roadTopLeftX + roadTopRightX) * 0.5f) * depthPerspective

                        // Shift obstacle laterally according to its lane position & car's lateral offset
                        val obsCenterX = roadCenterAtDepth + (relLateralCm / 95f) * (roadWidthAtDepth * 0.65f)
                        val obsBottomY = horizonY + (h - horizonY) * (0.16f + 0.78f * depthPerspective)
                        val obsTopLeft = Offset(obsCenterX - obsWidth / 2f, obsBottomY - obsHeight)

                        val boxColor = when {
                            isClearOfObstacle -> GoogleGreen
                            ultrasonic.criticalDanger -> if (pulseState) GoogleRed else FpvDangerPulse
                            ultrasonic.warning -> GoogleYellow
                            else -> BentoMint
                        }

                        // 3D Obstacle Shadow
                        drawRoundRect(
                            color = Color.Black.copy(alpha = 0.55f),
                            topLeft = Offset(obsTopLeft.x + 4f, obsBottomY - 6f),
                            size = Size(obsWidth, 10f),
                            cornerRadius = CornerRadius(6f, 6f)
                        )

                        // 3D Obstacle Body
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(boxColor.copy(alpha = 0.92f), boxColor.copy(alpha = 0.65f)),
                                startY = obsTopLeft.y,
                                endY = obsBottomY
                            ),
                            topLeft = obsTopLeft,
                            size = Size(obsWidth, obsHeight),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.92f),
                            topLeft = obsTopLeft,
                            size = Size(obsWidth, obsHeight),
                            cornerRadius = CornerRadius(8f, 8f),
                            style = Stroke(width = 2f)
                        )
                    }

                    // 7. Dual Ultrasonic Sonar Wave Arcs at Bumper (Left FL & Right FR)
                    val leftSonarColor = when {
                        ultrasonic.leftCm < 25f -> GoogleRed
                        ultrasonic.leftCm < 60f -> GoogleYellow
                        else -> GoogleGreen
                    }
                    val rightSonarColor = when {
                        ultrasonic.rightCm < 25f -> GoogleRed
                        ultrasonic.rightCm < 60f -> GoogleYellow
                        else -> GoogleGreen
                    }
                    listOf(22f, 36f, 50f).forEachIndexed { idx, r ->
                        val alpha = 0.55f - idx * 0.15f
                        drawArc(
                            color = leftSonarColor.copy(alpha = alpha),
                            startAngle = -145f,
                            sweepAngle = 70f,
                            useCenter = false,
                            topLeft = Offset(w * 0.22f - r, h - r - 4f),
                            size = Size(r * 2f, r * 2f),
                            style = Stroke(width = 2.2f, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = rightSonarColor.copy(alpha = alpha),
                            startAngle = -105f,
                            sweepAngle = 70f,
                            useCenter = false,
                            topLeft = Offset(w * 0.78f - r, h - r - 4f),
                            size = Size(r * 2f, r * 2f),
                            style = Stroke(width = 2.2f, cap = StrokeCap.Round)
                        )
                    }
                }

                // Top HUD Overlay: Mode Badge + Reset Obstacle + Dodged Counter + Gear/Speed
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = FpvHudOverlayBg
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (telemetry.isEmergencyBraked) GoogleRed else if (isSimulated) GoogleGreen else BentoMint)
                                )
                                Text(
                                    text = if (isSimulated) {
                                        stringResource(id = R.string.fpv_dodged_format, telemetry.obstaclesDodged)
                                    } else {
                                        stringResource(id = R.string.fpv_esp32_cam_live)
                                    },
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (isSimulated) {
                            Surface(
                                shape = CircleShape,
                                color = FpvHudOverlayBg,
                                modifier = Modifier.size(28.dp)
                            ) {
                                IconButton(
                                    onClick = onResetObstacleRequested,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = stringResource(id = R.string.fpv_reset_track_cd),
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.72f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.1f m", telemetry.distanceTraveledM),
                                color = BentoMint,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (telemetry.isEmergencyBraked) "E_STOP" else activeGear,
                                color = when {
                                    telemetry.isEmergencyBraked || activeGear == "E_STOP" -> GoogleRed
                                    activeGear == "FORWARD" -> GoogleGreen
                                    activeGear == "REVERSE" -> GoogleBlue
                                    else -> Color.White
                                },
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${latencyMs}ms",
                                color = FpvLatencyText,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Integrated Left Ultrasonic Sensor Badge (HC-SR04 FL)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.78f),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(id = R.string.fpv_sonar_left),
                            color = FpvSonarLabel,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val leftColor = when {
                            ultrasonic.leftCm < 25f -> GoogleRed
                            ultrasonic.leftCm < 60f -> GoogleYellow
                            else -> GoogleGreen
                        }
                        Text(
                            text = String.format(Locale.US, "%.0f cm", ultrasonic.leftCm),
                            color = leftColor,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }

                // Integrated Right Ultrasonic Sensor Badge (HC-SR04 FR)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.78f),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(id = R.string.fpv_sonar_right),
                            color = FpvSonarLabel,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val rightColor = when {
                            ultrasonic.rightCm < 25f -> GoogleRed
                            ultrasonic.rightCm < 60f -> GoogleYellow
                            else -> GoogleGreen
                        }
                        Text(
                            text = String.format(Locale.US, "%.0f cm", ultrasonic.rightCm),
                            color = rightColor,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }

                // Bottom Status / Emergency Brake / Collision Banner
                val isClearLane = abs(telemetry.obstacleLateralCm - telemetry.lateralOffsetCm) >= 28.0f
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                ) {
                    if (telemetry.isEmergencyBraked || activeGear == "E_STOP") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = GoogleRed.copy(alpha = 0.95f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(id = R.string.fpv_estop_banner),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    } else if (ultrasonic.criticalDanger && !isClearLane) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = GoogleRed.copy(alpha = 0.92f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(id = R.string.fpv_obstacle_danger_format, ultrasonic.minDistance),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    } else if (isClearLane && telemetry.obstacleDistanceCm in -5f..110f) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = GoogleGreen.copy(alpha = 0.90f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.fpv_clear_lane_banner),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
