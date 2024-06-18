package com.ftel.ptnetlibrary.services

import android.util.Log
import com.ftel.ptnetlibrary.dto.PageLoadDTO
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
class PageLoadService {
    private val client = OkHttpClient()

    fun execute(address: String): PageLoadDTO {
        if (address.replace(" ", "").isBlank()) {
            return PageLoadDTO("", "Error", 0, -1.0, "Invalid URL")
        }

        val url = if (address.contains("http://") || address.contains("https://")) {
            address.trim()
        } else {
            "http://${address.trim()}"
        }

        val request: Request = Request.Builder().url(url).build()

        val startTime = System.currentTimeMillis()
        return try {
            val response: Response = client.newCall(request).execute()
            val endTime = System.currentTimeMillis()
            val duration = (endTime - startTime).toDouble() / 1000

            val responseData = response.body?.string() ?: ""
            val statusCode = response.code
            val status = if (response.isSuccessful) "Success" else "Error"
            val message = if (response.isSuccessful) "Success" else "HTTP response status code: $statusCode"

            response.close()

            PageLoadDTO(responseData, status, statusCode, duration, message)
        } catch (e: IOException) {
            Log.d("PageLoad", "Get time throw error! (check network connected)", e)
            PageLoadDTO("", "Error", 0, -1.0, "Request error: ${e.localizedMessage}")
        }
    }
}