package com.ftel.ptnetlibrary.services

import com.ftel.ptnetlibrary.dto.PageLoadInfoDTO

expect class PageLoadService {
    fun pageLoadTimer(address: String): Double
}