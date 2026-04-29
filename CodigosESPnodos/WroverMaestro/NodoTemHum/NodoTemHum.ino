/*
 * AgroSense — Nodo 01 (Arduino Nano) — SHT31
 * ============================================
 * Protocolo PUSH: envía trama cada 2 s sin esperar REQ del maestro.
 * Formato: "$N:01,T:28.50,H:63.20\n"
 *
 * Pines:
 *   SHT31  → A4 (SDA), A5 (SCL), 3.3V, GND ...
 *   MAX485 → D4 (RO/RX), D5 (DI/TX), D6 (DE+RE unidos), 5V, GND
 *
 * Librerías: Adafruit SHT31 Library, Adafruit Unified Sensor
 */

#include <Wire.h>
#include <Adafruit_SHT31.h>
#include <SoftwareSerial.h>

#define RS485_RX_PIN  4
#define RS485_TX_PIN  5
#define RS485_DE_PIN  6
#define SEND_INTERVAL 2000

Adafruit_SHT31 sht31;
SoftwareSerial rs485Serial(RS485_RX_PIN, RS485_TX_PIN);

unsigned long lastSendTime = 0;
float lastTemp = 0.0;
float lastHum  = 0.0;

// ── Transmitir por RS485 ──────────────────────────────────────────────────
void rs485Transmit(const char* msg) {
  digitalWrite(RS485_DE_PIN, HIGH);
  delayMicroseconds(100);
  rs485Serial.print(msg);
  rs485Serial.flush();
  delayMicroseconds(100);
  digitalWrite(RS485_DE_PIN, LOW);
}

// ── Float a string sin snprintf float (AVR no lo soporta por defecto) ────
void floatToStr(char* buf, float val, int dec) {
  if (isnan(val)) { strcpy(buf, "0.00"); return; }
  int entero = (int)val;
  int frac   = abs((int)((val - entero) * 100));
  sprintf(buf, "%d.%02d", entero, frac);
}

// ── Leer SHT31 con delay entre T y H (más estable en AVR) ────────────────
bool leerSHT31() {
  float t = sht31.readTemperature();
  delay(20);
  float h = sht31.readHumidity();
  if (isnan(t) || isnan(h))          return false;
  if (t < -40.0 || t > 125.0)        return false;
  if (h <   0.0 || h > 100.0)        return false;
  lastTemp = t;
  lastHum  = h;
  return true;
}

// ── Setup ─────────────────────────────────────────────────────────────────
void setup() {
  Serial.begin(9600);
  Serial.println("[Nodo01] Iniciando...");

  pinMode(RS485_DE_PIN, OUTPUT);
  digitalWrite(RS485_DE_PIN, LOW);
  rs485Serial.begin(9600);

  Wire.begin();
  if (!sht31.begin(0x44)) {
    Serial.println("[ERROR] SHT31 no detectado en 0x44");
    while (1) delay(500);
  }
  Serial.println("[SHT31] Sensor OK");

  delay(500);
  leerSHT31();
  Serial.print("[SHT31] Primera lectura: T=");
  Serial.print(lastTemp, 2);
  Serial.print(" H=");
  Serial.println(lastHum, 2);
}

// ── Loop ──────────────────────────────────────────────────────────────────
void loop() {
  unsigned long now = millis();

  if (now - lastSendTime >= SEND_INTERVAL) {
    lastSendTime = now;

    if (!leerSHT31()) {
      Serial.println("[SHT31] Error — usando ultimo valor");
    }

    char sT[8], sH[8];
    floatToStr(sT, lastTemp, 2);
    floatToStr(sH, lastHum,  2);

    // Trama: "$N:01,T:28.50,H:63.20\n"
    char trama[48];
    strcpy(trama, "$N:01,T:");
    strcat(trama, sT);
    strcat(trama, ",H:");
    strcat(trama, sH);
    strcat(trama, "\n");

    rs485Transmit(trama);

    Serial.print("[TX] ");
    Serial.print(trama);
  }

  delay(10);
}
