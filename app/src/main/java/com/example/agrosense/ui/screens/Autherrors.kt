package com.example.agrosense.ui.screens

/**
 * Convierte mensajes de error crudos del servidor/red
 * en mensajes amigables para el usuario.
 */
fun friendlyAuthError(raw: String): String {
    val lower = raw.lowercase()
    return when {
        // Credenciales
        lower.contains("invalid") && lower.contains("credential") ->
            "El correo o la contraseña son incorrectos. Verifica tus datos e intenta de nuevo."
        lower.contains("incorrect") || lower.contains("wrong password") ->
            "La contraseña ingresada es incorrecta."
        lower.contains("user not found") || lower.contains("no user") ->
            "No encontramos una cuenta con ese correo electrónico."
        lower.contains("invalid email") ->
            "El formato del correo electrónico no es válido."

        // Registro
        lower.contains("already") || lower.contains("duplicate") || lower.contains("23505") ->
            "Ya existe una cuenta registrada con ese correo electrónico."
        lower.contains("password") && lower.contains("short") ->
            "La contraseña debe tener al menos 6 caracteres."
        lower.contains("password") && lower.contains("weak") ->
            "La contraseña es muy débil. Usa letras, números y símbolos."

        // Red / servidor
        lower.contains("unable to resolve") || lower.contains("failed to connect")
                || lower.contains("sockettimeout") || lower.contains("timeout") ->
            "No se pudo conectar al servidor. Verifica tu conexión a internet."
        lower.contains("network") ->
            "Error de red. Verifica tu conexión e intenta de nuevo."
        lower.contains("500") ->
            "El servidor tuvo un problema. Intenta más tarde."
        lower.contains("401") || lower.contains("403") ->
            "El correo o la contraseña son incorrectos."
        lower.contains("404") ->
            "Servicio no disponible temporalmente."

        // Sesión
        lower.contains("token") || lower.contains("expired") ->
            "Tu sesión ha expirado. Inicia sesión nuevamente."

        // Fallback
        else -> "Ocurrió un error inesperado. Intenta de nuevo."
    }
}