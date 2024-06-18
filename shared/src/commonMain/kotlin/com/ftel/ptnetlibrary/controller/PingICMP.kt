package com.ftel.ptnetlibrary.controller

import com.ftel.ptnetlibrary.dto.PingDTO

expect class PingICMP {
    fun execute(address: String): PingDTO
}w