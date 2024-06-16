package com.ftel.ptnetlibrary.dto

data class WifiScanResultDTO(
    var ssid: String,
    var bssid: String,
    var channel: Int,
    var signalLevel: Int,
    var channelBandwidth: Int,
    var advancedInfo: AdvancedInfo? = null
) {
    data class AdvancedInfo(
        val standard: String, // Modem's standard
        val capabilities: String, // All capabilities
        // BSS Transition (BT 802.11v): allows switch from one BSS to another BSS
        //  within the same Extended Service Set (ESS) without losing connectivity
        val bss: Int,
        // Fast BSS Transition (FT 802.11r): reducing the time it takes to transition
        // from one access point (AP) to another within the same Extended Service Set (ESS).
        val ft: Int,
        // Radio Measurement (RM 802.11k): enable WiFi clients and access points (APs)
        // gather information about the radio environment
        val rm: Int,
        // Management Frame Protection (MFP 802.11w): a security feature
        // that enhances the security of wireless networks,
        val pmf: Int,
        // Fine Timing Measurement (FTM 802.11mc): used to accurately measure the distance
        // or range between a client device and an access point (AP).
        val ftm: Int,
        // Multi-Link Operation (MLO 802.11be): allows a device to simultaneously communicate
        // with multiple peers (devices or access points) on different channels within the same band.
        val mlo: Int,
        // Multi-Link Device's Address (MLD 802.11be)
        val mld: String
    )
}