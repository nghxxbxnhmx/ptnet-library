package com.ftel.ptnetlibrary.ndpi.models


class AppStats : Cloneable {
    private var uid = 0
    var sentBytes: Long = 0
    var rcvdBytes: Long = 0
    var numConnections = 0
    var numBlockedConnections = 0

    constructor(uid: Int) {
        this.uid = uid
    }

    fun getUid(): Int {
        return uid
    }

    public override fun clone(): AppStats {
        val rv = AppStats(uid)
        rv.sentBytes = sentBytes
        rv.rcvdBytes = rcvdBytes
        rv.numConnections = numConnections
        rv.numBlockedConnections = numBlockedConnections
        return rv
    }

    override fun toString(): String {
        return "AppStats(uid=$uid, sentBytes=$sentBytes, rcvdBytes=$rcvdBytes, numConnections=$numConnections, numBlockedConnections=$numBlockedConnections)"
    }
}