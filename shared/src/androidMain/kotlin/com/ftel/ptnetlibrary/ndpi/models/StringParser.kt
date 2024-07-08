package com.ftel.ptnetlibrary.ndpi.models

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StringParser {
    fun dateParser(timestamp: Long): String {
        val date = Date(timestamp)
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        return format.format(date)
    }
}