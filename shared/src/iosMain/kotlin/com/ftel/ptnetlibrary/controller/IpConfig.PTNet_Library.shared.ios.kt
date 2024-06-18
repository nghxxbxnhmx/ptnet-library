package com.ftel.ptnetlibrary.controller

import com.ftel.ptnetlibrary.dto.IpConfigDTO

actual class IpConfig {
    actual fun getIpAddress(useIpv4: Boolean): String {
        TODO("Not yet implemented")
    }

    actual fun getSubnetMask(): String {
        TODO("Not yet implemented")
    }

    actual fun getGateway(): String {
        TODO("Not yet implemented")
    }

    actual fun getDeviceMAC(): String {
        TODO("Not yet implemented")
    }

    actual fun getBSSID(): String {
        TODO("Not yet implemented")
    }

    actual fun getSSID(): String {
        TODO("Not yet implemented")
    }

    actual fun getInternalIpAddress(useIpv4: String): String {
        TODO("Not yet implemented")
    }

    actual fun getIpConfigInfo(): IpConfigDTO {
        TODO("Not yet implemented")
    }
}