package com.rodrigos01.aipodcasts.player

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException
import kotlin.math.max

/**
 * A DataSource wrapper that transparently unchains chunked Ogg/Opus audio streams.
 *
 * Background:
 * AI podcast TTS backends generate audio in sequential chunks (e.g. Gemini TTS chunks)
 * and stream them over a single HTTP connection as chained Ogg bitstreams.
 * While browsers like Chrome demux and play chained Ogg bitstreams seamlessly,
 * Android ExoPlayer's built-in OggExtractor does not support chained bitstreams:
 * it treats the second chunk's OpusHead/OpusTags headers as audio samples and passes
 * them to c2.android.opus.decoder, causing a fatal UNKNOWN_ERROR (0x80000000) decoder crash,
 * or terminates playback at the first chunk's EOS (end-of-stream) flag.
 *
 * ChainedOggDataSource intercepts the byte stream and transforms it on-the-fly into a
 * single, unbroken, valid Ogg Opus logical bitstream:
 * 1. Passes through the initial OpusHead and OpusTags headers.
 * 2. Clears premature EOS (0x04) flags on the final pages of intermediate chunks.
 * 3. Discards duplicate OpusHead and OpusTags header pages from subsequent chunks so
 *    the Opus decoder never receives non-audio header frames.
 * 4. Normalizes all pages to share the initial stream serial number and sequential page numbers.
 * 5. Offsets granule positions across chunks so sample timestamps remain strictly monotonic.
 * 6. Recomputes RFC 3533 CRC-32 checksums for every modified page.
 */
@UnstableApi
class ChainedOggDataSource(
    private val upstream: DataSource
) : DataSource {

    class Factory(
        private val upstreamFactory: DataSource.Factory
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return ChainedOggDataSource(upstreamFactory.createDataSource())
        }
    }

    private var isOggStream: Boolean? = null
    private var firstHeadersEmitted = false
    private var initialSerial = 0
    private var currentChunkSerial = 0
    private var lastEmittedSequence = 0
    private var cumulativeGranuleOffset = 0L
    private var currentChunkMaxGranule = 0L

    // Page output buffer
    private var outputBuffer: ByteArray? = null
    private var outputOffset = 0
    private var outputLength = 0

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        log(TAG, "open: uri=${dataSpec.uri}")
        resetState()
        return upstream.open(dataSpec)
    }

    private fun resetState() {
        isOggStream = null
        firstHeadersEmitted = false
        initialSerial = 0
        currentChunkSerial = 0
        lastEmittedSequence = 0
        cumulativeGranuleOffset = 0L
        currentChunkMaxGranule = 0L
        outputBuffer = null
        outputOffset = 0
        outputLength = 0
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0

        // If not an Ogg stream, pass through directly to upstream
        if (isOggStream == false) {
            return upstream.read(buffer, offset, length)
        }

        // Deliver buffered bytes from current page if available
        while (outputLength == 0) {
            val hasMorePages = fetchNextPage()
            if (!hasMorePages) {
                return C.RESULT_END_OF_INPUT
            }
        }

        val bytesToCopy = minOf(length, outputLength)
        val buf = outputBuffer ?: return C.RESULT_END_OF_INPUT
        System.arraycopy(buf, outputOffset, buffer, offset, bytesToCopy)
        outputOffset += bytesToCopy
        outputLength -= bytesToCopy

        if (outputLength == 0) {
            outputBuffer = null
        }

        return bytesToCopy
    }

    /**
     * Reads the next valid Ogg page from upstream, applies de-chaining transformations,
     * and fills outputBuffer. Discards duplicate headers from subsequent chunks.
     * Returns true if a page was buffered, or false on EOF.
     */
    private fun fetchNextPage(): Boolean {
        while (true) {
            val page = readRawOggPage() ?: return false

            val pageSerial = getSerial(page)
            val numSegments = page[26].toInt() and 0xFF
            val payloadStart = 27 + numSegments
            val payloadLen = page.size - payloadStart

            val isOpusHead = isMagic(page, payloadStart, payloadLen, OPUS_HEAD_MAGIC)
            val isOpusTags = isMagic(page, payloadStart, payloadLen, OPUS_TAGS_MAGIC)

            if (!firstHeadersEmitted) {
                if (isOpusHead) {
                    initialSerial = pageSerial
                    currentChunkSerial = pageSerial
                    lastEmittedSequence = getSequence(page)
                    log(TAG, "Initial OpusHead emitted: serial=$initialSerial, seq=$lastEmittedSequence")
                    outputBuffer = page
                    outputOffset = 0
                    outputLength = page.size
                    return true
                }
                if (isOpusTags) {
                    firstHeadersEmitted = true
                    lastEmittedSequence = getSequence(page)
                    log(TAG, "Initial OpusTags emitted: seq=$lastEmittedSequence")
                    outputBuffer = page
                    outputOffset = 0
                    outputLength = page.size
                    return true
                }
                firstHeadersEmitted = true
            }

            // If we encounter OpusHead in subsequent chunks, this is a new chunk transition!
            if (isOpusHead) {
                if (currentChunkMaxGranule > 0) {
                    cumulativeGranuleOffset += currentChunkMaxGranule
                    currentChunkMaxGranule = 0L
                }
                currentChunkSerial = pageSerial
                log(TAG, "Chunk boundary (OpusHead): serial=$pageSerial, cumulativeGranuleOffset=$cumulativeGranuleOffset. Skipping header.")
                continue
            }

            // If we encounter OpusTags in subsequent chunks, discard it
            if (isOpusTags) {
                log(TAG, "Skipping duplicate OpusTags in subsequent chunk.")
                continue
            }

            // If stream serial changed without OpusHead, transition chunk
            if (currentChunkSerial != 0 && pageSerial != currentChunkSerial) {
                if (currentChunkMaxGranule > 0) {
                    cumulativeGranuleOffset += currentChunkMaxGranule
                    currentChunkMaxGranule = 0L
                }
                currentChunkSerial = pageSerial
                log(TAG, "Chunk boundary (serial change $currentChunkSerial -> $pageSerial), cumulativeGranuleOffset=$cumulativeGranuleOffset")
            }

            // Audio page processing
            val rawGranule = getGranule(page)
            if (rawGranule > 0) {
                currentChunkMaxGranule = max(currentChunkMaxGranule, rawGranule)
            }

            var headerType = page[5].toInt() and 0xFF
            // Clear EOS (0x04) so ExoPlayer does not terminate at chunk boundaries,
            // and clear BOS (0x02) on non-first chunks
            headerType = headerType and 0x04.inv()
            headerType = headerType and 0x02.inv()
            page[5] = headerType.toByte()

            // Rewrite stream serial to match first chunk
            setSerial(page, initialSerial)

            // Rewrite page sequence number monotonically
            lastEmittedSequence++
            setSequence(page, lastEmittedSequence)

            // Offset granule position
            if (rawGranule >= 0) {
                setGranule(page, rawGranule + cumulativeGranuleOffset)
            }

            // Recalculate CRC32
            setChecksum(page, calculateOggCrc(page, 0, page.size))

            outputBuffer = page
            outputOffset = 0
            outputLength = page.size
            return true
        }
    }

    /**
     * Reads a full Ogg page (header + segments + payload) from the upstream DataSource.
     */
    private fun readRawOggPage(): ByteArray? {
        val header = ByteArray(27)

        // Read or synchronize to OggS
        if (isOggStream == null) {
            val sync = readUntilOggS()
            if (!sync) {
                isOggStream = false
                return null
            }
            isOggStream = true
            // We found OggS, copy into header bytes 0..3
            header[0] = 'O'.code.toByte()
            header[1] = 'g'.code.toByte()
            header[2] = 'g'.code.toByte()
            header[3] = 'S'.code.toByte()
            if (!readFully(header, 4, 23)) {
                return null
            }
        } else {
            if (!readFully(header, 0, 27)) {
                return null
            }
            if (header[0] != 'O'.code.toByte() || header[1] != 'g'.code.toByte() ||
                header[2] != 'g'.code.toByte() || header[3] != 'S'.code.toByte()
            ) {
                // Sync error, resync
                val sync = readUntilOggS()
                if (!sync) return null
                header[0] = 'O'.code.toByte()
                header[1] = 'g'.code.toByte()
                header[2] = 'g'.code.toByte()
                header[3] = 'S'.code.toByte()
                if (!readFully(header, 4, 23)) return null
            }
        }

        val numSegments = header[26].toInt() and 0xFF
        val segmentTable = ByteArray(numSegments)
        if (numSegments > 0 && !readFully(segmentTable, 0, numSegments)) {
            return null
        }

        var payloadLen = 0
        for (i in 0 until numSegments) {
            payloadLen += segmentTable[i].toInt() and 0xFF
        }

        val page = ByteArray(27 + numSegments + payloadLen)
        System.arraycopy(header, 0, page, 0, 27)
        if (numSegments > 0) {
            System.arraycopy(segmentTable, 0, page, 27, numSegments)
        }
        if (payloadLen > 0) {
            if (!readFully(page, 27 + numSegments, payloadLen)) {
                return null
            }
        }

        return page
    }

    private fun readUntilOggS(): Boolean {
        val window = ByteArray(4)
        var count = 0
        val single = ByteArray(1)
        while (count < 65536) { // Search up to 64KB
            val read = upstream.read(single, 0, 1)
            if (read == C.RESULT_END_OF_INPUT) return false
            window[0] = window[1]
            window[1] = window[2]
            window[2] = window[3]
            window[3] = single[0]
            if (window[0] == 'O'.code.toByte() &&
                window[1] == 'g'.code.toByte() &&
                window[2] == 'g'.code.toByte() &&
                window[3] == 'S'.code.toByte()
            ) {
                return true
            }
            count++
        }
        return false
    }

    private fun readFully(target: ByteArray, offset: Int, length: Int): Boolean {
        var totalRead = 0
        while (totalRead < length) {
            val read = upstream.read(target, offset + totalRead, length - totalRead)
            if (read == C.RESULT_END_OF_INPUT) {
                return false
            }
            totalRead += read
        }
        return true
    }

    private fun isMagic(page: ByteArray, offset: Int, length: Int, magic: ByteArray): Boolean {
        if (length < magic.size || offset + magic.size > page.size) return false
        for (i in magic.indices) {
            if (page[offset + i] != magic[i]) return false
        }
        return true
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        resetState()
        upstream.close()
    }

    companion object {
        private const val TAG = "ChainedOgg"

        private val OPUS_HEAD_MAGIC = byteArrayOf(
            'O'.code.toByte(), 'p'.code.toByte(), 'u'.code.toByte(), 's'.code.toByte(),
            'H'.code.toByte(), 'e'.code.toByte(), 'a'.code.toByte(), 'd'.code.toByte()
        )
        private val OPUS_TAGS_MAGIC = byteArrayOf(
            'O'.code.toByte(), 'p'.code.toByte(), 'u'.code.toByte(), 's'.code.toByte(),
            'T'.code.toByte(), 'a'.code.toByte(), 'g'.code.toByte(), 's'.code.toByte()
        )

        fun getGranule(page: ByteArray): Long {
            var granule = 0L
            for (i in 0..7) {
                granule = granule or ((page[6 + i].toLong() and 0xFF) shl (i * 8))
            }
            return granule
        }

        fun setGranule(page: ByteArray, granule: Long) {
            for (i in 0..7) {
                page[6 + i] = ((granule ushr (i * 8)) and 0xFF).toByte()
            }
        }

        fun getSerial(page: ByteArray): Int {
            var serial = 0
            for (i in 0..3) {
                serial = serial or ((page[14 + i].toInt() and 0xFF) shl (i * 8))
            }
            return serial
        }

        fun setSerial(page: ByteArray, serial: Int) {
            for (i in 0..3) {
                page[14 + i] = ((serial ushr (i * 8)) and 0xFF).toByte()
            }
        }

        fun getSequence(page: ByteArray): Int {
            var seq = 0
            for (i in 0..3) {
                seq = seq or ((page[18 + i].toInt() and 0xFF) shl (i * 8))
            }
            return seq
        }

        fun setSequence(page: ByteArray, seq: Int) {
            for (i in 0..3) {
                page[18 + i] = ((seq ushr (i * 8)) and 0xFF).toByte()
            }
        }

        fun setChecksum(page: ByteArray, crc: Int) {
            page[22] = (crc and 0xFF).toByte()
            page[23] = ((crc ushr 8) and 0xFF).toByte()
            page[24] = ((crc ushr 16) and 0xFF).toByte()
            page[25] = ((crc ushr 24) and 0xFF).toByte()
        }

        /**
         * RFC 3533 Ogg CRC-32 calculation.
         * The checksum bytes (offset 22..25) must be 0 when computing the CRC.
         */
        fun calculateOggCrc(data: ByteArray, offset: Int, length: Int): Int {
            var crc = 0
            for (i in offset until offset + length) {
                val byteVal = if (i in (offset + 22)..(offset + 25)) 0 else (data[i].toInt() and 0xFF)
                val index = ((crc ushr 24) xor byteVal) and 0xFF
                crc = (crc shl 8) xor CRC_LOOKUP[index]
            }
            return crc
        }

        private val CRC_LOOKUP = IntArray(256) { i ->
            var r = i shl 24
            for (j in 0 until 8) {
                r = if ((r and 0x80000000.toInt()) != 0) {
                    (r shl 1) xor 0x04c11db7
                } else {
                    r shl 1
                }
            }
            r
        }

        private fun log(tag: String, msg: String) {
            try {
                Log.d(tag, msg)
            } catch (_: Throwable) {
                // Ignore in JVM unit tests where android.util.Log is stubbed
            }
        }
    }
}
