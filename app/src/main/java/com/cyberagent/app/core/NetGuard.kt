package com.cyberagent.app.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Vigilancia de la red: portal cautivo, cambios de DNS y Wi-Fi abierta.
 *
 * Detecta lo que un atacante de red hace de verdad: colarte una pantalla de
 * acceso falsa (portal cautivo), cambiarte los servidores DNS (para redirigirte)
 * o que te conectes a una red sin cifrado.
 */
object NetGuard {

    fun checkAndAlert(ctx: Context): List<String> {
        val findings = ArrayList<String>()
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return findings
            val caps = cm.getNetworkCapabilities(net) ?: return findings
            val link = cm.getLinkProperties(net)

            val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

            // 1. Portal cautivo (pantalla de acceso intermedia).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
            ) {
                val msg = "La red exige pasar por una pantalla de acceso. Si no es la habitual " +
                    "(hotel, aeropuerto, bar), desconfía: puede ser una imitación para capturar datos."
                findings.add(msg)
                if (Prefs.lastNetKey != "portal") {
                    Prefs.lastNetKey = "portal"
                    Db.addAlert(
                        SecurityAlert(
                            severity = "MEDIA", module = "Vigilante de red",
                            title = "Portal cautivo detectado", detail = msg, mitre = "T1557"
                        )
                    )
                }
            }

            // 2. Cambio de servidores DNS.
            val dns = link?.dnsServers?.map { it.hostAddress ?: "" }?.filter { it.isNotEmpty() }
                ?.sorted()?.joinToString(",") ?: ""
            if (dns.isNotEmpty()) {
                val prev = Prefs.dnsFingerprint
                if (prev.isNotEmpty() && prev != dns) {
                    val msg = "Los servidores DNS de la red han cambiado.\nAntes: $prev\nAhora: $dns\n" +
                        "Puede ser algo normal si has cambiado de red o de operador. Si sigues en la " +
                        "misma red, alguien puede estar redirigiendo tu tráfico."
                    findings.add(msg)
                    Db.addAlert(
                        SecurityAlert(
                            severity = "MEDIA", module = "Vigilante de red",
                            title = "Cambio de DNS detectado", detail = msg, mitre = "T1584"
                        )
                    )
                }
                Prefs.dnsFingerprint = dns
            }

            // 3. Wi-Fi sin cifrado y sin VPN: todo el tráfico es visible.
            if (isWifi && !vpn) {
                val open = try {
                    val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE)
                        as android.net.wifi.WifiManager
                    @Suppress("DEPRECATION")
                    val info = wifi.connectionInfo
                    val caps2 = wifi.scanResults?.firstOrNull {
                        it.BSSID.equals(info?.bssid, true)
                    }?.capabilities ?: ""
                    caps2.isEmpty() || (!caps2.contains("WPA") && !caps2.contains("WEP"))
                } catch (t: Throwable) {
                    false
                }
                if (open && Prefs.lastNetKey != "open") {
                    Prefs.lastNetKey = "open"
                    val msg = "Estás en una Wi-Fi sin cifrado y sin cortafuegos VPN. " +
                        "En una red abierta cualquiera puede ver a dónde te conectas."
                    findings.add(msg)
                    Db.addAlert(
                        SecurityAlert(
                            severity = "MEDIA", module = "Vigilante de red",
                            title = "Wi-Fi abierta", detail = msg, mitre = "T1557"
                        )
                    )
                }
            } else if (Prefs.lastNetKey == "open" || Prefs.lastNetKey == "portal") {
                Prefs.lastNetKey = ""
            }
        } catch (t: Throwable) {
            CrashLog.record(ctx, "NetGuard", t)
        }
        return findings
    }
}
