package com.ftel.ptnetlibrary.services

import com.ftel.ptnetlibrary.dto.PingDTO

expect class PingService {
    fun execute(address: String): PingDTO
}