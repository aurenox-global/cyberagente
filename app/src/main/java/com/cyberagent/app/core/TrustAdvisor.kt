package com.cyberagent.app.core

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager

/**
 * Panel de confianza: recomendaciones concretas por aplicación.
 *
 * Cruza los permisos DECLARADOS y CONCEDIDOS con el uso real (cuándo se usó la
 * app por última vez) y con su procedencia, y propone acciones: revocar
 * permisos, cortar la red, marcar de confianza o desinstalar.
 *
 * Es una recomendación honesta: se basa en datos medibles del sistema, no en
 * suposiciones sobre lo que hace la app por dentro.
 */
object TrustAdvisor {

    data class Advice(
        val pkg: String,
        val label: String,
        val severity: String,
        val problem: String,
        val suggestion: String,
        val kind: Kind
    )

    enum class Kind { REVOKE, BLOCK_NETWORK, UNINSTALL, REVIEW }

    /** Permisos que solo tienen sentido en apps concretas. */
    private val SENSITIVE_ONLY = mapOf(
        "android.permission.READ_SMS" to "leer tus SMS",
        "android.permission.RECEIVE_SMS" to "recibir tus SMS",
        "android.permission.SEND_SMS" to "enviar SMS",
        "android.permission.READ_CALL_LOG" to "leer tu historial de llamadas",
        "android.permission.CALL_PHONE" to "hacer llamadas",
        "android.permission.SYSTEM_ALERT_WINDOW" to "dibujar encima de otras apps",
        "android.permission.REQUEST_INSTALL_PACKAGES" to "instalar otras apps",
        "android.permission.ACCESS_BACKGROUND_LOCATION" to "tu ubicación en segundo plano"
    )

    fun advise(ctx: Context): List<Advice> {
        val out = ArrayList<Advice>()
        val pm = ctx.packageManager
        val used = lastUsedMap(ctx)

        val pkgs: List<PackageInfo> = try {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        } catch (t: Throwable) {
            emptyList()
        }

        for (p in pkgs) {
            val ai = p.applicationInfo ?: continue
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem) continue
            val pkg = p.packageName
            if (pkg == ctx.packageName) continue

            val label = try {
                pm.getApplicationLabel(ai).toString()
            } catch (t: Throwable) {
                pkg
            }

            val requested = p.requestedPermissions ?: emptyArray()
            val flags = p.requestedPermissionsFlags
            val granted = ArrayList<String>()
            for ((i, perm) in requested.withIndex()) {
                if (flags != null && i < flags.size &&
                    (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                ) granted.add(perm)
            }
            val daysSinceUse = daysSince(used[pkg])

            // 1. Permisos sensibles sin uso reciente.
            val stale = granted.filter { SENSITIVE_ONLY.containsKey(it) }
            if (stale.isNotEmpty() && daysSinceUse in 31..9_999) {
                out.add(
                    Advice(
                        pkg, label, "MEDIA",
                        "Puede ${stale.joinToString(" y ") { SENSITIVE_ONLY[it] ?: it.substringAfterLast('.') }} " +
                            "y no la usas desde hace $daysSinceUse días.",
                        "Revoca esos permisos o desinstálala si ya no la usas.",
                        Kind.REVOKE
                    )
                )
            }

            // 2. Muchos permisos sensibles concedidos.
            if (granted.size >= 6) {
                out.add(
                    Advice(
                        pkg, label, "MEDIA",
                        "Acumula ${granted.size} permisos sensibles concedidos.",
                        "Revisa permiso a permiso y retira los que no necesite para lo que la usas.",
                        Kind.REVIEW
                    )
                )
            }

            // 3. Instalada fuera de tienda.
            val installer = try {
                if (android.os.Build.VERSION.SDK_INT >= 30)
                    pm.getInstallSourceInfo(pkg).installingPackageName
                else @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg)
            } catch (t: Throwable) {
                null
            }
            if (installer == null) {
                out.add(
                    Advice(
                        pkg, label, "ALTA",
                        "No viene de una tienda de aplicaciones (instalación manual o APK descargado).",
                        "Si no la instalaste tú a propósito, desconfía: puede estar actualizada por un tercero.",
                        Kind.UNINSTALL
                    )
                )
            }

            // 4. Firmada como depuración.
            if ((ai.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                out.add(
                    Advice(
                        pkg, label, "ALTA",
                        "Build marcada como depurable: cualquiera con acceso al móvil puede alterarla.",
                        "Sustitúyela por la versión de tienda o desinstálala.",
                        Kind.UNINSTALL
                    )
                )
            }

            // 5. App muy antigua sin endurecimiento moderno.
            val target = ai.targetSdkVersion
            if (target in 1..25 && !Prefs.trustedPackages().contains(pkg)) {
                out.add(
                    Advice(
                        pkg, label, "MEDIA",
                        "Compilada para Android antiguo (targetSdk $target) y sin los límites actuales.",
                        "Actualízala; si ya no recibe soporte, valora quitarla.",
                        Kind.REVIEW
                    )
                )
            }
        }

        return out.sortedBy { severityRank(it.severity) }
    }

    /** Última vez que se usó cada app (0 si nunca / desconocido). */
    private fun lastUsedMap(ctx: Context): Map<String, Long> {
        val out = HashMap<String, Long>()
        try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_YEARLY, now - 400L * 86_400_000L, now)
            if (stats != null) {
                for (s in stats) {
                    val pkg = s.packageName ?: continue
                    val last = s.lastTimeUsed
                    val prev = out[pkg] ?: 0L
                    if (last > prev) out[pkg] = last
                }
            }
        } catch (t: Throwable) {
            // sin acceso al uso: no habrá datos de uso, el resto del análisis sigue
        }
        return out
    }

    private fun daysSince(ts: Long?): Int {
        if (ts == null || ts <= 0) return -1
        return ((System.currentTimeMillis() - ts) / 86_400_000L).toInt()
    }

    private fun severityRank(s: String): Int = when (s) {
        "CRITICA", "ALTA" -> 0
        "MEDIA" -> 1
        else -> 2
    }
}
