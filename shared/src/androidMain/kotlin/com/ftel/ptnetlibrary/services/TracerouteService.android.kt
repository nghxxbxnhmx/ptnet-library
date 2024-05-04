package com.ftel.ptnetlibrary.services

import android.util.Log
import com.ftel.ptnetlibrary.dto.TraceHopDTO
import java.io.IOException

actual class TracerouteService {
    actual fun trace(host: String, ttl: Int): TraceHopDTO {
        val pingResult : String = pingCommand(url = host, options = "-c 1 -t $ttl")
        val traceContainer : TraceHopDTO = parsePingOutput(pingResult)
        traceContainer.time = parsePingOutput(pingCommand(url = traceContainer.ipAddress, options = "-c 1")).time
        traceContainer.domain = traceContainer.domain.replace(":","")
        if(traceContainer.domain.equals(traceContainer.ipAddress)) traceContainer.domain = ""
        return traceContainer
    }


    fun pingCommand(url: String, options: String): String {
        return try {
            Runtime.getRuntime().exec("ping $options $url").inputStream.bufferedReader()
                .use { it.readText() }
        } catch (e: IOException) {
            Log.e("PingUtility", "Error executing ping command", e)
            ""
        }
    }

    private fun parsePingOutput(pingOutput: String): TraceHopDTO {
        val traceContainer = TraceHopDTO(0, "", "", 0.0, false)

        val lines = pingOutput.split("\n")
        val hopInfo = lines.getOrNull(1) ?: return TraceHopDTO(0, "", "", -1.0, false)

        traceContainer.status = true
        traceContainer.domain = parseDomain(hopInfo)
        traceContainer.ipAddress = parseIpAddress(hopInfo)
        traceContainer.time = parseTime(pingOutput)
        return traceContainer
    }

    private fun parseDomain(pingOutput: String): String {
        val hopMatch = Regex("(?:64 bytes from |From )((?:[a-zA-Z0-9-]+\\.)*[a-zA-Z0-9-]+)(?=\\s|\\()").find(pingOutput)
        return hopMatch?.groups?.get(1)?.value ?: ""
    }

    private fun parseIpAddress(pingOutput: String): String {
        val ipMatch = Regex("(\\d+\\.\\d+\\.\\d+\\.\\d+)").find(pingOutput)
        return ipMatch?.groups?.get(1)?.value ?: ""
    }

    private fun parseTime(pingOutput: String): Double {
        val hopPattern = "from (.*?):.*time=(\\d+\\.\\d+|\\d+) ms".toRegex()
        val timeLine = pingOutput.lines().firstOrNull { it.contains("time=") }

        return hopPattern.find(timeLine ?: "")?.groupValues?.get(2)?.toDoubleOrNull() ?: -1.0
    }


    fun parseEndPoint(pingOutput: String): TraceHopDTO {
        val domainAndIpPattern = Regex("PING (.+?) \\((.*?)\\)")
        val matchResult = domainAndIpPattern.find(pingOutput)

        return if (matchResult != null) {
            val domain = matchResult.groupValues[1]
            val ipAddress = matchResult.groupValues[2]

            TraceHopDTO(
                hopNumber = 1,
                domain = domain,
                ipAddress = ipAddress,
                time = 0.0,
                status = false
            )
        } else {
            TraceHopDTO(
                hopNumber = 1,
                domain = "",
                ipAddress = "",
                time = 0.0,
                status = false
            )
        }
    }

}