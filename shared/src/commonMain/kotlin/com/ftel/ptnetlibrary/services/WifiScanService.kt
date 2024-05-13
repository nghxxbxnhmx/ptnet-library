package com.ftel.ptnetlibrary.services

import com.ftel.ptnetlibrary.dto.WifiScanResultDTO

expect class WifiScanService {
    fun getScanResult(): List<WifiScanResultDTO>
    fun startScan() : Boolean
}