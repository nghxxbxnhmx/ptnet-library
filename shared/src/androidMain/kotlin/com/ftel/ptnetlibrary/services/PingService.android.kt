package com.ftel.ptnetlibrary.services

import android.util.Log
import com.ftel.ptnetlibrary.dto.PingDTO
import java.io.IOException

actual class PingService {
    actual fun execute(
        address: String
    ): PingDTO {
        return parseSinglePingResult(executePingCommand(address, "-c 1"))
    }


    private fun executePingCommand(address: String, options: String): String {
        return try {
            Runtime.getRuntime().exec("ping $options $address").inputStream.bufferedReader()
                .use { it.readText() }
        } catch (e: IOException) {
            Log.e("PingService", "func executePingCommand throw error!", e)
            "N/A"
        }
    }

    private fun parseSinglePingResult(pingOutput: String): PingDTO {
        val result = PingDTO("", "", -1.0)
        result.address = parseDomain(pingOutput)
        result.ip = parseIpAddress(pingOutput)
        result.time = parseTime(pingOutput).toDouble()
        return result
    }

    private fun parseDomain(hopInfo: String): String {
        val hopMatch =
            Regex("(?:64 bytes from |From )((?:[a-zA-Z0-9-]+\\.)*[a-zA-Z0-9-]+)(?=\\s|\\()").find(
                hopInfo
            )
        return hopMatch?.groups?.get(1)?.value ?: ""
    }

    private fun parseIpAddress(hopInfo: String): String {
        val ipMatch = Regex("(\\d+\\.\\d+\\.\\d+\\.\\d+)").find(hopInfo)
        return ipMatch?.groups?.get(1)?.value ?: ""
    }

    private fun parseTime(pingOutput: String): Float {
        val hopPattern = "from (.*?):.*time=(\\d+\\.\\d+|\\d+) ms".toRegex()
        val timeLine = pingOutput.lines().firstOrNull { it.contains("time=") }
        return hopPattern.find(timeLine ?: "")?.groupValues?.get(2)?.toFloatOrNull() ?: -1f
    }
}