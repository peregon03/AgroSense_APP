/*
 * AgroSense - Arduino Nano ESP32 (ABX00092)
 * ==========================================
 * - DHT11 en D2: temperatura + humedad aire reales
 * - CO2:    simulado (reemplazar con sensor MQ-135 u otro)
 * - Metano: simulado (reemplazar con sensor MQ-4 u otro)
 * - LED en D3: controlable desde app (simula motobomba)
 * - BLE: configuracion WiFi + datos en vivo + control bomba + historico
 * - NVS: credenciales WiFi y API key persisten entre reinicios
 * - WiFi: envio a AWS cada 30s + consulta programacion de bomba cada 60s
 *
 * LIBRERIAS NECESARIAS (Tools > Manage Libraries):
 *   - DHT sensor library  (Adafruit)
 *   - Adafruit Unified Sensor
 *   - ArduinoJson
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
#include <DHT.h>
#include <ArduinoJson.h>
#include <Preferences.h>

// Pines
#define DHT_PIN             2
#define DHT_TYPE            DHT11
#define LED_PIN             3

// UUIDs BLE
#define SERVICE_UUID        "0000A001-0000-1000-8000-00805F9B34FB"
#define DEVICE_ID_UUID      "0000A002-0000-1000-8000-00805F9B34FB"
#define READINGS_UUID       "0000A003-0000-1000-8000-00805F9B34FB"
#define PUMP_UUID           "0000A004-0000-1000-8000-00805F9B34FB"
#define HISTORY_REQ_UUID    "0000A005-0000-1000-8000-00805F9B34FB"
#define HISTORY_DATA_UUID   "0000A006-0000-1000-8000-00805F9B34FB"
#define WIFI_CONFIG_UUID    "0000A007-0000-1000-8000-00805F9B34FB"
#define WIFI_STATUS_UUID    "0000A008-0000-1000-8000-00805F9B34FB"

// Intervalos
#define BLE_INTERVAL        2000
#define WIFI_INTERVAL       30000
#define SAVE_INTERVAL       60000
#define PUMP_CHECK_INTERVAL 60000   // Consultar programacion cada 60 s

// SPIFFS
#define HISTORY_FILE        "/history.csv"
#define MAX_RECORDS         500

// Variables globales
DHT         dht(DHT_PIN, DHT_TYPE);
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
unsigned long pumpCheckInterval  = PUMP_CHECK_INTERVAL; // 5 s en manual, 60 s en auto
std::string   deviceId          = "";

// Credenciales cargadas desde NVS
String wifiSsid     = "";
String wifiPassword = "";
String apiKey       = "";
String backendUrl   = "http://3.15.133.197:3000/api/ingest";
String pumpStatusUrl = "http://3.15.133.197:3000/api/ingest/pump-status";

// Lecturas actuales
float currentTemperature = 0;
float currentAirHumidity = 0;
float currentCO2         = 400.0;   // ppm — reemplazar con lectura real del sensor
float currentMethane     = 1.5;     // ppm — reemplazar con lectura real del sensor

// ── Guardar / cargar config en NVS ─────────────────────────────────────────
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

// ── Conectar WiFi ──────────────────────────────────────────────────────────
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

// ── Notificar estado WiFi a la app ─────────────────────────────────────────
void notifyWifiStatus(const char* status) {
  if (pWifiStatusChar != nullptr) {
    pWifiStatusChar->setValue(status);
    pWifiStatusChar->notify();
    Serial.printf("[WIFI_STATUS] -> %s\n", status);
  }
}

// ── Callback: configuracion WiFi desde app ─────────────────────────────────
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

// ── Callback: control bomba/LED (manual desde BLE) ─────────────────────────
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

// ── Callback: peticion de historico ────────────────────────────────────────
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

// ── Leer sensores ──────────────────────────────────────────────────────────
void readSensors() {
  float t = dht.readTemperature();
  float h = dht.readHumidity();

  if (!isnan(t)) currentTemperature = t;
  else Serial.println("[DHT11] Error leyendo temperatura");

  if (!isnan(h)) currentAirHumidity = h;
  else Serial.println("[DHT11] Error leyendo humedad");

  // TODO: reemplazar con lectura real del sensor de CO2 (ej. MQ-135)
  float co2Delta = (random(0, 100) - 50) * 0.5f;
  currentCO2 = constrain(currentCO2 + co2Delta, 300.0, 2000.0);

  // TODO: reemplazar con lectura real del sensor de metano (ej. MQ-4)
  float methaneDelta = (random(0, 100) - 50) * 0.02f;
  currentMethane = constrain(currentMethane + methaneDelta, 0.5, 20.0);
}

// ── Construir JSON lectura actual ──────────────────────────────────────────
std::string buildReadingJson() {
  char buf[256];
  snprintf(buf, sizeof(buf),
    "{\"device_id\":\"%s\",\"temperature\":%.1f,\"air_humidity\":%.1f,\"co2\":%.1f,\"methane\":%.2f}",
    deviceId.c_str(), currentTemperature, currentAirHumidity, currentCO2, currentMethane);
  return std::string(buf);
}

// ── Guardar lectura en SPIFFS ──────────────────────────────────────────────
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

// ── Enviar historico por BLE ───────────────────────────────────────────────
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

// ── Enviar lecturas al servidor ────────────────────────────────────────────
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

// ── Consultar programacion de bomba al servidor ────────────────────────────
void checkPumpSchedule() {
  if (apiKey.length() == 0) {
    Serial.println("[PUMP_CHECK] Sin API key — omitiendo consulta");
    return;
  }
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
    bool isManual = strcmp(mode, "manual") == 0;

    // Intervalo adaptativo: 5 s en modo manual para respuesta rapida, 60 s en auto
    pumpCheckInterval = isManual ? 5000 : PUMP_CHECK_INTERVAL;

    Serial.printf("[PUMP_CHECK] pump_on=%s  modo=%s  intervalo=%lus\n",
      scheduledOn ? "true" : "false", mode, pumpCheckInterval / 1000);

    if (scheduledOn != pumpState) {
      pumpState = scheduledOn;
      digitalWrite(LED_PIN, pumpState ? HIGH : LOW);
      Serial.printf("[BOMBA] Estado aplicado: %s\n", pumpState ? "ENCENDIDA" : "APAGADA");
    } else {
      Serial.printf("[PUMP_CHECK] Sin cambio — bomba ya esta %s\n", pumpState ? "ON" : "OFF");
    }
  } else {
    Serial.printf("[PUMP_CHECK] Error HTTP: %d  Respuesta: %s\n", httpCode, payload.c_str());
  }
}

// ── Callback BLE: conexion / desconexion ───────────────────────────────────
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

// ── Setup ──────────────────────────────────────────────────────────────────
void setup() {
  Serial.begin(115200);
  delay(300);

  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  dht.begin();
  Serial.println("[DHT11] Iniciado en pin D2");

  if (!SPIFFS.begin(true)) {
    Serial.println("[SPIFFS] Error al montar");
  } else {
    Serial.println("[SPIFFS] Montado OK");
  }

  bool hasConfig = loadConfig();

  delay(3000);   // DHT11 necesita al menos 2s para estabilizarse
  readSensors();
  delay(2000);   // Segunda espera para primera lectura confiable
  readSensors();

  if (hasConfig && connectWiFi()) {
    configTime(-5 * 3600, 0, "pool.ntp.org");
    Serial.println("[TIME] Sincronizando NTP...");
    int retries = 0;
    while (!timeReady() && retries < 10) {
      delay(1000);
      retries++;
    }
    if (timeReady()) Serial.println("[TIME] Hora sincronizada OK");
  }

  BLEDevice::init("AgroSense");
  deviceId = BLEDevice::getAddress().toString();
  Serial.printf("[BLE] Device ID (MAC): %s\n", deviceId.c_str());

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

  Serial.println("[BLE] Activo - esperando conexion...");
  Serial.println("=== AgroSense listo ===");
}

// ── Loop ───────────────────────────────────────────────────────────────────
void loop() {
  unsigned long now = millis();

  // Leer sensores y notificar por BLE cada 2 s
  if (now - lastBleTime >= BLE_INTERVAL) {
    lastBleTime = now;
    readSensors();

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
      Serial.printf("[BLE] -> %s\n", payload.c_str());
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

  // Consultar programacion/override de bomba (5 s en manual, 60 s en auto)
  if (now - lastPumpCheckTime >= pumpCheckInterval) {
    lastPumpCheckTime = now;
    checkPumpSchedule();
  }

  delay(20);
}
