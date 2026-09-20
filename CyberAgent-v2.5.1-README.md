# 🛡️ CyberAgent

### Consola de ciberdefensa local para Android

[![Release](https://img.shields.io/badge/release-v2.5.1-00FFD6?style=flat-square&labelColor=03060A)](https://github.com/aurenox-global/cyberagente/releases)
[![Android](https://img.shields.io/badge/android-8.0%2B%20(API%2026)-8FB3BF?style=flat-square&labelColor=03060A)](#-instalación)
[![Telemetría](https://img.shields.io/badge/telemetría-0-00FF88?style=flat-square&labelColor=03060A)](#-privacidad)
[![Licencia](https://img.shields.io/badge/licencia-open%20source-3DA9FF?style=flat-square&labelColor=03060A)](./LICENSE)

**Creado por Andrés Mag (Cuba)** · Todos los derechos reservados.

---

## 📖 ¿Qué es CyberAgent?

No es un antivirus más: es una **consola de defensa** que vive en el teléfono y trabaja
**sin nube**. Bloquea la red de apps concretas, filtra SMS y llamadas, audita permisos,
detecta enlaces de phishing, vigila la red Wi-Fi y genera informes forenses.

Todo se ejecuta **en el dispositivo**. Sin telemetría, sin cuentas, sin SDK de terceros.

---

## ✨ 16 defensas (todas funcionales)

| # | Defensa | Qué hace de verdad |
|---|---------|--------------------|
| 01 | **Panel de seguridad** | Estado real, accesos concedidos, acciones rápidas, últimos eventos |
| 02 | **Cortafuegos por app (VPN)** | Enruta el tráfico de las apps marcadas al túnel y lo **descarta** (IPv4 + IPv6) |
| 03 | **Blindaje de llamadas** | `CallScreeningService`: rechaza lista negra, ocultos y no verificados (STIR/SHAKEN) |
| 04 | **Blindaje de SMS** | Analiza cada SMS y **descarta** los fraudulentos cuando es la app de SMS |
| 05 | **Monitor de Internet** | Consumo por app del día y de 7 días vía `NetworkStatsManager` |
| 06 | **Centro de cuarentena** | Aísla (corta la red), registra y ofrece desinstalar |
| 07 | **Verificador forense** | SHA-256 de ficheros + comprobación contra la base local de IoC |
| 08 | **Análisis de Wi-Fi** | Cifrado, redes abiertas, señales de suplantación |
| 09 | **Auditoría de apps** | Score de riesgo explicable: permisos, targetSdk, procedencia, firma |
| 10 | **Widget Panel 4×4** | Escaneos, apps analizadas, cuarentena, alertas 24 h y apps con tráfico reciente |
| 11 | **Guardián de enlaces** | Anti-phishing en SMS, notificaciones y "Compartir" (acortadores, punycode, marcas) |
| 12 | **Escaneo por huella** | SHA-256 del APK y del certificado + VirusTotal opcional respetando cuota |
| 13 | **Vigilante de permisos** | Avisa si una actualización añade permisos sensibles |
| 14 | **Anti-superposición** | Detecta una ventana dibujada sobre otra app (pantalla falsa bancaria) |
| 15 | **Bloqueo por horario** | Perfil noche: dentro de la franja solo las apps de confianza tienen red |
| 16 | **Panel de confianza + IoC** | Recomendaciones por app y base de dominios actualizable por HTTPS |

Extra: **asistente defensivo local** (ciclo Observar → Analizar → Recomendar con severidad y
referencia MITRE ATT&CK), **autodiagnóstico de 10 comprobaciones**, **informe HTML forense**,
**exportación JSON** y **copia de seguridad cifrada** (AES-256-GCM + PBKDF2).

### Novedades de la v2.5.1

- **Widget 4×4** con escaneos, apps analizadas, cuarentena, alertas 24 h y **apps con tráfico
  en los últimos 15 minutos** + botón de escaneo inmediato.
- **Guardián de enlaces** como destino de "Compartir" y sobre las notificaciones de cualquier app.
- **Escaneo en lote por huella** de todas las apps instaladas.
- **Vigilante de permisos** (permisos que cambian tras una actualización).
- **Anti-superposición** (opcional, requiere accesibilidad con lectura de ventanas).
- **Bloqueo por horario** y **vigilante de red** (portal cautivo, cambio de DNS, Wi-Fi abierta).
- **Panel de confianza** por aplicación con acciones concretas.
- **Informe HTML forense** exportable, con el crédito del autor.
- **Base IoC actualizable** con verificación por huella SHA-256.
- Rendimiento: todo lo pesado (red, apps, historial) pasó a **segundo plano** con caché.

---

## 🚀 Instalación

```bash
# APK v2.5.1 — Android 8.0+ (API 26) · ~4,5 MB
curl -LO https://serious-favourite-manhattan-buying.trycloudflare.com/CyberAgent_2.5.1.apk

# Verifica el hash antes de instalar
sha256sum CyberAgent_2.5.1.apk
```

```
bf975e5184171fead3fb82e8429de5d140cf434c85e47dedaf0a250b24a35013
```

> El enlace directo es temporal (túnel). La release oficial vive en
> [GitHub Releases](https://github.com/aurenox-global/cyberagente/releases).

### Verificación de la descarga

```
Paquete      : com.cyberagent.app
Versión      : 2.5.1 (versionCode 14)
minSdk       : 26 · targetSdk/compileSdk 35
Firma        : CN=CyberAgent Release, RSA 4096, esquemas v1 + v2 + v3
debuggable   : false
cleartext    : false (solo HTTPS)
allowBackup  : false
Telemetría   : 0 SDK de analítica / crash reporting
```

Se instala **encima** de cualquier versión previa 2.x (misma firma). Si vienes de la v2.0,
desinstala primero: cambió el certificado.

### Primer arranque: el asistente de accesos

Android no permite pedir todos los permisos de golpe. La app abre un **asistente** que los
recorre en orden y vuelve solo al siguiente paso:

1. Notificaciones y Wi-Fi · 2. Teléfono y llamadas · 3. SMS/MMS · 4. **Acceso al uso** ·
5. Rol de filtrado de llamadas · 6. App de SMS predeterminada · 7. Accesibilidad (guardia de
instalador) · 8. No Molestar · 9. **Batería sin restricciones** · 10. Acceso a notificaciones ·
11. Consentimiento VPN · 12. Anti-superposición (opcional)

Cada paso explica **para qué sirve**. Los opcionales no fuerzan nada.

---

## 🔨 Compilar desde el código

```bash
# Necesita el SDK de Android (API 35) y JDK 17
./gradlew assembleRelease
```

Sin `gradlew` en el repo, usa tu Gradle 8.x:

```bash
gradle assembleRelease
```

La firma se toma de `gradle.properties` (`cyberagent.storeFile`, …) o de las variables
`CYBERAGENT_STORE_PASSWORD`, `CYBERAGENT_KEY_ALIAS`, `CYBERAGENT_KEY_PASSWORD`.

---

## 🔒 Privacidad

- **Nada sale del dispositivo** salvo lo que tú autorices explícitamente.
- Los eventos (**título y detalle**) se guardan **cifrados** (AES-256-GCM, clave en el
  Android Keystore, no exportable).
- Los ajustes y listas de bloqueo también se cifran antes de escribirse.
- Las consultas externas (VirusTotal, RDAP) son **opcionales**, por **HTTPS obligatorio**, y
  requieren tu clave o tu activación en Ajustes.
- `allowBackup=false`: sin copia en la nube. La copia de seguridad cifrada la decides tú, con
  tu propia contraseña.
- El guardián de notificaciones **no guarda el texto** de los mensajes: solo el dominio
  sospechoso y la app de origen.

---

## 🧱 Stack

- **Kotlin** + **AppCompat puro** (UI 100 % programática, sin Material inflado).
- `CallScreeningService`, `NotificationListenerService`, dos `AccessibilityService`
  (uno mínimo y uno opcional), `VpnService`, `JobScheduler`, `NetworkStatsManager`,
  `AppWidgetProvider`.
- **SQLite** propio (sin Room) con cifrado a nivel de campo.
- Cero dependencias de red: solo `androidx` + `material` para recursos.

---

## 📁 Estructura del repositorio

```
app/src/main/java/com/cyberagent/app/
├── MainActivity.kt                 · panel + accesos + módulos
├── PermissionsActivity.kt           · asistente de accesos guiado
├── AlertsActivity.kt                · historial con filtros
├── SelfTestActivity.kt              · autodiagnóstico + comprobar enlace
├── TrustAdvisorActivity.kt          · panel de confianza por app
├── ShareInspectActivity.kt          · destino de "Compartir"
├── AppListActivity.kt / AppDetailActivity.kt
├── CallSmsShieldActivity.kt · InternetUsageActivity.kt · …
├── core/
│   ├── Crypto.kt (AES-GCM + Keystore) · Db.kt · Prefs.kt
│   ├── FirewallBridge / QuarantineManager / PanicModeManager / ScheduleGuard
│   ├── NetworkStatsReader · TrafficWatch · NetGuard · WifiAnalyzer
│   ├── LinkInspector · DnsFilter · IocDatabase · IocUpdater · OnlineIoc
│   ├── AppAnalyzer · ApkReputation · BatchScan · PermissionWatcher
│   ├── TrustAdvisor · Watchdog · Exporter · HtmlReport · SelfTest
│   └── AutoDefense · Notify · CrashLog
├── service/  FirewallVpnService · SecurityMonitorService · CallGuardService
│            SecurityAccessibilityService · OverlayGuardService · ScanJobService
│            NotifGuardService
├── receiver/ Boot · PackageChange · SMS (monitor y deliver) · MMS
└── widget/   Panel 4×4 · estado · uso de hoy · red 7 días
```

---

## ⚠️ Límites reales (sin vender humo)

- Sin **root** ni **device owner** no se puede suspender ni desinstalar una app a la fuerza.
- La detección de fraude (SMS, enlaces, score de apps) es **heurística**: hay falsos
  positivos y falsos negativos. No hay motor antivirus ni ML local.
- El cortafuegos **no inspecciona el contenido** de los paquetes: bloquea apps, no dominios.
  El filtrado DNS del túnel solo afecta a las apps ya bloqueadas.
- El túnel no reenvía tráfico (no es un VPN completo). El filtrado de dominios de todo el
  móvil exigiría una pila TCP/IP propia: proyecto aparte.
- La defensa automática depende del **JobScheduler**: en fabricantes con ahorro agresivo
  (Xiaomi, Huawei…) hay que quitarle restricciones de batería (paso 9 del asistente).
- **VPN + accesibilidad en la misma app no es publicable en Play.** Distribución fuera de
  tienda, para uso propio o instalación manual asistida.

---

## 🛠️ Cambios respecto a la v2.0

| v2.0 | v2.5.1 |
|------|--------|
| Firma de depuración | Keystore propio RSA 4096, v1+v2+v3, no debuggable |
| Accesibilidad con acceso total | Dos servicios: mínimo (sin leer contenido) + opcional anti-overlay |
| "IA" que no arrancaba | Asistente local determinista con MITRE ATT&CK |
| Tráfico en claro | Solo HTTPS (`cleartextTrafficPermitted=false`) |
| Datos en claro | AES-256-GCM + Android Keystore |
| Permisos raros | Eliminados; accesos guiados por asistente |
| 9 módulos anunciados | 16 defensas verificadas + autodiagnóstico de 10 pruebas |
| Sin widgets | 4 widgets, incluido el panel 4×4 |
| Sin informes | Informe JSON, informe HTML forense y copia cifrada |
| Bloqueo por app | + IPv6, DNS NXDOMAIN, guardián de enlaces, permisos, horario y red |

---

## 🤝 Contribuir

Issues y PRs son bienvenidos. Si vas a tocar el motor defensivo, añade una comprobación al
**autodiagnóstico** (`core/SelfTest.kt`): hay que poder demostrar que la defensa funciona.

---

## ⚠️ Aviso legal

Herramienta de defensa para tu propio dispositivo o con autorización expresa de su dueño.
No incluye capacidades ofensivas y el asistente rechaza generar contenido malicioso.

---

## 📄 Licencia

Open source (ver [`LICENSE`](./LICENSE)).

`v2.5.1` · Android 8.0+ · 100 % local · 0 telemetría · **creado por Andrés Mag (Cuba)**
