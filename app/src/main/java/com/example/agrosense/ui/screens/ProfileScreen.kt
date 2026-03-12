package com.example.agrosense.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.agrosense.ui.viewmodel.AuthViewModel

@Composable
fun ProfileScreen(
    vm: AuthViewModel,
    // ✅ este callback lo usas para navegar a la pantalla BLE ("ble")
    onRegisterSensor: () -> Unit = {},
    onViewSensors: () -> Unit = {},
    onViewCharts: () -> Unit = {},
    onEditProfile: () -> Unit = {}
) {
    val state by vm.state.collectAsState()
    val user = state.user

    LaunchedEffect(Unit) {
        vm.loadMe()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        // Header del perfil
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Sensors,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Bienvenido",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = if (user != null) "${user.first_name} ${user.last_name}" else "Cargando usuario...",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = user?.email ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Error
        state.error?.let { err ->
            Spacer(Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = err,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Acciones rápidas",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(10.dp))

        // Grid de botones (2x2)
        Row(modifier = Modifier.fillMaxWidth()) {
            ActionCard(
                modifier = Modifier.weight(1f),
                title = "Agregar sensor ",
                subtitle = "Buscar y vincular Dispositivos",
                icon = Icons.Filled.AddCircle,
                onClick = onRegisterSensor // ✅ aquí navegas a BLE
            )
            Spacer(Modifier.width(12.dp))
            ActionCard(
                modifier = Modifier.weight(1f),
                title = "Ver sensores",
                subtitle = "Listado y estado",
                icon = Icons.Filled.Sensors,
                onClick = onViewSensors
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            ActionCard(
                modifier = Modifier.weight(1f),
                title = "Datos y gráficas",
                subtitle = "Mediciones y reportes",
                icon = Icons.Filled.BarChart,
                onClick = onViewCharts
            )
            Spacer(Modifier.width(12.dp))
            ActionCard(
                modifier = Modifier.weight(1f),
                title = "Editar perfil",
                subtitle = "Actualizar tu información",
                icon = Icons.Filled.Edit,
                onClick = onEditProfile
            )
        }

        Spacer(Modifier.height(20.dp))

        Divider()

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { vm.logout() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            enabled = !state.isLoading
        ) {
            Icon(Icons.Filled.ExitToApp, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (state.isLoading) "Cerrando..." else "Cerrar sesión")
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = "AgroSense • Panel de usuario",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun ActionCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
