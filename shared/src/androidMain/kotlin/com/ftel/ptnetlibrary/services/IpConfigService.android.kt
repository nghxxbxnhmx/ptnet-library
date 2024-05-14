package com.ftel.ptnetlibrary.services

import android.content.Context
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.ftel.ptnetlibrary.utils.getAppContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.net.UnknownHostException
import java.util.Collections
import java.util.Locale

actual class IpConfigService {
    actual fun getIpAddress(useIpv4: Boolean): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs: List<InetAddress> = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress
                        if (useIpv4 && addr is Inet4Address || !useIpv4 && addr is Inet6Address) {
                            return sAddr.uppercase(Locale.getDefault())
                        }
                    }
                }
            }
        } catch (e: SocketException) {
            Log.e("IpConfigHelper", "Get IP Address throw error!", e)
        }
        return "N/A"
    }

    actual fun getSubnetMask(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (intf.isUp && !intf.isLoopback) {
                    for (addr in Collections.list(intf.inetAddresses)) {
                        if (addr is Inet4Address) {
                            val prefixLength = getIpv4PrefixLength(intf)
                            if (prefixLength >= 0 && prefixLength <= 32) {
                                return convertPrefixLengthToSubnetMask(prefixLength.toShort())
                            }
                        }
                    }
                }
            }
        } catch (e: SocketException) {
            Log.e("IpConfigHelper", "Get SubnetMask throw error!", e)
        }
        return "N/A"
    }
    private fun getIpv4PrefixLength(networkInterface: NetworkInterface): Int {
        val addresses = networkInterface.interfaceAddresses
        for (address in addresses) {
            val inetAddress = address.address
            if (inetAddress is Inet4Address) {
                return address.networkPrefixLength.toInt()
            }
        }
        return -1
    }

    private fun convertPrefixLengthToSubnetMask(prefixLength: Short): String {
        val netmask = -0x1 shl 32 - prefixLength
        return String.format(
            Locale.getDefault(),
            "%d.%d.%d.%d",
            netmask shr 24 and 0xff,
            netmask shr 16 and 0xff,
            netmask shr 8 and 0xff,
            netmask and 0xff
        )
    }


    actual fun getGateway(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val element = interfaces.nextElement()
                if (element.isUp && !element.isLoopback) {
                    val gateway = findDefaultGateway(element)
                    if (gateway != null) {
                        return gateway
                    }
                }
            }
        } catch (e: SocketException) {
            Log.d("IpConfigHelper", "Get Gateway throw error!!!", e)
        }
        return "N/A"
    }

    private fun findDefaultGateway(networkInterface: NetworkInterface): String? {
        for (interfaceAddress in networkInterface.interfaceAddresses) {
            val address = interfaceAddress.address
            if (address is Inet4Address) {
                val networkAddress =
                    calculateNetworkAddress(address, interfaceAddress.networkPrefixLength)
                return networkAddress!!.substring(0, networkAddress.lastIndexOf(".")) + ".1"
            }
        }
        return null
    }

    private fun calculateNetworkAddress(address: InetAddress, prefixLength: Short): String? {
        val ipBytes = address.address
        val ipInt = ipBytes[0].toInt() and 0xFF shl 24 or
                (ipBytes[1].toInt() and 0xFF shl 16) or
                (ipBytes[2].toInt() and 0xFF shl 8) or
                (ipBytes[3].toInt() and 0xFF)
        val networkInt = ipInt and (-0x1 shl 32 - prefixLength)
        val networkBytes = ByteArray(4)
        networkBytes[0] = (networkInt and -0x1000000 ushr 24).toByte()
        networkBytes[1] = (networkInt and 0x00FF0000 ushr 16).toByte()
        networkBytes[2] = (networkInt and 0x0000FF00 ushr 8).toByte()
        networkBytes[3] = (networkInt and 0x000000FF).toByte()
        return try {
            InetAddress.getByAddress(networkBytes).hostAddress
        } catch (e: UnknownHostException) {
            Log.e("IpConfigHelper", "func calculateNetworkAddress throw error!", e)
            null
        }
    }


    actual fun getDeviceMAC(): String {
        val wifiManager =
            getAppContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo: WifiInfo? = wifiManager.connectionInfo
        val macAddress = wifiInfo?.macAddress
        return if (macAddress.isNullOrEmpty() || macAddress == "02:00:00:00:00:00" || macAddress == "00:00:00:00:00:00") {
            "N/A"
        } else {
            macAddress
        }
    }

    actual fun getBSSID(): String {
        val wifiManager =
            getAppContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo: WifiInfo? = wifiManager.connectionInfo
        val bssid = wifiInfo?.bssid ?: "N/A"
        return bssid.toUpperCase()
    }

    actual fun getSSID(): String {
        val wifiManager =
            getAppContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo: WifiInfo? = wifiManager.connectionInfo
        val ssid = wifiInfo?.ssid?.let {
            return it.trim().removePrefix("\"").removeSuffix("\"")
        }
        return ssid ?: "N/A"
    }
}