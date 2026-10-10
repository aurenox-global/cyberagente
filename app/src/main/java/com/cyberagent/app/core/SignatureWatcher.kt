package com.cyberagent.app.core

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Vigilante de FIRMA de las aplicaciones.
 *
 * Guarda el SHA-256 del certificado de firma de cada app instalada y avisa si
 * una ACTUALIZACIÓN cambia de firmante. Es uno de los indicadores más fiables
 * de que una app legítima ha sido **republicada** por un tercero (repackage):
 * el paquete y el nombre son los mismos, pero quien firma ya no es el autor
 * original. La primera pasada solo crea la foto: no genera alertas.
 */
object SignatureWatcher {

    data class Change(
        val pkg: String,
        val label: String,
        val oldSig: String,
        val newSig: String
    )

    private const val FILE = "sig_snapshot.json"

    fun diff(ctx: Context): List<Change> {
        val now = snapshot(ctx)
        val before = read(ctx)
        write(ctx, now)

        if (before.isEmpty()) return emptyList() // primera vez: solo se guarda

        val out = ArrayList<Change>()
        for ((pkg, sig) in now) {
            val prev = before[pkg] ?: continue
            if (prev.isNotEmpty() && sig.isNotEmpty() && prev != sig) {
                out.add(Change(pkg, label(ctx, pkg), prev, sig))
            }
        }

        for (c in out) {
            Db.addAlert(
                SecurityAlert(
                    severity = "ALTA",
                    module = "Vigilante de firma",
                    title = "${c.label} cambió de firma",
                    detail = "La firma de ${c.pkg} ya no es la misma que antes de la última " +
                        "actualización.\nAntes:  ${c.oldSig.take(24)}…\nAhora:  ${c.newSig.take(24)}…\n" +
                        "Puede ser una app republicada por un tercero (repackage). Si no esperabas " +
                        "que cambiara de desarrollador, desconfía y valórala de nuevo.",
                    packageName = c.pkg,
                    mitre = "T1553"
                )
            )
        }
        if (out.isNotEmpty()) {
            Notify.post(
                ctx, "CyberAgent · firma cambiada",
                out.joinToString("\n") { it.label + " (" + it.pkg + ")" }
            )
        }
        return out
    }

    /** SHA-256 del certificado de firma de un paquete ("" si no se puede leer). */
    fun signerOf(ctx: Context, pkg: String): String = try {
        @Suppress("DEPRECATION")
        val info = ctx.packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
        signerSha256(info)
    } catch (t: Throwable) {
        ""
    }

    /** Reutilizable: firma de un PackageInfo ya leído. */
    fun signerSha256(info: android.content.pm.PackageInfo): String {
        return try {
            val sig = info.signatures?.firstOrNull() ?: return ""
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                .joinToString("") { "%02x".format(it) }
        } catch (t: Throwable) {
            ""
        }
    }

    private fun snapshot(ctx: Context): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        try {
            val pm = ctx.packageManager
            @Suppress("DEPRECATION")
            val pkgs = pm.getInstalledPackages(PackageManager.GET_SIGNATURES)
            for (p in pkgs) {
                val sig = signerSha256(p)
                if (sig.isNotEmpty()) out[p.packageName] = sig
            }
        } catch (t: Throwable) {
            // sin datos
        }
        return out
    }

    private fun read(ctx: Context): Map<String, String> {
        val out = HashMap<String, String>()
        try {
            val f = java.io.File(ctx.filesDir, FILE)
            if (!f.exists()) return out
            val root = JSONObject(f.readText())
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k == "_updatedAt") continue
                out[k] = root.optString(k, "")
            }
        } catch (t: Throwable) {
            return HashMap()
        }
        return out
    }

    private fun write(ctx: Context, data: Map<String, String>) {
        try {
            val root = JSONObject()
            for ((k, v) in data) root.put(k, v)
            root.put("_updatedAt", System.currentTimeMillis())
            java.io.File(ctx.filesDir, FILE).writeText(root.toString())
        } catch (t: Throwable) {
            // sin permisos de escritura no hay foto
        }
    }

    private fun label(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (t: Throwable) {
        pkg
    }
}
