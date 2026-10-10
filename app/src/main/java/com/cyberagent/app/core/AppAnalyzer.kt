package com.cyberagent.app.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager

/**
 * Auditoría de aplicaciones instaladas.
 *
 * v3 — Motor de riesgo REBALANCEADO y con señales de firma:
 *  · Permisos con TOPE (máx. 50): no pueden por sí solos dar «crítico».
 *  · Señales de postura: sideload, APK debuggable, targetSdk antiguo,
 *    combinaciones anómalas (overlay+accesibilidad, SMS+Internet), app oculta,
 *    suplantación de marca y de banca.
 *  · FIRMA: reputación (misma firma que otras apps), whitelist por firma
 *    (apps/desarrolladores de confianza) y hash en la lista de IoC.
 *  · Amortiguadores anti-falso-positivo: tienda oficial, targetSdk moderno,
 *    app de sistema y firma de confianza.
 */
object AppAnalyzer {

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

    private val STORES = setOf(
        "com.android.vending",
        "com.google.android.packageinstaller",
        "org.fdroid.fdroid",
        "com.amazon.venezia",
        "com.sec.android.app.samsungapps",
        "com.huawei.appmarket",
        "com.xiaomi.mipicks"
    )

    /** Marcas que se suplantan con frecuencia → (subcadena, prefijo oficial). */
    private val BRANDS = listOf(
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "facebook" to "com.facebook",
        "messenger" to "com.facebook.orca",
        "telegram" to "org.telegram.messenger",
        "netflix" to "com.netflix.mediaclient",
        "paypal" to "com.paypal",
        "binance" to "com.binance",
        "metamask" to "io.metamask",
        "trust wallet" to "com.wallet.crypto.trustapp",
        "coinbase" to "com.coinbase.android",
        "youtube" to "com.google.android.youtube",
        "tiktok" to "com.zhiliaoapp.musically",
        "spotify" to "com.spotify.music",
        "bbva" to "com.bbva",
        "santander" to "es.bancosantander",
        "caixabank" to "com.caixabank",
        "mercadopago" to "com.mercadopago"
    )

    /** Palabras de banca/finanzas (heurística de banco suplantado). */
    private val BANK_WORDS = listOf(
        "banco", "bank", "banca", "caixa", "caja", "ahorro", "credit", "crédito",
        "financ", "wallet", "cartera", "revolut", "n26", "wise", "zelle",
        "western union", "seguridad social", "hacienda"
    )

    fun analyzeAll(ctx: Context, includeSystem: Boolean = false): List<AppRisk> {
        val pm = ctx.packageManager
        val pkgs: List<PackageInfo> = try {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(
                PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA or PackageManager.GET_SIGNATURES
            )
        } catch (t: Throwable) {
            emptyList()
        }
        // Índice de firma → nº de paquetes que la comparten (repackage).
        val sigCount = HashMap<String, Int>()
        for (p in pkgs) {
            val s = SignatureWatcher.signerSha256(p)
            if (s.isNotEmpty()) sigCount[s] = (sigCount[s] ?: 0) + 1
        }
        val out = ArrayList<AppRisk>()
        for (p in pkgs) {
            val ai = p.applicationInfo ?: continue
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem && !includeSystem) continue
            out.add(analyze(ctx, p, sigCount))
        }
        return out.sortedByDescending { it.score }
    }

    fun analyze(ctx: Context, p: PackageInfo): AppRisk = analyze(ctx, p, emptyMap())

    /**
     * @param sigCount mapa firma→nº de paquetes que la comparten (opcional).
     * @param apkSha256 hash del APK si ya se conoce (se comprueba contra IoC).
     */
    fun analyze(
        ctx: Context,
        p: PackageInfo,
        sigCount: Map<String, Int> = emptyMap(),
        apkSha256: String? = null
    ): AppRisk {
        val pm = ctx.packageManager
        val ai = p.applicationInfo
        val isSystem = ai != null && (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val label = try {
            pm.getApplicationLabel(ai!!).toString()
        } catch (t: Throwable) {
            p.packageName
        }

        val requested = (p.requestedPermissions ?: emptyArray()).toList()
        val rset = requested.toSet()
        val granted = p.requestedPermissionsFlags
        val dangerous = ArrayList<String>()
        val reasons = ArrayList<String>()

        // ── 1. Permisos (con TOPE) ───────────────────────────────────────
        var permScore = 0
        for (perm in requested) {
            when {
                HIGH_RISK.containsKey(perm) -> { permScore += 6; dangerous.add(perm.substringAfterLast('.')) }
                MEDIUM_RISK.contains(perm) -> { permScore += 2; dangerous.add(perm.substringAfterLast('.')) }
            }
        }
        permScore = permScore.coerceAtMost(50)
        reasons.addAll(
            requested.filter { HIGH_RISK.containsKey(it) }.take(3).mapNotNull { HIGH_RISK[it] }
        )
        // (Los permisos DENEGADOS ya no cuentan como riesgo; si acaso, lo reducen.)

        // ── 2. Señales de postura ────────────────────────────────────────
        var posture = 0

        val installer = try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                pm.getInstallSourceInfo(p.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION") pm.getInstallerPackageName(p.packageName)
            }
        } catch (t: Throwable) {
            null
        }
        val fromStore = installer != null && STORES.contains(installer)
        if (installer == null && !isSystem) {
            posture += 10
            reasons.add("Instalación manual (sideload): sin tienda de origen")
        }

        if (ai != null && (ai.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            posture += 25
            reasons.add("Aplicación marcada como depurable (debuggable)")
        }

        val target = if (android.os.Build.VERSION.SDK_INT >= 33) {
            ai?.targetSdkVersion ?: 0
        } else {
            @Suppress("DEPRECATION") p.applicationInfo?.targetSdkVersion ?: 0
        }
        if (target in 1..25) {
            posture += 15
            reasons.add("targetSdk $target demasiado antiguo (sin endurecimiento moderno)")
        } else if (target in 26..28) {
            posture += 6
            reasons.add("targetSdk $target antiguo (API antigua)")
        }

        if (rset.contains("android.permission.SYSTEM_ALERT_WINDOW") &&
            rset.contains("android.permission.BIND_ACCESSIBILITY_SERVICE")
        ) {
            posture += 20
            reasons.add("Combina superposición y accesibilidad: patrón de troyano bancario")
        }
        if (rset.contains("android.permission.READ_SMS") &&
            rset.contains("android.permission.INTERNET")
        ) {
            posture += 8
            reasons.add("Lee SMS y tiene Internet: posible exfiltración de códigos 2FA")
        }
        if (rset.contains("android.permission.SEND_SMS") &&
            rset.contains("android.permission.INTERNET")
        ) {
            posture += 8
            reasons.add("Envía SMS y tiene Internet: riesgo de fraude premium")
        }

        val hasLauncher = try {
            pm.getLaunchIntentForPackage(p.packageName) != null
        } catch (t: Throwable) {
            true
        }
        if (!isSystem && !hasLauncher) {
            posture += 6
            reasons.add("Sin acceso desde el lanzador (app oculta)")
        }

        // Suplantación de marca por nombre visible.
        val lc = label.lowercase()
        for ((brand, official) in BRANDS) {
            if (lc.contains(brand) && !p.packageName.startsWith(official)) {
                posture += 25
                reasons.add("Posible suplantación de «$brand» (paquete ${p.packageName})")
                break
            }
        }
        // Heurística de banca/finanzas (nombre sin correspondencia con el paquete).
        for (w in BANK_WORDS) {
            if (lc.contains(w) && !p.packageName.lowercase().contains(w)) {
                posture += if (fromStore) 6 else 18
                reasons.add("Nombre de bancos/finanzas sin correspondencia con el paquete")
                break
            }
        }

        // ── 3. Firma: reputación, whitelist e IoC ────────────────────────
        val signer = SignatureWatcher.signerSha256(p)
        val trustedSigner = signer.isNotEmpty() && Prefs.isTrustedSigner(signer)

        // Firma compartida con OTRA app distinta → indicio de repackaging.
        // (Solo se señala si la app no viene de tienda: los editores grandes
        //  firman muchas apps legítimas con la misma clave.)
        if (!fromStore && signer.isNotEmpty()) {
            val shared = (sigCount[signer] ?: 1)
            if (shared > 1) {
                posture += 8
                reasons.add("Comparte firma con $shared apps distintas (posible repackaging)")
            }
        }

        var forcedCritical = false
        if (!apkSha256.isNullOrEmpty()) {
            val known = IocDatabase.lookupHash(ctx, apkSha256)
            if (known != null) {
                forcedCritical = true
                reasons.add(
                    0, "Hash del APK en la lista de indicadores (IoC): " +
                        known.optString("name", "malware conocido")
                )
            }
        }

        // ── 4. Amortiguadores ────────────────────────────────────────────
        var damping = 0
        if (fromStore) damping += 12
        if (target >= 33) damping += 5
        if (isSystem) damping += 10
        if (trustedSigner) {
            damping += 35
            reasons.add("Firma marcada de confianza por ti (whitelist)")
        }

        val score = if (forcedCritical) 100
        else (permScore + posture - damping).coerceIn(0, 100)

        val level = when {
            forcedCritical || score >= 70 -> RiskLevel.CRITICO
            score >= 45 -> RiskLevel.ALTO
            score >= 20 -> RiskLevel.MEDIO
            else -> RiskLevel.BAJO
        }

        return AppRisk(
            packageName = p.packageName,
            label = label,
            isSystem = isSystem,
            targetSdk = target,
            requestedPermissions = requested,
            dangerousPermissions = dangerous.distinct(),
            score = score,
            level = level,
            reasons = reasons.distinct(),
            fromStore = fromStore,
            signerSha256 = signer
        )
    }
}
