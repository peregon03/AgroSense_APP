/*
 * AgroSense - ESP32 WROVER-B DevKit  |  MAESTRO LoRa  — v6 SIM (Pruebas UX)
 * =========================================================================
 * VERSION DE PRUEBAS UX — NO USAR EN PRODUCCIÓN
 * =========================================================================
 * Nodo 03 (suelo): DATOS REALES via LoRa
 * Nodo 01 (T+H aire): SIMULADO con variación realista
 * Nodo 02 (metano):   SIMULADO con variación realista
 *
 * CO2: simulado en 400 ppm baseline (igual que producción)
 *
 * Envío a AWS: cada 60 segundos (modo pruebas)
 * BLE notify:  cada 2 segundos (igual que producción)
 * SPIFFS:      cada 60 segundos (igual que producción)
 *
 * Simulación:
 *   T aire    → 22°C base, ±4°C ciclo lento (~5 min), ±0.3°C ruido
 *   H aire    → 65% base, ±8% inversamente correlacionada con T, ±0.5% ruido
 *   CH4       → 150 ppm base, ±60 ppm ciclo lento (~10 min), ±8 ppm ruido
 *
 * LoRa Ra-02 (SX1278 433 MHz) — VSPI ESP32 WROVER-B:
 *   GPIO23 → MOSI        GPIO5  → NSS (CS)
 *   GPIO19 → MISO        GPIO14 → RST
 *   GPIO18 → SCK         GPIO26 → DIO0 (IRQ)
 *   3.3V   → VCC  ←  IMPORTANTE: Ra-02 es 3.3V, NO 5V
 *
 * GPIO16 y GPIO17 reservados para PSRAM del WROVER-B — NO usar.
 */

#include "time.h"
#include <math.h>
#include <SPI.h>
#include <LoRa.h>
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

// ── Pines ──────────────────────────────────────────────────────────────────────
#define LED_PIN      13

// ── LoRa Ra-02 (VSPI ESP32 WROVER-B) ──────────────────────────────────────────
#define LORA_NSS     5
#define LORA_RST     14
#define LORA_DIO0    26
#define LORA_FREQ    433E6
#define LORA_SF      7
#define LORA_BW      125E3
#define LORA_CR      5
#define LORA_SYNC    0xA5
#define LORA_POWER   17

#define NODE_TIMEOUT_MS  10000UL
#define CH4_MAX_PARSE    10000.0f

String loraBuf = "";

// ── UUIDs BLE ──────────────────────────────────────────────────────────────────
#define SERVICE_UUID        "0000A001-0000-1000-8000-00805F9B34FB"
#define DEVICE_ID_UUID      "0000A002-0000-1000-8000-00805F9B34FB"
#define READINGS_UUID       "0000A003-0000-1000-8000-00805F9B34FB"
#define PUMP_UUID           "0000A004-0000-1000-8000-00805F9B34FB"
#define HISTORY_REQ_UUID    "0000A005-0000-1000-8000-00805F9B34FB"
#define HISTORY_DATA_UUID   "0000A006-0000-1000-8000-00805F9B34FB"
#define WIFI_CONFIG_UUID    "0000A007-0000-1000-8000-00805F9B34FB"
#define WIFI_STATUS_UUID    "0000A008-0000-1000-8000-00805F9B34FB"

// ── Intervalos ─────────────────────────────────────────────────────────────────
#define BLE_INTERVAL        2000UL
#define WIFI_INTERVAL       60000UL   // 60 s en modo pruebas
#define SAVE_INTERVAL       60000UL
#define PUMP_CHECK_INTERVAL 10000UL
#define SIM_INTERVAL        5000UL    // Actualizar simulación cada 5 s

// ── SPIFFS ─────────────────────────────────────────────────────────────────────
#define HISTORY_FILE  "/history.csv"
#define MAX_RECORDS   900

// ── Preferences ────────────────────────────────────────────────────────────────
Preferences prefs;

// ── Variables BLE ──────────────────────────────────────────────────────────────
BLEServer*          pServer          = nullptr;
BLECharacteristic*  pDeviceIdChar    = nullptr;
BLECharacteristic*  pReadingsChar    = nullptr;
BLECharacteristic*  pPumpChar        = nullptr;
BLECharacteristic*  pHistoryReqChar  = nullptr;
BLECharacteristic*  pHistoryDataChar = nullptr;
BLECharacteristic*  pWifiConfigChar  = nullptr;
BLECharacteristic*  pWifiStatusChar  = nullptr;

bool          bleConnected      = false;
bool          pumpState         = false;
unsigned long lastBleTime       = 0;
unsigned long lastWifiTime      = 0;
unsigned long lastSaveTime      = 0;
unsigned long lastPumpCheckTime = 0;
unsigned long lastSimTime       = 0;
unsigned long pumpCheckInterval = PUMP_CHECK_INTERVAL;

String deviceId = "";
String wifiSsid = "", wifiPassword = "", apiKey = "";
String backendUrl    = "http://3.15.133.197:3000/api/ingest";
String pumpStatusUrl = "http://3.15.133.197:3000/api/ingest/pump-status";
String pumpAckUrl    = "http://3.15.133.197:3000/api/ingest/pump-ack";

// ── Lecturas actuales: Nodo 01 (SIMULADO — aire) ───────────────────────────────
float currentTemperature = 22.0f;
float currentAirHumidity = 65.0f;
float currentCO2         = 400.0f;  // siempre simulado

// ── Lecturas actuales: Nodo 02 (SIMULADO — metano) ────────────────────────────
float currentMethane = 150.0f;

// ── Lecturas actuales: Nodo 03 (REAL via LoRa — suelo) ────────────────────────
float soilTemp = 0.0f;
float soilHum  = 0.0f;
int   soilEC   = 0;
float soilPH   = 0.0f;
int   soilN    = 0;
int   soilP    = 0;
int   soilK    = 0;

// ── Flags de validez ───────────────────────────────────────────────────────────
// N01 y N02 siempre validos en modo simulacion
bool gotValidHumidity = true;
bool gotValidMethane  = true;
bool gotValidSoil     = false;

unsigned long lastNode3Rx = 0;

// ── Helper: extraer campo entre marcadores ────────────────────────────────────
String extractField(const String& frame, const char* tag) {
  int idx = frame.indexOf(tag);
  if (idx < 0) return "";
  int start = idx + strlen(tag);
  int end   = frame.indexOf(',', start);
  if (end < 0) end = frame.length();
  String val = frame.substring(start, end);
  val.trim();
  return val;
}

// ══════════════════════════════════════════════════════════════════════════════
//  Simulación de Nodo 01 (T+H aire) y Nodo 02 (metano)
// ══════════════════════════════════════════════════════════════════════════════

void updateSimulatedReadings() {
  float t = millis() / 1000.0f;  // segundos desde arranque

  // ── Temperatura aire: 22°C base, ±4°C ciclo lento 5 min, ±0.3°C ruido
  float noiseT = ((float)(esp_random() % 601) - 300) / 1000.0f;  // ±0.3
  currentTemperature = 22.0f + 4.0f * sinf(t / 300.0f) + noiseT;
  currentTemperature = constrain(currentTemperature, -10.0f, 60.0f);

  // ── Humedad aire: 65% base, inversamente correlada con T, ±0.5% ruido
  float noiseH = ((float)(esp_random() % 1001) - 500) / 1000.0f;  // ±0.5
  currentAirHumidity = 65.0f - 6.0f * sinf(t / 300.0f) + noiseH;
  currentAirHumidity = constrain(currentAirHumidity, 10.0f, 99.0f);

  // ── Metano: 150 ppm base, ±60 ppm ciclo lento 10 min, ±8 ppm ruido
  float noiseM = ((float)(esp_random() % 1601) - 800) / 100.0f;  // ±8
  currentMethane = 150.0f + 60.0f * sinf(t / 600.0f) + noiseM;
  currentMethane = constrain(currentMethane, 0.0f, 500.0f);

  Serial.printf("[SIM] N01→ T:%.2f°C H:%.2f%%  N02→ CH4:%.2f ppm\n",
    currentTemperature, currentAirHumidity, currentMethane);
}

// ══════════════════════════════════════════════════════════════════════════════
//  Parser LoRa — solo Nodo 03 (real)
// ══════════════════════════════════════════════════════════════════════════════

void parseFrame(const String& frame) {
  if (!frame.startsWith("$")) return;

  int nIdx = frame.indexOf("N:");
  if (nIdx < 0) return;
  String nodeId = frame.substring(nIdx + 2, nIdx + 4);

  // N01 y N02 ignorados — datos simulados
  if (nodeId == "01" || nodeId == "02") {
    Serial.printf("[LoRa] Trama N%s ignorada (modo simulacion)\n", nodeId.c_str());
    return;
  }

  // ── Nodo 03: Sensor suelo RS485 (REAL) ────────────────────────────────────
  if (nodeId == "03") {
    String tStr  = extractField(frame, "T:");
    String hStr  = extractField(frame, "H:");
    String ecStr = extractField(frame, "EC:");
    String phStr = extractField(frame, "PH:");
    String nStr  = extractField(frame, "N:");
    String pStr  = extractField(frame, "P:");
    String kStr  = extractField(frame, "K:");

    if (tStr.length() == 0 || hStr.length() == 0 || phStr.length() == 0) return;

    float t  = tStr.toFloat();
    float h  = hStr.toFloat();
    float ph = phStr.toFloat();
    int   ec = ecStr.toInt();
    int   n  = nStr.toInt();
    int   p  = pStr.toInt();
    int   k  = kStr.toInt();

    // Validación de rangos
    if (t < -20.0f || t > 80.0f)  return;
    if (h <   0.0f || h > 100.0f) return;
    if (ph <  3.0f || ph >  9.0f) return;

    soilTemp = t;
    soilHum  = h;
    soilEC   = ec;
    soilPH   = ph;
    soilN    = n;
    soilP    = p;
    soilK    = k;
    gotValidSoil = true;
    lastNode3Rx  = millis();

    Serial.printf("[N03] T:%.2f H:%.2f EC:%d pH:%.2f N:%d P:%d K:%d  RSSI:%d dBm\n",
      t, h, ec, ph, n, p, k, LoRa.packetRssi());
  }
}

// ── Recepción LoRa no bloqueante ───────────────────────────────────────────────
void readLoRa() {
  int pktSize = LoRa.parsePacket();
  if (pktSize > 0) {
    while (LoRa.available()) {
      char c = (char)LoRa.read();
      if (c == '\n') {
        loraBuf.trim();
        if (loraBuf.length() > 0) parseFrame(loraBuf);
        loraBuf = "";
      } else {
        loraBuf += c;
        if (loraBuf.length() > 128) loraBuf = "";  // protección overflow
      }
    }
  }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Helpers WiFi / NTP / Config
// ══════════════════════════════════════════════════════════════════════════════

bool timeReady() {
  time_t now = time(nullptr);
  return now > 1000000000UL;
}

bool connectWiFi() {
  if (wifiSsid.length() == 0) return false;
  if (WiFi.status() == WL_CONNECTED) return true;
  WiFi.begin(wifiSsid.c_str(), wifiPassword.c_str());
  int r = 0;
  while (WiFi.status() != WL_CONNECTED && r < 20) { delay(500); r++; }
  bool ok = (WiFi.status() == WL_CONNECTED);
  Serial.printf("[WiFi] %s — IP: %s\n", ok ? "Conectado" : "Sin conexion",
    ok ? WiFi.localIP().toString().c_str() : "—");
  return ok;
}

bool loadConfig() {
  prefs.begin("agrosense", true);
  wifiSsid     = prefs.getString("ssid",    "");
  wifiPassword = prefs.getString("pass",    "");
  apiKey       = prefs.getString("api_key", "");
  prefs.end();
  return wifiSsid.length() > 0 && apiKey.length() > 0;
}

void saveConfig() {
  prefs.begin("agrosense", false);
  prefs.putString("ssid",    wifiSsid);
  prefs.putString("pass",    wifiPassword);
  prefs.putString("api_key", apiKey);
  prefs.end();
}

// ── BLE Callbacks ──────────────────────────────────────────────────────────────
class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer*) override    { bleConnected = true;  Serial.println("[BLE] Conectado"); }
  void onDisconnect(BLEServer*) override {
    bleConnected = false;
    Serial.println("[BLE] Desconectado");
    BLEDevice::startAdvertising();
  }
};

class PumpCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* c) override {
    String v = String(c->getValue().c_str()); v.trim();
    pumpState = (v == "1");
    digitalWrite(LED_PIN, pumpState ? HIGH : LOW);
    Serial.printf("[BOMBA] BLE -> %s\n", pumpState ? "ON" : "OFF");
  }
};

class HistoryRequestCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* c) override {
    String v = String(c->getValue().c_str()); v.trim();
    if (v == "GET") sendHistoryBLE();
  }
};

class WifiConfigCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* c) override {
    String raw = String(c->getValue().c_str());
    raw.trim();
    Serial.printf("[WiFi-BLE] Recibido (%d bytes): %s\n", raw.length(), raw.c_str());

    // Formato: "ssid|password|apikey"
    int sep1 = raw.indexOf('|');
    int sep2 = raw.indexOf('|', sep1 + 1);
    if (sep1 < 0 || sep2 < 0) {
      Serial.println("[WiFi-BLE] Formato invalido (esperado: ssid|pass|apikey)");
      return;
    }
    wifiSsid     = raw.substring(0, sep1);
    wifiPassword = raw.substring(sep1 + 1, sep2);
    apiKey       = raw.substring(sep2 + 1);
    saveConfig();
    Serial.printf("[WiFi] Config recibida: ssid=%s apiKey=%s\n",
      wifiSsid.c_str(), apiKey.length() > 0 ? "(ok)" : "(vacio)");
    bool ok = connectWiFi();
    String status = ok ? "CONNECTED" : "DISCONNECTED";
    pWifiStatusChar->setValue(status);
    pWifiStatusChar->notify();
  }
};

// ── JSON de lecturas para BLE ──────────────────────────────────────────────────
String buildReadingJson() {
  char buf[320];
  snprintf(buf, sizeof(buf),
    "{\"t\":%.1f,\"a\":%.1f,\"co2\":%.1f,\"ch4\":%.2f,"
    "\"st\":%.1f,\"sh\":%.1f,\"ec\":%d,\"ph\":%.2f,\"n\":%d,\"p\":%d,\"k\":%d}",
    currentTemperature, currentAirHumidity, currentCO2, currentMethane,
    soilTemp, soilHum, soilEC, soilPH, soilN, soilP, soilK);
  return String(buf);
}

// ── Guardar en SPIFFS ──────────────────────────────────────────────────────────
void saveReadingToSPIFFS() {
  // En modo simulacion guardamos siempre que tengamos suelo real (o al menos datos sim)
  time_t now = time(nullptr);
  File f = SPIFFS.open(HISTORY_FILE, "a");
  if (!f) return;

  // Contar líneas para no exceder MAX_RECORDS
  int lines = 0;
  File fc = SPIFFS.open(HISTORY_FILE, "r");
  if (fc) { while (fc.available()) { if (fc.read() == '\n') lines++; } fc.close(); }

  if (lines >= MAX_RECORDS) {
    File ft = SPIFFS.open("/tmp.csv", "w");
    File fr = SPIFFS.open(HISTORY_FILE, "r");
    if (ft && fr) {
      fr.readStringUntil('\n');  // descartar primera línea
      while (fr.available()) ft.write(fr.read());
    }
    if (ft) ft.close();
    if (fr) fr.close();
    SPIFFS.remove(HISTORY_FILE);
    SPIFFS.rename("/tmp.csv", HISTORY_FILE);
    f = SPIFFS.open(HISTORY_FILE, "a");
    if (!f) return;
  }

  char row[200];
  snprintf(row, sizeof(row),
    "%lu,%.1f,%.1f,%.1f,%.2f,%.1f,%.1f,%d,%.2f,%d,%d,%d\n",
    (unsigned long)now,
    currentTemperature, currentAirHumidity, currentCO2, currentMethane,
    soilTemp, soilHum, soilEC, soilPH, soilN, soilP, soilK);
  f.print(row);
  f.close();
}

// ── Enviar historial por BLE ───────────────────────────────────────────────────
void sendHistoryBLE() {
  File f = SPIFFS.open(HISTORY_FILE, "r");
  if (!f) {
    pHistoryDataChar->setValue(String("EMPTY")); pHistoryDataChar->notify(); return;
  }
  pHistoryDataChar->setValue(String("START")); pHistoryDataChar->notify(); delay(100);
  int sent = 0;
  while (f.available()) {
    String line = f.readStringUntil('\n'); line.trim();
    if (line.length() == 0) continue;
    char json[220];
    int c1 = line.indexOf(',');
    if (c1 > 0) {
      snprintf(json, sizeof(json), "{\"ts\":%s,\"raw\":\"%s\"}",
        line.substring(0, c1).c_str(),
        line.substring(c1 + 1).c_str());
      pHistoryDataChar->setValue(String(json)); pHistoryDataChar->notify(); delay(50); sent++;
    }
  }
  f.close();
  char end[32]; snprintf(end, sizeof(end), "END:%d", sent);
  pHistoryDataChar->setValue(String(end)); pHistoryDataChar->notify();
  Serial.printf("[HISTORY] Enviados %d registros\n", sent);
}

// ══════════════════════════════════════════════════════════════════════════════
//  AWS + Bomba
// ══════════════════════════════════════════════════════════════════════════════

void sendToAWS() {
  if (apiKey.length() == 0) {
    Serial.println("[WiFi] Sin api_key — POST omitido. Envia config via BLE.");
    return;
  }
  // En modo simulacion N01+N02 siempre tienen datos; enviamos aunque N03 aun no tenga
  // (soilTemp etc valdrán 0 hasta que llegue el primer frame real del Nodo 03)

  if (WiFi.status() != WL_CONNECTED) { if (!connectWiFi()) return; }

  HTTPClient http;
  http.begin(backendUrl);
  http.addHeader("Content-Type", "application/json");

  char body[512];
  if (gotValidSoil) {
    // Nodo 03 activo: incluir todos los campos de suelo
    snprintf(body, sizeof(body),
      "{\"device_id\":\"%s\",\"api_key\":\"%s\","
      "\"temperature\":%.1f,\"air_humidity\":%.1f,\"co2\":%.1f,\"methane\":%.2f,"
      "\"soil_temp\":%.1f,\"soil_hum\":%.1f,\"ec\":%d,\"ph\":%.2f,"
      "\"nitrogen\":%d,\"phosphorus\":%d,\"potassium\":%d}",
      deviceId.c_str(), apiKey.c_str(),
      currentTemperature, currentAirHumidity, currentCO2, currentMethane,
      soilTemp, soilHum, soilEC, soilPH, soilN, soilP, soilK);
  } else {
    // Nodo 03 aun sin dato: enviar campos de suelo como null
    snprintf(body, sizeof(body),
      "{\"device_id\":\"%s\",\"api_key\":\"%s\","
      "\"temperature\":%.1f,\"air_humidity\":%.1f,\"co2\":%.1f,\"methane\":%.2f,"
      "\"soil_temp\":null,\"soil_hum\":null,\"ec\":null,\"ph\":null,"
      "\"nitrogen\":null,\"phosphorus\":null,\"potassium\":null}",
      deviceId.c_str(), apiKey.c_str(),
      currentTemperature, currentAirHumidity, currentCO2, currentMethane);
  }

  int code = http.POST(body);
  Serial.printf("[WiFi][SIM] POST %d  T:%.1f H:%.1f CH4:%.2f  SoilT:%.1f pH:%.2f EC:%d N03:%s\n",
    code, currentTemperature, currentAirHumidity, currentMethane,
    soilTemp, soilPH, soilEC, gotValidSoil ? "REAL" : "sin dato");
  http.end();
}

void sendPumpAck() {
  if (apiKey.length() == 0 || WiFi.status() != WL_CONNECTED) return;
  HTTPClient http; http.begin(pumpAckUrl); http.addHeader("Content-Type", "application/json");
  char body[128];
  snprintf(body, sizeof(body), "{\"device_id\":\"%s\",\"api_key\":\"%s\"}",
    deviceId.c_str(), apiKey.c_str());
  http.POST(body); http.end();
}

void checkPumpSchedule() {
  if (apiKey.length() == 0) return;
  if (WiFi.status() != WL_CONNECTED) { if (!connectWiFi()) return; }
  String url = pumpStatusUrl + "?device_id=" + deviceId + "&api_key=" + apiKey;
  HTTPClient http; http.begin(url);
  int code = http.GET(); String payload = http.getString(); http.end();
  if (code == 200) {
    StaticJsonDocument<128> doc;
    if (deserializeJson(doc, payload)) return;
    bool on = doc["pump_on"] | false;
    const char* mode = doc["mode"] | "auto";
    bool isManual = strcmp(mode, "manual") == 0;
    pumpCheckInterval = isManual ? 2000 : PUMP_CHECK_INTERVAL;
    if (on != pumpState) {
      pumpState = on; digitalWrite(LED_PIN, pumpState ? HIGH : LOW);
      Serial.printf("[BOMBA] %s\n", pumpState ? "ENCENDIDA" : "APAGADA");
      if (isManual) sendPumpAck();
    }
  }
}

// ══════════════════════════════════════════════════════════════════════════════
//  SETUP
// ══════════════════════════════════════════════════════════════════════════════

void setup() {
  Serial.begin(115200);
  delay(300);

  Serial.println("=== AgroSense WROVER-B LoRa v6 SIM — Modo Pruebas UX ===");
  Serial.println("[SIM] N01(aire) y N02(metano) SIMULADOS | N03(suelo) REAL");

  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  // LoRa — se inicializa para recibir solo N03
  LoRa.setPins(LORA_NSS, LORA_RST, LORA_DIO0);
  if (!LoRa.begin(LORA_FREQ)) {
    Serial.println("[LoRa] ERROR: modulo no encontrado.");
    while (true) delay(1000);
  }
  LoRa.setSpreadingFactor(LORA_SF);
  LoRa.setSignalBandwidth(LORA_BW);
  LoRa.setCodingRate4(LORA_CR);
  LoRa.setTxPower(LORA_POWER);
  LoRa.setSyncWord(LORA_SYNC);
  Serial.printf("[LoRa] OK — 433 MHz | SF%d | BW%.0fkHz | SyncWord 0x%02X\n",
    LORA_SF, LORA_BW / 1000.0f, LORA_SYNC);

  if (!SPIFFS.begin(true)) Serial.println("[SPIFFS] Error");
  else Serial.println("[SPIFFS] OK");

  bool hasConfig = loadConfig();

  // Arranque: valores iniciales simulados (antes de que corra el timer)
  updateSimulatedReadings();

  // Esperar primeras tramas del Nodo 03 (hasta 8 s)
  Serial.println("[LoRa] Esperando Nodo 03...");
  unsigned long waitStart = millis();
  while (millis() - waitStart < 8000) { readLoRa(); delay(10); }
  Serial.printf("[LoRa] N03=%s\n", gotValidSoil ? "OK" : "sin dato aun");

  if (hasConfig && connectWiFi()) {
    configTime(-5 * 3600, 0, "pool.ntp.org");
    int r = 0; while (!timeReady() && r < 10) { delay(1000); r++; }
    if (timeReady()) Serial.println("[NTP] Hora OK");
  }

  BLEDevice::init("AgroSense");
  deviceId = String(BLEDevice::getAddress().toString().c_str());
  Serial.printf("[BLE] MAC: %s\n", deviceId.c_str());

  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new ServerCallbacks());
  BLEService* pService = pServer->createService(BLEUUID(SERVICE_UUID), 40);

  pDeviceIdChar = pService->createCharacteristic(DEVICE_ID_UUID,
    BLECharacteristic::PROPERTY_READ);
  pDeviceIdChar->setValue(deviceId);

  pReadingsChar = pService->createCharacteristic(READINGS_UUID,
    BLECharacteristic::PROPERTY_NOTIFY);
  pReadingsChar->addDescriptor(new BLE2902());

  pPumpChar = pService->createCharacteristic(PUMP_UUID,
    BLECharacteristic::PROPERTY_WRITE |
    BLECharacteristic::PROPERTY_READ  |
    BLECharacteristic::PROPERTY_NOTIFY);
  pPumpChar->addDescriptor(new BLE2902());
  pPumpChar->setCallbacks(new PumpCallbacks());
  pPumpChar->setValue(String("0"));

  pHistoryReqChar = pService->createCharacteristic(HISTORY_REQ_UUID,
    BLECharacteristic::PROPERTY_WRITE);
  pHistoryReqChar->setCallbacks(new HistoryRequestCallbacks());

  pHistoryDataChar = pService->createCharacteristic(HISTORY_DATA_UUID,
    BLECharacteristic::PROPERTY_NOTIFY);
  pHistoryDataChar->addDescriptor(new BLE2902());

  pWifiConfigChar = pService->createCharacteristic(WIFI_CONFIG_UUID,
    BLECharacteristic::PROPERTY_WRITE);
  pWifiConfigChar->setCallbacks(new WifiConfigCallbacks());

  pWifiStatusChar = pService->createCharacteristic(WIFI_STATUS_UUID,
    BLECharacteristic::PROPERTY_NOTIFY | BLECharacteristic::PROPERTY_READ);
  pWifiStatusChar->addDescriptor(new BLE2902());
  pWifiStatusChar->setValue(WiFi.status() == WL_CONNECTED ? String("CONNECTED") :
                            (hasConfig ? String("DISCONNECTED") : String("NOT_CONFIGURED")));

  pService->start();
  BLEAdvertising* pAdv = BLEDevice::getAdvertising();
  pAdv->addServiceUUID(SERVICE_UUID);
  pAdv->setScanResponse(true);
  pAdv->setMinPreferred(0x06);
  pAdv->setMinPreferred(0x12);
  BLEDevice::startAdvertising();

  Serial.println("[SIM] Listo. N01+N02 simulados | N03 real | POST cada 60 s");
}

// ══════════════════════════════════════════════════════════════════════════════
//  LOOP
// ══════════════════════════════════════════════════════════════════════════════

void loop() {
  unsigned long now = millis();

  // Recibir LoRa (solo N03 procesado, el resto ignorado)
  readLoRa();

  // Aviso si N03 se silencia (solo cuando lastNode3Rx ya fue seteado)
  if (gotValidSoil && lastNode3Rx > 0 && now > lastNode3Rx && (now - lastNode3Rx) > 300000)
    Serial.printf("[LoRa] AVISO: N03 silencioso hace %lu ms\n", now - lastNode3Rx);

  // Actualizar simulacion N01+N02 cada 5 s
  if (now - lastSimTime >= SIM_INTERVAL) {
    lastSimTime = now;
    updateSimulatedReadings();
  }

  // BLE cada 2 s
  if (now - lastBleTime >= BLE_INTERVAL) {
    lastBleTime = now;
    if (bleConnected) {
      String payload = buildReadingJson();
      const int chunkSize = 20;
      int len = payload.length();
      for (int i = 0; i < len; i += chunkSize) {
        pReadingsChar->setValue(payload.substring(i, i + chunkSize));
        pReadingsChar->notify();
        delay(30);
      }
      Serial.printf("[BLE] -> %s\n", payload.c_str());
    }
  }

  // SPIFFS cada 60 s
  if (now - lastSaveTime >= SAVE_INTERVAL) {
    lastSaveTime = now;
    saveReadingToSPIFFS();
  }

  // AWS cada 60 s
  if (now - lastWifiTime >= WIFI_INTERVAL) {
    lastWifiTime = now;
    sendToAWS();
  }

  // Bomba
  if (now - lastPumpCheckTime >= pumpCheckInterval) {
    lastPumpCheckTime = now;
    checkPumpSchedule();
  }

  delay(20);
}
