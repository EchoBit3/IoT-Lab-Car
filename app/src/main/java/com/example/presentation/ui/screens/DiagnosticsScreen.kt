package com.example.presentation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.ConnectionMetrics
import com.example.domain.model.IoTNetworkDefaults
import com.example.domain.model.VehicleTelemetry
import com.example.presentation.ui.theme.BentoBlueLight
import com.example.presentation.ui.theme.BentoCharcoal
import com.example.presentation.ui.theme.BentoCharcoalBorder
import com.example.presentation.ui.theme.BentoCharcoalSurface
import com.example.presentation.ui.theme.BentoLavenderDark
import com.example.presentation.ui.theme.BentoLavenderLight
import com.example.presentation.ui.theme.BentoMintDark
import com.example.presentation.ui.theme.BentoMintLight
import com.example.presentation.ui.theme.BentoSubtleText
import com.example.presentation.ui.theme.GoogleBlue
import com.example.presentation.ui.theme.GoogleGreen
import com.example.presentation.ui.theme.GoogleRed
import com.example.presentation.ui.theme.WarningAmberBg
import com.example.presentation.ui.theme.WarningAmberBody
import com.example.presentation.ui.theme.WarningAmberBorder
import com.example.presentation.ui.theme.WarningAmberButton
import com.example.presentation.ui.theme.WarningAmberIcon
import com.example.presentation.ui.theme.WarningAmberTitle

/**
 * Clean, Coherent Google Dashboard for Network & ESP32 Configuration.
 */
@Composable
fun DiagnosticsScreen(
    metrics: ConnectionMetrics,
    telemetry: VehicleTelemetry,
    detectedGatewayIp: String = IoTNetworkDefaults.DEFAULT_AP_IP,
    isWifiConnected: Boolean = true,
    onOpenWifiSettings: () -> Unit = {},
    onSaveConfig: (ip: String, port: Int, streamUrl: String) -> Unit,
    onSwitchToSimulation: () -> Unit,
    modifier: Modifier = Modifier
) {
    var ipInput by remember(metrics.ipAddress) { mutableStateOf(metrics.ipAddress) }
    var portInput by remember(metrics.port) { mutableStateOf(metrics.port.toString()) }
    var streamUrlInput by remember(metrics.streamUrl) { mutableStateOf(metrics.streamUrl) }

    val effectiveConnected = metrics.isConnected && isWifiConnected
    val resolvedIpCandidate = ipInput.trim().ifBlank { detectedGatewayIp }
    val isIpValid = IoTNetworkDefaults.isValidPrivateLanIpv4(resolvedIpCandidate)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // BENTO 0: REAL-TIME PHONE WI-FI STATUS CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("diagnostics_wifi_status_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isWifiConnected) MaterialTheme.colorScheme.surface else WarningAmberBg
            ),
            border = BorderStroke(
                1.dp,
                if (isWifiConnected) MaterialTheme.colorScheme.outlineVariant else WarningAmberBorder
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (isWifiConnected) GoogleGreen.copy(alpha = 0.14f)
                            else WarningAmberBorder.copy(alpha = 0.22f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isWifiConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = if (isWifiConnected) GoogleGreen else WarningAmberIcon,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isWifiConnected) {
                            stringResource(id = R.string.diag_wifi_active_title)
                        } else {
                            stringResource(id = R.string.diag_wifi_disconnected_title)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isWifiConnected) MaterialTheme.colorScheme.onSurface else WarningAmberTitle
                    )
                    Text(
                        text = if (isWifiConnected) {
                            stringResource(id = R.string.diag_wifi_gateway_format, detectedGatewayIp)
                        } else {
                            stringResource(id = R.string.diag_wifi_connect_prompt)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isWifiConnected) MaterialTheme.colorScheme.onSurfaceVariant else WarningAmberBody,
                        lineHeight = 16.sp
                    )
                }

                Button(
                    onClick = onOpenWifiSettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isWifiConnected) GoogleBlue else WarningAmberButton
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isWifiConnected) {
                            stringResource(id = R.string.diag_wifi_networks_btn)
                        } else {
                            stringResource(id = R.string.ribbon_btn_connect)
                        },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // BENTO 1: DARK SLATE MASTER TELEMETRY BENTO
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("diagnostics_telemetry_bento"),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = BentoCharcoal),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.diag_telemetry_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = when {
                                !isWifiConnected -> stringResource(id = R.string.diag_telemetry_waiting_wifi)
                                effectiveConnected && metrics.isSimulated -> stringResource(
                                    id = R.string.diag_telemetry_sim_format,
                                    telemetry.wifiRssi
                                )
                                effectiveConnected -> stringResource(
                                    id = R.string.diag_telemetry_active_format,
                                    telemetry.wifiRssi
                                )
                                else -> stringResource(id = R.string.diag_telemetry_no_link)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = BentoSubtleText
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (effectiveConnected) BentoMintLight else GoogleRed.copy(alpha = 0.2f)
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
                                    .background(if (effectiveConnected) BentoMintDark else GoogleRed)
                            )
                            Text(
                                text = if (effectiveConnected) {
                                    stringResource(id = R.string.diag_badge_online)
                                } else {
                                    stringResource(id = R.string.diag_badge_offline)
                                },
                                color = if (effectiveConnected) BentoMintDark else GoogleRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BentoStatTile(
                        title = stringResource(id = R.string.diag_tile_latency),
                        value = if (effectiveConnected) "${metrics.latencyMs} ms" else "-- ms",
                        badge = if (effectiveConnected) {
                            stringResource(id = R.string.diag_tile_stable)
                        } else {
                            stringResource(id = R.string.diag_tile_waiting)
                        },
                        badgeBg = BentoMintLight,
                        badgeColor = BentoMintDark,
                        modifier = Modifier.weight(1f)
                    )

                    BentoStatTile(
                        title = stringResource(id = R.string.diag_tile_received),
                        value = "${metrics.packetsReceived}",
                        badge = stringResource(id = R.string.diag_tile_rx_badge),
                        badgeBg = BentoLavenderLight,
                        badgeColor = BentoLavenderDark,
                        modifier = Modifier.weight(1f)
                    )

                    BentoStatTile(
                        title = stringResource(id = R.string.diag_tile_sent),
                        value = "${metrics.packetsSent}",
                        badge = stringResource(id = R.string.diag_tile_tx_badge),
                        badgeBg = BentoBlueLight,
                        badgeColor = GoogleBlue,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // BENTO 2: NETWORK CONFIGURATION
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("diagnostics_config_card"),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(GoogleBlue.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Wifi,
                            contentDescription = null,
                            tint = GoogleBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(id = R.string.diag_config_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(id = R.string.diag_config_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = ipInput,
                        onValueChange = { ipInput = it },
                        label = { Text(stringResource(id = R.string.diag_field_ip_label)) },
                        placeholder = { Text(detectedGatewayIp) },
                        isError = !isIpValid,
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1.8f)
                    )

                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { portInput = it.filter { ch -> ch.isDigit() }.take(5) },
                        label = { Text(stringResource(id = R.string.diag_field_port_label)) },
                        placeholder = { Text(IoTNetworkDefaults.DEFAULT_TCP_PORT.toString()) },
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                }

                if (!isIpValid) {
                    Text(
                        text = stringResource(id = R.string.diag_invalid_ip_error),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                OutlinedTextField(
                    value = streamUrlInput,
                    onValueChange = { streamUrlInput = it },
                    label = { Text(stringResource(id = R.string.diag_field_stream_label)) },
                    placeholder = { Text(IoTNetworkDefaults.buildStreamUrl(resolvedIpCandidate)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = null,
                            tint = GoogleBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val apIp = IoTNetworkDefaults.DEFAULT_AP_IP
                                ipInput = apIp
                                portInput = IoTNetworkDefaults.DEFAULT_TCP_PORT.toString()
                                streamUrlInput = IoTNetworkDefaults.buildStreamUrl(apIp)
                            }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "ESP32 AP (${IoTNetworkDefaults.DEFAULT_AP_IP})",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                ipInput = detectedGatewayIp
                                portInput = IoTNetworkDefaults.DEFAULT_TCP_PORT.toString()
                                streamUrlInput = IoTNetworkDefaults.buildStreamUrl(detectedGatewayIp)
                            }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Gateway ($detectedGatewayIp)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        val safeIp = IoTNetworkDefaults.sanitizeLocalIp(resolvedIpCandidate, detectedGatewayIp)
                        val safePort = IoTNetworkDefaults.sanitizePort(
                            portInput.toIntOrNull() ?: IoTNetworkDefaults.DEFAULT_TCP_PORT
                        )
                        val safeStream = IoTNetworkDefaults.sanitizeStreamUrl(streamUrlInput, safeIp)
                        onSaveConfig(safeIp, safePort, safeStream)
                    },
                    enabled = isWifiConnected && isIpValid,
                    colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isWifiConnected) {
                            stringResource(id = R.string.diag_save_connect_btn)
                        } else {
                            stringResource(id = R.string.diag_requires_wifi_btn)
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                OutlinedButton(
                    onClick = onSwitchToSimulation,
                    enabled = isWifiConnected,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Default.Cast, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.fpv_start_simulation),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun BentoStatTile(
    title: String,
    value: String,
    badge: String,
    badgeBg: Color,
    badgeColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = BentoCharcoalSurface,
        border = BorderStroke(1.dp, BentoCharcoalBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = BentoSubtleText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = badgeBg
            ) {
                Text(
                    text = badge,
                    color = badgeColor,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}
