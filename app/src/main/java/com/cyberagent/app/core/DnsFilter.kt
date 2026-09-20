package com.cyberagent.app.core

/**
 * Filtro DNS del cortafuegos.
 *
 * El túnel no reenvía tráfico (no es un VPN completo), así que el bloqueo por
 * dominio se aplica donde sí es real: las consultas DNS de las apps bloqueadas
 * se responden con NXDOMAIN para que fallen AL INSTANTE (antes se descartaban
 * sin respuesta y la app se quedaba esperando el tiempo de expiración, lo que
 * gastaba batería y datos).
 *
 * Aquí solo se analiza/construye el paquete, sin estado: es fácil de probar.
 */
object DnsFilter {

    /** Extrae el nombre consultado de un paquete DNS sobre UDP/IPv4. */
    fun parseQueryName(packet: ByteArray, len: Int): String? {
        try {
            if (len < 28) return null
            val ihl = (packet[0].toInt() and 0x0F) * 4
            if (ihl < 20) return null
            if ((packet[9].toInt() and 0xFF) != 17) return null // UDP
            val dns = ihl + 8
            if (len < dns + 12) return null
            val qdCount = ((packet[dns + 4].toInt() and 0xFF) shl 8) or (packet[dns + 5].toInt() and 0xFF)
            if (qdCount < 1) return null
            var i = dns + 12
            val sb = StringBuilder()
            var guard = 0
            while (i < len && guard++ < 128) {
                val l = packet[i].toInt() and 0xFF
                if (l == 0) break
                if (l and 0xC0 == 0xC0) break // punteros de compresión: no en una consulta
                if (i + 1 + l > len) return null
                if (sb.isNotEmpty()) sb.append('.')
                for (k in 1..l) sb.append((packet[i + k].toInt() and 0xFF).toChar())
                i += 1 + l
            }
            return if (sb.isEmpty()) null else sb.toString().lowercase()
        } catch (t: Throwable) {
            return null
        }
    }

    /** Comprueba si un nombre está en la lista de dominios a filtrar. */
    fun isBlocked(ctx: android.content.Context, name: String): Boolean {
        if (name.isEmpty()) return false
        if (IocDatabase.lookupDomain(ctx, name) != null) return true
        var rest = name
        while (rest.contains('.')) {
            rest = rest.substringAfter('.')
            if (rest.isEmpty()) break
            if (IocDatabase.lookupDomain(ctx, rest) != null) return true
            if (!rest.contains('.')) break
        }
        return false
    }

    /**
     * Construye la respuesta NXDOMAIN para una consulta A/AAAA. Devuelve null si
     * el paquete no es una consulta DNS sencilla que sepamos responder.
     */
    fun buildNxDomain(query: ByteArray, len: Int): ByteArray? {
        try {
            if (len < 28) return null
            val ihl = (query[0].toInt() and 0x0F) * 4
            if (ihl < 20 || (query[9].toInt() and 0xFF) != 17) return null
            val dns = ihl + 8

            // Longitud de la pregunta: nombre + qtype(2) + qclass(2).
            var i = dns + 12
            var guard = 0
            while (i < len && guard++ < 128) {
                val l = query[i].toInt() and 0xFF
                if (l == 0) break
                if (l and 0xC0 == 0xC0) return null
                i += 1 + l
            }
            if (i >= len) return null
            val questionEnd = i + 1 + 4
            if (questionEnd > len) return null

            val outLen = questionEnd
            val out = ByteArray(outLen)

            // IP: se copia y se intercambian origen/destino.
            System.arraycopy(query, 0, out, 0, ihl)
            for (k in 0 until 4) {
                val a = query[12 + k]
                out[12 + k] = query[16 + k]
                out[16 + k] = a
            }
            out[2] = ((outLen shr 8) and 0xFF).toByte()
            out[3] = (outLen and 0xFF).toByte()
            out[8] = 64 // TTL
            out[10] = 0
            out[11] = 0
            val ipSum = checksum(out, 0, ihl)
            out[10] = ((ipSum shr 8) and 0xFF).toByte()
            out[11] = (ipSum and 0xFF).toByte()

            // UDP: se copian los puertos intercambiados; checksum 0 (válido en IPv4).
            out[ihl] = query[ihl + 2]
            out[ihl + 1] = query[ihl + 3]
            out[ihl + 2] = query[ihl]
            out[ihl + 3] = query[ihl + 1]
            val udpLen = (outLen - ihl).toShort()
            out[ihl + 4] = ((udpLen.toInt() shr 8) and 0xFF).toByte()
            out[ihl + 5] = (udpLen.toInt() and 0xFF).toByte()
            out[ihl + 6] = 0
            out[ihl + 7] = 0

            // DNS: respuesta NXDOMAIN con la misma pregunta.
            System.arraycopy(query, dns, out, dns, 2) // ID
            out[dns + 2] = 0x81.toByte() // QR=1, opcode 0, AA=0, TC=0, RD=1
            out[dns + 3] = 0x83.toByte() // RA=1, RCODE=3 (NXDOMAIN)
            out[dns + 4] = 0; out[dns + 5] = 1 // QDCOUNT
            out[dns + 6] = 0; out[dns + 7] = 0 // ANCOUNT
            out[dns + 8] = 0; out[dns + 9] = 0 // NSCOUNT
            out[dns + 10] = 0; out[dns + 11] = 0 // ARCOUNT
            System.arraycopy(query, dns + 12, out, dns + 12, questionEnd - (dns + 12))
            return out
        } catch (t: Throwable) {
            return null
        }
    }

    private fun checksum(b: ByteArray, from: Int, to: Int): Int {
        var sum = 0
        var i = from
        while (i + 1 < to) {
            sum += ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
            i += 2
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }
}
