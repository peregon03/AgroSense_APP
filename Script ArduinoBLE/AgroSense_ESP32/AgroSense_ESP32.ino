/*
 * AgroSense - Arduino Nano ESP32 (ABX00092)
 * BLE Server con datos simulados
 * Variables: Humedad suelo, Temperatura, Humedad ambiente
 *
 * El device_id es la MAC del ESP32 — así coincide con lo que
 * la app Android registra al vincularlo por BLE.
 *
 * UUIDs sincronizados con BleManager.kt
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// ── UUIDs ──────────────────────────────────────────────────────────────────
#define SERVICE_UUID    "0000A001-0000-1000-8000-00805F9B34FB"
#define DEVICE_ID_UUID  "0000A002-0000-1000-8000-00805F9B34FB"
#define READINGS_UUID   "0000A003-0000-1000-8000-00805F9B34FB"

// ── Intervalo de envío (ms) ────────────────────────────────────────────────
#define SEND_INTERVAL   2000

// ── Variables globales ─────────────────────────────────────────────────────
BLEServer*          pServer         = nullptr;
BLECharacteristic*  pDeviceIdChar   = nullptr;
BLECharacteristic*  pReadingsChar   = nullptr;
bool                deviceConnected = false;
unsigned long       lastSendTime    = 0;
std::string         deviceId        = "";   // Se llena con la MAC en setup()

// ── Callbacks ──────────────────────────────────────────────────────────────
class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) override {
    deviceConnected = true;
    Serial.println("Dispositivo conectado");
  }
  void onDisconnect(BLEServer* pServer) override {
    deviceConnected = false;
    Serial.println("Dispositivo desconectado - reiniciando advertising...");
    delay(500);
    pServer->startAdvertising();
  }
};

// ── Setup ──────────────────────────────────────────────────────────────────
void setup() {
  Serial.begin(115200);
  randomSeed(analogRead(0));

  // Inicializar BLE primero para poder leer la MAC
  BLEDevice::init("AgroSense");

  // Obtener MAC como device_id (std::string nativo)
  deviceId = BLEDevice::getAddress().toString();

  Serial.println("=== AgroSense BLE Server ===");
  Serial.print("Device ID (MAC): ");
  Serial.println(deviceId.c_str());

  // Crear servidor
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new ServerCallbacks());

  // Crear servicio
  BLEService* pService = pServer->createService(SERVICE_UUID);

  // Característica: Device ID (lectura)
  pDeviceIdChar = pService->createCharacteristic(
    DEVICE_ID_UUID,
    BLECharacteristic::PROPERTY_READ
  );
  pDeviceIdChar->setValue(deviceId);  // std::string directo, sin conversión

  // Característica: Lecturas (notificación)
  pReadingsChar = pService->createCharacteristic(
    READINGS_UUID,
    BLECharacteristic::PROPERTY_NOTIFY
  );
  pReadingsChar->addDescriptor(new BLE2902());

  // Iniciar servicio
  pService->start();

  // Advertising
  BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();

  Serial.println("BLE activo - esperando conexion...");
}

// ── Construir JSON con datos simulados ─────────────────────────────────────
std::string buildReadingsJson() {
  float soilHumidity = random(200, 800) / 10.0;  // 20.0 - 80.0 %
  float temperature  = random(150, 380) / 10.0;  // 15.0 - 38.0 C
  float airHumidity  = random(300, 950) / 10.0;  // 30.0 - 95.0 %

  char buf[200];
  snprintf(buf, sizeof(buf),
    "{\"device_id\":\"%s\",\"soil_humidity\":%.1f,\"temperature\":%.1f,\"air_humidity\":%.1f}",
    deviceId.c_str(),
    soilHumidity,
    temperature,
    airHumidity
  );

  return std::string(buf);
}

// ── Loop ───────────────────────────────────────────────────────────────────
void loop() {
  if (deviceConnected) {
    unsigned long now = millis();
    if (now - lastSendTime >= SEND_INTERVAL) {
      lastSendTime = now;

      std::string payload = buildReadingsJson();
      pReadingsChar->setValue(payload);
      pReadingsChar->notify();

      Serial.print("Enviado: ");
      Serial.println(payload.c_str());
    }
  }
}
