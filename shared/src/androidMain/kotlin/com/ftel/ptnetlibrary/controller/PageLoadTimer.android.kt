package com.ftel.ptnetlibrary.controller

import com.ftel.ptnetlibrary.dto.PageLoadDTO
import com.ftel.ptnetlibrary.services.PageLoadService

actual class PageLoadTimer {
    actual fun execute(address: String): PageLoadDTO {
        val service = PageLoadService()
        return service.execute(address)
    }
}