/*
 * AgroSense — Diagnóstico RS485 / Modbus
 * =======================================
 * Sube este sketch SOLO para diagnosticar.
 * Abre el Monitor Serial a 9600 baud.
 *
 * Qué hace:
 *   1. Prueba los 4 baud rates más comunes del sensor (2400/4800/9600/19200)
 *   2. Prueba las 4 direcciones Modbus más comunes (0x01/0x02/0xFE/0xFF)
 *   3. Imprime los bytes RAW de cada respuesta para que puedas ver qué llega
 *   4. Aumenta el timeout a 1000 ms para descartar problemas de timing
 *
 * Conexiones (igual que el nodo):
 *   D8 → RO   D7 → DI   D6 → DE+RE
 */

#include <SoftwareSerial.h>

#define RS485_RX  8
#define RS485_TX  7
#define RS485_DE  6

SoftwareSerial rs485(RS485_RX, RS485_TX);

const uint32_t BAUD_RATES[] = {9600, 4800, 19200, 2400};
const uint8_t  ADDRESSES[]  = {0x01, 0x02, 0xFE, 0xFF};

// ── CRC16 Modbus ──────────────────────────────────────────────────────────────
uint16_t crc16(const uint8_t* buf, uint8_t len) {
  uint16_t crc = 0xFFFF;
  for (uint8_t i = 0; i < len; i++) {
    crc ^= buf[i];
    for (uint8_t j = 0; j < 8; j++)
      crc = (crc & 1) ? (crc >> 1) ^ 0xA001 : crc >> 1;
  }
  return crc;
}

// ── Construir trama Modbus con CRC ────────────────────────────────────────────
void buildQuery(uint8_t addr, uint8_t* out) {
  out[0] = addr;
  out[1] = 0x03;
  out[2] = 0x00;
  out[3] = 0x00;
  out[4] = 0x00;
  out[5] = 0x07;
  uint16_t c = crc16(out, 6);
  out[6] = c & 0xFF;
  out[7] = (c >> 8) & 0xFF;
}

// ── Intentar lectura y mostrar resultado raw ───────────────────────────────────
bool tryRead(uint8_t addr, uint32_t baud) {
  rs485.end();
  rs485.begin(baud);
  delay(50);

  // Flush cualquier basura en el buffer
  while (rs485.available()) rs485.read();

  uint8_t query[8];
  buildQuery(addr, query);

  // Transmitir
  digitalWrite(RS485_DE, HIGH);
  delayMicroseconds(500);  // tiempo extra para estabilizar el bus
  for (uint8_t i = 0; i < 8; i++) rs485.write(query[i]);
  rs485.flush();
  delayMicroseconds(500);
  digitalWrite(RS485_DE, LOW);

  // Leer con timeout de 1000 ms
  uint8_t resp[32];
  uint8_t idx = 0;
  unsigned long t0 = millis();
  while (millis() - t0 < 1000 && idx < 32) {
    if (rs485.available()) {
      resp[idx++] = rs485.read();
      t0 = millis();  // reset timeout tras cada byte recibido
    }
  }

  Serial.print(F("  Bytes recibidos: "));
  Serial.println(idx);

  if (idx == 0) {
    Serial.println(F("  -> Sin respuesta"));
    return false;
  }

  // Mostrar bytes en HEX
  Serial.print(F("  RAW: "));
  for (uint8_t i = 0; i < idx; i++) {
    if (resp[i] < 0x10) Serial.print('0');
    Serial.print(resp[i], HEX);
    Serial.print(' ');
  }
  Serial.println();

  // Analizar si parece Modbus válido
  if (idx >= 5 && resp[1] == 0x03) {
    uint8_t byteCount = resp[2];
    Serial.print(F("  -> Parece Modbus RTU. Byte count: "));
    Serial.println(byteCount);

    if (idx >= (uint8_t)(3 + byteCount + 2)) {
      uint16_t crcCalc = crc16(resp, 3 + byteCount);
      uint16_t crcRcvd = (uint16_t)resp[3 + byteCount + 1] << 8 |
                                   resp[3 + byteCount];
      if (crcCalc == crcRcvd) {
        Serial.println(F("  -> CRC OK ✓  — SENSOR ENCONTRADO"));

        // Parsear si son 7 registros (14 bytes de datos)
        if (byteCount == 14) {
          Serial.println(F("  -> Registros:"));
          for (uint8_t r = 0; r < 7; r++) {
            uint16_t raw = (uint16_t)resp[3 + r*2] << 8 | resp[4 + r*2];
            Serial.print(F("     Reg"));
            Serial.print(r);
            Serial.print(F(": raw="));
            Serial.print(raw);
            Serial.print(F("  /10="));
            Serial.println(raw / 10.0f, 1);
          }
        }
        return true;
      } else {
        Serial.print(F("  -> CRC FALLO. Calc=0x"));
        Serial.print(crcCalc, HEX);
        Serial.print(F("  Rcvd=0x"));
        Serial.println(crcRcvd, HEX);
      }
    } else {
      Serial.print(F("  -> Trama incompleta, esperados="));
      Serial.print(3 + byteCount + 2);
      Serial.print(F(" recibidos="));
      Serial.println(idx);
    }
  } else if (idx > 0) {
    Serial.println(F("  -> Respuesta no reconocida como Modbus (func!=0x03)"));
  }

  return false;
}

// ══════════════════════════════════════════════════════════════════════════════
void setup() {
  Serial.begin(9600);
  while (!Serial) delay(10);
  delay(500);

  pinMode(RS485_DE, OUTPUT);
  digitalWrite(RS485_DE, LOW);

  Serial.println(F("=== AgroSense: Diagnostico RS485 Modbus ==="));
  Serial.println(F("Probando baud rates y direcciones..."));
  Serial.println();

  bool found = false;

  for (uint8_t b = 0; b < 4 && !found; b++) {
    for (uint8_t a = 0; a < 4 && !found; a++) {
      Serial.print(F("[ Baud="));
      Serial.print(BAUD_RATES[b]);
      Serial.print(F("  Addr=0x"));
      if (ADDRESSES[a] < 0x10) Serial.print('0');
      Serial.print(ADDRESSES[a], HEX);
      Serial.println(F(" ]"));

      if (tryRead(ADDRESSES[a], BAUD_RATES[b])) {
        Serial.println();
        Serial.println(F("===================================="));
        Serial.print(F("SENSOR OK — Baud: "));
        Serial.print(BAUD_RATES[b]);
        Serial.print(F("  Addr: 0x"));
        Serial.println(ADDRESSES[a], HEX);
        Serial.println(F("Usa estos valores en NodoSuelo_LoRa.ino"));
        Serial.println(F("===================================="));
        found = true;
      }
      delay(200);
    }
  }

  if (!found) {
    Serial.println();
    Serial.println(F("=== SENSOR NO ENCONTRADO ==="));
    Serial.println(F("Verifica:"));
    Serial.println(F("  1. VCC sensor >= 5V (recomendado 12V)"));
    Serial.println(F("  2. A del modulo RS485 -> 485A del sensor (amarillo)"));
    Serial.println(F("  3. B del modulo RS485 -> 485B del sensor (verde)"));
    Serial.println(F("  4. DE y RE del modulo puenteados juntos en D6"));
    Serial.println(F("  5. GND comun entre Arduino, modulo RS485 y fuente sensor"));
  }

  Serial.println(F("\nReiniciando diagnostico en 10 s..."));
}

void loop() {
  delay(10000);

  Serial.println(F("\n--- Reintento ---"));
  // Solo probar 9600/0x01 en el loop para monitoreo continuo
  rs485.end(); rs485.begin(9600); delay(50);
  while (rs485.available()) rs485.read();

  uint8_t query[8]; buildQuery(0x01, query);
  digitalWrite(RS485_DE, HIGH);
  delayMicroseconds(500);
  for (uint8_t i = 0; i < 8; i++) rs485.write(query[i]);
  rs485.flush();
  delayMicroseconds(500);
  digitalWrite(RS485_DE, LOW);

  uint8_t resp[32]; uint8_t idx = 0;
  unsigned long t0 = millis();
  while (millis() - t0 < 1000 && idx < 32) {
    if (rs485.available()) { resp[idx++] = rs485.read(); t0 = millis(); }
  }

  Serial.print(F("Bytes: ")); Serial.print(idx); Serial.print(F("  RAW: "));
  for (uint8_t i = 0; i < idx; i++) {
    if (resp[i] < 0x10) Serial.print('0');
    Serial.print(resp[i], HEX); Serial.print(' ');
  }
  Serial.println();
}
