package com.example.agrosense.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
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
    onRegisterSensor: () -> Unit = {},
    onViewSensors: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onNavigateHome: () -> Unit = {},
) {
    val state by vm.state.collectAsState()
    val user = state.user

    LaunchedEffect(Unit) {
        vm.loadMe()
    }

    Scaffold(
        topBar = {
            Surface(
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "Foto de perfil",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "AgroSense",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },

        // BottomNavBar — "Panel" activo
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = true,
                    onClick  = onNavigateHome,
                    icon     = { Icon(Icons.Filled.Dashboard, contentDescription = "Panel") },
                    label    = { Text("Panel") }
                )
                // "Perfil" navega a EditProfileScreen, que ahora contiene Cerrar Sesion
                NavigationBarItem(
                    selected = false,
                    onClick  = onEditProfile,
                    icon     = { Icon(Icons.Filled.Edit, contentDescription = "Perfil") },
                    label    = { Text("Perfil") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick  = onViewSensors,
                    icon     = { Icon(Icons.Filled.Sensors, contentDescription = "Sensores") },
                    label    = { Text("Sensores") }
                )
            }
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {

            Spacer(Modifier.height(16.dp))

            // Error card — logica original intacta
            state.error?.let { err ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text     = err,
                        modifier = Modifier.padding(12.dp),
                        color    = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // Saludo con nombre del usuario
            Text(
                text       = "Bienvenido Nuevamente,",
                style      = MaterialTheme.typography.bodyMedium,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Text(
                text       = if (user != null) "${user.first_name} ${user.last_name}" else "...",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color      = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text       = "Acciones rapidas",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(12.dp))

            // Fila 1: Agregar sensor + Ver sensores
            Row(modifier = Modifier.fillMaxWidth()) {
                ActionCard(
                    modifier  = Modifier.weight(1f),
                    title     = "Agregar sensor",
                    subtitle  = "Buscar y vincular dispositivos",
                    icon      = Icons.Filled.AddCircle,
                    onClick   = onRegisterSensor      // logica original intacta
                )
                Spacer(Modifier.width(12.dp))
                ActionCard(
                    modifier  = Modifier.weight(1f),
                    title     = "Ver sensores",
                    subtitle  = "Listado y estado",
                    icon      = Icons.Filled.Sensors,
                    onClick   = onViewSensors          // logica original intacta
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text     = "AgroSense - Panel de usuario",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActionCard(
    modifier  : Modifier = Modifier,
    title     : String,
    subtitle  : String,
    icon      : ImageVector,
    onClick   : () -> Unit
) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick   = onClick
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
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}