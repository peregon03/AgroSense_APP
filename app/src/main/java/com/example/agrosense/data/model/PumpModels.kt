package com.example.agrosense.data.model

// ── Modelo de programación de riego ───────────────────────────────────────────

data class IrrigationSchedule(
    val id: Int,
    val sensor_id: Int,
    val label: String?,            // etiqueta opcional, ej. "Riego mañana"
    val start_time: String,        // "HH:MM"
    val duration_seconds: Int,     // 10–3600 segundos
    val enabled: Boolean
)

// ── Requests / Responses ──────────────────────────────────────────────────────

data class IrrigationScheduleRequest(
    val label: String?,
    val start_time: String,
    val duration_seconds: Int,
    val enabled: Boolean
)

data class IrrigationScheduleResponse(
    val schedule: IrrigationSchedule
)

data class IrrigationSchedulesResponse(
    val schedules: List<IrrigationSchedule>
)

data class ToggleScheduleRequest(val enabled: Boolean)

// ── Control manual de bomba ───────────────────────────────────────────────────

data class PumpOverrideRequest(val override: Boolean?)
data class PumpOverrideResponse(val pump_manual_override: Boolean?)

// ── Estado ACK de instrucción de bomba ────────────────────────────────────────

data class PumpAckStatusResponse(
    val pending: Boolean,
    val pump_manual_override: Boolean?
)
