package com.example.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.PersonalDataSummary
import com.example.presentation.ui.theme.GoogleBlue
import com.example.presentation.ui.theme.GoogleRed
import com.example.presentation.ui.theme.WarningAmberBg
import com.example.presentation.ui.theme.WarningAmberBody
import com.example.presentation.ui.theme.WarningAmberBorder
import com.example.presentation.ui.theme.WarningAmberTitle

/**
 * Natural, clear, and accessible Privacy and Safety dialog
 * shown exclusively on the Login screen.
 */
@Composable
fun ComplianceAndPrivacyDialog(
    personalDataSummary: PersonalDataSummary,
    onDeleteAccountAndData: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var confirmDeletionStep by remember { mutableStateOf(false) }

    val tabs = listOf(
        "Tu Privacidad",
        "Uso Seguro",
        "Cómo Funciona"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .testTag("compliance_privacy_dialog"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(GoogleBlue.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PrivacyTip,
                                contentDescription = "Privacidad y Seguridad",
                                tint = GoogleBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "Privacidad y Seguridad",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("compliance_dialog_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar ventana de privacidad y seguridad"
                        )
                    }
                }

                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }

                HorizontalDivider()

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp, max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTab) {
                        0 -> PrivacyTabContent(
                            summary = personalDataSummary,
                            confirmDeletionStep = confirmDeletionStep,
                            onRequestDeleteConfirmation = { confirmDeletionStep = true },
                            onCancelDeleteConfirmation = { confirmDeletionStep = false },
                            onConfirmDeleteAccountAndData = onDeleteAccountAndData
                        )
                        1 -> RobotSafetyManualTabContent()
                        2 -> TechnicalTransparencyTabContent()
                    }
                }

                HorizontalDivider()

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        text = "Entendido",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyTabContent(
    summary: PersonalDataSummary,
    confirmDeletionStep: Boolean,
    onRequestDeleteConfirmation: () -> Unit,
    onCancelDeleteConfirmation: () -> Unit,
    onConfirmDeleteAccountAndData: () -> Unit
) {
    ComplianceSectionCard(
        title = "1. ¿Qué datos guardamos y para qué?",
        badge = "SOLO EN TU CELULAR",
        body = "• Cuando eliges tu cuenta para entrar, la app solo recuerda tu nombre, tu correo electrónico y la dirección IP del auto para que puedas conectarte y manejarlo.\n" +
            "• Tus datos nunca se suben a internet ni se comparten con servidores externos: todo queda guardado únicamente dentro de tu propio teléfono."
    )

    ComplianceSectionCard(
        title = "2. ¿Cómo se protege tu información?",
        badge = "PROTECCIÓN AUTOMÁTICA",
        body = "• Guardado bajo llave: La información de tu sesión se guarda protegida con cifrado de seguridad dentro del celular, nunca como texto descubierto.\n" +
            "• Cierre automático: Para tu tranquilidad, la sesión dura como máximo 8 horas y luego se cierra sola para que nadie más use tu cuenta por descuido.\n" +
            "• Estudiantes y menores de edad: Si eres menor de edad, usa la aplicación con el acompañamiento de tu profesor, mamá, papá o adulto responsable."
    )

    // Simple, clear view & delete card for local data
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = GoogleBlue,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Revisa o borra tus datos cuando quieras",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = if (summary.hasActiveOperatorSession) {
                    "• Cuenta guardada: ${summary.operatorName} - ${summary.operatorEmail}\n" +
                        "• Protección en el teléfono: Activa con datos cifrados\n" +
                        "• Dirección IP del auto recordada: ${summary.savedVehicleIp}"
                } else {
                    "• Cuenta guardada: Ninguna sesión activa en este momento.\n" +
                        "• Protección en el teléfono: Activa con datos cifrados\n" +
                        "• Dirección IP del auto recordada: ${summary.savedVehicleIp}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )

            if (!confirmDeletionStep) {
                OutlinedButton(
                    onClick = onRequestDeleteConfirmation,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoogleRed),
                    border = BorderStroke(1.dp, GoogleRed.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("delete_personal_data_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Borrar todos mis datos de este celular",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = WarningAmberBg,
                    border = BorderStroke(1.dp, WarningAmberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "¿Quieres borrar los datos guardados en este celular?",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = WarningAmberTitle
                        )
                        Text(
                            text = "Se eliminarán de inmediato cualquier sesión guardada, tu correo y los ajustes de red del auto en este teléfono.",
                            style = MaterialTheme.typography.bodySmall,
                            color = WarningAmberBody
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onCancelDeleteConfirmation,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text("Cancelar", fontSize = 12.sp)
                            }
                            Button(
                                onClick = onConfirmDeleteAccountAndData,
                                colors = ButtonDefaults.buttonColors(containerColor = GoogleRed),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("confirm_delete_personal_data_button")
                            ) {
                                Text("Sí, borrar todo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RobotSafetyManualTabContent() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = WarningAmberBg,
        border = BorderStroke(1.dp, WarningAmberBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.HealthAndSafety,
                contentDescription = "Consejos de seguridad para usar el auto",
                tint = WarningAmberTitle,
                modifier = Modifier.size(22.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Maneja siempre en un espacio seguro y despejado",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = WarningAmberTitle
                )
                Text(
                    text = "Usa el auto robótico en suelos planos, bajo supervisión directa y lejos de escaleras, calles con vehículos, niños pequeños o mascotas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningAmberBody,
                    lineHeight = 16.sp
                )
            }
        }
    }

    ComplianceSectionCard(
        title = "Freno de emergencia y parada automática",
        badge = "SEGURIDAD ACTIVA",
        body = "• Botón de Parada: En la pantalla de manejo tienes un botón rojo de emergencia que detiene las ruedas al instante.\n" +
            "• Freno automático si se corta el Wi-Fi: Si te alejas demasiado o se pierde la señal por más de 1,5 segundos mientras el auto avanza, los motores se detienen solos automáticamente para evitar choques.\n" +
            "• Interruptor del auto: El freno desde el celular es una gran ayuda, pero para apagar el vehículo por completo recuerda usar siempre el interruptor físico en el chasis del auto."
    )

    ComplianceSectionCard(
        title = "Cuidado de la batería y del equipo",
        badge = "CUIDADO BÁSICO",
        body = "• Utiliza baterías en buen estado y con protección contra sobrecargas o cortocircuitos.\n" +
            "• Al terminar las pruebas o la clase, apaga el auto y desconecta la batería para evitar que se caliente o se descargue de más.\n" +
            "• Cuando una batería cumpla su vida útil, llévala a un punto limpio de reciclaje electrónico y nunca la botes a la basura común."
    )
}

@Composable
private fun TechnicalTransparencyTabContent() {
    ComplianceSectionCard(
        title = "Buenas prácticas de calidad y facilidad de uso",
        badge = "DISEÑO AMIGABLE",
        body = "• Accesibilidad para todos: Diseñamos botones amplios y cómodos de tocar, textos con buen contraste y avisos que siempre combinan íconos y palabras claras, para que nadie dependa solo de distinguir un color.\n" +
            "• Red local protegida: La aplicación evita conexiones inseguras hacia internet y se comunica únicamente con la dirección local de tu auto robótico."
    )

    ComplianceSectionCard(
        title = "Herramientas utilizadas y aviso de errores",
        badge = "SOPORTE",
        body = "• Esta app fue creada con tecnologías oficiales y abiertas para Android: Jetpack Compose para la interfaz visual, AndroidX Security para la protección de datos en el teléfono y Kotlin Coroutines para la comunicación rápida con el auto.\n" +
            "• ¿Encontraste algún problema?: Si notas cualquier error en la pantalla o en la respuesta del auto, avísale al profesor o al encargado del taller indicando qué ocurrió para poder solucionarlo."
    )
}

@Composable
private fun ComplianceSectionCard(
    title: String,
    badge: String,
    body: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = GoogleBlue.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = badge,
                        color = GoogleBlue,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )
        }
    }
}
