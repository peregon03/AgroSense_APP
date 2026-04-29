/*
 * AgroSense — Nodo 02 (Arduino Nano) — MICS-6814
 * ================================================
 * Protocolo PUSH: envía trama cada 2 s sin esperar REQ del maestro.
 * Formato: "$N:02,M:3.47\n" ''
 *
 * Pines:
 *   MICS6814 → A0 (CO), A2 (NH3), 5V, GND
 *   MAX485   → D4 (RO/RX), D5 (DI/TX), D6 (DE+RE unidos), 5V, GND
 *
 * Nota: MICS-6814 necesita ~3 min de calentamiento para lecturas estables.
 *       WARMUP_MS = 0 para pruebas de banco, 180000 para producción.
 */

#include <SoftwareSerial.h>

#define RS485_RX_PIN  4
#define RS485_TX_PIN  5
#define RS485_DE_PIN  6

#define PIN_CO        A0
#define PIN_NH3       A2

#define SEND_INTERVAL 2000
#define SAMPLES       8        // promedio de lecturas por ciclo
#define CH4_MAX       20.0f    // ppm máximo representado
#define WARMUP_MS     0UL      // ← cambiar a 180000UL en producción

SoftwareSerial rs485Serial(RS485_RX_PIN, RS485_TX_PIN);

unsigned long lastSendTime = 0;

// ── Transmitir por RS485 ──────────────────────────────────────────────────
void rs485Transmit(const char* msg) {
  digitalWrite(RS485_DE_PIN, HIGH);
  delayMicroseconds(100);
  rs485Serial.print(msg);
  rs485Serial.flush();
  delayMicroseconds(100);
  digitalWrite(RS485_DE_PIN, LOW);
}

// ── Float a string sin snprintf float ────────────────────────────────────
void floatToStr(char* buf, float val, int dec) {
  if (isnan(val)) { strcpy(buf, "0.00"); return; }
  int entero = (int)val;
  int frac   = abs((int)((val - entero) * 100));
  sprintf(buf, "%d.%02d", entero, frac);
}

// ── Lectura MICS-6814 (promedio N muestras) ───────────────────────────────
float readMethane() {
  long sumCO = 0, sumNH3 = 0;
  for (int i = 0; i < SAMPLES; i++) {
    sumCO  += analogRead(PIN_CO);
    sumNH3 += analogRead(PIN_NH3);
    delay(5);
  }
  float co  = sumCO  / (float)SAMPLES;
  float nh3 = sumNH3 / (float)SAMPLES;

  float idx = (co * 0.3f + nh3 * 0.7f) / 1023.0f;
  float ppm = idx * CH4_MAX;
  return constrain(ppm, 0.0f, CH4_MAX);
}

// ── Setup ─────────────────────────────────────────────────────────────────
void setup() {
  Serial.begin(9600);
  Serial.println("[Nodo02] Iniciando MICS-6814...");

  pinMode(RS485_DE_PIN, OUTPUT);
  digitalWrite(RS485_DE_PIN, LOW);
  rs485Serial.begin(9600);

  if (WARMUP_MS > 0) {
    Serial.print("[Nodo02] Calentando sensor");
    unsigned long t0 = millis();
    while (millis() - t0 < WARMUP_MS) {
      delay(10000);
      Serial.print('.');
    }
    Serial.println();
  }

  Serial.print("[Nodo02] ADC inicial: CO=");
  Serial.print(analogRead(PIN_CO));
  Serial.print(" NH3=");
  Serial.println(analogRead(PIN_NH3));

  Serial.println("[Nodo02] Listo — enviando cada 2 s");
}

// ── Loop ──────────────────────────────────────────────────────────────────
void loop() {
  unsigned long now = millis();

  if (now - lastSendTime >= SEND_INTERVAL) {
    lastSendTime = now;

    float ppm = readMethane();

    char sM[8];
    floatToStr(sM, ppm, 2);

    // Trama: "$N:02,M:3.47\n"
    char trama[32];
    strcpy(trama, "$N:02,M:");
    strcat(trama, sM);
    strcat(trama, "\n");

    rs485Transmit(trama);

    Serial.print("[TX] ");
    Serial.print(trama);

    Serial.print("[ADC] CO=");
    Serial.print(analogRead(PIN_CO));
    Serial.print(" NH3=");
    Serial.println(analogRead(PIN_NH3));
  }

  delay(10);
}
