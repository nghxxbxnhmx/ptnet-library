package com.ftel.ptnetlibrary.ndpi.models

import kotlin.collections.ArrayList

class ConnectionUpdate {
    val UPDATE_STATS = 0x1
    val UPDATE_INFO = 0x2
    val UPDATE_PAYLOAD = 0x4
    val UPDATE_INFO_FLAG_ENCRYPTED_L7 = 0x1
    var incrId = 0
    var updateType = 0

    /* set if update_type & UPDATE_STATS */
    var lastSeen: Long = 0
    var payloadLength: Long = 0
    var sentBytes: Long = 0
    var rcvdBytes: Long = 0
    var sentPkts = 0
    var rcvdPkts = 0
    var blockedPkts = 0
    var tcpFlags = 0
    var status = 0
    var infoFlags = 0

    /* set if update_type & UPDATE_INFO */
    var info: String? = null
    var url: String? = null
    var l7proto: String? = null

    /* set if update_type & UPDATE_PAYLOAD */
    var payloadChunks: ArrayList<PayloadChunk>? = null
    var payloadTruncated = false

    constructor()

    constructor(incrId: Int) {
        this.incrId = incrId;
    }

    fun setStats(
        lastSeen: Long, payloadLength: Long, sentBytes: Long, rcvdBytes: Long,
        sentPkts: Int, rcvdPkts: Int, blockedPkts: Int,
        tcpFlags: Int, status: Int
    ) {
        updateType = updateType or UPDATE_STATS
        this.lastSeen = lastSeen
        this.payloadLength = payloadLength
        this.sentBytes = sentBytes
        this.rcvdBytes = rcvdBytes
        this.sentPkts = sentPkts
        this.blockedPkts = blockedPkts
        this.rcvdPkts = rcvdPkts
        this.tcpFlags = tcpFlags
        this.status = status
    }

    fun setInfo(info: String, url: String, l7proto: String, infoFlags: Int) {
        updateType = updateType or UPDATE_INFO
        this.info = info
        this.url = url
        this.l7proto = l7proto
        this.infoFlags = infoFlags
    }

    fun setPayload(payloadChunks: ArrayList<PayloadChunk>?, payloadTruncated: Boolean) {
        updateType = updateType or UPDATE_PAYLOAD
        this.payloadChunks = payloadChunks
        this.payloadTruncated = payloadTruncated
    }
}