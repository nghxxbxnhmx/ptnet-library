package com.ftel.ptnetlibrary.services

expect class IpConfigService {
    fun getIpAddress(useIpv4: Boolean): String
    fun getSubnetMask(): String
    fun getGateway(): String
    fun getDeviceMAC(): String
    fun getBSSID(): String
    fun getSSID(): String
}