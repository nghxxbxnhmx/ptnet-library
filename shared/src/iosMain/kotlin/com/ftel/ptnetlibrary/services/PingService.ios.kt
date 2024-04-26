package com.ftel.ptnetlibrary.services

import com.ftel.ptnetlibrary.dto.PingInfoDTO

import platform.Foundation.URL
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSError
import platform.Foundation.NSPipe
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.readDataToEndOfFile
import platform.posix.execlp
import kotlin.native.OsFamily.*
actual class PingService {
    actual fun execute(
        address: String,
        ttl: Int
    ): PingInfoDTO {
        val output = executePingCommand(address)
        return parseSinglePingResult(output)
    }

    private fun executePingCommand(address: String): String {
        return execlp("ping", "ping", "-c", "5", "google.com", null).toString()
    }

    fun runWindows() {
        val result = execlp("dir", "","")
        println("Ran on windows $result");
    }

    fun runUnix() {
        execlp("date", "","")
        println("Ran on UNIX")
    }
}