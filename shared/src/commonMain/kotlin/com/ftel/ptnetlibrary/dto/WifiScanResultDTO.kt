package com.ftel.ptnetlibrary.dto

data class WifiScanResultDTO (
    var ssid: String,
    var bssid: String,
    var channel: Int,
    var signalLevel: Int,
    var channelBandwidth: Int
)