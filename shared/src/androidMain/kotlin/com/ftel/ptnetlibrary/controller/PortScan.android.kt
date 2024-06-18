package com.ftel.ptnetlibrary.controller

import com.ftel.ptnetlibrary.services.PortScanService

actual class PortScan {
    private val service = PortScanService()
    actual fun portScan(
        address: String,
        port: Int,
        timeOut: Int
    ): String {
        return service.portScan(address, port, timeOut)
    }
}