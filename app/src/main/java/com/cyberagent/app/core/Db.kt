package com.cyberagent.app.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Persistencia local (SQLite). Los campos sensibles (`title`, `detail`) se
 * guardan cifrados con AES-256-GCM (Android Keystore) — a diferencia de la
 * v2.0, que almacenaba todo en claro.
 */
object Db {

    private const val NAME = "cyberagent.db"
    private const val VERSION = 1

    private lateinit var helper: Helper

    fun init(ctx: Context) {
        if (::helper.isInitialized) return
        helper = Helper(ctx.applicationContext)
    }

    fun addAlert(a: SecurityAlert): Long {
        val cv = ContentValues().apply {
            put("ts", a.ts)
            put("severity", a.severity)
            put("module", a.module)
            put("title_enc", Crypto.encrypt(a.title))
            put("detail_enc", Crypto.encrypt(a.detail))
            put("pkg", a.packageName)
            put("mitre", a.mitre)
            put("cvss", a.cvss)
        }
        return helper.writableDatabase.insert("alerts", null, cv)
    }

    fun alerts(limit: Int = 200, onlyHigh: Boolean = false): List<SecurityAlert> {
        val out = ArrayList<SecurityAlert>()
        val where = if (onlyHigh) "severity IN ('ALTA','CRITICA')" else null
        helper.readableDatabase.query(
            "alerts", null, where, null, null, null, "ts DESC", limit.toString()
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    SecurityAlert(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        ts = c.getLong(c.getColumnIndexOrThrow("ts")),
                        severity = c.getString(c.getColumnIndexOrThrow("severity")),
                        module = c.getString(c.getColumnIndexOrThrow("module")),
                        title = Crypto.decrypt(c.getString(c.getColumnIndexOrThrow("title_enc"))),
                        detail = Crypto.decrypt(c.getString(c.getColumnIndexOrThrow("detail_enc"))),
                        packageName = c.getString(c.getColumnIndexOrThrow("pkg")),
                        mitre = c.getString(c.getColumnIndexOrThrow("mitre")),
                        cvss = c.getDouble(c.getColumnIndexOrThrow("cvss"))
                    )
                )
            }
        }
        return out
    }

    fun countAlerts(since: Long = 0L): Int {
        helper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM alerts WHERE ts >= ?", arrayOf(since.toString())
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    fun countHighAlerts(since: Long = 0L): Int {
        helper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM alerts WHERE ts >= ? AND severity IN ('ALTA','CRITICA')",
            arrayOf(since.toString())
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    fun purge() {
        helper.writableDatabase.delete("alerts", null, null)
    }

    private class Helper(ctx: Context) : SQLiteOpenHelper(ctx, NAME, null, VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE alerts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ts INTEGER NOT NULL,
                    severity TEXT NOT NULL,
                    module TEXT NOT NULL,
                    title_enc TEXT NOT NULL,
                    detail_enc TEXT NOT NULL,
                    pkg TEXT,
                    mitre TEXT,
                    cvss REAL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX idx_alerts_ts ON alerts(ts)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {
            db.execSQL("DROP TABLE IF EXISTS alerts")
            onCreate(db)
        }
    }
}
