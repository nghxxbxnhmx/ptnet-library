package com.ftel.ptnetlibrary.ndpi.models

import android.content.Context
import com.ftel.ptnetlibrary.ndpi.AppsResolver
import com.ftel.ptnetlibrary.ndpi.HTTPReassembly
import com.ftel.ptnetlibrary.ndpi.HTTPReassembly.ReassemblyListener
import com.ftel.ptnetlibrary.ndpi.services.CaptureService
import java.io.Serializable
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

import com.ftel.ptnetlibrary.R

class ConnectionDescriptor : Serializable {
    private val captureService = CaptureService.getInstance()

    constructor()

    companion object{
        // sync with zdtun_conn_status_t
        val CONN_STATUS_NEW: Int = 0
        val CONN_STATUS_CONNECTING = 1
        val CONN_STATUS_CONNECTED = 2
        val CONN_STATUS_CLOSED = 3
        val CONN_STATUS_ERROR = 4
        val CONN_STATUS_SOCKET_ERROR = 5
        val CONN_STATUS_CLIENT_ERROR = 6
        val CONN_STATUS_RESET = 7
        val CONN_STATUS_UNREACHABLE = 8
    }
    // This is an high level status which abstracts the zdtun_conn_status_t
    enum class Status {
        STATUS_INVALID,
        STATUS_ACTIVE,
        STATUS_CLOSED,
        STATUS_UNREACHABLE,
        STATUS_ERROR
    }


    enum class DecryptionStatus {
        INVALID,
        ENCRYPTED,
        CLEARTEXT,
        DECRYPTED,
        NOT_DECRYPTABLE,
        WAITING_DATA,
        ERROR
    }


    enum class FilteringStatus {
        INVALID,
        ALLOWED,
        BLOCKED
    }


    /* Metadata */
    var ipver = 0
    var ipproto = 0
    lateinit var srcIp: String
    lateinit var dstIp: String
    var srcPort = 0
    var dstPort = 0
    var localPort = 0 // in VPN mode, this is the local port of the Internet connection


    /* Data */
    var firstSeen: Long = 0
    var lastSeen: Long = 0
    var payloadLength: Long = 0
    var sentBytes: Long = 0
    var rcvdBytes: Long = 0
    var sentPkts = 0
    var rcvdPkts = 0
    var blockedPkts = 0
    var info: String? = null
    lateinit var url: String
    lateinit var l7proto: String
    private var payloadChunks: ArrayList<PayloadChunk?>? = null // must be synchronized

    var uid: Int = 0
    var ifidx = 0
    var incrId = 0
    private var mitmDecrypt = false // true if the connection is under mitm for TLS decryption

    var status = 0
    private var tcpFlags = 0
    private var blacklistedIp = false
    private var blacklistedHost = false
    var isBlocked: Boolean = false
    private var portMappingApplied = false
    private var decryptionIgnored = false
    var netdBlockMissed = false
    private var payloadTruncated = false
    private var encryptedL7 = false // application layer is encrypted (e.g. TLS)
    var encryptedPayload: Boolean =
        false // actual payload is encrypted (e.g. telegram - see Utils.hasEncryptedPayload)

    var decryptionError: String? = null
    var jsInjectedScripts: String? = null
    lateinit var country: String
    lateinit var asn: Geomodel.ASN

    /* Internal */
    var alerted = false
    var blockAccounted = false

    constructor(
        incrId: Int, ipver: Int, ipproto: Int, srcIp: String, dstIp: String,
        srcPort: Int, dstPort: Int, localPort: Int, uid: Int, ifidx: Int,
        mitmDecrypt: Boolean, `when`: Long
    ) {
        this.incrId = incrId
        this.ipver = ipver
        this.ipproto = ipproto
        this.srcIp = srcIp
        this.dstIp = dstIp
        this.srcPort = srcPort
        this.dstPort = dstPort
        this.localPort = localPort
        this.uid = uid
        this.ifidx = ifidx
        this.firstSeen = `when`.also { this.lastSeen = it }
        this.l7proto = ""
        this.country = ""
        this.asn = Geomodel.ASN()
        this.payloadChunks = ArrayList<PayloadChunk?>()
        this.mitmDecrypt = mitmDecrypt
    }

    fun processUpdate(update: ConnectionUpdate) {
        // The "update_type" is used to limit the amount of data sent via the JNI
        if ((update.updateType and ConnectionUpdate().UPDATE_STATS) != 0) {
            sentBytes = update.sentBytes
            rcvdBytes = update.rcvdBytes
            sentBytes = update.sentBytes
            rcvdPkts = update.rcvdPkts
            blockedPkts = update.blockedPkts
            status = update.status and 0x00FF
            portMappingApplied = (update.status and 0x2000) != 0
            decryptionIgnored = (update.status and 0x1000) != 0
            netdBlockMissed = (update.status and 0x0800) != 0
            isBlocked = (update.status and 0x0400) != 0
            blacklistedHost = (update.status and 0x0200) != 0
            blacklistedIp = (update.status and 0x0100) != 0
            lastSeen = update.lastSeen
            tcpFlags = update.tcpFlags // NOTE: only for root capture

            // see MitmReceiver.handlePayload
            if ((status == CONN_STATUS_CLOSED) && (decryptionError != null)) {
                status = CONN_STATUS_CLIENT_ERROR
            }

            // with mitm we account the TLS payload length instead
            if (!mitmDecrypt) {
                payloadLength = update.payloadLength
            }
        }

        if ((update.updateType and ConnectionUpdate().UPDATE_INFO) != 0) {
            info = update.info!!
            url = update.url!!
            l7proto = update.l7proto!!
            encryptedL7 =
                ((update.infoFlags and ConnectionUpdate().UPDATE_INFO_FLAG_ENCRYPTED_L7) != 0)
        }
        if ((update.updateType and ConnectionUpdate().UPDATE_PAYLOAD) != 0) {
            // Payload for decryptable connections should be received via the MitmReceiver
            assert(decryptionIgnored || isNotDecryptable())

            // Some pending updates with payload may still be received after low memory has been
            // triggered and payload disabled
            if (!CaptureService.isLowMemory()) {
                synchronized(this) {
                    if (update.payloadChunks != null) payloadChunks!!.addAll(update.payloadChunks!!)
                    payloadTruncated = update.payloadTruncated
                }
            }
        }
    }

    fun getDstAddr(): InetAddress? {
        return try {
            InetAddress.getByName(dstIp)
        } catch (e: UnknownHostException) {
            e.printStackTrace()
            null
        }
    }

    fun getStatus(): Status {
        return if (status >= CONN_STATUS_CLOSED) {
            when (status) {
                CONN_STATUS_CLOSED, CONN_STATUS_RESET -> Status.STATUS_CLOSED
                CONN_STATUS_UNREACHABLE -> Status.STATUS_UNREACHABLE
                else -> Status.STATUS_ERROR
            }
        } else Status.STATUS_ACTIVE
    }

    private fun getStatusLabel(status: Status?, ctx: Context): String {
        val resid: Int
        when (status) {
            Status.STATUS_ACTIVE -> resid = R.string.conn_status_active
            Status.STATUS_CLOSED -> resid = R.string.conn_status_closed
            Status.STATUS_UNREACHABLE -> resid = R.string.conn_status_unreachable
            else -> resid = R.string.error
        }
        return ctx.getString(resid)
    }

    fun getStatusLabel(ctx: Context): String {
        return getStatusLabel(getStatus(), ctx)
    }

    fun matches(res: AppsResolver, filter: String): Boolean {
        val filter = filter.lowercase(Locale.getDefault())
        val app = res.getAppByUid(uid, 0)
//        return info != null && info.contains(filter) ||
//                dst_ip.contains(filter) || l7proto.lowercase(Locale.getDefault()) == filter || uid.toString() == filter ||
//                dst_port.toString()
//                    .contains(filter) || src_port.toString() == filter || app != null && app.matches(
//            filter,
//            true
//        )
        return info!!.contains(filter) || dstIp.contains(filter) || l7proto.lowercase(Locale.getDefault()) == filter || uid.toString() == filter || dstPort.toString()
            .contains(filter) || srcPort.toString() == filter || app != null && app.matches(
            filter,
            true
        )
    }

    fun getDecryptionStatus(): DecryptionStatus {
//        return if (isCleartext()) DecryptionStatus.CLEARTEXT else if (decryption_error != null) DecryptionStatus.ERROR else if (isNotDecryptable()) DecryptionStatus.NOT_DECRYPTABLE else if (decryptionIgnored) DecryptionStatus.ENCRYPTED else if (isDecrypted()) DecryptionStatus.DECRYPTED else DecryptionStatus.WAITING_DATA
        return if (isCleartext()) DecryptionStatus.CLEARTEXT else DecryptionStatus.ERROR
    }

    private fun getDecryptionStatusLabel(status: DecryptionStatus?, ctx: Context): String {
        val resId = when (status) {
            DecryptionStatus.CLEARTEXT -> R.string.not_encrypted
            DecryptionStatus.NOT_DECRYPTABLE -> R.string.not_decryptable
            DecryptionStatus.DECRYPTED -> R.string.decrypted
            DecryptionStatus.ENCRYPTED -> R.string.status_encrypted
            DecryptionStatus.WAITING_DATA -> R.string.waiting_application_data
            else -> R.string.error
        }
        return ctx.getString(resId)
    }

    fun getDecryptionStatusLabel(ctx: Context): String {
        return getDecryptionStatusLabel(getDecryptionStatus(), ctx)
    }

    private fun getSentTcpFlags(): Int {
        return tcpFlags shr 8
    }

    fun getRcvdTcpFlags(): Int {
        return tcpFlags and 0xFF
    }

    private fun isBlacklistedIp(): Boolean {
        return blacklistedIp
    }

    fun isBlacklistedHost(): Boolean {
        return blacklistedHost
    }

    fun isBlacklisted(): Boolean {
        return isBlacklistedIp() || isBlacklistedHost()
    }

    fun setPayloadTruncatedByAddon() {
        // only for the mitm addon
        assert(!isNotDecryptable())
        payloadTruncated = true
    }

    fun isPayloadTruncated(): Boolean {
        return payloadTruncated
    }

    fun isPortMappingApplied(): Boolean {
        return portMappingApplied
    }

    private fun isNotDecryptable(): Boolean {
        return !decryptionIgnored && (encryptedPayload || !mitmDecrypt)
    }

    fun isDecrypted(): Boolean {
        return !decryptionIgnored && !isNotDecryptable() && getNumPayloadChunks() > 0
    }

    fun isCleartext(): Boolean {
        return !encryptedPayload && !encryptedL7
    }

    @Synchronized
    fun getNumPayloadChunks(): Int {
        return payloadChunks!!.size
    }

    @Synchronized
    fun getPayloadChunk(idx: Int): PayloadChunk? {
        return if (getNumPayloadChunks() <= idx) null else payloadChunks!![idx]
    }

    @Synchronized
    fun addPayloadChunkMitm(chunk: PayloadChunk) {
        payloadChunks!!.add(chunk)
        payloadLength += chunk.payload.size
    }

    @Synchronized
    fun dropPayload() {
        payloadChunks!!.clear()
    }

    @Synchronized
    private fun hasHttp(isSent: Boolean): Boolean {
        for (chunk in payloadChunks!!) {
            if (chunk != null) {
                if (chunk.isSent == isSent) return chunk.type === PayloadChunk.ChunkType.HTTP
            }
        }
        return false
    }

    fun hasHttpRequest(): Boolean {
        return hasHttp(true)
    }

    fun hasHttpResponse(): Boolean {
        return hasHttp(false)
    }

    @Synchronized
    private fun getHttp(isSent: Boolean): String? {
        if (getNumPayloadChunks() == 0) return ""

        // Need to wrap the String to set it from the lambda
        val rv = AtomicReference<String?>()
        val reassembly =
            HTTPReassembly(CaptureService.getCurPayloadMode() == Prefs.PayloadMode.FULL,
                object : ReassemblyListener {
                    override fun onChunkReassembled(chunk: PayloadChunk?) {
                        rv.set(
                            String(
                                chunk!!.payload,
                                StandardCharsets.UTF_8
                            )
                        )
                    }
                })

        // Possibly reassemble/decode the request
        for (chunk in payloadChunks!!) {
            if (chunk != null) {
                if (chunk.isSent == isSent) reassembly.handleChunk(chunk)
            }

            // Stop at the first reassembly/chunk
            if (rv.get() != null) break
        }
        return rv.get()
    }

    fun getHttpRequest(): String? {
        return getHttp(true)
    }

    fun getHttpResponse(): String? {
        return getHttp(false)
    }

    fun hasSeenStart(): Boolean {
        return if (ipproto != 6 /* TCP */ || !captureService.isCapturingAsRoot()) true else getSentTcpFlags() and 0x2 != 0
        // SYN
    }

    override fun toString(): String {
        return "ConnectionDescriptor" +
                "\n [proto=" + ipproto + "/" + l7proto + "]: " + srcIp + ":" + srcPort + " -> " +
                dstIp + ":" + dstPort + " [" + uid + "] " + info +
                "\n Country: $country" +
                "\n Time{" +
                "First seen: ${StringParser.dateParser(firstSeen)}, " +
                "Last seen: ${StringParser.dateParser(lastSeen)}}" +
                "\n Bytes{" +
                "Send: $sentBytes, " +
                "Receive: $rcvdBytes}" +
                "\n Packets{" +
                "Send: $sentPkts, " +
                "Receive: $rcvdPkts}"
    }

}