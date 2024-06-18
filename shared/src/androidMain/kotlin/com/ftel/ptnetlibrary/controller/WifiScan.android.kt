package com.ftel.ptnetlibrary.controller

import com.ftel.ptnetlibrary.dto.WifiScanResultDTO
import com.ftel.ptnetlibrary.services.WifiScanService

actual class WifiScan {
    private val service = WifiScanService()
    actual fun getScanResult(): List<WifiScanResultDTO> {
        return service.getScanResult()
    }

    actual fun startScan(): Boolean {
        return service.startScan()
    }
}