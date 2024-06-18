package com.ftel.ptnetlibrary.controller

import com.ftel.ptnetlibrary.dto.TraceHopDTO

expect class Traceroute {
    fun trace(host: String,ttl: Int): TraceHopDTO
}