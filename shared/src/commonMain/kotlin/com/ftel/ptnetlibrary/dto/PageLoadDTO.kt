package com.ftel.ptnetlibrary.dto
data class PageLoadDTO(
    val data: String,
    val status: String,
    val statusCode: Int,
    val responseTime: Double,
    val message: String
)