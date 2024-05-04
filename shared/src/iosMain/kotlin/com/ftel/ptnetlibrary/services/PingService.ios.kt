package com.ftel.ptnetlibrary.services

import com.ftel.ptnetlibrary.dto.PingDTO
actual class PingService {
    actual fun execute(
        address: String
    ): PingDTO {
        return  PingDTO("google.com", "8.8.8.8", -1.0)
    }
}