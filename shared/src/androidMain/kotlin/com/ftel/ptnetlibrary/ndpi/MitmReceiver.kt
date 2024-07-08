package com.ftel.ptnetlibrary.ndpi

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import android.util.SparseArray
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.ftel.ptnetlibrary.ndpi.Utils.safeClose
import com.ftel.ptnetlibrary.ndpi.Utils.showToastLong
import com.ftel.ptnetlibrary.ndpi.interfaces.ConnectionsListener
import com.ftel.ptnetlibrary.ndpi.models.*
import com.ftel.ptnetlibrary.ndpi.models.MitmAPI.MitmConfig
import com.ftel.ptnetlibrary.ndpi.models.PayloadChunk.ChunkType
import com.ftel.ptnetlibrary.ndpi.services.CaptureService
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.*


import com.ftel.ptnetlibrary.R

class MitmReceiver : Runnable, ConnectionsListener, MitmListener {
    private val TAG = "MitmReceiver"
    val TLS_DECRYPTION_PROXY_PORT = 7780
    private var mThread: Thread? = null
    private lateinit var mReg: ConnectionsRegister
    private lateinit var mContext: Context
    private lateinit var mAddon: MitmAddon
    private lateinit var mConfig: MitmConfig
    private var mPcapngFormat = false
    private var proxyStatus: MutableLiveData<Status> = MutableLiveData<Status>(Status.NOT_STARTED)
    private var mSocketFd: ParcelFileDescriptor? = null
    private var mKeylog: BufferedOutputStream? = null

    // Shared state
    private val mPortToConnId: LruCache<Int, Int> = LruCache(64)
    private val mPendingMessages: SparseArray<ArrayList<PendingMessage>> =
        SparseArray<ArrayList<PendingMessage>>()


    private enum class MsgType {
        UNKNOWN, RUNNING, TLS_ERROR, HTTP_ERROR, HTTP_REQUEST,
        HTTP_REPLY, TCP_CLIENT_MSG, TCP_SERVER_MSG, TCP_ERROR, WEBSOCKET_CLIENT_MSG,
        WEBSOCKET_SERVER_MSG, DATA_TRUNCATED, MASTER_SECRET, LOG, JS_INJECTED
    }

    private class PendingMessage {
        lateinit var type: MsgType
        lateinit var msg: ByteArray
        var port: Int = 0
        var pendingSince: Long = 0
        var `when`: Long = 0

        constructor(type: MsgType, msg: ByteArray, port: Int, `when`: Long) {
            this.type = type
            this.msg = msg
            this.port = port
            this.pendingSince = SystemClock.elapsedRealtime()
            this.`when` = `when`
        }
    }


    enum class Status {
        NOT_STARTED,
        STARTING,
        START_ERROR,
        RUNNING
    }

    constructor()

    constructor(ctx: Context, settings: CaptureSettings, proxyAuth: String?) {
        mContext = ctx
        mReg = CaptureService.requireConnsRegister()
        mAddon = MitmAddon(mContext, this)
        mPcapngFormat = settings.pcapng_format
        mConfig = MitmConfig()
        mConfig.proxyPort = TLS_DECRYPTION_PROXY_PORT
        mConfig.proxyAuth = proxyAuth
        mConfig.dumpMasterSecrets = CaptureService.getDumpMode() !== Prefs.DumpMode.NONE
        mConfig.additionalOptions = settings.mitmproxy_opts
        mConfig.shortPayload = !settings.full_payload

        /* upstream certificate verification is disabled because the app does not provide a way to let the user
           accept a given cert. Moreover, it provides a workaround for a bug with HTTPS proxies described in
           https://github.com/mitmproxy/mitmproxy/issues/5109 */
        mConfig.sslInsecure = true

        // root capture uses transparent mode (redirection via iptables)
        mConfig.transparentMode = settings.root_capture
        getKeylogFilePath(mContext).delete()
    }

    private fun getKeylogFilePath(ctx: Context): File {
        return File(ctx.cacheDir, "SSLKEYLOG.txt")
    }

    @Throws(IOException::class)
    fun start(): Boolean {
        Log.d(TAG, "starting")
        proxyStatus.postValue(Status.STARTING)
        if (!mAddon.connect(Context.BIND_IMPORTANT)) {
            showToastLong(mContext, R.string.mitm_start_failed)
            return false
        }
        mReg.addListener(this)
        return true
    }

    @Throws(IOException::class)
    fun stop() {
        Log.d(TAG, "stopping")
        mReg.removeListener(this)
        val fd = mSocketFd
        mSocketFd = null
        safeClose(fd) // possibly wake mThread

        // send explicit stop message, as the addon may not be waked when the fd is closed
        mAddon.stopProxy()

        // on some devices, calling close on the socket is not enough to stop the thread,
        // the service must be unbound
        mAddon.disconnect()
        while (mThread != null && mThread!!.isAlive) {
            try {
                Log.d(TAG, "Joining receiver thread...")
                mThread!!.join()
            } catch (ignored: InterruptedException) {
            }
        }
        mThread = null
        Log.d(TAG, "stop done")
    }

    private fun isSent(type: MsgType): Boolean {
        return when (type) {
            MsgType.HTTP_REQUEST, MsgType.TCP_CLIENT_MSG, MsgType.WEBSOCKET_CLIENT_MSG -> true
            else -> false
        }
    }

    private fun getChunkType(type: MsgType): ChunkType {
        return when (type) {
            MsgType.HTTP_REQUEST, MsgType.HTTP_REPLY -> ChunkType.HTTP
            MsgType.WEBSOCKET_CLIENT_MSG, MsgType.WEBSOCKET_SERVER_MSG -> ChunkType.WEBSOCKET
            else -> ChunkType.RAW
        }
    }

    private fun handleMessage(conn: ConnectionDescriptor, type: MsgType, message: ByteArray, tstamp: Long) {
        // NOTE: we are possibly accessing the conn concurrently
        if ((type === MsgType.TLS_ERROR) || (type === MsgType.HTTP_ERROR) || (type === MsgType.TCP_ERROR)) {
            conn.decryptionError = String(message, StandardCharsets.US_ASCII)

            // see ConnectionDescriptor.processUpdate
            if (conn.status == ConnectionDescriptor.CONN_STATUS_CLOSED) conn.status =
                ConnectionDescriptor.CONN_STATUS_CLIENT_ERROR
        } else if (type === MsgType.DATA_TRUNCATED) {
            conn.setPayloadTruncatedByAddon()
        } else if (type === MsgType.JS_INJECTED) {
            conn.jsInjectedScripts = String(message, StandardCharsets.US_ASCII)
        } else conn.addPayloadChunkMitm(PayloadChunk(message, getChunkType(type), isSent(type), tstamp))
    }

    @Synchronized
    private fun addPendingMessage(pending: PendingMessage) {
        // Purge unresolved connections (should not happen, just in case)
        if (mPendingMessages.size() > 32) {
            val now = SystemClock.elapsedRealtime()

            for (i in mPendingMessages.size() - 1 downTo 0) {
                val pp = mPendingMessages.valueAt(i)

                if ((now - pp[0].pendingSince) > 5000 /* 5 sec */) {
                    Log.w(TAG, "Dropping " + pp.size + " old messages")
                    mPendingMessages.remove(mPendingMessages.keyAt(i))
                }
            }
        }

        val idx = mPendingMessages.indexOfKey(pending.port)
        val pp: ArrayList<PendingMessage>

        if (idx < 0) {
            pp = ArrayList()
            mPendingMessages.put(pending.port, pp)
        } else pp = mPendingMessages.valueAt(idx)

        pp.add(pending)
    }


    override fun run() {
        if (mSocketFd == null) {
            Log.e(TAG, "Null socket, abort")
            proxyStatus.postValue(Status.NOT_STARTED)
            return
        }

        Log.i(TAG, "Receiving data...")
        try {
            ParcelFileDescriptor.AutoCloseInputStream(mSocketFd).use { autoCloseInputStream ->
                BufferedReader(InputStreamReader(autoCloseInputStream)).use { reader ->
                    while (mAddon.isConnected()) {
                        val header = reader.readLine()

                        if (header == null) {
                            // Received when the addon is stopped
                            CaptureService.stopService()
                            break
                        }

                        var msgType: String
                        var port: Int
                        var msgLen: Int
                        var tstamp: Long

                        val tk = StringTokenizer(header, ":")

                        try {
                            // timestamp:port:msg_type:msg_length\n
                            val tkTStamp = tk.nextToken(":")
                            val tkPort = tk.nextToken()
                            msgType = tk.nextToken()
                            val tkLen = tk.nextToken()

                            tstamp = tkTStamp.toLong()
                            port = tkPort.toInt()
                            msgLen = tkLen.toInt()

                            if (msgLen < 0 || msgLen > 67108864) { // max 64 MB
                                Log.w(TAG, "Ignoring bad message length: $msgLen")
                                reader.skip(msgLen.toLong())  // Skip the invalid message
                                continue
                            }

                            val type: MsgType = parseMsgType(msgType)

                            val msg = ByteArray(msgLen)

                            // Read the message bytes into the ByteArray
                            var totalBytesRead = 0
                            while (totalBytesRead < msgLen) {
                                val bytesRead = autoCloseInputStream.read(msg, totalBytesRead, msgLen - totalBytesRead)
                                if (bytesRead == -1) break  // End of stream
                                totalBytesRead += bytesRead
                            }

                            when (type) {
                                MsgType.MASTER_SECRET -> logMasterSecret(msg)
                                MsgType.LOG -> handleLog(msg)
                                MsgType.RUNNING -> {
                                    Log.i(TAG, "MITM proxy is running")
                                    proxyStatus.postValue(Status.RUNNING)
                                }

                                else -> {
                                    val conn = getConnByLocalPort(port)
                                    // Log.d(TAG, "MSG.${type.name}[$msgLen B]: port=$port, match=${conn != null}")
                                    if (conn != null) handleMessage(conn, type, msg, tstamp)
                                    else addPendingMessage(PendingMessage(type, msg, port, tstamp))
                                }
                            }
                        } catch (e: NoSuchElementException) {
                            CaptureService.requireInstance()
                                .reportError("[BUG] Invalid header received from the mitm plugin")
                            CaptureService.stopService()
                            break
                        } catch (e: NumberFormatException) {
                            CaptureService.requireInstance()
                                .reportError("[BUG] Invalid header received from the mitm plugin")
                            CaptureService.stopService()
                            break
                        }
                    }
                }
            }
        } catch (e: IOException) {
            if (mSocketFd != null) // ignore termination
                e.printStackTrace()
        } finally {
            safeClose(mKeylog)
            mKeylog = null
        }

        if (proxyStatus.value == Status.STARTING) proxyStatus.postValue(Status.START_ERROR)
        else proxyStatus.postValue(Status.NOT_STARTED)

        Log.i(TAG, "End receiving data")
    }

    private fun parseMsgType(str: String): MsgType {
        return when (str) {
            "running" -> MsgType.RUNNING
            "tls_err" -> MsgType.TLS_ERROR
            "http_err" -> MsgType.HTTP_ERROR
            "http_req" -> MsgType.HTTP_REQUEST
            "http_rep" -> MsgType.HTTP_REPLY
            "tcp_climsg" -> MsgType.TCP_CLIENT_MSG
            "tcp_srvmsg" -> MsgType.TCP_SERVER_MSG
            "tcp_err" -> MsgType.TCP_ERROR
            "ws_climsg" -> MsgType.WEBSOCKET_CLIENT_MSG
            "ws_srvmsg" -> MsgType.WEBSOCKET_SERVER_MSG
            "trunc" -> MsgType.DATA_TRUNCATED
            "secret" -> MsgType.MASTER_SECRET
            "log" -> MsgType.LOG
            "js_inject" -> MsgType.JS_INJECTED
            else -> MsgType.UNKNOWN
        }
    }

    @Throws(IOException::class)
    private fun logMasterSecret(masterSecret: ByteArray) {
        if (mPcapngFormat) CaptureService.dumpMasterSecret(masterSecret)
        else {
            if (mKeylog == null) mKeylog = BufferedOutputStream(
                mContext.contentResolver.openOutputStream(
                    Uri.fromFile(getKeylogFilePath(mContext)), "rwt"
                )
            )

            mKeylog!!.write(masterSecret)
            mKeylog!!.write(0xa)
        }
    }

    private fun handleLog(message: ByteArray) {
        try {
            val msg = String(message, StandardCharsets.US_ASCII)

            // format: 1:message
            if (msg.length < 3) return

            val lvl = msg.substring(0, 1).toInt()
            Log.d(TAG, "handleLog: MITMADDON_LOGGER ($lvl - ${msg.substring(2)})")
//            Log.level(Log.MITMADDON_LOGGER, lvl, msg.substring(2))
        } catch (ignored: java.lang.NumberFormatException) {
        }
    }

    fun getProxyStatus(): Status? {
        return proxyStatus.value
    }

    fun observeStatus(lifecycleOwner: LifecycleOwner?, observer: Observer<Status>) {
        proxyStatus.observe(lifecycleOwner!!, observer)
    }

    override fun connectionsChanges(numOfConnection: Int) {}

    override fun connectionsRemoved(start: Int, descriptorArray: Array<ConnectionDescriptor>?) {}

    override fun connectionsUpdated(positions: IntArray?) {}

    override fun connectionsAdded(start: Int, descriptorArray: Array<ConnectionDescriptor>?) {
        synchronized(this) {
            // Save the latest port->ID mapping
            if (descriptorArray != null) {
                for (conn in descriptorArray) {
                    //Log.d(TAG, "[+] port " + conn.local_port)
                    mPortToConnId.put(conn.localPort, conn.incrId)

                    // Check if the message has already been received
                    val pendingIdx = mPendingMessages.indexOfKey(conn.localPort)
                    if (pendingIdx >= 0) {
                        val pp = mPendingMessages.valueAt(pendingIdx)
                        mPendingMessages.removeAt(pendingIdx)

                        for (pending in pp) {
                            //Log.d(TAG, "(pending) MSG." + pending.type.name() + "[" + pending.message.length + " B]: port=" + pending.port)
                            handleMessage(conn, pending.type, pending.msg, pending.`when`)
                        }
                    }
                }
            }
        }
    }

    override fun onMitmServiceConnect() {
        // Ensure that no other instance is running
        mAddon.stopProxy()

        // when connected, verify that the certificate is installed before starting the proxy.
        // will continue on onMitmGetCaCertificateResult.
        if (!mAddon.requestCaCertificate())
            mAddon.disconnect()
    }

    override fun onMitmGetCaCertificateResult(ca_pem: String?) {
        if (!MitmAddon.isCAInstallationSkipped(mContext) && !Utils.isCAInstalled(ca_pem)) {
            // The certificate has been uninstalled from the system
            Utils.showToastLong(mContext, R.string.cert_reinstall_required)
            MitmAddon.setDecryptionSetupDone(mContext, false)
            CaptureService.stopService()
            return
        }

        // Certificate installation verified, start the proxy
        mSocketFd = mAddon.startProxy(mConfig)
        if (mSocketFd == null) {
            mAddon.disconnect()
            return
        }

        if (MitmAddon.isDozeEnabled(mContext)) {
            Utils.showToastLong(mContext, R.string.mitm_doze_notice)
            mAddon.disableDoze()
        }

        mThread?.interrupt()

        mThread = Thread(this)
        mThread?.start()
    }


    override fun onMitmServiceDisconnect() {
        // Stop the capture if running, CaptureService will call MitmReceiver::stop
        CaptureService.stopService()
    }

    fun getConnByLocalPort(local_port: Int): ConnectionDescriptor? {
        var connId: Int?

        synchronized(this) {
            connId = mPortToConnId[local_port]
        }

        if (connId == null) return null

        val conn: ConnectionDescriptor? = mReg.getConnById(connId!!)
        if ((conn == null) || (conn.localPort != local_port)) return null

        // success
        return conn
    }
}