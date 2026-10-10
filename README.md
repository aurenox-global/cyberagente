<div align="center">

# 🛡️ CyberAgent

**Consola de ciberdefensa 100 % local para Android.**
Bloquea la red de apps concretas, filtra SMS y llamadas, audita permisos, detecta phishing,
vigila la Wi-Fi y genera informes forenses — **todo en el dispositivo**.

[![Release](https://img.shields.io/github/v/release/aurenox-global/cyberagente?style=flat-square&color=3FE0A6&labelColor=070B0A)](https://github.com/aurenox-global/cyberagente/releases/latest)
[![Android](https://img.shields.io/badge/android-8.0%2B%20(API%2026)-9AA8A2?style=flat-square&labelColor=070B0A)](#-instalación)
[![Telemetría](https://img.shields.io/badge/telemetría-0-4ADE80?style=flat-square&labelColor=070B0A)](#-privacidad)
[![Licencia](https://img.shields.io/badge/licencia-open%20source-5AA9FF?style=flat-square&labelColor=070B0A)](./LICENSE)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0-9B8CFF?style=flat-square&labelColor=070B0A)](https://kotlinlang.org)

🌐 **[aurenox-global.github.io/cyberagente](https://aurenox-global.github.io/cyberagente/)**

<sub>Creado por **Andrés Mag** (Cuba)</sub>

</div>

---

## 📖 ¿Qué es CyberAgent?

No es un antivirus más: es una **consola de defensa** que vive en el teléfono y trabaja
**sin nube**. Bloquea la red de apps concretas, filtra SMS y llamadas, audita permisos,
detecta enlaces de phishing, vigila la red Wi-Fi y genera informes forenses.

Todo se ejecuta **en el dispositivo**. Sin telemetría, sin cuentas, sin SDK de terceros.

---

## 🧩 16 defensas (todas funcionales)

| # | Defensa | Qué hace de verdad |
|---|---------|--------------------|
| 01 | **Panel de seguridad** | Estado real, accesos concedidos, acciones rápidas y últimos eventos |
| 02 | **Cortafuegos VPN** | Enruta el tráfico de las apps marcadas al túnel local y lo **descarta** (IPv4 + IPv6) |
| 03 | **Blindaje de llamadas** | `CallScreeningService`: rechaza lista negra, ocultos y no verificados (STIR/SHAKEN) |
| 04 | **Blindaje de SMS** | Analiza cada SMS y **descarta** los fraudulentos cuando es la app de SMS |
| 05 | **Monitor de Internet** | Consumo por app del día y de 7 días vía `NetworkStatsManager` |
| 06 | **Centro de cuarentena** | Aísla (corta la red), registra y ofrece desinstalar |
| 07 | **Verificador forense** | SHA-256 de ficheros + comprobación contra la base local de IoC |
| 08 | **Análisis de Wi-Fi** | Cifrado, redes abiertas y señales de suplantación |
| 09 | **Auditoría de apps** | Score de riesgo explicable: permisos, targetSdk, procedencia, firma y postura |
| 10 | **Widget Panel 4×4** | Escaneos, apps analizadas, cuarentena, alertas 24 h y apps con tráfico reciente |
| 11 | **Guardián de enlaces** | Anti-phishing en SMS, notificaciones y "Compartir" (acortadores, punycode, marcas) |
| 12 | **Escaneo por huella** | SHA-256 del APK y del certificado + VirusTotal opcional respetando cuota |
| 13 | **Vigilante de permisos** | Avisa si una actualización añade permisos sensibles |
| 14 | **Anti-superposición** | Detecta una ventana dibujada sobre otra app (pantalla falsa bancaria) |
| 15 | **Bloqueo por horario** | Perfil noche: dentro de la franja solo las apps de confianza tienen red |
| 16 | **Confianza + IoC** | Recomendaciones por app y base de dominios actualizable por HTTPS |

**Extras:** asistente defensivo local (ciclo *Observar → Analizar → Recomendar* con MITRE
ATT&CK), autodiagnóstico de 10 comprobaciones, informe HTML forense, exportación JSON y
copia de seguridad cifrada (AES-256-GCM + PBKDF2).

---

## 🆕 Novedades de la v2.6.0

- **Nuevo look (soft dark + menta):** tarjetas redondeadas, fondo con resplandor y tipografía
  mixta (títulos en sans, datos en monoespaciada).
- **Panel con pestañas de navegador:** *Inicio · Módulos · Acciones · Eventos*. La cabecera y
  las pestañas quedan **fijas**; solo hace scroll el contenido.
- **Iconos vectoriales** propios (trazo 24×24 tintable): fuera los emoji de la interfaz.
- **Motor de riesgo rebalanceado (menos falsos positivos):** permisos *con tope* + señales de
  postura (sideload, APK debuggable, targetSdk antiguo, combinaciones anómalas, app oculta,
  suplantación de marca y banca) − amortiguadores (tienda oficial, targetSdk moderno, firma de
  confianza). Antes, cualquier app con muchos permisos legítimos llegaba a «100/100 crítico».
- **Vigilante de firma:** avisa si una actualización **cambia el firmante** (repackage).
- **Reputación de firma** y **whitelist por firma** (marcar un desarrollador de confianza).
- **IoC integrado en el score:** si el hash del APK está en la lista, pasa directamente a crítico.
- **Correcciones:** RDAP fuera del hilo principal, cortafuegos sin avisos falsos, Wi-Fi
  auditable en Android 8–12 y filas de botones que ya no parten el texto.

---

## 🚀 Instalación

```bash
# APK v2.6.0 — Android 8.0+ (API 26) · ~4,5 MB
curl -LO https://github.com/aurenox-global/cyberagente/releases/download/v2.6.0/CyberAgent_2.6.0.apk

# Verifica el hash antes de instalar
sha256sum CyberAgent_2.6.0.apk
```

```
86abd12641b0279aa29dc5a28422c5fd73c43f1b056d426ef3c8d5df34d1dbcb
```

> Descarga oficial: [GitHub Releases](https://github.com/aurenox-global/cyberagente/releases/tag/v2.6.0).

Se instala **encima** de cualquier versión previa 2.x (misma firma). Si vienes de la v2.0,
desinstala primero: cambió el certificado.

### Verificación de la descarga

```
Paquete      : com.cyberagent.app
Versión      : 2.6.0 (versionCode 15)
minSdk       : 26 · targetSdk/compileSdk 35
Firma        : CN=CyberAgent Release, RSA 4096, esquemas v1 + v2 + v3
debuggable   : false
cleartext    : false (solo HTTPS)
allowBackup  : false
Telemetría   : 0 SDK de analítica / crash reporting
```

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

Necesitas el **SDK de Android (API 35)** y **JDK 17**.

```bash
git clone https://github.com/aurenox-global/cyberagente.git
cd cyberagente

# Indica tu SDK
echo "sdk.dir=$ANDROID_HOME" > local.properties

# Build de release (necesita el keystore; ver gradle.properties)
./gradlew assembleRelease
```

Sin `gradlew` en el repo, usa tu Gradle 8.x:

```bash
gradle assembleRelease
```

La firma de release se configura en `app/build.gradle.kts` (bloque `signingConfigs`), leyendo
las propiedades `cyberagent.storeFile`, `cyberagent.storePassword`, `cyberagent.keyAlias` y
`cyberagent.keyPassword` de `gradle.properties` o de variables de entorno
`CYBERAGENT_STORE_PASSWORD`, `CYBERAGENT_KEY_ALIAS`, `CYBERAGENT_KEY_PASSWORD`.

---

## 🏗️ Arquitectura

```
app/src/main/java/com/cyberagent/app/
├── core/        Motor defensivo (análisis, cifrado, IoC, red, riesgo…)
├── service/     Servicios: VPN, monitor, accesibilidad, notificaciones, escaneo
├── receiver/    Receptores: SMS, MMS, arranque, cambios de paquete
├── widget/      Widgets de pantalla de inicio
├── ui/          Sistema de diseño (Ui, IconView, FlowRow, gráficas)
└── *Activity.kt Pantallas (panel, apps, cuarentena, forense, ajustes…)
```

Puntos clave del diseño:

- **Todo lo pesado va en segundo plano** (`core/Bg.kt`): leer estadísticas de red, auditar apps
  o descifrar historial nunca bloquean la interfaz.
- **Datos cifrados en reposo:** los campos sensibles de SQLite y todos los ajustes se guardan
  con **AES-256-GCM** y una clave **no exportable** del Android Keystore (`core/Crypto.kt`).
- **Sin dependencias de terceros en runtime:** solo AndroidX. Nada de analítica ni crash reporting.
- **Cada defensa es comprobable** desde el autodiagnóstico (`core/SelfTest.kt`).

---

## 🔐 Privacidad

| | |
|---|---|
| **100 % local** | Ningún dato abandona el dispositivo. Sin telemetría ni servidores. |
| **Sin cuentas** | No hay registro ni login. |
| **Cifrado** | Historial y ajustes con AES-256-GCM + Android Keystore; copias con PBKDF2-HMAC-SHA256. |
| **Red solo HTTPS** | `cleartextTrafficPermitted=false`. Las consultas externas (opcionales) exigen autorización. |
| **Sin backup en la nube** | `allowBackup=false` y reglas de extracción que excluyen datos. |

---

## 🔑 Permisos y por qué

| Permiso | Motivo |
|---------|--------|
| `PACKAGE_USAGE_STATS` | Medir consumo de red por app (nunca se envía a ningún sitio) |
| `RECEIVE_SMS` / `READ_SMS` | Filtrar SMS fraudulentos (solo si eres la app de SMS) |
| `READ_PHONE_STATE` / `READ_CALL_LOG` / `READ_CONTACTS` | Blindaje de llamadas y listas |
| `BIND_VPN_SERVICE` | Cortafuegos local por aplicación |
| `BIND_ACCESSIBILITY_SERVICE` | Guardia de instalación (config **mínima**: sin leer contenido) |
| `NEARBY_WIFI_DEVICES` (y `ACCESS_FINE_LOCATION` ≤ API 32) | Analizar la seguridad de la Wi-Fi |
| `QUERY_ALL_PACKAGES` | Auditar las apps instaladas |

No se usan `KILL_BACKGROUND_PROCESSES`, ni `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, ni
`HIDE_OVERLAY_WINDOWS`: se sustituyeron por un servicio en primer plano bien declarado.

---

## ⚡ Rendimiento

- Cachés con TTL para estadísticas de red y comprobación de acceso al uso.
- Clave del Keystore cacheada (antes se abría en cada cifrado/descifrado).
- Listas y agregados calculados en hilo de fondo; la interfaz nunca se congela.
- Widgets con `goAsync()` para no bloquear el lanzador.

---

## 🤝 Contribuir

Issues y PRs son bienvenidos. Si tocas el motor defensivo, **añade una comprobación al
autodiagnóstico** (`core/SelfTest.kt`): hay que poder demostrar que la defensa funciona.

---

## ⚠️ Aviso legal

Herramienta de defensa para tu propio dispositivo o con autorización expresa de su dueño. No
incluye capacidades ofensivas y el asistente rechaza generar contenido malicioso.
La VPN + accesibilidad en la misma app no es publicable en Play: la distribución es directa.

---

## 📄 Licencia

Open source (ver [`LICENSE`](./LICENSE)).

<div align="center"><sub><code>v2.6.0</code> · Android 8.0+ · 100 % local · 0 telemetría · <b>creado por Andrés Mag (Cuba)</b></sub></div>
