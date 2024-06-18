package com.ftel.ptnetlibrary.controller

import com.ftel.ptnetlibrary.dto.PageLoadDTO

expect class PageLoadTimer {
    fun execute(address: String): PageLoadDTO
}