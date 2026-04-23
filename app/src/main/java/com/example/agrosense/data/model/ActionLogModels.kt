package com.example.agrosense.data.model

data class ActionLog(
    val id: Int,
    val sensor_id: Int?,
    val action_type: String,
    val created_at: String,
    val first_name: String?,
    val last_name: String?,
    val email: String?
) {
    val userName: String get() = when {
        first_name != null && last_name != null -> "$first_name $last_name"
        email != null -> email
        else -> "Usuario eliminado"
    }

    val actionLabel: String get() = when (action_type) {
        "pump_on"            -> "Riego encendido manualmente"
        "pump_off"           -> "Riego apagado manualmente"
        "pump_auto"          -> "Modo automático activado"
        "schedule_created"   -> "Programación de riego creada"
        "schedule_updated"   -> "Programación de riego editada"
        "schedule_deleted"   -> "Programación de riego eliminada"
        "schedule_enabled"   -> "Programación activada"
        "schedule_disabled"  -> "Programación desactivada"
        "sensor_shared"      -> "Sensor compartido con usuario"
        "share_updated"      -> "Permisos de acceso actualizados"
        "share_revoked"      -> "Acceso revocado"
        "thresholds_updated" -> "Umbrales de alerta actualizados"
        else                 -> action_type
    }

    // Grupo para colorear el ícono
    val actionGroup: ActionGroup get() = when (action_type) {
        "pump_on", "pump_off", "pump_auto"                  -> ActionGroup.PUMP
        "schedule_created", "schedule_updated",
        "schedule_deleted", "schedule_enabled",
        "schedule_disabled"                                 -> ActionGroup.SCHEDULE
        "sensor_shared", "share_updated", "share_revoked"   -> ActionGroup.SHARE
        "thresholds_updated"                                -> ActionGroup.THRESHOLD
        else                                                -> ActionGroup.OTHER
    }
}

enum class ActionGroup { PUMP, SCHEDULE, SHARE, THRESHOLD, OTHER }

data class ActionLogsResponse(
    val logs: List<ActionLog>,
    val count: Int
)
