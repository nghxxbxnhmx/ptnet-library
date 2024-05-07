package com.ftel.ptnetlibrary.services

import android.util.Log
import okio.IOException
import java.net.InetSocketAddress
import java.net.Socket

actual class PortScanService {
    actual fun portScan(address: String, port: Int, timeOut: Int): String {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(address.trim(), port), timeOut)
            Log.d("PortScan - Socket", "$socket")
            socket.close()
            "$port"
        } catch (e: IOException) {
            // Handle error, if needed
            Log.d("PortScan - SocketError", "${e.message}")
            ""
        }
    }
}