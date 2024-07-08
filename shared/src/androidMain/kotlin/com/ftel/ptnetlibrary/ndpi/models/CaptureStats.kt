package com.ftel.ptnetlibrary.ndpi.models

import java.io.Serializable

class CaptureStats : Serializable {
    var alloc_summary: String? = null
    var bytes_sent: Long = 0
    var bytes_rcvd: Long = 0
    var ipv6_bytes_sent: Long = 0
    var ipv6_bytes_rcvd: Long = 0
    var pcap_dump_size: Long = 0
    var pkts_sent = 0
    var pkts_rcvd = 0
    var pkts_dropped = 0
    var num_dropped_conns = 0
    var num_open_sockets = 0
    var max_fd = 0
    var active_conns = 0
    var tot_conns: Long = 0
    var num_dns_queries = 0

    /* Invoked by native code */
    @JvmName("setDataFromNative")
    fun setData(
        _alloc_summary: String?,
        _bytes_sent: Long, _bytes_rcvd: Long,
        _ipv6_bytes_sent: Long, _ipv6_bytes_rcvd: Long,
        _pcap_dump_size: Long, _pkts_sent: Int, _pkts_rcvd: Int,
        _pkts_dropped: Int, _num_dropped_conns: Int, _num_open_sockets: Int,
        _max_fd: Int, _active_conns: Int, _tot_conns: Int, _num_dns_queries: Int
    ) {
        alloc_summary = _alloc_summary
        bytes_sent = _bytes_sent
        bytes_rcvd = _bytes_rcvd
        ipv6_bytes_sent = _ipv6_bytes_sent
        ipv6_bytes_rcvd = _ipv6_bytes_rcvd
        pcap_dump_size = _pcap_dump_size
        pkts_sent = _pkts_sent
        pkts_rcvd = _pkts_rcvd
        pkts_dropped = _pkts_dropped
        num_dropped_conns = _num_dropped_conns
        num_open_sockets = _num_open_sockets
        max_fd = _max_fd
        active_conns = _active_conns
        tot_conns = _tot_conns.toLong()
        num_dns_queries = _num_dns_queries
    }
}