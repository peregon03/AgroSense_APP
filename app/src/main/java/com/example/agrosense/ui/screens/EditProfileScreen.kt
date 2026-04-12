package com.example.agrosense.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agrosense.ui.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    vm: AuthViewModel,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    val user = state.user

    // Campos del perfil
    var firstName by remember(user) { mutableStateOf(user?.first_name ?: "") }
    var lastName  by remember(user) { mutableStateOf(user?.last_name  ?: "") }
    var email     by remember(user) { mutableStateOf(user?.email      ?: "") }

    // Campos de contraseña
    var currentPassword  by remember { mutableStateOf("") }
    var newPassword      by remember { mutableStateOf("") }
    var confirmPassword  by remember { mutableStateOf("") }
    var showCurrent      by remember { mutableStateOf(false) }
    var showNew          by remember { mutableStateOf(false) }
    var showConfirm      by remember { mutableStateOf(false) }

    // Feedback — logica original intacta
    var profileMsg  by remember { mutableStateOf<String?>(null) }
    var profileErr  by remember { mutableStateOf<String?>(null) }
    var passwordMsg by remember { mutableStateOf<String?>(null) }
    var passwordErr by remember { mutableStateOf<String?>(null) }
    var savingProfile  by remember { mutableStateOf(false) }
    var savingPassword by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar perfil", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = MaterialTheme.colorScheme.primary,
                    titleContentColor          = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {

            // ── Hero header con avatar ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    )
                    .padding(vertical = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Avatar circular
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = buildString {
                                user?.first_name?.firstOrNull()?.let { append(it.uppercaseChar()) }
                                user?.last_name?.firstOrNull()?.let { append(it.uppercaseChar()) }
                                if (isEmpty()) append("?")
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    // Nombre completo actual
                    Text(
                        text = if (user != null) "${user.first_name} ${user.last_name}"
                        else "Cargando...",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    // Correo actual
                    Text(
                        text = user?.email ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {

                // ── Seccion: Informacion personal ────────────────────────────
                ProfileSectionCard(
                    title = "Informacion personal",
                    icon  = Icons.Default.Person
                ) {
                    // Campo Nombre
                    OutlinedTextField(
                        value         = firstName,
                        onValueChange = { firstName = it; profileMsg = null; profileErr = null },
                        label         = { Text("Nombre") },
                        leadingIcon   = { Icon(Icons.Default.Person, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary) },
                        singleLine    = true,
                        shape         = RoundedCornerShape(14.dp),
                        modifier      = Modifier.fillMaxWidth()
                    )
                    // Campo Apellido
                    OutlinedTextField(
                        value         = lastName,
                        onValueChange = { lastName = it; profileMsg = null; profileErr = null },
                        label         = { Text("Apellido") },
                        leadingIcon   = { Icon(Icons.Default.Person, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary) },
                        singleLine    = true,
                        shape         = RoundedCornerShape(14.dp),
                        modifier      = Modifier.fillMaxWidth()
                    )
                    // Campo Correo
                    OutlinedTextField(
                        value         = email,
                        onValueChange = { email = it; profileMsg = null; profileErr = null },
                        label         = { Text("Correo electronico") },
                        leadingIcon   = { Icon(Icons.Default.Email, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary) },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape         = RoundedCornerShape(14.dp),
                        modifier      = Modifier.fillMaxWidth()
                    )

                    // Mensajes de feedback
                    profileErr?.let {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(it,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                    profileMsg?.let {
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(it,
                                color = Color(0xFF2E7D32),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }

                    // Boton guardar — logica original intacta
                    Button(
                        onClick = {
                            savingProfile = true
                            profileMsg    = null
                            profileErr    = null
                            vm.updateProfile(
                                firstName = firstName,
                                lastName  = lastName,
                                email     = email,
                                onSuccess = {
                                    savingProfile = false
                                    profileMsg    = "Perfil actualizado correctamente"
                                },
                                onError   = { msg ->
                                    savingProfile = false
                                    profileErr    = msg
                                }
                            )
                        },
                        enabled  = !savingProfile,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape    = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = if (savingProfile) "Guardando..." else "Guardar cambios",
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 15.sp
                        )
                    }
                }

                // ── Seccion: Cambiar contrasena ──────────────────────────────
                ProfileSectionCard(
                    title = "Seguridad",
                    icon  = Icons.Default.Lock
                ) {
                    // Contrasena actual
                    ProfilePasswordField(
                        value         = currentPassword,
                        onValueChange = { currentPassword = it; passwordMsg = null; passwordErr = null },
                        label         = "Contrasena actual",
                        visible       = showCurrent,
                        onToggle      = { showCurrent = !showCurrent }
                    )
                    // Nueva contrasena
                    ProfilePasswordField(
                        value         = newPassword,
                        onValueChange = { newPassword = it; passwordMsg = null; passwordErr = null },
                        label         = "Nueva contrasena",
                        visible       = showNew,
                        onToggle      = { showNew = !showNew }
                    )
                    // Confirmar contrasena
                    ProfilePasswordField(
                        value         = confirmPassword,
                        onValueChange = { confirmPassword = it; passwordMsg = null; passwordErr = null },
                        label         = "Confirmar nueva contrasena",
                        visible       = showConfirm,
                        onToggle      = { showConfirm = !showConfirm }
                    )

                    // Mensajes de feedback
                    passwordErr?.let {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(it,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                    passwordMsg?.let {
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(it,
                                color = Color(0xFF2E7D32),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }

                    // Boton cambiar contrasena — logica original intacta
                    Button(
                        onClick = {
                            if (newPassword != confirmPassword) {
                                passwordErr = "Las contrasenas nuevas no coinciden"
                                return@Button
                            }
                            if (newPassword.length < 6) {
                                passwordErr = "La contrasena debe tener al menos 6 caracteres"
                                return@Button
                            }
                            savingPassword = true
                            passwordMsg    = null
                            passwordErr    = null
                            vm.changePassword(
                                currentPassword = currentPassword,
                                newPassword     = newPassword,
                                onSuccess = {
                                    savingPassword  = false
                                    passwordMsg     = "Contrasena cambiada correctamente"
                                    currentPassword = ""
                                    newPassword     = ""
                                    confirmPassword = ""
                                },
                                onError = { msg ->
                                    savingPassword = false
                                    passwordErr    = msg
                                }
                            )
                        },
                        enabled  = !savingPassword,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape    = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = if (savingPassword) "Cambiando..." else "Cambiar contrasena",
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 15.sp
                        )
                    }
                }

                // ── Boton Cerrar Sesion ──────────────────────────────────────
                // NUEVO: agregado aqui segun requerimiento; llama vm.logout() igual que ProfileScreen
                OutlinedButton(
                    onClick  = { vm.logout() },
                    enabled  = !state.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape  = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Filled.ExitToApp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (state.isLoading) "Cerrando..." else "Cerrar sesion",
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 15.sp
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun ProfileSectionCard(
    title   : String,
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    content : @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier            = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Encabezado de seccion con icono
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text       = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 15.sp,
                    color      = MaterialTheme.colorScheme.onSurface
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
private fun ProfilePasswordField(
    value         : String,
    onValueChange : (String) -> Unit,
    label         : String,
    visible       : Boolean,
    onToggle      : () -> Unit
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label) },
        leadingIcon   = {
            Icon(Icons.Default.Lock, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary)
        },
        singleLine    = true,
        visualTransformation = if (visible) VisualTransformation.None
        else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon  = {
            IconButton(onClick = onToggle) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Ocultar" else "Mostrar"
                )
            }
        },
        shape    = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    )
}