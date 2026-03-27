package com.example.agrosense.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

    // Feedback
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
                    containerColor         = MaterialTheme.colorScheme.primary,
                    titleContentColor      = MaterialTheme.colorScheme.onPrimary,
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Sección: Información personal ──────────────────────────────
            SectionCard(title = "Información personal") {
                OutlinedTextField(
                    value         = firstName,
                    onValueChange = { firstName = it; profileMsg = null; profileErr = null },
                    label         = { Text("Nombre") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value         = lastName,
                    onValueChange = { lastName = it; profileMsg = null; profileErr = null },
                    label         = { Text("Apellido") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value         = email,
                    onValueChange = { email = it; profileMsg = null; profileErr = null },
                    label         = { Text("Correo electrónico") },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier      = Modifier.fillMaxWidth()
                )

                profileErr?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                profileMsg?.let {
                    Text(it, color = Color(0xFF2E7D32), fontSize = 13.sp)
                }

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
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    Text(if (savingProfile) "Guardando..." else "Guardar cambios")
                }
            }

            // ── Sección: Cambiar contraseña ────────────────────────────────
            SectionCard(title = "Cambiar contraseña") {
                PasswordField(
                    value         = currentPassword,
                    onValueChange = { currentPassword = it; passwordMsg = null; passwordErr = null },
                    label         = "Contraseña actual",
                    visible       = showCurrent,
                    onToggle      = { showCurrent = !showCurrent }
                )
                PasswordField(
                    value         = newPassword,
                    onValueChange = { newPassword = it; passwordMsg = null; passwordErr = null },
                    label         = "Nueva contraseña",
                    visible       = showNew,
                    onToggle      = { showNew = !showNew }
                )
                PasswordField(
                    value         = confirmPassword,
                    onValueChange = { confirmPassword = it; passwordMsg = null; passwordErr = null },
                    label         = "Confirmar nueva contraseña",
                    visible       = showConfirm,
                    onToggle      = { showConfirm = !showConfirm }
                )

                passwordErr?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                passwordMsg?.let {
                    Text(it, color = Color(0xFF2E7D32), fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        if (newPassword != confirmPassword) {
                            passwordErr = "Las contraseñas nuevas no coinciden"
                            return@Button
                        }
                        if (newPassword.length < 6) {
                            passwordErr = "La contraseña debe tener al menos 6 caracteres"
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
                                passwordMsg     = "Contraseña cambiada correctamente"
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
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    Text(if (savingPassword) "Cambiando..." else "Cambiar contraseña")
                }
            }
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier              = Modifier.padding(16.dp),
            verticalArrangement   = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggle: () -> Unit
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label) },
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
        modifier = Modifier.fillMaxWidth()
    )
}
