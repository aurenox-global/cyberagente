package com.cyberagent.app.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Vigila los PERMISOS de las apps después de instalarlas.
 *
 * Es un patrón clásico de troyano: la app se instala pidiendo poco y, tras una
 * actualización, gana permisos peligrosos. Guardamos una foto de los permisos
 * sensibles de cada app de usuario y avisamos cuando aparecen nuevos
 * (o cuando desaparecen, que también es interesante).
 *
 * La primera pasada solo crea la foto: no genera alertas.
 */
object PermissionWatcher {

    data class Change(
        val pkg: String,
        val label: String,
        val added: List<String>,
        val removed: List<String>
    )

    private const val FILE = "perm_snapshot.json"

    /** Permisos que consideramos "sensibles" para esta vigilancia. */
    private val WATCHED = setOf(
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.READ_CONTACTS",
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_CALL_LOG",
        "android.permission.CALL_PHONE",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.QUERY_ALL_PACKAGES",
        "android.permission.READ_PHONE_STATE",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.MANAGE_EXTERNAL_STORAGE"
    )

    fun diff(ctx: Context): List<Change> {
        val now = snapshot(ctx)
        val before = readSnapshot(ctx)
        writeSnapshot(ctx, now)

        if (before.isEmpty()) return emptyList() // primera vez: solo se guarda

        val out = ArrayList<Change>()
        for ((pkg, perms) in now) {
            val prev = before[pkg] ?: continue // app nueva: el receptor de paquetes ya avisa
            val added = perms - prev
            val removed = prev - perms
            if (added.isEmpty() && removed.isEmpty()) continue
            out.add(
                Change(
                    pkg = pkg,
                    label = label(ctx, pkg),
                    added = added.map { it.substringAfterLast('.') },
                    removed = removed.map { it.substringAfterLast('.') }
                )
            )
        }

        for (c in out) {
            Db.addAlert(
                SecurityAlert(
                    severity = if (c.added.isNotEmpty()) "ALTA" else "MEDIA",
                    module = "Vigilante de permisos",
                    title = if (c.added.isNotEmpty())
                        "${c.label} ha ganado permisos sensibles"
                    else "${c.label} ha perdido permisos sensibles",
                    detail = buildString {
                        if (c.added.isNotEmpty()) append("Nuevos: ").append(c.added.joinToString(", ")).append('\n')
                        if (c.removed.isNotEmpty()) append("Retirados: ").append(c.removed.joinToString(", ")).append('\n')
                        append("Revisa si la actualización de esa app era esperada.")
                    },
                    packageName = c.pkg,
                    mitre = "T1626"
                )
            )
        }
        if (out.isNotEmpty()) {
            Notify.post(
                ctx, "Permisos cambiados",
                out.joinToString("\n") { it.label + ": +" + it.added.joinToString(",") }
            )
        }
        return out
    }

    /** También se usa desde el receptor de paquetes al actualizar una app. */
    fun checkOne(ctx: Context, pkg: String): Change? {
        val snapshot = readSnapshot(ctx).toMutableMap()
        val current = dangerousOf(ctx, pkg) ?: return null
        val prev = snapshot[pkg]
        snapshot[pkg] = current
        writeSnapshot(ctx, snapshot)
        if (prev == null) return null
        val added = (current - prev).map { it.substringAfterLast('.') }
        val removed = (prev - current).map { it.substringAfterLast('.') }
        if (added.isEmpty() && removed.isEmpty()) return null
        val c = Change(pkg, label(ctx, pkg), added, removed)
        Db.addAlert(
            SecurityAlert(
                severity = if (added.isNotEmpty()) "ALTA" else "MEDIA",
                module = "Vigilante de permisos",
                title = "${c.label} cambió sus permisos al actualizarse",
                detail = buildString {
                    if (added.isNotEmpty()) append("Nuevos: ").append(added.joinToString(", ")).append('\n')
                    if (removed.isNotEmpty()) append("Retirados: ").append(removed.joinToString(", ")).append('\n')
                },
                packageName = pkg,
                mitre = "T1626"
            )
        )
        return c
    }

    // ── Interno ──────────────────────────────────────────────────────

    private fun snapshot(ctx: Context): Map<String, Set<String>> {
        val out = LinkedHashMap<String, Set<String>>()
        try {
            @Suppress("DEPRECATION")
            val pkgs: List<PackageInfo> = ctx.packageManager.getInstalledPackages(
                PackageManager.GET_PERMISSIONS
            )
            for (p in pkgs) {
                val ai = p.applicationInfo ?: continue
                if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
                val watched = watchedOf(p)
                if (watched.isNotEmpty()) out[p.packageName] = watched
            }
        } catch (t: Throwable) {
            // sin datos
        }
        return out
    }

    private fun dangerousOf(ctx: Context, pkg: String): Set<String>? = try {
        @Suppress("DEPRECATION")
        val p = ctx.packageManager.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        watchedOf(p)
    } catch (t: Throwable) {
        null
    }

    private fun watchedOf(p: PackageInfo): Set<String> =
        (p.requestedPermissions ?: emptyArray()).filter { WATCHED.contains(it) }.toSet()

    private fun readSnapshot(ctx: Context): Map<String, Set<String>> {
        val out = HashMap<String, Set<String>>()
        try {
            val f = java.io.File(ctx.filesDir, FILE)
            if (!f.exists()) return out
            val root = JSONObject(f.readText())
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val arr = root.optJSONArray(k) ?: continue
                val set = LinkedHashSet<String>()
                for (i in 0 until arr.length()) set.add(arr.getString(i))
                out[k] = set
            }
        } catch (t: Throwable) {
            return HashMap()
        }
        return out
    }

    private fun writeSnapshot(ctx: Context, data: Map<String, Set<String>>) {
        try {
            val root = JSONObject()
            for ((k, v) in data) {
                val arr = JSONArray()
                for (p in v) arr.put(p)
                root.put(k, arr)
            }
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
