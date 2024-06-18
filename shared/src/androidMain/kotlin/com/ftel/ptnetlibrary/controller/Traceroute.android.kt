package com.ftel.ptnetlibrary.controller

import com.ftel.ptnetlibrary.dto.TraceHopDTO
import com.ftel.ptnetlibrary.services.TracerouteService

actual class Traceroute {
    private val service = TracerouteService()
    actual fun trace(host: String, ttl: Int): TraceHopDTO {
        return service.trace(host, ttl)
    }
}