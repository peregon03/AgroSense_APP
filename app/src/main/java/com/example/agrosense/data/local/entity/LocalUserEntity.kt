package com.example.agrosense.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_users")
data class LocalUserEntity(
    @PrimaryKey val id: Int = 1,        // Solo un usuario local permitido
    val name: String,
    val pinHash: String                 // SHA-256 del PIN de 4 dígitos
)
