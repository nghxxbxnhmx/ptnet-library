package com.ftel.ptnetlibrary.controller

import com.ftel.ptnetlibrary.dto.IpConfigDTO

expect class IpConfig {
    fun getIpAddress(useIpv4: Boolean): String
    fun getSubnetMask(): String
    fun getGateway(): String
    fun getDeviceMAC(): String
    fun getBSSID(): String
    fun getSSID(): String
    fun getInternalIpAddress(useIpv4: Boolean): String
    fun getIpConfigInfo(): IpConfigDTO
}