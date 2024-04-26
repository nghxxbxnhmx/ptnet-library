package com.ftel.ptnetlibrary.services

import com.ftel.ptnetlibrary.dto.PingInfoDTO
import okio.IOException

expect class PingService {
    fun execute(address: String, ttl: Int = -1): PingInfoDTO
}

fun parseDomain(hopInfo: String): String {
    val hopMatch = Regex("(?:64 bytes from |From )((?:[a-zA-Z0-9-]+\\.)*[a-zA-Z0-9-]+)(?=\\s|\\()").find(hopInfo)
    return hopMatch?.groups?.get(1)?.value ?: ""
}

fun parseIpAddress(hopInfo: String): String {
    val ipMatch = Regex("(\\d+\\.\\d+\\.\\d+\\.\\d+)").find(hopInfo)
    return ipMatch?.groups?.get(1)?.value ?: ""
}

fun parseTime(pingOutput: String): Float {
    val hopPattern = "from (.*?):.*time=(\\d+\\.\\d+|\\d+) ms".toRegex()
    val timeLine = pingOutput.lines().firstOrNull { it.contains("time=") }

    return hopPattern.find(timeLine ?: "")?.groupValues?.get(2)?.toFloatOrNull() ?: -1f
}

fun parseSinglePingResult(pingOutput: String): PingInfoDTO {
    val result = PingInfoDTO("", "", -1.0)
    result.address = parseDomain(pingOutput)
    result.ip = parseIpAddress(pingOutput)
    result.time = parseTime(pingOutput).toDouble()
    return result
}