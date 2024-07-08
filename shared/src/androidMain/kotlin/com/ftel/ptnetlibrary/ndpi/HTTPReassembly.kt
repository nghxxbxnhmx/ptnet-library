package com.ftel.ptnetlibrary.ndpi

import android.util.Log
import com.ftel.ptnetlibrary.ndpi.Utils.safeClose
import com.ftel.ptnetlibrary.ndpi.models.PayloadChunk
import com.ftel.ptnetlibrary.ndpi.Utils
import org.brotli.dec.BrotliInputStream
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream


class HTTPReassembly {
    private val TAG = "HTTPReassembly"
    private var MAX_HEADERS_SIZE = 1024
    private var mReadingHeaders = false
    private var mChunkedEncoding = false
    private var mContentType: String? = null
    private var mPath: String? = null
    private var mContentLength = 0
    private var mHeadersSize = 0

    private var mHeaders = ArrayList<PayloadChunk>()
    private var mBody = ArrayList<PayloadChunk>()
    private var mReassembleChunks = false
    private var mInvalidHttp = false
    private var mIsTx = false

    private lateinit var mContentEncoding: ContentEncoding
    private lateinit var mListener: ReassemblyListener

    constructor(mReassembleChunks: Boolean, mListener: ReassemblyListener) {
        this.mReassembleChunks = mReassembleChunks
        this.mListener = mListener
    }

    interface ReassemblyListener {
        fun onChunkReassembled(chunk: PayloadChunk?)
    }

    private enum class ContentEncoding {
        UNKNOWN,
        GZIP,
        DEFLATE,
        BROTLI
    }

    private fun reset() {
        mReadingHeaders = true
        mContentEncoding = ContentEncoding.UNKNOWN
        mChunkedEncoding = false
        mContentLength = -1
        mContentType = null
        mPath = null
        mHeadersSize = 0
        mHeaders.clear()
        mBody.clear()

        // Do not reset, these affects the whole connection
        //upgradeFound = false;
        //mInvalidHttp = false;
    }

    private fun log_d(msg: String) {
        Log.d(TAG + "(" + (if (mIsTx) "TX" else "RX") + ")", msg)
    }


    /* The request/response tab shows reassembled HTTP chunks.
     * Reassembling chunks is requires when using a content-encoding like gzip since we can only
     * decode the data when we have the full chunk and we cannot determine data bounds.
     * When reading data via the MitmReceiver, mitmproxy already performs chunks reassembly and
     * also handles HTTP/2 so that we only get the payload. */
    fun handleChunk(chunk: PayloadChunk) {
        var bodyStart = 0
        val payload = chunk.payload
        var chunkedComplete = false
        mIsTx = chunk.isSent
        if (mReadingHeaders) {
            // Reading the HTTP headers
            val headersEnd: Int = Utils.getEndOfHTTPHeaders(payload)
            val headersSize = if (headersEnd == 0) payload.size else headersEnd
            val isFirstLine = mHeadersSize == 0
            mHeadersSize += headersSize
            try {
                BufferedReader(
                    InputStreamReader(
                        ByteArrayInputStream(
                            payload,
                            0,
                            headersSize
                        )
                    )
                ).use { reader ->
                    var line = reader.readLine()
                    if (isFirstLine && line != null) {
                        if (line.startsWith("GET ") || line.startsWith("POST ")
                            || line.startsWith("HEAD ") || line.startsWith("PUT ")
                        ) {
                            val firstSpace = line.indexOf(' ')
                            val secondSpace = line.indexOf(' ', firstSpace + 1)
                            if (firstSpace > 0 && secondSpace > 0) {
                                mPath = line.substring(firstSpace + 1, secondSpace)
                                val queryStart = mPath!!.indexOf('?')
                                if (queryStart >= 0) mPath = mPath!!.substring(0, queryStart)
                                log_d("Path: $mPath")
                            }
                        }
                    }
                    while (line != null && line.isNotEmpty()) {
                        line = line.lowercase(Locale.getDefault())
                        //log_d("[HEADER] " + line);
                        if (line.startsWith("content-encoding: ")) {
                            val contentEncoding = line.substring(18)
                            log_d("Content-Encoding: $contentEncoding")
                            when (contentEncoding) {
                                "gzip" -> mContentEncoding = ContentEncoding.GZIP
                                "deflate" ->                                 // test with http://carsten.codimi.de/gzip.yaws/daniels.html?deflate=on
                                    mContentEncoding = ContentEncoding.DEFLATE

                                "br" ->                                 // test with google.com
                                    mContentEncoding = ContentEncoding.BROTLI
                            }
                        } else if (line.startsWith("content-type: ")) {
                            val endIdx = line.indexOf(";")
                            mContentType =
                                line.substring(14, if (endIdx > 0) endIdx else line.length)
                            log_d("Content-Type: $mContentType")
                        } else if (line.startsWith("content-length: ")) {
                            try {
                                mContentLength = line.substring(16).toInt()
                                log_d("Content-Length: $mContentLength")
                            } catch (ignored: NumberFormatException) {
                            }
                        } else if (line.startsWith("upgrade: ")) {
                            log_d("Upgrade found, stop parsing")
                            mReassembleChunks = false
                        } else if (line == "transfer-encoding: chunked") {
                            log_d("Detected chunked encoding")
                            mChunkedEncoding = true
                        }
                        line = reader.readLine()
                    }
                }
            } catch (ignored: IOException) {
            }
            if (headersEnd > 0) {
                mReadingHeaders = false
                bodyStart = headersEnd
                mHeaders.add(chunk.subchunk(0, bodyStart))
            } else {
                if (mHeadersSize > MAX_HEADERS_SIZE) {
                    log_d("Assuming not HTTP")

                    // Assume this is not valid HTTP traffic
                    mReadingHeaders = false
                    mReassembleChunks = false
                    mInvalidHttp = true
                }

                // Headers span all the packet
                mHeaders.add(chunk)
                bodyStart = payload.size
            }
        }

        // If not Content-Length provided and not using chunked encoding, then we cannot determine
        // chunks bounds, so disable reassembly
        if (!mReadingHeaders && mContentLength < 0 && !mChunkedEncoding && mReassembleChunks) {
            log_d("Cannot determine bounds, disable reassembly")
            mReassembleChunks = false
        }

        // When mReassembleChunks is false, each chunk should be passed to the mListener
        if (!mReassembleChunks) mReadingHeaders = false
        if (!mReadingHeaders) {
            // Reading HTTP body
            var bodySize = payload.size - bodyStart
            var newBodyStart = -1
            if (mChunkedEncoding && mContentLength < 0 && bodySize > 0) {
                try {
                    BufferedReader(
                        InputStreamReader(
                            ByteArrayInputStream(
                                payload,
                                bodyStart,
                                bodySize
                            )
                        )
                    ).use { reader ->
                        val line = reader.readLine()
                        if (line != null) {
                            try {
                                // Each chunk starts with the chunk length
                                mContentLength = line.toInt(16)
                                bodyStart += line.length + 2
                                bodySize -= line.length + 2
                                log_d("Chunk length: $mContentLength")
                                if (mContentLength == 0) chunkedComplete = true
                            } catch (ignored: NumberFormatException) {
                            }
                        }
                    }
                } catch (ignored: IOException) {
                }
            }

            // NOTE: Content-Length is optional in HTTP/2.0, mitmproxy reconstructs the entire message
            if (bodySize > 0) {
                if (mContentLength > 0) {
                    //log_d("body: " + body_size + " / " + mContentLength);
                    if (bodySize < mContentLength) mContentLength -= bodySize else {
                        bodySize = mContentLength
                        newBodyStart = bodyStart + mContentLength
                        mContentLength = -1

                        // With chunked encoding, skip the trailing \r\n
                        if (mChunkedEncoding) newBodyStart += 2
                    }
                }
                if (bodyStart == 0 && bodySize == chunk.payload.size) mBody.add(chunk) else mBody.add(
                    chunk.subchunk(bodyStart, bodySize)
                )
            }
            if (chunkedComplete || !mReassembleChunks) mChunkedEncoding = false
            if ((mContentLength <= 0 || !mReassembleChunks)
                && !mChunkedEncoding
            ) {
                // Reassemble the chunks (NOTE: gzip is applied only after all the chunks are collected)
                val headers: PayloadChunk = reassembleChunks(mHeaders)
                val body: PayloadChunk? = if (mBody.size > 0) reassembleChunks(mBody) else null

                //log_d("mContentLength=" + mContentLength + ", mReassembleChunks=" + mReassembleChunks + ", mChunkedEncoding=" + mChunkedEncoding);

                // Decode body
                if (body != null && mContentEncoding != ContentEncoding.UNKNOWN) decodeBody(body)
                val toAdd: PayloadChunk = if (body != null) {
                    // Reassemble headers and body into a single chunk
                    val reassembly = ByteArray(headers.payload.size + body.payload.size)
                    System.arraycopy(headers.payload, 0, reassembly, 0, headers.payload.size)
                    System.arraycopy(
                        body.payload,
                        0,
                        reassembly,
                        headers.payload.size,
                        body.payload.size
                    )
                    body.withPayload(reassembly)
                } else headers
                if (mInvalidHttp) toAdd.type = PayloadChunk.ChunkType.RAW
                toAdd.contentType = mContentType!!
                toAdd.path = mPath!!
                mListener.onChunkReassembled(toAdd)
                reset() // mReadingHeaders = true
            }
            if (newBodyStart > 0 && chunk.payload.size > newBodyStart) {
                // Part of this chunk should be processed as a new chunk
                log_d("Continue from $newBodyStart")
                handleChunk(chunk.subchunk(newBodyStart, chunk.payload.size - newBodyStart))
            }
        }
    }

    private fun decodeBody(body: PayloadChunk) {
        var inputStream: InputStream? = null

        //log_d("Decoding as " + mContentEncoding.name().toLowerCase());
        try {
            ByteArrayInputStream(body.payload).use { bis ->
                when (mContentEncoding) {
                    ContentEncoding.GZIP -> inputStream = GZIPInputStream(bis)
                    ContentEncoding.DEFLATE -> inputStream =
                        InflaterInputStream(bis, Inflater(true))

                    ContentEncoding.BROTLI -> inputStream = BrotliInputStream(bis)
                    ContentEncoding.UNKNOWN -> TODO()
                }
                if (inputStream != null) {
                    ByteArrayOutputStream().use { bos ->
                        val buf = ByteArray(1024)
                        var read: Int
                        while (inputStream!!.read(buf).also { read = it } != -1) bos.write(
                            buf,
                            0,
                            read
                        )

                        // success
                        body.payload = bos.toByteArray()
                    }
                }
            }
        } catch (ignored: IOException) {
            log_d(mContentEncoding.name.lowercase(Locale.getDefault()) + " decoding failed")
            //ignored.printStackTrace();
        } finally {
            safeClose(inputStream)
        }
    }

    private fun reassembleChunks(chunks: ArrayList<PayloadChunk>): PayloadChunk {
        if (chunks.size == 1) return chunks[0]
        var size = 0
        for (chunk in chunks) size += chunk.payload.size
        val reassembly = ByteArray(size)
        var sofar = 0
        for (chunk in chunks) {
            System.arraycopy(chunk.payload, 0, reassembly, sofar, chunk.payload.size)
            sofar += chunk.payload.size
        }
        return chunks[0].withPayload(reassembly)
    }
}