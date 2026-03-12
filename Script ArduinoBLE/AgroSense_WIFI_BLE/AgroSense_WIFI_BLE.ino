/*
 * AgroSense - Arduino Nano ESP32 (ABX00092)
 * BLE Server + WiFi → Railway
 *
 * - Expone datos por BLE cada 2s (para la app Android)
 * - Envía los mismos datos a Railway cada 30s (para la BD)
 *
 * ANTES DE SUBIR configura:
 *   WIFI_SSID, WIFI_PASSWORD, API_KEY
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <WiFi.h>
#include <HTTPClient.h>

// ── Configuración WiFi ─────────────────────────────────────────────────────
#define WIFI_SSID       "Familia Perez"
#define WIFI_PASSWORD   "Perez2903+"

// ── Backend Railway ────────────────────────────────────────────────────────
#define BACKEND_URL     "https://agrosense-backend-production.up.railway.app/api/ingest"
#define API_KEY         "65471ab95aea7174aef8ba8e550abb64fdb6311c73241dc6"   // La api_key que generó la app al registrar el sensor

// ── UUIDs BLE ──────────────────────────────────────────────────────────────
#define SERVICE_UUID    "0000A001-0000-1000-8000-00805F9B34FB"
#define DEVICE_ID_UUID  "0000A002-0000-1000-8000-00805F9B34FB"
#define READINGS_UUID   "0000A003-0000-1000-8000-00805F9B34FB"

// ── Intervalos ─────────────────────────────────────────────────────────────
#define BLE_INTERVAL    2000    // Envío BLE cada 2 segundos
#define WIFI_INTERVAL   30000   // Envío a Railway cada 30 segundos

// ── Variables globales ─────────────────────────────────────────────────────
BLEServer*          pServer         = nullptr;
BLECharacteristic*  pDeviceIdChar   = nullptr;
BLECharacteristic*  pReadingsChar   = nullptr;
bool                bleConnected    = false;
unsigned long       lastBleTime     = 0;
unsigned long       lastWifiTime    = 0;
std::string         deviceId        = "";

// Últimas lecturas generadas (compartidas entre BLE y WiFi)
float currentTemperature  = 0;
float currentAirHumidity  = 0;
float currentSoilHumidity = 0;

// ── Callbacks BLE ──────────────────────────────────────────────────────────
class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) override {
    bleConnected = true;
    Serial.println("[BLE] Dispositivo conectado");
  }
  void onDisconnect(BLEServer* pServer) override {
    bleConnected = false;
    Serial.println("[BLE] Desconectado - reiniciando advertising...");
    delay(500);
    pServer->startAdvertising();
  }
};

// ── Generar datos simulados ────────────────────────────────────────────────
void generateReadings() {
  currentTemperature  = random(150, 380) / 10.0;  // 15.0 - 38.0 °C
  currentAirHumidity  = random(300, 950) / 10.0;  // 30.0 - 95.0 %
  currentSoilHumidity = random(200, 800) / 10.0;  // 20.0 - 80.0 %
}

// ── Construir JSON ─────────────────────────────────────────────────────────
std::string buildJson() {
  char buf[256];
  snprintf(buf, sizeof(buf),
    "{\"device_id\":\"%s\",\"temperature\":%.1f,\"air_humidity\":%.1f,\"soil_humidity\":%.1f}",
    deviceId.c_str(),
    currentTemperature,
    currentAirHumidity,
    currentSoilHumidity
  );
  return std::string(buf);
}

// ── Enviar a Railway por WiFi ──────────────────────────────────────────────
void sendToRailway() {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[WiFi] Sin conexion, intentando reconectar...");
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    int tries = 0;
    while (WiFi.status() != WL_CONNECTED && tries < 10) {
      delay(500);
      tries++;
    }
    if (WiFi.status() != WL_CONNECTED) {
      Serial.println("[WiFi] No se pudo reconectar");
      return;
    }
  }

  HTTPClient http;
  http.begin(BACKEND_URL);
  http.addHeader("Content-Type", "application/json");

  // JSON para el backend (incluye api_key para autenticación)
  char body[300];
  snprintf(body, sizeof(body),
    "{\"device_id\":\"%s\",\"api_key\":\"%s\",\"temperature\":%.1f,\"air_humidity\":%.1f,\"soil_humidity\":%.1f}",
    deviceId.c_str(),
    API_KEY,
    currentTemperature,
    currentAirHumidity,
    currentSoilHumidity
  );

  int httpCode = http.POST(body);

  if (httpCode == 201) {
    Serial.println("[WiFi] Datos enviados a Railway OK");
  } else {
    Serial.print("[WiFi] Error HTTP: ");
    Serial.print(httpCode);
    Serial.print(" - ");
    Serial.println(http.getString().c_str());
  }

  http.end();
}

// ── Setup ──────────────────────────────────────────────────────────────────
void setup() {
  Serial.begin(115200);
  randomSeed(analogRead(0));

  // ── WiFi ──
  Serial.print("[WiFi] Conectando a ");
  Serial.println(WIFI_SSID);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  int tries = 0;
  while (WiFi.status() != WL_CONNECTED && tries < 20) {
    delay(500);
    Serial.print(".");
    tries++;
  }
  if (WiFi.status() == WL_CONNECTED) {
    Serial.print("\n[WiFi] Conectado! IP: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("\n[WiFi] No se conecto - continuando solo con BLE");
  }

  // ── BLE ──
  BLEDevice::init("AgroSense");
  deviceId = BLEDevice::getAddress().toString();

  Serial.print("[BLE] Device ID (MAC): ");
  Serial.println(deviceId.c_str());

  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new ServerCallbacks());

  BLEService* pService = pServer->createService(SERVICE_UUID);

  pDeviceIdChar = pService->createCharacteristic(
    DEVICE_ID_UUID,
    BLECharacteristic::PROPERTY_READ
  );
  pDeviceIdChar->setValue(deviceId);

  pReadingsChar = pService->createCharacteristic(
    READINGS_UUID,
    BLECharacteristic::PROPERTY_NOTIFY
  );
  pReadingsChar->addDescriptor(new BLE2902());

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

  // Envío BLE cada 2 segundos (solo si hay cliente conectado)
  if (bleConnected && now - lastBleTime >= BLE_INTERVAL) {
    lastBleTime = now;
    generateReadings();

    std::string payload = buildJson();
    pReadingsChar->setValue(payload);
    pReadingsChar->notify();

    Serial.print("[BLE] Enviado: ");
    Serial.println(payload.c_str());
  }

  // Envío a Railway cada 30 segundos (siempre, con o sin BLE)
  if (now - lastWifiTime >= WIFI_INTERVAL) {
    lastWifiTime = now;
    if (!bleConnected) generateReadings(); // Generar si BLE no lo hizo
    sendToRailway();
  }
}
