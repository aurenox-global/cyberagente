package com.cyberagent.app.core

import android.content.Context
import org.json.JSONObject

/**
 * Base local de indicadores de compromiso (IoC).
 *
 * Tiene DOS capas:
 *  1. La que viene empaquetada en `assets/ioc_db.json` (siempre disponible, offline).
 *  2. Una capa local ACTUALIZABLE (`files/ioc_local.json`) que se rellena desde
 *     el actualizador (ver [IocUpdater]) con listas externas verificadas.
 *
 * La capa local nunca sustituye a la de fábrica: se suman, así el motor sigue
 * funcionando aunque la actualización traiga menos entradas o falle.
 */
object IocDatabase {

    private var hashes: Map<String, JSONObject> = emptyMap()
    private var domains: List<JSONObject> = emptyList()
    private var localHashes: Map<String, JSONObject> = emptyMap()
    private var localDomains: List<JSONObject> = emptyList()

    var version: String = "-"
        private set

    /** Versión de la capa actualizable (independiente de la de fábrica). */
    var feedVersion: String = "-"
        private set

    @Synchronized
    fun load(ctx: Context) {
        if (hashes.isNotEmpty()) return
        loadAssets(ctx)
        loadLocal(ctx)
    }

    /** Recarga ambas capas (tras una actualización). */
    @Synchronized
    fun reload(ctx: Context) {
        hashes = emptyMap()
        domains = emptyList()
        localHashes = emptyMap()
        localDomains = emptyList()
        load(ctx)
    }

    private fun loadAssets(ctx: Context) {
        try {
            val text = ctx.assets.open("ioc_db.json").bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            version = root.optString("version", "-")
            hashes = parseHashes(root)
            domains = parseDomains(root)
        } catch (t: Throwable) {
            hashes = emptyMap()
            domains = emptyList()
        }
    }

    private fun loadLocal(ctx: Context) {
        try {
            val f = java.io.File(ctx.filesDir, LOCAL_FILE)
            if (!f.exists()) return
            val root = JSONObject(f.readText())
            feedVersion = root.optString("version", "-")
            localHashes = parseHashes(root)
            localDomains = parseDomains(root)
        } catch (t: Throwable) {
            localHashes = emptyMap()
            localDomains = emptyList()
        }
    }

    private fun parseHashes(root: JSONObject): Map<String, JSONObject> {
        val out = HashMap<String, JSONObject>()
        val arr = root.optJSONArray("hashes") ?: return out
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out[o.optString("sha256").lowercase()] = o
        }
        return out
    }

    private fun parseDomains(root: JSONObject): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        val arr = root.optJSONArray("domains") ?: return out
        for (i in 0 until arr.length()) out.add(arr.getJSONObject(i))
        return out
    }

    fun lookupHash(ctx: Context, sha256: String): JSONObject? {
        load(ctx)
        val k = sha256.lowercase()
        return hashes[k] ?: localHashes[k]
    }

    fun lookupDomain(ctx: Context, host: String): JSONObject? {
        load(ctx)
        return domains.firstOrNull { it.optString("host").equals(host, true) }
            ?: localDomains.firstOrNull { it.optString("host").equals(host, true) }
    }

    fun size(): Int = hashes.size + domains.size + localHashes.size + localDomains.size

    fun localSize(): Int = localHashes.size + localDomains.size

    fun factorySize(): Int = hashes.size + domains.size

    const val LOCAL_FILE = "ioc_local.json"
}
