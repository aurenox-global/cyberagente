package com.cyberagent.app.core

/**
 * Autodiagnóstico: batería de comprobaciones que verifica que los mecanismos
 * REALES funcionan en este dispositivo (no que la pantalla lo diga).
 * Se ejecuta en segundo plano desde la pantalla "Herramientas".
 */
object SelfTest {

    data class Check(val name: String, val ok: Boolean, val detail: String)

    fun run(ctx: android.content.Context): List<Check> {
        val out = ArrayList<Check>()

        // 1. Cifrado AES-256-GCM de ida y vuelta.
        out.add(try {
            val sample = "prueba-" + System.currentTimeMillis()
            val enc = Crypto.encrypt(sample)
            val dec = Crypto.decrypt(enc)
            Check("Cifrado AES-256-GCM", dec == sample && enc != sample,
                if (dec == sample) "Cifra y descifra correctamente." else "El texto descifrado no coincide.")
        } catch (t: Throwable) {
            Check("Cifrado AES-256-GCM", false, t.javaClass.simpleName + ": " + t.message)
        })

        // 2. Base de datos de eventos.
        out.add(try {
            val before = Db.countAlerts()
            Db.addAlert(SecurityAlert(severity = "INFO", module = "Autodiagnóstico", title = "Prueba interna", detail = "Comprobación de escritura/lectura."))
            val after = Db.countAlerts()
            Check("Base de datos cifrada", after == before + 1, "Escritura y lectura correctas ($before → $after).")
        } catch (t: Throwable) {
            Check("Base de datos cifrada", false, t.javaClass.simpleName)
        })

        // 3. Base local de indicadores.
        out.add(
            if (IocDatabase.size() > 0)
                Check("Base IoC local", true, "v${IocDatabase.version} · ${IocDatabase.size()} entradas.")
            else Check("Base IoC local", false, "No se pudo cargar el fichero de indicadores.")
        )

        // 4. Analizador DNS (con un paquete de prueba, sin red).
        val dnsOk = testDns()
        out.add(dnsOk)

        // 5. Inspector de enlaces con un dominio de la lista.
        val v = LinkInspector.inspect(ctx, "http://malware.test/pago")
        out.add(
            Check("Inspector de enlaces", v.risky,
                if (v.risky) "Detecta el dominio de prueba: " + v.reasons.first() else "No detectó el dominio de prueba.")
        )

        // 6. Integridad y firma de esta app.
        val rep = ApkReputation.analyze(ctx, ctx.packageName)
        out.add(
            if (rep == null) Check("Firma de la app", false, "No se pudo leer la firma.")
            else Check(
                "Firma de la app",
                rep.apkSha256.isNotEmpty() && rep.signerSha256.isNotEmpty() && !rep.debugSigned,
                buildString {
                    append("APK SHA-256: ").append(rep.apkSha256.take(16)).append("…\n")
                    append("Firma: ").append(rep.signerSubject.take(70)).append("\n")
                    append(if (rep.debugSigned) "⚠ firmada como depuración" else "✓ build de release")
                }
            )
        )

        // 7. Acceso al uso.
        out.add(
            if (Perms.usageAccess(ctx)) Check("Acceso al uso (red)", true, "Concedido.")
            else Check("Acceso al uso (red)", false, "Pendiente: el monitor de red no puede medir.")
        )

        // 8. Cortafuegos / túnel.
        val fw = Prefs.firewallEnabled
        val tun = com.cyberagent.app.service.FirewallVpnService.isTunnelRunning()
        out.add(
            Check(
                "Cortafuegos VPN",
                if (fw) tun else true,
                when {
                    fw && tun -> "Activo y con túnel establecido."
                    fw && !tun -> "⚠ Activado pero sin túnel (¿otra VPN? ¿consentimiento revocado?)."
                    else -> "Desactivado (opcional)."
                }
            )
        )

        // 9. Roles y accesos de sistema.
        val sms = com.cyberagent.app.receiver.SmsDeliverReceiver.isDefaultSmsApp(ctx)
        val call = com.cyberagent.app.service.CallGuardService.hasRole(ctx)
        val acc = Perms.accessibilityEnabled(ctx)
        out.add(
            Check(
                "Roles de sistema", sms || call || acc,
                "SMS: " + (if (sms) "sí" else "no") + " · llamadas: " + (if (call) "sí" else "no") +
                    " · accesibilidad: " + (if (acc) "sí" else "no")
            )
        )

        // 10. Batería sin restricciones (afecta a la defensa automática).
        val free = try {
            (ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager)
                .isIgnoringBatteryOptimizations(ctx.packageName)
        } catch (t: Throwable) {
            true
        }
        out.add(
            Check(
                "Batería sin restricciones", free,
                if (free) "Android no limitará los escaneos."
                else "⚠ Optimizada: los escaneos automáticos pueden retrasarse."
            )
        )

        return out
    }

    /** Prueba del analizador DNS con un paquete DNS sintético (sin red). */
    private fun testDns(): Check {
        return try {
            val name = "ejemplo.test"
            val labels = name.split('.')
            val qname = ArrayList<Byte>()
            for (l in labels) {
                qname.add(l.length.toByte())
                for (ch in l) qname.add(ch.code.toByte())
            }
            qname.add(0)
            val dnsLen = 12 + qname.size + 4
            val udpLen = 8 + dnsLen
            val total = 20 + udpLen
            val p = ByteArray(total)
            p[0] = 0x45
            p[2] = ((total shr 8) and 0xFF).toByte(); p[3] = (total and 0xFF).toByte()
            p[8] = 64; p[9] = 17
            p[12] = 10; p[13] = 0; p[14] = 0; p[15] = 2
            p[16] = 10; p[17] = 111.toByte(); p[18] = 222.toByte(); p[19] = 1
            p[20] = 0x9C.toByte(); p[21] = 0x40 // puerto origen 40000
            p[22] = 0; p[23] = 53
            p[24] = ((udpLen shr 8) and 0xFF).toByte(); p[25] = (udpLen and 0xFF).toByte()
            p[28] = 0x12; p[29] = 0x34 // ID
            p[30] = 0x01 // RD
            p[32] = 0; p[33] = 1 // QDCOUNT
            var i = 40
            for (b in qname) p[i++] = b
            p[i++] = 0; p[i++] = 1 // QTYPE A
            p[i] = 0; p[i + 1] = 1 // QCLASS IN

            val parsed = DnsFilter.parseQueryName(p, total)
            val resp = DnsFilter.buildNxDomain(p, total)
            val rcode = resp?.let { it[20 + 8 + 3].toInt() and 0x0F } ?: -1
            Check(
                "Analizador DNS",
                parsed == name && resp != null && rcode == 3,
                "Consulta «$parsed» · respuesta NXDOMAIN " + (if (rcode == 3) "correcta" else "no válida")
            )
        } catch (t: Throwable) {
            Check("Analizador DNS", false, t.javaClass.simpleName + ": " + t.message)
        }
    }
}
