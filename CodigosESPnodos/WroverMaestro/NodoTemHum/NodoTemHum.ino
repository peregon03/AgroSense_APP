/*
 * AgroSense — Nodo 01 (Arduino Nano) — SHT31
 * ============================================
 * Protocolo PUSH: envía trama cada 2 s sin esperar REQ del maestro.
 * Formato: "$N:01,T:28.50,H:63.20\n"
 *
 * Pines:
 *   SHT31  → A4 (SDA), A5 (SCL), 3.3V, GND
 *   MAX485 → D4 (RO/RX), D5 (DI/TX), D6 (DE+RE unidos), 5V, GND
 *
 * Librerías: Adafruit SHT31 Library, Adafruit Unified Sensor
 *
 * FIX v2:
 *   - floatToStr() corregido: redondeo antes de truncar + carry para
 *     evitar errores de punto flotante AVR que producían "0.00" erróneo.
 *   - Offset aleatorio en startup para evitar colisión RS485 con Nodo 02.
 *   - trim() local en campo H antes de toFloat() (ya cubre el maestro,
 *     pero aquí blindamos la trama en origen).
 *
 * FIX v3:
 *   - leerSHT31(): el SHT31 puede devolver h=0.0 sin devolver NaN en
 *     caso de glitch I2C o saturación del sensor. isnan(0.0)==false, así
 *     que pasaba todas las validaciones y se transmitía H:0.00 como dato
 *     real. Fix: rechazar h < HUM_MIN_VALID (1.0%) como inválido — en
 *     cualquier ambiente agrícola real la humedad nunca es 0%.
 *   - Reset I2C preventivo: si leerSHT31() falla SHT31_MAX_FAILS veces
 *     consecutivas, se llama sht31.reset() para desbloquear el bus I2C
 *     y reiniciar el sensor sin necesidad de cortar alimentación.
 */

#include <Wire.h>
#include <Adafruit_SHT31.h>
#include <SoftwareSerial.h>

#define RS485_RX_PIN  4
#define RS485_TX_PIN  5
#define RS485_DE_PIN  6
#define SEND_INTERVAL 2500

// Nodo 01 usa offset 0–499 ms para no colisionar con Nodo 02 (500–999 ms)
#define STARTUP_OFFSET_MIN 0
#define STARTUP_OFFSET_MAX 499

// FIX v3: umbral mínimo de humedad considerada válida.
// El SHT31 puede retornar 0.0 sin NaN en glitch I2C — se rechaza.
#define HUM_MIN_VALID   1.0f

// FIX v3: fallos consecutivos antes de hacer reset del sensor.
#define SHT31_MAX_FAILS 3

Adafruit_SHT31 sht31;
SoftwareSerial rs485Serial(RS485_RX_PIN, RS485_TX_PIN);

unsigned long lastSendTime = 0;
float lastTemp   = 0.0f;
float lastHum    = 0.0f;
int   sht31Fails = 0;    // contador de fallos consecutivos

// ── Transmitir por RS485 ──────────────────────────────────────────────────
void rs485Transmit(const char* msg) {
  digitalWrite(RS485_DE_PIN, HIGH);
  delayMicroseconds(100);
  rs485Serial.print(msg);
  rs485Serial.flush();
  delayMicroseconds(100);
  digitalWrite(RS485_DE_PIN, LOW);
}

// ── Float a string — versión corregida (FIX v2) ──────────────────────────
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

// ── Leer SHT31 ───────────────────────────────────────────────────────────
// FIX v3: rechaza h < HUM_MIN_VALID (glitch I2C devuelve 0.0 sin NaN).
//         Tras SHT31_MAX_FAILS fallos consecutivos hace reset del sensor.
bool leerSHT31() {
  float t = sht31.readTemperature();
  delay(20);
  float h = sht31.readHumidity();

  bool ok = !isnan(t) && !isnan(h)
            && t >= -40.0f  && t <= 125.0f
            && h >= HUM_MIN_VALID && h <= 100.0f;

  if (!ok) {
    sht31Fails++;
    Serial.print("[SHT31] Lectura invalida — T=");
    Serial.print(t);
    Serial.print("  H=");
    Serial.print(h);
    Serial.print("  Fallos: ");
    Serial.println(sht31Fails);

    if (sht31Fails >= SHT31_MAX_FAILS) {
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

// ── Setup ─────────────────────────────────────────────────────────────────
void setup() {
  Serial.begin(9600);
  Serial.println("[Nodo01] AgroSense SHT31 v3");

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

  // Offset anti-colisión RS485: Nodo 01 espera 0–499 ms aleatorios
  randomSeed(analogRead(A1));
  unsigned long offset = random(STARTUP_OFFSET_MIN, STARTUP_OFFSET_MAX + 1);
  Serial.print("[Nodo01] Offset startup: ");
  Serial.print(offset);
  Serial.println(" ms");
  delay(offset);

  lastSendTime = millis();
  Serial.println("[Nodo01] Listo — enviando cada 2.5 s");
}

// ── Loop ──────────────────────────────────────────────────────────────────
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

    rs485Transmit(trama);

    Serial.print("[TX] ");
    Serial.print(trama);
  }

  delay(10);
}
