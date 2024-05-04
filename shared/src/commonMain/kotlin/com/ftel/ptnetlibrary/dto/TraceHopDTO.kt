package com.ftel.ptnetlibrary.dto

data class TraceHopDTO (
    var hopNumber: Int,
    var domain: String,
    var ipAddress: String,
    var time: Double,
    var status: Boolean
)