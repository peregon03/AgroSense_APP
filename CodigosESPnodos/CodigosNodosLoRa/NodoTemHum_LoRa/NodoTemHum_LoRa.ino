/*
 * AgroSense — Nodo 01 (Arduino Nano) — SHT31 — LoRa
 * ====================================================
 * Protocolo PUSH: envía trama cada 2.5 s sin esperar petición del maestro.
 * Formato: "$N:01,T:28.50,H:63.20\n"
 *
 * LoRa Ra-02 (SX1278 433 MHz) — conexión Arduino Nano:
 *   D11 (MOSI) → MOSI      D10 (SS)  → NSS/CS
 *   D12 (MISO) → MISO      D9        → RST
 *   D13 (SCK)  → SCK       D2        → DIO0 (IRQ)
 *   3.3V       → VCC  ←  IMPORTANTE: Ra-02 es 3.3V, NO 5V
 *   GND        → GND
 *
 * SHT31:
 *   A4 (SDA), A5 (SCL), 3.3V, GND
 *
 * Librería requerida: "LoRa" by Sandeep Mistry (Arduino Library Manager)
 * Librería requerida: Adafruit SHT31, Adafruit Unified Sensor
 *
 * Configuración LoRa: 433 MHz | SF7 | BW 125 kHz | CR 4/5 | SyncWord 0xA5
 * (debe coincidir exactamente con el maestro)
 *
 * FIX v2 (heredado):
 *   - floatToStr() corregido: redondeo + carry para evitar "0.00" erróneo en AVR.
 *   - Offset aleatorio en startup para evitar colisión LoRa con Nodo 02.
 *
 * FIX v3 (heredado):
 *   - leerSHT31(): rechaza h < HUM_MIN_VALID (1.0%) — glitch I2C devuelve 0.0 sin NaN.
 *   - Reset I2C preventivo tras SHT31_MAX_FAILS fallos consecutivos.
 *
 * NOTA AVR: Serial.printf() con %f NO existe en Nano.
 *           Usar Serial.print(float, decimales) o dtostrf().
 */

#include <Wire.h>
#include <Adafruit_SHT31.h>
#include <SPI.h>
#include <LoRa.h>

// ── Pines LoRa Ra-02 (defaults de la librería para Arduino Nano) ───────────
#define LORA_NSS   10   // SS  — D10
#define LORA_RST    9   // RST — D9
#define LORA_DIO0   2   // IRQ — D2

#define LORA_FREQ   433E6
#define LORA_SF     7
#define LORA_BW     125E3
#define LORA_CR     5
#define LORA_SYNC   0xA5   // sync word privado AgroSense — igual al maestro

// ── Intervalos ────────────────────────────────────────────────────────────
#define SEND_INTERVAL 2500UL

// Nodo 01 usa offset 0–499 ms para no colisionar con Nodo 02 (500–999 ms)
#define STARTUP_OFFSET_MIN 0
#define STARTUP_OFFSET_MAX 499

// FIX v3: umbral mínimo de humedad válida
#define HUM_MIN_VALID   1.0f

// FIX v3: fallos consecutivos antes de reset del sensor
#define SHT31_MAX_FAILS 3

Adafruit_SHT31 sht31;

unsigned long lastSendTime = 0;
float lastTemp   = 0.0f;
float lastHum    = 0.0f;
int   sht31Fails = 0;

// ── Transmitir por LoRa ───────────────────────────────────────────────────
void loraTransmit(const char* msg) {
  LoRa.beginPacket();
  LoRa.print(msg);
  LoRa.endPacket();  // bloqueante ~50-70 ms a SF7/BW125 — seguro a 2.5 s de intervalo
}

// ── Float a string — versión corregida (FIX v2) ───────────────────────────
void floatToStr(char* buf, float val, int dec) {
  if (isnan(val) || val < -999.0f) { strcpy(buf, "0.00"); return; }

  bool neg = (val < 0.0f);
  if (neg) val = -val;

  long entero = (long)val;
  int  frac   = (int)roundf((val - (float)entero) * 100.0f);

  if (frac >= 100) { entero++; frac = 0; }

  if (neg)
    sprintf(buf, "-%ld.%02d", entero, frac);
  else
    sprintf(buf, "%ld.%02d", entero, frac);
}

// ── Leer SHT31 ────────────────────────────────────────────────────────────
bool leerSHT31() {
  float t = sht31.readTemperature();
  delay(20);
  float h = sht31.readHumidity();

  bool ok = !isnan(t) && !isnan(h)
            && t >= -40.0f  && t <= 125.0f
            && h >= HUM_MIN_VALID && h <= 100.0f;  // FIX v3

  if (!ok) {
    sht31Fails++;
    Serial.print("[SHT31] Lectura invalida — T=");
    Serial.print(t);
    Serial.print("  H=");
    Serial.print(h);
    Serial.print("  Fallos: ");
    Serial.println(sht31Fails);

    if (sht31Fails >= SHT31_MAX_FAILS) {  // FIX v3
      Serial.println("[SHT31] Reset por fallos consecutivos");
      sht31.reset();
      delay(500);
      sht31Fails = 0;
    }
    return false;
  }

  sht31Fails = 0;
  lastTemp   = t;
  lastHum    = h;
  return true;
}

// ── Setup ──────────────────────────────────────────────────────────────────
void setup() {
  Serial.begin(9600);
  Serial.println("[Nodo01] AgroSense SHT31 LoRa v1");

  // Inicializar LoRa
  LoRa.setPins(LORA_NSS, LORA_RST, LORA_DIO0);
  if (!LoRa.begin(LORA_FREQ)) {
    Serial.println("[LoRa] ERROR: modulo no encontrado. Verifica conexiones.");
    while (true) delay(1000);
  }
  LoRa.setSpreadingFactor(LORA_SF);
  LoRa.setSignalBandwidth(LORA_BW);
  LoRa.setCodingRate4(LORA_CR);
  LoRa.setSyncWord(LORA_SYNC);
  Serial.println("[LoRa] OK — 433 MHz SF7 BW125kHz SyncWord 0xA5");

  Wire.begin();
  if (!sht31.begin(0x44)) {
    Serial.println("[ERROR] SHT31 no detectado en 0x44");
    while (1) delay(500);
  }
  Serial.println("[SHT31] Sensor OK");

  delay(500);

  // Primera lectura — reintentar hasta obtener dato válido (máx 5 intentos)
  int intentos = 0;
  while (!leerSHT31() && intentos < 5) {
    intentos++;
    Serial.print("[SHT31] Reintento inicial: ");
    Serial.println(intentos);
    delay(300);
  }

  Serial.print("[SHT31] Primera lectura: T=");
  Serial.print(lastTemp, 2);
  Serial.print("  H=");
  Serial.println(lastHum, 2);

  // Offset anti-colisión LoRa: Nodo 01 espera 0–499 ms aleatorios
  randomSeed(analogRead(A1));
  unsigned long offset = random(STARTUP_OFFSET_MIN, STARTUP_OFFSET_MAX + 1);
  Serial.print("[Nodo01] Offset startup: ");
  Serial.print(offset);
  Serial.println(" ms");
  delay(offset);

  lastSendTime = millis();
  Serial.println("[Nodo01] Listo — enviando cada 2.5 s por LoRa");
}

// ── Loop ───────────────────────────────────────────────────────────────────
void loop() {
  unsigned long now = millis();

  if (now - lastSendTime >= SEND_INTERVAL) {
    lastSendTime = now;

    if (!leerSHT31()) {
      Serial.print("[SHT31] Usando ultimo valido: T=");
      Serial.print(lastTemp, 2);
      Serial.print("  H=");
      Serial.println(lastHum, 2);
    }

    char sT[10], sH[10];
    floatToStr(sT, lastTemp, 2);
    floatToStr(sH, lastHum,  2);

    // Trama: "$N:01,T:28.50,H:63.20\n"
    char trama[48];
    strcpy(trama, "$N:01,T:");
    strcat(trama, sT);
    strcat(trama, ",H:");
    strcat(trama, sH);
    strcat(trama, "\n");

    loraTransmit(trama);

    Serial.print("[TX] ");
    Serial.print(trama);
  }

  delay(10);
}
