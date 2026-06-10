# Guruyu Tracker

Sistema de seguimiento de ubicación con app Android nativa (Kotlin), backend PHP 8 y MySQL.

## Componentes

- **android/** — App Android 12+ con mapa OpenStreetMap, reporte cada minuto y cola offline.
- **backend/** — API REST + panel administrativo PHP.

## Requisitos

- Docker (para desarrollo local del backend)
- Android Studio o Android SDK + JDK 17 (para compilar el APK)
- Celular Android 12+ con instalación manual de APK

## 1. Backend local

```bash
cd backend
docker compose up -d
```

Servicios:

| Servicio | URL |
|----------|-----|
| Panel admin | http://localhost:8080/admin/login.php |
| API reporte | POST http://localhost:8080/api/report.php |
| API dispositivos | GET http://localhost:8080/api/devices.php |

**Credenciales admin:** `admin` / `admin123`

**API Key por defecto:** `cambia-esta-api-key-en-produccion`

Edita `backend/config/config.php` (se genera al levantar Docker desde `config.example.php`).

## 2. Flujo de uso

1. Instala el APK en el celular.
2. Abre la app y copia el **ID del dispositivo**.
3. En el panel admin, habilita ese UUID y asigna un nombre.
4. En el celular, pulsa **Iniciar seguimiento**.
5. La app reporta cada minuto mientras permanezca abierta (puede estar la pantalla apagada).
6. Si sales a otra app o al inicio, el seguimiento se detiene automáticamente.

## 3. Compilar APK

### Configurar URL del servidor

Edita `android/app/build.gradle.kts`:

```kotlin
buildConfigField("String", "API_BASE_URL", "\"http://TU_IP:8080/\"")
buildConfigField("String", "API_KEY", "\"tu-api-key\"")
```

- Emulador Android → `http://10.0.2.2:8080/`
- Celular físico en la misma red → `http://192.168.x.x:8080/` (IP de tu PC/VPS)

### Generar APK

```bash
cd android
./gradlew assembleDebug
```

APK resultante:

```
android/app/build/outputs/apk/debug/app-debug.apk
```

Para release firmado:

```bash
./gradlew assembleRelease
```

## 4. API

### POST `/api/report.php`

Headers: `X-API-Key: <api_key>`

```json
{
  "device_uuid": "uuid-v4",
  "latitude": 19.43260000,
  "longitude": -99.13320000,
  "reported_at": "2026-06-10 15:30:45"
}
```

### GET `/api/devices.php?device_uuid=<uuid>`

Headers: `X-API-Key: <api_key>`

Devuelve todos los dispositivos habilitados con su última ubicación. `is_stale: true` si no reportan hace 2+ minutos (se muestran en gris en el mapa).

## 5. Producción (VPS)

1. Instala PHP 8.2+, Apache/Nginx y MySQL 8.
2. Apunta el DocumentRoot a `backend/public`.
3. Importa `backend/sql/schema.sql`.
4. Copia `config.example.php` → `config.php` y ajusta BD + API key.
5. Cambia la contraseña del admin en MySQL.
6. Recompila el APK con la URL pública del servidor.
7. Usa HTTPS en producción si es posible.

## Comportamiento de ubicación

| Estado | Precisión |
|--------|-----------|
| App abierta, pantalla encendida | GPS alta precisión |
| App abierta, pantalla apagada | Red + Wi‑Fi |
| App en segundo plano / otra app | Seguimiento detenido |

Los reportes fallidos se guardan en cola local y se reenvían cuando hay conexión y el dispositivo está habilitado.
