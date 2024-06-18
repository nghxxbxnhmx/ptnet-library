package com.ftel.ptnetlibrary.controller

import com.ftel.ptnetlibrary.dto.WifiScanResultDTO

expect class WifiScan {
    fun getScanResult(): List<WifiScanResultDTO>
    fun startScan() : Boolean
}