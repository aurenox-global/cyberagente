package com.cyberagent.app.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Análisis de la seguridad de la red Wi-Fi activa: cifrado, red abierta,
 * suplantación básica (BSSID/SSID incoherentes) y señales de MITM.
 */
object WifiAnalyzer {

    @Suppress("DEPRECATION")
    fun analyze(ctx: Context): WifiReport {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        val caps = net?.let { cm.getNetworkCapabilities(it) }

        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        if (!isWifi) {
            val cell = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            return WifiReport(
                ssid = "",
                bssid = "",
                rssi = 0,
                security = if (cell) "RED MÓVIL" else "SIN CONEXIÓN",
                isOpen = false,
                isWeakCrypto = false,
                findings = listOf(
                    if (cell) "Conectado por datos móviles: no hay red Wi-Fi que auditar."
                    else "Sin conexión a red."
                ),
                score = 0
            )
        }

        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info: WifiInfo? = try {
            wifi.connectionInfo
        } catch (t: Throwable) {
            null
        }

        val granted = hasNearbyWifiPermission(ctx)
        val bssid = info?.bssid ?: ""
        val rssi = info?.rssi ?: 0
        @Suppress("MissingPermission")
        val ssid = try {
            info?.ssid?.trim('"') ?: ""
        } catch (t: Throwable) {
            ""
        }

        val findings = ArrayList<String>()
        var score = 0

        // Detección de cifrado a partir del escaneo (requiere permisos).
        var security = "DESCONOCIDO"
        var open = false
        var weak = false

        if (granted) {
            @Suppress("MissingPermission")
            try {
                val results = wifi.scanResults ?: emptyList()
                val match = results.firstOrNull { it.BSSID.equals(bssid, true) }
                if (match != null) {
                    val c = match.capabilities ?: ""
                    security = when {
                        c.contains("WPA3") && c.contains("SAE") -> "WPA3"
                        c.contains("WPA2") -> "WPA2"
                        c.contains("WPA") -> "WPA"
                        c.contains("WEP") -> "WEP"
                        else -> "ABIERTA"
                    }
                    open = security == "ABIERTA"
                    weak = security == "WEP" || security == "WPA"
                }
            } catch (t: Throwable) {
                findings.add("Escaneo no disponible (limitado por el sistema).")
            }
        } else {
            findings.add("Sin permiso de dispositivos cercanos: cifrado no verificable.")
        }

        if (open) {
            score += 45
            findings.add("Red ABIERTA: el tráfico no viaja cifrado en el aire. Riesgo de interceptación.")
        }
        if (weak) {
            score += 35
            findings.add("Cifrado débil ($security): vulnerable a ataques de diccionario.")
        }
        if (security == "WPA3") {
            findings.add("Cifrado WPA3: protección moderna (SAE, resistente a diccionario).")
        }
        if (security == "WPA2") {
            findings.add("Cifrado WPA2: aceptable. WPA3 sería preferible si el AP lo soporta.")
        }

        if (ssid.isEmpty()) {
            score += 10
            findings.add("SSID oculto o no legible: puede facilitar redes señuelo con el mismo nombre.")
        }

        // Suplantación básica: SSID conocido con BSSID distinto (heurística).
        if (!bssid.isNullOrEmpty() && bssid == "02:00:00:00:00:00") {
            score += 40
            findings.add("BSSID anómalo (02:00:00:00:00:00): posible punto de acceso falso.")
        }

        // DNS privado / VPN activa
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
            findings.add("Tráfico enrutado por VPN: la red local no ve el contenido.")
        }

        if (rssi != 0 && rssi < -80) {
            findings.add("Señal muy débil ($rssi dBm): el dispositivo puede reconectarse a otra red.")
        }

        return WifiReport(
            ssid = ssid.ifEmpty { "(oculta)" },
            bssid = bssid,
            rssi = rssi,
            security = security,
            isOpen = open,
            isWeakCrypto = weak,
            findings = findings,
            score = score.coerceAtMost(100)
        )
    }

    fun hasNearbyWifiPermission(ctx: Context): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    }
}
