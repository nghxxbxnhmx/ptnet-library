package com.ftel.ptnetlibrary.services

import com.ftel.ptnetlibrary.dto.TraceHopDTO

expect class TracerouteService {
    fun trace(host: String,ttl: Int): TraceHopDTO
}