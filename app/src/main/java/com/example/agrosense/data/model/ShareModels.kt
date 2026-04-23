package com.example.agrosense.data.model

// ── Acceso compartido (para gestionar shares de un sensor propio) ─────────────

data class SensorShare(
    val id: Int,
    val sensor_id: Int,
    val shared_with_id: Int,
    val first_name: String,
    val last_name: String,
    val email: String,
    val can_view_graphs: Boolean,
    val can_schedule: Boolean,
    val can_control_pump: Boolean,
    val created_at: String
)

// ── Sensor compartido conmigo (vista del usuario invitado) ───────────────────

data class SharedSensorEntry(
    val share_id: Int,
    val sensor_id: Int,
    val device_id: String,
    val name: String,
    val location: String?,
    val pump_manual_override: Boolean?,
    val owner_first_name: String,
    val owner_last_name: String,
    val owner_email: String,
    val can_view_graphs: Boolean,
    val can_schedule: Boolean,
    val can_control_pump: Boolean
) {
    val ownerName get() = "$owner_first_name $owner_last_name"

    fun toSensor() = Sensor(
        id                   = sensor_id,
        device_id            = device_id,
        name                 = name,
        location             = location,
        api_key              = null,
        pump_manual_override = pump_manual_override
    )
}

// ── Requests ─────────────────────────────────────────────────────────────────

data class ShareSensorRequest(
    val email: String,
    val can_view_graphs: Boolean,
    val can_schedule: Boolean,
    val can_control_pump: Boolean
)

data class SharePermissionsRequest(
    val can_view_graphs: Boolean,
    val can_schedule: Boolean,
    val can_control_pump: Boolean
)

// ── Responses ─────────────────────────────────────────────────────────────────

data class SensorShareResponse(val share: SensorShare)
data class SensorSharesResponse(val shares: List<SensorShare>)
data class SharedSensorsResponse(val shared_sensors: List<SharedSensorEntry>)
