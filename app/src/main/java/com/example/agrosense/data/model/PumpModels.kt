package com.example.agrosense.data.model

data class PumpSchedule(
    val pump_schedule_enabled: Boolean,
    val pump_start_time: String?,       // "HH:MM" — null when never configured
    val pump_duration_minutes: Int
)

data class PumpScheduleResponse(
    val schedule: PumpSchedule
)

data class PumpOverrideRequest(val override: Boolean?)
data class PumpOverrideResponse(val pump_manual_override: Boolean?)
