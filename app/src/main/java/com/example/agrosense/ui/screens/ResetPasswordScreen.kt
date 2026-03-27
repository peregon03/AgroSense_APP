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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agrosense.ui.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetPasswordScreen(
    vm: AuthViewModel,
    email: String,
    onSuccess: () -> Unit,
    onBack: () -> Unit
) {
    var code           by remember { mutableStateOf("") }
    var newPassword    by remember { mutableStateOf("") }
    var confirmPwd     by remember { mutableStateOf("") }
    var showNew        by remember { mutableStateOf(false) }
    var showConfirm    by remember { mutableStateOf(false) }
    var isLoading      by remember { mutableStateOf(false) }
    var error          by remember { mutableStateOf<String?>(null) }
    var resendMsg      by remember { mutableStateOf<String?>(null) }
    var resending      by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nueva contraseña", fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 28.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🔒", fontSize = 52.sp)

            Text(
                "Restablece tu contraseña",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center
            )
            Text(
                "Ingresa el código enviado a:\n$email",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Campo código
            OutlinedTextField(
                value         = code,
                onValueChange = {
                    if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                        code  = it
                        error = null
                    }
                },
                label           = { Text("Código de verificación") },
                singleLine      = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                textStyle       = LocalTextStyle.current.copy(
                    textAlign     = TextAlign.Center,
                    fontSize      = 26.sp,
                    letterSpacing = 10.sp
                ),
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                isError  = error != null
            )

            // Nueva contraseña
            OutlinedTextField(
                value         = newPassword,
                onValueChange = { newPassword = it; error = null },
                label         = { Text("Nueva contraseña") },
                singleLine    = true,
                visualTransformation = if (showNew) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon  = {
                    IconButton(onClick = { showNew = !showNew }) {
                        Icon(
                            if (showNew) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                isError  = error != null
            )

            // Confirmar contraseña
            OutlinedTextField(
                value         = confirmPwd,
                onValueChange = { confirmPwd = it; error = null },
                label         = { Text("Confirmar contraseña") },
                singleLine    = true,
                visualTransformation = if (showConfirm) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon  = {
                    IconButton(onClick = { showConfirm = !showConfirm }) {
                        Icon(
                            if (showConfirm) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                isError  = error != null
            )

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                    textAlign = TextAlign.Center)
            }
            resendMsg?.let {
                Text(it, color = Color(0xFF2E7D32), fontSize = 13.sp,
                    textAlign = TextAlign.Center)
            }

            Button(
                onClick = {
                    if (newPassword != confirmPwd) { error = "Las contraseñas no coinciden"; return@Button }
                    if (newPassword.length < 6)    { error = "Mínimo 6 caracteres"; return@Button }
                    isLoading = true
                    error     = null
                    vm.resetPassword(
                        email       = email,
                        code        = code,
                        newPassword = newPassword,
                        onSuccess   = { isLoading = false; onSuccess() },
                        onError     = { msg -> isLoading = false; error = msg }
                    )
                },
                enabled  = !isLoading && code.length == 6 && newPassword.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isLoading) "Guardando..." else "Restablecer contraseña",
                    fontWeight = FontWeight.SemiBold)
            }

            TextButton(
                onClick = {
                    resending = true
                    error     = null
                    resendMsg = null
                    vm.resendCode(
                        email     = email,
                        type      = "reset",
                        onSuccess = { resending = false; resendMsg = "Código reenviado a $email" },
                        onError   = { msg -> resending = false; error = msg }
                    )
                },
                enabled = !resending
            ) {
                Text(if (resending) "Enviando..." else "Reenviar código")
            }
        }
    }
}
