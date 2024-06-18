package com.ftel.ptnetlibrary.controller

import com.ftel.ptnetlibrary.dto.IpConfigDTO
import com.ftel.ptnetlibrary.services.IpConfigService

actual class IpConfig {
    private val ipConfigService = IpConfigService()
    actual fun getIpAddress(useIpv4: Boolean): String {
        return ipConfigService.getIpAddress(useIpv4)
    }

    actual fun getSubnetMask(): String {
        return ipConfigService.getSubnetMask()
    }

    actual fun getGateway(): String {
        return ipConfigService.getGateway()
    }

    actual fun getDeviceMAC(): String {
        return ipConfigService.getDeviceMAC()
    }

    actual fun getBSSID(): String {
        return ipConfigService.getBSSID()
    }

    actual fun getSSID(): String {
        return ipConfigService.getSSID()
    }

    actual fun getInternalIpAddress(useIpv4: Boolean): String {
        return ipConfigService.getInternalIpAddress(useIpv4)
    }

    actual fun getIpConfigInfo(): IpConfigDTO {
        return ipConfigService.getIpConfigInfo(true)
    }
}