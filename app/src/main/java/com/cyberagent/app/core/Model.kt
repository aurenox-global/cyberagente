package com.cyberagent.app.core

data class SecurityAlert(
    val id: Long = 0,
    val ts: Long = System.currentTimeMillis(),
    /** INFO | BAJA | MEDIA | ALTA | CRITICA */
    val severity: String = "INFO",
    val module: String = "",
    val title: String = "",
    val detail: String = "",
    val packageName: String? = null,
    val mitre: String? = null,
    val cvss: Double = 0.0
)

enum class RiskLevel { BAJO, MEDIO, ALTO, CRITICO }

data class AppRisk(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val targetSdk: Int,
    val requestedPermissions: List<String>,
    val dangerousPermissions: List<String>,
    val score: Int,
    val level: RiskLevel,
    val reasons: List<String>,
    /** Instalada desde una tienda verificada (Play, F-Droid…). */
    val fromStore: Boolean = false
)

data class WifiReport(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val security: String,
    val isOpen: Boolean,
    val isWeakCrypto: Boolean,
    val findings: List<String>,
    val score: Int
)

data class UsageRow(
    val uid: Int,
    val packageName: String,
    val label: String,
    val rxBytes: Long,
    val txBytes: Long
) {
    val total: Long get() = rxBytes + txBytes
}

data class ForensicResult(
    val fileName: String,
    val sha256: String,
    val sizeBytes: Long,
    val verdict: String,
    val source: String,
    val detail: String
)

data class ModuleStatus(
    val name: String,
    val ok: Boolean,
    val detail: String
)
