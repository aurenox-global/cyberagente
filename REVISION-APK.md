# Informe de revisión — CyberAgent_2.0.apk

**Fecha:** 2026-09-20 · **Analista:** Key (revisión estática, sin ejecución)
**Fichero:** `CyberAgent_2.0.apk` — 6.846.020 bytes (6,53 MB) · 961 entradas · 6 ficheros DEX

---

## 1. Integridad y firma

| Campo | Valor |
|---|---|
| SHA-256 | `5e6e4874e752313cc86fe850efabe71401d39c7d3eba44b0c8a161c7e7f0a5c2` |
| SHA-1 | `11acbf59cbca835bd107277ffcab866387e0d55a` |
| MD5 | `03de66d266301ff435c5a1244f00cccf` |
| Esquema de firma | Solo **v2** (sin v1 ni v3) — válida |
| Certificado | **`C=US, O=Android, CN=Android Debug`** |
| Huella cert SHA-256 | `fda7dc9968704f9b4e461567190465e5426e648a1b084d3373728a1f2a4a1a3b` |

🔴 **El APK está firmado con el certificado de depuración de Android.**
No es una build de release distribuible.

---

## 2. Identidad de la aplicación

| Campo | Valor |
|---|---|
| Package | `com.cyberagent.app` |
| versionName / versionCode | `2.0` / `2` |
| minSdk / targetSdk / compileSdk | 26 (Android 8.0) / 35 (Android 15) / 35 |
| Label | CyberAgent |
| AGP | 8.7.0 |
| Stack inferido | Kotlin + Views/XML (Material Components, ViewBinding), Room, WorkManager, androidx. **No usa Jetpack Compose.** |
| Build | `android:debuggable="true"` |

---

## 3. Hallazgos críticos

### C-01 · APK de depuración firmado con clave pública
- `android:debuggable="true"`.
- Certificado `Android Debug`: es la keystore **estándar y pública** (alias `androiddebugkey`, contraseña `android`).
- **Impacto:**
  1. Cualquier tercero puede firmar un APK con el mismo certificado y el mismo package → el sistema lo aceptaría como **actualización legítima**.
  2. Con acceso ADB al dispositivo se puede adjuntar depurador / `run-as` al proceso y volcar sus datos.
- **Severidad:** Crítica para distribución.

### C-02 · Servicio de accesibilidad con acceso total a pantalla
`res/xml/accessibility_service_config.xml`:
```
canRetrieveWindowContent = true
accessibilityFlags       = 0x21  (FLAG_DEFAULT | FLAG_REQUEST_FILTER_KEY_EVENTS)
accessibilityEventTypes  = 0x30  (TYPE_VIEW_TEXT_CHANGED | TYPE_WINDOW_STATE_CHANGED)
```
- Lee el **contenido de la ventana de cualquier app** y el **texto que se escribe** en campos de otras aplicaciones.
- `FLAG_REQUEST_FILTER_KEY_EVENTS` permite **filtrar/capturar eventos de tecla**.
- El servicio está declarado `exported=true` con `BIND_ACCESSIBILITY_SERVICE` (correcto), pero la capacidad es la más alta del sistema.
- **Combinado con C-01** (debuggable) el riesgo se multiplica: un depurador conectado ve todo lo que el servicio captura.
- **Severidad:** Crítica/alta (mitigada parcialmente por ser una app defensiva, pero no hay justificación documentada ni filtrado selectivo de eventos).

### C-03 · La IA offline no puede funcionar con este APK
- La clase `com.cyberagent.app.LlamaEngine` usa `System.loadLibrary(...)` y busca el modelo en:
  `/sdcard/CyberAgent/models/qwen2.5-3b-q4_k_m.gguf`
- **El APK no contiene ninguna librería nativa** (0 ficheros `.so`, no existe carpeta `lib/`) ni ningún modelo en `assets/`.
- **Impacto:** el "Asistente IA integrado 100% offline" **no arranca** tras instalar: falta el binario nativo (JNI de llama.cpp) y el usuario debe copiar a mano un modelo de ~2 GB en almacenamiento externo.
- Si el `.so` se cargase desde `/sdcard` (almacenamiento externo escribible), sería un **vector de ejecución de código**: cualquier app con permiso de escritura podría sustituirlo.
- **Severidad:** Alta (funcional + seguridad).

---

## 4. Hallazgos medios

### M-01 · Tráfico en claro permitido
`res/xml/network_security_config.xml` → `<base-config cleartextTrafficPermitted="true">`.
Aunque el manifest declara `usesCleartextTraffic="false"`, el `networkSecurityConfig` **tiene prioridad** → HTTP en claro permitido.

### M-02 · Base de datos sin cifrar
Room (`AlertDatabase`, `AlertDao`) sin SQLCipher. Alertas e histórico de amenazas quedan en claro dentro del sandbox (aceptable, pero contradice el tono "keystore/cripto" de la documentación).

### M-03 · Permisos potentes declarados
`QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS`, `KILL_BACKGROUND_PROCESSES`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `ACCESS_FINE_LOCATION`, `NEARBY_WIFI_DEVICES`, `READ_MEDIA_IMAGES`, `HIDE_OVERLAY_WINDOWS`, `INTERNET`, `BIND_VPN_SERVICE`.
Son coherentes con un monitor de seguridad, pero `KILL_BACKGROUND_PROCESSES` y `HIDE_OVERLAY_WINDOWS` no encajan en el discurso y deberían justificarse o eliminarse.

### M-04 · Solo firma v2
Sin esquema v3/v4 → sin rotación de clave ni protección adicional.

---

## 5. Discrepancias con la web pública y el README

| Afirmación | Realidad verificada |
|---|---|
| "Blindaje de llamadas y SMS" | **No declara** `READ_PHONE_STATE`, `READ_CALL_LOG`, `RECEIVE_SMS`, `SEND_SMS` ni `READ_CONTACTS`. El módulo (`CallSmsShieldActivity`) no puede leer ni bloquear llamadas/SMS reales; solo UI + accesibilidad. |
| "Asistente IA integrado" | Requiere librería nativa y modelo externo no incluidos (ver C-03). |
| "Consulta de IoCs contra VirusTotal / AbuseIPDB" | **0 endpoints de red** en el APK. No hay lógica de consulta remota. |
| "~8 MB" | 6,53 MB. |
| "9+ módulos activos" | 243 clases propias; módulos presentes, pero varios son parciales. |

---

## 6. Puntos correctos (verificados)

- ✅ **Cero telemetría**: 0 coincidencias de Firebase, Crashlytics, AppsFlyer, Adjust, Facebook, Amplitude, Sentry o Unity.
- ✅ **Cero URLs externas** y **cero secretos/claves hardcoded** en el DEX.
- ✅ `allowBackup=false` + `backup_rules.xml` y `data_extraction_rules.xml` excluyen `database`, `sharedpref`, `files` y `root`.
- ✅ Firma v2 válida y verificable.
- ✅ `FirewallVpnService` declarado `exported=false` con `BIND_VPN_SERVICE`.
- ✅ `BootReceiver` protegido con `RECEIVE_BOOT_COMPLETED` (broadcast protegido) + `MY_PACKAGE_REPLACED`.
- ✅ Sin WebView cargando contenido dinámico.
- ✅ Permisos de accesibilidad y VPN declarados explícitamente para que el usuario los conceda a mano.

---

## 7. Recomendaciones

**Bloqueantes para publicar**
1. Regenerar como **build de release** con keystore propio: `debuggable=false`, `minifyEnabled=true`, `shrinkResources=true`.
2. Empaquetar las `.so` de llama.cpp en `jniLibs/` (o eliminar la feature IA si no está lista).
3. Si la IA se mantiene: descarga del modelo **verificada por hash** y guardada en almacenamiento interno (`getFilesDir()`), nunca en `/sdcard`.

**Importantes**
4. Reducir el servicio de accesibilidad: `accessibilityEventTypes` a lo estrictamente necesario y quitar `FLAG_REQUEST_FILTER_KEY_EVENTS` si no se usa.
5. `cleartextTrafficPermitted=false` en `network_security_config.xml`.
6. Cifrar la base Room (SQLCipher) si guarda datos de amenazas.
7. Quitar `KILL_BACKGROUND_PROCESSES` y `HIDE_OVERLAY_WINDOWS`, o documentar su uso.
8. Activar firma **v3** (y v4 opcional).

**Coherencia de producto**
9. Corregir la web/README: no anunciar blindaje de llamadas/SMS sin los permisos necesarios, ni IoC online sin endpoints, ni IA integrada sin binario nativo.
10. Publicar siempre el **SHA-256 real** del APK en cada release.

---

## 8. Veredicto

APK **coherente con un prototipo funcional de app defensiva** (monitorización de uso de red, análisis de permisos, cuarentena, verificador de hash, VPN local, widgets), **sin telemetría ni exfiltración detectada**.

Pero **no es apto para distribución pública** por tres motivos: build de depuración con clave pública (C-01), servicio de accesibilidad con captura de texto y teclas (C-02) y feature de IA anunciada que no funciona en el APK entregado (C-03).

*Revisión estática: no se ejecutó el binario ni se realizó análisis dinámico.*
