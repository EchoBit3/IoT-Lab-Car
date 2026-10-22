package com.example

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.ConnectionMetrics
import com.example.domain.model.VehicleTelemetry
import com.example.presentation.ui.components.ComplianceAndPrivacyDialog
import com.example.presentation.ui.components.ErgonomicCockpitControls
import com.example.presentation.ui.components.IntegratedCameraRadarView
import com.example.presentation.ui.components.PermissionsDialog
import com.example.presentation.ui.screens.AuthScreen
import com.example.presentation.ui.screens.DiagnosticsScreen
import com.example.presentation.ui.theme.BentoMintDark
import com.example.presentation.ui.theme.BentoMintLight
import com.example.presentation.ui.theme.GoogleBlue
import com.example.presentation.ui.theme.GoogleGreen
import com.example.presentation.ui.theme.GoogleRed
import com.example.presentation.ui.theme.MyApplicationTheme
import com.example.presentation.ui.theme.WarningAmberBg
import com.example.presentation.ui.theme.WarningAmberBody
import com.example.presentation.ui.theme.WarningAmberBorder
import com.example.presentation.ui.theme.WarningAmberButton
import com.example.presentation.ui.theme.WarningAmberIcon
import com.example.presentation.ui.theme.WarningAmberTitle
import com.example.presentation.viewmodel.VehicleLabViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                IoTCarApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IoTCarApp(viewModel: VehicleLabViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val operatorSession by viewModel.operatorSession.collectAsStateWithLifecycle()
    val isWifiConnected by viewModel.isWifiConnected.collectAsStateWithLifecycle()
    val detectedGatewayIp by viewModel.detectedGatewayIp.collectAsStateWithLifecycle()
    val arePermissionsGranted by viewModel.arePermissionsGranted.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val showPermissionsDialog by viewModel.showPermissionsDialog.collectAsStateWithLifecycle()
    val showComplianceDialog by viewModel.showComplianceDialog.collectAsStateWithLifecycle()
    val selectedSpeedPct by viewModel.selectedSpeedPct.collectAsStateWithLifecycle()
    val activeGear by viewModel.activeGear.collectAsStateWithLifecycle()
    val joystickThrottlePct by viewModel.joystickThrottlePct.collectAsStateWithLifecycle()
    val joystickSteeringPct by viewModel.joystickSteeringPct.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START) {
                viewModel.refreshWifiStatus()
                viewModel.checkPermissionsState(promptIfMissing = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val wifiSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshWifiStatus()
        val cfg = viewModel.getSavedVehicleConfig()
        viewModel.saveVehicleSettings(cfg.first, cfg.second, cfg.third)
    }

    val openWifiSettings: () -> Unit = {
        try {
            wifiSettingsLauncher.launch(Intent(Settings.ACTION_WIFI_SETTINGS))
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
                try {
                    wifiSettingsLauncher.launch(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                } catch (_: Exception) {}
            }
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        viewModel.onPermissionsResult(grantResults)
    }

    val launchNativePermissionsPopup = {
        val perms = viewModel.getRequiredRuntimePermissions().toTypedArray()
        permissionsLauncher.launch(perms)
    }

    if (showPermissionsDialog) {
        PermissionsDialog(
            onDismiss = { viewModel.dismissPermissionsDialog() },
            onGrantRequested = {
                viewModel.dismissPermissionsDialog()
                try {
                    launchNativePermissionsPopup()
                } catch (_: Exception) {
                    viewModel.onPermissionsResult()
                }
            }
        )
    }

    if (operatorSession == null && showComplianceDialog) {
        ComplianceAndPrivacyDialog(
            personalDataSummary = viewModel.getPersonalDataSummary(),
            onDeleteAccountAndData = { viewModel.deleteOperatorAccountAndData() },
            onDismiss = { viewModel.dismissComplianceDialog() }
        )
    }

    if (operatorSession == null) {
        AuthScreen(
            onGoogleSignInClicked = { name, email ->
                viewModel.loginWithGoogle(name, email)
            },
            isWifiConnected = isWifiConnected,
            arePermissionsGranted = arePermissionsGranted,
            onOpenWifiSettings = openWifiSettings,
            onRequestPermissions = {
                viewModel.requestPermissionsDialog()
            },
            onOpenComplianceAndPrivacy = {
                viewModel.openComplianceDialog()
            }
        )
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(38.dp),
                                shape = RoundedCornerShape(11.dp),
                                shadowElevation = 3.dp
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_launcher_background),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                        contentDescription = stringResource(id = R.string.app_name),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Text(
                                text = stringResource(id = R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        if (!arePermissionsGranted) {
                            IconButton(
                                onClick = { viewModel.requestPermissionsDialog() },
                                modifier = Modifier.testTag("topbar_permissions_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = stringResource(id = R.string.topbar_permissions_cd),
                                    tint = GoogleBlue
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.logout() },
                            modifier = Modifier.testTag("topbar_logout_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = stringResource(id = R.string.topbar_logout_cd),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        icon = { Icon(Icons.Default.SportsEsports, contentDescription = null) },
                        label = { Text(stringResource(id = R.string.nav_tab_cockpit)) }
                    )
                    NavigationBarItem(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        icon = { Icon(Icons.Default.Router, contentDescription = null) },
                        label = { Text(stringResource(id = R.string.nav_tab_diagnostics)) }
                    )
                }
            }
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.TopCenter
            ) {
                val isWideScreen = maxWidth >= 680.dp

                if (selectedTabIndex == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = if (isWideScreen) 1080.dp else 640.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!isWifiConnected) {
                            WifiDisconnectedCockpitAlert(
                                onOpenWifiSettings = openWifiSettings,
                                onRefresh = { viewModel.refreshWifiStatus() }
                            )
                        } else {
                            Esp32ConnectionRibbon(
                                metrics = metrics,
                                telemetry = telemetry,
                                detectedGatewayIp = detectedGatewayIp,
                                onConnectClicked = {
                                    val cfg = viewModel.getSavedVehicleConfig()
                                    viewModel.saveVehicleSettings(cfg.first, cfg.second, cfg.third)
                                    openWifiSettings()
                                },
                                onDisconnectClicked = {
                                    viewModel.disconnectFromVehicle()
                                }
                            )
                        }

                        if (isWideScreen) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                IntegratedCameraRadarView(
                                    isConnected = metrics.isConnected && isWifiConnected,
                                    telemetry = telemetry,
                                    activeGear = activeGear,
                                    latencyMs = metrics.latencyMs,
                                    isSimulated = metrics.isSimulated,
                                    isWifiConnected = isWifiConnected,
                                    onStartSimulationRequested = {
                                        viewModel.switchToSimulation()
                                    },
                                    onResetObstacleRequested = {
                                        viewModel.resetSimulationObstacle()
                                    },
                                    modifier = Modifier.weight(1f)
                                )

                                ErgonomicCockpitControls(
                                    isConnected = metrics.isConnected,
                                    isWifiConnected = isWifiConnected,
                                    selectedSpeedPct = selectedSpeedPct,
                                    activeGear = activeGear,
                                    throttlePct = joystickThrottlePct,
                                    steeringPct = joystickSteeringPct,
                                    velocityMps = telemetry.velocityMps,
                                    leftMotorPwm = telemetry.leftMotorPwm,
                                    rightMotorPwm = telemetry.rightMotorPwm,
                                    onJoystickMoved = { throttle, steering ->
                                        viewModel.onJoystickVectorChanged(throttle, steering)
                                    },
                                    onSpeedSelected = { speed ->
                                        viewModel.setSpeed(speed)
                                    },
                                    onEmergencyStop = {
                                        viewModel.emergencyStop()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            IntegratedCameraRadarView(
                                isConnected = metrics.isConnected && isWifiConnected,
                                telemetry = telemetry,
                                activeGear = activeGear,
                                latencyMs = metrics.latencyMs,
                                isSimulated = metrics.isSimulated,
                                isWifiConnected = isWifiConnected,
                                onStartSimulationRequested = {
                                    viewModel.switchToSimulation()
                                },
                                onResetObstacleRequested = {
                                    viewModel.resetSimulationObstacle()
                                }
                            )

                            ErgonomicCockpitControls(
                                isConnected = metrics.isConnected,
                                isWifiConnected = isWifiConnected,
                                selectedSpeedPct = selectedSpeedPct,
                                activeGear = activeGear,
                                throttlePct = joystickThrottlePct,
                                steeringPct = joystickSteeringPct,
                                velocityMps = telemetry.velocityMps,
                                leftMotorPwm = telemetry.leftMotorPwm,
                                rightMotorPwm = telemetry.rightMotorPwm,
                                onJoystickMoved = { throttle, steering ->
                                    viewModel.onJoystickVectorChanged(throttle, steering)
                                },
                                onSpeedSelected = { speed ->
                                    viewModel.setSpeed(speed)
                                },
                                onEmergencyStop = {
                                    viewModel.emergencyStop()
                                }
                            )
                        }
                    }
                } else {
                    DiagnosticsScreen(
                        metrics = metrics,
                        telemetry = telemetry,
                        detectedGatewayIp = detectedGatewayIp,
                        isWifiConnected = isWifiConnected,
                        onOpenWifiSettings = openWifiSettings,
                        onSaveConfig = { ip, port, streamUrl ->
                            viewModel.saveVehicleSettings(ip, port, streamUrl)
                            selectedTabIndex = 0
                        },
                        onSwitchToSimulation = {
                            viewModel.switchToSimulation()
                            selectedTabIndex = 0
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WifiDisconnectedCockpitAlert(
    onOpenWifiSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = WarningAmberBg,
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(WarningAmberBorder.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = stringResource(id = R.string.alert_wifi_disconnected_cd),
                    tint = WarningAmberIcon,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.alert_wifi_disconnected_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = WarningAmberTitle
                )
                Text(
                    text = stringResource(id = R.string.alert_wifi_disconnected_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningAmberBody,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(id = R.string.alert_wifi_refresh_cd),
                        tint = WarningAmberBody,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Button(
                    onClick = onOpenWifiSettings,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WarningAmberButton),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.alert_wifi_button),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun Esp32ConnectionRibbon(
    metrics: ConnectionMetrics,
    telemetry: VehicleTelemetry,
    detectedGatewayIp: String,
    onConnectClicked: () -> Unit,
    onDisconnectClicked: () -> Unit
) {
    val isOnline = metrics.isConnected
    val statusColor = if (metrics.isConnected) GoogleGreen else GoogleRed

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = when {
                                metrics.isConnected -> if (metrics.isSimulated) {
                                    stringResource(id = R.string.ribbon_status_simulated)
                                } else {
                                    stringResource(id = R.string.ribbon_status_online)
                                }
                                else -> stringResource(id = R.string.ribbon_status_disconnected)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                        if (isOnline && telemetry.batteryPct > 0) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = BentoMintLight
                            ) {
                                Text(
                                    text = "${telemetry.batteryPct}%",
                                    color = BentoMintDark,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (metrics.isConnected) {
                            stringResource(
                                id = R.string.ribbon_sub_connected_format,
                                metrics.latencyMs,
                                metrics.ipAddress
                            )
                        } else {
                            detectedGatewayIp
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (metrics.isConnected) {
                OutlinedButton(
                    onClick = onDisconnectClicked,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoogleRed),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(id = R.string.ribbon_btn_disconnect),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onConnectClicked,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(id = R.string.ribbon_btn_connect),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
