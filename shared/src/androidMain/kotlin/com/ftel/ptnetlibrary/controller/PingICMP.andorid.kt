package com.ftel.ptnetlibrary.controller

import com.ftel.ptnetlibrary.dto.PingDTO
import com.ftel.ptnetlibrary.services.PingService

actual class PingICMP {
    private val pingService = PingService()
    actual fun execute(address: String): PingDTO {
        return pingService.execute(address)
    }
}