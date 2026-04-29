/*
 * AgroSense — Nodo Sensor (Arduino Nano)
 * ========================================
 * Sensor : SHT31 (temperatura + humedad) vía I²C
 * Salida : RS485 via módulo TTL→RS485
 *
 * Pines:
 *   SHT31  → A4 (SDA), A5 (SCL), 3.3V, GND
 *   RS485  → D4 (RX soft), D5 (TX soft), D6 (DE/RE), 5V, GND
 *
 * Protocolo: trama ASCII cada 2 s
 *   "$T:23.50,H:61.20\n"
 *
 * Librerías necesarias (Tools > Manage Libraries):
 *   - Adafruit SHT31 Library  (Adafruit)
 *   - Adafruit Unified Sensor (Adafruit)
 *   - SoftwareSerial          (incluida en el IDE)
 */

#include <Wire.h>
#include <Adafruit_SHT31.h>
#include <SoftwareSerial.h>

// ── Pines RS485 ─────────────────────────────────────────────────────────────
#define RS485_RX_PIN   4   // Recepción  (RO del módulo)
#define RS485_TX_PIN   5   // Transmisión (DI del módulo)
#define RS485_DE_PIN   6   // DE + RE unidos — HIGH = TX, LOW = RX

// ── Intervalo de envío ───────────────────────────────────────────────────────
#define SEND_INTERVAL  2000   // ms

// ── Instancias ────────────────────────────────────────────────────────────────
Adafruit_SHT31  sht31;
SoftwareSerial  rs485Serial(RS485_RX_PIN, RS485_TX_PIN);

unsigned long lastSendTime = 0;
float lastTemp = 0.0;
float lastHum  = 0.0;

// ── Helpers RS485 ────────────────────────────────────────────────────────────
void rs485Transmit(const char* msg) {
  digitalWrite(RS485_DE_PIN, HIGH);   // Modo TX
  delayMicroseconds(100);
  rs485Serial.print(msg);
  rs485Serial.flush();
  delayMicroseconds(100);
  digitalWrite(RS485_DE_PIN, LOW);    // Volver a modo RX
}

// ── Setup ────────────────────────────────────────────────────────────────────
void setup() {
  Serial.begin(9600);
  Serial.println("[NANO] Iniciando AgroSense Nodo Sensor...");

  // RS485
  pinMode(RS485_DE_PIN, OUTPUT);
  digitalWrite(RS485_DE_PIN, LOW);    // RX por defecto
  rs485Serial.begin(9600);
  Serial.println("[RS485] SoftwareSerial iniciado (D4=RX, D5=TX, D6=DE)");

  // SHT31
  Wire.begin();
  if (!sht31.begin(0x44)) {           // Dirección I2C por defecto del SHT31
    Serial.println("[SHT31] ERROR: sensor no encontrado en 0x44");
    // Parpadeo infinito si el sensor no responde
    while (true) {
      delay(500);
    }
  }
  Serial.println("[SHT31] Sensor OK");

  // Primera lectura para estabilizar
  delay(500);
  leerSHT31();
  Serial.print("[SHT31] Primera lectura: T=");
  Serial.print(lastTemp, 2);
  Serial.print(" C  H=");
  Serial.print(lastHum, 2);
  Serial.println(" %");
}

// ── Convertir float a string sin snprintf float (AVR no lo soporta) ──────────
void floatToStr(char* buf, float val, int decimales) {
  if (isnan(val)) { strcpy(buf, "0.00"); return; }
  int entero = (int)val;
  int frac   = abs((int)((val - entero) * 100));
  char tmp[12];
  sprintf(tmp, "%d.%02d", entero, frac);
  strcpy(buf, tmp);
}

// ── Leer SHT31 de forma robusta en AVR ───────────────────────────────────────
bool leerSHT31() {
  float t = sht31.readTemperature();
  delay(20);
  float h = sht31.readHumidity();

  if (isnan(t) || isnan(h)) return false;
  if (t < -40.0 || t > 125.0)  return false;
  if (h < 0.0   || h > 100.0)  return false;

  lastTemp = t;
  lastHum  = h;
  return true;
}

// ── Loop ─────────────────────────────────────────────────────────────────────
void loop() {
  unsigned long now = millis();

  if (now - lastSendTime >= SEND_INTERVAL) {
    lastSendTime = now;

    if (!leerSHT31()) {
      Serial.println("[SHT31] Error de lectura — usando ultimo valor");
    }

    // Construir trama sin snprintf float: "$T:29.03,H:60.64\n"
    char sT[8], sH[8];
    floatToStr(sT, lastTemp, 2);
    floatToStr(sH, lastHum,  2);

    char trama[40];
    strcpy(trama, "$T:");
    strcat(trama, sT);
    strcat(trama, ",H:");
    strcat(trama, sH);
    strcat(trama, "\n");

    rs485Transmit(trama);

    Serial.print("[TX RS485] ");
    Serial.print(trama);
  }

  delay(10);
}
