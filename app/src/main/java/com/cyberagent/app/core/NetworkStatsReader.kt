package com.cyberagent.app.core

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import android.os.RemoteException

/**
 * Consumo de red por aplicación usando NetworkStatsManager.
 * Requiere el permiso especial de acceso al uso (PACKAGE_USAGE_STATS).
 */
object NetworkStatsReader {

    /**
     * Caché en memoria: leer NetworkStatsManager es lento (varios segundos con
     * muchas apps). Así, al repintar una pantalla o volver a ella no se repite
     * la consulta. TTL corto para no servir datos viejos.
     */
    private val cache = HashMap<String, Pair<Long, List<UsageRow>>>()
    private const val CACHE_MS = 45_000L

    fun invalidate() = synchronized(cache) { cache.clear() }

    @Volatile private var usageCheckedAt = 0L
    @Volatile private var usageChecked = false

    /**
     * Comprobación cacheada (20 s). Sin caché, esta llamada se hacía en el hilo
     * principal en cada repintado y, en los móviles que responden MODE_DEFAULT,
     * lanzaba una consulta real de estadísticas: causa de congelaciones.
     */
    fun hasUsageAccess(ctx: Context): Boolean {
        val now = System.currentTimeMillis()
        if (now - usageCheckedAt < 20_000L) return usageChecked
        val result = checkUsageAccess(ctx)
        usageChecked = result
        usageCheckedAt = now
        return result
    }

    /** Fuerza a re-comprobar el acceso al uso (tras volver de Ajustes). */
    fun invalidateUsageAccessCache() {
        usageCheckedAt = 0L
    }

    private fun checkUsageAccess(ctx: Context): Boolean {
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                ctx.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                ctx.packageName
            )
        }
        if (mode == AppOpsManager.MODE_ALLOWED) return true
        // Algunos fabricantes devuelven MODE_DEFAULT: comprobamos por uso real.
        return try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now)?.isNotEmpty() == true
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Uso acumulado por UID en [from, to] para móvil y Wi-Fi.
     * Devuelve vacío si no hay permiso o el sistema lo bloquea.
     */
    fun perAppUsage(ctx: Context, from: Long, to: Long): List<UsageRow> {
        val key = "$from-$to"
        synchronized(cache) {
            val hit = cache[key]
            if (hit != null && System.currentTimeMillis() - hit.first < CACHE_MS) return hit.second
        }
        val result = queryPerApp(ctx, from, to)
        synchronized(cache) { cache[key] = System.currentTimeMillis() to result }
        return result
    }

    private fun queryPerApp(ctx: Context, from: Long, to: Long): List<UsageRow> {
        val nsm = ctx.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val pm = ctx.packageManager

        val totals = HashMap<Int, LongArray>() // uid -> [rx, tx]

        for (type in intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            try {
                val bucket = nsm.querySummary(type, null, from, to)
                while (bucket.hasNextBucket()) {
                    val b = NetworkStats.Bucket()
                    bucket.getNextBucket(b)
                    val cur = totals[b.uid] ?: longArrayOf(0, 0)
                    cur[0] += b.rxBytes
                    cur[1] += b.txBytes
                    totals[b.uid] = cur
                }
                bucket.close()
            } catch (e: RemoteException) {
                // sin permiso o no disponible en esta red
            } catch (t: Throwable) {
                // ignorar y seguir con el siguiente tipo
            }
        }

        val out = ArrayList<UsageRow>()
        for ((uid, v) in totals) {
            if (uid == Process.SYSTEM_UID || v[0] + v[1] <= 0) continue
            val pkgs = pm.getPackagesForUid(uid) ?: continue
            for (p in pkgs) {
                val label = try {
                    val ai = pm.getApplicationInfo(p, 0)
                    pm.getApplicationLabel(ai).toString()
                } catch (t: Throwable) {
                    p
                }
                out.add(UsageRow(uid, p, label, v[0], v[1]))
            }
        }
        return out.sortedByDescending { it.total }
    }

    /**
     * Serie diaria para UNA aplicación concreta (rx/tx por día).
     * Se usa desde el detalle de consumo, en segundo plano.
     */
    fun dailySeriesForUid(ctx: Context, uid: Int, days: Int = 7): List<Triple<Long, Long, Long>> {
        val out = ArrayList<Triple<Long, Long, Long>>()
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        for (d in (days - 1) downTo 0) {
            val c = cal.clone() as java.util.Calendar
            c.add(java.util.Calendar.DAY_OF_YEAR, -d)
            val start = c.timeInMillis
            val rows = perAppUsage(ctx, start, start + 86_400_000L).filter { it.uid == uid }
            out.add(Triple(start, rows.sumOf { it.rxBytes }, rows.sumOf { it.txBytes }))
        }
        return out
    }

    fun totalForToday(ctx: Context): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val rows = perAppUsage(ctx, cal.timeInMillis, System.currentTimeMillis())
        return rows.sumOf { it.total }
    }

    /** Serie de los últimos 7 días (bytes totales por día). */
    fun last7Days(ctx: Context): List<Pair<String, Long>> {
        val out = ArrayList<Pair<String, Long>>()
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val fmt = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
        for (i in 6 downTo 0) {
            val start = cal.timeInMillis - i * 86_400_000L
            val end = start + 86_400_000L
            val total = perAppUsage(ctx, start, end).sumOf { it.total }
            out.add(fmt.format(java.util.Date(start)) to total)
        }
        return out
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb)
        return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
    }
}
