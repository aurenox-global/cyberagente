package com.cyberagent.app.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager

/**
 * Auditoría de aplicaciones instaladas: permisos peligrosos, targetSdk
 * obsoleto, procedencia de la instalación y score de riesgo explicable.
 */
object AppAnalyzer {

    /** Permisos que más peso tienen en el score, con su motivo. */
    private val HIGH_RISK = mapOf(
        "android.permission.SEND_SMS" to "Envío de SMS (fraude premium)",
        "android.permission.RECEIVE_MMS" to "Recepción de MMS",
        "android.permission.CALL_PHONE" to "Llamadas sin confirmación",
        "android.permission.PROCESS_OUTGOING_CALLS" to "Intercepta llamadas",
        "android.permission.SYSTEM_ALERT_WINDOW" to "Superpone ventanas sobre otras apps",
        "android.permission.REQUEST_INSTALL_PACKAGES" to "Instala APKs (sideload)",
        "android.permission.BIND_ACCESSIBILITY_SERVICE" to "Servicio de accesibilidad (control total)",
        "android.permission.BIND_DEVICE_ADMIN" to "Administrador de dispositivo",
        "android.permission.READ_SMS" to "Lectura de SMS (2FA)",
        "android.permission.RECORD_AUDIO" to "Micrófono",
        "android.permission.CAMERA" to "Cámara",
        "android.permission.ACCESS_BACKGROUND_LOCATION" to "Ubicación en segundo plano",
        "android.permission.READ_CONTACTS" to "Contactos",
        "android.permission.READ_CALL_LOG" to "Historial de llamadas",
        "android.permission.QUERY_ALL_PACKAGES" to "Lista todas las apps instaladas"
    )

    private val MEDIUM_RISK = setOf(
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_PHONE_STATE",
        "android.permission.POST_NOTIFICATIONS"
    )

    fun analyzeAll(ctx: Context, includeSystem: Boolean = false): List<AppRisk> {
        val pm = ctx.packageManager
        val out = ArrayList<AppRisk>()
        val pkgs: List<PackageInfo> = try {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA)
        } catch (t: Throwable) {
            emptyList()
        }
        for (p in pkgs) {
            val ai = p.applicationInfo ?: continue
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem && !includeSystem) continue
            out.add(analyze(ctx, p))
        }
        return out.sortedByDescending { it.score }
    }

    fun analyze(ctx: Context, p: PackageInfo): AppRisk {
        val pm = ctx.packageManager
        val ai = p.applicationInfo
        val isSystem = ai != null && (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val label = try {
            pm.getApplicationLabel(ai!!).toString()
        } catch (t: Throwable) {
            p.packageName
        }

        val requested = (p.requestedPermissions ?: emptyArray()).toList()
        val granted = p.requestedPermissionsFlags
        val dangerous = ArrayList<String>()
        var score = 0
        val reasons = ArrayList<String>()

        for ((idx, perm) in requested.withIndex()) {
            HIGH_RISK[perm]?.let {
                score += 18
                dangerous.add(perm.substringAfterLast('.'))
                reasons.add(it)
            }
            if (MEDIUM_RISK.contains(perm)) {
                score += 7
                dangerous.add(perm.substringAfterLast('.'))
            }
            // Permiso declarado pero NO concedido: el usuario ya lo denegó.
            if (granted != null && idx < granted.size &&
                (granted[idx] and PackageInfo.REQUESTED_PERMISSION_GRANTED) == 0 &&
                (HIGH_RISK.containsKey(perm) || MEDIUM_RISK.contains(perm))
            ) {
                reasons.add("Permiso sensible denegado: ${perm.substringAfterLast('.')}")
            }
        }

        val target = if (android.os.Build.VERSION.SDK_INT >= 33) {
            ai?.targetSdkVersion ?: 0
        } else {
            @Suppress("DEPRECATION") p.applicationInfo?.targetSdkVersion ?: 0
        }
        if (target in 1..25) {
            score += 20
            reasons.add("targetSdk $target demasiado antiguo (sin endurecimiento moderno)")
        }

        // Procedencia: instalada desde fuera de una tienda.
        val installer = try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                pm.getInstallSourceInfo(p.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION") pm.getInstallerPackageName(p.packageName)
            }
        } catch (t: Throwable) {
            null
        }
        if (installer == null && !isSystem) {
            score += 12
            reasons.add("Instalación manual (sideload): sin tienda de origen verificada")
        }

        if (ai != null && (ai.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            score += 25
            reasons.add("Aplicación marcada como depurable (debuggable)")
        }

        val level = when {
            score >= 60 -> RiskLevel.CRITICO
            score >= 35 -> RiskLevel.ALTO
            score >= 15 -> RiskLevel.MEDIO
            else -> RiskLevel.BAJO
        }

        return AppRisk(
            packageName = p.packageName,
            label = label,
            isSystem = isSystem,
            targetSdk = target,
            requestedPermissions = requested,
            dangerousPermissions = dangerous.distinct(),
            score = score.coerceAtMost(100),
            level = level,
            reasons = reasons.distinct(),
            fromStore = installer != null
        )
    }
}
