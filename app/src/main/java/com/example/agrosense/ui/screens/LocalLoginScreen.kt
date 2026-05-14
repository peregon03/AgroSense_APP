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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.agrosense.ui.viewmodel.LocalViewModel

// ── Colores del tema (locales para evitar dependencia circular) ───────────────
private val LocalPrimary         = Color(0xFF0D631B)
private val LocalPrimaryDark     = Color(0xFF2E7D32)
private val LocalSurface         = Color(0xFFFCF9F8)
private val LocalSurfaceContLow  = Color(0xFFF6F3F2)
private val LocalSurfaceCont     = Color(0xFFF0EDED)
private val LocalOnSurface       = Color(0xFF1B1C1C)
private val LocalOnSurfaceVar    = Color(0xFF40493D)
private val LocalErrorContainer  = Color(0xFFFFDAD6)
private val LocalOnErrorContainer= Color(0xFF93000A)
private val LocalError           = Color(0xFFBA1A1A)

private val LocalGradient = Brush.linearGradient(colors = listOf(LocalPrimary, LocalPrimaryDark))

@Composable
fun LocalLoginScreen(
    vm: LocalViewModel,
    onLoginSuccess: () -> Unit,
    onUseCloud: () -> Unit
) {
    val state by vm.state.collectAsState()
    val focusManager = LocalFocusManager.current

    var pin         by remember { mutableStateOf("") }
    var pinVisible  by remember { mutableStateOf(false) }

    // Para creación de nuevo perfil
    var name        by remember { mutableStateOf("") }
    var confirmPin  by remember { mutableStateOf("") }

    LaunchedEffect(pin, name) { if (state.error != null) vm.clearError() }

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onLoginSuccess()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Ícono modo local ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(LocalSurfaceCont),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.BluetoothConnected,
                    contentDescription = "Modo local",
                    tint = LocalPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Modo Local",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 30.sp,
                color = LocalOnSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (state.hasUser)
                    "Ingresa tu PIN para continuar"
                else
                    "Configura un perfil para usar AgroSense sin conexión a internet",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalOnSurfaceVar,
                textAlign = TextAlign.Center
            )

            // ── Chip "Sin conexión a internet" ────────────────────────────
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(LocalSurfaceCont)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.WifiOff,
                    contentDescription = null,
                    tint = LocalOnSurfaceVar,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Sin conexión a internet · Solo BLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalOnSurfaceVar
                )
            }

            Spacer(Modifier.height(32.dp))

            if (!state.hasUser) {
                // ── CREAR PERFIL LOCAL ────────────────────────────────────
                SectionLabel("Nombre")
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("¿Cómo te llamas?", color = LocalOnSurfaceVar.copy(alpha = 0.6f)) },
                    leadingIcon = {
                        Icon(Icons.Filled.Person, null,
                            tint = if (name.isNotEmpty()) LocalPrimary else LocalOnSurfaceVar)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    isError = state.error != null,
                    shape = RoundedCornerShape(14.dp),
                    colors = localTextFieldColors()
                )

                Spacer(Modifier.height(16.dp))
                SectionLabel("PIN (4 dígitos)")
                PinField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin = it },
                    visible = pinVisible,
                    onVisibilityToggle = { pinVisible = !pinVisible },
                    placeholder = "Elige un PIN de 4 dígitos",
                    imeAction = ImeAction.Next,
                    isError = state.error != null
                )

                Spacer(Modifier.height(16.dp))
                SectionLabel("Confirmar PIN")
                PinField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) confirmPin = it },
                    visible = pinVisible,
                    onVisibilityToggle = { pinVisible = !pinVisible },
                    placeholder = "Repite el PIN",
                    imeAction = ImeAction.Done,
                    isError = state.error != null,
                    onDone = {
                        focusManager.clearFocus()
                        if (pin != confirmPin) vm.clearError().also {
                            // Forzamos el error de confirmación
                        } else vm.createUser(name, pin)
                    }
                )

                // Validación de PINs que no coinciden
                if (pin.length == 4 && confirmPin.length == 4 && pin != confirmPin) {
                    Spacer(Modifier.height(8.dp))
                    Text("Los PINs no coinciden", color = LocalError,
                        style = MaterialTheme.typography.bodySmall)
                }

                ErrorCard(state.error)

                Spacer(Modifier.height(16.dp))
                GradientButton(
                    text = if (state.isLoading) "Creando perfil..." else "Crear perfil local",
                    isLoading = state.isLoading,
                    enabled = !state.isLoading && name.isNotBlank() && pin.length == 4 && pin == confirmPin
                ) {
                    focusManager.clearFocus()
                    vm.createUser(name, pin)
                }

            } else {
                // ── INICIAR SESIÓN ────────────────────────────────────────
                Spacer(Modifier.height(8.dp))
                SectionLabel("PIN")
                PinField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin = it },
                    visible = pinVisible,
                    onVisibilityToggle = { pinVisible = !pinVisible },
                    placeholder = "Ingresa tu PIN de 4 dígitos",
                    imeAction = ImeAction.Done,
                    isError = state.error != null,
                    onDone = {
                        focusManager.clearFocus()
                        vm.loginWithPin(pin)
                    }
                )

                ErrorCard(state.error)

                Spacer(Modifier.height(16.dp))
                GradientButton(
                    text = if (state.isLoading) "Verificando..." else "Ingresar",
                    isLoading = state.isLoading,
                    enabled = !state.isLoading && pin.length == 4
                ) {
                    focusManager.clearFocus()
                    vm.loginWithPin(pin)
                }
            }

            Spacer(Modifier.height(32.dp))
            HorizontalDivider(color = LocalSurfaceCont)
            Spacer(Modifier.height(16.dp))

            // ── Cambiar a modo nube ───────────────────────────────────────
            TextButton(onClick = onUseCloud) {
                Text(
                    "Usar modo nube (requiere internet)",
                    color = LocalPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

// ── Componentes auxiliares ────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = LocalOnSurfaceVar,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onVisibilityToggle: () -> Unit,
    placeholder: String,
    imeAction: ImeAction,
    isError: Boolean,
    onDone: (() -> Unit)? = null
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = LocalOnSurfaceVar.copy(alpha = 0.6f)) },
        leadingIcon = {
            Icon(Icons.Filled.Lock, null,
                tint = if (value.isNotEmpty()) LocalPrimary else LocalOnSurfaceVar)
        },
        trailingIcon = {
            IconButton(onClick = onVisibilityToggle) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Ocultar" else "Mostrar",
                    tint = LocalOnSurfaceVar
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onDone  = { onDone?.invoke() },
            onNext  = {}
        ),
        isError = isError,
        shape = RoundedCornerShape(14.dp),
        colors = localTextFieldColors()
    )
}

@Composable
private fun ErrorCard(error: String?) {
    error?.let {
        Spacer(Modifier.height(14.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = LocalErrorContainer)
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, null, tint = LocalError, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(it, color = LocalOnErrorContainer, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun GradientButton(
    text: String,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(brush = LocalGradient, alpha = if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            enabled = enabled,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
        }
    }
}

@Composable
private fun localTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor     = LocalSurfaceContLow,
    unfocusedContainerColor   = LocalSurfaceContLow,
    errorContainerColor       = LocalErrorContainer,
    focusedIndicatorColor     = Color.Transparent,
    unfocusedIndicatorColor   = Color.Transparent,
    errorIndicatorColor       = Color.Transparent,
    focusedTextColor          = LocalOnSurface,
    unfocusedTextColor        = LocalOnSurface,
    cursorColor               = LocalPrimary,
    focusedLeadingIconColor   = LocalPrimary,
    unfocusedLeadingIconColor = LocalOnSurfaceVar
)
