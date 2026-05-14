/*
 * AgroSense — Nodo 02 (Arduino Nano) — MICS-6814 — LoRa
 * =======================================================
 * Rango: 0 – 10 000 ppm CH4  |  Protocolo PUSH cada 2 s
 * Formato trama: "$N:02,M:1234.56\n"
 *
 * LoRa Ra-02 (SX1278 433 MHz) — conexión Arduino Nano:
 *   D11 (MOSI) → MOSI      D10 (SS)  → NSS/CS
 *   D12 (MISO) → MISO      D9        → RST
 *   D13 (SCK)  → SCK       D2        → DIO0 (IRQ)
 *   3.3V       → VCC  ←  IMPORTANTE: Ra-02 es 3.3V, NO 5V
 *   GND        → GND
 *
 * MICS-6814:
 *   A0 (canal CO), A2 (canal NH3 → usado para CH4), 5V, GND
 *
 * Librería requerida: "LoRa" by Sandeep Mistry (Arduino Library Manager)
 *
 * Configuración LoRa: 433 MHz | SF7 | BW 125 kHz | CR 4/5 | SyncWord 0xA5
 * (debe coincidir exactamente con el maestro)
 *
 * CALIBRACIÓN (una sola vez en aire limpio):
 *  1. CALIBRATION_MODE true → cargar al Nano
 *  2. Serial Monitor 9600 → esperar resultado R0
 *  3. Pegar ese valor en R0_OHM aquí abajo
 *  4. CALIBRATION_MODE false → re-cargar
 *
 * NOTA AVR: Serial.printf() con %f NO existe en Nano.
 *           Todo float se convierte con dtostrf() antes de imprimir.
 *
 * FIXES v4 (heredados — sin cambios en lógica de sensor):
 *   - rsToPpm(): eliminado "if (ratio >= 1.0f) return 0" que enviaba CH4=0
 *     siempre que el sensor estaba en aire limpio o calentándose.
 */

#include <SPI.h>
#include <LoRa.h>
#include <math.h>

// ── Macro de debug compatible con AVR ─────────────────────────────────────
#define PDBG(label, val, dec) do { \
  char _tb[16]; dtostrf(val, 1, dec, _tb); \
  Serial.print(label); Serial.println(_tb); \
} while(0)

// ═══════════════════════════════════════════════════════════════════════════
//  CONFIGURACIÓN — EDITAR AQUÍ
// ═══════════════════════════════════════════════════════════════════════════

#define CALIBRATION_MODE    false  // true = medir R0, false = producción

// R0: resistencia del sensor en aire limpio.
// REEMPLAZAR con el valor que arroje el modo calibración.
#define R0_OHM  198776.0f

// Resistencia de carga del módulo (verificar esquemático — típico 10kΩ)
#define RL_OHM              10000.0f

#define VCC                 5.0f

// Curva de potencia datasheet MICS-6814, canal NH3, para CH4:
//   ppm = A * (Rs/R0)^B
#define CURVE_A             1000.0f
#define CURVE_B             -1.96f

#define CH4_MIN_PPM         0.0f
#define CH4_MAX_PPM         10000.0f

// Filtrado
#define SAMPLES             15        // mediana (mantener impar)
#define EMA_ALPHA           0.10f
#define BASELINE_ALPHA      0.001f
#define NOISE_FLOOR         30.0f     // cuentas ADC mín para reportar gas

// Pines MICS-6814
#define PIN_NH3             A2
#define PIN_CO              A0

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
#define SEND_INTERVAL       2000UL

// Anti-colisión: Nodo01=0–499ms | Nodo02=500–999ms
#define STARTUP_OFFSET_MIN  500
#define STARTUP_OFFSET_MAX  999

// Calentamiento: 3 min (datasheet). Poner 0 solo para pruebas de banco.
#define WARMUP_MS           180000UL

// ═══════════════════════════════════════════════════════════════════════════
//  VARIABLES GLOBALES
// ═══════════════════════════════════════════════════════════════════════════

float emaRs      = -1.0f;
float baselineRs = -1.0f;
unsigned long lastSendTime = 0;

// ═══════════════════════════════════════════════════════════════════════════
//  FUNCIONES AUXILIARES
// ═══════════════════════════════════════════════════════════════════════════

void loraTransmit(const char* msg) {
  LoRa.beginPacket();
  LoRa.print(msg);
  LoRa.endPacket();  // bloqueante ~50-70 ms a SF7/BW125 — seguro a 2 s de intervalo
}

// floatToStr — redondeo correcto en AVR (evita "0.00" falso) — FIX v2
void floatToStr(char* buf, float val) {
  if (isnan(val) || val < 0.0f) { strcpy(buf, "0.00"); return; }
  long entero = (long)val;
  int  frac   = (int)roundf((val - (float)entero) * 100.0f);
  if (frac >= 100) { entero++; frac = 0; }
  sprintf(buf, "%ld.%02d", entero, frac);
}

void sortArray(int* arr, int n) {
  for (int i = 1; i < n; i++) {
    int key = arr[i], j = i - 1;
    while (j >= 0 && arr[j] > key) { arr[j+1] = arr[j]; j--; }
    arr[j+1] = key;
  }
}

// ADC → Rs (Ω)
float adcToRs(int adc) {
  if (adc <= 0) adc = 1;
  float vout = ((float)adc / 1023.0f) * VCC;
  if (vout <= 0.0f) vout = 0.001f;
  return RL_OHM * ((VCC / vout) - 1.0f);
}

// Rs → ppm usando curva logarítmica del datasheet
// FIX v4: eliminado "if (ratio >= 1.0f) return CH4_MIN_PPM" — producía CH4=0
// siempre que el sensor estaba en reposo o calentándose (Rs > R0).
float rsToPpm(float rs) {
  if (rs <= 0.0f) return CH4_MAX_PPM;
  float ratio = rs / R0_OHM;
  float ppm   = CURVE_A * powf(ratio, CURVE_B);
  return constrain(ppm, CH4_MIN_PPM, CH4_MAX_PPM);
}

// ═══════════════════════════════════════════════════════════════════════════
//  LECTURA PRINCIPAL
// ═══════════════════════════════════════════════════════════════════════════

float readMethane() {
  int samplesNH3[SAMPLES], samplesCO[SAMPLES];

  for (int i = 0; i < SAMPLES; i++) {
    samplesNH3[i] = analogRead(PIN_NH3); delay(6);
    samplesCO[i]  = analogRead(PIN_CO);  delay(6);
  }

  sortArray(samplesNH3, SAMPLES);
  sortArray(samplesCO,  SAMPLES);
  int medNH3 = samplesNH3[SAMPLES / 2];
  int medCO  = samplesCO[SAMPLES / 2];

  float rsNow = adcToRs(medNH3);

  if (emaRs < 0.0f) { emaRs = rsNow; baselineRs = rsNow; }

  emaRs = EMA_ALPHA * rsNow + (1.0f - EMA_ALPHA) * emaRs;

  float baselineAdc = 1023.0f / (1.0f + (baselineRs / RL_OHM));
  float deltaAdc    = abs((float)medNH3 - baselineAdc);

  if (deltaAdc < NOISE_FLOOR) {
    baselineRs = BASELINE_ALPHA * emaRs + (1.0f - BASELINE_ALPHA) * baselineRs;
  }

  float ppm   = rsToPpm(emaRs);
  float ratio = emaRs / R0_OHM;

  char s1[12], s2[12], s3[12], s4[12], s5[12], s6[12];
  dtostrf(deltaAdc, 1, 1, s1);
  dtostrf(rsNow,    1, 0, s2);
  dtostrf(emaRs,    1, 0, s3);
  dtostrf(baselineRs, 1, 0, s4);
  dtostrf(ratio,    1, 4, s5);
  dtostrf(ppm,      1, 1, s6);

  Serial.print("[ADC]  NH3="); Serial.print(medNH3);
  Serial.print("  CO=");       Serial.print(medCO);
  Serial.print("  deltaADC="); Serial.println(s1);

  Serial.print("[Rs]   rsNow="); Serial.print(s2);
  Serial.print("  emaRs=");      Serial.print(s3);
  Serial.print("  base=");       Serial.print(s4);
  Serial.print("  R0=");         Serial.println(R0_OHM, 0);

  Serial.print("[PPM]  ratio="); Serial.print(s5);
  Serial.print("  CH4=");        Serial.print(s6);
  Serial.println(" ppm");
  Serial.println();

  return ppm;
}

// ═══════════════════════════════════════════════════════════════════════════
//  MODO CALIBRACIÓN
// ═══════════════════════════════════════════════════════════════════════════

void runCalibration() {
  Serial.println();
  Serial.println("==========================================");
  Serial.println("  MODO CALIBRACION - MICS-6814");
  Serial.println("  AIRE LIMPIO - lejos de gas o velas");
  Serial.println("==========================================");
  Serial.println("Calentando 3 minutos...");

  unsigned long t0 = millis();
  while (millis() - t0 < WARMUP_MS) {
    delay(30000);
    unsigned long elapsed = (millis() - t0) / 1000UL;
    unsigned long remain  = (WARMUP_MS / 1000UL) - elapsed;
    Serial.print("  Transcurrido: "); Serial.print(elapsed);
    Serial.print("s  Restante: ");    Serial.print(remain); Serial.println("s");
  }

  Serial.println("Promediando 60 lecturas (30s)...");
  long sumNH3 = 0;
  for (int i = 0; i < 60; i++) {
    sumNH3 += analogRead(PIN_NH3);
    delay(500);
    if ((i + 1) % 10 == 0) {
      Serial.print("  Muestra "); Serial.print(i + 1);
      Serial.print("/60 — ADC parcial: "); Serial.println(sumNH3 / (i + 1));
    }
  }

  float avgAdc     = sumNH3 / 60.0f;
  float r0measured = adcToRs((int)avgAdc);

  char sAdc[12], sR0[12];
  dtostrf(avgAdc,     1, 1, sAdc);
  dtostrf(r0measured, 1, 0, sR0);

  Serial.println();
  Serial.println("==========================================");
  Serial.println("  RESULTADO");
  Serial.print("  ADC promedio NH3 : "); Serial.println(sAdc);
  Serial.print("  R0 medido        : "); Serial.print(sR0); Serial.println(" ohm");
  Serial.println("------------------------------------------");
  Serial.println("  Copia esta linea en el sketch:");
  Serial.print("  #define R0_OHM  "); Serial.print(sR0); Serial.println("f");
  Serial.println("  Luego CALIBRATION_MODE false y re-quema");
  Serial.println("==========================================");

  while (true) delay(10000);
}

// ═══════════════════════════════════════════════════════════════════════════
//  SETUP
// ═══════════════════════════════════════════════════════════════════════════

void setup() {
  Serial.begin(9600);
  delay(300);
  Serial.println("[Nodo02] AgroSense MICS-6814 LoRa v1");
  Serial.print("[Nodo02] R0="); Serial.print(R0_OHM, 0);
  Serial.print(" ohm  Rango=0-"); Serial.print((int)CH4_MAX_PPM);
  Serial.println(" ppm");

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

  if (CALIBRATION_MODE) runCalibration();

  Serial.println("[Nodo02] Calentando sensor...");
  unsigned long t0 = millis();
  while (millis() - t0 < WARMUP_MS) {
    delay(30000);
    Serial.print("[Nodo02] Calentamiento: ");
    Serial.print((millis() - t0) / 1000UL);
    Serial.print("s / ");
    Serial.print(WARMUP_MS / 1000UL);
    Serial.println("s");
  }
  Serial.println("[Nodo02] Sensor listo");

  readMethane();
  Serial.print("[Nodo02] Baseline Rs inicial: ");
  Serial.print(baselineRs, 0); Serial.println(" ohm");

  randomSeed(analogRead(A1));
  unsigned long offset = random(STARTUP_OFFSET_MIN, STARTUP_OFFSET_MAX + 1);
  Serial.print("[Nodo02] Offset anti-colision: ");
  Serial.print(offset); Serial.println("ms");
  delay(offset);

  lastSendTime = millis();
  Serial.println("[Nodo02] Listo — enviando cada 2s por LoRa");
}

// ═══════════════════════════════════════════════════════════════════════════
//  LOOP
// ═══════════════════════════════════════════════════════════════════════════

void loop() {
  unsigned long now = millis();

  if (now - lastSendTime >= SEND_INTERVAL) {
    lastSendTime = now;

    float ppm = readMethane();

    char sM[10];
    floatToStr(sM, ppm);

    char trama[32];
    strcpy(trama, "$N:02,M:");
    strcat(trama, sM);
    strcat(trama, "\n");

    loraTransmit(trama);
    Serial.print("[TX] "); Serial.print(trama);
  }

  delay(10);
}
