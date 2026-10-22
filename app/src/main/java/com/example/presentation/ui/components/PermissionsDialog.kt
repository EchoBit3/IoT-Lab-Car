package com.example.presentation.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.presentation.ui.theme.GoogleBlue

private data class SinglePermissionStep(
    val stepNumber: Int,
    val totalSteps: Int,
    val icon: ImageVector,
    val questionTitle: String,
    val description: String,
    val primaryAllowLabel: String,
    val secondaryAllowLabel: String,
    val denyLabel: String
)

/**
 * Sequential Individual Permission Popups that appear one by one at startup,
 * behaving like real Android OS individual permission prompts (1 of 3, 2 of 3, 3 of 3).
 * The user accepts or denies each permission individually before advancing to the next popup,
 * and once completed, delegates the actual Android runtime permission request to [onGrantRequested].
 */
@Composable
fun PermissionsDialog(
    onDismiss: () -> Unit,
    onGrantRequested: () -> Unit
) {
    val permissionSteps = listOf(
        SinglePermissionStep(
            stepNumber = 1,
            totalSteps = 3,
            icon = Icons.Default.Wifi,
            questionTitle = "¿Permitir que IoT Lab Car busque dispositivos cercanos y se conecte por Wi-Fi?",
            description = "Necesario para verificar en todo momento que el celular esté conectado a una red Wi-Fi y enlazar en tiempo real con el vehículo ESP32.",
            primaryAllowLabel = "Permitir mientras la app está en uso",
            secondaryAllowLabel = "Solo esta vez",
            denyLabel = "No permitir"
        ),
        SinglePermissionStep(
            stepNumber = 2,
            totalSteps = 3,
            icon = Icons.Default.LocationOn,
            questionTitle = "¿Permitir que IoT Lab Car acceda a la ubicación de este dispositivo?",
            description = "Requerido por el sistema Android para detectar desconexiones de la red Wi-Fi en tiempo real y validar el estado del enlace con el auto.",
            primaryAllowLabel = "Mientras la app está en uso",
            secondaryAllowLabel = "Solo esta vez",
            denyLabel = "No permitir"
        ),
        SinglePermissionStep(
            stepNumber = 3,
            totalSteps = 3,
            icon = Icons.Default.NotificationsActive,
            questionTitle = "¿Permitir que IoT Lab Car te envíe notificaciones?",
            description = "Permite recibir alertas inmediatas de seguridad cuando los sensores ultrasónicos detecten un obstáculo cercano o se apague el Wi-Fi.",
            primaryAllowLabel = "Permitir",
            secondaryAllowLabel = "Solo esta vez",
            denyLabel = "No permitir"
        )
    )

    var currentIndex by rememberSaveable { mutableIntStateOf(0) }
    val currentStep = permissionSteps.getOrElse(currentIndex) { permissionSteps.last() }

    val advanceToNextOrFinish: () -> Unit = {
        if (currentIndex < permissionSteps.lastIndex) {
            currentIndex += 1
        } else {
            onGrantRequested()
        }
    }

    Dialog(
        onDismissRequest = {
            if (currentIndex < permissionSteps.lastIndex) {
                currentIndex += 1
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220)))
                        .togetherWith(fadeOut(animationSpec = tween(150)))
                },
                label = "individual_permission_popup"
            ) { step ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 380.dp)
                        .testTag("permissions_dialog"),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Step progress indicator pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = GoogleBlue.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "Permiso ${step.stepNumber} de ${step.totalSteps}",
                                    color = GoogleBlue,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Centered System Permission Icon
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(GoogleBlue.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = step.icon,
                                contentDescription = null,
                                tint = GoogleBlue,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Individual Permission Question & Clear Explanation
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = step.questionTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp
                            )
                            Text(
                                text = step.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }

                        // Step indicator dots (1 - 2 - 3)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (i in 1..step.totalSteps) {
                                val isCurrent = i == step.stepNumber
                                Box(
                                    modifier = Modifier
                                        .size(width = if (isCurrent) 20.dp else 7.dp, height = 7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isCurrent) GoogleBlue
                                            else MaterialTheme.colorScheme.outlineVariant
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Authentic Android OS Stacked Permission Action Buttons
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { advanceToNextOrFinish() },
                                colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("grant_permissions_button")
                            ) {
                                Text(
                                    text = step.primaryAllowLabel,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    textAlign = TextAlign.Center
                                )
                            }

                            OutlinedButton(
                                onClick = { advanceToNextOrFinish() },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, GoogleBlue.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 46.dp)
                            ) {
                                Text(
                                    text = step.secondaryAllowLabel,
                                    color = GoogleBlue,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    if (currentIndex < permissionSteps.lastIndex) {
                                        currentIndex += 1
                                    } else {
                                        onDismiss()
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 44.dp)
                                    .testTag("dismiss_permissions_button")
                            ) {
                                Text(
                                    text = step.denyLabel,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.5.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
