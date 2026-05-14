package com.example.agrosense.data.local

import android.content.Context
import com.example.agrosense.data.local.entity.LocalReadingEntity
import com.example.agrosense.data.local.entity.LocalSensorEntity
import com.example.agrosense.data.local.entity.LocalUserEntity
import com.example.agrosense.data.storage.SessionManager
import java.security.MessageDigest

class LocalRepository(context: Context) {

    private val db          = AgroSenseDatabase.getInstance(context)
    private val userDao     = db.localUserDao()
    private val sensorDao   = db.localSensorDao()
    private val readingDao  = db.localReadingDao()
    private val session     = SessionManager(context)

    // ── Utilidad: hash SHA-256 del PIN ─────────────────────────────────────

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ── Modo de la app ─────────────────────────────────────────────────────

    suspend fun getAppMode(): String = session.getAppMode()

    suspend fun setAppMode(mode: String) = session.saveAppMode(mode)

    // ── Usuario local ──────────────────────────────────────────────────────

    suspend fun hasUser(): Boolean = userDao.getUser() != null

    suspend fun getUser(): LocalUserEntity? = userDao.getUser()

    suspend fun createUser(name: String, pin: String) {
        userDao.saveUser(LocalUserEntity(name = name.trim(), pinHash = hashPin(pin)))
    }

    /**
     * Valida el PIN. Retorna el usuario si es correcto, null si no.
     */
    suspend fun validatePin(pin: String): LocalUserEntity? {
        val user = userDao.getUser() ?: return null
        return if (user.pinHash == hashPin(pin)) user else null
    }

    suspend fun deleteUser() = userDao.deleteUser()

    // ── Sensores locales ───────────────────────────────────────────────────

    suspend fun getAllSensors(): List<LocalSensorEntity> = sensorDao.getAll()

    suspend fun saveSensor(
        deviceId: String,
        name: String,
        location: String?,
        apiKey: String?
    ) {
        sensorDao.save(LocalSensorEntity(deviceId, name, location, apiKey))
    }

    suspend fun deleteSensor(deviceId: String) = sensorDao.delete(deviceId)

    // ── Lecturas locales ───────────────────────────────────────────────────

    /**
     * Parsea las líneas JSON del histórico BLE y las persiste.
     * Formato ESP32 SPIFFS: {"ts":1234567890,"t":28.5,"a":63.2,"co2":400.0,"ch4":12.34}
     * IGNORE en la inserción evita duplicar registros ya guardados.
     */
    suspend fun saveReadingsFromHistory(deviceId: String, historyLines: List<String>) {
        val entities = historyLines.mapNotNull { line -> parseHistoryLine(deviceId, line) }
        if (entities.isNotEmpty()) {
            readingDao.insertAll(entities)
        }
    }

    suspend fun getReadingsForRange(
        deviceId: String,
        from: Long,
        to: Long
    ): List<LocalReadingEntity> = readingDao.getForRange(deviceId, from, to)

    /**
     * Elimina lecturas con más de 30 días de antigüedad para liberar espacio.
     */
    suspend fun pruneOldReadings() {
        val thirtyDaysAgoSec = (System.currentTimeMillis() / 1000L) - (30L * 24 * 60 * 60)
        readingDao.deleteOlderThan(thirtyDaysAgoSec)
    }

    suspend fun countReadings(deviceId: String): Int = readingDao.countForDevice(deviceId)

    // ── Parser de línea histórica ──────────────────────────────────────────

    private fun parseHistoryLine(deviceId: String, json: String): LocalReadingEntity? {
        return try {
            fun long(key: String): Long? =
                Regex("\"$key\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toLongOrNull()
            fun float(key: String): Float? =
                Regex("\"$key\"\\s*:\\s*([\\d.]+)").find(json)?.groupValues?.get(1)?.toFloatOrNull()

            val ts = long("ts") ?: return null
            LocalReadingEntity(
                deviceId    = deviceId,
                timestamp   = ts,
                temperature = float("t"),
                airHumidity = float("a"),
                co2         = float("co2"),
                methane     = float("ch4")
            )
        } catch (e: Exception) { null }
    }
}
