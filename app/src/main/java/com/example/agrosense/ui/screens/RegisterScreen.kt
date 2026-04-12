package com.example.agrosense.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private val GreenDark   = Color(0xFF1B5E20)
private val GreenMid    = Color(0xFF2E7D32)
private val GreenLight  = Color(0xFF4CAF50)
private val GreenPale   = Color(0xFFF1F8E9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    vm: AuthViewModel,
    onBackToLogin: () -> Unit,
    onVerifyEmail: (email: String) -> Unit = {}
) {
    var nombre      by remember { mutableStateOf("") }
    var apellido    by remember { mutableStateOf("") }
    var email       by remember { mutableStateOf("") }
    var pass        by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }
    val state       by vm.state.collectAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(nombre, apellido, email, pass) {
        if (state.error != null) vm.clearError()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(GreenDark, GreenMid, GreenPale)
                )
            )
    ) {
        // Botón volver flotante
        IconButton(
            onClick = onBackToLogin,
            modifier = Modifier
                .padding(12.dp)
                .statusBarsPadding()
                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(50))
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Volver",
                tint = Color.White
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Encabezado ────────────────────────────────────────
            Spacer(Modifier.height(80.dp))

            Text(
                text = "🌱",
                fontSize = 56.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "AgroSense",
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Crea tu cuenta y empieza a monitorear",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp)
            )

            Spacer(Modifier.height(32.dp))

            // ── Tarjeta formulario ────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp,
                    bottomStart = 28.dp, bottomEnd = 28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Crear cuenta",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = GreenDark
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Completa los datos para registrarte",
                        fontSize = 13.sp,
                        color = Color(0xFF9E9E9E),
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(24.dp))

                    // Nombre
                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it },
                        label = { Text("Nombre") },
                        leadingIcon = {
                            Icon(Icons.Filled.Person, contentDescription = null,
                                tint = GreenMid)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = GreenLight,
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            focusedLabelColor    = GreenMid,
                            cursorColor          = GreenMid
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        isError = state.error != null
                    )

                    Spacer(Modifier.height(14.dp))

                    // Apellido
                    OutlinedTextField(
                        value = apellido,
                        onValueChange = { apellido = it },
                        label = { Text("Apellido") },
                        leadingIcon = {
                            Icon(Icons.Filled.Person, contentDescription = null,
                                tint = GreenMid)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = GreenLight,
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            focusedLabelColor    = GreenMid,
                            cursorColor          = GreenMid
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        isError = state.error != null
                    )

                    Spacer(Modifier.height(14.dp))

                    // Email
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Correo electrónico") },
                        leadingIcon = {
                            Icon(Icons.Filled.Email, contentDescription = null,
                                tint = GreenMid)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = GreenLight,
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            focusedLabelColor    = GreenMid,
                            cursorColor          = GreenMid
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction    = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        isError = state.error != null
                    )

                    Spacer(Modifier.height(14.dp))

                    // Contraseña
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Contraseña") },
                        leadingIcon = {
                            Icon(Icons.Filled.Lock, contentDescription = null,
                                tint = GreenMid)
                        },
                        trailingIcon = {
                            IconButton(onClick = { passVisible = !passVisible }) {
                                Icon(
                                    if (passVisible) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Visibility,
                                    contentDescription = if (passVisible) "Ocultar" else "Mostrar",
                                    tint = GreenMid
                                )
                            }
                        },
                        visualTransformation = if (passVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = GreenLight,
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            focusedLabelColor    = GreenMid,
                            cursorColor          = GreenMid
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction    = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                vm.register(nombre, apellido, email, pass,
                                    onNeedsVerification = onVerifyEmail)
                            }
                        ),
                        isError = state.error != null
                    )

                    // Error
                    state.error?.let { rawError ->
                        Spacer(Modifier.height(14.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = friendlyAuthError(rawError),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(28.dp))

                    // ── Botón Crear cuenta ────────────────────────
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            vm.register(nombre, apellido, email, pass,
                                onNeedsVerification = onVerifyEmail)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        enabled = !state.isLoading &&
                                nombre.isNotBlank() && apellido.isNotBlank() &&
                                email.isNotBlank() && pass.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GreenMid,
                            disabledContainerColor = Color(0xFFBDBDBD)
                        )
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = if (state.isLoading) "Creando cuenta..." else "Crear cuenta",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    // ── Link login ────────────────────────────────
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "¿Ya tienes cuenta?",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9E9E9E)
                        )
                        TextButton(onClick = onBackToLogin) {
                            Text(
                                "Iniciar sesión",
                                fontWeight = FontWeight.Bold,
                                color = GreenMid
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}