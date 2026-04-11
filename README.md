# AgroSense

**AgroSense** es una aplicación móvil Android para el monitoreo remoto de sensores agrícolas IoT. Permite a agricultores y técnicos visualizar en tiempo real variables ambientales como temperatura, humedad del aire, CO₂ y metano, configurar alertas automáticas y controlar actuadores (bombas de riego) desde el teléfono, usando comunicación Bluetooth Low Energy (BLE) y sincronización con un servidor en la nube.

---

## Tabla de Contenidos

- [Descripción general](#descripción-general)
- [Características principales](#características-principales)
- [Stack tecnológico](#stack-tecnológico)
- [Estructura del proyecto](#estructura-del-proyecto)
- [Documentación adicional](#documentación-adicional)
- [Requisitos previos](#requisitos-previos)
- [Instalación y configuración](#instalación-y-configuración)
- [Autores](#autores)

---

## Descripción general

AgroSense conecta un dispositivo móvil Android con nodos sensores basados en **Arduino Nano ESP32** mediante Bluetooth Low Energy. Una vez configurado el nodo, este se conecta de forma autónoma a una red WiFi y envía lecturas periódicas a un servidor backend en la nube. La app actúa como panel de control: permite registrar sensores, visualizar datos históricos en gráficas interactivas, configurar umbrales de alerta y recibir notificaciones cuando una variable sale del rango normal.

```
[Sensores ESP32] ←──BLE──→ [App Android] ←──HTTP──→ [Backend API REST]
                                                              ↕
                                                       [Base de datos
                                                        PostgreSQL]
```

El flujo típico de uso es:

1. El usuario se registra e inicia sesión en la app.
2. Acerca el teléfono al nodo sensor y se conecta vía BLE.
3. Registra el sensor en el sistema y lo configura con las credenciales WiFi.
4. El ESP32 se conecta a la red y comienza a enviar datos al servidor de forma autónoma.
5. Desde la app, el usuario puede ver las lecturas en tiempo real, consultar el historial y gestionar alertas.

---

## Características principales

| Categoría | Funcionalidad |
|-----------|---------------|
| **Autenticación** | Registro, inicio de sesión, verificación de correo, recuperación de contraseña |
| **Sensores BLE** | Escaneo de dispositivos, conexión, lectura en tiempo real de temperatura, humedad, CO₂ y metano |
| **Configuración** | Envío de credenciales WiFi al sensor vía BLE, reset remoto del dispositivo |
| **Actuadores** | Control de bomba de riego desde la app vía BLE |
| **Historial** | Gráficas interactivas con rango configurable: hoy, semana, mes o trimestre |
| **Alertas** | Configuración de umbrales mínimos y máximos por variable, notificaciones push en background |
| **Perfil** | Edición de datos personales y cambio de contraseña |
| **Sesión** | Persistencia de sesión con token almacenado localmente (DataStore) |

---

## Stack tecnológico

### Aplicación móvil (Android)

| Tecnología | Versión | Uso |
|------------|---------|-----|
| Kotlin | 2.0.21 | Lenguaje principal |
| Jetpack Compose | BOM 2024.09.03 | Interfaz de usuario declarativa |
| Material Design 3 | — | Sistema de diseño |
| Retrofit 2 | 2.11.0 | Cliente HTTP para API REST |
| OkHttp | 4.12.0 | Capa de red, logging de peticiones |
| Gson | — | Serialización/deserialización JSON |
| DataStore Preferences | 1.1.1 | Almacenamiento local de sesión |
| Android BLE API | — | Comunicación Bluetooth Low Energy |
| MPAndroidChart | 3.1.0 | Gráficas de líneas interactivas |
| WorkManager | 2.9.1 | Verificación periódica de alertas en background |
| Accompanist Permissions | 0.34.0 | Manejo de permisos en tiempo de ejecución |
| Navigation Compose | 2.8.7 | Navegación entre pantallas |
| ViewModel + StateFlow | 2.8.7 | Arquitectura MVVM, gestión de estado reactivo |

### Hardware (nodo sensor)

| Componente | Detalle |
|------------|---------|
| Microcontrolador | Arduino Nano ESP32 (ABX00092) |
| Protocolo de comunicación local | Bluetooth Low Energy (BLE) GATT Server |
| Protocolo de comunicación remota | WiFi → HTTP REST |
| Almacenamiento offline | SPIFFS (sistema de archivos en flash) |
| Variables medidas | Temperatura (°C), Humedad aire (%), CO₂ (ppm), Metano (ppm) |

### Backend

| Tecnología | Uso |
|------------|-----|
| Node.js | Servidor de aplicaciones |
| PostgreSQL | Base de datos relacional |
| API REST | Comunicación con app móvil y sensores |
| AWS EC2 | Hosting del servidor (`3.15.133.197:3000`) |

---

## Estructura del proyecto

```
AgroSense_APP/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/example/agrosense/
│           ├── MainActivity.kt               # Punto de entrada, navegación raíz
│           ├── ui/
│           │   ├── screens/                  # 15 pantallas Jetpack Compose
│           │   ├── viewmodel/                # 4 ViewModels (Auth, Sensor, Ble, Alert)
│           │   └── theme/                    # Colores, tipografía, tema Material 3
│           ├── data/
│           │   ├── api/                      # Interfaces Retrofit (REST)
│           │   ├── model/                    # Data classes (modelos de datos)
│           │   ├── ble/                      # BleManager: lógica BLE completa
│           │   └── storage/                  # SessionManager (DataStore)
│           └── workers/
│               └── AlertCheckWorker.kt       # Tarea periódica de verificación de alertas
├── Script ArduinoBLE/                        # Firmware del nodo sensor ESP32
│   ├── AgroSense_ESP32/
│   ├── AgroSense_WIFI_BLE/
│   └── ConfguracionFlashh/
├── Scripts base de datos creacion/           # Scripts SQL para PostgreSQL
├── build.gradle.kts
└── settings.gradle.kts
```

> La documentación técnica detallada se encuentra en la carpeta [`docs/`](docs/).

---

## Documentación adicional

| Documento | Descripción |
|-----------|-------------|
| [`docs/ARQUITECTURA.md`](docs/ARQUITECTURA.md) | Arquitectura MVVM, módulos, flujo de datos general |
| [`docs/BACKEND.md`](docs/BACKEND.md) | API REST: endpoints, modelos de base de datos, servidor |
| [`docs/BLE_HARDWARE.md`](docs/BLE_HARDWARE.md) | Protocolo BLE, firmware ESP32, UUIDs, flujos de comunicación |
| [`docs/MODULOS.md`](docs/MODULOS.md) | Pantallas, ViewModels, flujos por módulo |
| [`docs/INSTALACION.md`](docs/INSTALACION.md) | Guía de instalación y configuración del entorno |

---

## Requisitos previos

- **Android Studio** Hedgehog o superior
- **SDK de Android:** mínimo API 24 (Android 7.0), compilado con API 36
- **Dispositivo físico** Android con soporte BLE (se recomienda no usar emulador para funciones BLE)
- **Arduino IDE** con soporte para placas ESP32 (para el firmware del sensor)
- **Node.js** y acceso a PostgreSQL (para el backend)

---

## Instalación y configuración

Ver guía completa en [`docs/INSTALACION.md`](docs/INSTALACION.md).

Pasos rápidos:

```bash
# 1. Clonar el repositorio
git clone https://github.com/santiagocar2108-hub/AgroSense_APP.git

# 2. Abrir en Android Studio
# File → Open → seleccionar la carpeta AgroSense_APP

# 3. Sincronizar dependencias Gradle
# Android Studio lo hace automáticamente al abrir

# 4. Conectar un dispositivo físico Android

# 5. Ejecutar la app (Run → Run 'app')
```

---

## Autores

Desarrollado como proyecto de grado — Ingeniería de Sistemas.

- **Santiago** — Desarrollo móvil Android, firmware ESP32, integración BLE
