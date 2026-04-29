/*
 * AgroSense - ESP32 WROVER-B DevKit  |  MAESTRO RS485
 * =====================================================
 * Recepción pasiva (PUSH): los nodos envían solos cada 2 s.
 * Trama nodo 01: "$N:01,T:28.50,H:63.20\n"  → temperatura + humedad
 * Trama nodo 02: "$N:02,M:3.47\n"            → metano
 *
 * RS485 (MAX485):
 *   GPIO32 → RO (RX)   — GPIO16/17 reservados para PSRAM del WROVER-B
 *   GPIO33 → DI (TX)
 *   GPIO25 → DE + RE unidos
 *   VCC    → 3.3V  ← importante, NO 5V
 *   GND    → GND.
 *
 * Todo lo demás idéntico al original:
 *   BLE (mismos UUIDs), NVS, SPIFFS, AWS, bomba, NTP
 *
 * FIX v2: lastValidHumidity / lastValidMethane
 *   Cuando hay un glitch en el bus RS485 y currentAirHumidity o
 *   currentMethane caen a 0, sendToAWS() y saveReadingToSPIFFS()
 *   usan el último valor válido recibido en lugar de enviar 0.
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

// ── Pines ──────────────────────────────────────────────────────────────────
#define LED_PIN     13

// ── RS485 ─────────────────────────────────────────────────────────────────
#define RS485_RX    32
#define RS485_TX    33
#define RS485_DE    25
#define RS485_BAUD  9600

HardwareSerial RS485Serial(2);
String rs485Buf = "";

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
#define MAX_RECORDS   500

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

// ── Último valor válido (fallback anti-glitch RS485) ──────────────────────
// Se actualizan solo cuando el valor parseado es > 0.
// sendToAWS() y saveReadingToSPIFFS() los usan como fallback
// para evitar enviar 0 al servidor cuando hay un timeout de bus.
float lastValidHumidity = 0.0f;
float lastValidMethane  = 0.0f;
bool  gotValidHumidity  = false;
bool  gotValidMethane   = false;

unsigned long lastNode1Rx = 0;
unsigned long lastNode2Rx = 0;

// ══════════════════════════════════════════════════════════════════════════
//  RS485 — recepción pasiva
// ══════════════════════════════════════════════════════════════════════════

/*
 * Parsea tramas:
 *   "$N:01,T:28.50,H:63.20"  → temperatura y humedad
 *   "$N:02,M:3.47"           → metano
 */
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
    float h = frame.substring(hIdx + 2).toFloat();
    if (t < -40.0f || t > 125.0f) return;
    if (h <   0.0f || h > 100.0f) return;

    currentTemperature = t;
    currentAirHumidity = h;

    // Solo actualizar fallback si el valor es positivo
    if (h > 0.0f) {
      lastValidHumidity = h;
      gotValidHumidity  = true;
    }

    lastNode1Rx = millis();
    Serial.printf("[N01] T:%.2f H:%.2f\n", t, h);

  } else if (nodeId == "02") {
    int mIdx = frame.indexOf("M:");
    if (mIdx < 0) return;

    float m = frame.substring(mIdx + 2).toFloat();
    if (m < 0.0f || m > 20.0f) return;

    currentMethane = m;

    // Solo actualizar fallback si el valor es positivo
    if (m > 0.0f) {
      lastValidMethane = m;
      gotValidMethane  = true;
    }

    lastNode2Rx = millis();
    Serial.printf("[N02] CH4:%.2f ppm\n", m);
  }
}

void readRS485() {
  while (RS485Serial.available()) {
    char c = RS485Serial.read();
    if (c == '\n') {
      rs485Buf.trim();
      if (rs485Buf.length() > 0) parseFrame(rs485Buf);
      rs485Buf = "";
    } else if (rs485Buf.length() < 64) {
      rs485Buf += c;
    } else {
      rs485Buf = "";   // trama corrupta
    }
  }
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
//  WiFi
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
  Serial.println("\n[WiFi] Sin conexión");
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
//  JSON
// ══════════════════════════════════════════════════════════════════════════

String buildReadingJson() {
  char buf[256];
  snprintf(buf, sizeof(buf),
    "{\"device_id\":\"%s\",\"temperature\":%.1f,\"air_humidity\":%.1f,\"co2\":%.1f,\"methane\":%.2f}",
    deviceId.c_str(), currentTemperature, currentAirHumidity, currentCO2, currentMethane);
  return String(buf);
}

// ══════════════════════════════════════════════════════════════════════════
//  SPIFFS
// ══════════════════════════════════════════════════════════════════════════

void saveReadingToSPIFFS() {
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

  // Fallback anti-glitch: usar último valor válido si el actual es 0
  float hToSave = (currentAirHumidity > 0.0f) ? currentAirHumidity
                : (gotValidHumidity   ? lastValidHumidity : 0.0f);
  float mToSave = (currentMethane     > 0.0f) ? currentMethane
                : (gotValidMethane    ? lastValidMethane  : 0.0f);

  if (hToSave != currentAirHumidity)
    Serial.printf("[SPIFFS] Humedad 0 detectada → usando fallback %.1f\n", hToSave);
  if (mToSave != currentMethane)
    Serial.printf("[SPIFFS] Metano 0 detectado  → usando fallback %.2f\n", mToSave);

  time_t now; time(&now);
  char line[96];
  snprintf(line, sizeof(line), "%ld,%.1f,%.1f,%.1f,%.2f\n",
    now, currentTemperature, hToSave, currentCO2, mToSave);
  File f = SPIFFS.open(HISTORY_FILE, "w");
  if (f) { f.print(existing); f.print(line); f.close();
    Serial.printf("[SPIFFS] Registro #%d\n", count + 1); }
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
  if (WiFi.status() != WL_CONNECTED) { if (!connectWiFi()) return; }

  // Fallback anti-glitch: usar último valor válido si el actual es 0
  float hToSend = (currentAirHumidity > 0.0f) ? currentAirHumidity
                : (gotValidHumidity   ? lastValidHumidity : 0.0f);
  float mToSend = (currentMethane     > 0.0f) ? currentMethane
                : (gotValidMethane    ? lastValidMethane  : 0.0f);

  if (hToSend != currentAirHumidity)
    Serial.printf("[WiFi] Humedad 0 detectada → usando fallback %.1f\n", hToSend);
  if (mToSend != currentMethane)
    Serial.printf("[WiFi] Metano 0 detectado  → usando fallback %.2f\n", mToSend);

  HTTPClient http; http.begin(backendUrl); http.addHeader("Content-Type", "application/json");
  char body[320];
  snprintf(body, sizeof(body),
    "{\"device_id\":\"%s\",\"api_key\":\"%s\",\"temperature\":%.1f,\"air_humidity\":%.1f,\"co2\":%.1f,\"methane\":%.2f}",
    deviceId.c_str(), apiKey.c_str(), currentTemperature, hToSend, currentCO2, mToSend);
  int code = http.POST(body);
  Serial.printf("[WiFi] POST %d\n", code);
  http.end();
}

void sendPumpAck() {
  if (apiKey.length() == 0 || WiFi.status() != WL_CONNECTED) return;
  HTTPClient http; http.begin(pumpAckUrl); http.addHeader("Content-Type", "application/json");
  char body[128];
  snprintf(body, sizeof(body), "{\"device_id\":\"%s\",\"api_key\":\"%s\"}", deviceId.c_str(), apiKey.c_str());
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

  // RS485 — solo recepción, DE queda LOW permanentemente
  pinMode(RS485_DE, OUTPUT);
  digitalWrite(RS485_DE, LOW);
  RS485Serial.begin(RS485_BAUD, SERIAL_8N1, RS485_RX, RS485_TX);
  Serial.println("[RS485] Iniciado en modo recepción (GPIO32/33/25)");

  if (!SPIFFS.begin(true)) Serial.println("[SPIFFS] Error");
  else Serial.println("[SPIFFS] OK");

  bool hasConfig = loadConfig();

  // Esperar primeros datos de los nodos (hasta 5 s)
  Serial.println("[RS485] Esperando tramas de nodos...");
  unsigned long waitStart = millis();
  while (millis() - waitStart < 5000) { readRS485(); delay(10); }

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

  Serial.println("=== AgroSense WROVER-B listo ===");
}

// ══════════════════════════════════════════════════════════════════════════
//  LOOP
// ══════════════════════════════════════════════════════════════════════════

void loop() {
  unsigned long now = millis();

  // Leer RS485 en cada iteración
  readRS485();

  // Advertencia si un nodo lleva más de 5 min sin responder
  if (lastNode1Rx > 0 && (now - lastNode1Rx) > 300000)
    Serial.printf("[RS485] AVISO: Nodo 01 silencioso hace %lu ms\n", now - lastNode1Rx);
  if (lastNode2Rx > 0 && (now - lastNode2Rx) > 300000)
    Serial.printf("[RS485] AVISO: Nodo 02 silencioso hace %lu ms\n", now - lastNode2Rx);

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
  if (now - lastSaveTime >= SAVE_INTERVAL) { lastSaveTime = now; saveReadingToSPIFFS(); }

  // AWS cada 30 s
  if (now - lastWifiTime >= WIFI_INTERVAL) { lastWifiTime = now; sendToAWS(); }

  // Bomba
  if (now - lastPumpCheckTime >= pumpCheckInterval) { lastPumpCheckTime = now; checkPumpSchedule(); }

  delay(20);
}
