package com.ftel.ptnetlibrary.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import com.ftel.ptnetlibrary.dto.WifiScanResultDTO
import com.ftel.ptnetlibrary.utils.getAppContext

actual class WifiScanService {
    actual fun scan(): List<WifiScanResultDTO> {
        val wifiManager =
            getAppContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifiManager.isWifiEnabled) {
            return emptyList()
        }
        val wifiScanResults = wifiManager.scanResults

        val wifiScanResultList = mutableListOf<WifiScanResultDTO>()

        if (ContextCompat.checkSelfPermission(
                getAppContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                getAppContext() as Activity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1009
            )
            return emptyList()
        }

        wifiScanResults.forEach { scanResult ->
            val wifiScanResultDTO = WifiScanResultDTO(
                ssid = scanResult.SSID,
                bssid = scanResult.BSSID,
                channel = getWifiChannel(scanResult.frequency),
                signalLevel = scanResult.level,
                bandwidth = scanResult.frequency
            )
            wifiScanResultList.add(wifiScanResultDTO)
        }

        return wifiScanResultList
    }

    private fun getWifiChannel(frequency: Int): Int {
        return when (frequency) {
            in 2412..2484 -> (frequency - 2412) / 5 + 1
            in 5170..5825 -> (frequency - 5170) / 5 + 34
            else -> -1
        }
    }
}