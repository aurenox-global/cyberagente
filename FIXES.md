# Correcciones de la v2.1.0 respecto a la v2.0

Informe de la auditoría que originó estos cambios: [`REVISION-APK.md`](REVISION-APK.md)

Resumen: cada hallazgo con su corrección verificada en el APK compilado.

---

## 🔴 Críticos

### C-01 · Build de depuración firmado con clave pública

**Antes:** `CN=Android Debug`, `android:debuggable="true"`, solo firma v2.

**Ahora:**
- Keystore propio `keystore/cyberagent-release.jks` (RSA 4096, validez 10 000 días).
- `CN=CyberAgent Release, OU=Blue Team, O=Aurenox Global, L=Vienna, C=AT`
- `isDebuggable = false` en release.
- Firma **v1 + v2 + v3**.

```
Signer #1 certificate DN: CN=CyberAgent Release, OU=Blue Team, O=Aurenox Global, L=Vienna, C=AT
Signer #1 key size (bits): 4096
Verified using v2 scheme: true
Verified using v3 scheme: true
```

### C-02 · Servicio de accesibilidad con acceso total

**Antes:** `canRetrieveWindowContent=true`, eventos `TYPE_VIEW_TEXT_CHANGED` +
`TYPE_WINDOW_STATE_CHANGED`, `FLAG_REQUEST_FILTER_KEY_EVENTS`.

**Ahora** (`res/xml/accessibility_service_config.xml`):

```xml
android:accessibilityEventTypes="typeWindowStateChanged"
android:accessibilityFlags="flagDefault"
android:canRetrieveWindowContent="false"
```

Ya no lee el contenido de otras aplicaciones ni eventos de teclado. Conserva solo el
guardia de instalación (aviso cuando se abre el instalador de paquetes).

### C-03 · La IA no podía funcionar

**Antes:** `LlamaEngine` → `System.loadLibrary(...)` + `/sdcard/CyberAgent/models/qwen2.5-3b-q4_k_m.gguf`,
pero el APK **no incluía ninguna librería nativa** ni modelo. La función anunciada no arrancaba.

**Ahora:** motor local determinista (`SecurityAssistant.localReport`) que:
- Analiza las alertas almacenadas, los permisos de las apps, el estado de la red y la cuarentena.
- Genera hallazgos con severidad, referencia **MITRE ATT&CK** y acción recomendada.
- **No necesita** modelos, ficheros externos ni conexión.

Modo remoto **opcional**: endpoint compatible con OpenAI, solo HTTPS y con autorización
explícita en cada consulta.

---

## 🟠 Medios

### M-01 · Tráfico en claro

**Antes:** `<base-config cleartextTrafficPermitted="true">`.
**Ahora:** `cleartextTrafficPermitted="false"` + `android:usesCleartextTraffic="false"`.

### M-02 · Datos sin cifrar

**Antes:** SQLite con títulos y detalles en claro.
**Ahora:**
- Los campos sensibles de la base de datos se cifran con **AES-256-GCM** (`core/Crypto.kt`).
- Todos los ajustes y listas se cifran igualmente antes de escribirse (`core/Prefs.kt`).
- Clave **no exportable** custodiada en el **Android Keystore**.

### M-03 · Permisos raros

**Antes:** `KILL_BACKGROUND_PROCESSES`, `HIDE_OVERLAY_WINDOWS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`,
`READ_MEDIA_IMAGES`, `ACCESS_FINE_LOCATION`.

**Ahora:** eliminados todos. Se usan `NEARBY_WIFI_DEVICES` (`neverForLocation`) para la Wi-Fi y
el foreground service declarado como `specialUse` en lugar de matar procesos.

### M-04 · Solo firma v2

**Ahora:** v1 + v2 + v3.

---

## ✅ Funciones que ahora sí se ejecutan

| Anuncio de la v2.0 | Cómo se implementa en la v2.1.0 |
|--------------------|----------------------------------|
| Blindaje de llamadas | `CallScreeningService` + rol `ROLE_CALL_SCREENING`; rechaza (`setDisallowCall(true)`, `setRejectCall(true)`) números bloqueados, ocultos o con formato anómalo. Permisos reales: `READ_PHONE_STATE`, `READ_CALL_LOG`, `READ_CONTACTS`. |
| Blindaje de SMS | `SMS_RECEIVED` para análisis y aviso; `SMS_DELIVER` + rol `ROLE_SMS` para **descartar** el mensaje fraudulento de verdad. Permisos: `RECEIVE_SMS`, `READ_SMS`. |
| Monitor de Internet | `NetworkStatsManager` con `PACKAGE_USAGE_STATS`: consumo por app del día y serie de 7 días. |
| Cortafuegos VPN | `VpnService` real: las apps bloqueadas se enrutan al túnel y sus paquetes se **descartan**; el resto mantiene su conexión. Sin servidor remoto. |
| Cuarentena | Corta la red de la app + registro + desinstalación + suspensión si es device owner. |
| IoC online | `OnlineIoc` con clave del usuario, **HTTPS obligatorio** y consentimiento explícito por operación. Base local en `assets/ioc_db.json` que funciona offline. |
| Widgets | Tres widgets operativos (estado, consumo de hoy, gráfica de 7 días renderizada a bitmap). |

---

## 🔍 Verificación del APK compilado

```
Paquete      : com.cyberagent.app
Versión      : 2.1.0 (versionCode 3)
minSdk       : 26 · targetSdk/compileSdk 35
Tamaño       : 4.431.780 bytes (~4,2 MB)
SHA-256      : b7e2490e146104f71f21645bd2ba9edaab6ed10e78dfc71b3217d5be82dad6b5
debuggable   : ausente (false)
cleartext    : false
allowBackup  : false
Firma        : v2 + v3, RSA 4096
Librerías .so: 0 (ninguna necesidad de código nativo)
Telemetría   : 0 SDK de analítica / crash reporting
```

Componentes exportados (mínimos y justificados):

- `MainActivity` — lanzador.
- `SecurityAccessibilityService` — protegido por `BIND_ACCESSIBILITY_SERVICE`.
- `CallGuardService` — protegido por `BIND_SCREENING_SERVICE`.
- `SmsMonitorReceiver` — protegido por `RECEIVE_SMS`.
- `SmsDeliverReceiver` — protegido por `BROADCAST_SMS`.

`BootReceiver` y `PackageChangeReceiver` quedan con `exported=false` (solo reciben
broadcasts del sistema o de la propia app).

---

## ⚠️ Notas de instalación

- La firma cambió respecto a la v2.0: **hay que desinstalar la versión anterior** antes de instalar
  la 2.1.0 (Android no permite actualizar entre firmas distintas).
- El acceso al uso, el rol de llamadas y el rol de SMS se conceden **manualmente** desde el panel
  y desde Ajustes; son permisos/roles que el sistema no otorga por diálogo normal.
- Este APK se ha compilado y verificado de forma estática. **No se ha ejecutado en un dispositivo
  real**, así que conviene una prueba funcional antes de publicarlo.

---

# Novedades de la v2.3.0 (versionCode 7)

## 🔐 Los accesos se conceden solos al iniciar

Antes había que ir a Ajustes y activar a mano cada permiso especial. Ahora al
abrir la app por primera vez se lanza el **Asistente de accesos**
(`PermissionsActivity`):

- Recorre **los 9 accesos** en orden: notificaciones/Wi-Fi, teléfono y llamadas,
  SMS/MMS, acceso al uso, rol de filtrado de llamadas, rol de app de SMS,
  accesibilidad, No Molestar y consentimiento de la VPN.
- Un solo toque en **CONCEDER TODO**: pide cada cosa y **vuelve solo al paso
  siguiente** cuando el usuario regresa de la pantalla del sistema.
- Lista con estado (● concedido / ○ pendiente) y barra de progreso.
- Cada acceso explica **para qué sirve** en una línea.
- Se puede repetir en cualquier momento desde el panel o desde Ajustes.

## 🧊 Se acabaron los bloqueos ("se frisaba")

La causa eran lecturas pesadas en el **hilo principal**: NetworkStatsManager
(segundos por consulta), el análisis de todas las apps instaladas y el
descifrado del historial. Ahora:

- Nuevo `core/Bg.kt`: todo lo pesado va a un executor de fondo y el resultado
  se publica en el hilo de interfaz. Pantallas con "Calculando…" mientras tanto.
- `NetworkStatsReader` con **caché de 45 s** + `invalidate()`: repintar o volver
  a una pantalla ya no repite la consulta.
- Detalle de consumo por app: una **serie diaria** calculada en fondo
  (`dailySeriesForUid`).
- Panel, lista de apps y registro de eventos cargan **asíncronos**.

## 🎨 Interfaz rediseñada por áreas

- Tarjetas de sección con **barra de color** + título en mayúsculas y subtítulo.
- **Estado general** en tarjeta destacada (héroe) con chips de resumen.
- Módulos en **mosaicos tocables** (rejilla de 2 columnas) en vez de una lista
  larga de botones.
- Acciones rápidas como **filas anchas** con explicación.
- Eventos con **chip de severidad** por color.
- Pantalla nueva **Registro de eventos** (`AlertsActivity`): historial completo
  con filtros por severidad y paginación, siempre accesible.

## 🧾 Verificación

```
Paquete    : com.cyberagent.app
Versión    : 2.3.0 (versionCode 7) · minSdk 26 · targetSdk 35
Tamaño     : 4.462.451 bytes
SHA-256    : dabdf7dffce04586663cee5bdc62daaf1e96d6675582a5a7f222e8668a77b122
Firma      : CN=CyberAgent Release, RSA 4096, esquemas v1+v2+v3
```

Instalable **encima de la 2.2.1** (misma firma).
