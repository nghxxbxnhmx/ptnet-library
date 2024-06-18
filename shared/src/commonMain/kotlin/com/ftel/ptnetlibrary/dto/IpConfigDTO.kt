package com.ftel.ptnetlibrary.dto

data class IpConfigDTO(
    val ipAddress: String,
    val subnetMask: String,
    val gateway: String,
    val deviceMAC: String,
    val bssid: String,
    val ssid: String,
    val internalIpAddress: String
)