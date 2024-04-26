package com.ftel.ptnetlibrary.services

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

actual class PageLoadService {
    val client = OkHttpClient()


    actual fun pageLoadTimer(address: String): Double {
        if (address.isNullOrEmpty()) {
            return -1.0
        }
        var url = ""
        if (address.contains("http://") || address.contains("https://")) {
            url = address.trim()
        } else {
            url = "http://${address.trim()}"
        }

        val request: Request = Request.Builder().url(url).build()

        val startTime = System.currentTimeMillis()
        return try {
            val response: Response = client.newCall(request).execute()
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            response.body
                ?.close()
            duration.toDouble()
        } catch (e: IOException) {
            -1.0
        }
    }
}