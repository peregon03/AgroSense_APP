-- =========================================
-- AgroSense - Esquema base (PostgreSQL)
-- =========================================

-- 1) USERS
CREATE TABLE IF NOT EXISTS users (
  id SERIAL PRIMARY KEY,
  first_name VARCHAR(80) NOT NULL,
  last_name  VARCHAR(80) NOT NULL,
  email      VARCHAR(120) UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT NOW()
);

-- 2) SENSORS (cada sensor pertenece a un usuario)
CREATE TABLE IF NOT EXISTS sensors (
  id SERIAL PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_id VARCHAR(80) UNIQUE NOT NULL,
  name VARCHAR(120) NOT NULL,
  location VARCHAR(160),
  is_active BOOLEAN DEFAULT TRUE,
  -- Si vas a seguir usando api_key para ingest directo, déjalo:
  api_key TEXT,
  created_at TIMESTAMP DEFAULT NOW()
);

-- Índices útiles
CREATE INDEX IF NOT EXISTS idx_sensors_user_id ON sensors(user_id);
CREATE INDEX IF NOT EXISTS idx_sensors_device_id ON sensors(device_id);

-- 3) SENSOR READINGS (lecturas por sensor)
CREATE TABLE IF NOT EXISTS sensor_readings (
  id BIGSERIAL PRIMARY KEY,
  sensor_id INTEGER NOT NULL REFERENCES sensors(id) ON DELETE CASCADE,
  temperature_c NUMERIC(5,2) NOT NULL,
  humidity_pct NUMERIC(5,2) NOT NULL,
  created_at TIMESTAMP DEFAULT NOW()
);

select * from sensors

DELETE FROM sensors WHERE id = 1;



-- Eliminar tabla vieja si existe (tenía columnas distintas)
DROP TABLE IF EXISTS sensor_readings;

-- Crear tabla con las 3 variables de AgroSense
CREATE TABLE sensor_readings (
    id            SERIAL PRIMARY KEY,
    sensor_id     INTEGER NOT NULL REFERENCES sensors(id) ON DELETE CASCADE,
    temperature   NUMERIC(5,2),
    air_humidity  NUMERIC(5,2),
    soil_humidity NUMERIC(5,2),
    created_at    TIMESTAMP DEFAULT NOW()
);

-- Índice para consultas rápidas por sensor
CREATE INDEX idx_readings_sensor_id ON sensor_readings(sensor_id);
CREATE INDEX idx_readings_created_at ON sensor_readings(created_at DESC);


SELECT * FROM sensor_readings;

SELECT * FROM sensors