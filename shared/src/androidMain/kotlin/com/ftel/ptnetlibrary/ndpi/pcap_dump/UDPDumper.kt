package com.ftel.ptnetlibrary.ndpi.pcap_dump

import com.ftel.ptnetlibrary.ndpi.Utils
import com.ftel.ptnetlibrary.ndpi.interfaces.PcapDumper
import com.ftel.ptnetlibrary.ndpi.services.CaptureService
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress


class UDPDumper : PcapDumper {
    private val TAG = "UDPDumper"
    private var mServer: InetSocketAddress? = null
    private var mPcapngFormat = false
    private var mSendHeader = false
    private var mSocket: DatagramSocket? = null

    constructor()

    constructor(server: InetSocketAddress, pcapngFormat: Boolean) {
        mServer = server;
        mSendHeader = true;
        mPcapngFormat = pcapngFormat;
    }

    @Throws(IOException::class)
    override fun startDumper() {
        mSocket = DatagramSocket()
        CaptureService.requireInstance().protect(mSocket)
    }

    @Throws(IOException::class)
    override fun stopDumper() {
        mSocket!!.close()
    }

    override fun getBpf(): String {
        return "not (host " + mServer!!.address.hostAddress + " and udp port " + mServer!!.port + ")"
    }

    @Throws(IOException::class)
    private fun sendDatagram(data: ByteArray, offset: Int, len: Int) {
        val request = DatagramPacket(data, offset, len, mServer)
        mSocket!!.send(request)
    }

    @Throws(IOException::class)
    override fun dumpData(data: ByteArray?) {
        if (mSendHeader) {
            mSendHeader = false
            val hdr: ByteArray = CaptureService.getPcapHeader()!!
            sendDatagram(hdr, 0, hdr.size)
        }
        val it: Iterator<Int> = Utils.iterPcapRecords(data!!, mPcapngFormat)
        var pos = 0
        while (it.hasNext()) {
            val recLen = it.next()
            sendDatagram(data, pos, recLen)
            pos += recLen
        }
    }
}