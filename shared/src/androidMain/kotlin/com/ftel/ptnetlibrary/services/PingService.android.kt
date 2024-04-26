package com.ftel.ptnetlibrary.services

import com.ftel.ptnetlibrary.dto.PingInfoDTO
import java.io.IOException

actual class PingService {
    actual fun execute(
        address: String,
        ttl: Int
    ): PingInfoDTO {
        if (ttl != -1) {
            return parseSinglePingResult(executePingCommand(address, "-c 1 -t $ttl"))
        } else {
            return parseSinglePingResult(executePingCommand(address, "-c 1"))
        }
    }

    private fun executePingCommand(address: String, options: String): String {
        return try {
            Runtime.getRuntime().exec("ping $options $address").inputStream.bufferedReader()
                .use { it.readText() }
        } catch (e: IOException) {
            "N/A"
        }
    }

}