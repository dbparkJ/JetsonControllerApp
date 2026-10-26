package com.example.jetsoncontroller.data.rtk

/**
 * Observes a single caster response without changing the bytes relayed to the receiver.
 * Only complete, CRC24Q-verified RTCM3 frames count as received correction data.
 * Retains bounded protocol/prefix buffers and one RTCM3 frame, never a full response body.
 */
internal class NtripCasterResponseTracker(requestPath: String? = null) {
    @Volatile private var requestPath: String? = requestPath
    private var sourceTableResponse = false
    private var sourceTableV1 = false
    private var sourceTableComplete = false
    private val sourceTablePrefix = StringBuilder(4)
    private var sourceTableLineStart = true
    private var sourceTableEndMatched = 0

    fun setRequestPath(path: String?) {
        requestPath = path
    }

    var validatedBytes: Long = 0
        private set
    var error: String? = null
        private set

    private enum class Phase {
        STATUS, ICY_START, HEADERS, BODY, CHUNK_SIZE, CHUNK_DATA,
        CHUNK_CR, CHUNK_LF, TRAILERS, DONE, FAILED
    }

    private var phase = Phase.STATUS
    private val line = ByteArray(MAX_LINE_BYTES)
    private var lineSize = 0
    private var headerBytes = 0
    private var trailerBytes = 0
    private var chunked = false
    private var contentLength: Long? = null
    private var bodyRemaining: Long? = null
    private var chunkRemaining = 0L
    private var bodyStarted = false
    private val frame = ByteArray(MAX_FRAME_BYTES)
    private var frameSize = 0

    /** Returns newly verified frame bytes (RTCM header + payload + CRC, without HTTP framing). */
    fun accept(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): Long {
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
        val before = validatedBytes
        for (index in offset until offset + length) consume(bytes[index].toInt() and 0xff)
        return validatedBytes - before
    }

    /** A protocol error takes precedence over EOF; no response text is ever exposed. */
    fun endOfInput(): String? = error ?: when {
        requestPath == "/" && !sourceTableResponse -> UNEXPECTED_SOURCE_TABLE_RESPONSE
        requestPath == "/" && !sourceTableComplete -> "NTRIP caster 소스테이블 응답이 중간에 끊겼습니다."
        requestPath == "/" -> null
        validatedBytes == 0L -> "NTRIP caster에서 유효한 RTCM3 데이터를 받기 전에 연결이 종료되었습니다."
        else -> "NTRIP caster 데이터 수신이 종료되었습니다."
    }

    private fun consume(value: Int) {
        when (phase) {
            Phase.STATUS, Phase.HEADERS -> {
                if (++headerBytes > MAX_HEADER_BYTES) fail("NTRIP caster 응답 헤더가 너무 큽니다.")
                else readLine(value)?.let { if (phase == Phase.STATUS) status(it) else header(it) }
            }
            Phase.ICY_START -> {
                // NTRIP v1 commonly starts binary data immediately after ICY 200 OK\r\n.
                if (value == PREAMBLE) {
                    phase = Phase.BODY
                    payload(value)
                } else {
                    phase = Phase.HEADERS
                    consume(value)
                }
            }
            Phase.BODY -> {
                payload(value)
                bodyRemaining?.let {
                    bodyRemaining = it - 1
                    if (it == 1L && phase != Phase.FAILED) phase = Phase.DONE
                }
            }
            Phase.CHUNK_SIZE -> readLine(value)?.let(::chunkSize)
            Phase.CHUNK_DATA -> {
                payload(value)
                if (--chunkRemaining == 0L && phase != Phase.FAILED) phase = Phase.CHUNK_CR
            }
            Phase.CHUNK_CR -> if (value == 13) phase = Phase.CHUNK_LF else invalidChunk()
            Phase.CHUNK_LF -> if (value == 10) phase = Phase.CHUNK_SIZE else invalidChunk()
            Phase.TRAILERS -> {
                if (++trailerBytes > MAX_HEADER_BYTES) invalidChunk()
                else readLine(value)?.let { if (it.isEmpty()) phase = Phase.DONE }
            }
            Phase.DONE, Phase.FAILED -> Unit
        }
    }

    private fun readLine(value: Int): String? {
        if (value == 10) {
            if (lineSize == 0 || line[lineSize - 1] != 13.toByte()) {
                fail("NTRIP caster 응답 형식이 올바르지 않습니다.")
                return null
            }
            val result = String(line, 0, lineSize - 1, Charsets.US_ASCII)
            lineSize = 0
            return result
        }
        if (lineSize == line.size || (value !in 32..126 && value != 13 && value != 9)) {
            fail("NTRIP caster 응답 형식이 올바르지 않습니다.")
            return null
        }
        line[lineSize++] = value.toByte()
        return null
    }

    private fun status(value: String) {
        if (requestPath == "/" && SOURCE_TABLE_STATUS_LINE.matches(value)) {
            sourceTableResponse = true
            sourceTableV1 = true
            phase = Phase.HEADERS
            return
        }
        if (value.startsWith("SOURCETABLE", ignoreCase = true)) {
            fail("NTRIP caster가 RTCM3 대신 SOURCETABLE을 반환했습니다. 마운트포인트를 확인하세요.")
            return
        }
        val match = STATUS_LINE.matchEntire(value)
        if (match == null) {
            fail("NTRIP caster 응답 상태를 확인할 수 없습니다.")
            return
        }
        val code = match.groupValues[2].toInt()
        if (code != 200) {
            fail(when (code) {
                401 -> "NTRIP caster 인증에 실패했습니다 (HTTP 401). 계정 정보를 확인하세요."
                404 -> "NTRIP caster 마운트포인트를 찾을 수 없습니다 (HTTP 404)."
                else -> "NTRIP caster가 오류 응답을 반환했습니다 (HTTP $code)."
            })
        } else {
            phase = if (match.groupValues[1] == "ICY" && requestPath != "/") Phase.ICY_START else Phase.HEADERS
        }
    }

    private fun header(value: String) {
        // Some NTRIP v1 casters omit all headers and the separating empty line.
        if (sourceTableV1 && (SOURCE_TABLE_PREFIXES.any(value::startsWith) || value == SOURCE_TABLE_END)) {
            phase = Phase.BODY
            value.forEach { sourceTablePayload(it.code) }
            sourceTablePayload(10)
            return
        }
        if (value.isEmpty()) {
            if (chunked && contentLength != null) {
                fail("NTRIP caster 응답의 데이터 길이 형식이 모호합니다.")
                return
            }
            bodyRemaining = contentLength
            phase = when {
                chunked -> Phase.CHUNK_SIZE
                contentLength == 0L -> Phase.DONE
                else -> Phase.BODY
            }
            return
        }
        val colon = value.indexOf(':')
        if (colon <= 0) {
            fail("NTRIP caster 응답 헤더 형식이 올바르지 않습니다.")
            return
        }
        val field = value.substring(colon + 1).trim()
        when (value.substring(0, colon).lowercase()) {
            "content-type" -> {
                val type = field.substringBefore(';').trim().lowercase()
                if (requestPath == "/") {
                    if (type == "gnss/sourcetable") sourceTableResponse = true
                } else if (type.startsWith("text/") || listOf("html", "xml", "json", "sourcetable").any { it in type }) {
                    fail("NTRIP caster가 RTCM3 데이터가 아닌 응답을 반환했습니다. 마운트포인트를 확인하세요.")
                }
            }
            "content-encoding" -> if (!field.equals("identity", ignoreCase = true)) {
                fail("NTRIP caster의 압축 응답은 RTCM3 수신 확인을 지원하지 않습니다.")
            }
            "transfer-encoding" -> {
                if (chunked || !field.equals("chunked", ignoreCase = true)) {
                    fail("NTRIP caster의 전송 인코딩은 RTCM3 수신 확인을 지원하지 않습니다.")
                } else chunked = true
            }
            "content-length" -> {
                val size = field.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }?.toLongOrNull()
                if (size == null || (contentLength != null && contentLength != size)) {
                    fail("NTRIP caster 응답의 데이터 길이가 올바르지 않습니다.")
                } else contentLength = size
            }
        }
    }

    private fun chunkSize(value: String) {
        // RFC 9112 chunk extensions are framing metadata, never RTCM payload.
        val digits = value.substringBefore(';')
        val size = digits.takeIf { it.isNotEmpty() && it.all { c -> c in "0123456789abcdefABCDEF" } }
            ?.toLongOrNull(16)
        if (size == null) invalidChunk()
        else {
            chunkRemaining = size
            phase = if (size == 0L) Phase.TRAILERS else Phase.CHUNK_DATA
        }
    }

    private fun payload(value: Int) {
        if (requestPath == "/") {
            sourceTablePayload(value)
            return
        }
        if (!bodyStarted) {
            bodyStarted = true
            // Do not search HTML/text error bodies for an accidentally embedded RTCM frame.
            if (value != PREAMBLE) {
                fail("NTRIP caster가 RTCM3 데이터가 아닌 응답을 반환했습니다. 마운트포인트를 확인하세요.")
                return
            }
        }
        if (frameSize == 0 && value != PREAMBLE) return
        frame[frameSize++] = value.toByte()
        while (frameSize > 0) {
            if ((frame[0].toInt() and 0xff) != PREAMBLE) {
                discardFramePrefix(1)
                continue
            }
            if (frameSize < 3) return
            val second = frame[1].toInt() and 0xff
            val payloadLength = ((second and 3) shl 8) or (frame[2].toInt() and 0xff)
            if ((second and 0xfc) != 0 || payloadLength < 2) {
                discardFramePrefix(1)
                continue
            }
            val size = payloadLength + 6
            if (frameSize < size) return
            val crcOffset = size - 3
            val expected = ((frame[crcOffset].toInt() and 0xff) shl 16) or
                ((frame[crcOffset + 1].toInt() and 0xff) shl 8) or
                (frame[crcOffset + 2].toInt() and 0xff)
            if (crc24q(frame, crcOffset) == expected) {
                validatedBytes += size
                discardFramePrefix(size)
            } else discardFramePrefix(1)
        }
    }

    private fun sourceTablePayload(value: Int) {
        if (sourceTableComplete) return
        if (!sourceTableResponse) {
            sourceTablePrefix.append(value.toChar())
            if (SOURCE_TABLE_PREFIXES.none { it.startsWith(sourceTablePrefix.toString()) }) {
                fail(UNEXPECTED_SOURCE_TABLE_RESPONSE)
                return
            }
            if (sourceTablePrefix.length == 4) sourceTableResponse = true
        }
        // Only match a terminator at the start of a body line, across reads and HTTP chunks.
        if (sourceTableLineStart) {
            if (value == SOURCE_TABLE_END[sourceTableEndMatched].code) {
                if (++sourceTableEndMatched == SOURCE_TABLE_END.length) sourceTableComplete = true
            } else sourceTableLineStart = false
        }
        if (value == 10) {
            sourceTableLineStart = true
            sourceTableEndMatched = 0
        }
    }

    private fun discardFramePrefix(size: Int) {
        frame.copyInto(frame, 0, size, frameSize)
        frameSize -= size
    }

    private fun invalidChunk() = fail("NTRIP caster의 chunked 응답 형식이 올바르지 않습니다.")

    private fun fail(message: String) {
        if (error == null) error = message
        phase = Phase.FAILED
        lineSize = 0
        frameSize = 0
    }

    private companion object {
        const val PREAMBLE = 0xd3
        const val MAX_LINE_BYTES = 4096
        const val MAX_HEADER_BYTES = 16384
        const val MAX_FRAME_BYTES = 1023 + 6
        const val UNEXPECTED_SOURCE_TABLE_RESPONSE = "NTRIP caster가 소스테이블 대신 다른 응답을 보냈습니다."
        const val SOURCE_TABLE_END = "ENDSOURCETABLE"
        val SOURCE_TABLE_PREFIXES = listOf("STR;", "CAS;", "NET;")
        val STATUS_LINE = Regex("(HTTP/1\\.[01]|ICY) ([0-9]{3})(?:[ \\t].*)?")
        val SOURCE_TABLE_STATUS_LINE = Regex("SOURCETABLE 200(?:[ \\t].*)?", RegexOption.IGNORE_CASE)

        fun crc24q(bytes: ByteArray, length: Int): Int {
            var crc = 0
            for (index in 0 until length) {
                crc = crc xor ((bytes[index].toInt() and 0xff) shl 16)
                repeat(8) {
                    crc = crc shl 1
                    if ((crc and 0x1000000) != 0) crc = crc xor 0x1864cfb
                }
            }
            return crc and 0xffffff
        }
    }
}
