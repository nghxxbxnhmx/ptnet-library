package com.ftel.ptnetlibrary.services

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import com.ftel.ptnetlibrary.dto.WifiScanResultDTO
import com.ftel.ptnetlibrary.utils.getAppContext

actual class WifiScanService {
    private val wifiManager =
        getAppContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @SuppressLint("MissingPermission")
    actual fun getScanResult(): List<WifiScanResultDTO> {

        if (!wifiManager.isWifiEnabled) {
            return emptyList()
        }

        val wifiScanResults = wifiManager.scanResults

        val wifiScanResultList = mutableListOf<WifiScanResultDTO>()

        wifiScanResults.forEach { scanResult ->
            val wifiScanResultDTO = WifiScanResultDTO(
                ssid = parseSSID(scanResult),
                bssid = scanResult.BSSID,
                channel = getWifiChannel(scanResult.frequency),
                signalLevel = scanResult.level,
                channelBandwidth = convertToChannelWidth(getWifiChannel(scanResult.frequency)),
                advancedInfo = WifiScanResultDTO.AdvancedInfo(
                    standard = parseStandard(scanResult),
                    capabilities = scanResult.capabilities,
                    bss = isSupportedBT80211v(scanResult), ft = isSupportedFT80211r(scanResult),
                    rm = isSupportedRM80211k(scanResult),
                    pmf = isSupportedPMF80211w(scanResult),
                    ftm = isSupportedFTM80211mc(scanResult),
                    mlo = isSupportedMLO80211be(scanResult),
                    mld = parseMLD(scanResult)
                )
            )
            wifiScanResultList.add(wifiScanResultDTO)
        }

        return wifiScanResultList
    }

    actual fun startScan(): Boolean {
        return wifiManager.startScan()
    }

    private fun getWifiChannel(frequency: Int): Int {
        return when (frequency) {
            in 2412..2484 -> (frequency - 2412) / 5 + 1
            in 5170..5825 -> (frequency - 5170) / 5 + 34
            else -> -1
        }
    }

    private fun convertToChannelWidth(channelWidth: Int): Int {
        return when (channelWidth) {
            in 1..14 -> 20 // Channels 1-14 in the 2.4 GHz band
            in 36..64 -> 40 // Channels 36-64 in the 5 GHz band
            in 100..144 -> 80 // Channels 100-144 in the 5 GHz band
            in 149..165 -> 160 // Channels 149-165 in the 5 GHz band
            else -> -1 // Default case
        }
    }

    // New Feature --------------------------------------------------------------
    /**
     * SSID Parser for given <hidden> network
     * @hide
     * Result: "[hidden]" / {SSID}
     */
    private fun parseSSID(wifi: ScanResult): String {
        return if (wifi.SSID.isNullOrBlank()) "[hidden]" else wifi.SSID
    }

    /**
     * Wifi's Modem Parser
     * @hide
     * Result: "Required API 30+" / {Standard}
     */
    private fun parseStandard(wifi: ScanResult): String {
        // Wi-Fi types
        val standardMap = mapOf(
            0 to "Unknown",
            1 to "Legacy",
            4 to "802.11n",
            5 to "802.11ac",
            6 to "802.11ax",
            7 to "802.11ad",
            8 to "802.11be"
        )
        // Lookup the standard name based on the ID
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            standardMap.getOrElse(wifi.wifiStandard) { "Unknown" }
        else "Required API 30+"
    }

    /**
     * BSS Transition (BT 802.11v) (May out-of-date: 2012)
     * @hide
     * Capabilities contains ["WPA"] & not ["WPA2"]
     * As ChatGPT: If Fast BSS Transition supported -> BSS Transition supported
     * Result: -1: ?
     *          0: Not Supported
     *          1: Supported
     */
    private fun isSupportedBT80211v(wifi: ScanResult): Int {
        val caps = wifi.capabilities
        if (isSupportedFT80211r(wifi) == 1) return 1
        if (caps.contains("WPA2") || caps.contains("WPA3") || caps.contains("RSN")) return 0
        if (caps.contains("WPA")) return 1
        return 0
    }

    /**
     * Fast BSS Transition (FT 802.11r)
     * @hide
     * Capabilities contains ["FT?PSK", "FT?EAP", "FT?SAE"]["+FT]
     * Result: -1: ?
     *          0: Not Supported
     *          1: Supported
     */
    private fun isSupportedFT80211r(wifi: ScanResult): Int {
        val caps = wifi.capabilities
        if (caps.contains("+FT")) return 1
        if (Regex("FT[_/+\\-]?(PSK|EAP|SAE)").containsMatchIn(caps)) return 1
        return 0
    }

    /**
     * Protected Management Frames (PMF 802.11w) Supported Check
     * @hide
     * SecurityParams.java----------------------------- ID from WifiConfiguration
     * SECURITY_TYPE_PASSPOINT_R3							12
     * SECURITY_TYPE_OWE									6
     * SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT			5
     * SECURITY_TYPE_EAP_WPA3_ENTERPRISE					9
     * SECURITY_TYPE_SAE									4
     * SECURITY_TYPE_DPP									13
     *
     * Or
     * Capabilities contains ["PMF","MFPR","MFPC"]
     * Result: -1: Require API 33+
     *          0: Not Supported
     *          1: Supported
     */
    private val pmfCheckList: IntArray = intArrayOf(4, 5, 6, 9, 12, 13)
    private fun isSupportedPMF80211w(wifi: ScanResult): Int {
        if (wifi.capabilities.contains("PMF")) return 1
        if (wifi.capabilities.contains("MFPR")) return 1
        if (wifi.capabilities.contains("MFPC")) return 1

        // Additional check by type of Security
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val securityTypes = wifi.securityTypes
//            var listSecurity = mutableListOf<Int>()
            for (securityType in securityTypes) {
//                listSecurity.add(securityType)
                if (pmfCheckList.contains(securityType)) {
                    return 1
                }
            }
            return 0
        }
        return -1
    }

    /**
     * Radio Measurement (RM 802.11k) Supported Check
     * @hide
     * In Android, there isn't a direct API through WifiManager
     * to check specifically for Resource Measurement/Management (RM) support.
     * RM is a feature that is generally handled at the driver or firmware level of the Wi-Fi chipset,
     * and it's not directly exposed to app developers through the Android SDK.
     *
     * Result: -1: Require API 33+
     *          0: Not Supported
     *          1: Supported
     */
    private fun isSupportedRM80211k(wifi: ScanResult): Int {
        if (wifi.capabilities.contains("PRM")) return 1
        if (wifi.capabilities.contains("RRM")) return 1

        // Additional check by type of Security
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val securityTypes = wifi.securityTypes
            for (securityType in securityTypes) {
                if (securityType == 11) {
                    return 1
                }
            }
            return 0
        }
        return -1
    }

    /** Fine-Time-Measurement (FTM 802.11mc)
     * @hide
     * https://developer.android.com/develop/connectivity/wifi/wifi-rtt?hl=en#java
     * Result: -1: Require API 23+
     *          0: Not supported
     *          1: Supported
     */
    private fun isSupportedFTM80211mc(wifi: ScanResult): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return if (wifi.is80211mcResponder) 1 else 0
        } else -1
    }

    /**
     * Multi-Link Operation (MLO 802.11be) Supported Check
     * @hide
     * Result:  -1: "Required API 33+"
     *           0: "Not supported"
     *           1: "Supported"
     */
    private fun isSupportedMLO80211be(wifi: ScanResult): Int {
        // The Multi-Link Operation (MLO) link id for the access point. Only applicable for Wi-Fi 7 access point
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (wifi.apMloLinkId == -1) 0 else 1
        } else -1
    }

    /**
     * Multi-Link Device (MLD) Address
     * @hide
     * Result:  "Required API 33+" / {Address}
     */
    private fun parseMLD(wifi: ScanResult): String {
        // The Multi-Link Device (MLD) address of the access point. Only applicable for Wi-Fi 7 access points, null otherwise.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            "${wifi.apMldMacAddress ?: ""}"
        } else "Required API 33+"
    }
}