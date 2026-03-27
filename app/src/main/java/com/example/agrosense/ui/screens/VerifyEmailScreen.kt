package com.example.agrosense.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agrosense.ui.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyEmailScreen(
    vm: AuthViewModel,
    email: String,
    onSuccess: () -> Unit,
    onBack: () -> Unit
) {
    var code       by remember { mutableStateOf("") }
    var isLoading  by remember { mutableStateOf(false) }
    var error      by remember { mutableStateOf<String?>(null) }
    var resendMsg  by remember { mutableStateOf<String?>(null) }
    var resending  by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verificar correo", fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 28.dp),
            verticalArrangement   = Arrangement.Center,
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            Text("📧", fontSize = 52.sp)
            Spacer(Modifier.height(16.dp))

            Text(
                "Revisa tu correo",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Enviamos un código de 6 dígitos a:\n$email",
                style      = MaterialTheme.typography.bodyMedium,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign  = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

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
                    textAlign    = TextAlign.Center,
                    fontSize     = 26.sp,
                    letterSpacing = 10.sp
                ),
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                isError  = error != null
            )

            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                    textAlign = TextAlign.Center)
            }
            resendMsg?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = Color(0xFF2E7D32), fontSize = 13.sp,
                    textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    isLoading = true
                    error     = null
                    resendMsg = null
                    vm.verifyEmail(
                        email     = email,
                        code      = code,
                        onSuccess = { isLoading = false; onSuccess() },
                        onError   = { msg -> isLoading = false; error = msg }
                    )
                },
                enabled  = !isLoading && code.length == 6,
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
                Text(if (isLoading) "Verificando..." else "Verificar cuenta",
                    fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick = {
                    resending = true
                    error     = null
                    resendMsg = null
                    vm.resendCode(
                        email     = email,
                        type      = "verify",
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
