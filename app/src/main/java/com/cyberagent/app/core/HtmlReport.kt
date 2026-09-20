package com.cyberagent.app.core

import android.content.Context

/**
 * Informe forense en HTML, autocontenido y con estilo oscuro (mismo look que la
 * app). Sirve para revisar el estado de un móvil sin tener que ir pantalla por
 * pantalla: resumen, módulos, apps de riesgo, cuarentena, permisos cambiados,
 * consejos y el historial de eventos.
 */
object HtmlReport {

    fun build(ctx: Context): String {
        val now = System.currentTimeMillis()
        val alerts = Db.alerts(limit = 200)
        val high = alerts.count { it.severity == "ALTA" || it.severity == "CRITICA" }
        val apps = try {
            AppAnalyzer.analyzeAll(ctx)
        } catch (t: Throwable) {
            emptyList()
        }
        val risky = apps.filter { it.level == RiskLevel.ALTO || it.level == RiskLevel.CRITICO }.take(20)
        val advice = try {
            TrustAdvisor.advise(ctx)
        } catch (t: Throwable) {
            emptyList()
        }

        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"utf-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        sb.append("<title>Informe CyberAgent</title><style>")
        sb.append("body{margin:0;background:#03060A;color:#E8FFF9;font-family:ui-monospace,Menlo,Consolas,monospace;font-size:14px;line-height:1.5}")
        sb.append("header{padding:20px 18px;border-bottom:1px solid #12303C}")
        sb.append("h1{font-size:18px;color:#00FFD6;margin:0 0 4px;letter-spacing:.06em}")
        sb.append("h2{font-size:14px;color:#00FFD6;margin:24px 18px 8px;letter-spacing:.06em}")
        sb.append(".wrap{padding:0 18px 40px}")
        sb.append(".card{background:#0A141F;border:1px solid #12303C;border-radius:10px;padding:12px 14px;margin:8px 0}")
        sb.append(".grid{display:flex;flex-wrap:wrap;gap:8px}")
        sb.append(".kpi{flex:1 1 130px;background:#071019;border:1px solid #12303C;border-radius:10px;padding:10px 12px}")
        sb.append(".kpi b{display:block;font-size:22px;color:#00FFD6}")
        sb.append(".kpi span{font-size:11px;color:#8FB3BF}")
        sb.append(".ok{color:#00FF88}.warn{color:#FFC44D}.bad{color:#FF3B5C}.mut{color:#4A6B77}")
        sb.append("table{width:100%;border-collapse:collapse}")
        sb.append("td,th{border-bottom:1px solid #12303C;padding:6px 4px;text-align:left;vertical-align:top;font-size:12.5px}")
        sb.append("th{color:#8FB3BF;font-weight:normal}")
        sb.append(".tag{display:inline-block;border:1px solid #12303C;border-radius:20px;padding:1px 8px;font-size:11px}")
        sb.append("</style></head><body>")

        sb.append("<header><h1>CYBERAGENT · INFORME DE ESTADO</h1>")
        sb.append("<div class=\"mut\">Generado: ").append(esc(Ui_time(now)))
        sb.append(" · Versión ").append(esc(com.cyberagent.app.BuildConfig.VERSION_NAME))
        sb.append(" · Android ").append(android.os.Build.VERSION.RELEASE)
        sb.append(" · ").append(esc(android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL))
        sb.append("</div>")
        sb.append("<div class=\"mut\">Base IoC v").append(esc(IocDatabase.version))
        sb.append(" · entradas: ").append(IocDatabase.size())
        sb.append(" (fábrica ").append(IocDatabase.factorySize())
        .append(" + local ").append(IocDatabase.localSize()).append(")</div></header>")

        sb.append("<div class=\"wrap\">")

        // KPIs
        sb.append("<h2>RESUMEN</h2><div class=\"grid\">")
        sb.append(kpi(alerts.size.toString(), "eventos registrados"))
        sb.append(kpi(high.toString(), "de alta severidad"))
        sb.append(kpi(apps.size.toString(), "apps analizadas"))
        sb.append(kpi(risky.size.toString(), "apps de riesgo alto"))
        sb.append(kpi(Prefs.quarantine().size.toString(), "en cuarentena"))
        sb.append(kpi(Prefs.blockedPackages().size.toString(), "apps sin red"))
        sb.append("</div>")

        // Módulos
        sb.append("<h2>MÓDULOS</h2><div class=\"card\"><table>")
        row(sb, "Cortafuegos VPN", Prefs.firewallEnabled, "apps sin red: " + Prefs.blockedPackages().size)
        row(sb, "Túnel establecido", com.cyberagent.app.service.FirewallVpnService.isTunnelRunning(), "")
        row(sb, "Acceso al uso (red)", Perms.usageAccess(ctx), "")
        row(sb, "Blindaje SMS activo", Prefs.smsShieldEnabled, "")
        row(sb, "App de SMS predeterminada", com.cyberagent.app.receiver.SmsDeliverReceiver.isDefaultSmsApp(ctx), "")
        row(sb, "Filtro de llamadas", Prefs.callShieldEnabled, "rol: " + com.cyberagent.app.service.CallGuardService.hasRole(ctx))
        row(sb, "Accesibilidad (instalador)", Perms.accessibilityEnabled(ctx), "")
        row(sb, "Guardián de enlaces", Prefs.notifGuardEnabled, "acceso: " + com.cyberagent.app.service.NotifGuardService.isConnected(ctx))
        row(sb, "Defensa automática", Prefs.autoDefenseEnabled, "cada " + Prefs.autoScanIntervalMin + " min")
        row(sb, "Bloqueo por horario", ScheduleGuard.isActiveNow(), ScheduleGuard.describe())
        row(sb, "Modo pánico", Prefs.panicMode, "apps de confianza: " + Prefs.trustedPackages().size)
        sb.append("</table></div>")

        // Apps de riesgo
        sb.append("<h2>APPS DE RIESGO ALTO</h2><div class=\"card\"><table>")
        sb.append("<tr><th>App</th><th>Score</th><th>Motivos</th></tr>")
        if (risky.isEmpty()) {
            sb.append("<tr><td colspan=\"3\" class=\"ok\">Ninguna app supera el umbral de riesgo alto.</td></tr>")
        } else {
            for (a in risky) {
                sb.append("<tr><td>").append(esc(a.label)).append("<div class=\"mut\">")
                    .append(esc(a.packageName)).append("</div></td><td class=\"warn\">")
                    .append(a.score).append("/100</td><td>")
                    .append(esc(a.reasons.take(3).joinToString(" · "))).append("</td></tr>")
            }
        }
        sb.append("</table></div>")

        // Consejos
        sb.append("<h2>RECOMENDACIONES</h2><div class=\"card\"><table>")
        if (advice.isEmpty()) {
            sb.append("<tr><td class=\"ok\">Sin recomendaciones: nada destacable.</td></tr>")
        } else {
            for (ad in advice.take(25)) {
                val cls = if (ad.severity == "ALTA") "bad" else "warn"
                sb.append("<tr><td>").append(esc(ad.label))
                    .append("<div class=\"mut\">").append(esc(ad.pkg)).append("</div></td>")
                    .append("<td class=\"").append(cls).append("\">").append(esc(ad.severity)).append("</td>")
                    .append("<td>").append(esc(ad.problem)).append("<div class=\"mut\">")
                    .append(esc(ad.suggestion)).append("</div></td></tr>")
            }
        }
        sb.append("</table></div>")

        // Cuarentena
        val q = Prefs.quarantine()
        sb.append("<h2>CUARENTENA (").append(q.size).append(")</h2><div class=\"card\">")
        if (q.isEmpty()) sb.append("<span class=\"ok\">Vacía.</span>")
        else for (p in q) sb.append("<div>· ").append(esc(p)).append("</div>")
        sb.append("</div>")

        // Historial
        sb.append("<h2>HISTORIAL DE EVENTOS (").append(alerts.size).append(")</h2><div class=\"card\"><table>")
        sb.append("<tr><th>Fecha</th><th>Sev.</th><th>Módulo</th><th>Evento</th></tr>")
        for (a in alerts.take(120)) {
            val cls = when (a.severity) {
                "CRITICA", "ALTA" -> "bad"
                "MEDIA" -> "warn"
                "BAJA" -> "mut"
                else -> "ok"
            }
            sb.append("<tr><td class=\"mut\">").append(esc(Ui_time(a.ts))).append("</td>")
                .append("<td class=\"").append(cls).append("\"><span class=\"tag\">")
                .append(esc(a.severity)).append("</span></td>")
                .append("<td>").append(esc(a.module)).append("</td>")
                .append("<td>").append(esc(a.title))
            if (a.detail.isNotEmpty()) {
                sb.append("<div class=\"mut\">").append(esc(a.detail.take(300))).append("</div>")
            }
            sb.append("</td></tr>")
        }
        sb.append("</table></div>")

        sb.append("<div class=\"card mut\">Informe generado localmente por CyberAgent. " +
            "Ningún dato sale del dispositivo salvo que tú lo compartas.<br>" +
            "<b>Creado por Andrés Mag (Cuba)</b></div>")
        sb.append("</div></body></html>")
        return sb.toString()
    }

    private fun kpi(value: String, label: String) =
        "<div class=\"kpi\"><b>" + esc(value) + "</b><span>" + esc(label) + "</span></div>"

    private fun row(sb: StringBuilder, name: String, ok: Boolean, extra: String) {
        sb.append("<tr><td>").append(esc(name)).append("</td><td class=\"")
            .append(if (ok) "ok" else "warn").append("\">")
            .append(if (ok) "OK" else "pendiente").append("</td><td class=\"mut\">")
            .append(esc(extra)).append("</td></tr>")
    }

    private fun Ui_time(ts: Long): String =
        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(ts))

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
