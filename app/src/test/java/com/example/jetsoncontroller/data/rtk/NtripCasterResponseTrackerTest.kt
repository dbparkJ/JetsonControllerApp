package com.example.jetsoncontroller.data.rtk

import java.math.BigInteger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NtripCasterResponseTrackerTest {
    // RTCM3 type 1005, 19 payload bytes, CRC24Q 0x950a66 (fixed reference vector).
    private val frame = hex("d300133ed00003000000000000000000000000000000950a66")
    private val http = ascii("HTTP/1.1 200 OK\r\nContent-Type: gnss/data\r\n\r\n")
    private val icy = ascii("ICY 200 OK\r\n")

    @Test
    fun `one hundred fragmented HTTP 401 retries never report a successful receive`() {
        var received = 0L
        repeat(100) {
            val tracker = NtripCasterResponseTracker()
            val response = ascii("HTTP/1.1 401 Unauthorized\r\nContent-Type: text/html\r\n\r\n<html>Denied</html>") + frame
            response.forEach { received += tracker.accept(byteArrayOf(it)) }
            assertEquals(0L, received)
            assertEquals(0L, tracker.validatedBytes)
            val error = tracker.error
            assertNotNull(error)
            assertTrue(error!!.contains("HTTP 401"))
            assertEquals(error, tracker.endOfInput())
        }
    }

    @Test
    fun `HTTP 404 and SOURCETABLE are terminal errors even with a valid embedded frame`() {
        for (status in listOf("HTTP/1.1 404 Not Found", "SOURCETABLE 200 OK", "ICY 401 Unauthorized")) {
            val tracker = NtripCasterResponseTracker()
            assertEquals(0L, tracker.accept(ascii("$status\r\n\r\n") + frame))
            assertNotNull(tracker.error)
            assertEquals(tracker.error, tracker.endOfInput())
        }
    }

    @Test
    fun `HTTP and ICY 200 headers alone do not prove data success`() {
        for (header in listOf(http, icy, ascii("HTTP/1.0 200 OK\r\n\r\n"))) {
            val tracker = NtripCasterResponseTracker()
            header.forEach { assertEquals(0L, tracker.accept(byteArrayOf(it))) }
            assertNull(tracker.error)
            assertEquals(0L, tracker.validatedBytes)
            assertTrue(tracker.endOfInput().contains("유효한 RTCM3"))
        }
    }

    @Test
    fun `ICY single CRLF and HTTP double CRLF count only verified frame bytes`() {
        for (header in listOf(icy, http, ascii("HTTP/1.0 200 OK\r\n\r\n"), ascii("ICY 200 OK\r\nContent-Type: gnss/data\r\n\r\n"))) {
            val tracker = NtripCasterResponseTracker()
            assertEquals(frame.size * 2L, tracker.accept(header + frame + frame))
            assertEquals(frame.size * 2L, tracker.validatedBytes)
            assertNull(tracker.error)
        }
    }

    @Test
    fun `every header payload and CRC split boundary preserves complete frames`() {
        for (header in listOf(http, icy)) {
            val response = header + frame + frame
            for (split in 0..response.size) {
                val tracker = NtripCasterResponseTracker()
                val count = tracker.accept(response, 0, split) +
                    tracker.accept(response, split, response.size - split)
                assertEquals("split=$split", frame.size * 2L, count)
                assertNull(tracker.error)
            }
        }
    }

    @Test
    fun `byte by byte reception counts nothing until the final CRC byte`() {
        val tracker = NtripCasterResponseTracker()
        (http + frame.dropLast(1).toByteArray()).forEach {
            assertEquals(0L, tracker.accept(byteArrayOf(it)))
        }
        assertEquals(frame.size.toLong(), tracker.accept(byteArrayOf(frame.last())))
        assertNull(tracker.error)
    }

    @Test
    fun `truncated payload or CRC at every boundary is never counted`() {
        for (length in 0 until frame.size) {
            val tracker = NtripCasterResponseTracker()
            assertEquals(0L, tracker.accept(http + frame.copyOf(length)))
            assertEquals(0L, tracker.validatedBytes)
            assertTrue(tracker.endOfInput().contains("유효한 RTCM3"))
        }
    }

    @Test
    fun `all single bit corruptions fail CRC or framing validation`() {
        for (index in frame.indices) {
            for (bit in 0..7) {
                val corrupted = frame.copyOf().apply { this[index] = (this[index].toInt() xor (1 shl bit)).toByte() }
                val tracker = NtripCasterResponseTracker()
                assertEquals("index=$index bit=$bit", 0L, tracker.accept(http + corrupted))
                assertEquals(0L, tracker.validatedBytes)
            }
        }
    }

    @Test
    fun `bad CRC can resynchronize to a following valid frame`() {
        val bad = frame.copyOf().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
        val tracker = NtripCasterResponseTracker()
        assertEquals(frame.size.toLong(), tracker.accept(http + bad + frame))
        assertNull(tracker.error)
    }

    @Test
    fun `empty and undersized CRC valid packets are not RTCM messages`() {
        for (payload in listOf(byteArrayOf(), byteArrayOf(1))) {
            val tracker = NtripCasterResponseTracker()
            assertEquals(0L, tracker.accept(http + makeFrame(payload)))
        }
    }

    @Test
    fun `maximum length RTCM frames work over a long fragmented stream`() {
        val large = makeFrame(ByteArray(1023) { 0x55 }.apply { this[0] = 0x3e; this[1] = 0xd0.toByte() })
        val tracker = NtripCasterResponseTracker()
        tracker.accept(http)
        repeat(200) {
            var received = 0L
            large.asList().chunked(17).forEach { received += tracker.accept(it.toByteArray()) }
            assertEquals(large.size.toLong(), received)
        }
        assertEquals(large.size * 200L, tracker.validatedBytes)
        assertNull(tracker.error)
    }

    @Test
    fun `HTML JSON and SOURCETABLE media types reject even binary looking bodies`() {
        for (type in listOf("text/html", "text/plain", "application/json", "application/xml", "gnss/sourcetable")) {
            val tracker = NtripCasterResponseTracker()
            assertEquals(0L, tracker.accept(ascii("HTTP/1.1 200 OK\r\nContent-Type: $type\r\n\r\n") + frame))
            assertNotNull(tracker.error)
        }
    }

    @Test
    fun `HTML and sourcetable without a media type cannot hide a valid frame`() {
        for (body in listOf("<html>Error</html>", " \r\n<!DOCTYPE html>", "SOURCETABLE 200 OK\r\n", "{\"error\":true}")) {
            val tracker = NtripCasterResponseTracker()
            assertEquals(0L, tracker.accept(ascii("HTTP/1.1 200 OK\r\n\r\n$body") + frame))
            assertNotNull(tracker.error)
        }
    }

    @Test
    fun `chunked frames span chunks extensions trailers and arbitrary read boundaries`() {
        val payload = frame + frame
        val body = payload.asList().chunked(3).fold(byteArrayOf()) { bytes, part ->
            bytes + ascii("${part.size.toString(16)};example=value\r\n") + part.toByteArray() + ascii("\r\n")
        } + ascii("0\r\nX-Example: complete\r\n\r\n")
        val response = ascii("HTTP/1.1 200 OK\r\nTransfer-Encoding: Chunked\r\n\r\n") + body
        for (split in 0..response.size) {
            val tracker = NtripCasterResponseTracker()
            val count = tracker.accept(response, 0, split) +
                tracker.accept(response, split, response.size - split)
            assertEquals("split=$split", payload.size.toLong(), count)
            assertNull(tracker.error)
        }
        val tracker = NtripCasterResponseTracker()
        var count = 0L
        response.forEach { count += tracker.accept(byteArrayOf(it)) }
        assertEquals(payload.size.toLong(), count)
    }

    @Test
    fun `chunk syntax and trailers never count as payload after terminating chunk`() {
        val tracker = NtripCasterResponseTracker()
        val header = ascii("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n")
        assertEquals(0L, tracker.accept(header + ascii("0\r\nX-Trailer: not-data\r\n\r\n") + frame))
        assertEquals(0L, tracker.validatedBytes)
    }

    @Test
    fun `invalid or overflowing chunk sizes fail closed`() {
        for (size in listOf("-1", "+2", "xyz", "8000000000000000", "", "f".repeat(4097))) {
            val tracker = NtripCasterResponseTracker()
            assertEquals(0L, tracker.accept(ascii("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n$size\r\n") + frame))
            assertNotNull(tracker.error)
        }
    }

    @Test
    fun `invalid chunk delimiter cannot turn a partial frame into success`() {
        val tracker = NtripCasterResponseTracker()
        val response = ascii("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\n") +
            frame.copyOf(3) + ascii("xx") + frame
        assertEquals(0L, tracker.accept(response))
        assertTrue(tracker.error!!.contains("chunked"))
    }

    @Test
    fun `compressed unsupported or ambiguous transfer encodings do not claim success`() {
        for (headers in listOf(
            "Content-Encoding: gzip\r\n", "Transfer-Encoding: gzip, chunked\r\n",
            "Transfer-Encoding: chunked\r\nContent-Length: 25\r\n",
            "Content-Length: -1\r\n", "Content-Length: 25\r\nContent-Length: 26\r\n"
        )) {
            val tracker = NtripCasterResponseTracker()
            assertEquals(0L, tracker.accept(ascii("HTTP/1.1 200 OK\r\n$headers\r\n") + frame))
            assertNotNull(tracker.error)
        }
    }

    @Test
    fun `content length bounds observer to the HTTP body`() {
        for (length in listOf(0, frame.size - 1, frame.size)) {
            val tracker = NtripCasterResponseTracker()
            val response = ascii("HTTP/1.1 200 OK\r\nContent-Length: $length\r\n\r\n") + frame + frame
            val expected = if (length == frame.size) frame.size.toLong() else 0L
            assertEquals(expected, tracker.accept(response))
        }
    }

    @Test
    fun `oversized response lines and total headers fail without retaining the body`() {
        for (header in listOf(
            "HTTP/1.1 200 " + "a".repeat(5000),
            "HTTP/1.1 200 OK\r\n" + "X-Example: value\r\n".repeat(2000)
        )) {
            val tracker = NtripCasterResponseTracker()
            assertEquals(0L, tracker.accept(ascii(header)))
            assertNotNull(tracker.error)
            repeat(100) { assertEquals(0L, tracker.accept(ByteArray(8192) { 0xd3.toByte() })) }
            assertEquals(0L, tracker.validatedBytes)
        }
    }

    @Test
    fun `observer never changes upstream bytes and never includes response text in errors`() {
        val response = ascii("HTTP/1.1 401 private-response-marker\r\n\r\n") + frame
        val original = response.copyOf()
        val tracker = NtripCasterResponseTracker()
        tracker.accept(response)
        assertArrayEquals(original, response)
        assertFalse(tracker.endOfInput().contains("private-response-marker"))
    }

    private fun ascii(value: String) = value.toByteArray(Charsets.US_ASCII)

    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    // Independent polynomial long division, separate from the streaming implementation.
    private fun makeFrame(payload: ByteArray): ByteArray {
        val data = byteArrayOf(0xd3.toByte(), (payload.size shr 8).toByte(), payload.size.toByte()) + payload
        var remainder = BigInteger(1, data).shiftLeft(24)
        val polynomial = BigInteger("1864cfb", 16)
        while (remainder.bitLength() > 24) {
            remainder = remainder.xor(polynomial.shiftLeft(remainder.bitLength() - 25))
        }
        val crc = remainder.toInt()
        return data + byteArrayOf((crc shr 16).toByte(), (crc shr 8).toByte(), crc.toByte())
    }
}
