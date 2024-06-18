package com.ftel.ptnetlibrary.services

import android.content.Context
import android.net.wifi.WifiManager
import com.ftel.ptnetlibrary.dto.WifiScanResultDTO
import com.ftel.ptnetlibrary.utils.getAppContext

class WifiScanService {
    private val wifiManager =
        getAppContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun getScanResult(): List<WifiScanResultDTO> {

        if (!wifiManager.isWifiEnabled) {
            return emptyList()
        }
        val wifiScanResults = wifiManager.scanResults

        val wifiScanResultList = mutableListOf<WifiScanResultDTO>()

//        if (ContextCompat.checkSelfPermission(
//                getAppContext(),
//                Manifest.permission.ACCESS_FINE_LOCATION
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            ActivityCompat.requestPermissions(
//                getAppContext() as Activity,
//                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
//                1009
//            )
//            return emptyList()
//        }

        wifiScanResults.forEach { scanResult ->
            val wifiScanResultDTO = WifiScanResultDTO(
                ssid = scanResult.SSID,
                bssid = scanResult.BSSID,
                channel = getWifiChannel(scanResult.frequency),
                signalLevel = scanResult.level,
                channelBandwidth = convertToChannelWidth(getWifiChannel(scanResult.frequency))
            )
            wifiScanResultList.add(wifiScanResultDTO)
        }

        return wifiScanResultList
    }

    fun startScan(): Boolean {
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
}