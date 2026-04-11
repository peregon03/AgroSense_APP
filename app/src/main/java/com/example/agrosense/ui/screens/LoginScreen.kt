package com.example.agrosense.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agrosense.ui.viewmodel.AuthViewModel

// ── Colores del tema AgroSense (extraídos del HTML) ──────────────────────────
private val AgroPrimary           = Color(0xFF0D631B)
private val AgroPrimaryDark       = Color(0xFF2E7D32)
private val AgroSurface           = Color(0xFFFCF9F8)
private val AgroSurfaceContainer  = Color(0xFFF0EDED)
private val AgroSurfaceContLow    = Color(0xFFF6F3F2)
private val AgroOnSurface         = Color(0xFF1B1C1C)
private val AgroOnSurfaceVariant  = Color(0xFF40493D)
private val AgroErrorContainer    = Color(0xFFFFDAD6)
private val AgroOnErrorContainer  = Color(0xFF93000A)
private val AgroError             = Color(0xFFBA1A1A)

// Gradiente orgánico: linear-gradient(135deg, #0d631b 0%, #2e7d32 100%)
private val OrganicGradient = Brush.linearGradient(
    colors = listOf(AgroPrimary, AgroPrimaryDark)
)

@Composable
fun LoginScreen(
    vm: AuthViewModel,
    onGoRegister: () -> Unit,
    onForgotPassword: () -> Unit = {},
    onNeedsVerification: (email: String) -> Unit = {}
) {
    // ── Estado — idéntico al original ────────────────────────────────────────
    var email       by remember { mutableStateOf("") }
    var pass        by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }
    val state       by vm.state.collectAsState()
    val focusManager = LocalFocusManager.current

    // Limpiar error al escribir — lógica intacta
    LaunchedEffect(email, pass) {
        if (state.error != null) vm.clearError()
    }

    // ── Raíz ─────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AgroSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Header / Branding ─────────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(AgroSurfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Eco,
                        contentDescription = "AgroSense logo",
                        tint = AgroPrimary,
                        modifier = Modifier.size(44.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "AgroSense",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 34.sp,
                    color = AgroOnSurface,
                    letterSpacing = (-0.5).sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Monitorea y cuida tu cultivo fácilmente",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AgroOnSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(32.dp))

            // ── Ilustración con capa rotada decorativa ────────────────────
            Box(modifier = Modifier.fillMaxWidth()) {
                // Sombra decorativa rotada (efecto HTML: -rotate-2 scale-105)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(192.dp)
                        .rotate(-2f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(AgroPrimary.copy(alpha = 0.07f))
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(192.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFFE8F5E9), Color(0xFFA5D6A7))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.Eco,
                                contentDescription = null,
                                tint = AgroPrimary.copy(alpha = 0.5f),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Tu cultivo, tu futuro",
                                color = AgroPrimary.copy(alpha = 0.7f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(36.dp))

            // ── Campo email ───────────────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Correo electrónico",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AgroOnSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
                TextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = {
                        Text(
                            "Ingresa tu correo electrónico",
                            color = AgroOnSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Email,
                            contentDescription = null,
                            tint = if (email.isNotEmpty()) AgroPrimary else AgroOnSurfaceVariant
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    isError = state.error != null,
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor     = AgroSurfaceContLow,
                        unfocusedContainerColor   = AgroSurfaceContLow,
                        errorContainerColor       = AgroErrorContainer,
                        focusedIndicatorColor     = Color.Transparent,
                        unfocusedIndicatorColor   = Color.Transparent,
                        errorIndicatorColor       = Color.Transparent,
                        focusedTextColor          = AgroOnSurface,
                        unfocusedTextColor        = AgroOnSurface,
                        cursorColor               = AgroPrimary,
                        focusedLeadingIconColor   = AgroPrimary,
                        unfocusedLeadingIconColor = AgroOnSurfaceVariant
                    )
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Campo contraseña ──────────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Contraseña",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AgroOnSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
                TextField(
                    value = pass,
                    onValueChange = { pass = it },
                    placeholder = {
                        Text(
                            "Ingresa tu contraseña",
                            color = AgroOnSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = if (pass.isNotEmpty()) AgroPrimary else AgroOnSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { passVisible = !passVisible }) {
                            Icon(
                                if (passVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passVisible) "Ocultar" else "Mostrar",
                                tint = AgroOnSurfaceVariant
                            )
                        }
                    },
                    visualTransformation = if (passVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            vm.login(email, pass, onNeedsVerification = onNeedsVerification)
                        }
                    ),
                    isError = state.error != null,
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor      = AgroSurfaceContLow,
                        unfocusedContainerColor    = AgroSurfaceContLow,
                        errorContainerColor        = AgroErrorContainer,
                        focusedIndicatorColor      = Color.Transparent,
                        unfocusedIndicatorColor    = Color.Transparent,
                        errorIndicatorColor        = Color.Transparent,
                        focusedTextColor           = AgroOnSurface,
                        unfocusedTextColor         = AgroOnSurface,
                        cursorColor                = AgroPrimary,
                        focusedLeadingIconColor    = AgroPrimary,
                        unfocusedLeadingIconColor  = AgroOnSurfaceVariant
                    )
                )
            }

            // ── Mensaje de error — lógica intacta ────────────────────────
            state.error?.let { rawError ->
                Spacer(Modifier.height(14.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AgroErrorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = AgroError,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = friendlyAuthError(rawError),
                            color = AgroOnErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // ¿Olvidaste tu contraseña? — alineado a la derecha
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = onForgotPassword) {
                    Text(
                        "¿Olvidaste tu contraseña?",
                        color = AgroPrimary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Botón con gradiente orgánico ──────────────────────────────
            val btnEnabled = !state.isLoading && email.isNotBlank() && pass.isNotBlank()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        brush = OrganicGradient,
                        alpha = if (btnEnabled) 1f else 0.45f
                    ),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        vm.login(email, pass, onNeedsVerification = onNeedsVerification)
                    },
                    modifier = Modifier.fillMaxSize(),
                    enabled = btnEnabled,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor         = Color.Transparent,
                        disabledContainerColor = Color.Transparent
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation  = 0.dp,
                        pressedElevation  = 0.dp,
                        disabledElevation = 0.dp
                    )
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color       = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text       = if (state.isLoading) "Ingresando..." else "Iniciar sesión",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                        color      = Color.White
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Footer: ¿No tienes cuenta? ────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "¿No tienes cuenta?",
                    style = MaterialTheme.typography.bodySmall,
                    color = AgroOnSurfaceVariant
                )
                TextButton(onClick = onGoRegister) {
                    Text(
                        "Crear cuenta",
                        color = AgroPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}