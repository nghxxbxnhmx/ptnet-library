package com.ftel.ptnetlibrary.ndpi.pcap_dump

import android.content.Context
import android.util.Log
import com.ftel.ptnetlibrary.ndpi.Utils
import com.ftel.ptnetlibrary.ndpi.Utils.getEndOfHTTPHeaders
import com.ftel.ptnetlibrary.ndpi.Utils.getUniquePcapFileName
import com.ftel.ptnetlibrary.ndpi.Utils.safeClose
import com.ftel.ptnetlibrary.ndpi.interfaces.PcapDumper
import com.ftel.ptnetlibrary.ndpi.services.CaptureService
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.StringTokenizer
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit


class HTTPServer : PcapDumper, Runnable {
    private val TAG = "HTTPServer"
    private val PCAP_MIME = "application/vnd.tcpdump.pcap"
    private val PCAPNG_MIME = "application/x-pcapng"
    val MAX_CLIENTS = 8
    private var mSocket: ServerSocket? = null
    private var mRunning = false
    private var mThread: Thread? = null
    var mPort = 0
    var mPcapngFormat: Boolean = false
    var mMimeType: String? = null
    var mContext: Context? = null

    // Shared state, must be synchronized
    private val mClients: ArrayList<ClientHandler> = ArrayList<ClientHandler>()

    constructor()
    constructor(context: Context, port: Int, pcapngFormat: Boolean) {
        mPort = port
        mContext = context
        mPcapngFormat = pcapngFormat
        mMimeType = if (pcapngFormat) PCAPNG_MIME else PCAP_MIME;
    }


    class ChunkedOutputStream(out: OutputStream?) : FilterOutputStream(out) {
        @Throws(IOException::class)
        override fun write(data: ByteArray) {
            // Chunked transfer coding
            // https://datatracker.ietf.org/doc/html/rfc2616#section-3.6.1
            out.write(String.format("%x\r\n", data.size).toByteArray())
            out.write(data)
            out.write("\r\n".toByteArray())
            out.flush()
        }

        @Throws(IOException::class)
        fun finish() {
            // Chunked transfer termination
            out.write("0\r\n\r\n".toByteArray())
        }
    }


    /* Handles a single HTTP client. The normal workflow is:
     *  1. if isReadyForData then sendChunk
     *  2. if isClosed then remove this client
     *
     * No need for synchronization because sendChunk is only called when the runnable has terminated
     * (see isReadyForData).
     */
    inner class ClientHandler : Runnable {
        val INPUT_BUFSIZE = 1024
        var mSocket: Socket? = null
        var mInputStream: InputStream? = null
        var mOutputStream: OutputStream? = null
        var mFname: String? = null
        var mMimeType: String? = null
        var mChunkedOutputStream: ChunkedOutputStream? = null
        var mHasError = false
        var mReadyForData = false
        var mHeaderSent = false
        var mIsClosed = false

        constructor()
        constructor(socket: Socket, mimeType: String, fname: String) {
            mSocket = socket;
            mFname = fname;
            mInputStream = mSocket!!.getInputStream();
            mOutputStream = mSocket!!.getOutputStream();
            mMimeType = mimeType;
        }

        fun close(error: String?) {
            if (isClosed()) return
            if (error != null) {
                Log.i(TAG, "Client error: $error")
                mHasError = true
            } else if (mReadyForData) {
                try {
                    // Terminate the chunked stream
                    mChunkedOutputStream!!.finish()
                } catch (ignored: IOException) {
                }
            }
            safeClose(mChunkedOutputStream)
            safeClose(mOutputStream)
            safeClose(mInputStream)
            safeClose(mSocket)
            mIsClosed = true
        }

        fun stop() {
            // if running, will trigger a IOException
            safeClose(mSocket)
        }

        override fun run() {
            val buf = ByteArray(INPUT_BUFSIZE)
            var sofar = 0
            var req_size = 0
            try {
                while (req_size <= 0) {
                    sofar += mInputStream!!.read(buf, sofar, buf.size - sofar)
                    req_size = getEndOfHTTPHeaders(buf)
                }
                Log.d(TAG, "Request headers end at $req_size")
                BufferedReader(
                    InputStreamReader(
                        ByteArrayInputStream(
                            buf,
                            0,
                            req_size
                        )
                    )
                ).use { reader ->
                    val line = reader.readLine()
                    if (line == null) {
                        close("Bad request")
                        return
                    }
                    val tk = StringTokenizer(line)
                    val method = tk.nextToken()
                    val url = tk.nextToken()
                    if (method != "GET") {
                        close("Bad request method")
                        return
                    }
                    if (url == "/") {
                        redirectToPcap()
                        close(null)
                    } else {
                        Log.d(TAG, "URL: $url")

                        // NOTE: compressing with gzip is almost useless as most HTTP data is already
                        // gzip-compressed
                        mOutputStream!!.write(
                            """HTTP/1.1 200 OK
Content-Type: $mMimeType
Connection: close
Transfer-Encoding: chunked

""".toByteArray()
                        )
                        mOutputStream!!.flush()
                        Log.d(TAG, "Ready for data")
                        mChunkedOutputStream = ChunkedOutputStream(mOutputStream)
                        mReadyForData = true
                    }
                }
            } catch (e: IOException) {
                close(e.localizedMessage)
            } catch (e: NoSuchElementException) {
                close(e.localizedMessage)
            }
        }

        /* Sends a 302 redirect to allow saving the PCAP file with a specific name */
        @Throws(IOException::class)
        private fun redirectToPcap() {
            Log.d(TAG, "Redirecting to PCAP: $mFname")
            mOutputStream!!.write(
                """HTTP/1.1 302 Found
    Location: /$mFname
    
    """.toByteArray()
            )
        }

        // Returns true if the client socket is closed
        fun isClosed(): Boolean {
            return mIsClosed
        }

        fun isReadyForData(): Boolean {
            return mReadyForData
        }

        // Send a chunk of data
        fun sendChunk(data: ByteArray?) {
            try {
                if (!mHeaderSent) {
                    mChunkedOutputStream!!.write(CaptureService.getPcapHeader()!!)
                    mHeaderSent = true
                }

                //Log.d(TAG, "+CHUNK [" + data.length + "]");
                mChunkedOutputStream!!.write(data!!)
            } catch (e: IOException) {
                close(e.localizedMessage)
            }
        }
    }

    @Throws(IOException::class)
    override fun startDumper() {
        mSocket = ServerSocket()
        mSocket!!.setReuseAddress(true)
        mSocket!!.bind(InetSocketAddress(mPort))
        mRunning = true
        mThread = Thread(this)
        mThread!!.start()
    }

    override fun run() {
        // NOTE: threads only handle the initial client communication.
        // After isReadyForData, clients are handled in dumpData.
        val pool = Executors.newFixedThreadPool(MAX_CLIENTS)
        while (mRunning) {
            try {
                val client = mSocket!!.accept()
                synchronized(this) {
                    if (mClients.size >= MAX_CLIENTS) {
                        Log.w(TAG, "Clients limit reached")
                        safeClose(client)
                    }
                }
                Log.i(
                    TAG,
                    "New client: " + client.getInetAddress().hostAddress + ":" + client.getPort()
                )
                val handler: ClientHandler =
                    ClientHandler(
                        client,
                        mMimeType!!,
                        getUniquePcapFileName(mContext, mPcapngFormat)
                    )
                try {
                    // will fail if pool is full
                    pool.submit(handler)
                    synchronized(this) { mClients.add(handler) }
                } catch (e: RejectedExecutionException) {
                    Log.w(TAG, e.localizedMessage!!)
                    safeClose(client)
                }
            } catch (e: IOException) {
                if (!mRunning) Log.d(TAG, "Got termination request") else Log.d(
                    TAG,
                    e.localizedMessage!!
                )
            }
        }
        safeClose(mSocket)

        // Terminate the running clients threads
        pool.shutdown()
        synchronized(this) {
            // Possibly wake clients blocked on read
            for (client in mClients) {
                if (!client.isReadyForData()) client.stop()
            }
        }
        while (true) {
            try {
                if (pool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS)) break
            } catch (ignored: InterruptedException) {
            }
        }

        // Close the clients
        synchronized(this) {
            for (client in mClients) {
                if (!client.isClosed()) client.close(null)
            }
            mClients.clear()
        }
    }

    @Throws(IOException::class)
    override fun stopDumper() {
        mRunning = false

        // Generate a socket exception
        mSocket!!.close()
        while (mThread != null && mThread!!.isAlive) {
            try {
                Log.d(TAG, "Joining HTTP thread...")
                mThread!!.join()
            } catch (ignored: InterruptedException) {
            }
        }
    }

    override fun getBpf(): String {
        return ("not (host " + Utils.getLocalIPAddress(mContext!!)).toString() + " and tcp port " + mPort + ")"
    }

    @Throws(IOException::class)
    override fun dumpData(data: ByteArray?) {
        synchronized(this) {
            val it =
                mClients.iterator()
            while (it.hasNext()) {
                val client = it.next()
                if (client.isReadyForData()) client.sendChunk(data)
                if (client.isClosed()) {
                    it.remove()
                    Log.d(TAG, "Client closed, active clients: " + mClients.size)
                }
            }
        }
    }
}