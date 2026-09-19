<div align="center">

██████╗██╗ ██╗██████╗ ███████╗██████╗ █████╗ ██████╗ ███████╗███╗ ██╗████████╗ ██╔════╝╚██╗ ██╔╝██╔══██╗██╔════╝██╔══██╗██╔══██╗██╔════╝ ██╔════╝████╗ ██║╚══██╔══╝ ██║ ╚████╔╝ ██████╔╝█████╗ ██████╔╝███████║██║ ███╗█████╗ ██╔██╗ ██║ ██║ ██║ ╚██╔╝ ██╔══██╗██╔══╝ ██╔══██╗██╔══██║██║ ██║██╔══╝ ██║╚██╗██║ ██║ ╚██████╗ ██║ ██████╔╝███████╗██║ ██║██║ ██║╚██████╔╝███████╗██║ ╚████║ ██║ ╚═════╝ ╚═╝ ╚═════╝ ╚══════╝╚═╝ ╚═╝╚═╝ ╚═╝ ╚═════╝ ╚══════╝╚═╝ ╚═══╝ ╚═╝

### 🛡️ Centro de ciberdefensa local para Android

Monitoriza. Analiza. Protege. Sin que un solo byte salga de tu teléfono.

Release
Android-00FF88?style=flat-square&labelColor=03060A)
Telemetría
MITRE ATT&CK
OWASP
Licencia

⬇️ Descargar APK ·
🌐 Web ·
📦 Releases ·
🐛 Reportar bug

</div>

📖 ¿Qué es CyberAgent?
CyberAgent no es un antivirus más. Es una consola de defensa que corre íntegramente en el
dispositivo: monitoriza llamadas, red, permisos y ficheros en tiempo real, y clasifica cada hallazgo
con estándares de industria (CVSS v3, MITRE ATT&CK, OWASP Top 10).

Diseñado bajo filosofía Blue Team: todo el análisis es defensivo, local y auditable.
No hay backend, no hay cuenta, no hay telemetría. Si el dato no sale del teléfono,
no hay superficie que proteger.

✨ Módulos
#	Módulo	Qué hace	Categoría
01	🛡️ Panel de Seguridad Ejecutivo	Dashboard en vivo, contador de alertas, modo pánico, corte de internet por app	Core
02	📞 Blindaje de Llamadas y SMS	Listas blancas/negras, heurística anti-spam, detección de spoofing, No Molestar programable	Protección
03	🌐 Monitor de Uso de Internet	Consumo por app, picos anómalos, bloqueo de salida de red por aplicación	Red
04	🦠 Centro de Cuarentena	Aísla apps sospechosas, revoca permisos peligrosos, histórico con cadena de custodia	Amenazas
05	🔬 Verificador Forense	Hash SHA-256, firmas locales, identificación de IoCs sin salir del dispositivo	Forense
06	📶 Análisis de Seguridad Wi-Fi	Cifrado débil, redes abiertas, ARP anómalo, sospecha de man-in-the-middle	Red
07	📱 Análisis de Aplicaciones	Auditoría de permisos, comportamiento anómalo, score de riesgo explicable	Apps
08	🔥 Cortafuegos VPN Local	Firewall como servicio VPN en loopback: intercepta y filtra tráfico saliente	Firewall
09	📊 Métricas & Widgets	Widgets 4×2 y 2×2 en vivo, gráficas e histórico diario de eventos	Dashboard
🤖 IA defensiva con ciclo ReAct
Un agente especializado en ciberseguridad defensiva que razona, planifica y actúa
siguiendo el patrón Reason + Act. Corre offline, en el propio dispositivo.

cyber@agent:~$ analyze_logs --last 24h
→ Analizando 2.847 eventos de sistema...
→ Correlacionando con baseline de comportamiento
⚠ ANOMALÍA: com.unknown.app — exfiltración potencial
  Severidad: ALTA · CVSS 8.1 · MITRE ATT&CK T1422
cyber@agent:~$ review_code app.dex --lang java
→ Análisis estático (AST) completado
⚠ CVE-2024-0031 — permisos excesivos · OWASP M6
cyber@agent:~$ scan_ports 192.168.1.0/24
→ Operación de red. ¿Confirmas autorización del objetivo? [s/N]
> s
→ Autorización registrada · barriendo 254 hosts...
✓ 12 hosts • 0 puertos críticos expuestos
Capacidades

✅ Análisis de logs con detección de anomalías en tiempo real
✅ Revisión de código fuente frente a OWASP Top 10
✅ Consulta de IoCs en bases locales y APIs opcionales (VirusTotal / AbuseIPDB)
✅ Clasificación por severidad CVSS v3 con referencias MITRE ATT&CK
✅ Memoria RAG — conserva contexto entre sesiones
✅ Guardrails estrictos: jamás genera código malicioso ni asistencia ofensiva
🚀 Instalación
Descarga directa
# APK v2.0 — Android 8.0+ (API 26) · ~8 MB
curl -LO https://github.com/aurenox-global/cyberagente/releases/download/v2.0.0.1/CyberAgent_2.0.apk
O desde el navegador: ⬇️ Descargar CyberAgent_2.0.apk

Pasos
Descarga el APK e instálalo (habilita “Instalar apps de origen desconocido” si el sistema lo pide).
Concede permisos — servicio de accesibilidad y VPN local son los dos únicos accesos necesarios.
Configura tu perfil — sensibilidad de alertas (baja / media / alta) y listas de confianza.
Listo — protección continua 24/7 con arranque automático y widgets de estado.
⚠️ Verifica siempre el hash SHA-256 del APK antes de instalar. El hash del release se publica
en cada entrada de Releases.

Compilar desde fuente
git clone https://github.com/aurenox-global/cyberagente.git
cd cyberagente
./gradlew assembleRelease        # APK de release
./gradlew installDebug           # instalar en dispositivo conectado vía adb
Requisitos: Android SDK (API 26+), JDK 17, Gradle 8+.

🔒 Privacidad
Principio	Implementación
🏠 100% local	Ningún dato abandona el dispositivo. Sin telemetría, servidores ni analíticas.
🔑 Sin credenciales almacenadas	Las claves API opcionales se guardan cifradas en el keystore del dispositivo.
⚖️ Uso ético	Guardrails en la IA que impiden generar código malicioso o asistir en actividades ilegales.
🛡️ Confirmación humana	Toda operación de red (escaneos, consultas externas) exige autorización explícita.
🔄 Arranque seguro	El servicio de monitorización se restaura tras cada reinicio con un BroadcastReceiver protegido.
📋 Estándares	CVSS v3, MITRE ATT&CK y OWASP Top 10 integrados en todos los análisis.
