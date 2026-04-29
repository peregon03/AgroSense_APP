/*
 * AgroSense - Arduino UNO R4 WiFi / Nano ESP32 (ABX00092)
 * =========================================================
 * - SHT31 remoto vía RS485 (Arduino Nano nodo sensor):
 *     temperatura + humedad REALES
 * - CO2:    simulado (reemplazar con sensor MQ-135 u otro)
 * - Metano: simulado (reemplazar con sensor MQ-4 u otro)
 * - LED en D3: controlable desde app (simula motobomba)
 * - RS485 recepción: D0(RX)/D1(TX) hardware serial — D2 = DE/RE
 * - BLE: configuracion WiFi + datos en vivo + control bomba + historico
 * - NVS: credenciales WiFi y API key persisten entre reinicios
 * - WiFi: envio a AWS cada 30s + consulta programacion de bomba cada 60s
 *
 * LIBRERIAS NECESARIAS (Tools > Manage Libraries):
 *   - ArduinoJson
 *
 * NOTA: las librerías BLE, WiFi, Preferences y LittleFS vienen incluidas
 *       con el paquete de soporte del ABX00092 (arduino-esp32 core).
 */

#include "time.h"
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <LittleFS.h>
#define SPIFFS LittleFS
#include <ArduinoJson.h>
#include <Preferences.h>

// ── Pines ────────────────────────────────────────────────────────────────────
#define LED_PIN        3    // Simula bomba
#define RS485_DE_PIN   2    // DE + RE del módulo RS485 (HIGH = TX, LOW = RX)

// En el ABX00092 (ESP32-S3):
//   Serial  → USB-CDC  (Serial Monitor del PC — debug)
//   Serial0 → D0/D1    (RS485 hardware — pines físicos)
#define RS485_SERIAL   Serial0

// ── UUIDs BLE ────────────────────────────────────────────────────────────────
#define SERVICE_UUID        "0000A001-0000-1000-8000-00805F9B34FB"
#define DEVICE_ID_UUID      "0000A002-0000-1000-8000-00805F9B34FB"
#define READINGS_UUID       "0000A003-0000-1000-8000-00805F9B34FB"
#define PUMP_UUID           "0000A004-0000-1000-8000-00805F9B34FB"
#define HISTORY_REQ_UUID    "0000A005-0000-1000-8000-00805F9B34FB"
#define HISTORY_DATA_UUID   "0000A006-0000-1000-8000-00805F9B34FB"
#define WIFI_CONFIG_UUID    "0000A007-0000-1000-8000-00805F9B34FB"
#define WIFI_STATUS_UUID    "0000A008-0000-1000-8000-00805F9B34FB"

// ── Intervalos ───────────────────────────────────────────────────────────────
#define BLE_INTERVAL        2000
#define WIFI_INTERVAL       600000
#define SAVE_INTERVAL       60000
#define PUMP_CHECK_INTERVAL 60000
#define RS485_TIMEOUT_MS    5000    // Si no llegan datos en 5s, loguear advertencia

// ── SPIFFS ───────────────────────────────────────────────────────────────────
#define HISTORY_FILE   "/history.csv"
#define MAX_RECORDS    500

// ── Variables globales ───────────────────────────────────────────────────────
Preferences prefs;

BLEServer*          pServer          = nullptr;
BLECharacteristic*  pDeviceIdChar    = nullptr;
BLECharacteristic*  pReadingsChar    = nullptr;
BLECharacteristic*  pPumpChar        = nullptr;
BLECharacteristic*  pHistoryReqChar  = nullptr;
BLECharacteristic*  pHistoryDataChar = nullptr;
BLECharacteristic*  pWifiConfigChar  = nullptr;
BLECharacteristic*  pWifiStatusChar  = nullptr;

bool          bleConnected       = false;
bool          pumpState          = false;
unsigned long lastBleTime        = 0;
unsigned long lastWifiTime       = 0;
unsigned long lastSaveTime       = 0;
unsigned long lastPumpCheckTime  = 0;
unsigned long pumpCheckInterval  = PUMP_CHECK_INTERVAL;
unsigned long lastRS485Rx        = 0;   // Último momento con dato válido recibido
std::string   deviceId          = "";

// Credenciales NVS
String wifiSsid     = "";
String wifiPassword = "";
String apiKey       = "";
String backendUrl   = "http://3.15.133.197:3000/api/ingest";
String pumpStatusUrl = "http://3.15.133.197:3000/api/ingest/pump-status";

// Lecturas actuales — temperatura y humedad llegan del Nano vía RS485
float currentTemperature = 0.0;
float currentAirHumidity = 0.0;
float currentCO2         = 400.0;   // ppm — reemplazar con sensor real MQ-135
float currentMethane     = 1.5;     // ppm — reemplazar con sensor real MQ-4

// Buffer RS485 para parseo de trama
String rs485Buffer = "";

// ── RS485: modo RX (por defecto) ─────────────────────────────────────────────
void rs485ModeRX() {
  digitalWrite(RS485_DE_PIN, LOW);
}

// ── RS485: parsear trama "$T:23.50,H:61.20\n" ────────────────────────────────
bool parseRS485Frame(const String& frame) {
  // Verificar cabecera
  if (!frame.startsWith("$")) return false;

  int tIdx = frame.indexOf("T:");
  int hIdx = frame.indexOf("H:");
  int cIdx = frame.indexOf(",", tIdx);

  if (tIdx < 0 || hIdx < 0 || cIdx < 0) return false;

  String tStr = frame.substring(tIdx + 2, cIdx);
  String hStr = frame.substring(hIdx + 2);

  float t = tStr.toFloat();
  float h = hStr.toFloat();

  // Validar rangos razonables del SHT31
  if (t < -40.0 || t > 125.0) return false;
  if (h < 0.0   || h > 100.0) return false;

  currentTemperature = t;
  currentAirHumidity = h;
  lastRS485Rx = millis();

  Serial.printf("[RS485 RX] T=%.2f C  H=%.2f %%\n", t, h);
  return true;
}

// ── RS485: leer bytes disponibles del Serial hardware ────────────────────────
void readRS485() {
  while (RS485_SERIAL.available()) {
    char c = (char)RS485_SERIAL.read();

    if (c == '\n') {
      rs485Buffer.trim();
      if (rs485Buffer.length() > 0) {
        if (!parseRS485Frame(rs485Buffer)) {
          Serial.printf("[RS485] Trama inválida: '%s'\n", rs485Buffer.c_str());
        }
      }
      rs485Buffer = "";
    } else {
      // Limitar buffer para evitar desbordamiento
      if (rs485Buffer.length() < 64) {
        rs485Buffer += c;
      } else {
        rs485Buffer = "";   // Trama corrupta — descartar
      }
    }
  }
}

// ── Actualizar CO2 y metano simulados ─────────────────────────────────────────
// TODO: reemplazar con lectura real de sensores MQ-135 / MQ-4
void updateSimulatedSensors() {
  float co2Delta     = (random(0, 100) - 50) * 0.5f;
  currentCO2         = constrain(currentCO2 + co2Delta, 300.0, 2000.0);

  float methaneDelta = (random(0, 100) - 50) * 0.02f;
  currentMethane     = constrain(currentMethane + methaneDelta, 0.5, 20.0);
}

// ── Guardar / cargar config en NVS ──────────────────────────────────────────
void saveConfig() {
  prefs.begin("agrosense", false);
  prefs.putString("ssid",     wifiSsid);
  prefs.putString("password", wifiPassword);
  prefs.putString("apikey",   apiKey);
  prefs.end();
  Serial.println("[CONFIG] Guardado en NVS");
}

bool loadConfig() {
  prefs.begin("agrosense", true);
  wifiSsid     = prefs.getString("ssid",     "");
  wifiPassword = prefs.getString("password", "");
  apiKey       = prefs.getString("apikey",   "");
  prefs.end();

  Serial.printf("[CONFIG] SSID: %s  APIKey: %s\n",
    wifiSsid.c_str(),
    apiKey.length() > 0 ? "***configurada***" : "no configurada");

  return wifiSsid.length() > 0;
}

bool timeReady() {
  struct tm timeinfo;
  if (!getLocalTime(&timeinfo)) return false;
  return true;
}

// ── Conectar WiFi ────────────────────────────────────────────────────────────
bool connectWiFi() {
  if (wifiSsid.length() == 0) {
    Serial.println("[WiFi] Sin credenciales configuradas");
    return false;
  }
  Serial.printf("[WiFi] Conectando a %s\n", wifiSsid.c_str());
  WiFi.begin(wifiSsid.c_str(), wifiPassword.c_str());
  int tries = 0;
  while (WiFi.status() != WL_CONNECTED && tries < 20) {
    delay(500);
    Serial.print(".");
    tries++;
  }
  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("\n[WiFi] Conectado! IP: %s\n", WiFi.localIP().toString().c_str());
    return true;
  }
  Serial.println("\n[WiFi] No se pudo conectar");
  return false;
}

// ── Notificar estado WiFi ────────────────────────────────────────────────────
void notifyWifiStatus(const char* status) {
  if (pWifiStatusChar != nullptr) {
    pWifiStatusChar->setValue(status);
    pWifiStatusChar->notify();
    Serial.printf("[WIFI_STATUS] -> %s\n", status);
  }
}

// ── Callback: configuracion WiFi desde app ───────────────────────────────────
class WifiConfigCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* pChar) override {
    std::string val = pChar->getValue();
    if (val.length() == 0) return;

    Serial.printf("[WIFI_CONFIG] Recibido: %s\n", val.c_str());

    if (val == "RESET") {
      prefs.begin("agrosense", false);
      prefs.clear();
      prefs.end();
      wifiSsid     = "";
      wifiPassword = "";
      apiKey       = "";
      WiFi.disconnect(true);
      notifyWifiStatus("RESET_OK");
      Serial.println("[CONFIG] Factory reset — NVS borrado");
      return;
    }

    String data = String(val.c_str());
    int sep1 = data.indexOf('|');
    int sep2 = data.indexOf('|', sep1 + 1);

    if (sep1 < 0 || sep2 < 0) {
      notifyWifiStatus("ERROR:formato_invalido");
      return;
    }

    wifiSsid     = data.substring(0, sep1);
    wifiPassword = data.substring(sep1 + 1, sep2);
    apiKey       = data.substring(sep2 + 1);

    Serial.printf("[WIFI_CONFIG] SSID: %s\n", wifiSsid.c_str());
    saveConfig();
    notifyWifiStatus("CONNECTING");
    WiFi.disconnect();
    delay(500);

    if (connectWiFi()) {
      configTime(-5 * 3600, 0, "pool.ntp.org");
      int retries = 0;
      while (!timeReady() && retries < 10) { delay(1000); retries++; }
      notifyWifiStatus("CONNECTED");
    } else {
      notifyWifiStatus("ERROR:no_se_pudo_conectar");
    }
  }
};

// ── Callback: control bomba/LED ──────────────────────────────────────────────
class PumpCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* pChar) override {
    std::string val = pChar->getValue();
    if (val.length() == 0) return;
    char cmd = val[0];
    if (cmd == '1') {
      pumpState = true;
      digitalWrite(LED_PIN, HIGH);
      Serial.println("[BOMBA] Encendida (manual BLE)");
    } else if (cmd == '0') {
      pumpState = false;
      digitalWrite(LED_PIN, LOW);
      Serial.println("[BOMBA] Apagada (manual BLE)");
    }
    char status[2] = { cmd, '\0' };
    pPumpChar->setValue((uint8_t*)status, 1);
    pPumpChar->notify();
  }
};

// ── Callback: peticion de historico ─────────────────────────────────────────
class HistoryRequestCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* pChar) override {
    std::string val = pChar->getValue();
    if (val == "GET") {
      Serial.println("[HISTORY] App solicito historico");
      sendHistoryBLE();
    } else if (val == "CLEAR") {
      SPIFFS.remove(HISTORY_FILE);
      pHistoryDataChar->setValue("CLEARED");
      pHistoryDataChar->notify();
      Serial.println("[HISTORY] Historico borrado");
    }
  }
};

// ── Construir JSON lectura actual ────────────────────────────────────────────
std::string buildReadingJson() {
  char buf[256];
  snprintf(buf, sizeof(buf),
    "{\"device_id\":\"%s\",\"temperature\":%.1f,\"air_humidity\":%.1f,\"co2\":%.1f,\"methane\":%.2f}",
    deviceId.c_str(), currentTemperature, currentAirHumidity, currentCO2, currentMethane);
  return std::string(buf);
}

// ── Guardar lectura en SPIFFS ────────────────────────────────────────────────
void saveReadingToSPIFFS() {
  String existing = "";
  int count = 0;

  if (SPIFFS.exists(HISTORY_FILE)) {
    File f = SPIFFS.open(HISTORY_FILE, "r");
    if (f) {
      existing = f.readString();
      f.close();
      for (char c : existing) if (c == '\n') count++;
    }
  }

  if (count >= MAX_RECORDS) {
    int skip = 0, pos = 0;
    while (pos < (int)existing.length() && skip < 50) {
      if (existing[pos] == '\n') skip++;
      pos++;
    }
    existing = existing.substring(pos);
  }

  time_t now;
  time(&now);

  char newLine[96];
  snprintf(newLine, sizeof(newLine), "%ld,%.1f,%.1f,%.1f,%.2f\n",
    now, currentTemperature, currentAirHumidity, currentCO2, currentMethane);

  File f = SPIFFS.open(HISTORY_FILE, "w");
  if (f) {
    f.print(existing);
    f.print(newLine);
    f.close();
    Serial.printf("[SPIFFS] Guardado registro #%d\n", count + 1);
  }
}

// ── Enviar historico por BLE ─────────────────────────────────────────────────
void sendHistoryBLE() {
  if (!SPIFFS.exists(HISTORY_FILE)) {
    pHistoryDataChar->setValue("EMPTY");
    pHistoryDataChar->notify();
    return;
  }

  File f = SPIFFS.open(HISTORY_FILE, "r");
  if (!f) {
    pHistoryDataChar->setValue("ERROR");
    pHistoryDataChar->notify();
    return;
  }

  pHistoryDataChar->setValue("START");
  pHistoryDataChar->notify();
  delay(100);

  int sent = 0;
  while (f.available()) {
    String line = f.readStringUntil('\n');
    line.trim();
    if (line.length() == 0) continue;

    int c1 = line.indexOf(',');
    int c2 = line.indexOf(',', c1 + 1);
    int c3 = line.indexOf(',', c2 + 1);
    int c4 = line.indexOf(',', c3 + 1);

    if (c1 > 0 && c2 > 0 && c3 > 0 && c4 > 0) {
      char json[160];
      snprintf(json, sizeof(json),
        "{\"ts\":%s,\"t\":%s,\"a\":%s,\"co2\":%s,\"ch4\":%s}",
        line.substring(0, c1).c_str(),
        line.substring(c1 + 1, c2).c_str(),
        line.substring(c2 + 1, c3).c_str(),
        line.substring(c3 + 1, c4).c_str(),
        line.substring(c4 + 1).c_str());
      pHistoryDataChar->setValue(json);
      pHistoryDataChar->notify();
      delay(50);
      sent++;
    }
  }
  f.close();

  char endMsg[32];
  snprintf(endMsg, sizeof(endMsg), "END:%d", sent);
  pHistoryDataChar->setValue(endMsg);
  pHistoryDataChar->notify();
  Serial.printf("[HISTORY] Enviados %d registros\n", sent);
}

// ── Enviar lecturas al servidor ──────────────────────────────────────────────
void sendToAWS() {
  if (apiKey.length() == 0) {
    Serial.println("[WiFi] Sin API key - configura el sensor desde la app");
    return;
  }
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[WiFi] Sin conexion, reconectando...");
    if (!connectWiFi()) return;
  }

  HTTPClient http;
  http.begin(backendUrl);
  http.addHeader("Content-Type", "application/json");

  char body[320];
  snprintf(body, sizeof(body),
    "{\"device_id\":\"%s\",\"api_key\":\"%s\",\"temperature\":%.1f,\"air_humidity\":%.1f,\"co2\":%.1f,\"methane\":%.2f}",
    deviceId.c_str(), apiKey.c_str(),
    currentTemperature, currentAirHumidity, currentCO2, currentMethane);

  int httpCode = http.POST(body);
  if (httpCode == 200 || httpCode == 201) {
    Serial.println("[WiFi] Datos enviados OK");
  } else {
    String response = http.getString();
    Serial.printf("[WiFi] Error HTTP: %d  Respuesta: %s\n", httpCode, response.c_str());
  }
  http.end();
}

// ── Consultar programacion de bomba ──────────────────────────────────────────
void checkPumpSchedule() {
  if (apiKey.length() == 0) return;
  if (WiFi.status() != WL_CONNECTED) {
    if (!connectWiFi()) return;
  }

  String url = pumpStatusUrl
             + "?device_id=" + String(deviceId.c_str())
             + "&api_key="   + apiKey;

  Serial.printf("[PUMP_CHECK] Consultando: %s\n", url.c_str());

  HTTPClient http;
  http.begin(url);
  int httpCode = http.GET();
  String payload = http.getString();
  http.end();

  Serial.printf("[PUMP_CHECK] HTTP %d  Body: %s\n", httpCode, payload.c_str());

  if (httpCode == 200) {
    StaticJsonDocument<128> doc;
    DeserializationError err = deserializeJson(doc, payload);
    if (err) {
      Serial.printf("[PUMP_CHECK] Error JSON: %s\n", err.c_str());
      return;
    }
    bool scheduledOn = doc["pump_on"] | false;
    const char* mode = doc["mode"] | "auto";
    bool isManual    = strcmp(mode, "manual") == 0;

    pumpCheckInterval = isManual ? 5000 : PUMP_CHECK_INTERVAL;

    Serial.printf("[PUMP_CHECK] pump_on=%s  modo=%s  intervalo=%lus\n",
      scheduledOn ? "true" : "false", mode, pumpCheckInterval / 1000);

    if (scheduledOn != pumpState) {
      pumpState = scheduledOn;
      digitalWrite(LED_PIN, pumpState ? HIGH : LOW);
      Serial.printf("[BOMBA] Estado aplicado: %s\n", pumpState ? "ENCENDIDA" : "APAGADA");
    }
  }
}

// ── Callback BLE: conexion / desconexion ─────────────────────────────────────
class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) override {
    bleConnected = true;
    Serial.println("[BLE] Cliente conectado");
  }
  void onDisconnect(BLEServer* pServer) override {
    bleConnected = false;
    Serial.println("[BLE] Cliente desconectado - reiniciando advertising");
    delay(500);
    BLEDevice::startAdvertising();
  }
};

// ── Setup ─────────────────────────────────────────────────────────────────────
void setup() {
  // Serial  → USB-CDC del ESP32-S3 (Serial Monitor del PC — debug)
  // Serial0 → pines D0/D1 físicos  (bus RS485)
  Serial.begin(115200);         // Debug por USB — velocidad libre
  delay(100);
  Serial.println("[R4] Iniciando AgroSense maestro...");

  // RS485 en Serial0 (D0=RX, D1=TX) al mismo baud que el Nano
  RS485_SERIAL.begin(9600);
  pinMode(RS485_DE_PIN, OUTPUT);
  rs485ModeRX();
  Serial.println("[RS485] Serial0 iniciado a 9600 baud (D0=RX, D1=TX, D2=DE)");

  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  if (!SPIFFS.begin(true)) {
    Serial.println("[SPIFFS] Error al montar");
  } else {
    Serial.println("[SPIFFS] Montado OK");
  }

  bool hasConfig = loadConfig();

  // Esperar primeros datos del Nano antes de arrancar BLE
  // (hasta 3 s; si no llegan, arranca con valores 0 de todas formas)
  unsigned long waitStart = millis();
  while (millis() - waitStart < 3000) {
    readRS485();
    delay(10);
  }

  if (hasConfig && connectWiFi()) {
    configTime(-5 * 3600, 0, "pool.ntp.org");
    int retries = 0;
    while (!timeReady() && retries < 10) { delay(1000); retries++; }
  }

  BLEDevice::init("AgroSense");
  deviceId = BLEDevice::getAddress().toString();

  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new ServerCallbacks());

  BLEService* pService = pServer->createService(BLEUUID(SERVICE_UUID), 40);

  pDeviceIdChar = pService->createCharacteristic(DEVICE_ID_UUID, BLECharacteristic::PROPERTY_READ);
  pDeviceIdChar->setValue(deviceId);

  pReadingsChar = pService->createCharacteristic(READINGS_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  pReadingsChar->addDescriptor(new BLE2902());

  pPumpChar = pService->createCharacteristic(
    PUMP_UUID,
    BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
  pPumpChar->addDescriptor(new BLE2902());
  pPumpChar->setCallbacks(new PumpCallbacks());
  pPumpChar->setValue("0");

  pHistoryReqChar = pService->createCharacteristic(HISTORY_REQ_UUID, BLECharacteristic::PROPERTY_WRITE);
  pHistoryReqChar->setCallbacks(new HistoryRequestCallbacks());

  pHistoryDataChar = pService->createCharacteristic(HISTORY_DATA_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  pHistoryDataChar->addDescriptor(new BLE2902());

  pWifiConfigChar = pService->createCharacteristic(WIFI_CONFIG_UUID, BLECharacteristic::PROPERTY_WRITE);
  pWifiConfigChar->setCallbacks(new WifiConfigCallbacks());

  pWifiStatusChar = pService->createCharacteristic(
    WIFI_STATUS_UUID,
    BLECharacteristic::PROPERTY_NOTIFY | BLECharacteristic::PROPERTY_READ);
  pWifiStatusChar->addDescriptor(new BLE2902());

  String initStatus = WiFi.status() == WL_CONNECTED ? "CONNECTED" :
                      (hasConfig ? "DISCONNECTED" : "NOT_CONFIGURED");
  pWifiStatusChar->setValue(initStatus.c_str());

  pService->start();

  BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();
}

// ── Loop ─────────────────────────────────────────────────────────────────────
void loop() {
  unsigned long now = millis();

  // Leer RS485 en cada iteración del loop (sin bloquear)
  readRS485();

  // Advertir si el Nano lleva más de 5 s sin enviar datos
  if (lastRS485Rx > 0 && (now - lastRS485Rx) > RS485_TIMEOUT_MS) {
    // Silencioso en producción; si quieres debug descomenta:
    // Serial.println("[RS485] ADVERTENCIA: sin datos del Nano en >5s");
    lastRS485Rx = now;   // Reinicia para no repetir cada ciclo
  }

  // Actualizar CO2 y metano simulados
  updateSimulatedSensors();

  // Notificar por BLE cada 2 s
  if (now - lastBleTime >= BLE_INTERVAL) {
    lastBleTime = now;

    if (bleConnected) {
      std::string payload = buildReadingJson();
      const int chunkSize = 20;
      int len = payload.length();
      for (int i = 0; i < len; i += chunkSize) {
        std::string chunk = payload.substr(i, chunkSize);
        pReadingsChar->setValue(chunk);
        pReadingsChar->notify();
        delay(30);
      }
    }
  }

  // Guardar en SPIFFS cada 60 s
  if (now - lastSaveTime >= SAVE_INTERVAL) {
    lastSaveTime = now;
    saveReadingToSPIFFS();
  }

  // Enviar al servidor cada 30 s
  if (now - lastWifiTime >= WIFI_INTERVAL) {
    lastWifiTime = now;
    sendToAWS();
  }

  // Consultar programacion bomba
  if (now - lastPumpCheckTime >= pumpCheckInterval) {
    lastPumpCheckTime = now;
    checkPumpSchedule();
  }

  delay(20);
}
