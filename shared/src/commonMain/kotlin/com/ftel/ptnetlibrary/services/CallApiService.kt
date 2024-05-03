package com.ftel.ptnetlibrary.services

expect class CallApiService {
    fun getApiResponse(url: String?): String
}