select * from users

CREATE TABLE IF NOT EXISTS sensors (
  id SERIAL PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,

  -- identificador único del dispositivo (ej: MAC del ESP32 o un UUID)
  device_id VARCHAR(80) NOT NULL UNIQUE,

  -- nombre visible para el usuario
  name VARCHAR(120) NOT NULL,

  -- opcional: ubicación o descripción
  location VARCHAR(160),

  -- clave para que el ESP32 pueda enviar datos sin usar el token del usuario
  api_key TEXT NOT NULL UNIQUE,

  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sensors_user_id ON sensors(user_id);



CREATE TABLE IF NOT EXISTS sensor_readings (
  id BIGSERIAL PRIMARY KEY,
  sensor_id INTEGER NOT NULL REFERENCES sensors(id) ON DELETE CASCADE,

  temperature_c REAL,
  humidity_pct REAL,

  -- opcional: si luego agregas batería, señal, etc.
  -- battery_v REAL,
  -- rssi INTEGER,

  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_readings_sensor_id_created_at
ON sensor_readings(sensor_id, created_at DESC);


select * from sensors