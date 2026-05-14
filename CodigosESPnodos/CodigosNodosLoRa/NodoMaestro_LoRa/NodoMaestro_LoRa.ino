/*
 * AgroSense - ESP32 WROVER-B DevKit  |  MAESTRO LoRa
 * ====================================================
 * Recepción pasiva (PUSH): los nodos envían solos cada 2-2.5 s.
 * Trama nodo 01: "$N:01,T:28.50,H:63.20\n"  → temperatura + humedad
 * Trama nodo 02: "$N:02,M:1234.56\n"         → metano (0–10000 ppm)
 *
 * LoRa Ra-02 (SX1278 433 MHz) — conexión VSPI ESP32 WROVER-B:
 *   GPIO23 → MOSI        GPIO5  → NSS (CS)
 *   GPIO19 → MISO        GPIO14 → RST
 *   GPIO18 → SCK         GPIO26 → DIO0 (IRQ)
 *   3.3V   → VCC  ←  IMPORTANTE: Ra-02 es 3.3V, NO 5V
 *   GND    → GND
 *
 * Librería requerida: "LoRa" by Sandeep Mistry (Arduino Library Manager)
 *
 * Configuración LoRa: 433 MHz | SF7 | BW 125 kHz | CR 4/5 | SyncWord 0xA5
 *
 * GPIO16 y GPIO17 reservados para PSRAM del WROVER-B — NO usar.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * FIXES v4/v5 (heredados de versión RS485 — sin cambios en lógica):
 *
 *  FIX A — Rango de metano corregido: CH4_MAX_PARSE = 10000 ppm
 *  FIX B — Guardia gotValidHumidity && gotValidMethane antes de POST/SAVE
 *  FIX C — Fallback por timeout: usa último valor válido (NODE_TIMEOUT_MS)
 *  FIX D — trim() en campos H y M antes de toFloat()
 *  FIX E — Guardia gotValidX en lugar de lastNodeXRx>0 (evita overflow)
 * ─────────────────────────────────────────────────────────────────────────
 */

#include "time.h"
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

// ── Pines ──────────────────────────────────────────────────────────────────
#define LED_PIN      13

// ── LoRa Ra-02 (VSPI ESP32 WROVER-B) ──────────────────────────────────────
#define LORA_NSS     5     // GPIO5  — NSS/CS (VSPI SS por defecto)
#define LORA_RST     14    // GPIO14 — Reset
#define LORA_DIO0    26    // GPIO26 — DIO0 / IRQ
// MOSI=23, MISO=19, SCK=18 son los pines VSPI por defecto del ESP32

#define LORA_FREQ    433E6
#define LORA_SF      7
#define LORA_BW      125E3
#define LORA_CR      5
#define LORA_SYNC    0xA5  // sync word privado AgroSense
#define LORA_POWER   17    // dBm (máx Ra-02 = 17–20 según versión)

// Tiempo máximo sin paquete de un nodo antes de usar fallback
#define NODE_TIMEOUT_MS  10000UL

// FIX A: rango real del sensor de metano
#define CH4_MAX_PARSE    10000.0f

String loraBuf = "";

// ── UUIDs BLE ─────────────────────────────────────────────────────────────
#define SERVICE_UUID        "0000A001-0000-1000-8000-00805F9B34FB"
#define DEVICE_ID_UUID      "0000A002-0000-1000-8000-00805F9B34FB"
#define READINGS_UUID       "0000A003-0000-1000-8000-00805F9B34FB"
#define PUMP_UUID           "0000A004-0000-1000-8000-00805F9B34FB"
#define HISTORY_REQ_UUID    "0000A005-0000-1000-8000-00805F9B34FB"
#define HISTORY_DATA_UUID   "0000A006-0000-1000-8000-00805F9B34FB"
#define WIFI_CONFIG_UUID    "0000A007-0000-1000-8000-00805F9B34FB"
#define WIFI_STATUS_UUID    "0000A008-0000-1000-8000-00805F9B34FB"

// ── Intervalos ────────────────────────────────────────────────────────────
#define BLE_INTERVAL        2000
#define WIFI_INTERVAL       30000
#define SAVE_INTERVAL       60000
#define PUMP_CHECK_INTERVAL 10000

// ── SPIFFS ────────────────────────────────────────────────────────────────
#define HISTORY_FILE  "/history.csv"
#define MAX_RECORDS   900

// ── Variables BLE ─────────────────────────────────────────────────────────
Preferences prefs;

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
unsigned long pumpCheckInterval = PUMP_CHECK_INTERVAL;

String deviceId = "";

// ── Credenciales ──────────────────────────────────────────────────────────
String wifiSsid      = "";
String wifiPassword  = "";
String apiKey        = "";
String backendUrl    = "http://3.15.133.197:3000/api/ingest";
String pumpStatusUrl = "http://3.15.133.197:3000/api/ingest/pump-status";
String pumpAckUrl    = "http://3.15.133.197:3000/api/ingest/pump-ack";

// ── Lecturas actuales ──────────────────────────────────────────────────────
float currentTemperature = 0.0f;
float currentAirHumidity = 0.0f;
float currentCO2         = 400.0f;
float currentMethane     = 0.0f;

// ── Último valor válido y timestamps de recepción ─────────────────────────
float lastValidHumidity = 0.0f;
float lastValidMethane  = 0.0f;
bool  gotValidHumidity  = false;
bool  gotValidMethane   = false;

unsigned long lastNode1Rx = 0;
unsigned long lastNode2Rx = 0;

// ── Función auxiliar: valores con fallback por timeout ────────────────────
bool getValidReadings(float& outHum, float& outMethane) {
  unsigned long now = millis();

  bool node1Stale = (lastNode1Rx == 0) || ((now - lastNode1Rx) > NODE_TIMEOUT_MS);
  bool node2Stale = (lastNode2Rx == 0) || ((now - lastNode2Rx) > NODE_TIMEOUT_MS);

  if (node1Stale && gotValidHumidity) {
    outHum = lastValidHumidity;
    Serial.printf("[FALLBACK] Nodo01 timeout -> humedad %.1f\n", outHum);
  } else {
    outHum = currentAirHumidity;
  }

  if (node2Stale && gotValidMethane) {
    outMethane = lastValidMethane;
    Serial.printf("[FALLBACK] Nodo02 timeout -> metano %.2f\n", outMethane);
  } else {
    outMethane = currentMethane;
  }

  // FIX B: retorna true solo si ambos nodos enviaron al menos un dato
  return gotValidHumidity && gotValidMethane;
}

// ══════════════════════════════════════════════════════════════════════════
//  Parser de tramas (idéntico a versión RS485)
// ══════════════════════════════════════════════════════════════════════════

void parseFrame(const String& frame) {
  if (!frame.startsWith("$")) return;

  int nIdx = frame.indexOf("N:");
  if (nIdx < 0) return;
  String nodeId = frame.substring(nIdx + 2, nIdx + 4);

  if (nodeId == "01") {
    int tIdx = frame.indexOf("T:");
    int hIdx = frame.indexOf("H:");
    int cIdx = frame.indexOf(",", tIdx);
    if (tIdx < 0 || hIdx < 0 || cIdx < 0) return;

    float t = frame.substring(tIdx + 2, cIdx).toFloat();

    String hStr = frame.substring(hIdx + 2);
    hStr.trim();  // FIX D
    float h = hStr.toFloat();

    if (t < -40.0f || t > 125.0f) return;
    if (h <   0.0f || h > 100.0f) return;

    currentTemperature = t;
    currentAirHumidity = h;
    lastValidHumidity  = h;
    gotValidHumidity   = true;
    lastNode1Rx        = millis();

    Serial.printf("[N01] T:%.2f H:%.2f  RSSI:%d dBm\n", t, h, LoRa.packetRssi());

  } else if (nodeId == "02") {
    int mIdx = frame.indexOf("M:");
    if (mIdx < 0) return;

    String mStr = frame.substring(mIdx + 2);
    mStr.trim();  // FIX D
    float m = mStr.toFloat();

    // FIX A: rango corregido a 0–10000 ppm
    if (m < 0.0f || m > CH4_MAX_PARSE) return;

    currentMethane   = m;
    lastValidMethane = m;
    gotValidMethane  = true;
    lastNode2Rx      = millis();

    Serial.printf("[N02] CH4:%.2f ppm  RSSI:%d dBm\n", m, LoRa.packetRssi());
  }
}

// ══════════════════════════════════════════════════════════════════════════
//  LoRa — recepción no bloqueante
// ══════════════════════════════════════════════════════════════════════════

void readLoRa() {
  int packetSize = LoRa.parsePacket();
  if (packetSize == 0) return;

  loraBuf = "";
  while (LoRa.available()) {
    char c = (char)LoRa.read();
    if (loraBuf.length() < 64) loraBuf += c;
  }
  loraBuf.trim();
  if (loraBuf.length() > 0) parseFrame(loraBuf);
}

// ══════════════════════════════════════════════════════════════════════════
//  NVS
// ══════════════════════════════════════════════════════════════════════════

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

// ══════════════════════════════════════════════════════════════════════════
//  WiFi + NTP
// ══════════════════════════════════════════════════════════════════════════

bool timeReady() {
  struct tm timeinfo;
  return getLocalTime(&timeinfo);
}

bool connectWiFi() {
  if (wifiSsid.length() == 0) { Serial.println("[WiFi] Sin credenciales"); return false; }
  Serial.printf("[WiFi] Conectando a %s\n", wifiSsid.c_str());
  WiFi.begin(wifiSsid.c_str(), wifiPassword.c_str());
  int tries = 0;
  while (WiFi.status() != WL_CONNECTED && tries < 20) { delay(500); Serial.print('.'); tries++; }
  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("\n[WiFi] IP: %s\n", WiFi.localIP().toString().c_str());
    return true;
  }
  Serial.println("\n[WiFi] Sin conexion");
  return false;
}

void notifyWifiStatus(const char* status) {
  if (pWifiStatusChar) { pWifiStatusChar->setValue(String(status)); pWifiStatusChar->notify(); }
}

// ══════════════════════════════════════════════════════════════════════════
//  Callbacks BLE
// ══════════════════════════════════════════════════════════════════════════

class WifiConfigCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* pChar) override {
    String val = pChar->getValue();
    if (val.length() == 0) return;
    if (val == "RESET") {
      prefs.begin("agrosense", false); prefs.clear(); prefs.end();
      wifiSsid = wifiPassword = apiKey = "";
      WiFi.disconnect(true);
      notifyWifiStatus("RESET_OK");
      return;
    }
    int sep1 = val.indexOf('|'), sep2 = val.indexOf('|', sep1 + 1);
    if (sep1 < 0 || sep2 < 0) { notifyWifiStatus("ERROR:formato_invalido"); return; }
    wifiSsid     = val.substring(0, sep1);
    wifiPassword = val.substring(sep1 + 1, sep2);
    apiKey       = val.substring(sep2 + 1);
    saveConfig();
    notifyWifiStatus("CONNECTING");
    WiFi.disconnect(); delay(500);
    if (connectWiFi()) {
      configTime(-5 * 3600, 0, "pool.ntp.org");
      int r = 0; while (!timeReady() && r < 10) { delay(1000); r++; }
      notifyWifiStatus("CONNECTED");
    } else { notifyWifiStatus("ERROR:no_se_pudo_conectar"); }
  }
};

class PumpCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* pChar) override {
    String val = pChar->getValue();
    if (val.length() == 0) return;
    char cmd = val[0];
    pumpState = (cmd == '1');
    digitalWrite(LED_PIN, pumpState ? HIGH : LOW);
    Serial.printf("[BOMBA] %s (BLE)\n", pumpState ? "ON" : "OFF");
    char st[2] = { cmd, '\0' };
    pPumpChar->setValue((uint8_t*)st, 1); pPumpChar->notify();
  }
};

class HistoryRequestCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* pChar) override {
    String val = pChar->getValue();
    if (val == "GET") sendHistoryBLE();
    else if (val == "CLEAR") {
      SPIFFS.remove(HISTORY_FILE);
      pHistoryDataChar->setValue(String("CLEARED")); pHistoryDataChar->notify();
    }
  }
};

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer*)    override { bleConnected = true;  Serial.println("[BLE] Conectado"); }
  void onDisconnect(BLEServer*) override { bleConnected = false; delay(500); BLEDevice::startAdvertising(); }
};

// ══════════════════════════════════════════════════════════════════════════
//  JSON para BLE
// ══════════════════════════════════════════════════════════════════════════

String buildReadingJson() {
  float hOut, mOut;
  getValidReadings(hOut, mOut);
  char buf[256];
  snprintf(buf, sizeof(buf),
    "{\"device_id\":\"%s\",\"temperature\":%.1f,\"air_humidity\":%.1f,\"co2\":%.1f,\"methane\":%.2f}",
    deviceId.c_str(), currentTemperature, hOut, currentCO2, mOut);
  return String(buf);
}

// ══════════════════════════════════════════════════════════════════════════
//  SPIFFS
// ══════════════════════════════════════════════════════════════════════════

void saveReadingToSPIFFS() {
  float hToSave, mToSave;
  bool ready = getValidReadings(hToSave, mToSave);

  // FIX B
  if (!ready) {
    Serial.println("[SPIFFS] Esperando datos de todos los nodos — guardado omitido");
    return;
  }

  String existing = ""; int count = 0;
  if (SPIFFS.exists(HISTORY_FILE)) {
    File f = SPIFFS.open(HISTORY_FILE, "r");
    if (f) { existing = f.readString(); f.close();
      for (int i = 0; i < (int)existing.length(); i++) if (existing[i] == '\n') count++; }
  }
  if (count >= MAX_RECORDS) {
    int skip = 0, pos = 0;
    while (pos < (int)existing.length() && skip < 50) { if (existing[pos] == '\n') skip++; pos++; }
    existing = existing.substring(pos);
  }

  time_t now; time(&now);
  char line[96];
  snprintf(line, sizeof(line), "%ld,%.1f,%.1f,%.1f,%.2f\n",
    now, currentTemperature, hToSave, currentCO2, mToSave);
  File f = SPIFFS.open(HISTORY_FILE, "w");
  if (f) { f.print(existing); f.print(line); f.close();
    Serial.printf("[SPIFFS] Registro #%d  T:%.1f H:%.1f CH4:%.2f\n",
      count + 1, currentTemperature, hToSave, mToSave); }
}

void sendHistoryBLE() {
  if (!SPIFFS.exists(HISTORY_FILE)) {
    pHistoryDataChar->setValue(String("EMPTY")); pHistoryDataChar->notify(); return; }
  File f = SPIFFS.open(HISTORY_FILE, "r");
  if (!f) { pHistoryDataChar->setValue(String("ERROR")); pHistoryDataChar->notify(); return; }
  pHistoryDataChar->setValue(String("START")); pHistoryDataChar->notify(); delay(100);
  int sent = 0;
  while (f.available()) {
    String line = f.readStringUntil('\n'); line.trim();
    if (line.length() == 0) continue;
    int c1=line.indexOf(','), c2=line.indexOf(',',c1+1),
        c3=line.indexOf(',',c2+1), c4=line.indexOf(',',c3+1);
    if (c1>0&&c2>0&&c3>0&&c4>0) {
      char json[160];
      snprintf(json, sizeof(json),
        "{\"ts\":%s,\"t\":%s,\"a\":%s,\"co2\":%s,\"ch4\":%s}",
        line.substring(0,c1).c_str(), line.substring(c1+1,c2).c_str(),
        line.substring(c2+1,c3).c_str(), line.substring(c3+1,c4).c_str(),
        line.substring(c4+1).c_str());
      pHistoryDataChar->setValue(String(json)); pHistoryDataChar->notify(); delay(50); sent++;
    }
  }
  f.close();
  char end[32]; snprintf(end, sizeof(end), "END:%d", sent);
  pHistoryDataChar->setValue(String(end)); pHistoryDataChar->notify();
  Serial.printf("[HISTORY] Enviados %d registros\n", sent);
}

// ══════════════════════════════════════════════════════════════════════════
//  AWS + Bomba
// ══════════════════════════════════════════════════════════════════════════

void sendToAWS() {
  if (apiKey.length() == 0) return;

  float hToSend, mToSend;
  bool ready = getValidReadings(hToSend, mToSend);

  // FIX B
  if (!ready) {
    Serial.println("[WiFi] Esperando datos de todos los nodos — POST omitido");
    return;
  }

  if (WiFi.status() != WL_CONNECTED) { if (!connectWiFi()) return; }

  HTTPClient http; http.begin(backendUrl); http.addHeader("Content-Type", "application/json");
  char body[320];
  snprintf(body, sizeof(body),
    "{\"device_id\":\"%s\",\"api_key\":\"%s\",\"temperature\":%.1f,\"air_humidity\":%.1f,\"co2\":%.1f,\"methane\":%.2f}",
    deviceId.c_str(), apiKey.c_str(), currentTemperature, hToSend, currentCO2, mToSend);
  int code = http.POST(body);
  Serial.printf("[WiFi] POST %d  T:%.1f H:%.1f CH4:%.2f\n",
    code, currentTemperature, hToSend, mToSend);
  http.end();
}

void sendPumpAck() {
  if (apiKey.length() == 0 || WiFi.status() != WL_CONNECTED) return;
  HTTPClient http; http.begin(pumpAckUrl); http.addHeader("Content-Type", "application/json");
  char body[128];
  snprintf(body, sizeof(body),
    "{\"device_id\":\"%s\",\"api_key\":\"%s\"}", deviceId.c_str(), apiKey.c_str());
  int code = http.POST(body);
  Serial.printf("[PUMP_ACK] HTTP %d\n", code);
  http.end();
}

void checkPumpSchedule() {
  if (apiKey.length() == 0) return;
  if (WiFi.status() != WL_CONNECTED) { if (!connectWiFi()) return; }
  String url = pumpStatusUrl + "?device_id=" + deviceId + "&api_key=" + apiKey;
  HTTPClient http; http.begin(url);
  int code = http.GET(); String payload = http.getString(); http.end();
  Serial.printf("[PUMP_CHECK] HTTP %d  %s\n", code, payload.c_str());
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

// ══════════════════════════════════════════════════════════════════════════
//  SETUP
// ══════════════════════════════════════════════════════════════════════════

void setup() {
  Serial.begin(115200);
  delay(300);

  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  // ── Inicializar LoRa ────────────────────────────────────────────────────
  LoRa.setPins(LORA_NSS, LORA_RST, LORA_DIO0);
  if (!LoRa.begin(LORA_FREQ)) {
    Serial.println("[LoRa] ERROR: modulo no encontrado. Verifica conexiones.");
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

  // Esperar primeras tramas de los nodos (hasta 5 s)
  Serial.println("[LoRa] Esperando nodos...");
  unsigned long waitStart = millis();
  while (millis() - waitStart < 5000) { readLoRa(); delay(10); }
  Serial.printf("[LoRa] Nodo01=%s  Nodo02=%s\n",
    gotValidHumidity ? "OK" : "sin dato",
    gotValidMethane  ? "OK" : "sin dato");

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

  pDeviceIdChar = pService->createCharacteristic(DEVICE_ID_UUID, BLECharacteristic::PROPERTY_READ);
  pDeviceIdChar->setValue(deviceId);

  pReadingsChar = pService->createCharacteristic(READINGS_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  pReadingsChar->addDescriptor(new BLE2902());

  pPumpChar = pService->createCharacteristic(PUMP_UUID,
    BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
  pPumpChar->addDescriptor(new BLE2902());
  pPumpChar->setCallbacks(new PumpCallbacks());
  pPumpChar->setValue(String("0"));

  pHistoryReqChar = pService->createCharacteristic(HISTORY_REQ_UUID, BLECharacteristic::PROPERTY_WRITE);
  pHistoryReqChar->setCallbacks(new HistoryRequestCallbacks());

  pHistoryDataChar = pService->createCharacteristic(HISTORY_DATA_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  pHistoryDataChar->addDescriptor(new BLE2902());

  pWifiConfigChar = pService->createCharacteristic(WIFI_CONFIG_UUID, BLECharacteristic::PROPERTY_WRITE);
  pWifiConfigChar->setCallbacks(new WifiConfigCallbacks());

  pWifiStatusChar = pService->createCharacteristic(WIFI_STATUS_UUID,
    BLECharacteristic::PROPERTY_NOTIFY | BLECharacteristic::PROPERTY_READ);
  pWifiStatusChar->addDescriptor(new BLE2902());
  pWifiStatusChar->setValue(WiFi.status() == WL_CONNECTED ? String("CONNECTED") :
                            (hasConfig ? String("DISCONNECTED") : String("NOT_CONFIGURED")));

  pService->start();
  BLEAdvertising* pAdv = BLEDevice::getAdvertising();
  pAdv->addServiceUUID(SERVICE_UUID); pAdv->setScanResponse(true);
  pAdv->setMinPreferred(0x06); pAdv->setMinPreferred(0x12);
  BLEDevice::startAdvertising();

  Serial.println("=== AgroSense WROVER-B LoRa listo ===");
}

// ══════════════════════════════════════════════════════════════════════════
//  LOOP
// ══════════════════════════════════════════════════════════════════════════

void loop() {
  unsigned long now = millis();

  readLoRa();

  // FIX E: aviso usa gotValidX como guardia para evitar overflow unsigned long
  if (gotValidHumidity && (now - lastNode1Rx) > 300000)
    Serial.printf("[LoRa] AVISO: Nodo01 silencioso hace %lu ms\n", now - lastNode1Rx);
  if (gotValidMethane && (now - lastNode2Rx) > 300000)
    Serial.printf("[LoRa] AVISO: Nodo02 silencioso hace %lu ms\n", now - lastNode2Rx);

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

  // SPIFFS cada 60 s — FIX B
  if (now - lastSaveTime >= SAVE_INTERVAL) { lastSaveTime = now; saveReadingToSPIFFS(); }

  // AWS cada 30 s — FIX B
  if (now - lastWifiTime >= WIFI_INTERVAL) { lastWifiTime = now; sendToAWS(); }

  // Bomba
  if (now - lastPumpCheckTime >= pumpCheckInterval) { lastPumpCheckTime = now; checkPumpSchedule(); }

  delay(20);
}
