/*
 * AgroSense — Nodo 03 (Arduino Nano) — Sensor Suelo RS485 — LoRa
 * Correcciones v2:
 *   - SENSOR_ADDR: 0x02 → 0x01  (confirmado por diagnóstico)
 *   - MODBUS_QUERY: CRC actualizado para addr 0x01 → {0x01,0x03,0x00,0x00,0x00,0x07,0x04,0x08}
 *   - DIO0 LoRa: D2 → D3  (evita conflicto INT0 con SoftwareSerial)
 *   - Log "9600 baud" corregido a "4800 baud"
 *   - LoRa.end() antes de querySensor() para liberar SPI durante lectura RS485
 *   - Timeout RS485 aumentado a 500 ms (más margen a 4800 baud)
 */

#include <SoftwareSerial.h>
#include <SPI.h>
#include <LoRa.h>

// ── Pines RS485 ───────────────────────────────────────────────────────────────
#define RS485_RX_PIN   8
#define RS485_TX_PIN   7
#define RS485_DE_RE    6

// ── Pines LoRa Ra-02 ──────────────────────────────────────────────────────────
#define LORA_NSS   10
#define LORA_RST    9
#define LORA_DIO0   3   // D3, no D2 — evita conflicto INT0 con SoftwareSerial

// ── Config LoRa ───────────────────────────────────────────────────────────────
#define LORA_FREQ   433E6
#define LORA_SF     7
#define LORA_BW     125E3
#define LORA_CR     5
#define LORA_SYNC   0xA5

// ── Intervalos ────────────────────────────────────────────────────────────────
#define SEND_INTERVAL   5000UL
#define RS485_TIMEOUT   500UL   // aumentado de 300 a 500 ms

// ── Anti-colisión ─────────────────────────────────────────────────────────────
#define STARTUP_OFFSET_MIN  1000
#define STARTUP_OFFSET_MAX  1499

// ── Dirección Modbus CORREGIDA ────────────────────────────────────────────────
#define SENSOR_ADDR  0x01   // ← era 0x02, diagnóstico confirmó 0x01

// ── Query Modbus: addr=0x01, func=0x03, reg=0x0000, count=7
//    CRC para esta trama: 0x04 0x08  (little-endian)
//    (era 0xC5 0xCB que correspondía a addr=0x02 — CRC incorrecto)
static const uint8_t MODBUS_QUERY[] = {
  0x01, 0x03, 0x00, 0x00, 0x00, 0x07, 0x04, 0x08
};
#define MODBUS_QUERY_LEN  8
#define MODBUS_RESP_LEN  19

SoftwareSerial rs485(RS485_RX_PIN, RS485_TX_PIN);

// ── Últimas lecturas válidas ──────────────────────────────────────────────────
float lastTemp  = 0.0f;
float lastHum   = 0.0f;
int   lastEC    = 0;
float lastPH    = 0.0f;
int   lastN     = 0;
int   lastP     = 0;
int   lastK     = 0;
bool  gotValidReading = false;

unsigned long lastSendTime = 0;

// ══════════════════════════════════════════════════════════════════════════════
// CRC16 Modbus
// ══════════════════════════════════════════════════════════════════════════════
uint16_t crc16Modbus(const uint8_t* buf, uint8_t len) {
  uint16_t crc = 0xFFFF;
  for (uint8_t i = 0; i < len; i++) {
    crc ^= (uint16_t)buf[i];
    for (uint8_t j = 0; j < 8; j++) {
      if (crc & 0x0001) { crc >>= 1; crc ^= 0xA001; }
      else               { crc >>= 1; }
    }
  }
  return crc;
}

// ══════════════════════════════════════════════════════════════════════════════
// Inicializar LoRa
// ══════════════════════════════════════════════════════════════════════════════
void loraInit() {
  LoRa.setPins(LORA_NSS, LORA_RST, LORA_DIO0);
  LoRa.begin(LORA_FREQ);
  LoRa.setSpreadingFactor(LORA_SF);
  LoRa.setSignalBandwidth(LORA_BW);
  LoRa.setCodingRate4(LORA_CR);
  LoRa.setSyncWord(LORA_SYNC);
}

// ══════════════════════════════════════════════════════════════════════════════
// Consultar sensor RS485
// LoRa se apaga antes y se reactiva después para evitar conflicto SPI/interrupciones
// ══════════════════════════════════════════════════════════════════════════════
bool querySensor() {
  // Apagar LoRa mientras usamos SoftwareSerial — libera interrupciones
  LoRa.end();
  delay(10);

  // Flush buffer de entrada
  while (rs485.available()) rs485.read();

  // Modo transmisión
  digitalWrite(RS485_DE_RE, HIGH);
  delay(1);  // margen antes de transmitir
  rs485.write(MODBUS_QUERY, MODBUS_QUERY_LEN);
  rs485.flush();
  delay(5);  // margen después de transmitir — más robusto que delayMicroseconds

  // Modo recepción
  digitalWrite(RS485_DE_RE, LOW);

  // Leer respuesta
  uint8_t resp[MODBUS_RESP_LEN];
  uint8_t idx = 0;
  unsigned long t0 = millis();

  while (millis() - t0 < RS485_TIMEOUT) {
    if (rs485.available()) {
      resp[idx++] = rs485.read();
      if (idx >= MODBUS_RESP_LEN) break;
    }
  }

  // Reiniciar LoRa antes de retornar
  loraInit();
  delay(10);

  if (idx < MODBUS_RESP_LEN) {
    Serial.print(F("[RS485] Timeout o trama corta: "));
    Serial.print(idx);
    Serial.println(F(" bytes"));
    return false;
  }

  // Verificar encabezado
  if (resp[0] != SENSOR_ADDR || resp[1] != 0x03 || resp[2] != 0x0E) {
    Serial.print(F("[RS485] Encabezado incorrecto: "));
    Serial.print(resp[0], HEX); Serial.print(' ');
    Serial.print(resp[1], HEX); Serial.print(' ');
    Serial.println(resp[2], HEX);
    return false;
  }

  // Verificar CRC
  uint16_t crcCalc = crc16Modbus(resp, MODBUS_RESP_LEN - 2);
  uint16_t crcRcvd = (uint16_t)resp[MODBUS_RESP_LEN - 1] << 8 |
                     (uint16_t)resp[MODBUS_RESP_LEN - 2];
  if (crcCalc != crcRcvd) {
    Serial.print(F("[RS485] CRC error. Calc=0x"));
    Serial.print(crcCalc, HEX);
    Serial.print(F(" Rcvd=0x"));
    Serial.println(crcRcvd, HEX);
    return false;
  }

  // Parsear registros (confirmados por diagnóstico)
  uint16_t rawHum  = (uint16_t)resp[3]  << 8 | resp[4];
  uint16_t rawTemp = (uint16_t)resp[5]  << 8 | resp[6];
  uint16_t rawEC   = (uint16_t)resp[7]  << 8 | resp[8];
  uint16_t rawPH   = (uint16_t)resp[9]  << 8 | resp[10];
  uint16_t rawN    = (uint16_t)resp[11] << 8 | resp[12];
  uint16_t rawP    = (uint16_t)resp[13] << 8 | resp[14];
  uint16_t rawK    = (uint16_t)resp[15] << 8 | resp[16];

  float t  = rawTemp / 10.0f;
  float h  = rawHum  / 10.0f;
  float ph = rawPH   / 10.0f;

  // Validación de rangos
  if (t < -20.0f || t > 80.0f)  { Serial.println(F("[RS485] Temp fuera de rango"));    return false; }
  if (h <   0.0f || h > 100.0f) { Serial.println(F("[RS485] Humedad fuera de rango")); return false; }
  if (ph < 3.0f  || ph > 9.0f)  { Serial.println(F("[RS485] pH fuera de rango"));      return false; }

  lastTemp = t;
  lastHum  = h;
  lastEC   = (int)rawEC;
  lastPH   = ph;
  lastN    = (int)rawN;
  lastP    = (int)rawP;
  lastK    = (int)rawK;
  gotValidReading = true;
  return true;
}

// ══════════════════════════════════════════════════════════════════════════════
// Float a string (AVR no tiene printf %f)
// ══════════════════════════════════════════════════════════════════════════════
void fToStr(char* buf, float val, int dec) {
  if (isnan(val)) { strcpy(buf, "0.00"); return; }
  dtostrf(val, 1, dec, buf);
}

// ══════════════════════════════════════════════════════════════════════════════
// Transmitir por LoRa
// ══════════════════════════════════════════════════════════════════════════════
void loraTransmit(const char* msg) {
  LoRa.beginPacket();
  LoRa.print(msg);
  LoRa.endPacket();
}

// ══════════════════════════════════════════════════════════════════════════════
// SETUP
// ══════════════════════════════════════════════════════════════════════════════
void setup() {
  Serial.begin(9600);
  Serial.println(F("[Nodo03] AgroSense Suelo RS485 LoRa v2"));

  pinMode(RS485_DE_RE, OUTPUT);
  digitalWrite(RS485_DE_RE, LOW);

  rs485.begin(4800);
  Serial.println(F("[RS485] SoftwareSerial OK — 4800 baud, addr 0x01"));

  // Inicializar LoRa
  LoRa.setPins(LORA_NSS, LORA_RST, LORA_DIO0);
  if (!LoRa.begin(LORA_FREQ)) {
    Serial.println(F("[LoRa] ERROR: modulo no encontrado."));
    while (true) delay(1000);
  }
  LoRa.setSpreadingFactor(LORA_SF);
  LoRa.setSignalBandwidth(LORA_BW);
  LoRa.setCodingRate4(LORA_CR);
  LoRa.setSyncWord(LORA_SYNC);
  Serial.println(F("[LoRa] OK — 433 MHz SF7 BW125kHz SyncWord 0xA5"));

  delay(1000);  // sensor warmup

  // Primera lectura con reintentos
  int intentos = 0;
  while (!querySensor() && intentos < 5) {
    intentos++;
    Serial.print(F("[RS485] Reintento: "));
    Serial.println(intentos);
    delay(500);
  }

  if (gotValidReading) {
    Serial.print(F("[Suelo] OK T="));   Serial.print(lastTemp, 1);
    Serial.print(F(" H="));             Serial.print(lastHum, 1);
    Serial.print(F(" EC="));            Serial.print(lastEC);
    Serial.print(F(" pH="));            Serial.print(lastPH, 1);
    Serial.print(F(" N="));             Serial.print(lastN);
    Serial.print(F(" P="));             Serial.print(lastP);
    Serial.print(F(" K="));             Serial.println(lastK);
  } else {
    Serial.println(F("[RS485] Sin respuesta. Verifica cableado y alimentacion."));
  }

  // Offset anti-colisión
  randomSeed(analogRead(A1));
  unsigned long offset = random(STARTUP_OFFSET_MIN, STARTUP_OFFSET_MAX + 1);
  Serial.print(F("[Nodo03] Offset startup: "));
  Serial.print(offset);
  Serial.println(F(" ms"));
  delay(offset);

  lastSendTime = millis();
  Serial.println(F("[Nodo03] Listo — enviando cada 5 s"));
}

// ══════════════════════════════════════════════════════════════════════════════
// LOOP
// ══════════════════════════════════════════════════════════════════════════════
void loop() {
  unsigned long now = millis();

  if (now - lastSendTime >= SEND_INTERVAL) {
    lastSendTime = now;

    bool ok = querySensor();
    if (!ok) {
      if (gotValidReading) {
        Serial.println(F("[RS485] Fallo — usando ultimo valor valido"));
      } else {
        Serial.println(F("[RS485] Sin datos validos — omitiendo TX"));
        return;
      }
    }

    char sT[8], sH[8], sPH[6], tmp[8];
    fToStr(sT,  lastTemp, 2);
    fToStr(sH,  lastHum,  2);
    fToStr(sPH, lastPH,   2);

    char trama[80];
    strcpy(trama, "$N:03,T:");  strcat(trama, sT);
    strcat(trama, ",H:");       strcat(trama, sH);
    strcat(trama, ",EC:");      itoa(lastEC, tmp, 10); strcat(trama, tmp);
    strcat(trama, ",PH:");      strcat(trama, sPH);
    strcat(trama, ",N:");       itoa(lastN,  tmp, 10); strcat(trama, tmp);
    strcat(trama, ",P:");       itoa(lastP,  tmp, 10); strcat(trama, tmp);
    strcat(trama, ",K:");       itoa(lastK,  tmp, 10); strcat(trama, tmp);
    strcat(trama, "\n");

    loraTransmit(trama);
    Serial.print(F("[TX] "));
    Serial.print(trama);
  }

  delay(10);
}